package de.peterspace.cardanodbsyncapi.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
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
import de.peterspace.cardanodbsyncapi.dto.StakeHandle;
import de.peterspace.cardanodbsyncapi.dto.StakeInfo;
import de.peterspace.cardanodbsyncapi.dto.StakeReturnAddress;
import de.peterspace.cardanodbsyncapi.dto.PolicyRoyalties;
import de.peterspace.cardanodbsyncapi.dto.TokenDetails;
import de.peterspace.cardanodbsyncapi.dto.TokenListItem;
import de.peterspace.cardanodbsyncapi.dto.TxOut;
import de.peterspace.cardanodbsyncapi.dto.Utxo;
import jakarta.annotation.PostConstruct;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
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
  private final ObjectMapper objectMapper = JsonMapper.builder().build();
  private byte[] handlePolicyBytes;

  /** Policy of ADA Handle, differs per network. Default is mainnet. */
  @Value("${cardano.handle-policy:f0ff48bbb7bbe9d59a40f1ce90e9e9d0ff5002ec48f232b49ca0fb9a}")
  private String handlePolicy;

  @PostConstruct
  public void init() throws DecoderException {
    handlePolicyBytes = Hex.decodeHex(handlePolicy);

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

    log.info("Creating function ct_cip68_metadata");
    jdbcTemplate.execute(
        """
					CREATE OR REPLACE FUNCTION ct_cip68_metadata(p_policy bytea, p_name bytea)
					RETURNS jsonb
					LANGUAGE plpgsql STABLE AS $$
					BEGIN
					  IF p_name IS NULL OR length(p_name) < 4
					     OR substring(p_name from 1 for 4) NOT IN (
					       decode('000de140', 'hex'),
					       decode('0014df10', 'hex'),
					       decode('001bc280', 'hex')
					     ) THEN
					    RETURN NULL;
					  END IF;
					  RETURN (
					    SELECT d.value->'fields'->0
					    FROM multi_asset ref
					    JOIN ma_tx_out mto ON mto.ident = ref.id
					    JOIN tx_out txo ON txo.id = mto.tx_out_id AND txo.consumed_by_tx_id IS NULL
					    JOIN datum d ON d.hash = txo.data_hash
					    WHERE ref.policy = p_policy
					      AND ref.name = decode('000643b0', 'hex') || substring(p_name from 5)
					    LIMIT 1
					  );
					END;
					$$;
					""");
    jdbcTemplate.execute("DROP FUNCTION IF EXISTS ct_from_plutus(jsonb);");

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
    // id_a and id_b identify a row, which a refresh without locking out readers needs
    if (materializedViewNeedsRebuild("ct_minswap_pools", "id_a")) {
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
							coalesce(mto_b.quantity, txo.value) quantity_b,
							mto_a.id id_a,
							coalesce(mto_b.id, 0) id_b
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

    log.info("Creating index ct_minswap_pools_key");
    jdbcTemplate.execute(
        "CREATE UNIQUE INDEX IF NOT EXISTS ct_minswap_pools_key ON ct_minswap_pools (id_a, id_b);");

    log.info("Indexes created");
  }

  /** A view is rebuilt when it is missing or its definition lacks one of the given parts. */
  private boolean materializedViewNeedsRebuild(String viewName, String... requiredParts) {
    try {
      String def =
          jdbcTemplate.queryForObject(
              "select pg_get_viewdef(to_regclass('public.' || ?), true)", String.class, viewName);
      if (def == null || !def.contains("consumed_by_tx_id")) {
        return true;
      }
      for (String part : requiredParts) {
        if (!def.contains(part)) {
          return true;
        }
      }
      return false;
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
					-- "+ 0" keeps the planner from walking the whole primary key to find the first row
					order by txo.id + 0
					limit 1
					""",
          (rs, rowNum) -> new ReturnAddress(rs.getString("address")),
          stakeAddress);
    } catch (EmptyResultDataAccessException e) {
      return null;
    }
  }

  public List<StakeReturnAddress> getReturnAddresses(List<String> stakeAddresses) {
    if (stakeAddresses.isEmpty()) {
      return List.of();
    }
    return jdbcTemplate.query(
        """
					select sa."view" stake_address, first_out.address
					from stake_address sa
					join lateral (
						select txo.address
						from tx_out txo
						where txo.stake_address_id=sa.id
						-- "+ 0" keeps the planner from walking the whole primary key to find the first row
						order by txo.id + 0
						limit 1
					) first_out on true
					where sa."view" = any(?)
					""",
        (rs, rowNum) ->
            new StakeReturnAddress(rs.getString("stake_address"), rs.getString("address")),
        (Object) stakeAddresses.toArray(String[]::new));
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

  public List<TokenListItem> getTokenFeed(Long afterMintid, Long beforeMintid) {
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

    return jdbcTemplate.query(
        """
						select
							mtm.id ma_mint_id
							,b.slot_no
							,ma."policy" ma_policy_id
							,ma.name ma_name
							,ma.fingerprint ma_fingerprint
							,mtm.quantity
							,c68.cip68
							,coalesce(tm.json->encode(ma.policy::bytea, 'hex')->encode(ma.name::bytea, 'escape'), tm.json->encode(ma.policy::bytea, 'hex')->encode(ma.name::bytea, 'hex')) cip25
						from ma_tx_mint mtm
						join multi_asset ma on ma.id = mtm.ident
						join tx on tx.id = mtm.tx_id
						join block b on b.id = tx.block_id
						left join tx_metadata tm on tm.tx_id = tx.id and tm.key=721
						left join lateral (select ct_cip68_metadata(ma."policy", ma.name) cip68) c68 on true
						where true
						and mtm.quantity > 0
						and coalesce(c68.cip68, tm.json->encode(ma.policy::bytea, 'hex')->encode(ma.name::bytea, 'escape'), tm.json->encode(ma.policy::bytea, 'hex')->encode(ma.name::bytea, 'hex')) is not null
						"""
            + StringUtils.join(filters, " ")
            + """

						order by mtm.id desc
						limit 100
						""",
        (rs, rowNum) -> toTokenListItem(rs, rs.getLong("ma_mint_id"), rs.getLong("slot_no")),
        filterParams.toArray());
  }

  public List<TokenListItem> getTokenSearch(String filter, String after) throws DecoderException {
    List<String> filters = new ArrayList<String>();
    List<Object> filterParams = new ArrayList<Object>();

    if (StringUtils.isBlank(filter)) {
      return List.of();
    }
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
    } else if (filter.startsWith("$") && filter.length() > 1) {
      byte[] handleName =
          filter.substring(1).toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);
      byte[] cip68Name = new byte[4 + handleName.length];
      System.arraycopy(Hex.decodeHex("000de140"), 0, cip68Name, 0, 4);
      System.arraycopy(handleName, 0, cip68Name, 4, handleName.length);
      filters.add("and ma.\"policy\"=? and ma.name in (?, ?)");
      filterParams.add(handlePolicyBytes);
      filterParams.add(handleName);
      filterParams.add(cip68Name);
    } else {
      return List.of();
    }

    if (!StringUtils.isBlank(after)) {
      String[] afterBits = after.trim().split("\\.");
      if (afterBits.length != 2 || afterBits[0].length() != 56) {
        return List.of();
      }
      filters.add("and (ma.\"policy\", ma.name) > (?, ?)");
      filterParams.add(Hex.decodeHex(afterBits[0]));
      filterParams.add(Hex.decodeHex(afterBits[1]));
    }

    filters.add(
        """
							and (
								length(ma.name) < 4
								or substring(ma.name from 1 for 4) in (
									decode('000de140','hex'), decode('0014df10','hex'), decode('001bc280','hex')
								)
								or (get_byte(ma.name, 0) >> 4) <> 0
								or (get_byte(ma.name, 3) & 15) <> 0
							)""");

    return jdbcTemplate.query(
        """
						with page as materialized (
							select ma.id, ma.policy, ma.name, ma.fingerprint
							from multi_asset ma
							where true
							"""
            + StringUtils.join(filters, " ")
            + """

							order by ma.policy, ma.name
							limit 100
						),
						agg as materialized (
							select
								page.id
								,page.policy
								,page.name
								,page.fingerprint
								,max(mtm.id) ma_mint_id
								,max(mtm.id) filter (where mtm.quantity > 0) pos_mint_id
								,sum(mtm.quantity) quantity
							from page
							join ma_tx_mint mtm on mtm.ident = page.id
							group by page.id, page.policy, page.name, page.fingerprint
							having sum(mtm.quantity) > 0
						)
						select
							agg.ma_mint_id
							,b.slot_no
							,agg.policy ma_policy_id
							,agg.name ma_name
							,agg.fingerprint ma_fingerprint
							,agg.quantity
							,case
								when length(agg.name) >= 4 and substring(agg.name from 1 for 4) in (
									decode('000de140','hex'), decode('0014df10','hex'), decode('001bc280','hex')
								) then ct_cip68_metadata(agg.policy, agg.name)
							end cip68
							,case
								when length(agg.name) >= 4 and substring(agg.name from 1 for 4) in (
									decode('000de140','hex'), decode('0014df10','hex'), decode('001bc280','hex')
								) then null
								else coalesce(
									tm.json->encode(agg.policy::bytea, 'hex')->encode(agg.name::bytea, 'escape'),
									tm.json->encode(agg.policy::bytea, 'hex')->encode(agg.name::bytea, 'hex')
								)
							end cip25
						from agg
						join ma_tx_mint mtm on mtm.id = agg.ma_mint_id
						join tx on tx.id = mtm.tx_id
						join block b on b.id = tx.block_id
						left join ma_tx_mint mtm_pos on mtm_pos.id = agg.pos_mint_id
						left join tx_metadata tm on tm.tx_id = mtm_pos.tx_id and tm.key = 721
						order by agg.policy, agg.name
						""",
        (rs, rowNum) -> toTokenListItem(rs, rs.getLong("ma_mint_id"), rs.getLong("slot_no")),
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
							ma."policy" ma_policy_id
							,ma.name ma_name
							,max(ma.fingerprint) ma_fingerprint
							,sum(mto.quantity) quantity
							,ct_cip68_metadata(ma."policy", ma.name) cip68
							,(select
								coalesce(tm.json->encode(ma.policy::bytea, 'hex')->encode(ma.name::bytea, 'escape'), tm.json->encode(ma.policy::bytea, 'hex')->encode(ma.name::bytea, 'hex'))
								from ma_tx_mint mtm
								join tx_metadata tm on tm.tx_id=mtm.tx_id and tm."key"=721
								where mtm.ident=max(mto.ident) and mtm.quantity>0
								order by tm.id desc limit 1) cip25
						from tx_out txo
						%s
						join ma_tx_out mto on mto.tx_out_id=txo.id
						join multi_asset ma on ma.id=mto.ident
						where
							txo.consumed_by_tx_id is null
							and %s
						group by ma."policy", ma.name
						order by max(txo.id) desc
						""",
            join, where);
    return jdbcTemplate.query(query, (rs, rowNum) -> toTokenListItem(rs, null, null), hash);
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
					,ct_cip68_metadata(ma."policy", ma.name) cip68
					,coalesce(tm.json->encode(ma.policy::bytea, 'hex')->encode(ma.name::bytea, 'escape'), tm.json->encode(ma.policy::bytea, 'hex')->encode(ma.name::bytea, 'hex')) cip25
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
          String.format(query, whereClause), (rs, rowNum) -> toTokenDetails(rs), params);
    } catch (EmptyResultDataAccessException e) {
      return null;
    }
  }

  /**
   * The royalties of a policy: the 777 metadata of the first mint of its token with the empty
   * name (CIP-27), or null if there is none. Old tokens name the rate "pct" and write the address
   * as one string instead of chunks.
   */
  public PolicyRoyalties getPolicyRoyalties(String policyId) throws DecoderException {
    try {
      return jdbcTemplate.queryForObject(
          """
					select
						coalesce(tm.json->>'rate', tm.json->>'pct') rate
						,case when jsonb_typeof(tm.json->'addr') = 'array'
							then (select string_agg(e.value, '' order by e.ordinality) from jsonb_array_elements_text(tm.json->'addr') with ordinality e)
							else tm.json->>'addr' end address
						,not exists (
							select 1 from multi_asset ma_other
							join ma_tx_mint mtm_other on mtm_other.ident = ma_other.id
							where ma_other."policy" = ma."policy" and mtm_other.tx_id < mtm.tx_id
						) first_mint
					from multi_asset ma
					join ma_tx_mint mtm on mtm.ident = ma.id
					join tx_metadata tm on tm.tx_id = mtm.tx_id and tm.key = 777
					where ma."policy" = ? and ma.name = ''::bytea and mtm.quantity > 0
					order by mtm.id
					limit 1
					""",
          (rs, rowNum) ->
              new PolicyRoyalties(
                  rs.getString("rate"), rs.getString("address"), rs.getBoolean("first_mint")),
          Hex.decodeHex(policyId));
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
								-- a refund returns the deposit of a retired pool, it is not earned
								,(select coalesce(sum(r.amount), 0) from reward r where r.addr_id=d.addr_id and r.pool_id=d.pool_hash_id and r.type <> 'refund') pool_rewards
							from delegation d
							join stake_address sa on sa.id=d.addr_id
							where sa."view"=?
								-- a deregistration ends the delegation, only what was delegated after it counts
								and d.tx_id > coalesce((select max(sd.tx_id) from stake_deregistration sd where sd.addr_id=d.addr_id), 0)
							order by d.id desc
							limit 1
							""",
          (rs, rowNum) ->
              new StakeInfo(
                  rs.getLong("stake"),
                  rs.getString("pool_hash"),
                  rs.getString("ticker_name"),
                  rs.getLong("total_stake"),
                  rs.getLong("pool_rewards")),
          stakeAddress);
    } catch (EmptyResultDataAccessException e) {
      return null;
    }
  }

  /** Every pool once, with the ticker of its most recent metadata. */
  public List<PoolInfo> getPoolList() {
    return jdbcTemplate.query(
        """
				select ticker_name, pool_hash
				from (
					-- off_chain_pool_data keeps every metadata a pool ever registered, the latest has the highest pmr_id
					select distinct on (pod.pool_id) pod.ticker_name, ph."view" pool_hash
					from off_chain_pool_data pod
					join pool_hash ph on ph.id=pod.pool_id
					order by pod.pool_id, pod.pmr_id desc
				) latest
				order by ticker_name
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

  /**
   * One handle per stake address: the shortest, then the alphabetically first. Which handle a
   * holder picked as default is not on-chain. Stake addresses without a handle are omitted.
   */
  public List<StakeHandle> getHandlesOfStakeAddresses(List<String> stakeAddresses) {
    if (stakeAddresses.isEmpty()) {
      return List.of();
    }
    Map<String, String> handles = new TreeMap<>();
    jdbcTemplate.query(
        """
					select sa.view stake_address, ma.name asset_name
					from stake_address sa
					join tx_out txo on txo.stake_address_id=sa.id and txo.consumed_by_tx_id is null
					join ma_tx_out mto on mto.tx_out_id=txo.id
					join multi_asset ma on ma.id=mto.ident and ma."policy"=?
					where sa.view = any(?)
					""",
        rs -> {
          String handle = handleName(rs.getBytes("asset_name"));
          if (handle != null) {
            handles.merge(
                rs.getString("stake_address"),
                handle,
                (a, b) -> HANDLE_ORDER.compare(a, b) <= 0 ? a : b);
          }
        },
        handlePolicyBytes,
        (Object) stakeAddresses.toArray(String[]::new));
    return handles.entrySet().stream()
        .map(entry -> new StakeHandle(entry.getKey(), entry.getValue()))
        .toList();
  }

  private static final Comparator<String> HANDLE_ORDER =
      Comparator.comparingInt(String::length).thenComparing(Comparator.naturalOrder());

  /** CIP-67 label 222, the token a holder owns of a CIP-68 handle */
  private static final byte[] CIP68_USER_TOKEN = {0x00, 0x0d, (byte) 0xe1, 0x40};

  /**
   * Readable name of a handle asset, or null for assets that are not a handle of their holder, such
   * as the reference token of a CIP-68 handle.
   */
  static String handleName(byte[] assetName) {
    if (assetName.length == 0) {
      return null;
    }
    if (assetName[0] != 0) {
      return new String(assetName, StandardCharsets.UTF_8);
    }
    if (assetName.length > 4
        && Arrays.equals(assetName, 0, 4, CIP68_USER_TOKEN, 0, 4)) {
      return new String(assetName, 4, assetName.length - 4, StandardCharsets.UTF_8);
    }
    return null;
  }

  public StakeAddress getAddressByHandle(String handle) throws DecoderException {
    // a newer handle is a CIP-68 asset: its holder owns the name behind the label of the user token
    byte[] plainName = handle.getBytes(StandardCharsets.UTF_8);
    byte[] cip68Name = new byte[CIP68_USER_TOKEN.length + plainName.length];
    System.arraycopy(CIP68_USER_TOKEN, 0, cip68Name, 0, CIP68_USER_TOKEN.length);
    System.arraycopy(plainName, 0, cip68Name, CIP68_USER_TOKEN.length, plainName.length);
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
					and ma.name in (?, ?)
					limit 1
					""",
          (rs, rowNum) -> new StakeAddress(rs.getString("address")),
          handlePolicyBytes,
          plainName,
          cip68Name);
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

  public List<TokenDetails> getMints(String stakeAddress) {
    return jdbcTemplate.query(
        """
							select
								b.slot_no
								,ma."policy" ma_policy_id
								,ma.name ma_name
								,ma.fingerprint
								,c68.cip68
								,coalesce(tm.json->encode(ma.policy::bytea, 'hex')->encode(ma.name::bytea, 'escape'), tm.json->encode(ma.policy::bytea, 'hex')->encode(ma.name::bytea, 'hex')) cip25
								,script.json ma_policy_script
								,tx.hash tx_hash
								,(select sum(quantity) from ma_tx_mint mtm_total where mtm_total.ident = mtm.ident) total_supply
							from ma_tx_mint mtm
							join multi_asset ma ON ma.id=mtm.ident
							join tx on tx.id=mtm.tx_id
							join block b on b.id = tx.block_id
							left join tx_metadata tm on tm.tx_id = tx.id and tm."key"=721
							left join lateral (select ct_cip68_metadata(ma."policy", ma.name) cip68) c68 on true
							join script on script.hash=ma."policy"
							where exists (
								select 1
								from tx_out to2
								join stake_address sa on sa.id=to2.stake_address_id
								where to2.tx_id=mtm.tx_id
									and sa.view=?
							)
							and mtm.quantity > 0
							order by mtm.id desc
						""",
        (rs, rowNum) -> toTokenDetails(rs),
        stakeAddress);
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
										-- start of the epoch, calculated: "epoch" is a view that aggregates the current epoch's blocks on every lookup
										TO_TIMESTAMP(rw.earned_epoch * 432000 + 1506203091) at time zone 'utc',
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

  private TokenListItem toTokenListItem(ResultSet rs, Long maMintId, Long slotNo)
      throws SQLException {
    String policyHex = toHexString(rs.getBytes("ma_policy_id"));
    String nameHex = toHexString(rs.getBytes("ma_name"));
    String cip68 = rs.getString("cip68");
    String cip25 = rs.getString("cip25");
    JsonNode metadata = resolveMetadata(cip68, cip25, policyHex, nameHex);
    Integer cip = null;
    if (cip68 != null) {
      cip = 68;
    } else if (cip25 != null) {
      cip = 25;
    }
    return new TokenListItem(
        maMintId,
        slotNo,
        policyHex,
        nameHex,
        rs.getString("ma_fingerprint"),
        rs.getLong("quantity"),
        nameField(metadata, nameHex),
        imageField(metadata),
        cip);
  }

  private TokenDetails toTokenDetails(ResultSet rs) throws SQLException {
    String policyHex = toHexString(rs.getBytes("ma_policy_id"));
    String nameHex = toHexString(rs.getBytes("ma_name"));
    String cip68 = rs.getString("cip68");
    String cip25 = rs.getString("cip25");
    JsonNode metadata = resolveMetadata(cip68, cip25, policyHex, nameHex);
    Integer cip = null;
    if (cip68 != null) {
      cip = 68;
    } else if (cip25 != null) {
      cip = 25;
    }
    return new TokenDetails(
        rs.getLong("slot_no"),
        policyHex,
        nameHex,
        rs.getString("fingerprint"),
        metadata == null ? null : metadata.toString(),
        cip,
        rs.getString("ma_policy_script"),
        toHexString(rs.getBytes("tx_hash")),
        rs.getLong("total_supply"));
  }

  // db-sync stores CIP-68 as Plutus JSON ({"bytes":"6e616d65"}, {"map":[{"k":...,"v":...}]}).
  private JsonNode resolveMetadata(
      String cip68Raw, String cip25Json, String policyHex, String nameHex) {
    try {
      if (cip68Raw != null) {
        return unwrap721(fromPlutus(objectMapper.readTree(cip68Raw)), policyHex, nameHex);
      }
      if (cip25Json != null) {
        return objectMapper.readTree(cip25Json);
      }
      return null;
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  private JsonNode unwrap721(JsonNode converted, String policyHex, String nameHex) {
    if (converted == null || !converted.has("721")) {
      return converted;
    }
    JsonNode nested = converted.path("721").path(policyHex);
    if (nameHex.length() < 8) {
      return null;
    }
    String rest = nameHex.substring(8);
    if (nested.has(rest)) {
      return nested.get(rest);
    }
    try {
      String utf8 =
          StandardCharsets.UTF_8
              .newDecoder()
              .decode(ByteBuffer.wrap(Hex.decodeHex(rest)))
              .toString();
      return nested.has(utf8) ? nested.get(utf8) : null;
    } catch (DecoderException | CharacterCodingException e) {
      return null;
    }
  }

  private JsonNode fromPlutus(JsonNode data) {
    if (data == null || data.isNull() || data.isMissingNode()) {
      return null;
    }
    if (data.has("bytes")) {
      String hex = data.get("bytes").asText();
      try {
        String utf8 =
            StandardCharsets.UTF_8
                .newDecoder()
                .decode(ByteBuffer.wrap(Hex.decodeHex(hex)))
                .toString();
        return TextNode.valueOf(utf8);
      } catch (DecoderException | CharacterCodingException e) {
        return TextNode.valueOf(hex);
      }
    }
    if (data.has("int")) {
      return data.get("int");
    }
    if (data.has("list")) {
      ArrayNode array = JsonNodeFactory.instance.arrayNode();
      for (JsonNode e : data.get("list")) {
        array.add(fromPlutus(e));
      }
      return array;
    }
    if (data.has("map")) {
      ObjectNode object = JsonNodeFactory.instance.objectNode();
      for (JsonNode elem : data.get("map")) {
        JsonNode keyNode = elem.get("k");
        if (keyNode == null) {
          continue;
        }
        JsonNode convertedKey = fromPlutus(keyNode);
        if (convertedKey == null || !convertedKey.isValueNode()) {
          continue;
        }
        object.set(convertedKey.asText(), fromPlutus(elem.get("v")));
      }
      return object;
    }
    return null;
  }

  private String nameField(JsonNode metadata, String nameHex) {
    String name = metadataUri(metadata, "name");
    if (name != null) {
      return name;
    }
    name = metadataUri(metadata, "ticker");
    if (name != null) {
      return name;
    }
    return utf8AssetName(nameHex);
  }

  private String utf8AssetName(String nameHex) {
    if (nameHex == null || nameHex.isEmpty()) {
      return null;
    }
    String hex = nameHex;
    if (hasCip67Prefix(hex)) {
      hex = hex.substring(8);
    }
    if (hex.isEmpty()) {
      return null;
    }
    try {
      return StandardCharsets.UTF_8
          .newDecoder()
          .decode(ByteBuffer.wrap(Hex.decodeHex(hex)))
          .toString();
    } catch (DecoderException | CharacterCodingException e) {
      return null;
    }
  }

  private boolean hasCip67Prefix(String nameHex) {
    return nameHex.length() >= 8 && nameHex.charAt(0) == '0' && nameHex.charAt(7) == '0';
  }

  private String imageField(JsonNode metadata) {
    String image = metadataUri(metadata, "image");
    return image != null ? image : metadataUri(metadata, "logo");
  }

  private String metadataUri(JsonNode metadata, String field) {
    if (metadata == null || !metadata.has(field) || metadata.get(field).isNull()) {
      return null;
    }
    JsonNode node = metadata.get(field);
    if (node.isArray()) {
      StringBuilder joined = new StringBuilder();
      for (JsonNode part : node) {
        joined.append(part.asText());
      }
      return joined.isEmpty() ? null : joined.toString();
    }
    return node.asText(null);
  }

  private String toHexString(byte[] bytes) {
    return bytes == null ? null : Hex.encodeHexString(bytes);
  }
}
