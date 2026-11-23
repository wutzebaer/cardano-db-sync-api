package de.peterspace.cardanodbsyncapi.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Value;

/** Royalties a policy defines with its CIP-27 royalty token. */
@Value
public class PolicyRoyalties {
  /** Share of a sale as written on chain, e.g. "0.05" for 5 %. */
  String rate;

  /** Address receiving the royalties. */
  String address;

  /** CIP-27 only counts a royalty token that was minted before any other token of its policy. */
  @NotNull boolean firstMint;
}
