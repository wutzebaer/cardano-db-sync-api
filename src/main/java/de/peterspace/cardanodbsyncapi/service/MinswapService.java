package de.peterspace.cardanodbsyncapi.service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.codec.DecoderException;
import org.springframework.stereotype.Component;

import com.google.common.base.Objects;

import de.peterspace.cardanodbsyncapi.dto.LiquidityPool;
import de.peterspace.cardanodbsyncapi.dto.Utxo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@RequiredArgsConstructor
public class MinswapService {

	private static String FACTORY_POLICY_ID = "13aa2accf2e1561723aa26871e071fdf32c867cff7e7d50ad470d62f";
	private static String FACTORY_ASSET_NAME = "4d494e53574150";
	private static String POOL_NFT_POLICY_ID = "0be55d262b29f564998ff81efe21bdc0022621c12f15af08d0f2ddb1";

	private final CardanoDbSyncService cardanoDbSyncService;

	public List<LiquidityPool> getMinswapPools(String policyId, String assetName) throws DecoderException {
		List<Utxo> minswapUtxos = cardanoDbSyncService.getMinswapUtxos(policyId, assetName);
		return minswapUtxos.stream()
				.collect(Collectors.groupingBy(utxo -> utxo.getTxHash() + "-" + utxo.getTxIndex()))
				.values().stream()
				// must have FACTORY_POLICY_ID tokens
				.filter(list -> list.stream().anyMatch(item -> Objects.equal(item.getMaPolicyId(), FACTORY_POLICY_ID) && Objects.equal(item.getMaName(), FACTORY_ASSET_NAME)))
				// must have POOL_NFT_POLICY_ID tokens
				.filter(list -> list.stream().anyMatch(item -> Objects.equal(item.getMaPolicyId(), POOL_NFT_POLICY_ID)))
				// must have tokens of requested policyId+assetName
				.filter(list -> list.stream().anyMatch(item -> Objects.equal(item.getMaPolicyId(), policyId) && Objects.equal(item.getMaName(), assetName)))
				// remove FACTORY_POLICY_ID tokens from lists
				.map(list -> list.stream().filter(utxo -> !Objects.equal(utxo.getMaPolicyId(), FACTORY_POLICY_ID)).toList())
				// remove POOL_NFT_POLICY_ID tokens from lists, and also all with the same name
				.map(list -> {
					Utxo nft = list.stream().filter(utxo -> Objects.equal(utxo.getMaPolicyId(), POOL_NFT_POLICY_ID)).findFirst().get();
					return list.stream().filter(utxo -> !Objects.equal(utxo.getMaName(), nft.getMaName())).toList();
				})
				// remove ada mion utxo if not an token -> ada pool
				.map(list -> {
					if (list.size() == 3) {
						list = list.stream().filter(utxo -> java.util.Objects.nonNull(utxo.getMaPolicyId())).toList();
					}
					if (list.size() != 2) {
						throw new RuntimeException("List has not 2 items: " + list);
					}
					return list;
				})
				// convert to pool
				.map(list -> {
					Utxo assetA = list.stream().filter(item -> Objects.equal(item.getMaPolicyId(), policyId) && Objects.equal(item.getMaName(), assetName)).findFirst().get();
					Utxo assetB = list.stream().filter(item -> !(Objects.equal(item.getMaPolicyId(), policyId) && Objects.equal(item.getMaName(), assetName))).findFirst().get();
					return new LiquidityPool(assetA, assetB);
				}).toList();

	}

}
