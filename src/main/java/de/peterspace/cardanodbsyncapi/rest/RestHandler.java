package de.peterspace.cardanodbsyncapi.rest;

import de.peterspace.cardanodbsyncapi.dto.AccountStatementRow;
import de.peterspace.cardanodbsyncapi.dto.DRepInfo;
import de.peterspace.cardanodbsyncapi.dto.EpochStake;
import de.peterspace.cardanodbsyncapi.dto.GovernanceOverview;
import de.peterspace.cardanodbsyncapi.dto.LiquidityPool;
import de.peterspace.cardanodbsyncapi.dto.OwnerInfo;
import de.peterspace.cardanodbsyncapi.dto.PoolCheck;
import de.peterspace.cardanodbsyncapi.dto.PoolInfo;
import de.peterspace.cardanodbsyncapi.dto.PoolEpoch;
import de.peterspace.cardanodbsyncapi.dto.PoolPerformance;
import de.peterspace.cardanodbsyncapi.dto.ReturnAddress;
import de.peterspace.cardanodbsyncapi.dto.ScriptOutput;
import de.peterspace.cardanodbsyncapi.dto.StakeAddress;
import de.peterspace.cardanodbsyncapi.dto.StakeCheck;
import de.peterspace.cardanodbsyncapi.dto.StakeHandle;
import de.peterspace.cardanodbsyncapi.dto.StakeInfo;
import de.peterspace.cardanodbsyncapi.dto.StakeReturnAddress;
import de.peterspace.cardanodbsyncapi.dto.PolicyCollection;
import de.peterspace.cardanodbsyncapi.dto.PolicyRoyalties;
import de.peterspace.cardanodbsyncapi.dto.TokenDetails;
import de.peterspace.cardanodbsyncapi.dto.TokenListItem;
import de.peterspace.cardanodbsyncapi.dto.TxOut;
import de.peterspace.cardanodbsyncapi.dto.Utxo;
import de.peterspace.cardanodbsyncapi.service.CardanoDbSyncService;
import de.peterspace.cardanodbsyncapi.service.GovernanceService;
import de.peterspace.cardanodbsyncapi.service.ScriptService;
import de.peterspace.cardanodbsyncapi.service.StakeCheckService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.DecoderException;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/")
public class RestHandler {

  private static final String SAMPLE_STAKE_ADDRESS =
      "stake1u9k8maputfwef7kjl6nvguupxcvllplpa967amss06fxwngzsj30w";
  private static final String SAMPLE_ADDRESS =
      "addr1qx8lsj4menq5s7w5f8jupm64n9d3aamvcppllujwse473636fhhttcg3x8kfhm6qqpvujfhgmu8jww3mfn49m3fkjssqhx0348";
  private static final String SAMPLE_POLICY_ID =
      "89267e9a35153a419e1b8ffa23e511ac39ea4e3b00452e9d500f2982";
  private static final String SAMPLE_ASSET_NAME = "436176616c6965724b696e67436861726c6573";
  private static final String SAMPLE_FINGERPRINT = "asset1r9v95ujk83kx90lr3g8cd0uqu5de3kqjptp7sm";
  private static final int MAX_RETURN_ADDRESSES = 10000;
  private static final String SAMPLE_POOL_ID =
      "pool180fejev4xgwe2y53ky0pxvgxr3wcvkweu6feq5mdljfzcsmtg6u";
  private final CardanoDbSyncService cardanoDbSyncService;
  private final StakeCheckService stakeCheckService;
  private final GovernanceService governanceService;
  private final ScriptService scriptService;

  @Operation(
      summary =
          "Unspent outputs at a script that hold a token of the script's own policy whose name starts with the prefix, with their inline datum. One row for the ADA of an output and one per token")
  @GetMapping(value = "/script/{scriptHash}/outputs")
  @Cacheable("getScriptOutputs")
  public List<ScriptOutput> getScriptOutputs(
      @Parameter(example = SAMPLE_POLICY_ID) @PathVariable String scriptHash,
      @Parameter(description = "Start of the token name as hex", example = "677270") @RequestParam
          String namePrefix)
      throws DecoderException {
    return scriptService.getOutputs(scriptHash, namePrefix);
  }

  @Operation(
      summary =
          "Outputs of the first transaction that paid to a script, spent or not, with their inline datum")
  @GetMapping(value = "/script/{scriptHash}/firstOutputs")
  @Cacheable("getScriptFirstOutputs")
  public List<ScriptOutput> getScriptFirstOutputs(
      @Parameter(example = SAMPLE_POLICY_ID) @PathVariable String scriptHash)
      throws DecoderException {
    return scriptService.getFirstOutputs(scriptHash);
  }

  @Operation(summary = "The Plutus V3 cost model of the current epoch")
  @GetMapping(value = "/costModel/plutusV3")
  @Cacheable("getPlutusV3CostModel")
  public List<Long> getPlutusV3CostModel() throws Exception {
    return scriptService.getPlutusV3CostModel();
  }

  @Operation(summary = "Active DReps with their name and the ADA delegated to them, the largest first")
  @GetMapping(value = "/dreps")
  @Cacheable("getDreps")
  public List<DRepInfo> getDreps() {
    return governanceService.getDreps();
  }

  @Operation(
      summary =
          "Open and recently closed governance actions with the votes cast, and how one DRep and one pool voted on each")
  @GetMapping(value = "/governance")
  @Cacheable("getGovernance")
  public GovernanceOverview getGovernance(
      @Parameter(example = "drep13d6sxkyz6st9h65qqrzd8ukpywhr8swe9f6357qntgjqye0gttd")
          @RequestParam(required = false)
          String drepId,
      @Parameter(example = SAMPLE_POOL_ID) @RequestParam(required = false) String poolHash) {
    return governanceService.getOverview(drepId, poolHash);
  }

  @Operation(
      summary =
          "Staking state of a stake address: registration, pool, DRep and withdrawable rewards. Empty for an unknown address")
  @GetMapping(value = "/{stakeAddress}/stakeCheck")
  @Cacheable("getStakeCheck")
  public StakeCheck getStakeCheck(
      @Parameter(example = SAMPLE_STAKE_ADDRESS) @PathVariable String stakeAddress) {
    return stakeCheckService.getStakeCheck(stakeAddress);
  }

  @Operation(summary = "Fees, retirement, stake and saturation of a pool. Empty for an unknown pool")
  @GetMapping(value = "/pool/{poolHash}/check")
  @Cacheable("getPoolCheck")
  public PoolCheck getPoolCheck(
      @Parameter(example = SAMPLE_POOL_ID) @PathVariable String poolHash) {
    return stakeCheckService.getPoolCheck(poolHash);
  }

  @Operation(
      summary =
          "Blocks and delegator returns of a pool over the last epochs, next to the network average")
  @GetMapping(value = "/pool/{poolHash}/performance")
  @Cacheable("getPoolPerformance")
  public PoolPerformance getPoolPerformance(
      @Parameter(example = SAMPLE_POOL_ID) @PathVariable String poolHash) {
    return stakeCheckService.getPoolPerformance(poolHash);
  }

  @Operation(
      summary =
          "Blocks, stake and delegators of a pool in one epoch and the blocks its stake lets expect. Empty for an unknown pool")
  @GetMapping(value = "/pool/{poolHash}/epoch/{epoch}")
  @Cacheable("getPoolEpoch")
  public PoolEpoch getPoolEpoch(
      @Parameter(example = SAMPLE_POOL_ID) @PathVariable String poolHash,
      @Parameter(example = "668") @PathVariable int epoch) {
    return stakeCheckService.getPoolEpoch(poolHash, epoch);
  }

  @Operation(
      summary =
          "Pools that make blocks, are not retiring, take at most 1 % margin and the minimum fixed cost and hold 300 thousand to 30 million ADA")
  @GetMapping(value = "/poolAlternatives")
  @Cacheable("getPoolAlternatives")
  public List<PoolCheck> getPoolAlternatives() {
    return stakeCheckService.getPoolAlternatives();
  }

  @Operation(summary = "Get infos where address is staked to")
  @GetMapping(value = "/{stakeAddress}/stakeInfo")
  @Cacheable("getStakeInfo")
  public StakeInfo getStakeInfo(
      @Parameter(example = SAMPLE_STAKE_ADDRESS) @PathVariable String stakeAddress) {
    return cardanoDbSyncService.getStakeInfo(stakeAddress);
  }

  @Operation(summary = "Find utxos of given address or stakeAddress including multi assets")
  @GetMapping(value = "/{address}/utxos")
  @Cacheable(value = "getUtxos", sync = true)
  public List<Utxo> getUtxos(
      @Parameter(example = SAMPLE_STAKE_ADDRESS) @PathVariable String address)
      throws DecoderException {
    return cardanoDbSyncService.getUtxos(address);
  }

  @Operation(
      summary =
          "Find the first known address with the same stake address, which should not be mangled")
  @GetMapping(value = "/{stakeAddress}/returnAddress")
  @Cacheable("getReturnAddress")
  public ReturnAddress getReturnAddress(
      @Parameter(example = SAMPLE_STAKE_ADDRESS) @PathVariable String stakeAddress) {
    return cardanoDbSyncService.getReturnAddress(stakeAddress);
  }

  @Operation(
      summary =
          "Find the first known address for many stake addresses at once, unknown stake addresses are omitted")
  @PostMapping(value = "/returnAddresses")
  public List<StakeReturnAddress> getReturnAddresses(@RequestBody List<String> stakeAddresses) {
    if (stakeAddresses.size() > MAX_RETURN_ADDRESSES) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "At most " + MAX_RETURN_ADDRESSES + " stake addresses per request");
    }
    return cardanoDbSyncService.getReturnAddresses(stakeAddresses);
  }

  @Operation(summary = "Find stakeAddress of address")
  @GetMapping(value = "/{address}/stakeAddress")
  @Cacheable("getStakeAddress")
  public StakeAddress getStakeAddress(
      @Parameter(example = SAMPLE_ADDRESS) @PathVariable String address) {
    return cardanoDbSyncService.getStakeAddress(address);
  }

  @Operation(summary = "Find stakeAddress by stakeAddressHash")
  @GetMapping(value = "/stakeAddress/{stakeAddressHash}")
  @Cacheable("getStakeAddressByHash")
  public StakeAddress getStakeAddressByHash(
      @Parameter(example = "e1ddbe7a587e6bdd2674bf53fc093226bbd43af035f4ea07d781167966")
          @PathVariable
          String stakeAddressHash)
      throws DecoderException {
    return cardanoDbSyncService.getStakeAddressByHash(stakeAddressHash);
  }

  @Operation(summary = "Find stakeAddressHash by stakeAddress")
  @GetMapping(value = "/stakeHash/{stakeAddress}")
  @Cacheable("getStakeHashByAddress")
  public StakeAddress getStakeHashByAddress(
      @Parameter(example = SAMPLE_STAKE_ADDRESS) @PathVariable String stakeAddress)
      throws DecoderException {
    return cardanoDbSyncService.getStakeHashByAddress(stakeAddress);
  }

  @Operation(summary = "getTokenFeed")
  @GetMapping(value = "/tokenFeed")
  @Cacheable("getTokenFeed")
  public List<TokenListItem> getTokenFeed(
      @RequestParam(required = false) Long afterMintid,
      @RequestParam(required = false) Long beforeMintid) {
    return cardanoDbSyncService.getTokenFeed(afterMintid, beforeMintid);
  }

  @Operation(summary = "getTokenSearch")
  @GetMapping(value = "/tokenSearch")
  @Cacheable("getTokenSearch")
  public List<TokenListItem> getTokenSearch(
      @Parameter(example = "$petergrossmann") @RequestParam String filter,
      @Parameter(example = SAMPLE_POLICY_ID + "." + SAMPLE_ASSET_NAME)
          @RequestParam(required = false)
          String after)
      throws DecoderException {
    return cardanoDbSyncService.getTokenSearch(filter, after);
  }

  @Operation(summary = "getAddressTokenList")
  @GetMapping(value = "/{address}/token")
  @Cacheable("getAddressTokenList")
  public List<TokenListItem> getAddressTokenList(
      @Parameter(example = SAMPLE_STAKE_ADDRESS) @PathVariable String address)
      throws DecoderException {
    return cardanoDbSyncService.getAddressTokenList(address);
  }

  @Operation(summary = "Get all transactions for an address or stakeAddress")
  @GetMapping(value = "/{address}/statement")
  @Cacheable("getStatement")
  public List<AccountStatementRow> getStatement(
      @Parameter(example = SAMPLE_STAKE_ADDRESS) @PathVariable String address)
      throws DecoderException {
    return cardanoDbSyncService.getStatement(address);
  }

  @Operation(summary = "Get all handles from a stakeAddress")
  @GetMapping(value = "/{stakeAddress}/handles")
  @Cacheable("getHandles")
  public List<StakeAddress> getHandles(
      @Parameter(example = SAMPLE_STAKE_ADDRESS) @PathVariable String stakeAddress)
      throws DecoderException {
    return cardanoDbSyncService.getHandles(stakeAddress);
  }

  @Operation(
      summary =
          "Find one handle for many stake addresses at once, stake addresses without a handle are omitted")
  @PostMapping(value = "/handles")
  public List<StakeHandle> getHandlesOfStakeAddresses(@RequestBody List<String> stakeAddresses) {
    if (stakeAddresses.size() > MAX_RETURN_ADDRESSES) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "At most " + MAX_RETURN_ADDRESSES + " stake addresses per request");
    }
    return cardanoDbSyncService.getHandlesOfStakeAddresses(stakeAddresses);
  }

  @Operation(summary = "Get address for handle")
  @GetMapping(value = "/handles/{handle}")
  @Cacheable("getAddressByHandle")
  public StakeAddress getAddressByHandle(
      @Parameter(example = "petergrossmann") @PathVariable String handle) throws DecoderException {
    return cardanoDbSyncService.getAddressByHandle(handle);
  }

  @Operation(summary = "Get all minted tokens for a stakeAddress")
  @GetMapping(value = "/{stakeAddress}/mints")
  @Cacheable("getMints")
  public List<TokenDetails> getMints(
      @Parameter(example = SAMPLE_STAKE_ADDRESS) @PathVariable String stakeAddress) {
    return cardanoDbSyncService.getMints(stakeAddress);
  }

  @Operation(summary = "getTokenDetails")
  @GetMapping(value = {"/token/{policyId}/{assetName}"})
  @Cacheable("getTokenDetails")
  public TokenDetails getTokenDetails(
      @Parameter(example = SAMPLE_POLICY_ID) @PathVariable String policyId,
      @Parameter(example = SAMPLE_ASSET_NAME) @PathVariable(required = false) String assetName)
      throws DecoderException {
    return cardanoDbSyncService.getTokenDetails(policyId, assetName);
  }

  @Operation(summary = "getTokenDetailsByFingerprint")
  @GetMapping(value = {"/token/{fingerprint}/"})
  @Cacheable("getTokenDetails")
  public TokenDetails getTokenDetails(
      @Parameter(example = SAMPLE_FINGERPRINT) @PathVariable String fingerprint)
      throws DecoderException {
    return cardanoDbSyncService.getTokenDetails(fingerprint);
  }

  @Operation(summary = "Get the CIP-27 royalties of a policy, empty if it defines none")
  @GetMapping(value = "/policy/{policyId}/royalties")
  @Cacheable("getPolicyRoyalties")
  public PolicyRoyalties getPolicyRoyalties(
      @Parameter(example = SAMPLE_POLICY_ID) @PathVariable String policyId)
      throws DecoderException {
    return cardanoDbSyncService.getPolicyRoyalties(policyId);
  }

  @Operation(
      summary = "Get the collection a policy registered on chain (CIP-88), empty if it did not")
  @GetMapping(value = "/policy/{policyId}/collection")
  @Cacheable("getPolicyCollection")
  public PolicyCollection getPolicyCollection(
      @Parameter(example = SAMPLE_POLICY_ID) @PathVariable String policyId)
      throws DecoderException {
    return cardanoDbSyncService.getPolicyCollection(policyId);
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
      @Parameter(example = "pool180fejev4xgwe2y53ky0pxvgxr3wcvkweu6feq5mdljfzcsmtg6u") @PathVariable
          String poolHash,
      @Parameter(example = "432") @PathVariable int epoch) {
    return cardanoDbSyncService.getEpochStake(poolHash, epoch);
  }

  @Operation(summary = "Get all token owners of a policyId, values get updated twice a day")
  @GetMapping(value = "/policy/{policyId}/owners")
  @Cacheable("getOwners")
  public List<OwnerInfo> getOwners(
      @Parameter(example = SAMPLE_POLICY_ID) @PathVariable String policyId)
      throws DecoderException {
    return cardanoDbSyncService.getOwners(policyId);
  }

  @Operation(summary = "Get json metadata of tx")
  @GetMapping(value = "/transaction/{txId}/metadata")
  @Cacheable("getTransactionMetadata")
  public String getTransactionMetadata(
      @Parameter(example = "a6ca444bd39cb51c7e997a9cead4a8071e2f7e5d1579ac4194b6aaaba923bc58")
          @PathVariable
          String txId)
      throws DataAccessException, DecoderException {
    return cardanoDbSyncService.getTransactionMetadata(txId);
  }

  @Operation(summary = "Get ada outputs if tx")
  @GetMapping(value = "/transaction/{txId}/outputs")
  @Cacheable("getTransactionOutputs")
  public List<TxOut> getTransactionOutputs(
      @Parameter(example = "a6ca444bd39cb51c7e997a9cead4a8071e2f7e5d1579ac4194b6aaaba923bc58")
          @PathVariable
          String txId)
      throws DataAccessException, DecoderException {
    return cardanoDbSyncService.getTransactionOutputs(txId);
  }

  @Operation(summary = "Checks is a txid has been included in the chain")
  @GetMapping(value = "/transaction/{txId}/confirmed")
  @Cacheable("isTransactionConfirmed")
  public Boolean isTransactionConfirmed(
      @Parameter(example = "a6ca444bd39cb51c7e997a9cead4a8071e2f7e5d1579ac4194b6aaaba923bc58")
          @PathVariable
          String txId)
      throws DataAccessException, DecoderException {
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
  @Cacheable("getMinswapPools")
  public List<LiquidityPool> getMinswapPools(
      @Parameter(example = SAMPLE_POLICY_ID) @PathVariable String policyId,
      @Parameter(example = SAMPLE_ASSET_NAME) @PathVariable String assetName)
      throws DecoderException {
    return cardanoDbSyncService.getMinswapPools(policyId, assetName);
  }
}
