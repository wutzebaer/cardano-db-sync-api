package de.peterspace.cardanodbsyncapi.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Value;

@Value
public class StakeReturnAddress {
  @NotNull String stakeAddress;
  @NotNull String address;
}
