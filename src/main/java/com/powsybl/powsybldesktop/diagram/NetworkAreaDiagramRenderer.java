/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.diagram;

import com.powsybl.iidm.network.Container;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Substation;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.nad.NadParameters;
import com.powsybl.nad.NetworkAreaDiagram;
import com.powsybl.nad.build.iidm.VoltageLevelFilter;
import com.powsybl.nad.layout.FixedLayoutFactory;
import com.powsybl.nad.layout.GeographicalLayoutFactory;
import com.powsybl.nad.layout.LayoutFactory;
import com.powsybl.nad.svg.metadata.DiagramMetadata;
import com.powsybl.nad.svg.metadata.EdgeInfoMetadata;
import com.powsybl.nad.svg.metadata.EdgeMetadata;
import com.powsybl.nad.svg.metadata.NodeMetadata;
import com.powsybl.powsybldesktop.parameters.DesktopNadParameters;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Area-diagram rendering, extracted out of {@link SubstationsController} so it can be unit-tested
 * without a JavaFX toolkit or {@link javafx.scene.web.WebView} (mirrors {@link SubstationDiagramRenderer}).
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class NetworkAreaDiagramRenderer {

    private NetworkAreaDiagramRenderer() {
    }

    /**
     * @param equipmentIds the equipment drawn by each SVG element that has one: voltage level circles, their buses and
     *                     labels give the voltage level (a boundary node's, its boundary line), branches, injections and
     *                     their values (arrows and labels, drawn in a group of their own) the branch or injection
     * @param hiddenEndIds the voltage level outside the diagram at the end of each branch drawn as a half line, by the
     *                     SVG ids of the branch and its values
     */
    public record DiagramRender(String svg, DiagramMetadata metadata, Map<String, String> equipmentIds, Map<String, String> hiddenEndIds) {
    }

    public static DiagramRender render(Container<?> container, int depth, NadParameters nadParameters) throws IOException {
        return render(container, depth, nadParameters, null);
    }

    /**
     * @param pinned a previous render, whose voltage levels (and their labels) this one keeps where they were, laying
     *               out the others around them; ignored by the geographical layout, whose positions don't move anyway
     */
    public static DiagramRender render(Container<?> container, int depth, NadParameters nadParameters, DiagramMetadata pinned) throws IOException {
        Network network = container.getNetwork();
        List<String> voltageLevelIds = voltageLevelIdsOf(container);
        Predicate<VoltageLevel> filter = VoltageLevelFilter.createVoltageLevelsDepthFilter(network, voltageLevelIds, depth);
        // Required by the WebView-based renderer regardless of user-configured parameters.
        nadParameters.getSvgParameters().setSvgWidthAndHeightAdded(true);
        // GeographicalLayoutFactory needs the network at construction (to read substation positions) whereas
        // LayoutFactory gets none, so it's swapped in around this render only
        LayoutFactory layoutFactory = nadParameters.getLayoutFactory();
        if (nadParameters instanceof DesktopNadParameters desktopParameters
                && desktopParameters.getLayoutAlgorithm() == DesktopNadParameters.LayoutAlgorithm.GEOGRAPHICAL) {
            nadParameters.setLayoutFactory(new GeographicalLayoutFactory(network));
        } else if (pinned != null) {
            nadParameters.setLayoutFactory(new FixedLayoutFactory(pinned.getFixedPositions(), pinned.getFixedTextPositions(), layoutFactory));
        }
        try (StringWriter svgWriter = new StringWriter();
             StringWriter metadataWriter = new StringWriter()) {
            NetworkAreaDiagram.draw(network, svgWriter, metadataWriter, nadParameters, filter);
            svgWriter.flush();
            metadataWriter.flush();
            DiagramMetadata metadata = DiagramMetadata.parseJson(new StringReader(metadataWriter.toString()));
            return new DiagramRender(svgWriter.toString(), metadata, equipmentIds(metadata), hiddenEndIds(metadata));
        } finally {
            nadParameters.setLayoutFactory(layoutFactory);
        }
    }

    private static Map<String, String> equipmentIds(DiagramMetadata metadata) {
        Map<String, String> ids = new HashMap<>();
        metadata.getNodesMetadata().forEach(node -> ids.put(node.getSvgId(), node.getEquipmentId()));
        // a bus's own equipment id is its bus view bus
        metadata.getBusNodesMetadata().forEach(bus -> ids.put(bus.getSvgId(), ids.get(bus.getVlNodeId())));
        metadata.getTextNodesMetadata().forEach(text -> ids.put(text.getSvgId(), text.getEquipmentId()));
        metadata.getEdgesMetadata().forEach(edge -> {
            ids.put(edge.getSvgId(), edge.getEquipmentId());
            putInfo(ids, edge.getEdgeInfo1(), edge.getEquipmentId());
            putInfo(ids, edge.getEdgeInfo2(), edge.getEquipmentId());
            putInfo(ids, edge.getEdgeInfoMiddle(), edge.getEquipmentId());
        });
        metadata.getInjectionsMetadata().forEach(injection -> {
            ids.put(injection.getSvgId(), injection.getEquipmentId());
            putInfo(ids, injection.getEdgeInfoMetadata(), injection.getEquipmentId());
        });
        ids.values().removeIf(Objects::isNull);
        return ids;
    }

    private static Map<String, String> hiddenEndIds(DiagramMetadata metadata) {
        Map<String, String> voltageLevels = metadata.getNodesMetadata().stream()
                .collect(Collectors.toMap(NodeMetadata::getSvgId, NodeMetadata::getEquipmentId));
        Map<String, String> ids = new HashMap<>();
        for (EdgeMetadata edge : metadata.getEdgesMetadata()) {
            String hiddenNode = edge.isInvisibleSide1() ? edge.getNode1SvgId() : edge.isInvisibleSide2() ? edge.getNode2SvgId() : null;
            if (hiddenNode != null) {
                String voltageLevel = voltageLevels.get(hiddenNode);
                Stream.concat(Stream.of(edge.getSvgId()), Stream.of(edge.getEdgeInfo1(), edge.getEdgeInfo2(), edge.getEdgeInfoMiddle())
                                .filter(Objects::nonNull).map(EdgeInfoMetadata::getSvgId))
                        .forEach(id -> ids.put(id, voltageLevel));
            }
        }
        return ids;
    }

    private static void putInfo(Map<String, String> ids, EdgeInfoMetadata info, String equipmentId) {
        if (info != null) {
            ids.put(info.getSvgId(), equipmentId);
        }
    }

    public static List<String> voltageLevelIdsOf(Container<?> container) {
        if (container instanceof VoltageLevel voltageLevel) {
            return List.of(voltageLevel.getId());
        }
        if (container instanceof Substation substation) {
            return substation.getVoltageLevelStream().map(Identifiable::getId).toList();
        }
        throw new IllegalArgumentException("Unsupported container type: " + container.getClass());
    }
}
