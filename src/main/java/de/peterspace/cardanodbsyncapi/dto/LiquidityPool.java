package de.peterspace.cardanodbsyncapi.dto;

import lombok.Value;

@Value
public class LiquidityPool {
	Utxo assetA;
	Utxo assetB;
}
