package de.peterspace.cardanodbsyncapi.service;

import de.peterspace.cardanodbsyncapi.dto.DRepInfo;
import de.peterspace.cardanodbsyncapi.dto.GovAction;
import de.peterspace.cardanodbsyncapi.dto.GovernanceOverview;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Governance actions and how DReps, pools and the committee voted on them. */
@Component
@RequiredArgsConstructor
public class GovernanceService {

  /** Actions that ended longer ago than this are left out. */
  private static final int CLOSED_EPOCHS = 18;

  private static final int MAX_ACTIONS = 60;

  private final JdbcTemplate jdbcTemplate;

  private Long idOf(String table, String view) {
    if (view == null || view.isBlank()) {
      return null;
    }
    List<Long> ids =
        jdbcTemplate.queryForList("select id from " + table + " where \"view\" = ? limit 1", Long.class, view);
    return ids.isEmpty() ? null : ids.get(0);
  }

  /**
   * DReps that are registered and have voted or registered recently enough to count as active, the
   * one with the most ADA behind it first. The two automatic choices are not DReps and are left out.
   */
  public List<DRepInfo> getDreps() {
    return jdbcTemplate.query(
        """
				-- the same actions the overview shows: open ones and those that ended within the last epochs
				with acts as (
					select gap.id
					from gov_action_proposal gap
					where coalesce(gap.enacted_epoch, gap.dropped_epoch, gap.expired_epoch, 999999) >= (select max(epoch_no) from block) - ?
					order by gap.id desc limit ?
				),
				-- a DRep can vote again until an action ends, only the latest vote counts
				latest as (
					select distinct on (vp.gov_action_proposal_id, vp.drep_voter) vp.drep_voter, vp.vote::text vote
					from voting_procedure vp
					where vp.gov_action_proposal_id in (select id from acts) and vp.drep_voter is not null and vp.invalid is null
					order by vp.gov_action_proposal_id, vp.drep_voter, vp.id desc
				),
				tally as (
					select drep_voter
						,count(*) filter (where vote = 'Yes') yes
						,count(*) filter (where vote = 'No') no
						,count(*) filter (where vote = 'Abstain') abstain
					from latest group by drep_voter
				)
				select dh."view" drep_id, dd.amount voting_power, m.given_name drep_name
					-- what the DRep says it stands for, as one short line
					,left(btrim(regexp_replace(m.objectives, '[[:space:]]+', ' ', 'g')), 240) objectives
					,coalesce(t.yes, 0) votes_yes, coalesce(t.no, 0) votes_no, coalesce(t.abstain, 0) votes_abstain
					,(select count(*) from acts) recent_actions
				from drep_distr dd
				join drep_hash dh on dh.id = dd.hash_id
				left join tally t on t.drep_voter = dh.id
				left join lateral (
					select d.given_name, d.objectives
					from drep_registration dr
					join off_chain_vote_data o on o.voting_anchor_id = dr.voting_anchor_id
					join off_chain_vote_drep_data d on d.off_chain_vote_data_id = o.id
					where dr.drep_hash_id = dh.id and d.given_name is not null
					order by dr.id desc limit 1) m on true
				where dd.epoch_no = (select max(epoch_no) from drep_distr)
					and dd.active_until >= dd.epoch_no
					and dh."view" like 'drep1%'
				order by dd.amount desc
				""",
        (rs, rowNum) ->
            new DRepInfo(
                rs.getString("drep_id"),
                rs.getString("drep_name"),
                rs.getString("objectives"),
                rs.getLong("voting_power"),
                rs.getInt("votes_yes"),
                rs.getInt("votes_no"),
                rs.getInt("votes_abstain"),
                rs.getInt("recent_actions")),
        CLOSED_EPOCHS,
        MAX_ACTIONS);
  }

  /**
   * Open actions and those that ended within the last epochs. A voter can vote again until an action
   * ends, only the latest vote counts. The parameters are the DRep and the pool whose votes are
   * reported per action, each may be null.
   */
  public GovernanceOverview getOverview(String drepId, String poolHash) {
    int epoch = jdbcTemplate.queryForObject("select max(epoch_no) from block", Integer.class);
    // -1 matches no voter, so the query is the same with and without a DRep or pool
    long drep = drepId == null ? -1 : java.util.Objects.requireNonNullElse(idOf("drep_hash", drepId), -1L);
    long pool = poolHash == null ? -1 : java.util.Objects.requireNonNullElse(idOf("pool_hash", poolHash), -1L);

    List<String> names =
        drep < 0
            ? List.of()
            : jdbcTemplate.queryForList(
                """
								select d.given_name
								from drep_registration dr
								join off_chain_vote_data o on o.voting_anchor_id = dr.voting_anchor_id
								join off_chain_vote_drep_data d on d.off_chain_vote_data_id = o.id
								where dr.drep_hash_id = ? and d.given_name is not null
								order by dr.id desc limit 1
								""",
                String.class,
                drep);

    List<GovAction> actions =
        jdbcTemplate.query(
            """
						with acts as (
							select gap.id, tx.hash, gap.index, gap.type::text "type", gap.expiration, gap.ratified_epoch, gap.enacted_epoch,
								gap.dropped_epoch, gap.expired_epoch, gap.voting_anchor_id, gap.description, b.epoch_no submitted_epoch, b.time submitted_time
							from gov_action_proposal gap
							join tx on tx.id = gap.tx_id
							join block b on b.id = tx.block_id
							where coalesce(gap.enacted_epoch, gap.dropped_epoch, gap.expired_epoch, 999999) >= ? - ?
							order by gap.id desc limit ?
						),
						latest as (
							select distinct on (vp.gov_action_proposal_id, vp.voter_role, coalesce(vp.drep_voter, vp.pool_voter, vp.committee_voter))
								vp.gov_action_proposal_id, vp.voter_role::text voter_role, vp.vote::text vote, vp.drep_voter, vp.pool_voter, vp.voting_anchor_id
							from voting_procedure vp
							where vp.gov_action_proposal_id in (select id from acts) and vp.invalid is null
							order by vp.gov_action_proposal_id, vp.voter_role, coalesce(vp.drep_voter, vp.pool_voter, vp.committee_voter), vp.id desc
						)
						select
							encode(a.hash, 'hex') tx_hash, a.index, a."type", a.submitted_epoch, a.expiration,
								extract(epoch from a.submitted_time)::bigint submitted_unix,
							a.ratified_epoch, a.enacted_epoch, a.dropped_epoch, a.expired_epoch,
							max(ga.title) title, max(ga.abstract) summary, max(va.url) anchor_url,
							case when a."type" = 'TreasuryWithdrawals'
								then (select sum((w->>1)::numeric) from jsonb_array_elements(a.description->'contents'->0) w) end withdrawal,
							count(*) filter (where l.voter_role = 'DRep' and l.vote = 'Yes') drep_yes,
							count(*) filter (where l.voter_role = 'DRep' and l.vote = 'No') drep_no,
							count(*) filter (where l.voter_role = 'DRep' and l.vote = 'Abstain') drep_abstain,
							count(*) filter (where l.voter_role = 'SPO' and l.vote = 'Yes') pool_yes,
							count(*) filter (where l.voter_role = 'SPO' and l.vote = 'No') pool_no,
							count(*) filter (where l.voter_role = 'SPO' and l.vote = 'Abstain') pool_abstain,
							count(*) filter (where l.voter_role = 'ConstitutionalCommittee' and l.vote = 'Yes') committee_yes,
							count(*) filter (where l.voter_role = 'ConstitutionalCommittee' and l.vote = 'No') committee_no,
							max(l.vote) filter (where l.drep_voter = ?) drep_vote,
							max(l.vote) filter (where l.pool_voter = ?) pool_vote,
							(select pva.url from latest pl join voting_anchor pva on pva.id = pl.voting_anchor_id
								where pl.gov_action_proposal_id = a.id and pl.pool_voter = ?) pool_vote_anchor_url
						from acts a
						left join voting_anchor va on va.id = a.voting_anchor_id
						-- one row at most: more than one would multiply the votes counted below
						left join lateral (
							select gad.title, gad.abstract
							from off_chain_vote_data ovd
							join off_chain_vote_gov_action_data gad on gad.off_chain_vote_data_id = ovd.id
							where ovd.voting_anchor_id = a.voting_anchor_id
							order by ovd.id desc limit 1
						) ga on true
						left join latest l on l.gov_action_proposal_id = a.id
						group by a.id, a.hash, a.index, a."type", a.submitted_epoch, a.submitted_time, a.expiration, a.ratified_epoch, a.enacted_epoch,
							a.dropped_epoch, a.expired_epoch, a.description
						order by a.id desc
						""",
            (rs, rowNum) ->
                new GovAction(
                    rs.getString("tx_hash"),
                    rs.getInt("index"),
                    rs.getString("type"),
                    rs.getString("title"),
                    rs.getString("summary"),
                    rs.getString("anchor_url"),
                    rs.getInt("submitted_epoch"),
                    rs.getInt("expiration"),
                    rs.getObject("ratified_epoch", Integer.class),
                    rs.getObject("enacted_epoch", Integer.class),
                    rs.getObject("dropped_epoch", Integer.class),
                    rs.getObject("expired_epoch", Integer.class),
                    rs.getObject("withdrawal") == null ? null : rs.getBigDecimal("withdrawal").longValue(),
                    rs.getInt("drep_yes"),
                    rs.getInt("drep_no"),
                    rs.getInt("drep_abstain"),
                    rs.getInt("pool_yes"),
                    rs.getInt("pool_no"),
                    rs.getInt("pool_abstain"),
                    rs.getInt("committee_yes"),
                    rs.getInt("committee_no"),
                    rs.getString("drep_vote"),
                    rs.getString("pool_vote"),
                    rs.getString("pool_vote_anchor_url"),
                    rs.getLong("submitted_unix")),
            epoch,
            CLOSED_EPOCHS,
            MAX_ACTIONS,
            drep,
            pool,
            pool);

    return new GovernanceOverview(epoch, names.isEmpty() ? null : names.get(0), actions);
  }
}
