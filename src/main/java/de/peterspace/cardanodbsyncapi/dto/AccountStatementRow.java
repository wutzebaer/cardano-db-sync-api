package de.peterspace.cardanodbsyncapi.dto;

import java.util.Date;

import lombok.Value;

@Value
public class AccountStatementRow {
	Date timestamp;
	int epoch;
	String txHash;
	long withdrawn;
	long rewards;
	long out;
	long in;
	long change;
	long sum;
	String[] operations;
}
