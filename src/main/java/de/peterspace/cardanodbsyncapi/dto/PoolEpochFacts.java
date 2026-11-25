package de.peterspace.cardanodbsyncapi.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;

/** Numbers around one epoch of a pool, for the little facts a pool tells its delegators. */
@Value
@Builder
public class PoolEpochFacts {
  @NotNull String poolHash;

  @NotNull int epoch;

  /** Blocks the pool made in the epoch. */
  @NotNull int blocks;

  /** Transactions in the pool's blocks of the epoch. */
  @NotNull long transactions;

  /** Lovelace the transactions in the pool's blocks sent to their outputs. */
  @NotNull long lovelaceMoved;

  /** Fees of the transactions in the pool's blocks, in lovelace. */
  @NotNull long fees;

  /** Lovelace sent by the largest transaction in the pool's blocks, null without a transaction. */
  Long largestTransaction;

  /** Average size of the pool's blocks against the size limit, 0 to 1, null without a block. */
  Double blockFill;

  /** Number of the pool's last block of the epoch on the chain, null without a block. */
  Long lastBlockNo;

  /** Outputs carrying native tokens in the pool's blocks. */
  @NotNull long tokenOutputs;

  /** Smart contract runs (redeemers) in the pool's blocks. */
  @NotNull long scriptRuns;

  /** Token kinds minted in the pool's blocks. */
  @NotNull long tokensMinted;

  /** All blocks of the network in the epoch. */
  @NotNull long networkBlocks;

  /** Epoch the pool's first registration took effect, null for an unknown pool. */
  Integer registeredEpoch;

  /** Blocks the pool made up to and including the epoch. */
  @NotNull long lifetimeBlocks;

  /** Epochs up to and including this one in which the pool made a block. */
  @NotNull int epochsWithBlocks;

  /** Epochs in a row with a block, ending with this one; 0 when it has none. */
  @NotNull int blockStreak;

  /** Stake of the pool in the epoch before, in lovelace. */
  @NotNull long previousStake;

  /** Delegators of the pool in the epoch before. */
  @NotNull int previousDelegators;

  /** Place of the pool among all pools with stake in the epoch, the largest first. Null without stake. */
  Integer stakeRank;

  /** Pools with stake in the epoch. */
  @NotNull int rankedPools;

  /** Rewards the pool's delegators received so far, in lovelace. */
  @NotNull long delegatorRewards;
}
