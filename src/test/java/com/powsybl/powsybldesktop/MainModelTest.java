/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.contingency.list.ContingencyList;
import com.powsybl.contingency.list.DefaultContingencyList;
import com.powsybl.ieeecdf.converter.IeeeCdfNetworkFactory;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.NetworkFactory;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.loadflow.LoadFlowResultImpl;
import com.powsybl.openloadflow.sa.OpenSecurityAnalysisParameters;
import com.powsybl.powsybldesktop.navigation.NavigationEvent;
import com.powsybl.powsybldesktop.navigation.NavigationType;
import com.powsybl.powsybldesktop.navigation.NetworkNavigationState;
import com.powsybl.powsybldesktop.network.search.NetworkSearchIndex;
import com.powsybl.powsybldesktop.notification.Notification;
import com.powsybl.security.SecurityAnalysisParameters;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
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
        model.getNavigationHistory().navigate(NavigationEvent.create(NavigationType.NETWORKS, NetworkNavigationState.create(network1)));
        model.getNavigationHistory().navigate(NavigationEvent.create(NavigationType.NETWORKS, NetworkNavigationState.create(network2)));

        model.removeNetwork(network1);

        assertNull(model.getNetwork());
        assertEquals(List.of(network2), model.getNetworks());
        assertEquals(1, model.getNavigationHistory().getPast().size());
        assertEquals(network2, model.getNavigationHistory().getPast().get(0).state().getSelectedNetwork());
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
            model.getNavigationHistory().navigate(i % 2 == 0 ? NavigationEvent.create(NavigationType.NETWORKS) : NavigationEvent.create(NavigationType.SUBSTATIONS));
        }
        assertEquals(30, model.getNavigationHistory().getPast().size());

        model.getNavigationHistory().navigateBackward();
        assertEquals(1, model.getNavigationHistory().getFuture().size());

        model.getNavigationHistory().navigate(NavigationEvent.create(NavigationType.LOGS));
        assertTrue(model.getNavigationHistory().getFuture().isEmpty());
    }

    @Test
    void navigateBackwardAndForwardMoveThroughHistory() {
        NavigationEvent first = NavigationEvent.create(NavigationType.NETWORKS);
        NavigationEvent second = NavigationEvent.create(NavigationType.LOGS);
        NavigationEvent third = NavigationEvent.create(NavigationType.REPORTS);
        model.getNavigationHistory().navigate(first);
        model.getNavigationHistory().navigate(second);
        model.getNavigationHistory().navigate(third);

        model.getNavigationHistory().navigateBackward();
        assertEquals(second, model.getNavigationHistory().currentEventProperty().get());
        model.getNavigationHistory().navigateBackward();
        assertEquals(first, model.getNavigationHistory().currentEventProperty().get());
        model.getNavigationHistory().navigateBackward();
        assertEquals(first, model.getNavigationHistory().currentEventProperty().get(), "no more past entries to go back to");

        model.getNavigationHistory().navigateForward();
        assertEquals(second, model.getNavigationHistory().currentEventProperty().get());
        model.getNavigationHistory().navigateForward();
        assertEquals(third, model.getNavigationHistory().currentEventProperty().get());
        model.getNavigationHistory().navigateForward();
        assertEquals(third, model.getNavigationHistory().currentEventProperty().get(), "no more future entries to go forward to");
    }

    @Test
    void duplicateConsecutiveEventIsNotAdded() {
        model.getNavigationHistory().navigate(NavigationEvent.create(NavigationType.LOGS));
        model.getNavigationHistory().navigate(NavigationEvent.create(NavigationType.LOGS));
        model.getNavigationHistory().navigate(NavigationEvent.create(NavigationType.LOGS));

        assertEquals(1, model.getNavigationHistory().getPast().size());
    }

    @Test
    void navigateBackwardToIndexIgnoresOutOfBoundsIndex() {
        model.getNavigationHistory().navigate(NavigationEvent.create(NavigationType.NETWORKS));
        model.getNavigationHistory().navigate(NavigationEvent.create(NavigationType.LOGS));

        model.getNavigationHistory().navigateBackwardToIndex(-1);
        model.getNavigationHistory().navigateBackwardToIndex(5);

        assertEquals(2, model.getNavigationHistory().getPast().size());
        assertTrue(model.getNavigationHistory().getFuture().isEmpty());
    }

    @Test
    void notificationLifecycle() {
        Notification running = Notification.createRunning("loading", null);
        model.getNotificationsModel().add(running);
        assertEquals(List.of(running), model.getNotificationsModel().getNotifications());

        Notification success = Notification.createSuccess(running.startTimestamp(), "done");
        model.getNotificationsModel().replace(running, success);
        assertEquals(List.of(success), model.getNotificationsModel().getNotifications());

        model.getNotificationsModel().remove(success);
        assertTrue(model.getNotificationsModel().getNotifications().isEmpty());
    }

    @Test
    void networkStaysBusyUntilEveryJobReleasesIt() {
        Network merged = Network.merge("MERGED",
                NetworkFactory.findDefault().createNetwork("N1", "test"),
                NetworkFactory.findDefault().createNetwork("N2", "test"));
        Network subnetwork1 = merged.getSubnetwork("N1");
        model.addNetwork(merged);
        model.setNetwork(subnetwork1);

        Runnable release1 = model.markBusy(merged);
        Runnable release2 = model.markBusy(merged.getSubnetwork("N2"));
        assertTrue(model.isBusy(subnetwork1), "busy is tracked per root network");
        assertTrue(model.networkBusyProperty().get());

        release1.run();
        release1.run();
        assertTrue(model.networkBusyProperty().get(), "a release is idempotent, and the other job still runs");

        release2.run();
        assertFalse(model.networkBusyProperty().get());
    }

    @Test
    void clearNotificationsKeepsRunningOnes() {
        Notification running = Notification.createRunning("loading", () -> { });
        Notification done = Notification.createSuccess(Instant.now(), "done");
        model.getNotificationsModel().add(running);
        model.getNotificationsModel().add(done);

        model.getNotificationsModel().clear();

        assertEquals(List.of(running), model.getNotificationsModel().getNotifications(), "a running notification carries the only Cancel button");
    }

    @Test
    void replaceNotificationAddsWhenOldNotificationIsMissing() {
        Notification success = Notification.createSuccess(Instant.now(), "done");

        model.getNotificationsModel().replace(Notification.createRunning("loading", null), success);

        assertEquals(List.of(success), model.getNotificationsModel().getNotifications());
    }

    @Test
    void loadFlowResultIsKeyedByRootNetworkRegardlessOfWhichSubnetworkIsPassed() {
        Network network1 = NetworkFactory.findDefault().createNetwork("N1", "test");
        Network network2 = NetworkFactory.findDefault().createNetwork("N2", "test");
        Network merged = Network.merge("MERGED", network1, network2);
        Network subnetwork1 = merged.getSubnetwork("N1");
        LoadFlowResult result = new LoadFlowResultImpl(true, Map.of(), null);
        model.addNetwork(merged);

        model.getStudy(merged).setLoadFlowResult(result);

        assertSame(result, model.getStudy(merged).getLoadFlowResult());
        assertSame(result, model.getStudy(subnetwork1).getLoadFlowResult());
    }

    @Test
    void removeRootNetworkClearsSelectedSubnetworkAndItsNavigation() {
        Network merged = Network.merge("MERGED",
                NetworkFactory.findDefault().createNetwork("N1", "test"),
                NetworkFactory.findDefault().createNetwork("N2", "test"));
        Network subnetwork1 = merged.getSubnetwork("N1");
        model.addNetwork(merged);
        model.getNavigationHistory().navigate(NavigationEvent.create(NavigationType.NETWORKS, NetworkNavigationState.create(subnetwork1)));
        model.setNetwork(subnetwork1);

        model.removeNetwork(merged);

        assertNull(model.getNetwork());
        assertTrue(model.getNavigationHistory().getPast().isEmpty());
        assertNull(model.getNavigationHistory().currentEventProperty().get(), "a language reload re-fires the current event, which would re-select it");
    }

    @Test
    void setNetworkSetsSearchIndexStateBeforeNotifyingNetworkListeners() {
        Network network = IeeeCdfNetworkFactory.create14();
        // what MainController.ensureSearchIndex does when the selected network has no index yet
        model.networkProperty().addListener((observable, oldValue, newValue) ->
                model.searchIndexStateProperty().setValue(NetworkSearchIndex.State.BUILDING));

        model.addNetwork(network);

        assertEquals(NetworkSearchIndex.State.BUILDING, model.searchIndexStateProperty().get());
    }

    @Test
    void detachSubnetworkSelectsDetachedNetworkAndDropsReferencesToTheOldSubnetwork() {
        Network merged = Network.merge("MERGED",
                NetworkFactory.findDefault().createNetwork("N1", "test"),
                NetworkFactory.findDefault().createNetwork("N2", "test"));
        Network subnetwork1 = merged.getSubnetwork("N1");
        model.addNetwork(merged);
        model.getNavigationHistory().navigate(NavigationEvent.create(NavigationType.NETWORKS, NetworkNavigationState.create(subnetwork1)));
        model.setNetwork(subnetwork1);
        ContingencyList list = new DefaultContingencyList("list", List.of());
        model.getStudy(merged).getContingencyLists().add(list);

        model.detachSubnetworks(List.of(subnetwork1));

        Network detached = model.getNetwork();
        assertEquals("N1", detached.getId());
        assertNotSame(subnetwork1, detached);
        assertEquals(List.of(merged, detached), model.getNetworks());
        assertTrue(model.getNavigationHistory().getPast().isEmpty());
        assertEquals(List.of(list), model.getStudy(merged).getContingencyLists(), "lists stay with the root network they were edited for");
        assertTrue(model.getStudy(detached).getContingencyLists().isEmpty());
    }

    @Test
    void contingencyListsAreKeyedByRootNetwork() {
        Network merged = Network.merge("MERGED",
                NetworkFactory.findDefault().createNetwork("N1", "test"),
                NetworkFactory.findDefault().createNetwork("N2", "test"));
        model.addNetwork(merged);

        assertSame(model.getStudy(merged).getContingencyLists(), model.getStudy(merged.getSubnetwork("N1")).getContingencyLists());
        assertSame(model.getStudy(merged).getContingencyLists(), model.getStudy(merged.getSubnetwork("N2")).getContingencyLists());
    }

    @Test
    void securityAnalysisParametersStartsWiredToTheSameLoadFlowParametersInstance() {
        assertNotNull(model.getParametersModel().securityAnalysisParametersProperty().get().getExtension(OpenSecurityAnalysisParameters.class));
        assertSame(model.getParametersModel().loadFlowParametersProperty().get(), model.getParametersModel().securityAnalysisParametersProperty().get().getLoadFlowParameters());
    }

    @Test
    void replacingLoadFlowParametersRewiresSecurityAnalysisParameters() {
        LoadFlowParameters replacement = new LoadFlowParameters();

        model.getParametersModel().loadFlowParametersProperty().setValue(replacement);

        assertSame(replacement, model.getParametersModel().securityAnalysisParametersProperty().get().getLoadFlowParameters());
    }

    @Test
    void replacingSecurityAnalysisParametersDiscardsItsOwnEmbeddedLoadFlowParameters() {
        SecurityAnalysisParameters imported = new SecurityAnalysisParameters();
        imported.setLoadFlowParameters(new LoadFlowParameters().setDc(true)); // stale, from e.g. an imported JSON file

        model.getParametersModel().securityAnalysisParametersProperty().setValue(imported);

        assertSame(model.getParametersModel().loadFlowParametersProperty().get(), imported.getLoadFlowParameters());
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
