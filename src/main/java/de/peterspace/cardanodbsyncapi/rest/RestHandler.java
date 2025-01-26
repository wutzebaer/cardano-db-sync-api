package de.peterspace.cardanodbsyncapi.rest;

import java.util.List;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataAccessException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.peterspace.cardanodbsyncapi.dto.AccountStatementRow;
import de.peterspace.cardanodbsyncapi.dto.EpochStake;
import de.peterspace.cardanodbsyncapi.dto.LiquidityPool;
import de.peterspace.cardanodbsyncapi.dto.OwnerInfo;
import de.peterspace.cardanodbsyncapi.dto.PoolInfo;
import de.peterspace.cardanodbsyncapi.dto.ReturnAddress;
import de.peterspace.cardanodbsyncapi.dto.StakeAddress;
import de.peterspace.cardanodbsyncapi.dto.StakeInfo;
import de.peterspace.cardanodbsyncapi.dto.TokenDetails;
import de.peterspace.cardanodbsyncapi.dto.TokenListItem;
import de.peterspace.cardanodbsyncapi.dto.TxOut;
import de.peterspace.cardanodbsyncapi.dto.Utxo;
import de.peterspace.cardanodbsyncapi.service.CardanoDbSyncService;
import de.peterspace.cardanodbsyncapi.service.MinswapService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/")
public class RestHandler {

	private static final String SAMPLE_STAKE_ADDRESS = "stake1u8wmu7jc0e4a6fn5haflczfjy6aagwhsxh6w5p7hsyt8jeshhy0rn";
	private static final String SAMPLE_ADDRESS = "addr1qx8lsj4menq5s7w5f8jupm64n9d3aamvcppllujwse473636fhhttcg3x8kfhm6qqpvujfhgmu8jww3mfn49m3fkjssqhx0348";
	private static final String SAMPLE_POLICY_ID = "89267e9a35153a419e1b8ffa23e511ac39ea4e3b00452e9d500f2982";
	private static final String SAMPLE_ASSET_NAME = "436176616c6965724b696e67436861726c6573";
	private final CardanoDbSyncService cardanoDbSyncService;
	private final MinswapService minswapService;

	@Operation(summary = "Get infos where address is staked to")
	@GetMapping(value = "/{stakeAddress}/stakeInfo")
	@Cacheable("getStakeInfo")
	public StakeInfo getStakeInfo(@Parameter(example = SAMPLE_STAKE_ADDRESS) @PathVariable String stakeAddress) {
		return cardanoDbSyncService.getStakeInfo(stakeAddress);
	}

	@Operation(summary = "Find utxos of given address or stakeAddress including multi assets")
	@GetMapping(value = "/{address}/utxos")
	@Cacheable(value = "getUtxos", sync = true)
	public List<Utxo> getUtxos(@Parameter(example = SAMPLE_STAKE_ADDRESS) @PathVariable String address) throws DecoderException {
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

	@Operation(summary = "Find stakeAddressHash by stakeAddress")
	@GetMapping(value = "/stakeHash/{stakeAddress}")
	@Cacheable("getStakeHashByAddress")
	public StakeAddress getStakeHashByAddress(@Parameter(example = SAMPLE_STAKE_ADDRESS) @PathVariable String stakeAddress) throws DecoderException {
		return cardanoDbSyncService.getStakeHashByAddress(stakeAddress);
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

	@Operation(summary = "Get all handles from a stakeAddress")
	@GetMapping(value = "/{stakeAddress}/handles")
	@Cacheable("getHandles")
	public List<StakeAddress> getHandles(
			@Parameter(example = SAMPLE_STAKE_ADDRESS) @PathVariable String stakeAddress) throws DecoderException {
		return cardanoDbSyncService.getHandles(stakeAddress);
	}

	@Operation(summary = "Get address for handle")
	@GetMapping(value = "/handles/{handle}")
	@Cacheable("getAddressByHandle")
	public StakeAddress getAddressByHandle(
			@Parameter(example = "petergrossmann") @PathVariable String handle) throws DecoderException {
		return cardanoDbSyncService.getAddressByHandle(handle);
	}

	public static record GetLastMintRequest(String stakeAddress, List<String> policyIds) {
	}

	@Operation(summary = "Get last minted tokens for stakeAddress and policy ids")
	@PostMapping(value = "/lastMint")
	@Cacheable("getLastMint")
	public List<TokenDetails> getLastMint(
			@io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = { @ExampleObject(value = """
					{
					  "stakeAddress": "stake1u8wmu7jc0e4a6fn5haflczfjy6aagwhsxh6w5p7hsyt8jeshhy0rn",
					  "policyIds": [
					    "38e97ac082af9312c69c9e2b0949c0d7873f0bbca34b0a8905ec2441"
					  ]
					}
					""") })) @RequestBody GetLastMintRequest getLastMintRequest) {
		return cardanoDbSyncService.getLastMint(getLastMintRequest.stakeAddress, getLastMintRequest.policyIds);
	}

	@Operation(summary = "getTokenDetails")
	@GetMapping(value = { "/token/{policyId}/{assetName}", "/token/{policyId}/" })
	@Cacheable("getTokenDetails")
	public TokenDetails getTokenDetails(
			@Parameter(example = SAMPLE_POLICY_ID) @PathVariable String policyId,
			@Parameter(example = SAMPLE_ASSET_NAME) @PathVariable(required = false) String assetName) throws DecoderException {
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

	@Operation(summary = "Get json metadata of tx")
	@GetMapping(value = "/transaction/{txId}/metadata")
	@Cacheable("getTransactionMetadata")
	public String getTransactionMetadata(@Parameter(example = "a6ca444bd39cb51c7e997a9cead4a8071e2f7e5d1579ac4194b6aaaba923bc58") @PathVariable String txId) throws DataAccessException, DecoderException {
		return cardanoDbSyncService.getTransactionMetadata(txId);
	}

	@Operation(summary = "Get ada outputs if tx")
	@GetMapping(value = "/transaction/{txId}/outputs")
	@Cacheable("getTransactionOutputs")
	public List<TxOut> getTransactionOutputs(@Parameter(example = "a6ca444bd39cb51c7e997a9cead4a8071e2f7e5d1579ac4194b6aaaba923bc58") @PathVariable String txId) throws DataAccessException, DecoderException {
		return cardanoDbSyncService.getTransactionOutputs(txId);
	}

	@Operation(summary = "Checks is a txid has been included in the chain")
	@GetMapping(value = "/transaction/{txId}/confirmed")
	@Cacheable("isTransactionConfirmed")
	public Boolean isTransactionConfirmed(@Parameter(example = "a6ca444bd39cb51c7e997a9cead4a8071e2f7e5d1579ac4194b6aaaba923bc58") @PathVariable String txId) throws DataAccessException, DecoderException {
		return cardanoDbSyncService.isTransactionConfirmed(txId);
	}

	@Operation(summary = "Returns current tip of db")
	@GetMapping(value = "/tip")
	@Cacheable("getTip")
	public Long getTip() {
		return cardanoDbSyncService.getTip();
	}

	@Operation(summary = "Get minswap pools for token")
	@GetMapping(value = "/minswap/{policyId}/{assetName}")
	// @Cacheable("getMinswapPools")
	public List<LiquidityPool> getMinswapPools(
			@Parameter(example = SAMPLE_POLICY_ID) @PathVariable String policyId,
			@Parameter(example = SAMPLE_ASSET_NAME) @PathVariable String assetName) throws DecoderException {
		return minswapService.getMinswapPools(policyId, assetName);
	}

}
