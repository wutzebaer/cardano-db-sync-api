package de.peterspace.cardanodbsyncapi.rest;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class RestHandlerTest {

  private static final String SAMPLE_STAKE_ADDRESS =
      "stake1u9k8maputfwef7kjl6nvguupxcvllplpa967amss06fxwngzsj30w";
  private static final String SAMPLE_ADDRESS =
      "addr1qx8lsj4menq5s7w5f8jupm64n9d3aamvcppllujwse473636fhhttcg3x8kfhm6qqpvujfhgmu8jww3mfn49m3fkjssqhx0348";
  private static final String SAMPLE_POLICY_ID =
      "89267e9a35153a419e1b8ffa23e511ac39ea4e3b00452e9d500f2982";
  private static final String SAMPLE_POOL_ID =
      "pool180fejev4xgwe2y53ky0pxvgxr3wcvkweu6feq5mdljfzcsmtg6u";
  private static final String SAMPLE_ASSET_NAME = "436176616c6965724b696e67436861726c6573";
  private static final String SAMPLE_FINGERPRINT = "asset1r9v95ujk83kx90lr3g8cd0uqu5de3kqjptp7sm";
  private static final String SAMPLE_CIP68_POLICY =
      "f0ff48bbb7bbe9d59a40f1ce90e9e9d0ff5002ec48f232b49ca0fb9a";
  private static final String SAMPLE_CIP68_ASSET_NAME = "000de1406e757473";
  private static final String SAMPLE_CIP68_FT_FINGERPRINT =
      "asset14u343978h4nc9euau4a9xl29lx8ssepqy4qfx9";
  private static final String SAMPLE_STAKE_HASH =
      "e1ddbe7a587e6bdd2674bf53fc093226bbd43af035f4ea07d781167966";
  private static final String SAMPLE_TX_ID =
      "a6ca444bd39cb51c7e997a9cead4a8071e2f7e5d1579ac4194b6aaaba923bc58";
  private static final String SAMPLE_POOL_HASH =
      "pool180fejev4xgwe2y53ky0pxvgxr3wcvkweu6feq5mdljfzcsmtg6u";
  private static final String HEX_64 = "^[0-9a-f]{64}$";
  private static final String HEX_56 = "^[0-9a-f]{56}$";

  @Autowired private MockMvc mockMvc;

  private static Matcher<Object> numberAtLeast(long min) {
    return new TypeSafeMatcher<Object>() {
      @Override
      protected boolean matchesSafely(Object item) {
        return item instanceof Number && ((Number) item).longValue() >= min;
      }

      @Override
      public void describeTo(Description description) {
        description.appendText("a number >= " + min);
      }
    };
  }

  @Test
  void getStakeInfo() throws Exception {
    mockMvc
        .perform(get("/{stakeAddress}/stakeInfo", SAMPLE_STAKE_ADDRESS))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.stake", numberAtLeast(0)))
        .andExpect(jsonPath("$.poolHash", equalTo(SAMPLE_POOL_HASH)))
        .andExpect(jsonPath("$.tickerName", equalTo("CHIEN")))
        .andExpect(jsonPath("$.totalStake", numberAtLeast(1)))
        .andExpect(jsonPath("$.poolRewards", numberAtLeast(0)));
  }

  @Test
  void getUtxosStakeAddress() throws Exception {
    mockMvc
        .perform(get("/{address}/utxos", SAMPLE_STAKE_ADDRESS))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$[*].txHash", everyItem(matchesPattern(HEX_64))))
        .andExpect(jsonPath("$[*].txIndex", everyItem(numberAtLeast(0))))
        .andExpect(jsonPath("$[*].value", everyItem(numberAtLeast(0))))
        .andExpect(jsonPath("$[*].owningAddress", everyItem(startsWith("addr"))));
  }

  @Test
  void getUtxosPaymentAddress() throws Exception {
    mockMvc
        .perform(get("/{address}/utxos", SAMPLE_ADDRESS))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$[*].txHash", everyItem(matchesPattern(HEX_64))))
        .andExpect(jsonPath("$[*].owningAddress", everyItem(equalTo(SAMPLE_ADDRESS))))
        .andExpect(jsonPath("$[*].value", everyItem(numberAtLeast(0))));
  }

  @Test
  void getReturnAddress() throws Exception {
    mockMvc
        .perform(get("/{stakeAddress}/returnAddress", SAMPLE_STAKE_ADDRESS))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.address", startsWith("addr1")));
  }

  @Test
  void getReturnAddresses() throws Exception {
    mockMvc
        .perform(
            post("/returnAddresses")
                .contentType(MediaType.APPLICATION_JSON)
                .content("[\"" + SAMPLE_STAKE_ADDRESS + "\", \"stake1unknown\"]"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].stakeAddress", equalTo(SAMPLE_STAKE_ADDRESS)))
        .andExpect(jsonPath("$[0].address", startsWith("addr1")));
  }

  @Test
  void getHandlesOfStakeAddresses() throws Exception {
    mockMvc
        .perform(
            post("/handles")
                .contentType(MediaType.APPLICATION_JSON)
                .content("[\"" + SAMPLE_STAKE_ADDRESS + "\", \"stake1unknown\"]"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(lessThanOrEqualTo(1))))
        .andExpect(jsonPath("$[*].stakeAddress", everyItem(equalTo(SAMPLE_STAKE_ADDRESS))))
        .andExpect(jsonPath("$[*].handle", everyItem(not(emptyOrNullString()))));
  }

  @Test
  void getStakeAddress() throws Exception {
    mockMvc
        .perform(get("/{address}/stakeAddress", SAMPLE_ADDRESS))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.address", startsWith("stake1")));
  }

  @Test
  void getStakeAddressByHash() throws Exception {
    mockMvc
        .perform(get("/stakeAddress/{stakeAddressHash}", SAMPLE_STAKE_HASH))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.address", equalTo(SAMPLE_STAKE_ADDRESS)));
  }

  @Test
  void getStakeHashByAddress() throws Exception {
    mockMvc
        .perform(get("/stakeHash/{stakeAddress}", SAMPLE_STAKE_ADDRESS))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.address", equalTo(SAMPLE_STAKE_HASH)));
  }

  @Test
  void getTokenFeed() throws Exception {
    mockMvc
        .perform(get("/tokenFeed"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$", hasSize(greaterThan(0))))
        .andExpect(jsonPath("$", hasSize(lessThanOrEqualTo(100))))
        .andExpect(jsonPath("$[0].maMintId", numberAtLeast(1)))
        .andExpect(jsonPath("$[0].slotNo", numberAtLeast(1)))
        .andExpect(jsonPath("$[*].maPolicyId", everyItem(matchesPattern(HEX_56))))
        .andExpect(jsonPath("$[*].maFingerprint", everyItem(startsWith("asset"))));
  }

  @Test
  void getTokenSearch() throws Exception {
    mockMvc
        .perform(get("/tokenSearch").param("filter", SAMPLE_POLICY_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$", hasSize(greaterThan(0))))
        .andExpect(jsonPath("$[*].maPolicyId", everyItem(equalTo(SAMPLE_POLICY_ID))))
        .andExpect(jsonPath("$[*].maFingerprint", everyItem(startsWith("asset"))))
        .andExpect(jsonPath("$[0].cip", equalTo(25)));
  }

  @Test
  void getTokenSearchAfterSkipsCursor() throws Exception {
    mockMvc
        .perform(
            get("/tokenSearch")
                .param("filter", SAMPLE_CIP68_POLICY + "." + SAMPLE_CIP68_ASSET_NAME)
                .param("after", SAMPLE_CIP68_POLICY + "." + SAMPLE_CIP68_ASSET_NAME))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$", hasSize(0)));
  }

  @Test
  void getTokenSearchByHandle() throws Exception {
    mockMvc
        .perform(get("/tokenSearch").param("filter", "$petergrossmann"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$", hasSize(greaterThan(0))))
        .andExpect(jsonPath("$[*].maPolicyId", everyItem(equalTo(SAMPLE_CIP68_POLICY))))
        .andExpect(jsonPath("$[0].name", containsString("petergrossmann")));
  }

  @Test
  void getTokenSearchByCip68Handle() throws Exception {
    mockMvc
        .perform(get("/tokenSearch").param("filter", "$nuts"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$", hasSize(greaterThan(0))))
        .andExpect(jsonPath("$[0].maPolicyId", equalTo(SAMPLE_CIP68_POLICY)))
        .andExpect(jsonPath("$[0].name", equalTo("$nuts")))
        .andExpect(jsonPath("$[0].cip", equalTo(68)));
  }

  @Test
  void getAddressTokenList() throws Exception {
    mockMvc
        .perform(get("/{address}/token", SAMPLE_STAKE_ADDRESS))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$[*].maPolicyId", everyItem(matchesPattern(HEX_56))))
        .andExpect(jsonPath("$[*].quantity", everyItem(numberAtLeast(1))))
        .andExpect(jsonPath("$[*].maFingerprint", everyItem(startsWith("asset"))));
  }

  @Test
  void getStatement() throws Exception {
    mockMvc
        .perform(get("/{address}/statement", SAMPLE_STAKE_ADDRESS))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$", hasSize(greaterThan(0))))
        .andExpect(jsonPath("$[0].timestamp").exists())
        .andExpect(jsonPath("$[0].epoch", numberAtLeast(1)))
        .andExpect(jsonPath("$[0].operations").isArray())
        .andExpect(jsonPath("$[0].operations", hasSize(greaterThan(0))))
        .andExpect(jsonPath("$[*].in", everyItem(numberAtLeast(0))))
        .andExpect(jsonPath("$[*].out", everyItem(numberAtLeast(0))));
  }

  @Test
  void getHandles() throws Exception {
    mockMvc
        .perform(get("/{stakeAddress}/handles", SAMPLE_STAKE_ADDRESS))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$[*].address", hasItem("chien-pool")))
        .andExpect(jsonPath("$[0].default", equalTo(true)))
        .andExpect(jsonPath("$[?(@.default == true)]", hasSize(1)));
  }

  @Test
  void getAddressByHandle() throws Exception {
    mockMvc
        .perform(get("/handles/{handle}", "petergrossmann"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.address", startsWith("stake1")));
  }

  @Test
  void getMints() throws Exception {
    mockMvc
        .perform(get("/{stakeAddress}/mints", SAMPLE_STAKE_ADDRESS))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$", hasSize(greaterThan(0))))
        .andExpect(jsonPath("$[*].maPolicyId", everyItem(matchesPattern(HEX_56))))
        .andExpect(jsonPath("$[*].txHash", everyItem(matchesPattern(HEX_64))))
        .andExpect(jsonPath("$[*].totalSupply", everyItem(numberAtLeast(0))));
  }

  @Test
  void getTokenDetails() throws Exception {
    mockMvc
        .perform(get("/token/{policyId}/{assetName}", SAMPLE_POLICY_ID, SAMPLE_ASSET_NAME))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.maPolicyId", equalTo(SAMPLE_POLICY_ID)))
        .andExpect(jsonPath("$.maName", equalTo(SAMPLE_ASSET_NAME)))
        .andExpect(jsonPath("$.fingerprint", startsWith("asset")))
        .andExpect(jsonPath("$.slotNo", numberAtLeast(1)))
        .andExpect(jsonPath("$.txHash", matchesPattern(HEX_64)))
        .andExpect(jsonPath("$.totalSupply", numberAtLeast(0)))
        .andExpect(jsonPath("$.maPolicyScript", not(emptyOrNullString())))
        .andExpect(jsonPath("$.cip", equalTo(25)));
  }

  @Test
  void getTokenDetailsByFingerprint() throws Exception {
    mockMvc
        .perform(get("/token/{fingerprint}/", SAMPLE_FINGERPRINT))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fingerprint", equalTo(SAMPLE_FINGERPRINT)))
        .andExpect(jsonPath("$.maPolicyId", matchesPattern(HEX_56)))
        .andExpect(jsonPath("$.maName", not(emptyOrNullString())))
        .andExpect(jsonPath("$.totalSupply", numberAtLeast(0)));
  }

  @Test
  void getTokenDetailsCip68() throws Exception {
    mockMvc
        .perform(get("/token/{policyId}/{assetName}", SAMPLE_CIP68_POLICY, SAMPLE_CIP68_ASSET_NAME))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.maPolicyId", equalTo(SAMPLE_CIP68_POLICY)))
        .andExpect(jsonPath("$.maName", equalTo(SAMPLE_CIP68_ASSET_NAME)))
        .andExpect(jsonPath("$.metadata", containsString("\"name\"")))
        .andExpect(jsonPath("$.metadata", containsString("$nuts")))
        .andExpect(jsonPath("$.metadata", containsString("ipfs://")))
        .andExpect(jsonPath("$.cip", equalTo(68)));
  }

  @Test
  void getTokenListCip68() throws Exception {
    mockMvc
        .perform(
            get("/tokenSearch")
                .param("filter", SAMPLE_CIP68_POLICY + "." + SAMPLE_CIP68_ASSET_NAME))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].maPolicyId", equalTo(SAMPLE_CIP68_POLICY)))
        .andExpect(jsonPath("$[0].maName", equalTo(SAMPLE_CIP68_ASSET_NAME)))
        .andExpect(jsonPath("$[0].name", equalTo("$nuts")))
        .andExpect(jsonPath("$[0].image", startsWith("ipfs://")))
        .andExpect(jsonPath("$[0].cip", equalTo(68)));
  }

  @Test
  void getTokenListCip68FungibleUsesLogoAsImage() throws Exception {
    mockMvc
        .perform(get("/tokenSearch").param("filter", SAMPLE_CIP68_FT_FINGERPRINT))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].name", equalTo("PBG Token")))
        .andExpect(jsonPath("$[0].image", equalTo("https://token.pbg.io/logo.png")))
        .andExpect(jsonPath("$[0].cip", equalTo(68)));
  }

  @Test
  void getPoolList() throws Exception {
    mockMvc
        .perform(get("/poolList"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$", hasSize(greaterThan(0))))
        .andExpect(jsonPath("$[*].poolHash", everyItem(startsWith("pool1"))))
        .andExpect(jsonPath("$[*].poolHash", hasItem(SAMPLE_POOL_HASH)))
        .andExpect(jsonPath("$[*].tickerName", everyItem(not(emptyOrNullString()))));
  }

  @Test
  void getEpochStake() throws Exception {
    mockMvc
        .perform(get("/epochStake/{poolHash}/{epoch}", SAMPLE_POOL_HASH, 432))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$", hasSize(greaterThan(0))))
        .andExpect(jsonPath("$[*].stakeAddress", everyItem(startsWith("stake1"))))
        .andExpect(jsonPath("$[*].amount", everyItem(numberAtLeast(1))));
  }

  @Test
  void getOwners() throws Exception {
    mockMvc
        .perform(get("/policy/{policyId}/owners", SAMPLE_POLICY_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray());
  }

  @Test
  void getStakeCheck() throws Exception {
    mockMvc
        .perform(get("/{stakeAddress}/stakeCheck", SAMPLE_STAKE_ADDRESS))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.stakeAddress", equalTo(SAMPLE_STAKE_ADDRESS)))
        .andExpect(jsonPath("$.currentEpoch", numberAtLeast(600)))
        .andExpect(jsonPath("$.balance", numberAtLeast(0)))
        .andExpect(jsonPath("$.rewardsAvailable", numberAtLeast(0)));
  }

  @Test
  void getStakeCheckOfAnUnknownAddressIsEmpty() throws Exception {
    mockMvc
        .perform(
            get(
                "/{stakeAddress}/stakeCheck",
                "stake1uxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"))
        .andExpect(status().isOk())
        .andExpect(content().string(""));
  }

  @Test
  void getPoolCheck() throws Exception {
    mockMvc
        .perform(get("/pool/{poolHash}/check", SAMPLE_POOL_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.poolHash", equalTo(SAMPLE_POOL_ID)))
        .andExpect(jsonPath("$.fixedCost", numberAtLeast(170000000)))
        .andExpect(jsonPath("$.activeStake", numberAtLeast(1)))
        .andExpect(jsonPath("$.delegators", numberAtLeast(1)))
        .andExpect(jsonPath("$.saturationStake", numberAtLeast(1)));
  }

  @Test
  void getPoolPerformance() throws Exception {
    mockMvc
        .perform(get("/pool/{poolHash}/performance", SAMPLE_POOL_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.blocks", hasSize(12)))
        .andExpect(jsonPath("$.lifetimeBlocks", numberAtLeast(1)))
        .andExpect(jsonPath("$.networkRoa").isNumber());
  }

  @Test
  void getPoolAlternatives() throws Exception {
    mockMvc
        .perform(get("/poolAlternatives"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(greaterThan(0))))
        .andExpect(jsonPath("$[*].poolHash", everyItem(startsWith("pool1"))))
        .andExpect(jsonPath("$[*].retiringEpoch", everyItem(equalTo(null))));
  }

  @Test
  void getStakeInfoOfADeregisteredAddressIsEmpty() throws Exception {
    // delegated to a pool and deregistered afterwards
    mockMvc
        .perform(get("/{stakeAddress}/stakeInfo", "stake1uytvd9eyqvkyn3hhv8wnrjpkjlmpt246v4g65pw9q7sutdgy26xk7"))
        .andExpect(status().isOk())
        .andExpect(content().string(""));
  }

  @Test
  void getDreps() throws Exception {
    mockMvc
        .perform(get("/dreps"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(greaterThan(10))))
        .andExpect(jsonPath("$[*].drepId", everyItem(startsWith("drep1"))))
        .andExpect(jsonPath("$[*].votingPower", everyItem(numberAtLeast(0))));
  }

  @Test
  void getGovernance() throws Exception {
    mockMvc
        .perform(get("/governance").param("poolHash", SAMPLE_POOL_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.currentEpoch", numberAtLeast(600)))
        .andExpect(jsonPath("$.actions", hasSize(greaterThan(0))))
        .andExpect(jsonPath("$.actions[*].txHash", everyItem(matchesPattern("[0-9a-f]{64}"))))
        .andExpect(jsonPath("$.actions[*].expiresEpoch", everyItem(numberAtLeast(500))));
  }

  @Test
  void getPolicyRoyalties() throws Exception {
    // its royalty token was the first mint and writes the address in two chunks
    mockMvc
        .perform(
            get(
                "/policy/{policyId}/royalties",
                "eead402e47940fa95539e4bd087f5355efd4ba0d67a6fb988caf05a8"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rate", equalTo("0.05")))
        .andExpect(
            jsonPath(
                "$.address",
                equalTo(
                    "addr1q9638h6kw8kezqcywkhxd3exnw7ecgdejmh87alu6scql7ng26wcj59j79qack5kppp9can6wfpvldap0e6zjj4eyv7qy3v77x")))
        .andExpect(jsonPath("$.firstMint", equalTo(true)));
  }

  @Test
  void getPolicyRoyaltiesOfAPolicyWithoutIsEmpty() throws Exception {
    mockMvc
        .perform(
            get(
                "/policy/{policyId}/royalties",
                "c225195b211214eda7b4f19a7c92f69f53f8a308f00738ee10af25df"))
        .andExpect(status().isOk())
        .andExpect(content().string(""));
  }

  @Test
  void getTransactionMetadata() throws Exception {
    mockMvc
        .perform(get("/transaction/{txId}/metadata", SAMPLE_TX_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$", hasSize(greaterThan(0))));
  }

  @Test
  void getTransactionOutputs() throws Exception {
    mockMvc
        .perform(get("/transaction/{txId}/outputs", SAMPLE_TX_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$", hasSize(greaterThan(0))))
        .andExpect(jsonPath("$[*].targetAddress", everyItem(startsWith("addr"))))
        .andExpect(jsonPath("$[*].value", everyItem(numberAtLeast(0))));
  }

  @Test
  void isTransactionConfirmed() throws Exception {
    mockMvc
        .perform(get("/transaction/{txId}/confirmed", SAMPLE_TX_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", equalTo(true)));
  }

  @Test
  void getTip() throws Exception {
    mockMvc
        .perform(get("/tip"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", numberAtLeast(100_000_000L)));
  }

  @Test
  void getMinswapPools() throws Exception {
    mockMvc
        .perform(get("/minswap/{policyId}/{assetName}", SAMPLE_POLICY_ID, SAMPLE_ASSET_NAME))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$[*].policyA", everyItem(equalTo(SAMPLE_POLICY_ID))))
        .andExpect(jsonPath("$[*].nameA", everyItem(equalTo(SAMPLE_ASSET_NAME))))
        .andExpect(jsonPath("$[*].quantityA", everyItem(numberAtLeast(1))));
  }
}
