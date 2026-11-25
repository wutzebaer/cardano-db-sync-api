package de.peterspace.cardanodbsyncapi.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Value;

/** A block on the chain and the pool that made it. */
@Value
public class SlotBlock {
  @NotNull long slot;

  @NotNull long blockNo;

  /** Bech32 id of the pool that made the block, null for a block not made by a pool. */
  String poolHash;

  /** Ticker of the pool from its latest metadata, null without metadata. */
  String ticker;
}
