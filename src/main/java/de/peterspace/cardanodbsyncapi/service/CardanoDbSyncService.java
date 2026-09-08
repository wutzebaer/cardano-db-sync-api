package de.peterspace.cardanodbsyncapi.service;

import de.peterspace.cardano.javalib.CardanoUtils;
import de.peterspace.cardano.javalib.CardanoUtils.AddressType;
import de.peterspace.cardanodbsyncapi.config.TrackExecutionTime;
import de.peterspace.cardanodbsyncapi.dto.AccountStatementRow;
import de.peterspace.cardanodbsyncapi.dto.EpochStake;
import de.peterspace.cardanodbsyncapi.dto.LiquidityPool;
import de.peterspace.cardanodbsyncapi.dto.OwnerInfo;
import de.peterspace.cardanodbsyncapi.dto.PoolInfo;
import de.peterspace.cardanodbsyncapi.dto.ReturnAddress;
import de.peterspace.cardanodbsyncapi.dto.StakeAddress;
import de.peterspace.cardanodbsyncapi.dto.StakeInfo;
import de.peterspace.cardanodbsyncapi.dto.TokenDetails;
import de.peterspace.cardanodbsyncapi.dto.TokenListItem;
import de.peterspace.cardanodbsyncapi.dto.TxOut;
import de.peterspace.cardanodbsyncapi.dto.Utxo;
import jakarta.annotation.PostConstruct;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class CardanoDbSyncService {
  private final JdbcTemplate jdbcTemplate;
  private byte[] handlePolicyBytes;

  @PostConstruct
  public void init() throws DecoderException {
    handlePolicyBytes = Hex.decodeHex("f0ff48bbb7bbe9d59a40f1ce90e9e9d0ff5002ec48f232b49ca0fb9a");

    log.info("Creating index ct_idx_ma_tx_mint_ident");
    jdbcTemplate.execute(
        "CREATE INDEX IF NOT EXISTS ct_idx_ma_tx_mint_ident ON ma_tx_mint USING btree (ident);");

    log.info("Creating index ct_idx_multi_asset_fingerprint");
    jdbcTemplate.execute(
        "CREATE INDEX IF NOT EXISTS ct_idx_multi_asset_fingerprint ON multi_asset USING btree (fingerprint);");

    log.info("Creating index ct_tx_metadata_tx_id_key_index");
    jdbcTemplate.execute(
        "CREATE INDEX IF NOT EXISTS ct_tx_metadata_tx_id_key_index ON tx_metadata USING btree (tx_id, key);");

    log.info("Creating index ct_idx_tx_out_unspent_payment_cred");
    jdbcTemplate.execute(
        "CREATE INDEX IF NOT EXISTS ct_idx_tx_out_unspent_payment_cred ON tx_out (payment_cred) WHERE consumed_by_tx_id IS NULL;");

    log.info("Creating index ct_idx_tx_out_unspent_stake_address_id");
    jdbcTemplate.execute(
        "CREATE INDEX IF NOT EXISTS ct_idx_tx_out_unspent_stake_address_id ON tx_out (stake_address_id) WHERE consumed_by_tx_id IS NULL;");

    log.info("Creating index ct_idx_ma_tx_out_ident");
    jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS ct_idx_ma_tx_out_ident ON ma_tx_out (ident);");

    log.info("Creating index ct_idx_epoch_stake_pool_id_epoch_no");
    jdbcTemplate.execute(
        "CREATE INDEX IF NOT EXISTS ct_idx_epoch_stake_pool_id_epoch_no ON epoch_stake (pool_id, epoch_no);");

    log.info("Creating index ct_idx_pool_hash_view");
    jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS ct_idx_pool_hash_view ON pool_hash (view);");

    log.info("Creating materialized view ct_ma_owners");
    if (materializedViewNeedsRebuild("ct_ma_owners")) {
      jdbcTemplate.execute("DROP MATERIALIZED VIEW IF EXISTS ct_ma_owners CASCADE;");
      jdbcTemplate.execute(
          """
					CREATE MATERIALIZED VIEW ct_ma_owners AS
					select
						coalesce(sa."view" , txo.address) address
						,sum(mto.quantity) quantity
						,ma."policy" "policy"
						,array_agg(distinct encode(ma."name", 'hex'))  maNames
					from multi_asset ma
					join ma_tx_out mto on ma.id=mto.ident
					join tx_out txo on txo.id=mto.tx_out_id
					left join stake_address sa on sa.id=txo.stake_address_id
					where
					txo.consumed_by_tx_id is null
					group by ma."policy", coalesce(sa."view" , txo.address)
					WITH NO DATA;
					""");
    }

    log.info("Creating index ct_ma_owners_address_policy");
    jdbcTemplate.execute(
        "CREATE UNIQUE INDEX IF NOT EXISTS ct_ma_owners_address_policy ON ct_ma_owners (address, policy);");

    log.info("Creating index ct_idx_ma_owners_policy");
    jdbcTemplate.execute(
        "CREATE INDEX IF NOT EXISTS ct_idx_ma_owners_policy ON ct_ma_owners (policy);");

    log.info("Creating materialized view ct_minswap_pools");
    if (materializedViewNeedsRebuild("ct_minswap_pools")) {
      jdbcTemplate.execute("DROP MATERIALIZED VIEW IF EXISTS ct_minswap_pools CASCADE;");
      jdbcTemplate.execute(
          """
					CREATE MATERIALIZED VIEW ct_minswap_pools AS
						select
							ma_a."policy" policy_a,
							ma_a."name" name_a,
							mto_a.quantity quantity_a,
							ma_b."policy" policy_b,
							ma_b."name" name_b,
							coalesce(mto_b.quantity, txo.value) quantity_b
						from tx_out txo
						join ma_tx_out mto_a on mto_a.tx_out_id=txo.id
						join multi_asset ma_a on ma_a.id=mto_a.ident and ma_a."policy" != decode('f5808c2c990d86da54bfc97d89cee6efa20cd8461616359478d96b4c', 'hex')
						left join (ma_tx_out mto_b join multi_asset ma_b on ma_b.id = mto_b.ident and ma_b."policy" != decode('f5808c2c990d86da54bfc97d89cee6efa20cd8461616359478d96b4c', 'hex')) on mto_b.tx_out_id = txo.id and mto_b.id != mto_a.id
						where
							txo.consumed_by_tx_id is null
							and txo.payment_cred = decode('ea07b733d932129c378af627436e7cbc2ef0bf96e0036bb51b3bde6b', 'hex')
					WITH NO DATA;
				""");
    }

    log.info("Creating index ct_minswap_pools_idx");
    jdbcTemplate.execute(
        "CREATE INDEX IF NOT EXISTS ct_minswap_pools_idx ON ct_minswap_pools (policy_a, name_a);");

    log.info("Indexes created");
  }

  private boolean materializedViewNeedsRebuild(String viewName) {
    try {
      String def =
          jdbcTemplate.queryForObject(
              "select pg_get_viewdef(to_regclass('public.' || ?), true)", String.class, viewName);
      return def == null || !def.contains("consumed_by_tx_id");
    } catch (DataAccessException e) {
      return true;
    }
  }

  @TrackExecutionTime
  @Scheduled(cron = "0 0 0/12 * * *")
  public void updateOwnerView() {
    refreshMaterializedView("ct_ma_owners");
  }

  @TrackExecutionTime
  @Scheduled(cron = "0 0 * * * *")
  public void updateMinswapView() {
    refreshMaterializedView("ct_minswap_pools");
  }

  private void refreshMaterializedView(String viewName) {
    log.info("Refreshing {}", viewName);
    if (isMaterializedViewPopulated(viewName)) {
      jdbcTemplate.execute("REFRESH MATERIALIZED VIEW CONCURRENTLY " + viewName);
    } else {
      jdbcTemplate.execute("REFRESH MATERIALIZED VIEW " + viewName);
    }
  }

  public List<Utxo> getUtxos(String addr) throws DecoderException {

    AddressType addressType = CardanoUtils.determineAddressType(addr);

    String join;
    String where;
    byte[] hash;
    if (addressType == AddressType.STAKE_ADDRESS) {
      String stakeHash = CardanoUtils.stakeToHash(addr);
      hash = Hex.decodeHex(stakeHash);
      join = "join stake_address sa on sa.id=txo.stake_address_id ";
      where = "sa.hash_raw=? ";
    } else {
      String paymentHash = CardanoUtils.extractPaymentHash(addr);
      hash = Hex.decodeHex(paymentHash);
      join = "";
      where = "txo.payment_cred=? ";
    }

    String query =
        String.format(
            """
				select
					tx.hash tx_hash,
					txo."index" tx_index,
					null ma_policy_id,
					null ma_name,
					txo.value,
					txo.address owning_address,
					(
						select src.address
						from tx_out src
						where src.consumed_by_tx_id = txo.tx_id
						limit 1
					) source_address
				from tx_out txo
				%s
				join tx on tx.id = txo.tx_id
				where
					txo.consumed_by_tx_id is null
					and %s
				union
				select
					tx.hash,
					txo."index",
					ma."policy",
					ma."name",
					mto.quantity,
					txo.address owning_address,
					(
						select src.address
						from tx_out src
						where src.consumed_by_tx_id = txo.tx_id
						limit 1
					) source_address
				from tx_out txo
				%s
				join tx on tx.id = txo.tx_id
				join ma_tx_out mto on mto.tx_out_id=txo.id
				join multi_asset ma on ma.id=mto.ident
				where
					txo.consumed_by_tx_id is null
					and %s
				""",
            join, where, join, where);
    return jdbcTemplate.query(
        query,
        (rs, rowNum) ->
            new Utxo(
                Hex.encodeHexString(rs.getBytes("tx_hash")),
                rs.getInt("tx_index"),
                toHexString(rs.getBytes("ma_policy_id")),
                toHexString(rs.getBytes("ma_name")),
                rs.getLong("value"),
                rs.getString("owning_address"),
                rs.getString("source_address")),
        hash,
        hash);
  }

  public List<LiquidityPool> getMinswapPools(String policyId, String assetName)
      throws DecoderException {
    if (!isMaterializedViewPopulated("ct_minswap_pools")) {
      return List.of();
    }
    String query =
        """
				select policy_a, name_a, quantity_a, policy_b, name_b, quantity_b
				from ct_minswap_pools
				where policy_a=? and name_a=?
				""";
    return jdbcTemplate.query(
        query,
        (rs, rowNum) ->
            new LiquidityPool(
                toHexString(rs.getBytes("policy_a")),
                toHexString(rs.getBytes("name_a")),
                rs.getLong("quantity_a"),
                toHexString(rs.getBytes("policy_b")),
                toHexString(rs.getBytes("name_b")),
                rs.getLong("quantity_b")),
        Hex.decodeHex(policyId),
        Hex.decodeHex(assetName));
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

      return jdbcTemplate.queryForObject(
          """
					select txo.address
					from stake_address sa
					join tx_out txo on txo.stake_address_id=sa.id
					where sa."view"=?
					order by txo.id
					limit 1
					""",
          (rs, rowNum) -> new ReturnAddress(rs.getString("address")),
          stakeAddress);
    } catch (EmptyResultDataAccessException e) {
      return null;
    }
  }

  public StakeAddress getStakeAddress(String address) {
    try {
      byte[] paymentCred = Hex.decodeHex(CardanoUtils.extractPaymentHash(address));
      return jdbcTemplate.queryForObject(
          """
					select sa."view" stakeAddress from
					tx_out txo
					join stake_address sa on sa.id=txo.stake_address_id
					where txo.payment_cred=? and txo.address=?
					limit 1
					""",
          (rs, rowNum) -> new StakeAddress(rs.getString("stakeAddress")),
          paymentCred,
          address);
    } catch (EmptyResultDataAccessException | DecoderException e) {
      return null;
    }
  }

  public StakeAddress getStakeAddressByHash(String stakeAddressHash)
      throws DataAccessException, DecoderException {
    try {
      return jdbcTemplate.queryForObject(
          """
					select view stakeAddress from stake_address sa where sa.hash_raw=?;
					""",
          (rs, rowNum) -> new StakeAddress(rs.getString("stakeAddress")),
          Hex.decodeHex(stakeAddressHash));
    } catch (EmptyResultDataAccessException e) {
      return null;
    }
  }

  public StakeAddress getStakeHashByAddress(String stakeAddress)
      throws DataAccessException, DecoderException {
    try {
      return jdbcTemplate.queryForObject(
          """
					select hash_raw hash from stake_address sa where sa.view=?;
					""",
          (rs, rowNum) -> new StakeAddress(Hex.encodeHexString(rs.getBytes("hash"))),
          stakeAddress);
    } catch (EmptyResultDataAccessException e) {
      return null;
    }
  }

  public List<TokenListItem> getTokenList(Long afterMintid, Long beforeMintid, String filter)
      throws DecoderException {

    List<String> filters = new ArrayList<String>();
    List<Object> filterParams = new ArrayList<Object>();

    if (!StringUtils.isBlank(filter)) {
      filter = filter.trim();
      String[] bits = filter.split("\\.");
      if (bits.length == 2 && bits[0].length() == 56) {
        filters.add("and ma.\"policy\"=? and ma.name=?");
        filterParams.add(Hex.decodeHex(bits[0]));
        filterParams.add(Hex.decodeHex(bits[1]));
      } else if (bits.length == 1 && bits[0].length() == 56) {
        filters.add("and ma.\"policy\"=?");
        filterParams.add(Hex.decodeHex(bits[0]));
      } else if (bits[0].length() == 44 && bits[0].startsWith("asset")) {
        filters.add("and ma.fingerprint=?");
        filterParams.add(bits[0]);
      } else {
        return List.of();
      }
    }

    if (afterMintid != null) {
      filters.add("and mtm.id > ?");
      filterParams.add(afterMintid);
    }

    if (beforeMintid != null) {
      filters.add("and mtm.id < ?");
      filterParams.add(beforeMintid);
    }

    String metadataFilter =
        StringUtils.isBlank(filter)
            ? "and coalesce(tm.json->encode(ma.policy::bytea, 'hex')->encode(ma.name::bytea, 'escape'), tm.json->encode(ma.policy::bytea, 'hex')->encode(ma.name::bytea, 'hex')) is not null "
            : "";

    return jdbcTemplate.query(
        """
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
							where true
							"""
            + metadataFilter
            + " "
            + StringUtils.join(filters, " ")
            + """
							
							order by mtm.id desc
							limit 100
							) sub
							""",
        (rs, rowNum) ->
            new TokenListItem(
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

    AddressType addressType = CardanoUtils.determineAddressType(addr);

    String join;
    String where;
    byte[] hash;
    if (addressType == AddressType.STAKE_ADDRESS) {
      hash = Hex.decodeHex(CardanoUtils.stakeToHash(addr));
      join = "join stake_address sa on sa.id=txo.stake_address_id ";
      where = "sa.hash_raw=? ";
    } else {
      hash = Hex.decodeHex(CardanoUtils.extractPaymentHash(addr));
      join = "";
      where = "txo.payment_cred=? ";
    }

    String query =
        String.format(
            """
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
								from tx_out txo
								%s
								join ma_tx_out mto on mto.tx_out_id=txo.id
								join multi_asset ma on ma.id=mto.ident
								where
									txo.consumed_by_tx_id is null
									and %s
								group by ma."policy", ma.name
								order by max(txo.id) desc
								) sub
						""",
            join, where);
    return jdbcTemplate.query(
        query,
        (rs, rowNum) ->
            new TokenListItem(
                null,
                null,
                toHexString(rs.getBytes("ma_policy_id")),
                toHexString(rs.getBytes("ma_name")),
                rs.getString("ma_fingerprint"),
                rs.getLong("quantity"),
                rs.getString("name"),
                rs.getString("image")),
        hash);
  }

  public TokenDetails getTokenDetails(String policyId, String assetName) throws DecoderException {
    return getTokenDetails(policyId, assetName, null);
  }

  public TokenDetails getTokenDetails(String fingerprint) throws DecoderException {
    return getTokenDetails(null, null, fingerprint);
  }

  private TokenDetails getTokenDetails(String policyId, String assetName, String fingerprint)
      throws DecoderException {
    String query =
        """
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
					%s
					and mtm.quantity>0
				order by mtm.id desc
				limit 1
				""";
    String whereClause;
    Object[] params;

    if (policyId != null && assetName != null) {
      whereClause = "ma.\"policy\"=? and ma.\"name\"=?";
      params =
          new Object[] {Hex.decodeHex(policyId), Hex.decodeHex(StringUtils.trimToEmpty(assetName))};
    } else if (fingerprint != null) {
      whereClause = "ma.\"fingerprint\"=?";
      params = new Object[] {fingerprint};
    } else {
      throw new IllegalArgumentException(
          "Either policyId and assetName or fingerprint must be provided");
    }

    try {
      return jdbcTemplate.queryForObject(
          String.format(query, whereClause),
          (rs, rowNum) ->
              new TokenDetails(
                  rs.getLong("slot_no"),
                  toHexString(rs.getBytes("ma_policy_id")),
                  toHexString(rs.getBytes("ma_name")),
                  rs.getString("fingerprint"),
                  rs.getString("metadata"),
                  rs.getString("ma_policy_script"),
                  toHexString(rs.getBytes("tx_hash")),
                  rs.getLong("total_supply")),
          params);
    } catch (EmptyResultDataAccessException e) {
      return null;
    }
  }

  public StakeInfo getStakeInfo(String stakeAddress) {
    try {
      return jdbcTemplate.queryForObject(
          """
							select
								(select sum(value) from tx_out utxo where utxo.stake_address_id=d.addr_id and utxo.consumed_by_tx_id is null) stake
								,(select view from pool_hash ph where ph.id=d.pool_hash_id order by id desc limit 1) pool_hash
								,(select ticker_name from off_chain_pool_data pod where pod.pool_id=d.pool_hash_id order by id desc limit 1) ticker_name
								,(select sum(amount) from epoch_stake es where es.pool_id=d.pool_hash_id and es.epoch_no = (select max(epoch_no) from epoch_stake es2 where es2.pool_id=d.pool_hash_id)) total_stake
							from delegation d
							join stake_address sa on sa.id=d.addr_id
							where sa."view"=?
							order by d.id desc
							limit 1
							""",
          (rs, rowNum) ->
              new StakeInfo(
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
    return jdbcTemplate.query(
        """
				select distinct pod.ticker_name, ph."view" pool_hash
				from off_chain_pool_data pod
				join pool_hash ph on ph.id=pod.pool_id
				order by pod.ticker_name
				""",
        (rs, rowNum) -> new PoolInfo(rs.getString("ticker_name"), rs.getString("pool_hash")));
  }

  public List<EpochStake> getEpochStake(String poolHash, int epoch) {
    return jdbcTemplate.query(
        """
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
        (rs, rowNum) -> new EpochStake(rs.getString("stake_address"), rs.getLong("amount")),
        poolHash,
        epoch);
  }

  public List<OwnerInfo> getOwners(String policyId) throws DecoderException {
    if (!isMaterializedViewPopulated("ct_ma_owners")) {
      return List.of();
    }
    return jdbcTemplate.query(
        """
					select * from ct_ma_owners mo where mo.policy=?
				""",
        (rs, rowNum) -> {
          List<String> maNames = new ArrayList<>();
          ResultSet maNamesRs = rs.getArray("maNames").getResultSet();
          while (maNamesRs.next()) {
            maNames.add(maNamesRs.getString(2));
          }
          OwnerInfo ownerInfo =
              new OwnerInfo(rs.getString("address"), rs.getLong("quantity"), maNames);
          return ownerInfo;
        },
        Hex.decodeHex(policyId));
  }

  public List<StakeAddress> getHandles(String stakeAddress) throws DecoderException {
    return jdbcTemplate.query(
        """
					select ma.name assetName
					from stake_address sa
					join tx_out txo on txo.stake_address_id=sa.id and txo.consumed_by_tx_id is null
					join ma_tx_out mto on mto.tx_out_id=txo.id
					join multi_asset ma on ma.id=mto.ident and ma."policy"=?
					where sa.view=?
				""",
        (rs, rowNum) -> new StakeAddress(new String(rs.getBytes("assetName"))),
        handlePolicyBytes,
        stakeAddress);
  }

  public StakeAddress getAddressByHandle(String handle) throws DecoderException {
    try {
      return jdbcTemplate.queryForObject(
          """
					select coalesce(sa.view, txo.address) address
					from multi_asset ma
					join ma_tx_out mto on mto.ident = ma.id
					join tx_out txo on txo.id = mto.tx_out_id and txo.consumed_by_tx_id is null
					left join stake_address sa on sa.id = txo.stake_address_id
					where
					ma."policy"=?
					and ma.name=?
					limit 1
					""",
          (rs, rowNum) -> new StakeAddress(rs.getString("address")),
          handlePolicyBytes,
          handle.getBytes());
    } catch (EmptyResultDataAccessException e) {
      return null;
    }
  }

  public String getTransactionMetadata(String txId) throws DecoderException {
    try {
      return jdbcTemplate.queryForObject(
          """
					select json_agg(tm."json" order by tm.key)
					from tx t
					join tx_metadata tm on tm.tx_id=t.id
					where t.hash=?
					""",
          (rs, rowNum) -> rs.getString(1),
          Hex.decodeHex(txId));
    } catch (EmptyResultDataAccessException e) {
      return null;
    }
  }

  public List<TxOut> getTransactionOutputs(String txId) throws DecoderException {
    try {
      return jdbcTemplate.query(
          """
					select txo.address, txo.value
					from tx t
					join tx_out txo on txo.tx_id=t.id
					where t.hash=?
					""",
          (rs, rowNum) -> new TxOut(rs.getString("address"), rs.getLong("value")),
          Hex.decodeHex(txId));
    } catch (EmptyResultDataAccessException e) {
      return null;
    }
  }

  public Boolean isTransactionConfirmed(String txId) throws DataAccessException, DecoderException {
    return jdbcTemplate.queryForObject(
        """
				select count(*) from tx where hash=?
				""",
        (rs, rowNum) -> rs.getBoolean(1),
        Hex.decodeHex(txId));
  }

  public Long getTip() {
    return jdbcTemplate.queryForObject(
        """
				select max(slot_no) from block
				""",
        (rs, rowNum) -> rs.getLong(1));
  }

  public List<TokenDetails> getLastMint(String stakeAddress, List<String> policyIds) {
    return jdbcTemplate.query(
        """
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
        (rs, rowNum) ->
            new TokenDetails(
                rs.getLong("slot_no"),
                toHexString(rs.getBytes("ma_policy_id")),
                toHexString(rs.getBytes("ma_name")),
                rs.getString("fingerprint"),
                rs.getString("metadata"),
                rs.getString("ma_policy_script"),
                toHexString(rs.getBytes("tx_hash")),
                rs.getLong("total_supply")),
        stakeAddress,
        policyIds.stream()
            .map(
                policyId -> {
                  try {
                    return Hex.decodeHex(policyId);
                  } catch (DecoderException e) {
                    throw new RuntimeException(e);
                  }
                })
            .toArray(byte[][]::new));
  }

  public List<AccountStatementRow> getStatement(String address) throws DecoderException {
    if (address.startsWith("stake")) {
      return accountStatement(address);
    } else {
      return addressStatement(address);
    }
  }

  private List<AccountStatementRow> addressStatement(String address) throws DecoderException {
    byte[] paymentCred = Hex.decodeHex(CardanoUtils.extractPaymentHash(address));
    return jdbcTemplate.query(
        """
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
									where to2.payment_cred = ?
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
									from tx_out to2
									join tx t2 on t2.id=to2.consumed_by_tx_id
									join block b2 on b2.id=t2.block_id
									where to2.payment_cred = ?
						) movings
						group by "timestamp", txId
						order by "timestamp" desc, txId desc
						""",
        accountStatementRowMapper,
        paymentCred,
        paymentCred);
  }

  private List<AccountStatementRow> accountStatement(String stakeAddress) {
    return jdbcTemplate.query(
        """
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
									from tx_out to2
									join tx t2 on t2.id=to2.consumed_by_tx_id
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
        stakeAddress,
        stakeAddress,
        stakeAddress,
        stakeAddress);
  }

  private RowMapper<AccountStatementRow> accountStatementRowMapper =
      (result, rowNum) ->
          new AccountStatementRow(
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

  private boolean isMaterializedViewPopulated(String viewName) {
    Boolean populated =
        jdbcTemplate.queryForObject(
            "select relispopulated from pg_class where relname = ?", Boolean.class, viewName);
    return Boolean.TRUE.equals(populated);
  }

  private String toHexString(byte[] bytes) {
    return bytes == null ? null : Hex.encodeHexString(bytes);
  }
}
