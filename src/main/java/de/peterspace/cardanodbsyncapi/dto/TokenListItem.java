package de.peterspace.cardanodbsyncapi.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Value;

@Value
public class TokenListItem {
	Long maMintId;
	Long slotNo;
	@NotNull
	String maPolicyId;
	@NotNull
	String maName;
	@NotNull
	String maFingerprint;
	@NotNull
	long quantity;
	@NotNull
	String name;
	@NotNull
	String image;
}
