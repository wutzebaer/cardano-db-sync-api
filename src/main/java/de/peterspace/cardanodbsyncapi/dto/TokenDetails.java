package de.peterspace.cardanodbsyncapi.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Value;

@Value
public class TokenDetails {
	@NotNull
	long slotNo;
	@NotNull
	String maPolicyId;
	@NotNull
	String maName;
	@NotNull
	String fingerprint;
	String metadata;
	@NotNull
	String maPolicyScript;
	@NotNull
	String txHash;
	@NotNull
	long totalSupply;
}
