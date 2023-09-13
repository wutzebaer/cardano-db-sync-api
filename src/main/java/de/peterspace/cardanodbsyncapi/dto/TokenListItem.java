package de.peterspace.cardanodbsyncapi.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Value;

@Value
public class TokenListItem {
	@NotNull
	long maMintId;
	@NotNull
	long slotNo;
	@NotNull
	String maPolicyId;
	@NotNull
	String maName;
	@NotNull
	long quantity;
}
