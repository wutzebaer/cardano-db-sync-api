package de.peterspace.cardanodbsyncapi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Value;

/** A handle held by a stake address. */
@Value
public class WalletHandle {
  /** The handle without the leading "$", named "address" for compatibility with older clients */
  @NotNull String address;

  @Schema(
      description =
          "The handle wallets show for this stake address, exactly one per stake address. Same order"
              + " as handle.me: the default set in the handle, then the OG number, the shortest, the"
              + " first minted and the alphabetically first.")
  @JsonProperty("default")
  boolean defaultHandle;
}
