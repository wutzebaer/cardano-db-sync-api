package de.peterspace.cardanodbsyncapi.service;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import de.peterspace.cardano.javalib.CardanoUtils;
import de.peterspace.cardano.javalib.CardanoUtils.AddressType;
import de.peterspace.cardano.javalib.HexUtils;
import de.peterspace.cardanodbsyncapi.config.TrackExecutionTime;
import de.peterspace.cardanodbsyncapi.dto.AccountStatementRow;
import de.peterspace.cardanodbsyncapi.dto.EpochStake;
import de.peterspace.cardanodbsyncapi.dto.OwnerInfo;
import de.peterspace.cardanodbsyncapi.dto.PoolInfo;
import de.peterspace.cardanodbsyncapi.dto.ReturnAddress;
import de.peterspace.cardanodbsyncapi.dto.StakeAddress;
import de.peterspace.cardanodbsyncapi.dto.StakeInfo;
import de.peterspace.cardanodbsyncapi.dto.TokenDetails;
import de.peterspace.cardanodbsyncapi.dto.TokenListItem;
import de.peterspace.cardanodbsyncapi.dto.Utxo;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@RequiredArgsConstructor
public class CardanoDbSyncService {
	private final JdbcTemplate jdbcTemplate;
	private byte[] handlePolicyBytes;

	@PostConstruct
	public void init() throws DecoderException {
		handlePolicyBytes = Hex.decodeHex("f0ff48bbb7bbe9d59a40f1ce90e9e9d0ff5002ec48f232b49ca0fb9a");

		// find mints of multi asset
		log.info("Creating index idx_ma_tx_mint_ident");
		jdbcTemplate.execute("CREATE index if not exists idx_ma_tx_mint_ident ON ma_tx_mint USING btree (ident);");

		// find multi asset by fingerprint
		log.info("Creating index idx_multi_asset_fingerprint");
		jdbcTemplate.execute("CREATE index if not exists idx_multi_asset_fingerprint ON multi_asset USING btree (fingerprint);");

		// index for utxo view, to lookup used txos dirctly with txid and idx, not only
		// txid
		log.info("Creating index idx_tx_in_tx_out_id_tx_out_index");
		jdbcTemplate.execute("CREATE INDEX if not exists idx_tx_in_tx_out_id_tx_out_index ON tx_in USING btree (tx_out_id, tx_out_index);");

		// token owners
		log.info("Creating materialized view ma_owners");
		jdbcTemplate.execute("""
				CREATE MATERIALIZED VIEW IF NOT exists ma_owners AS
					select
						coalesce(sa."view" , txo.address) address
						,sum(mto.quantity) quantity
						,ma."policy" "policy"
						,array_agg(distinct encode(ma."name", 'hex'))  maNames
					from multi_asset ma
					join ma_tx_out mto on ma.id=mto.ident
					join tx_out txo on txo.id=mto.tx_out_id
					left join tx_in ti on ti.tx_out_id=txo.tx_id and ti.tx_out_index=txo."index"
					left join stake_address sa on sa.id=txo.stake_address_id
					where
					ti.id is null
					group by ma."policy", coalesce(sa."view" , txo.address);
					""");
		log.info("Creating index idx_ma_owners_policy");
		jdbcTemplate.execute("CREATE INDEX if not exists idx_ma_owners_policy ON ma_owners (policy);");

		log.info("Creating materialized view minswap_utxos");
		jdbcTemplate.execute("""
				CREATE MATERIALIZED VIEW IF NOT exists minswap_utxos AS
				select
					tx.hash tx_hash,
					uv."index" tx_index,
					null ma_policy_id,
					null ma_name,
					uv.value,
					uv.address owning_address
				from utxo_view uv
				join tx on tx.id = uv.tx_id
				where
					uv.payment_cred=decode('e1317b152faac13426e6a83e06ff88a4d62cce3c1634ab0a5ec13309', 'hex')
				union
				select
					tx.hash,
					uv."index",
					ma."policy",
					ma."name",
					mto.quantity,
					uv.address owning_address
				from utxo_view uv
				join tx on tx.id = uv.tx_id
				join tx_out txo on txo.tx_id = uv.tx_id and txo."index" = uv."index"
				join ma_tx_out mto on mto.tx_out_id=txo.id
				join multi_asset ma on ma.id=mto.ident
				where
					uv.payment_cred=decode('e1317b152faac13426e6a83e06ff88a4d62cce3c1634ab0a5ec13309', 'hex')
				""");

		log.info("Indexes created");
	}

	@TrackExecutionTime
	@Scheduled(cron = "0 0 0/12 * * *")
	public void updateOwnerView() {
		log.info("Refreshing ma_owners");
		jdbcTemplate.execute("REFRESH MATERIALIZED VIEW CONCURRENTLY ma_owners;");
	}

	@TrackExecutionTime
	@Scheduled(cron = "0 0 * * * *")
	public void updateMinswapView() {
		log.info("Refreshing minswap_utxos");
		jdbcTemplate.execute("REFRESH MATERIALIZED VIEW CONCURRENTLY minswap_utxos;");
	}

	public List<Utxo> getUtxos(String addr) throws DecoderException {

		AddressType addressType = CardanoUtils.determineAddressType(addr);

		String join;
		String where;
		byte[] hash;
		if (addressType == AddressType.STAKE_ADDRESS) {
			String stakeHash = CardanoUtils.stakeToHash(addr);
			hash = Hex.decodeHex(stakeHash);
			join = "join stake_address sa on sa.id=uv.stake_address_id ";
			where = "sa.hash_raw=? ";
		} else {
			String paymentHash = CardanoUtils.extractPaymentHash(addr);
			hash = Hex.decodeHex(paymentHash);
			join = "";
			where = "uv.payment_cred=? ";
		}

		String query = String.format("""
				select
					tx.hash tx_hash,
					uv."index" tx_index,
					null ma_policy_id,
					null ma_name,
					uv.value,
					uv.address owning_address,
					(
						select txo.address
						from tx_in ti
						join tx_out txo on txo.tx_id = ti.tx_out_id and txo."index" = ti.tx_out_index
						where ti.tx_in_id = uv.tx_id
						limit 1
					) source_address
				from utxo_view uv
				%s
				join tx on tx.id = uv.tx_id
				where
					%s
				union
				select
					tx.hash,
					uv."index",
					ma."policy",
					ma."name",
					mto.quantity,
					uv.address owning_address,
					(
						select txo.address
						from tx_in ti
						join tx_out txo on txo.tx_id = ti.tx_out_id and txo."index" = ti.tx_out_index
						where ti.tx_in_id = uv.tx_id
						limit 1
					) source_address
				from utxo_view uv
				%s
				join tx on tx.id = uv.tx_id
				join tx_out txo on txo.tx_id = uv.tx_id and txo."index" = uv."index"
				join ma_tx_out mto on mto.tx_out_id=txo.id
				join multi_asset ma on ma.id=mto.ident
				where
					%s
				""", join, where, join, where);
		return jdbcTemplate.query(query,
				(rs, rowNum) -> new Utxo(
						Hex.encodeHexString(rs.getBytes("tx_hash")),
						rs.getInt("tx_index"),
						toHexString(rs.getBytes("ma_policy_id")),
						toHexString(rs.getBytes("ma_name")),
						rs.getLong("value"),
						rs.getString("owning_address"),
						rs.getString("source_address")),
				hash, hash);
	}

	public List<Utxo> getMinswapUtxos(String policyId, String assetName) throws DecoderException {
		String query = """
				select mu.*
				from minswap_utxos mu_find
				join minswap_utxos mu on (mu.tx_hash=mu_find.tx_hash and mu.tx_index=mu_find.tx_index)
				where mu_find.ma_policy_id=? and mu_find.ma_name=?
				order by mu.tx_hash, mu.tx_index
				""";
		return jdbcTemplate.query(query,
				(rs, rowNum) -> new Utxo(
						Hex.encodeHexString(rs.getBytes("tx_hash")),
						rs.getInt("tx_index"),
						toHexString(rs.getBytes("ma_policy_id")),
						toHexString(rs.getBytes("ma_name")),
						rs.getLong("value"),
						rs.getString("owning_address"),
						null),
				Hex.decodeHex(policyId), Hex.decodeHex(assetName));
	}

	public ReturnAddress getReturnAddress(String stakeAddress) {
		try {
			AddressType addressType = CardanoUtils.determineAddressType(stakeAddress);

			if (addressType == AddressType.SERVICE_ADDRESS) {
				return new ReturnAddress(stakeAddress);
			}

			if (addressType == AddressType.SHELLY_ADDRESS) {
				stakeAddress = getStakeAddress(stakeAddress).getAddress();
			}

			return jdbcTemplate.queryForObject("""
					select txo.address
					from stake_address sa
					join tx_out txo on txo.stake_address_id=sa.id
					where sa."view"=?
					order by txo.id
					limit 1
					""",
					(rs, rowNum) -> new ReturnAddress(rs.getString("address")), stakeAddress);
		} catch (EmptyResultDataAccessException e) {
			return null;
		}
	}

	public StakeAddress getStakeAddress(String address) {
		try {
			return jdbcTemplate.queryForObject("""
					select sa."view" stakeAddress from
					tx_out txo
					join stake_address sa on sa.id=txo.stake_address_id
					where txo.address=?
					limit 1
					""",
					(rs, rowNum) -> new StakeAddress(rs.getString("stakeAddress")), address);
		} catch (EmptyResultDataAccessException e) {
			return null;
		}
	}

	public StakeAddress getStakeAddressByHash(String stakeAddressHash) throws DataAccessException, DecoderException {
		try {
			return jdbcTemplate.queryForObject("""
					select view stakeAddress from stake_address sa where sa.hash_raw=?;
					""",
					(rs, rowNum) -> new StakeAddress(rs.getString("stakeAddress")), Hex.decodeHex(stakeAddressHash));
		} catch (EmptyResultDataAccessException e) {
			return null;
		}
	}

	public StakeAddress getStakeHashByAddress(String stakeAddress) throws DataAccessException, DecoderException {
		try {
			return jdbcTemplate.queryForObject("""
					select hash_raw hash from stake_address sa where sa.view=?;
					""",
					(rs, rowNum) -> new StakeAddress(Hex.encodeHexString(rs.getBytes("hash"))), stakeAddress);
		} catch (EmptyResultDataAccessException e) {
			return null;
		}
	}

	public List<TokenListItem> getTokenList(Long afterMintid, Long beforeMintid, String filter) throws DecoderException {

		List<String> filters = new ArrayList<String>();
		List<Object> filterParams = new ArrayList<Object>();

		if (afterMintid != null) {
			filters.add("and ma_mint_id > ?");
			filterParams.add(afterMintid);
		}

		if (beforeMintid != null) {
			filters.add("and ma_mint_id < ?");
			filterParams.add(beforeMintid);
		}

		if (!StringUtils.isBlank(filter)) {
			filter = filter.trim();
			String[] bits = filter.split("\\.");
			if (bits.length == 2 && bits[0].length() == 56) {
				filters.add("and ma_policy_id=? and ma_name=?");
				filterParams.add(Hex.decodeHex(bits[0]));
				filterParams.add(Hex.decodeHex(bits[1]));
			} else if (bits.length == 1 && bits[0].length() == 56) {
				filters.add("and ma_policy_id=?");
				filterParams.add(Hex.decodeHex(bits[0]));
			} else if (bits[0].length() == 44 && bits[0].startsWith("asset")) {
				filters.add("and ma_fingerprint=?");
				filterParams.add(bits[0]);
			} else {
				return List.of();
			}
		}

		return jdbcTemplate.query("""
				select
					ma_mint_id
					,slot_no
					,ma_policy_id
					,ma_name
					,ma_fingerprint
					,quantity
					,metadata->>'name' "name"
					,case
						WHEN jsonb_typeof(metadata->'image') = 'array'
						then (select string_agg(value, '') from jsonb_array_elements_text(metadata->'image'))
				    	ELSE metadata->>'image'
				  	END "image"
				from (
					select
						mtm.id ma_mint_id
						,b.slot_no
						,ma."policy" ma_policy_id
						,ma.name ma_name
						,ma.fingerprint ma_fingerprint
						,mtm.quantity
						,coalesce(tm.json->encode(ma.policy::bytea, 'hex')->encode(ma.name::bytea, 'escape'), tm.json->encode(ma.policy::bytea, 'hex')->encode(ma.name::bytea, 'hex')) metaData
					from ma_tx_mint mtm
					join multi_asset ma on ma.id = mtm.ident
					join tx on tx.id = mtm.tx_id
					join block b on b.id = tx.block_id
					left join tx_metadata tm on tm.tx_id = tx.id and tm.key=721
					) sub
				where
					metadata is not null
					""" + StringUtils.join(filters, " ") + """
				order by ma_mint_id desc
				limit 100
				""",
				(rs, rowNum) -> new TokenListItem(
						rs.getLong("ma_mint_id"),
						rs.getLong("slot_no"),
						toHexString(rs.getBytes("ma_policy_id")),
						toHexString(rs.getBytes("ma_name")),
						rs.getString("ma_fingerprint"),
						rs.getLong("quantity"),
						rs.getString("name"),
						rs.getString("image")),
				filterParams.toArray());
	}

	public List<TokenListItem> getAddressTokenList(String addr) throws DecoderException {

		String join;
		String where;
		if (addr.startsWith("stake")) {
			join = "join stake_address sa on sa.id=uv.stake_address_id ";
			where = "sa.\"view\"=? ";
		} else {
			join = "";
			where = "uv.address=? ";
		}

		String query = String.format("""
				select
					ma_policy_id
					,ma_name
					,ma_fingerprint
					,quantity
					,metadata->>'name' "name"
					,case
						WHEN jsonb_typeof(metadata->'image') = 'array'
						then (select string_agg(value, '') from jsonb_array_elements_text(metadata->'image'))
				    	ELSE metadata->>'image'
				  	END "image"
				from (
						select
							ma."policy" ma_policy_id
							,ma.name ma_name
							,max(ma.fingerprint) ma_fingerprint
							,sum(mto.quantity) quantity
							,(select
								coalesce(tm.json->encode(ma.policy::bytea, 'hex')->encode(ma.name::bytea, 'escape'), tm.json->encode(ma.policy::bytea, 'hex')->encode(ma.name::bytea, 'hex'))
								from ma_tx_mint mtm
								join tx_metadata tm on tm.tx_id=mtm.tx_id and tm."key"=721
								where mtm.ident=max(mto.ident) and mtm.quantity>0
								order by tm.id desc limit 1) metaData
						from utxo_view uv
						%s
						join tx_out txo on txo.tx_id = uv.tx_id and txo."index" = uv."index"
						join ma_tx_out mto on mto.tx_out_id=txo.id
						join multi_asset ma on ma.id=mto.ident
						where
							%s
						group by ma."policy", ma.name
						order by max(uv.id) desc
						) sub
				""", join, where);
		return jdbcTemplate.query(query,
				(rs, rowNum) -> new TokenListItem(
						null,
						null,
						toHexString(rs.getBytes("ma_policy_id")),
						toHexString(rs.getBytes("ma_name")),
						rs.getString("ma_fingerprint"),
						rs.getLong("quantity"),
						rs.getString("name"),
						rs.getString("image")),
				addr);
	}

	public TokenDetails getTokenDetails(String policyId, String assetName) throws DecoderException {
		try {
			return jdbcTemplate.queryForObject("""
					select
						b.slot_no
						,ma."policy" ma_policy_id
						,ma.name ma_name
						,ma.fingerprint
						,coalesce(tm.json->encode(ma.policy::bytea, 'hex')->encode(ma.name::bytea, 'escape'), tm.json->encode(ma.policy::bytea, 'hex')->encode(ma.name::bytea, 'hex')) metadata
						,script.json ma_policy_script
						,tx.hash tx_hash
						,(select sum(quantity) from ma_tx_mint mtm_total where mtm_total.ident = mtm.ident) total_supply
					from ma_tx_mint mtm
					join multi_asset ma on ma.id = mtm.ident
					join tx on tx.id = mtm.tx_id
					join block b on b.id = tx.block_id
					left join tx_metadata tm on tm.tx_id = tx.id and tm.key=721
					join script on script.hash=ma."policy"
					where
						ma."policy"=?
						and ma."name"=?
						and mtm.quantity>0
					order by mtm.id desc
					limit 1
					""",
					(rs, rowNum) -> new TokenDetails(
							rs.getLong("slot_no"),
							toHexString(rs.getBytes("ma_policy_id")),
							toHexString(rs.getBytes("ma_name")),
							rs.getString("fingerprint"),
							rs.getString("metadata"),
							rs.getString("ma_policy_script"),
							toHexString(rs.getBytes("tx_hash")),
							rs.getLong("total_supply")),
					Hex.decodeHex(policyId), Hex.decodeHex(assetName));
		} catch (EmptyResultDataAccessException e) {
			return null;
		}
	}

	public StakeInfo getStakeInfo(String stakeAddress) {
		try {
			return jdbcTemplate.queryForObject("""
					select
						(select sum(value) from utxo_view utxo where utxo.stake_address_id=d.addr_id) stake
						,(select view from pool_hash ph where ph.id=d.pool_hash_id order by id desc limit 1) pool_hash
						,(select ticker_name from pool_offline_data pod where pod.pool_id=d.pool_hash_id order by id desc limit 1) ticker_name
						,(select sum(amount) from epoch_stake es where es.pool_id=d.pool_hash_id group by es.epoch_no order by es.epoch_no desc limit 1) total_stake
					from delegation d
					join stake_address sa on sa.id=d.addr_id
					where sa."view"=?
					order by d.id desc
					limit 1
					""",
					(rs, rowNum) -> new StakeInfo(
							rs.getLong("stake"),
							rs.getString("pool_hash"),
							rs.getString("ticker_name"),
							rs.getLong("total_stake")),
					stakeAddress);
		} catch (EmptyResultDataAccessException e) {
			return null;
		}
	}

	public List<PoolInfo> getPoolList() {
		return jdbcTemplate.query("""
				select distinct pod.ticker_name, ph."view" pool_hash
				from pool_offline_data pod
				join pool_hash ph on ph.id=pod.pool_id
				order by pod.ticker_name
				""",
				(rs, rowNum) -> new PoolInfo(
						rs.getString("ticker_name"),
						rs.getString("pool_hash")));
	}

	public List<EpochStake> getEpochStake(String poolHash, int epoch) {
		return jdbcTemplate.query("""
				select
					sa."view" stake_address,
					es.amount
				from pool_hash ph
				join epoch_stake es on es.pool_id=ph.id
				join stake_address sa on sa.id=es.addr_id
				where
				ph.view=?
				and epoch_no=?
				""",
				(rs, rowNum) -> new EpochStake(
						rs.getString("stake_address"),
						rs.getLong("amount")),
				poolHash, epoch);
	}

	public List<OwnerInfo> getOwners(String policyId) throws DecoderException {
		return jdbcTemplate.query("""
					select * from ma_owners mo where mo.policy=?
				""",
				(rs, rowNum) -> {
					List<String> maNames = new ArrayList<>();
					ResultSet maNamesRs = rs.getArray("maNames").getResultSet();
					while (maNamesRs.next()) {
						maNames.add(maNamesRs.getString(2));
					}
					OwnerInfo ownerInfo = new OwnerInfo(
							rs.getString("address"),
							rs.getLong("quantity"),
							maNames);
					return ownerInfo;
				},
				Hex.decodeHex(policyId));
	}

	public List<StakeAddress> getHandles(String stakeAddress) throws DecoderException {
		return jdbcTemplate.query("""
					select ma.name assetName
					from stake_address sa
					join utxo_view uv on uv.stake_address_id=sa.id
					join tx_out to2 on to2.tx_id=uv.tx_id and to2."index"=uv."index"
					join ma_tx_out mto on mto.tx_out_id=to2.id
					join multi_asset ma on ma.id=mto.ident and ma."policy"=?
					where sa.view=?
				""",
				(rs, rowNum) -> new StakeAddress(new String(rs.getBytes("assetName"))),
				handlePolicyBytes, stakeAddress);
	}

	public StakeAddress getAddressByHandle(String handle) throws DecoderException {
		try {
			return jdbcTemplate.queryForObject("""
					select address from ma_owners mo
					where
					mo."policy"=?
					and ?=ANY(mo.manames)
					""",
					(rs, rowNum) -> new StakeAddress(rs.getString("address")),
					handlePolicyBytes, Hex.encodeHexString(handle.getBytes()));
		} catch (EmptyResultDataAccessException e) {
			return null;
		}
	}

	public String getTransactionMetadata(String txId) throws DecoderException {
		try {
			return jdbcTemplate.queryForObject("""
					select tm."json"
					from tx t
					join tx_metadata tm on tm.tx_id=t.id
					where t.hash=?
					""",
					(rs, rowNum) -> rs.getString(1), Hex.decodeHex(txId));
		} catch (EmptyResultDataAccessException e) {
			return null;
		}
	}

	public Boolean isTransactionConfirmed(String txId) throws DataAccessException, DecoderException {
		return jdbcTemplate.queryForObject("""
				select count(*) from tx where hash=?
				""",
				(rs, rowNum) -> rs.getBoolean(1), Hex.decodeHex(txId));
	}

	public Long getTip() {
		return jdbcTemplate.queryForObject("""
				select max(slot_no) from block
				""",
				(rs, rowNum) -> rs.getLong(1));
	}

	public List<TokenDetails> getLastMint(String stakeAddress, List<String> policyIds) {
		return jdbcTemplate.query("""
					with lastTransaction as (
						select t2.hash
						from ma_tx_mint mtm
						join multi_asset ma ON ma.id=mtm.ident
						join tx_out to2 on to2.tx_id=mtm.tx_id
						join tx t2 on t2.id=mtm.tx_id
						join stake_address sa on sa.id=to2.stake_address_id
						where sa.view=? AND ma."policy"=ANY(?)
						order by mtm.id desc
						limit 1
					)
					select
						b.slot_no
						,ma."policy" ma_policy_id
						,ma.name ma_name
						,ma.fingerprint
						,coalesce(tm.json->encode(ma.policy::bytea, 'hex')->encode(ma.name::bytea, 'escape'), tm.json->encode(ma.policy::bytea, 'hex')->encode(ma.name::bytea, 'hex')) metadata
						,script.json ma_policy_script
						,tx.hash tx_hash
						,(select sum(quantity) from ma_tx_mint mtm_total where mtm_total.ident = mtm.ident) total_supply
					from ma_tx_mint mtm
					join multi_asset ma ON ma.id=mtm.ident
					join tx on tx.id=mtm.tx_id
					join block b on b.id = tx.block_id
					left join tx_metadata tm on tm.tx_id = tx.id and tm."key"=721
					join script on script.hash=ma."policy"
					where tx.hash=(select hash from lastTransaction)
					order by ma.id desc
				""",
				(rs, rowNum) -> new TokenDetails(
						rs.getLong("slot_no"),
						toHexString(rs.getBytes("ma_policy_id")),
						toHexString(rs.getBytes("ma_name")),
						rs.getString("fingerprint"),
						rs.getString("metadata"),
						rs.getString("ma_policy_script"),
						toHexString(rs.getBytes("tx_hash")),
						rs.getLong("total_supply")),
				stakeAddress, policyIds.stream().map(policyId -> {
					try {
						return Hex.decodeHex(policyId);
					} catch (DecoderException e) {
						throw new RuntimeException(e);
					}
				}).toArray(byte[][]::new));
	}

	public List<AccountStatementRow> getStatement(String address) {
		if (address.startsWith("stake")) {
			return accountStatement(address);
		} else {
			return addressStatement(address);
		}
	}

	private List<AccountStatementRow> addressStatement(String address) {
		return jdbcTemplate.query("""
				select
					"time" "timestamp",
					min("epoch_no") epoch,
					min(encode(hash, 'hex')) tx_hash,
					sum("WITHDRAWN") withdrawn,
					sum("REWARDS") rewards,
					sum("OUT") "OUT",
					sum("IN") "IN",
					(sum("IN")-sum("OUT")-sum("WITHDRAWN")+sum("REWARDS")) "change",
					sum(sum("IN")-sum("OUT")-sum("WITHDRAWN")+sum("REWARDS")) over (order by min("time") asc, txId asc rows between unbounded preceding and current row),
					string_agg(distinct "TYPE", ',') operations
					from (
							-- normal input
							select
								t2.id txId,
								b2.time,
								b2.epoch_no,
								t2.hash,
								'IN' "TYPE",
								0 "OUT",
								to2.value "IN",
								0 "WITHDRAWN",
								0 "REWARDS"
							from tx_out to2
							join tx t2 on t2.id=to2.tx_id
							join block b2 on b2.id=t2.block_id
							where to2.address = ?
							union all
							-- normal output
							select
								t2.id txId,
								b2.time,
								b2.epoch_no,
								t2.hash,
								'OUT' "TYPE",
								to2.value "OUT",
								0 "IN",
								0 "WITHDRAWN",
								0 "REWARDS"
							from tx_in ti
							join tx t2 on t2.id=ti.tx_in_id
							join tx_out to2 on to2.tx_id=ti.tx_out_id and to2."index"=ti.tx_out_index
							join block b2 on b2.id=t2.block_id
							where to2.address = ?
				) movings
				group by "timestamp", txId
				order by "timestamp" desc, txId desc
				""",
				accountStatementRowMapper,
				address, address);
	}

	private List<AccountStatementRow> accountStatement(String stakeAddress) {
		return jdbcTemplate.query("""
				select
					"time" "timestamp",
					min("epoch_no") epoch,
					min(encode(hash, 'hex')) tx_hash,
					sum("WITHDRAWN") withdrawn,
					sum("REWARDS") rewards,
					sum("OUT") "OUT",
					sum("IN") "IN",
					(sum("IN")-sum("OUT")-sum("WITHDRAWN")+sum("REWARDS")) "change",
					sum(sum("IN")-sum("OUT")-sum("WITHDRAWN")+sum("REWARDS")) over (order by min("time") asc, txId asc rows between unbounded preceding and current row),
					string_agg(distinct "TYPE", ',') operations
					from (
							-- normal input
							select
								t2.id txId,
								b2.time,
								b2.epoch_no,
								t2.hash,
								'IN' "TYPE",
								0 "OUT",
								to2.value "IN",
								0 "WITHDRAWN",
								0 "REWARDS"
							from tx_out to2
							join tx t2 on t2.id=to2.tx_id
							join block b2 on b2.id=t2.block_id
							join stake_address sa on sa.id=to2.stake_address_id
							where sa."view" = ?
							union all
							-- normal output
							select
								t2.id txId,
								b2.time,
								b2.epoch_no,
								t2.hash,
								'OUT' "TYPE",
								to2.value "OUT",
								0 "IN",
								0 "WITHDRAWN",
								0 "REWARDS"
							from tx_in ti
							join tx t2 on t2.id=ti.tx_in_id
							join tx_out to2 on to2.tx_id=ti.tx_out_id and to2."index"=ti.tx_out_index
							join block b2 on b2.id=t2.block_id
							join stake_address sa on sa.id=to2.stake_address_id
							where sa."view" = ?
							union all
							-- withdrawn
							select
								t2.id txId,
								b2.time,
								b2.epoch_no,
								t2.hash,
								'WITHDRAW' "TYPE",
								0 "OUT",
								0 "IN",
								wi.amount "WITHDRAWN",
								0 "REWARDS"
							from withdrawal wi
							join tx t2 on t2.id=wi.tx_id
							join block b2 on b2.id=t2.block_id
							join stake_address sa on sa.id=wi.addr_id
							where sa."view" = ?
							union all
							-- generated reward
							select
								0 "txId",
								TO_TIMESTAMP(rw.earned_epoch * 432000 + 1506203091),
								rw.earned_epoch epoch_no,
								null hash,
								'REWARD_'||rw."type" "TYPE",
								0 "OUT",
								0 "IN",
								0 "WITHDRAWN",
								rw.amount "REWARDS"
							from reward rw
							join stake_address sa on sa.id=rw.addr_id
							where sa."view" = ?
				) movings
				group by "timestamp", txId
				order by "timestamp" desc, txId desc
				""",
				accountStatementRowMapper,
				stakeAddress, stakeAddress, stakeAddress, stakeAddress);
	}

	private RowMapper<AccountStatementRow> accountStatementRowMapper = (result, rowNum) -> new AccountStatementRow(
			result.getTimestamp("timestamp"),
			result.getInt("epoch"),
			result.getString("tx_hash"),
			result.getLong("withdrawn"),
			result.getLong("rewards"),
			result.getLong("OUT"),
			result.getLong("IN"),
			result.getLong("change"),
			result.getLong("sum"),
			result.getString("operations").split(","));

	private String toHexString(byte[] bytes) {
		return bytes == null ? null : Hex.encodeHexString(bytes);
	}

}
