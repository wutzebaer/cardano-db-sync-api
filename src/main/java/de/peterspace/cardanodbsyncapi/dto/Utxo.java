package de.peterspace.cardanodbsyncapi.dto;

import lombok.Value;

@Value
public class Utxo {
	String txHash;
	int txIndex;
	String maPolicyId;
	String maName;
	long value;
	String sourceAddress;
}
