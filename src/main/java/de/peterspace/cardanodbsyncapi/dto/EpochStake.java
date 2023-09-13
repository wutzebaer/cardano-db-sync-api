package de.peterspace.cardanodbsyncapi.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Value;

@Value
public class EpochStake {
	@NotNull
	String stakeAddress;
	@NotNull
	long amount;
}
