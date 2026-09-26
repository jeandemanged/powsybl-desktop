/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.navigation;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.ieeecdf.converter.IeeeCdfNetworkFactory;
import com.powsybl.iidm.network.Generator;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Substation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class NavigationEventTest {

    private final Network network = IeeeCdfNetworkFactory.create14();

    @Test
    void describeWithoutStateIsJustTheScreenName() {
        NavigationEvent event = NavigationEvent.create(NavigationType.LOGS);

        assertEquals("Logs", event.describe());
    }

    @Test
    void describeWithNetworkAppendsNetworkName() {
        NavigationEvent event = NavigationEvent.create(NavigationType.NETWORKS, NetworkNavigationState.create(network));

        assertEquals("Networks - " + network.getNameOrId(), event.describe());
    }

    @Test
    void describeWithGeneratorAppendsGeneratorName() {
        Generator generator = network.getGeneratorStream().findFirst().orElseThrow();
        NavigationEvent event = NavigationEvent.create(NavigationType.NETWORK_TABLE_GENERATORS, GeneratorNavigationState.create(generator));

        assertEquals("Generators - " + generator.getNameOrId(), event.describe());
    }

    @Test
    void describeWithNoGeneratorFallsBackToNetworkName() {
        NavigationEvent event = NavigationEvent.create(NavigationType.NETWORK_TABLE_GENERATORS, GeneratorNavigationState.createNoGenerator(network));

        assertEquals("Generators - " + network.getNameOrId(), event.describe());
    }

    @Test
    void describeWithSubstationAppendsSubstationName() {
        Substation substation = network.getSubstationStream().findFirst().orElseThrow();
        NavigationEvent event = NavigationEvent.create(NavigationType.NETWORK_TABLE_SUBSTATIONS, SubstationNavigationState.create(substation));

        assertEquals("Substations - " + substation.getNameOrId(), event.describe());
    }

    @Test
    void describeWithNoSubstationFallsBackToNetworkName() {
        NavigationEvent event = NavigationEvent.create(NavigationType.NETWORK_TABLE_SUBSTATIONS, SubstationNavigationState.createNoSubstation(network));

        assertEquals("Substations - " + network.getNameOrId(), event.describe());
    }

    @Test
    void describeWithContainerAppendsContainerName() {
        Substation substation = network.getSubstationStream().findFirst().orElseThrow();
        NavigationEvent event = NavigationEvent.create(NavigationType.SUBSTATIONS, ContainerNavigationState.create(substation));

        assertEquals("Substations - " + substation.getNameOrId(), event.describe());
    }

    @Test
    void describeWithReportAppendsReportMessage() {
        ReportNode reportNode = ReportNode.newRootReportNode().withAllResourceBundlesFromClasspath().withMessageTemplate("powsybl.desktop.loadflow").build();
        NavigationEvent event = NavigationEvent.create(NavigationType.REPORTS, ReportNavigationState.create(reportNode));

        assertEquals("Reports - " + reportNode.getMessage(), event.describe());
    }
}
