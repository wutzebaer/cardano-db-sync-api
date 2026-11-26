package de.peterspace.cardanodbsyncapi.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.peterspace.cardanodbsyncapi.dto.ProtocolParams;
import de.peterspace.cardanodbsyncapi.dto.ScriptOutput;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

/** What a page needs to work with a Plutus contract: the outputs at its address and the cost model. */
@Service
@RequiredArgsConstructor
public class ScriptService {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final JdbcTemplate jdbcTemplate;

  /** One row for the ADA and the datum of each chosen output, one row per token it holds. */
  private static final String ROWS =
      """
			select tx.hash tx_hash, o."index" tx_index, null::bytea ma_policy_id, null::bytea ma_name,
				o.value, d.bytes datum, o.consumed_by_tx_id is not null spent
			from chosen o
			join tx on tx.id = o.tx_id
			left join datum d on d.id = o.inline_datum_id
			union all
			select tx.hash, o."index", ma."policy", ma."name", mto.quantity, null, o.consumed_by_tx_id is not null
			from chosen o
			join tx on tx.id = o.tx_id
			join ma_tx_out mto on mto.tx_out_id = o.id
			join multi_asset ma on ma.id = mto.ident
			order by tx_hash, tx_index
			""";

  private static final RowMapper<ScriptOutput> MAPPER =
      (rs, rowNum) ->
          new ScriptOutput(
              Hex.encodeHexString(rs.getBytes("tx_hash")),
              rs.getInt("tx_index"),
              hex(rs.getBytes("ma_policy_id")),
              hex(rs.getBytes("ma_name")),
              rs.getLong("value"),
              hex(rs.getBytes("datum")),
              rs.getBoolean("spent"));

  private static String hex(byte[] bytes) {
    return bytes == null ? null : Hex.encodeHexString(bytes);
  }

  /**
   * The unspent outputs at a script that hold a token of the script's own policy whose name starts
   * with the prefix. A contract marks its outputs with such tokens, and whatever else was sent to
   * its address is of no interest (and can be a lot).
   */
  public List<ScriptOutput> getOutputs(String scriptHash, String namePrefix)
      throws DecoderException {
    byte[] script = Hex.decodeHex(scriptHash);
    byte[] prefix = Hex.decodeHex(namePrefix);
    return jdbcTemplate.query(
        """
				with chosen as (
					select txo.id, txo.tx_id, txo."index", txo.value, txo.inline_datum_id, txo.consumed_by_tx_id
					from tx_out txo
					where txo.payment_cred = ?
						and txo.consumed_by_tx_id is null
						and exists (
							select 1
							from ma_tx_out mto
							join multi_asset ma on ma.id = mto.ident
							where mto.tx_out_id = txo.id
								and ma."policy" = ?
								and substring(ma."name" from 1 for ?) = ?
						)
				)
				"""
            + ROWS,
        MAPPER,
        script,
        script,
        prefix.length,
        prefix);
  }

  /**
   * The outputs the first transaction that ever paid to the script put there, spent or not. A
   * contract that is set up by one transaction can be read from them after its outputs are gone.
   */
  public List<ScriptOutput> getFirstOutputs(String scriptHash) throws DecoderException {
    byte[] script = Hex.decodeHex(scriptHash);
    return jdbcTemplate.query(
        """
				-- materialized: left to itself the planner walks tx_out in the order of tx_id to find the
				-- first match, which takes minutes for a script with few outputs
				with mine as materialized (
					select txo.id, txo.tx_id, txo."index", txo.value, txo.inline_datum_id, txo.consumed_by_tx_id
					from tx_out txo
					where txo.payment_cred = ?
				),
				chosen as (
					select * from mine where tx_id = (select min(tx_id) from mine)
				)
				"""
            + ROWS,
        MAPPER,
        script);
  }

  /** Names a contract reads at most at once, a drop holds up to 10,000 tokens. */
  private static final int MAX_MINTED_NAMES = 20000;

  /**
   * The names (hex) of all tokens ever minted under the policy of a script that start with the
   * prefix, burned ones included. A contract that mints a token when it is sold can tell from them
   * what was sold, also after the outputs that kept track of it are gone.
   */
  public List<String> getMintedNames(String scriptHash, String namePrefix) throws DecoderException {
    byte[] prefix = Hex.decodeHex(namePrefix);
    return jdbcTemplate.query(
        """
				select ma."name"
				from multi_asset ma
				where ma."policy" = ? and substring(ma."name" from 1 for ?) = ?
				order by ma."name"
				limit ?
				""",
        (rs, rowNum) -> Hex.encodeHexString(rs.getBytes("name")),
        Hex.decodeHex(scriptHash),
        prefix.length,
        prefix,
        MAX_MINTED_NAMES);
  }

  /**
   * The protocol parameters of the current epoch. The prices of script runs are fractions on chain
   * and decimal numbers in the database, which is exact for the values in use (0.0577, 0.0000721).
   */
  public ProtocolParams getProtocolParams() {
    return jdbcTemplate.queryForObject(
        """
				select ep.epoch_no, ep.min_fee_a, ep.min_fee_b, ep.key_deposit, ep.pool_deposit,
					ep.coins_per_utxo_size, ep.max_tx_size, ep.max_val_size, ep.price_mem, ep.price_step,
					ep.max_tx_ex_mem, ep.max_tx_ex_steps, ep.min_fee_ref_script_cost_per_byte,
					ep.collateral_percent, ep.max_collateral_inputs
				from epoch_param ep
				order by ep.epoch_no desc
				limit 1
				""",
        (rs, rowNum) ->
            new ProtocolParams(
                rs.getInt("epoch_no"),
                rs.getLong("min_fee_a"),
                rs.getLong("min_fee_b"),
                rs.getLong("key_deposit"),
                rs.getLong("pool_deposit"),
                rs.getLong("coins_per_utxo_size"),
                rs.getLong("max_tx_size"),
                rs.getLong("max_val_size"),
                rs.getDouble("price_mem"),
                rs.getDouble("price_step"),
                rs.getLong("max_tx_ex_mem"),
                rs.getLong("max_tx_ex_steps"),
                rs.getDouble("min_fee_ref_script_cost_per_byte"),
                rs.getInt("collateral_percent"),
                rs.getInt("max_collateral_inputs")));
  }

  /** The Plutus V3 cost model of the current epoch, which a transaction that runs a script commits to. */
  public List<Long> getPlutusV3CostModel() throws Exception {
    String costs =
        jdbcTemplate.queryForObject(
            """
						select cm.costs -> 'PlutusV3'
						from epoch_param ep
						join cost_model cm on cm.id = ep.cost_model_id
						order by ep.epoch_no desc
						limit 1
						""",
            String.class);
    // a chain that has not reached the Conway era yet has no such model
    return costs == null ? List.of() : OBJECT_MAPPER.readValue(costs, new TypeReference<List<Long>>() {});
  }
}
