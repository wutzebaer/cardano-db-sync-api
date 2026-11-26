package de.peterspace.cardanodbsyncapi.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Value;

/**
 * The protocol parameters of the current epoch a page needs to build a transaction: what it costs,
 * how large it may be and what its outputs have to hold.
 */
@Value
public class ProtocolParams {
  @NotNull int epoch;

  /** Lovelace per byte of a transaction. */
  @NotNull long minFeeA;

  /** Lovelace every transaction costs on top. */
  @NotNull long minFeeB;

  /** Deposit for registering a stake key, in lovelace. */
  @NotNull long keyDeposit;

  @NotNull long poolDeposit;

  /** Lovelace an output has to hold per byte of its size. */
  @NotNull long coinsPerUtxoByte;

  /** Bytes a transaction may have. */
  @NotNull long maxTxSize;

  /** Bytes the value of one output may have. */
  @NotNull long maxValueSize;

  /** Lovelace per unit of memory a script run may use. */
  @NotNull double priceMem;

  /** Lovelace per step a script run may use. */
  @NotNull double priceStep;

  /** What all script runs of one transaction may use together. */
  @NotNull long maxTxExMem;

  @NotNull long maxTxExSteps;

  /** Lovelace per byte of a script a transaction refers to. */
  @NotNull double minFeeRefScriptCostPerByte;

  /** Collateral in percent of the fee. */
  @NotNull int collateralPercent;

  /** Outputs a transaction may put up as collateral at most. */
  @NotNull int maxCollateralInputs;
}
