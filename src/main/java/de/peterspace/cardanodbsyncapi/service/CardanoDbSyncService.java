package de.peterspace.cardanodbsyncapi.service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import de.peterspace.cardanodbsyncapi.dto.PoolInfo;
import de.peterspace.cardanodbsyncapi.dto.ReturnAddress;
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

	@PostConstruct
	public void init() {
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

		log.info("Indexes created");
	}

	public StakeInfo getStakeInfo(String addr) {
		try {
			return jdbcTemplate.queryForObject("""
					select
						(select sum(value) from utxo_view utxo where utxo.stake_address_id=txo.stake_address_id) stake
						,(select view from pool_hash ph where ph.id=d.pool_hash_id order by id desc limit 1) pool_hash
						,(select ticker_name from pool_offline_data pod where pod.pool_id=d.pool_hash_id order by id desc limit 1) ticker_name
						,(select sum(amount) from epoch_stake es where es.pool_id=d.pool_hash_id group by es.epoch_no order by es.epoch_no desc limit 1) total_stake
					from tx_out txo
					join delegation d on d.addr_id=txo.stake_address_id
					join stake_address sa on sa.id=txo.stake_address_id
					where txo.address=?
					order by d.slot_no desc
					limit 1
					""",
					(rs, rowNum) -> new StakeInfo(
							rs.getLong("stake"),
							rs.getString("pool_hash"),
							rs.getString("ticker_name"),
							rs.getLong("total_stake")),
					addr);
		} catch (EmptyResultDataAccessException e) {
			return null;
		}
	}

	public List<Utxo> getUtxos(String addr) {

		String join;
		String where;
		if (StringUtils.length(addr) == 59 && addr.startsWith("stake")) {
			join = "join stake_address sa on sa.id=uv.stake_address_id ";
			where = "sa.\"view\"=? ";
		} else {
			join = "";
			where = "uv.address=? ";
		}

		String query = String.format("""
				select
					encode(tx.hash::bytea, 'hex') tx_hash,
					uv."index" tx_index,
					null ma_policy_id,
					null ma_name,
					uv.value,
					(
						select coalesce(sa."view", txo.address)
						from tx_in ti
						join tx_out txo on txo.tx_id = ti.tx_out_id and txo."index" = ti.tx_out_index
						left join stake_address sa on sa.id=txo.stake_address_id
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
					encode(tx.hash::bytea, 'hex'),
					uv."index",
					ma."policy",
					ma."name",
					mto.quantity,
					(
						select coalesce(sa."view", txo.address)
						from tx_in ti
						join tx_out txo on txo.tx_id = ti.tx_out_id and txo."index" = ti.tx_out_index
						left join stake_address sa on sa.id=txo.stake_address_id
						where ti.tx_in_id = uv.tx_id
						limit 1
					) source_address0
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
						rs.getString("tx_hash"),
						rs.getInt("tx_index"),
						toHexString(rs.getBytes("ma_policy_id")),
						toHexString(rs.getBytes("ma_name")),
						rs.getLong("value"),
						rs.getString("source_address")),
				addr, addr);
	}

	public ReturnAddress getReturnAddress(String addr) {
		// if address has no stake part, just return it
		if (StringUtils.length(addr) == 58) {
			return new ReturnAddress(addr);
		}
		try {
			return jdbcTemplate.queryForObject("""
					select txo_stake.address, txo_stake.id
					from tx_out txo
					join tx_out txo_stake on txo_stake.stake_address_id=txo.stake_address_id
					where txo.address=?
					union
					select txo_stake.address, txo_stake.id
					from stake_address sa
					join tx_out txo_stake on txo_stake.stake_address_id=sa.id
					where sa."view"=?
					order by id
					limit 1
					""",
					(rs, rowNum) -> new ReturnAddress(rs.getString("address")), addr, addr);
		} catch (EmptyResultDataAccessException e) {
			return null;
		}
	}

	public List<TokenListItem> getTokenList(Long afterMintid, Long beforeMintid, String filter) throws DecoderException {

		List<String> filters = new ArrayList<String>();
		List<Object> filterParams = new ArrayList<Object>();

		if (afterMintid != null) {
			filters.add("and mtm.id > ?");
			filterParams.add(afterMintid);
		}

		if (beforeMintid != null) {
			filters.add("and mtm.id < ?");
			filterParams.add(beforeMintid);
		}

		if (!StringUtils.isBlank(filter)) {
			filter = filter.trim();
			String[] bits = filter.split("\\.");
			if (bits.length == 2 && bits[0].length() == 56) {
				filters.add("and ma.policy=? and ma.name=?");
				filterParams.add(Hex.decodeHex(bits[0]));
				filterParams.add(bits[1].getBytes(StandardCharsets.UTF_8));
			} else if (bits.length == 1 && bits[0].length() == 56) {
				filters.add("and ma.policy=?");
				filterParams.add(Hex.decodeHex(bits[0]));
			} else if (bits[0].length() == 44 && bits[0].startsWith("asset")) {
				filters.add("and ma.fingerprint=?");
				filterParams.add(bits[0]);
			}
		}

		return jdbcTemplate.query("""
				select
					mtm.id ma_mint_id
					,b.slot_no
					,ma."policy" ma_policy_id
					,ma.name ma_name
					,mtm.quantity
				from ma_tx_mint mtm
				join multi_asset ma on ma.id = mtm.ident
				join tx on tx.id = mtm.tx_id
				join block b on b.id = tx.block_id
				left join tx_metadata tm on tm.tx_id = tx.id and tm.key=721
				where
					coalesce(tm.json->encode(ma.policy::bytea, 'hex')->encode(ma.name::bytea, 'escape'), tm.json->encode(ma.policy::bytea, 'hex')->encode(ma.name::bytea, 'hex')) is not null
					and mtm.quantity>0
					""" + StringUtils.join(filters, " ") + """
				order by mtm.id desc
				limit 100
				""",
				(rs, rowNum) -> new TokenListItem(
						rs.getLong("ma_mint_id"),
						rs.getLong("slot_no"),
						toHexString(rs.getBytes("ma_policy_id")),
						toHexString(rs.getBytes("ma_name")),
						rs.getLong("slot_no")),
				filterParams.toArray());
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
							rs.getString("tx_hash"),
							rs.getLong("total_supply")),
					Hex.decodeHex(policyId), Hex.decodeHex(assetName));
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

	private String toHexString(byte[] bytes) {
		return bytes == null ? null : Hex.encodeHexString(bytes);
	}

}
