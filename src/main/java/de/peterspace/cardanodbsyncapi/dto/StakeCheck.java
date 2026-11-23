package de.peterspace.cardanodbsyncapi.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Value;

/** The staking state of a stake address: registration, pool, DRep and rewards. */
@Value
public class StakeCheck {
  @NotNull String stakeAddress;
  @NotNull int currentEpoch;

  /** False for a stake key that was never registered or has been deregistered. */
  @NotNull boolean registered;

  /** Lovelace in unspent outputs of the stake address. */
  @NotNull long balance;

  /** Lovelace of rewards that can be withdrawn now. */
  @NotNull long rewardsAvailable;

  /**
   * The DRep the stake address delegates its vote to: a drep1… id, drep_always_abstain or
   * drep_always_no_confidence. Null without a vote delegation, rewards cannot be withdrawn then.
   */
  String drepId;

  /** Null if the stake address is not delegated to a pool. */
  PoolCheck pool;
}
