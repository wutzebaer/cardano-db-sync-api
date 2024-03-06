package de.peterspace.cardanodbsyncapi.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Value;

@Value
public class TxOut {
	@NotNull
	String targetAddress;
	@NotNull
	long value;
}
