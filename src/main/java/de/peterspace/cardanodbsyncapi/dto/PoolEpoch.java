package de.peterspace.cardanodbsyncapi.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Value;

/** What a pool did in one epoch, next to what its stake let expect. */
@Value
public class PoolEpoch {
  @NotNull String poolHash;

  @NotNull int epoch;

  /** Blocks the pool made in the epoch so far. */
  @NotNull int blocks;

  /** Stake that counted for the pool in the epoch, in lovelace. Zero while the epoch has no snapshot yet. */
  @NotNull long stake;

  /** Stake that counted for all pools in the epoch, in lovelace. */
  @NotNull long totalStake;

  /** Delegators the stake came from. */
  @NotNull int delegators;

  /**
   * Blocks a pool of this share of the stake makes in an average epoch: the 21,600 blocks of an
   * epoch times the share. Null while the epoch has no snapshot yet.
   */
  Double expectedBlocks;
}
