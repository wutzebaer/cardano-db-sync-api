package de.peterspace.cardanodbsyncapi.dto;

import lombok.Value;

@Value
public class TokenDetails {
	long slotNo;
	String maPolicyId;
	String maName;
	String fingerprint;
	String metadata;
	String maPolicyScript;
	String txHash;
	long totalSupply;
}
