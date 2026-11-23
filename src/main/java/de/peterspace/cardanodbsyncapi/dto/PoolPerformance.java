package de.peterspace.cardanodbsyncapi.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Value;

/** Blocks and returns of a pool over the last epochs. */
@Value
public class PoolPerformance {
  @NotNull String poolHash;

  /** First and last epoch the returns were earned in. */
  @NotNull int fromEpoch;

  @NotNull int toEpoch;

  /**
   * What delegators of the pool earned in these epochs, per year in percent of their stake. Null if
   * the pool paid no rewards in them.
   */
  Double roa;

  /** All staking rewards of the network in the same epochs, per year in percent of all stake. */
  @NotNull double networkRoa;

  /** Blocks per epoch from fromEpoch to the current one, epochs without a block included. */
  @NotNull List<EpochBlocks> blocks;

  @NotNull long lifetimeBlocks;
}
