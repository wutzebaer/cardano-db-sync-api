package de.peterspace.cardanodbsyncapi.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Value;

@Value
public class PoolInfo {
	@NotNull
	String tickerName;
	@NotNull
	String poolHash;
}
