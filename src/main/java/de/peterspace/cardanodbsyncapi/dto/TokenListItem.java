package de.peterspace.cardanodbsyncapi.dto;

import lombok.Value;

@Value
public class TokenListItem {
	long maMintId;
	long slotNo;
	String maPolicyId;
	String maName;
	long quantity;
}
