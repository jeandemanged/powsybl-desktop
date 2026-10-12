/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop;

import com.powsybl.ieeecdf.converter.IeeeCdfNetworkFactory;
import com.powsybl.iidm.network.Network;
import com.powsybl.powsybldesktop.navigation.ContainerNavigationState;
import com.powsybl.powsybldesktop.navigation.NavigationEvent;
import com.powsybl.powsybldesktop.navigation.NavigationType;
import com.powsybl.powsybldesktop.navigation.NetworkNavigationState;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class SceneModelTest {

    // see MainModelTest
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

    private final Network network = IeeeCdfNetworkFactory.create14();
    private final Network otherNetwork = IeeeCdfNetworkFactory.create9();

    private MainModel mainModel() {
        MainModel mainModel = new MainModel();
        mainModel.addNetwork(network);
        mainModel.addNetwork(otherNetwork);
        mainModel.setNetwork(network);
        return mainModel;
    }

    @Test
    void mainSceneFollowsTheSelectionAndSeparateSceneKeepsItsNetwork() {
        MainModel mainModel = mainModel();
        SceneModel main = SceneModel.main(mainModel);
        SceneModel separate = SceneModel.separate(mainModel, () -> { });

        mainModel.setNetwork(otherNetwork);

        assertSame(otherNetwork, main.getNetwork());
        assertSame(network, separate.getNetwork());
        assertFalse(main.isSeparate());
        assertTrue(separate.isSeparate());
    }

    @Test
    void onlyTheMainSceneRecordsItsState() {
        MainModel mainModel = mainModel();
        NavigationEvent event = NavigationEvent.create(NavigationType.SUBSTATIONS, ContainerNavigationState.create(network.getVoltageLevel("VL1")));

        SceneModel.separate(mainModel, () -> { }).record(event);
        assertTrue(mainModel.getNavigationHistory().getPast().isEmpty());

        SceneModel.main(mainModel).record(event);
        assertEquals(List.of(event), mainModel.getNavigationHistory().getPast());
        // recorded, not dispatched to the main window
        assertEquals(null, mainModel.getNavigationHistory().currentEventProperty().get());
    }

    @Test
    void separateSceneNavigatesTheMainWindowAndBringsItToFront() {
        MainModel mainModel = mainModel();
        boolean[] broughtToFront = {false};
        NavigationEvent event = NavigationEvent.create(NavigationType.SUBSTATIONS, ContainerNavigationState.create(network.getVoltageLevel("VL1")));

        SceneModel.separate(mainModel, () -> broughtToFront[0] = true).navigate(event);

        assertEquals(event, mainModel.getNavigationHistory().currentEventProperty().get());
        assertEquals(List.of(event), mainModel.getNavigationHistory().getPast());
        assertTrue(broughtToFront[0]);
    }

    @Test
    void separateSceneDiagramStateStartsFromTheMainOneAndIsItsOwn() {
        MainModel mainModel = mainModel();
        mainModel.setDiagramZoom(1.5);
        SceneModel separate = SceneModel.separate(mainModel, () -> { });

        separate.diagramZoomProperty().set(0.5);

        assertEquals(1.5, mainModel.getDiagramZoom());
        assertEquals(0.5, separate.diagramZoomProperty().get());
    }

    @Test
    void networkChangeGuardIsOnlyAskedForAnotherNetwork() {
        MainModel mainModel = mainModel();
        int[] asked = {0};
        mainModel.setNetworkChangeGuard(target -> {
            asked[0]++;
            return false;
        });

        assertTrue(mainModel.confirmNetworkChange(network));
        assertFalse(mainModel.confirmNetworkChange(otherNetwork));
        assertEquals(1, asked[0]);
    }

    @Test
    void vetoedNavigationIsNeitherDispatchedNorRecorded() {
        MainModel mainModel = mainModel();
        var history = mainModel.getNavigationHistory();
        NavigationEvent first = NavigationEvent.create(NavigationType.NETWORKS, NetworkNavigationState.create(network));
        NavigationEvent second = NavigationEvent.create(NavigationType.NETWORKS, NetworkNavigationState.create(otherNetwork));
        history.navigate(first);
        history.navigate(second);
        history.setGuard(event -> event != first);

        history.navigateBackward();
        assertEquals(List.of(first, second), history.getPast());
        assertEquals(second, history.currentEventProperty().get());

        history.navigate(first);
        assertEquals(List.of(first, second), history.getPast());
    }
}
