package de.peterspace.cardanodbsyncapi.rest;

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
  private static final String SAMPLE_ASSET_NAME = "436176616c6965724b696e67436861726c6573";
  private static final String SAMPLE_FINGERPRINT = "asset1r9v95ujk83kx90lr3g8cd0uqu5de3kqjptp7sm";
  private static final String SAMPLE_STAKE_HASH =
      "e1ddbe7a587e6bdd2674bf53fc093226bbd43af035f4ea07d781167966";
  private static final String SAMPLE_TX_ID =
      "a6ca444bd39cb51c7e997a9cead4a8071e2f7e5d1579ac4194b6aaaba923bc58";
  private static final String SAMPLE_POOL_HASH =
      "pool180fejev4xgwe2y53ky0pxvgxr3wcvkweu6feq5mdljfzcsmtg6u";
  private static final String SAMPLE_LAST_MINT_POLICY =
      "38e97ac082af9312c69c9e2b0949c0d7873f0bbca34b0a8905ec2441";
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
        .andExpect(jsonPath("$.totalStake", numberAtLeast(1)));
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
  void getTokenList() throws Exception {
    mockMvc
        .perform(get("/token"))
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
  void getTokenListFiltered() throws Exception {
    mockMvc
        .perform(get("/token").param("filter", SAMPLE_POLICY_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$", hasSize(greaterThan(0))))
        .andExpect(jsonPath("$[*].maPolicyId", everyItem(equalTo(SAMPLE_POLICY_ID))))
        .andExpect(jsonPath("$[*].maFingerprint", everyItem(startsWith("asset"))));
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
        .andExpect(jsonPath("$[*].address", everyItem(not(emptyOrNullString()))));
  }

  @Test
  void getAddressByHandle() throws Exception {
    mockMvc
        .perform(get("/handles/{handle}", "petergrossmann"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.address", startsWith("stake1")));
  }

  @Test
  void getLastMint() throws Exception {
    mockMvc
        .perform(
            post("/lastMint")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
					{
					  "stakeAddress": "%s",
					  "policyIds": ["%s"]
					}
					"""
                        .formatted(SAMPLE_STAKE_ADDRESS, SAMPLE_LAST_MINT_POLICY)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$", hasSize(greaterThan(0))))
        .andExpect(jsonPath("$[*].maPolicyId", everyItem(equalTo(SAMPLE_LAST_MINT_POLICY))))
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
        .andExpect(jsonPath("$.maPolicyScript", not(emptyOrNullString())));
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
