package de.peterspace.cardanodbsyncapi.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Value;

/**
 * One asset of an output at a script address. An output comes as one row for its ADA and one row
 * per token, like {@link Utxo}. The row of the ADA carries the inline datum.
 */
@Value
public class ScriptOutput {
  @NotNull String txHash;

  @NotNull int txIndex;

  /** Empty on the row of the ADA. */
  String maPolicyId;

  String maName;

  /** Lovelace on the row of the ADA, the quantity on the row of a token. */
  @NotNull long value;

  /** The inline datum of the output as CBOR hex, on the row of the ADA only. */
  String datum;

  /** Whether the output has been spent since. */
  @NotNull boolean spent;
}
