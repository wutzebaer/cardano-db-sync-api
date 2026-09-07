package de.peterspace.cardanodbsyncapi.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Value;

@Value
public class OwnerInfo {
  @NotNull String address;
  @NotNull long amount;
  @NotNull List<String> maNames;
}
