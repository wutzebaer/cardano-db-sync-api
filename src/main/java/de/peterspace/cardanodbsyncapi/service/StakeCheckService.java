package de.peterspace.cardanodbsyncapi.service;

import de.peterspace.cardanodbsyncapi.dto.EpochBlocks;
import de.peterspace.cardanodbsyncapi.dto.PoolCheck;
import de.peterspace.cardanodbsyncapi.dto.PoolPerformance;
import de.peterspace.cardanodbsyncapi.dto.StakeCheck;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** The staking state of an address and the pools it could be delegated to. */
@Component
@RequiredArgsConstructor
public class StakeCheckService {

  /** Epochs the returns of a pool are averaged over. */
  private static final int ROA_EPOCHS = 10;

  /** Rewards earned in an epoch are known two epoch boundaries later. */
  private static final int REWARD_DELAY_EPOCHS = 2;

  private static final long MAX_LOVELACE = 45_000_000_000_000_000L;

  private static final int EPOCHS_PER_YEAR = 73;

  /**
   * One row per pool. Parameters of a registration count from its active epoch on, a retirement
   * only if it was announced after the last registration. The first two parameters are the current
   * epoch.
   */
  private static final String POOL_QUERY =
      """
			select
				ph."view" pool_hash
				,pod.ticker_name
				,pod.json->>'name' pool_name
				,pod.json->>'homepage' homepage
				,coalesce(cur_pu.margin, next_pu.margin) margin
				,coalesce(cur_pu.fixed_cost, next_pu.fixed_cost) fixed_cost
				,coalesce(cur_pu.pledge, next_pu.pledge) pledge
				,next_pu.margin next_margin
				,next_pu.fixed_cost next_fixed_cost
				,pr.retiring_epoch
				,coalesce(st.stake, 0) active_stake
				,coalesce(st.delegators, 0) delegators
			from pool_hash ph
			join lateral (
				select pu.margin, pu.fixed_cost, pu.pledge, pu.registered_tx_id
				from pool_update pu where pu.hash_id = ph.id order by pu.id desc limit 1
			) next_pu on true
			left join lateral (
				select pu.margin, pu.fixed_cost, pu.pledge
				from pool_update pu where pu.hash_id = ph.id and pu.active_epoch_no <= ? order by pu.id desc limit 1
			) cur_pu on true
			left join lateral (
				select pr.retiring_epoch
				from pool_retire pr where pr.hash_id = ph.id and pr.announced_tx_id > next_pu.registered_tx_id order by pr.id desc limit 1
			) pr on true
			left join lateral (
				-- off_chain_pool_data keeps every metadata a pool ever registered, the latest has the highest pmr_id
				select pod.ticker_name, pod.json
				from off_chain_pool_data pod where pod.pool_id = ph.id order by pod.pmr_id desc limit 1
			) pod on true
			left join lateral (
				select sum(es.amount) stake, count(*) delegators
				from epoch_stake es where es.pool_id = ph.id and es.epoch_no = ?
			) st on true
			where %s
			""";

  private final JdbcTemplate jdbcTemplate;

  /** "epoch" is a view that aggregates the current epoch on every lookup, the blocks know it too. */
  private int currentEpoch() {
    return jdbcTemplate.queryForObject("select max(epoch_no) from block", Integer.class);
  }

  /** A pool is saturated at the k-th part of all ADA outside the reserves. */
  private long saturationStake() {
    return jdbcTemplate.queryForObject(
        """
				select ((? - (select reserves from ada_pots order by id desc limit 1))
					/ (select optimal_pool_count from epoch_param order by id desc limit 1))::bigint
				""",
        Long.class,
        MAX_LOVELACE);
  }

  private PoolCheck toPoolCheck(ResultSet rs, long saturationStake) throws SQLException {
    double margin = rs.getDouble("margin");
    long fixedCost = rs.getLong("fixed_cost");
    double nextMargin = rs.getDouble("next_margin");
    long nextFixedCost = rs.getLong("next_fixed_cost");
    return new PoolCheck(
        rs.getString("pool_hash"),
        rs.getString("ticker_name"),
        rs.getString("pool_name"),
        rs.getString("homepage"),
        margin,
        fixedCost,
        rs.getLong("pledge"),
        nextMargin == margin ? null : nextMargin,
        nextFixedCost == fixedCost ? null : nextFixedCost,
        rs.getObject("retiring_epoch", Integer.class),
        rs.getLong("active_stake"),
        rs.getInt("delegators"),
        saturationStake);
  }

  public PoolCheck getPoolCheck(String poolHash) {
    int epoch = currentEpoch();
    long saturationStake = saturationStake();
    try {
      return jdbcTemplate.queryForObject(
          String.format(POOL_QUERY, "ph.\"view\" = ?"),
          (rs, rowNum) -> toPoolCheck(rs, saturationStake),
          epoch,
          epoch,
          poolHash);
    } catch (EmptyResultDataAccessException e) {
      return null;
    }
  }

  /**
   * Pools a delegator can move to without second thoughts: making blocks, not retiring, at most 1 %
   * margin and the minimum fixed cost, between 300 thousand and 30 million ADA of stake, which is
   * far from saturation and enough for regular blocks. Ordered by pool id, the caller picks.
   */
  public List<PoolCheck> getPoolAlternatives() {
    int epoch = currentEpoch();
    long saturationStake = saturationStake();
    return jdbcTemplate.query(
        String.format(
                POOL_QUERY,
                """
								ph.id in (
									select es.pool_id from epoch_stake es where es.epoch_no = ?
									group by es.pool_id having sum(es.amount) between 300000000000 and 30000000000000
								)
								and ph.id in (
									select sl.pool_hash_id from block b join slot_leader sl on sl.id = b.slot_leader_id
									where b.epoch_no >= ? - 5 and sl.pool_hash_id is not null
									group by sl.pool_hash_id having count(*) >= 3
								)
								and next_pu.margin <= 0.01
								and cur_pu.margin <= 0.01
								and next_pu.fixed_cost <= (select min_pool_cost from epoch_param order by id desc limit 1)
								and pr.retiring_epoch is null
								and pod.ticker_name is not null
								""")
            + " order by ph.\"view\"",
        (rs, rowNum) -> toPoolCheck(rs, saturationStake),
        epoch,
        epoch,
        epoch,
        epoch);
  }

  public StakeCheck getStakeCheck(String stakeAddress) {
    int epoch = currentEpoch();
    try {
      return jdbcTemplate.queryForObject(
          """
					select
						-- a key registered again after a deregistration is registered
						coalesce((select max(tx_id) from stake_registration where addr_id = sa.id), 0)
							> coalesce((select max(tx_id) from stake_deregistration where addr_id = sa.id), 0) registered
						,coalesce((select sum(utxo.value) from tx_out utxo where utxo.stake_address_id = sa.id and utxo.consumed_by_tx_id is null), 0) balance
						,coalesce((select sum(amount) from reward where addr_id = sa.id and spendable_epoch <= ?), 0)
							+ coalesce((select sum(amount) from reward_rest where addr_id = sa.id and spendable_epoch <= ?), 0)
							- coalesce((select sum(amount) from withdrawal where addr_id = sa.id), 0) rewards_available
						,(select dh."view" from delegation_vote dv join drep_hash dh on dh.id = dv.drep_hash_id
							where dv.addr_id = sa.id order by dv.id desc limit 1) drep_id
						,(select ph."view" from delegation d join pool_hash ph on ph.id = d.pool_hash_id
							where d.addr_id = sa.id order by d.id desc limit 1) pool_hash
					from stake_address sa
					where sa."view" = ?
					""",
          (rs, rowNum) -> {
            boolean registered = rs.getBoolean("registered");
            String poolHash = rs.getString("pool_hash");
            return new StakeCheck(
                stakeAddress,
                epoch,
                registered,
                rs.getLong("balance"),
                // a deregistration pays the rewards out, what the sums leave is rounding of history
                registered ? Math.max(0, rs.getLong("rewards_available")) : 0,
                // delegations end with the registration
                registered ? rs.getString("drep_id") : null,
                registered && poolHash != null ? getPoolCheck(poolHash) : null);
          },
          epoch,
          epoch,
          stakeAddress);
    } catch (EmptyResultDataAccessException e) {
      return null;
    }
  }

  public PoolPerformance getPoolPerformance(String poolHash) {
    Long poolId;
    try {
      poolId = jdbcTemplate.queryForObject("select id from pool_hash where \"view\" = ?", Long.class, poolHash);
    } catch (EmptyResultDataAccessException e) {
      return null;
    }
    int epoch = currentEpoch();
    int toEpoch = epoch - REWARD_DELAY_EPOCHS;
    int fromEpoch = toEpoch - ROA_EPOCHS + 1;

    // every member reward next to the stake it was earned with, so the pledge of the operator does not thin it out
    Double roa =
        jdbcTemplate.queryForObject(
            """
						select (sum(r.amount)::numeric / nullif(sum(es.amount), 0) * ? * 100)::float8
						from reward r
						join epoch_stake es on es.addr_id = r.addr_id and es.epoch_no = r.earned_epoch and es.pool_id = r.pool_id
						where r.pool_id = ? and r.type = 'member' and r.earned_epoch between ? and ?
						""",
            Double.class,
            EPOCHS_PER_YEAR,
            poolId,
            fromEpoch,
            toEpoch);

    double networkRoa =
        jdbcTemplate.queryForObject(
            """
						select (
							(select sum(amount) from reward where earned_epoch between ? and ? and type in ('member', 'leader'))::numeric
							/ (select sum(amount) from epoch_stake where epoch_no between ? and ?) * ? * 100
						)::float8
						""",
            Double.class,
            fromEpoch,
            toEpoch,
            fromEpoch,
            toEpoch,
            EPOCHS_PER_YEAR);

    Map<Integer, Integer> blocksByEpoch = new TreeMap<>();
    for (int e = fromEpoch; e <= epoch; e++) {
      blocksByEpoch.put(e, 0);
    }
    jdbcTemplate.query(
        """
				select b.epoch_no, count(*) blocks
				from block b join slot_leader sl on sl.id = b.slot_leader_id
				where sl.pool_hash_id = ? and b.epoch_no >= ?
				group by b.epoch_no
				""",
        rs -> {
          blocksByEpoch.put(rs.getInt("epoch_no"), rs.getInt("blocks"));
        },
        poolId,
        fromEpoch);
    List<EpochBlocks> blocks = new ArrayList<>();
    blocksByEpoch.forEach((e, count) -> blocks.add(new EpochBlocks(e, count)));

    long lifetimeBlocks =
        jdbcTemplate.queryForObject(
            "select count(*) from block b join slot_leader sl on sl.id = b.slot_leader_id where sl.pool_hash_id = ?",
            Long.class,
            poolId);

    return new PoolPerformance(poolHash, fromEpoch, toEpoch, roa, networkRoa, blocks, lifetimeBlocks);
  }
}
