/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.edit;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.modification.topology.RemoveVoltageLevelBuilder;
import com.powsybl.iidm.network.Load;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.iidm.network.test.FourSubstationsNodeBreakerFactory;
import com.powsybl.powsybldesktop.MainModel;
import com.powsybl.powsybldesktop.NetworkStructureChange;
import com.powsybl.powsybldesktop.navigation.ContainerNavigationState;
import com.powsybl.powsybldesktop.navigation.LoadNavigationState;
import com.powsybl.powsybldesktop.navigation.NavigationEvent;
import com.powsybl.powsybldesktop.navigation.NavigationType;
import com.powsybl.powsybldesktop.network.search.NetworkSearch;
import com.powsybl.powsybldesktop.network.search.NetworkSearchIndex;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class NetworkEditsTest {

    private final Network network = FourSubstationsNodeBreakerFactory.create();
    private final MainModel mainModel = new MainModel();
    private final List<NetworkStructureChange> changes = new ArrayList<>();

    // MainModel starts a LogsModel Timeline, which needs the JavaFX toolkit up, see MainModelTest
    @BeforeAll
    static void initJavaFxToolkit() {
        System.setProperty("prism.order", "sw");
        System.setProperty("java.awt.headless", "true");
        System.setProperty("glass.platform", "Headless");
        try {
            Platform.startup(() -> { });
        } catch (IllegalStateException alreadyStarted) {
            // toolkit already started by another test class running in this fork
        }
    }

    @BeforeEach
    void setUp() {
        mainModel.addNetwork(network);
        mainModel.structureChangeProperty().addListener((observable, oldValue, change) -> {
            if (change != null) {
                changes.add(change);
            }
        });
    }

    @Test
    void publishesEverythingAModificationRemoves() {
        VoltageLevel voltageLevel = network.getVoltageLevel("S1VL1");
        Load load = network.getLoad("LD1");

        assertTrue(NetworkEdits.apply(mainModel, network, n -> NetworkEdits.apply(n, new RemoveVoltageLevelBuilder().withVoltageLevelId("S1VL1").build())));

        assertEquals(1, changes.size());
        NetworkStructureChange change = changes.getFirst();
        assertTrue(change.created().isEmpty());
        assertTrue(change.removed().contains(voltageLevel));
        assertTrue(change.removed().contains(load));
        assertTrue(change.removed().stream().anyMatch(identifiable -> "S1VL1_BBS".equals(identifiable.getId())));
    }

    @Test
    void publishesCreatedObjectsButNotThoseCreatedThenRemoved() {
        NetworkEdits.apply(mainModel, network, n -> {
            n.newSubstation().setId("NEW_S").add();
            n.newSubstation().setId("TEMP_S").add().remove();
        });

        NetworkStructureChange change = changes.getFirst();
        assertEquals(1, change.created().size());
        assertTrue(change.created().contains(network.getSubstation("NEW_S")));
        assertTrue(change.removed().isEmpty());
    }

    @Test
    void publishesWhatWasChangedBeforeAFailure() {
        assertThrows(PowsyblException.class, () -> NetworkEdits.apply(mainModel, network, n -> {
            n.newSubstation().setId("NEW_S").add();
            n.newSubstation().setId("NEW_S").add();
        }));

        assertTrue(changes.getFirst().created().contains(network.getSubstation("NEW_S")));
    }

    @Test
    void refusedWhileBusy() {
        Runnable done = mainModel.markBusy(network);

        assertFalse(NetworkEdits.apply(mainModel, network, n -> n.newSubstation().setId("NEW_S").add()));

        done.run();
        assertTrue(changes.isEmpty());
        assertEquals(null, network.getSubstation("NEW_S"));
    }

    @Test
    void forgetsHistoryEntriesOfRemovedObjects() {
        Load load = network.getLoad("LD1");
        NavigationEvent loadEvent = NavigationEvent.create(NavigationType.NETWORK_TABLE_LOADS, LoadNavigationState.create(load));
        NavigationEvent containerEvent = NavigationEvent.create(NavigationType.SUBSTATIONS,
                ContainerNavigationState.create(network.getVoltageLevel("S1VL1")));
        NavigationEvent otherEvent = NavigationEvent.create(NavigationType.SUBSTATIONS,
                ContainerNavigationState.create(network.getVoltageLevel("S2VL1")));
        mainModel.getNavigationHistory().navigate(loadEvent);
        mainModel.getNavigationHistory().navigate(containerEvent);
        mainModel.getNavigationHistory().navigate(otherEvent);

        NetworkEdits.apply(mainModel, network, n -> NetworkEdits.apply(n, new RemoveVoltageLevelBuilder().withVoltageLevelId("S1VL1").build()));

        assertEquals(List.of(otherEvent), mainModel.getNavigationHistory().getPast());
    }

    @Test
    void searchIndexFollowsStructureChanges() {
        try (NetworkSearchIndex index = NetworkSearchIndex.build(network)) {
            NetworkEdits.apply(mainModel, network, n -> {
                n.newSubstation().setId("NEW_S").add();
                NetworkEdits.apply(n, new RemoveVoltageLevelBuilder().withVoltageLevelId("S1VL1").build());
            });
            NetworkStructureChange change = changes.getFirst();
            index.applyStructureChange(network, change.created(), change.removed());

            // an empty substation is only a match when searching every kind
            assertEquals(List.of(network.getSubstation("NEW_S")), index.search("NEW_S", NetworkSearch.ALL_KINDS).matches());
            // the fuzzy fallback still finds similar ids
            assertTrue(index.search("LD1", EnumSet.of(NetworkSearch.Kind.LOAD)).matches().stream().noneMatch(match -> "LD1".equals(match.getId())));
            assertTrue(index.search("S1VL1", NetworkSearch.ALL_KINDS).matches().stream().noneMatch(match -> "S1VL1".equals(match.getId())));
        }
    }
}
