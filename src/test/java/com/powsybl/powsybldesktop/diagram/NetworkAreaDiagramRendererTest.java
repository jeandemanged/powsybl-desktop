/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.diagram;

import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Substation;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.iidm.network.test.FourSubstationsNodeBreakerFactory;
import com.powsybl.nad.NadParameters;
import com.powsybl.nad.layout.LayoutFactory;
import com.powsybl.nad.model.Point;
import com.powsybl.nad.svg.metadata.DiagramMetadata;
import com.powsybl.nad.svg.metadata.EdgeMetadata;
import com.powsybl.nad.svg.metadata.InjectionMetadata;
import com.powsybl.powsybldesktop.parameters.DesktopNadParameters;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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

        String svg = NetworkAreaDiagramRenderer.render(s2vl1, 1, new NadParameters()).svg();

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
    void geographicalLayoutIsResolvedPerRenderAndFactoryRestored() throws IOException {
        Network network = FourSubstationsNodeBreakerFactory.create();
        DesktopNadParameters parameters = new DesktopNadParameters().setLayoutAlgorithm(DesktopNadParameters.LayoutAlgorithm.GEOGRAPHICAL);
        LayoutFactory layoutFactory = parameters.getLayoutFactory();

        String svg = NetworkAreaDiagramRenderer.render(network.getSubstation("S2"), 2, parameters).svg();

        assertTrue(svg.contains("<svg"));
        assertSame(layoutFactory, parameters.getLayoutFactory());
    }

    @Test
    void widerDepthIncludesMoreVoltageLevels() throws IOException {
        Network network = FourSubstationsNodeBreakerFactory.create();
        VoltageLevel s2vl1 = network.getVoltageLevel("S2VL1");

        String depth1 = NetworkAreaDiagramRenderer.render(s2vl1, 1, new NadParameters()).svg();
        String depth3 = NetworkAreaDiagramRenderer.render(s2vl1, 3, new NadParameters()).svg();

        assertTrue(depth3.length() > depth1.length());
    }

    @Test
    void mapsDrawnElementsToTheirEquipment() throws IOException {
        Network network = FourSubstationsNodeBreakerFactory.create();
        NadParameters parameters = new NadParameters();
        parameters.getLayoutParameters().setInjectionsAdded(true);

        NetworkAreaDiagramRenderer.DiagramRender render = NetworkAreaDiagramRenderer.render(network.getVoltageLevel("S1VL2"), 1, parameters);

        DiagramMetadata metadata = render.metadata();
        Map<String, String> ids = render.equipmentIds();
        String vlNode = metadata.getNodesMetadata().stream().filter(node -> node.getEquipmentId().equals("S1VL2")).findFirst().orElseThrow().getSvgId();
        // a voltage level is drawn as its circle, its buses and its label
        metadata.getBusNodesMetadata().stream().filter(bus -> bus.getVlNodeId().equals(vlNode))
                .forEach(bus -> assertEquals("S1VL2", ids.get(bus.getSvgId())));
        metadata.getTextNodesMetadata().stream().filter(text -> text.getVlNodeId().equals(vlNode))
                .forEach(text -> assertEquals("S1VL2", ids.get(text.getSvgId())));
        // a branch's values are drawn apart from the branch
        EdgeMetadata transformer = metadata.getEdgesMetadata().stream().filter(edge -> edge.getEquipmentId().equals("TWT")).findFirst().orElseThrow();
        assertEquals("TWT", ids.get(transformer.getSvgId()));
        assertEquals("TWT", ids.get(transformer.getEdgeInfo1().getSvgId()));
        InjectionMetadata load = metadata.getInjectionsMetadata().stream().filter(injection -> injection.getEquipmentId().equals("LD2")).findFirst().orElseThrow();
        assertEquals("LD2", ids.get(load.getSvgId()));
        assertEquals("LD2", ids.get(load.getEdgeInfoMetadata().getSvgId()));
        // in the SVG under the same ids
        assertTrue(render.svg().contains("id=\"" + transformer.getSvgId() + "\""));
    }

    @Test
    void mapsAHalfLineToTheVoltageLevelOutsideTheDiagram() throws IOException {
        Network network = FourSubstationsNodeBreakerFactory.create();

        NetworkAreaDiagramRenderer.DiagramRender render = NetworkAreaDiagramRenderer.render(network.getVoltageLevel("S2VL1"), 1, new DesktopNadParameters());

        // S3VL1 is drawn, S4VL1 one step further isn't
        EdgeMetadata line = render.metadata().getEdgesMetadata().stream().filter(edge -> edge.getEquipmentId().equals("LINE_S3S4")).findFirst().orElseThrow();
        assertEquals("S4VL1", render.hiddenEndIds().get(line.getSvgId()));
        assertEquals("S4VL1", render.hiddenEndIds().get(line.getEdgeInfo1().getSvgId()));
        EdgeMetadata drawnLine = render.metadata().getEdgesMetadata().stream().filter(edge -> edge.getEquipmentId().equals("LINE_S2S3")).findFirst().orElseThrow();
        assertNull(render.hiddenEndIds().get(drawnLine.getSvgId()));
    }

    @ParameterizedTest
    @EnumSource(value = DesktopNadParameters.LayoutAlgorithm.class, names = {"ATLAS2_FORCE", "BASIC_FORCE"})
    void aPinnedRenderKeepsTheVoltageLevelsOfThePreviousOne(DesktopNadParameters.LayoutAlgorithm algorithm) throws IOException {
        Network network = FourSubstationsNodeBreakerFactory.create();
        DesktopNadParameters parameters = new DesktopNadParameters().setLayoutAlgorithm(algorithm);
        DiagramMetadata previous = NetworkAreaDiagramRenderer.render(network.getVoltageLevel("S2VL1"), 1, parameters).metadata();
        LayoutFactory layoutFactory = parameters.getLayoutFactory();

        DiagramMetadata centered = NetworkAreaDiagramRenderer.render(network.getVoltageLevel("S3VL1"), 1, parameters, previous).metadata();

        Map<String, Point> before = previous.getFixedPositions();
        Map<String, Point> after = centered.getFixedPositions();
        // shown before and after
        for (String voltageLevel : List.of("S2VL1", "S3VL1")) {
            assertEquals(before.get(voltageLevel).x(), after.get(voltageLevel).x(), 1e-6, voltageLevel);
            assertEquals(before.get(voltageLevel).y(), after.get(voltageLevel).y(), 1e-6, voltageLevel);
        }
        // the pinned layout factory is swapped in around the render only
        assertSame(layoutFactory, parameters.getLayoutFactory());
    }
}
