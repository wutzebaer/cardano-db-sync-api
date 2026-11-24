package de.peterspace.cardanodbsyncapi.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Value;

/** A governance action with its outcome so far and the votes cast on it. */
@Value
public class GovAction {
  @NotNull String txHash;
  @NotNull int index;

  /**
   * TreasuryWithdrawals, InfoAction, ParameterChange, NewConstitution, NewCommittee,
   * HardForkInitiation or NoConfidence.
   */
  @NotNull String type;

  /** From the metadata the action points to, null if db-sync could not fetch it. */
  String title;

  /** Summary from the same metadata. */
  String summary;

  /** Where the metadata is, an https or ipfs address. */
  String anchorUrl;

  @NotNull int submittedEpoch;

  /** Last epoch votes are counted in. */
  @NotNull int expiresEpoch;

  Integer ratifiedEpoch;
  Integer enactedEpoch;
  Integer droppedEpoch;
  Integer expiredEpoch;

  /** Lovelace a treasury withdrawal asks for, null for other actions. */
  Long withdrawal;

  /** Voters by their latest vote. Numbers of voters, not weighted by stake. */
  @NotNull int drepYes;

  @NotNull int drepNo;
  @NotNull int drepAbstain;
  @NotNull int poolYes;
  @NotNull int poolNo;
  @NotNull int poolAbstain;
  @NotNull int committeeYes;
  @NotNull int committeeNo;

  /** Yes, No or Abstain of the DRep asked for, null if it did not vote or none was asked for. */
  String drepVote;

  /** Yes, No or Abstain of the pool asked for, null if it did not vote or none was asked for. */
  String poolVote;

  /** Where the pool explains its vote, null without a rationale. */
  String poolVoteAnchorUrl;

  /** When the action was submitted, in seconds since 1970 (UTC). */
  @NotNull long submittedUnix;
}
