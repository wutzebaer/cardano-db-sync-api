package de.peterspace.cardanodbsyncapi.dto;

import lombok.Value;

@Value
public class EpochStake {
	String stakeAddress;
	long amount;
}
