package de.peterspace.cardanodbsyncapi.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Value;

/** A delegated representative that votes with the ADA delegated to it. */
@Value
public class DRepInfo {
  @NotNull String drepId;

  /** Name the DRep registered with, null if it has none or its metadata could not be read. */
  String name;

  /** Lovelace delegated to the DRep in the latest snapshot. */
  @NotNull long votingPower;
}
