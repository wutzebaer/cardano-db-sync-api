package de.peterspace.cardanodbsyncapi.dto;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import lombok.Value;

@Value
public class OwnerInfo {
	@NotNull
	String address;
	@NotNull
	long amount;
	@NotNull
	List<String> maNames;
}
