package de.peterspace.cardanodbsyncapi.dto;

import java.util.Date;

import jakarta.validation.constraints.NotNull;
import lombok.Value;

@Value
public class AccountStatementRow {
	@NotNull
	Date timestamp;
	@NotNull
	int epoch;
	@NotNull
	String txHash;
	@NotNull
	long withdrawn;
	@NotNull
	long rewards;
	@NotNull
	long out;
	@NotNull
	long in;
	@NotNull
	long change;
	@NotNull
	long sum;
	@NotNull
	String[] operations;
}
