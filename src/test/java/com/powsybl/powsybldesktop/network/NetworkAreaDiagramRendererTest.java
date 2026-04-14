/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network;

import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Substation;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.iidm.network.test.FourSubstationsNodeBreakerFactory;
import com.powsybl.nad.NadParameters;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plain JUnit — no JavaFX toolkit needed, since {@link NetworkAreaDiagramRenderer} is pure Java.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class NetworkAreaDiagramRendererTest {

    @Test
    void rendersAreaDiagramAroundVoltageLevel() throws IOException {
        Network network = FourSubstationsNodeBreakerFactory.create();
        VoltageLevel s2vl1 = network.getVoltageLevel("S2VL1");

        String svg = NetworkAreaDiagramRenderer.render(s2vl1, 1, new NadParameters());

        assertFalse(svg.isBlank());
        assertTrue(svg.contains("<svg"));
    }

    @Test
    void rendersAreaDiagramAroundSubstationWithoutThrowing() {
        Network network = FourSubstationsNodeBreakerFactory.create();
        Substation s2 = network.getSubstation("S2");

        assertDoesNotThrow(() -> NetworkAreaDiagramRenderer.render(s2, 2, new NadParameters()));
    }

    @Test
    void widerDepthIncludesMoreVoltageLevels() throws IOException {
        Network network = FourSubstationsNodeBreakerFactory.create();
        VoltageLevel s2vl1 = network.getVoltageLevel("S2VL1");

        String depth1 = NetworkAreaDiagramRenderer.render(s2vl1, 1, new NadParameters());
        String depth3 = NetworkAreaDiagramRenderer.render(s2vl1, 3, new NadParameters());

        assertTrue(depth3.length() > depth1.length());
    }
}
