package de.peterspace.cardanodbsyncapi.utils;

import lombok.experimental.UtilityClass;

@UtilityClass
public class Utils {

	public static enum AddressType {
		STAKE_ADDRESS, SERVICE_ADDRESS, FULL_ADDRESS
	}

	public static AddressType determineAddressType(String address) {
		address = address.replace("_test", "");
		if (address.startsWith("stake")) {
			return AddressType.STAKE_ADDRESS;
		} else if (address.startsWith("addr") && address.length() == 103) {
			return AddressType.FULL_ADDRESS;
		} else if (address.startsWith("addr") && address.length() == 58) {
			return AddressType.SERVICE_ADDRESS;
		} else {
			throw new IllegalArgumentException(String.format("Cannot determin addresstype of %s", address));
		}
	}

}
