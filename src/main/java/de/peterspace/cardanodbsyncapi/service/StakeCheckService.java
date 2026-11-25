package de.peterspace.cardanodbsyncapi.service;

import de.peterspace.cardanodbsyncapi.dto.EpochBlocks;
import de.peterspace.cardanodbsyncapi.dto.PoolCheck;
import de.peterspace.cardanodbsyncapi.dto.PoolEpoch;
import de.peterspace.cardanodbsyncapi.dto.PoolEpochFacts;
import de.peterspace.cardanodbsyncapi.dto.PoolPerformance;
import de.peterspace.cardanodbsyncapi.dto.SlotBlock;
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
				,odrep."view" operator_drep_id
				,(select d.given_name
					from drep_registration dr
					join off_chain_vote_data o on o.voting_anchor_id = dr.voting_anchor_id
					join off_chain_vote_drep_data d on d.off_chain_vote_data_id = o.id
					where dr.drep_hash_id = odrep.id and d.given_name is not null
					order by dr.id desc limit 1) operator_drep_name
			from pool_hash ph
			join lateral (
				select pu.margin, pu.fixed_cost, pu.pledge, pu.registered_tx_id, pu.reward_addr_id
				from pool_update pu where pu.hash_id = ph.id order by pu.id desc limit 1
			) next_pu on true
			left join lateral (
				-- where the account that receives the pool's rewards has delegated its own vote
				select dh.id, dh."view"
				from delegation_vote dv join drep_hash dh on dh.id = dv.drep_hash_id
				where dv.addr_id = next_pu.reward_addr_id order by dv.id desc limit 1
			) odrep on true
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
        saturationStake,
        rs.getString("operator_drep_id"),
        rs.getString("operator_drep_name"));
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

  /** One slot in twenty has a block (the active slot coefficient) and an epoch has 432,000 slots. */
  private static final double BLOCKS_PER_EPOCH = 21600;

  public PoolEpoch getPoolEpoch(String poolHash, int epoch) {
    try {
      return jdbcTemplate.queryForObject(
          """
					select
						(select count(*) from block b join slot_leader sl on sl.id = b.slot_leader_id
							where sl.pool_hash_id = ph.id and b.epoch_no = ?) blocks,
						(select coalesce(sum(es.amount), 0) from epoch_stake es where es.epoch_no = ? and es.pool_id = ph.id) stake,
						(select count(*) from epoch_stake es where es.epoch_no = ? and es.pool_id = ph.id) delegators,
						-- db-sync writes a snapshot in parts: until it is complete the sums would be too small
						(select coalesce(sum(es.amount), 0) from epoch_stake es where es.epoch_no = ?
							and exists (select 1 from epoch_stake_progress p where p.epoch_no = es.epoch_no and p.completed)) total_stake
					from pool_hash ph
					where ph."view" = ?
					""",
          (rs, rowNum) -> {
            long stake = rs.getLong("stake");
            long totalStake = rs.getLong("total_stake");
            return new PoolEpoch(
                poolHash,
                epoch,
                rs.getInt("blocks"),
                stake,
                totalStake,
                rs.getInt("delegators"),
                totalStake > 0 ? BLOCKS_PER_EPOCH * stake / totalStake : null);
          },
          epoch,
          epoch,
          epoch,
          epoch,
          poolHash);
    } catch (EmptyResultDataAccessException e) {
      return null;
    }
  }

  /**
   * The first block in or after a slot. When a pool was leader in the slot and its block is not on
   * the chain, this block took the height the pool's block would have had: in the same slot it won
   * a slot battle, in a later slot a height battle.
   */
  public SlotBlock getBlockFrom(long slot) {
    List<SlotBlock> blocks =
        jdbcTemplate.query(
            """
				select b.slot_no, b.block_no, ph."view" pool_hash,
					(select pod.ticker_name from off_chain_pool_data pod where pod.pool_id = ph.id order by pod.pmr_id desc limit 1) ticker
				from block b
				join slot_leader sl on sl.id = b.slot_leader_id
				left join pool_hash ph on ph.id = sl.pool_hash_id
				where b.slot_no >= ?
				order by b.slot_no
				limit 1
				""",
            (rs, rowNum) ->
                new SlotBlock(
                    rs.getLong("slot_no"),
                    rs.getLong("block_no"),
                    rs.getString("pool_hash"),
                    rs.getString("ticker")),
            slot);
    return blocks.isEmpty() ? null : blocks.get(0);
  }

  /**
   * Numbers around one epoch of a pool: what its blocks carried, its history and its place among
   * the pools. Several small queries, together they are far faster than one. Null for an unknown
   * pool.
   */
  public PoolEpochFacts getPoolEpochFacts(String poolHash, int epoch) {
    List<Long> poolIds =
        jdbcTemplate.queryForList(
            "select id from pool_hash where \"view\" = ?", Long.class, poolHash);
    if (poolIds.isEmpty()) {
      return null;
    }
    long poolId = poolIds.get(0);
    PoolEpochFacts.PoolEpochFactsBuilder facts =
        PoolEpochFacts.builder().poolHash(poolHash).epoch(epoch);

    List<Long> txIds = new ArrayList<>();
    jdbcTemplate.query(
        """
				select count(distinct b.id) blocks, count(tx.id) transactions,
					coalesce(sum(tx.out_sum), 0) moved, coalesce(sum(tx.fee), 0) fees, max(tx.out_sum) largest,
					max(b.block_no) last_block_no,
					avg(b.size)::float / (select max_block_size from epoch_param where epoch_no = ?) fill,
					array_remove(array_agg(tx.id), null) tx_ids
				from block b
				join slot_leader sl on sl.id = b.slot_leader_id
				left join tx on tx.block_id = b.id
				where sl.pool_hash_id = ? and b.epoch_no = ?
				""",
        rs -> {
          facts
              .blocks(rs.getInt("blocks"))
              .transactions(rs.getLong("transactions"))
              .lovelaceMoved(rs.getLong("moved"))
              .fees(rs.getLong("fees"))
              .largestTransaction(rs.getObject("largest") == null ? null : rs.getLong("largest"))
              .lastBlockNo(
                  rs.getObject("last_block_no") == null ? null : rs.getLong("last_block_no"))
              .blockFill(rs.getObject("fill") == null ? null : rs.getDouble("fill"));
          for (Long id : (Long[]) rs.getArray("tx_ids").getArray()) {
            txIds.add(id);
          }
        },
        epoch,
        poolId,
        epoch);

    if (txIds.isEmpty()) {
      facts.tokenOutputs(0).scriptRuns(0).tokensMinted(0);
    } else {
      Long[] ids = txIds.toArray(Long[]::new);
      facts
          .tokenOutputs(
              jdbcTemplate.queryForObject(
                  "select count(*) from ma_tx_out mto join tx_out o on o.id = mto.tx_out_id where o.tx_id = any(?)",
                  Long.class,
                  (Object) ids))
          // redeemer has no index on tx_id: a scan of about a second, the result is cached
          .scriptRuns(
              jdbcTemplate.queryForObject(
                  "select count(*) from redeemer where tx_id = any(?)", Long.class, (Object) ids))
          .tokensMinted(
              jdbcTemplate.queryForObject(
                  "select count(*) from ma_tx_mint where tx_id = any(?) and quantity > 0",
                  Long.class,
                  (Object) ids));
    }

    jdbcTemplate.query(
        """
				select
					(select count(*) from block where epoch_no = ?) network_blocks,
					(select min(active_epoch_no) from pool_update where hash_id = ?) registered_epoch,
					(select coalesce(sum(amount), 0) from epoch_stake where epoch_no = ? and pool_id = ?) previous_stake,
					(select count(*) from epoch_stake where epoch_no = ? and pool_id = ?) previous_delegators,
					(select coalesce(sum(amount), 0) from reward where pool_id = ? and type = 'member') delegator_rewards
				""",
        rs -> {
          facts
              .networkBlocks(rs.getLong("network_blocks"))
              .registeredEpoch(
                  rs.getObject("registered_epoch") == null ? null : rs.getInt("registered_epoch"))
              .previousStake(rs.getLong("previous_stake"))
              .previousDelegators(rs.getInt("previous_delegators"))
              .delegatorRewards(rs.getLong("delegator_rewards"));
        },
        epoch,
        poolId,
        epoch - 1,
        poolId,
        epoch - 1,
        poolId,
        poolId);

    // blocks per epoch of the pool's life, for the total, the epochs with a block and the streak
    TreeMap<Integer, Long> blocksPerEpoch = new TreeMap<>();
    jdbcTemplate.query(
        """
				select b.epoch_no, count(*) blocks from block b join slot_leader sl on sl.id = b.slot_leader_id
				where sl.pool_hash_id = ? and b.epoch_no <= ? group by b.epoch_no
				""",
        rs -> {
          blocksPerEpoch.put(rs.getInt("epoch_no"), rs.getLong("blocks"));
        },
        poolId,
        epoch);
    int streak = 0;
    while (blocksPerEpoch.containsKey(epoch - streak)) {
      streak++;
    }
    facts
        .lifetimeBlocks(blocksPerEpoch.values().stream().mapToLong(Long::longValue).sum())
        .epochsWithBlocks(blocksPerEpoch.size())
        .blockStreak(streak);

    jdbcTemplate.query(
        """
				with x as (select pool_id, sum(amount) s from epoch_stake where epoch_no = ? group by pool_id)
				select (select count(*) from x) pools,
					(select count(*) + 1 from x where s > (select s from x where pool_id = ?)) place,
					exists (select 1 from x where pool_id = ?) staked
				""",
        rs -> {
          facts.rankedPools(rs.getInt("pools"));
          facts.stakeRank(rs.getBoolean("staked") ? rs.getInt("place") : null);
        },
        epoch,
        poolId,
        poolId);
    return facts.build();
  }
}
