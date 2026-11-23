package de.peterspace.cardanodbsyncapi.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Value;

@Value
public class StakeHandle {
  @NotNull String stakeAddress;

  /** Without the leading "$" */
  @NotNull String handle;
}
