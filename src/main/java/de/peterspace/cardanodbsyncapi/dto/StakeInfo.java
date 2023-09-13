package de.peterspace.cardanodbsyncapi.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Value;

@Value
public class StakeInfo {
	@NotNull
	long stake;
	@NotNull
	String poolHash;
	@NotNull
	String tickerName;
	@NotNull
	long totalStake;
}
