package de.peterspace.cardanodbsyncapi.dto;

import lombok.Value;

@Value
public class LiquidityPool {
	String policyA;
	String nameA;
	long quantityA;
	
	String policyB;
	String nameB;
	long quantityB;
}
