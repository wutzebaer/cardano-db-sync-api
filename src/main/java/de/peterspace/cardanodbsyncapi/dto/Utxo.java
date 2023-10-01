package de.peterspace.cardanodbsyncapi.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Value;

@Value
public class Utxo {
	@NotNull
	String txHash;
	@NotNull
	int txIndex;
	String maPolicyId;
	String maName;
	@NotNull
	long value;
	@NotNull
	String owningAddress;
	@NotNull
	String sourceAddress;
}
