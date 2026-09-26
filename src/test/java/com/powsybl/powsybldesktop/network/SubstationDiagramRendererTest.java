/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network;

import com.powsybl.ieeecdf.converter.IeeeCdfNetworkFactory;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.iidm.network.test.FourSubstationsNodeBreakerFactory;
import com.powsybl.powsybldesktop.parameters.DesktopSldParameters;
import com.powsybl.sld.SldParameters;
import com.powsybl.sld.layout.VerticalSubstationLayoutFactory;
import com.powsybl.sld.library.FlatDesignLibrary;
import com.powsybl.sld.svg.GraphMetadata;
import com.powsybl.sld.svg.styles.NominalVoltageStyleProviderFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plain JUnit — no JavaFX toolkit needed, since {@link SubstationDiagramRenderer} is pure Java.
 * See the plan for why {@link SubstationsController} itself (its WebView) is not tested headlessly.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class SubstationDiagramRendererTest {

    @Test
    void rendersNodeBreakerVoltageLevelWithSwitchAndGeneratorMetadata() throws IOException {
        Network network = FourSubstationsNodeBreakerFactory.create();
        VoltageLevel s2vl1 = network.getVoltageLevel("S2VL1");

        SubstationDiagramRenderer.DiagramRender render = SubstationDiagramRenderer.render(s2vl1, new SldParameters());

        assertFalse(render.svg().isBlank());
        assertTrue(render.svg().contains("<svg"));

        GraphMetadata metadata = render.metadata();
        GraphMetadata.NodeMetadata breaker = metadata.getNodeMetadata().stream()
                .filter(n -> "S2VL1_GTH1_BREAKER".equals(n.getEquipmentId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No node metadata for switch S2VL1_GTH1_BREAKER"));
        assertEquals("BREAKER", breaker.getComponentType());
        assertEquals("idS2VL1_95_GTH1_95_BREAKER", breaker.getId());

        GraphMetadata.NodeMetadata generator = metadata.getNodeMetadata().stream()
                .filter(n -> "GTH1".equals(n.getEquipmentId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No node metadata for generator GTH1"));
        assertEquals("GENERATOR", generator.getComponentType());
    }

    @Test
    void rendersBusBreakerVoltageLevelWithoutThrowing() {
        Network network = IeeeCdfNetworkFactory.create14();
        VoltageLevel vl1 = network.getVoltageLevel("VL1");

        assertDoesNotThrow(() -> SubstationDiagramRenderer.render(vl1, new SldParameters()));
    }

    @ParameterizedTest
    @EnumSource(DesktopSldParameters.VoltageLevelLayout.class)
    void rendersSubstationWithEachVoltageLevelLayout(DesktopSldParameters.VoltageLevelLayout layout) throws IOException {
        Network network = FourSubstationsNodeBreakerFactory.create();
        DesktopSldParameters parameters = new DesktopSldParameters().setVoltageLevelLayout(layout);
        parameters.setComponentLibrary(new FlatDesignLibrary())
                .setStyleProviderFactory(new NominalVoltageStyleProviderFactory())
                .setSubstationLayoutFactory(new VerticalSubstationLayoutFactory());

        SubstationDiagramRenderer.DiagramRender render = SubstationDiagramRenderer.render(network.getSubstation("S1"), parameters);

        assertTrue(render.svg().contains("<svg"));
    }
}
