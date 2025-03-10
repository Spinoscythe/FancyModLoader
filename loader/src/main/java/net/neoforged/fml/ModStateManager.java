/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml;

import com.google.common.graph.GraphBuilder;
import cpw.mods.modlauncher.util.ServiceLoaderUtils;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.ServiceLoader;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.toposort.TopologicalSort;

@SuppressWarnings("UnstableApiUsage")
public class ModStateManager {
    static ModStateManager INSTANCE;
    private final EnumMap<ModLoadingPhase, List<IModLoadingState>> stateMap;

    public ModStateManager() {
        INSTANCE = this;
        final var sp = ServiceLoader.load(FMLLoader.getGameLayer(), IModStateProvider.class);
        this.stateMap = ServiceLoaderUtils.streamWithErrorHandling(sp, sce -> {})
                .map(IModStateProvider::getAllStates)
                .<IModLoadingState>mapMulti(Iterable::forEach)
                .collect(Collectors.groupingBy(IModLoadingState::phase, () -> new EnumMap<>(ModLoadingPhase.class), Collectors.toUnmodifiableList()));
    }

    public List<IModLoadingState> getStates(final ModLoadingPhase phase) {
        var nodes = stateMap.get(phase);
        var lookup = nodes.stream().collect(Collectors.toMap(IModLoadingState::name, Function.identity()));

        var graph = GraphBuilder.directed().allowsSelfLoops(false).<IModLoadingState>build();
        var dummy = ModLoadingState.empty("", "", phase);
        nodes.forEach(graph::addNode);
        graph.addNode(dummy);
        nodes.forEach(n -> graph.putEdge(lookup.getOrDefault(n.previous(), dummy), n));
        return TopologicalSort.topologicalSort(graph, Comparator.comparingInt(nodes::indexOf)).stream().filter(st -> st != dummy).toList();
    }

    public IModLoadingState findState(final String stateName) {
        return stateMap.values()
                .stream()
                .flatMap(Collection::stream)
                .filter(mls -> mls.name().equals(stateName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown IModLoadingState: " + stateName));
    }
}
