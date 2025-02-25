/*
 * Copyright (c) 2016, 2017, 2018, 2019 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.fabricmc.fabric.impl.biome;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.base.Preconditions;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import net.minecraft.registry.RegistryEntryLookup;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.biome.source.MultiNoiseBiomeSource;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;

import net.fabricmc.fabric.impl.biome.modification.BuiltInRegistryKeys;

/**
 * Internal data for modding Vanilla's {@link MultiNoiseBiomeSource.Preset#NETHER}.
 */
public final class NetherBiomeData {
	// Cached sets of the biomes that would generate from Vanilla's default biome source without consideration
	// for data packs (as those would be distinct biome sources).
	private static final Set<RegistryKey<Biome>> NETHER_BIOMES = new HashSet<>();

	private static final Map<RegistryKey<Biome>, MultiNoiseUtil.NoiseHypercube> NETHER_BIOME_NOISE_POINTS = new HashMap<>();

	private static final Logger LOGGER = LogUtils.getLogger();

	private NetherBiomeData() {
	}

	public static void addNetherBiome(RegistryKey<Biome> biome, MultiNoiseUtil.NoiseHypercube spawnNoisePoint) {
		Preconditions.checkArgument(biome != null, "Biome is null");
		Preconditions.checkArgument(spawnNoisePoint != null, "MultiNoiseUtil.NoiseValuePoint is null");
		NETHER_BIOME_NOISE_POINTS.put(biome, spawnNoisePoint);
		clearBiomeSourceCache();
	}

	public static Map<RegistryKey<Biome>, MultiNoiseUtil.NoiseHypercube> getNetherBiomeNoisePoints() {
		return NETHER_BIOME_NOISE_POINTS;
	}

	public static boolean canGenerateInNether(RegistryKey<Biome> biome) {
		if (NETHER_BIOMES.isEmpty()) {
			MultiNoiseBiomeSource source = MultiNoiseBiomeSource.Preset.NETHER.getBiomeSource(BuiltInRegistryKeys.biomeRegistryWrapper());

			for (RegistryEntry<Biome> entry : source.getBiomes()) {
				entry.getKey().ifPresent(NETHER_BIOMES::add);
			}
		}

		return NETHER_BIOMES.contains(biome) || NETHER_BIOME_NOISE_POINTS.containsKey(biome);
	}

	private static void clearBiomeSourceCache() {
		NETHER_BIOMES.clear(); // Clear cached biome source data
	}

	private static MultiNoiseUtil.Entries<RegistryEntry<Biome>> withModdedBiomeEntries(MultiNoiseUtil.Entries<RegistryEntry<Biome>> entries, RegistryEntryLookup<Biome> biomes) {
		if (NETHER_BIOME_NOISE_POINTS.isEmpty()) {
			return entries;
		}

		ArrayList<Pair<MultiNoiseUtil.NoiseHypercube, RegistryEntry<Biome>>> entryList = new ArrayList<>(entries.getEntries());

		for (Map.Entry<RegistryKey<Biome>, MultiNoiseUtil.NoiseHypercube> entry : NETHER_BIOME_NOISE_POINTS.entrySet()) {
			RegistryEntry.Reference<Biome> biomeEntry = biomes.getOptional(entry.getKey()).orElse(null);

			if (biomeEntry != null) {
				entryList.add(Pair.of(entry.getValue(), biomeEntry));
			} else {
				LOGGER.warn("Nether biome {} not loaded", entry.getKey().getValue());
			}
		}

		return new MultiNoiseUtil.Entries<>(entryList);
	}

	public static void modifyBiomeSource(RegistryEntryLookup<Biome> biomeRegistry, BiomeSource biomeSource) {
		if (biomeSource instanceof MultiNoiseBiomeSource multiNoiseBiomeSource) {
			if (((BiomeSourceAccess) multiNoiseBiomeSource).fabric_shouldModifyBiomeEntries() && multiNoiseBiomeSource.matchesInstance(MultiNoiseBiomeSource.Preset.NETHER)) {
				multiNoiseBiomeSource.biomeEntries = NetherBiomeData.withModdedBiomeEntries(
						MultiNoiseBiomeSource.Preset.NETHER.biomeSourceFunction.apply(biomeRegistry),
						biomeRegistry);
				multiNoiseBiomeSource.biomes = multiNoiseBiomeSource.biomeEntries.getEntries().stream().map(Pair::getSecond).collect(Collectors.toSet());
				((BiomeSourceAccess) multiNoiseBiomeSource).fabric_setModifyBiomeEntries(false);
			}
		}
	}
}
