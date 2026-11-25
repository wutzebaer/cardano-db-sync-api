package de.peterspace.cardanodbsyncapi.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Value;

/** Who holds a token right now, and how much of it. */
@Value
public class TokenHolder {
  /** The stake address of the holder, or the address itself where it has no stake part. */
  @NotNull String address;

  @NotNull long quantity;
}
