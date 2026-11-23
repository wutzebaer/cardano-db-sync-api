package de.peterspace.cardanodbsyncapi.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Value;

@Value
public class EpochBlocks {
  @NotNull int epoch;
  @NotNull int blocks;
}
