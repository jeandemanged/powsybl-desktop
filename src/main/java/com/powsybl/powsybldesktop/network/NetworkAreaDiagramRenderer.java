/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network;

import com.powsybl.iidm.network.Container;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Substation;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.nad.NadParameters;
import com.powsybl.nad.NetworkAreaDiagram;
import com.powsybl.nad.build.iidm.VoltageLevelFilter;
import com.powsybl.nad.layout.GeographicalLayoutFactory;
import com.powsybl.nad.layout.LayoutFactory;
import com.powsybl.powsybldesktop.parameters.DesktopNadParameters;

import java.io.IOException;
import java.io.StringWriter;
import java.util.List;
import java.util.function.Predicate;

/**
 * Area-diagram rendering, extracted out of {@link SubstationsController} so it can be unit-tested
 * without a JavaFX toolkit or {@link javafx.scene.web.WebView} (mirrors {@link SubstationDiagramRenderer}).
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class NetworkAreaDiagramRenderer {

    private NetworkAreaDiagramRenderer() {
    }

    public static String render(Container<?> container, int depth, NadParameters nadParameters) throws IOException {
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
        }
        try (StringWriter svgWriter = new StringWriter();
             StringWriter metadataWriter = new StringWriter()) {
            NetworkAreaDiagram.draw(network, svgWriter, metadataWriter, nadParameters, filter);
            svgWriter.flush();
            return svgWriter.toString();
        } finally {
            nadParameters.setLayoutFactory(layoutFactory);
        }
    }

    private static List<String> voltageLevelIdsOf(Container<?> container) {
        if (container instanceof VoltageLevel voltageLevel) {
            return List.of(voltageLevel.getId());
        }
        if (container instanceof Substation substation) {
            return substation.getVoltageLevelStream().map(Identifiable::getId).toList();
        }
        throw new IllegalArgumentException("Unsupported container type: " + container.getClass());
    }
}
