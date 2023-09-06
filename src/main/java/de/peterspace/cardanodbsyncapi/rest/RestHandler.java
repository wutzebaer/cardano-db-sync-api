package de.peterspace.cardanodbsyncapi.rest;

import java.util.List;

import org.apache.commons.codec.DecoderException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.peterspace.cardanodbsyncapi.dto.PoolInfo;
import de.peterspace.cardanodbsyncapi.dto.ReturnAddress;
import de.peterspace.cardanodbsyncapi.dto.StakeInfo;
import de.peterspace.cardanodbsyncapi.dto.TokenDetails;
import de.peterspace.cardanodbsyncapi.dto.TokenListItem;
import de.peterspace.cardanodbsyncapi.dto.Utxo;
import de.peterspace.cardanodbsyncapi.service.CardanoDbSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequiredArgsConstructor
@RequestMapping("/cardanoDbSyncApi")
@Slf4j
public class RestHandler {

	private static final String SAMPLE_STAKE_ADDRESS = "stake1uywupacwlt7vdpgj26vmtpy3fx5d3tv6v4zrdppaxkmls5sak6xqg";
	private static final String SAMPLE_ADDRESS = "addr1qx8lsj4menq5s7w5f8jupm64n9d3aamvcppllujwse473636fhhttcg3x8kfhm6qqpvujfhgmu8jww3mfn49m3fkjssqhx0348";
	private static final String SAMPLE_POLICY_ID = "d1edc4dfb4f5f7fb240239ad64a4730c2fd4744eda3c8a7d0fff1f92";
	private static final String SAMPLE_ASSET_NAME = "504f524b5958383835";
	private final CardanoDbSyncService cardanoDbSyncService;

	@Operation(summary = "Get infos where address is staked to")
	@GetMapping(value = "/{address}/stakeInfo")
	public StakeInfo getStakeInfo(@Parameter(example = SAMPLE_ADDRESS) @PathVariable String address) {
		return cardanoDbSyncService.getStakeInfo(address);
	}

	@Operation(summary = "Find utxos of given address including multi assets")
	@GetMapping(value = "/{address}/utxos")
	public List<Utxo> getUtxos(@Parameter(example = SAMPLE_ADDRESS) @PathVariable String address) {
		return cardanoDbSyncService.getUtxos(address);
	}

	@Operation(summary = "Find the first known address with the same stake address, which should not be mangled")
	@GetMapping(value = "/{address}/returnAddress")
	public ReturnAddress getReturnAddress(@Parameter(example = SAMPLE_STAKE_ADDRESS) @PathVariable String address) {
		return cardanoDbSyncService.getReturnAddress(address);
	}

	@Operation(summary = "getTokenList")
	@GetMapping(value = "/token")
	public List<TokenListItem> getTokenList(@RequestParam(required = false) Long afterMintid, @RequestParam(required = false) Long beforeMintid, @RequestParam(required = false) String filter) throws DecoderException {
		return cardanoDbSyncService.getTokenList(afterMintid, beforeMintid, filter);
	}

	@Operation(summary = "getTokenDetails")
	@GetMapping(value = "/token/{policyId}/{assetName}")
	public TokenDetails getTokenList(
			@Parameter(example = SAMPLE_POLICY_ID) @PathVariable String policyId,
			@Parameter(example = SAMPLE_ASSET_NAME) @PathVariable String assetName) throws DecoderException {
		return cardanoDbSyncService.getTokenDetails(policyId, assetName);
	}

	@Operation(summary = "getPoolList")
	@GetMapping(value = "/poolList")
	public List<PoolInfo> getPoolList() {
		return cardanoDbSyncService.getPoolList();
	}

}
