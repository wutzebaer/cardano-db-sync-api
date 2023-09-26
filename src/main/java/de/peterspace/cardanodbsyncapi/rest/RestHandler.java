package de.peterspace.cardanodbsyncapi.rest;

import java.util.List;

import org.apache.commons.codec.DecoderException;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataAccessException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.peterspace.cardanodbsyncapi.dto.AccountStatementRow;
import de.peterspace.cardanodbsyncapi.dto.EpochStake;
import de.peterspace.cardanodbsyncapi.dto.OwnerInfo;
import de.peterspace.cardanodbsyncapi.dto.PoolInfo;
import de.peterspace.cardanodbsyncapi.dto.ReturnAddress;
import de.peterspace.cardanodbsyncapi.dto.StakeAddress;
import de.peterspace.cardanodbsyncapi.dto.StakeInfo;
import de.peterspace.cardanodbsyncapi.dto.TokenDetails;
import de.peterspace.cardanodbsyncapi.dto.TokenListItem;
import de.peterspace.cardanodbsyncapi.dto.Utxo;
import de.peterspace.cardanodbsyncapi.service.CardanoDbSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/cardanoDbSyncApi")
public class RestHandler {

	private static final String SAMPLE_STAKE_ADDRESS = "stake1u8wmu7jc0e4a6fn5haflczfjy6aagwhsxh6w5p7hsyt8jeshhy0rn";
	private static final String SAMPLE_ADDRESS = "addr1qx8lsj4menq5s7w5f8jupm64n9d3aamvcppllujwse473636fhhttcg3x8kfhm6qqpvujfhgmu8jww3mfn49m3fkjssqhx0348";
	private static final String SAMPLE_POLICY_ID = "d1edc4dfb4f5f7fb240239ad64a4730c2fd4744eda3c8a7d0fff1f92";
	private static final String SAMPLE_ASSET_NAME = "504f524b5958383835";
	private final CardanoDbSyncService cardanoDbSyncService;

	@Operation(summary = "Get infos where address is staked to")
	@GetMapping(value = "/{stakeAddress}/stakeInfo")
	@Cacheable("getStakeInfo")
	public StakeInfo getStakeInfo(@Parameter(example = SAMPLE_STAKE_ADDRESS) @PathVariable String stakeAddress) {
		return cardanoDbSyncService.getStakeInfo(stakeAddress);
	}

	@Operation(summary = "Find utxos of given address or stakeAddress including multi assets")
	@GetMapping(value = "/{address}/utxos")
	@Cacheable("getUtxos")
	public List<Utxo> getUtxos(@Parameter(example = SAMPLE_STAKE_ADDRESS) @PathVariable String address) {
		return cardanoDbSyncService.getUtxos(address);
	}

	@Operation(summary = "Find the first known address with the same stake address, which should not be mangled")
	@GetMapping(value = "/{stakeAddress}/returnAddress")
	@Cacheable("getReturnAddress")
	public ReturnAddress getReturnAddress(@Parameter(example = SAMPLE_STAKE_ADDRESS) @PathVariable String stakeAddress) {
		return cardanoDbSyncService.getReturnAddress(stakeAddress);
	}

	@Operation(summary = "Find stakeAddress of address")
	@GetMapping(value = "/{address}/stakeAddress")
	@Cacheable("getStakeAddress")
	public StakeAddress getStakeAddress(@Parameter(example = SAMPLE_ADDRESS) @PathVariable String address) {
		return cardanoDbSyncService.getStakeAddress(address);
	}

	@Operation(summary = "Find stakeAddress by stakeAddressHash")
	@GetMapping(value = "/stakeAddress/{stakeAddressHash}")
	@Cacheable("getStakeAddressByHash")
	public StakeAddress getStakeAddressByHash(@Parameter(example = "e1ddbe7a587e6bdd2674bf53fc093226bbd43af035f4ea07d781167966") @PathVariable String stakeAddressHash) throws DecoderException {
		return cardanoDbSyncService.getStakeAddressByHash(stakeAddressHash);
	}

	@Operation(summary = "getTokenList")
	@GetMapping(value = "/token")
	@Cacheable("getTokenList")
	public List<TokenListItem> getTokenList(
			@RequestParam(required = false) Long afterMintid,
			@RequestParam(required = false) Long beforeMintid,
			@Parameter(example = SAMPLE_POLICY_ID) @RequestParam(required = false) String filter) throws DecoderException {
		return cardanoDbSyncService.getTokenList(afterMintid, beforeMintid, filter);
	}

	@Operation(summary = "getAddressTokenList")
	@GetMapping(value = "/{address}/token")
	@Cacheable("getAddressTokenList")
	public List<TokenListItem> getAddressTokenList(
			@Parameter(example = SAMPLE_STAKE_ADDRESS) @PathVariable String address) throws DecoderException {
		return cardanoDbSyncService.getAddressTokenList(address);
	}

	@Operation(summary = "Get all transactions for an address or stakeAddress")
	@GetMapping(value = "/{address}/statement")
	@Cacheable("getStatement")
	public List<AccountStatementRow> getStatement(
			@Parameter(example = SAMPLE_STAKE_ADDRESS) @PathVariable String address) {
		return cardanoDbSyncService.getStatement(address);
	}

	@Operation(summary = "getTokenDetails")
	@GetMapping(value = "/token/{policyId}/{assetName}")
	@Cacheable("getTokenDetails")
	public TokenDetails getTokenDetails(
			@Parameter(example = SAMPLE_POLICY_ID) @PathVariable String policyId,
			@Parameter(example = SAMPLE_ASSET_NAME) @PathVariable String assetName) throws DecoderException {
		return cardanoDbSyncService.getTokenDetails(policyId, assetName);
	}

	@Operation(summary = "getPoolList")
	@GetMapping(value = "/poolList")
	@Cacheable("getPoolList")
	public List<PoolInfo> getPoolList() {
		return cardanoDbSyncService.getPoolList();
	}

	@Operation(summary = "getEpochStake")
	@GetMapping(value = "/epochStake/{poolHash}/{epoch}")
	@Cacheable("getEpochStake")
	public List<EpochStake> getEpochStake(
			@Parameter(example = "pool180fejev4xgwe2y53ky0pxvgxr3wcvkweu6feq5mdljfzcsmtg6u") @PathVariable String poolHash,
			@Parameter(example = "432") @PathVariable int epoch) {
		return cardanoDbSyncService.getEpochStake(poolHash, epoch);
	}

	@Operation(summary = "Get all token owners of a policyId, values get updated twice a day")
	@GetMapping(value = "/policy/{policyId}/owners")
	@Cacheable("getOwners")
	public List<OwnerInfo> getOwners(@Parameter(example = SAMPLE_POLICY_ID) @PathVariable String policyId) throws DecoderException {
		return cardanoDbSyncService.getOwners(policyId);
	}

}
