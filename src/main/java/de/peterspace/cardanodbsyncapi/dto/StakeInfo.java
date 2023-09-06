package de.peterspace.cardanodbsyncapi.dto;

import lombok.Value;

@Value
public class StakeInfo {
	long stake;
	String poolHash;
	String tickerName;
	long totalStake;
}
