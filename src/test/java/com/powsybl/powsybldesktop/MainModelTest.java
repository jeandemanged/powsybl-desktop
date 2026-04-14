/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.ieeecdf.converter.IeeeCdfNetworkFactory;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.NetworkFactory;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.loadflow.LoadFlowResultImpl;
import com.powsybl.powsybldesktop.navigation.NavigationEvent;
import com.powsybl.powsybldesktop.navigation.NavigationType;
import com.powsybl.powsybldesktop.navigation.NetworkNavigationState;
import com.powsybl.powsybldesktop.notification.Notification;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class MainModelTest {

    // MainModel starts a LogsModel Timeline, which needs the JavaFX toolkit up (headless, no display needed here).
    // Must NOT set testfx.headless: that's a global JVM system property that would make TestFX's
    // ApplicationLauncherImpl (used by every AbstractHeadlessApplicationTest subclass sharing this
    // forked JVM) try to load the openjfx-monocle classes this project no longer depends on.
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

    private final MainModel model = new MainModel();

    @Test
    void addNetworkSelectsOnlyFirstNetwork() {
        Network network1 = IeeeCdfNetworkFactory.create14();
        Network network2 = IeeeCdfNetworkFactory.create14();

        model.addNetwork(network1);
        assertEquals(network1, model.getNetwork());

        model.addNetwork(network2);
        assertEquals(network1, model.getNetwork());
        assertEquals(List.of(network1, network2), model.getNetworks());
    }

    @Test
    void removeSelectedNetworkClearsSelectionAndRelatedNavigationHistory() {
        Network network1 = IeeeCdfNetworkFactory.create14();
        Network network2 = IeeeCdfNetworkFactory.create14();
        model.addNetwork(network1);
        model.addNetwork(network2);
        model.addNavigationEvent(NavigationEvent.create(NavigationType.NETWORKS, NetworkNavigationState.create(network1)));
        model.addNavigationEvent(NavigationEvent.create(NavigationType.NETWORKS, NetworkNavigationState.create(network2)));

        model.removeNetwork(network1);

        assertNull(model.getNetwork());
        assertEquals(List.of(network2), model.getNetworks());
        assertEquals(1, model.getNavigationPast().size());
        assertEquals(network2, model.getNavigationPast().get(0).state().getSelectedNetwork());
    }

    @Test
    void removeNonSelectedNetworkKeepsSelection() {
        Network network1 = IeeeCdfNetworkFactory.create14();
        Network network2 = IeeeCdfNetworkFactory.create14();
        model.addNetwork(network1);
        model.addNetwork(network2);

        model.removeNetwork(network2);

        assertEquals(network1, model.getNetwork());
        assertEquals(List.of(network1), model.getNetworks());
    }

    @Test
    void removeAllNetworksClearsSelection() {
        model.addNetwork(IeeeCdfNetworkFactory.create14());
        model.addNetwork(IeeeCdfNetworkFactory.create14());

        model.removeAllNetworks();

        assertTrue(model.getNetworks().isEmpty());
        assertNull(model.getNetwork());
    }

    @Test
    void navigationPastIsCappedAndFutureClearedOnNewEvent() {
        // alternate event types so no two consecutive entries are equal (see duplicateConsecutiveEventIsNotAdded)
        for (int i = 0; i < 35; i++) {
            model.addNavigationEvent(i % 2 == 0 ? NavigationEvent.create(NavigationType.NETWORKS) : NavigationEvent.create(NavigationType.SUBSTATIONS));
        }
        assertEquals(30, model.getNavigationPast().size());

        model.navigateBackward();
        assertEquals(1, model.getNavigationFuture().size());

        model.addNavigationEvent(NavigationEvent.create(NavigationType.LOGS));
        assertTrue(model.getNavigationFuture().isEmpty());
    }

    @Test
    void navigateBackwardAndForwardMoveThroughHistory() {
        NavigationEvent first = NavigationEvent.create(NavigationType.NETWORKS);
        NavigationEvent second = NavigationEvent.create(NavigationType.LOGS);
        NavigationEvent third = NavigationEvent.create(NavigationType.REPORTS);
        model.addNavigationEvent(first);
        model.addNavigationEvent(second);
        model.addNavigationEvent(third);

        model.navigateBackward();
        assertEquals(second, model.navigationEventProperty().get());
        model.navigateBackward();
        assertEquals(first, model.navigationEventProperty().get());
        model.navigateBackward();
        assertEquals(first, model.navigationEventProperty().get(), "no more past entries to go back to");

        model.navigateForward();
        assertEquals(second, model.navigationEventProperty().get());
        model.navigateForward();
        assertEquals(third, model.navigationEventProperty().get());
        model.navigateForward();
        assertEquals(third, model.navigationEventProperty().get(), "no more future entries to go forward to");
    }

    @Test
    void duplicateConsecutiveEventIsNotAdded() {
        model.addNavigationEvent(NavigationEvent.create(NavigationType.LOGS));
        model.addNavigationEvent(NavigationEvent.create(NavigationType.LOGS));
        model.addNavigationEvent(NavigationEvent.create(NavigationType.LOGS));

        assertEquals(1, model.getNavigationPast().size());
    }

    @Test
    void navigateBackwardToIndexIgnoresOutOfBoundsIndex() {
        model.addNavigationEvent(NavigationEvent.create(NavigationType.NETWORKS));
        model.addNavigationEvent(NavigationEvent.create(NavigationType.LOGS));

        model.navigateBackwardToIndex(-1);
        model.navigateBackwardToIndex(5);

        assertEquals(2, model.getNavigationPast().size());
        assertTrue(model.getNavigationFuture().isEmpty());
    }

    @Test
    void notificationLifecycle() {
        Notification running = Notification.createRunning("loading", null);
        model.addNotification(running);
        assertEquals(List.of(running), model.getNotifications());

        Notification success = Notification.createSuccess(running.startTimestamp(), "done");
        model.replaceNotification(running, success);
        assertEquals(List.of(success), model.getNotifications());

        model.removeNotification(success);
        assertTrue(model.getNotifications().isEmpty());
    }

    @Test
    void replaceNotificationAddsWhenOldNotificationIsMissing() {
        Notification success = Notification.createSuccess(Instant.now(), "done");

        model.replaceNotification(Notification.createRunning("loading", null), success);

        assertEquals(List.of(success), model.getNotifications());
    }

    @Test
    void loadFlowResultIsKeyedByRootNetworkRegardlessOfWhichSubnetworkIsPassed() {
        Network network1 = NetworkFactory.findDefault().createNetwork("N1", "test");
        Network network2 = NetworkFactory.findDefault().createNetwork("N2", "test");
        Network merged = Network.merge("MERGED", network1, network2);
        Network subnetwork1 = merged.getSubnetwork("N1");
        LoadFlowResult result = new LoadFlowResultImpl(true, Map.of(), null);

        model.setLoadFlowResult(merged, result);

        assertSame(result, model.getLoadFlowResult(merged));
        assertSame(result, model.getLoadFlowResult(subnetwork1));
    }

    @Test
    void reportsAccumulateAndClear() {
        ReportNode report = ReportNode.newRootReportNode().withAllResourceBundlesFromClasspath().withMessageTemplate("powsybl.desktop.loadflow").build();

        model.addReport(report);
        assertEquals(List.of(report), model.getReports());

        model.clearReports();
        assertTrue(model.getReports().isEmpty());
    }
}
