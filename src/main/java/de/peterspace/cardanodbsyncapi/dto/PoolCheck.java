package de.peterspace.cardanodbsyncapi.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Value;

/** What a delegator should know about a pool right now. */
@Value
public class PoolCheck {
  @NotNull String poolHash;
  String ticker;
  String name;
  String homepage;

  /** Share of the rewards the pool keeps, 0.01 is 1 %. In effect this epoch. */
  @NotNull double margin;

  /** Lovelace the pool takes from its rewards every epoch before sharing them. In effect this epoch. */
  @NotNull long fixedCost;

  @NotNull long pledge;

  /** Margin of a registration that is not in effect yet, null if nothing changes. */
  Double nextMargin;

  /** Fixed cost of a registration that is not in effect yet, null if nothing changes. */
  Long nextFixedCost;

  /** Epoch the pool retires in, also when that epoch has passed. Null for a pool that goes on. */
  Integer retiringEpoch;

  /** Lovelace delegated to the pool in the stake snapshot of this epoch. */
  @NotNull long activeStake;

  @NotNull int delegators;

  /** Lovelace above which a pool earns no more rewards for additional stake. */
  @NotNull long saturationStake;
}
