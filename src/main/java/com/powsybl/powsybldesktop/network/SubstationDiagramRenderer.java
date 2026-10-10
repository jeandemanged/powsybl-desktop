/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network;

import com.powsybl.iidm.network.Container;
import com.powsybl.sld.SingleLineDiagram;
import com.powsybl.sld.SldParameters;
import com.powsybl.sld.svg.GraphMetadata;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

/**
 * Single-line-diagram rendering, extracted out of {@link SubstationsController} so it can be
 * unit-tested without a JavaFX toolkit or {@link javafx.scene.web.WebView}.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class SubstationDiagramRenderer {

    private SubstationDiagramRenderer() {
    }

    public record DiagramRender(String svg, GraphMetadata metadata) {
    }

    public static DiagramRender render(Container<?> container, SldParameters sldParameters) throws IOException {
        try (StringWriter svgWriter = new StringWriter();
             StringWriter metadataWriter = new StringWriter()) {
            // Forced regardless of user-configured parameters: svgWidthAndHeightAdded is required by the
            // WebView-based renderer, and diagramName must track whichever container is currently selected.
            sldParameters.getSvgParameters()
                    .setSvgWidthAndHeightAdded(true)
                    .setDiagramName(container.getNameOrId());
            SingleLineDiagram.draw(container.getNetwork(), container.getId(), svgWriter, metadataWriter, sldParameters);
            svgWriter.flush();
            metadataWriter.flush();
            String svg = svgWriter.toString();
            GraphMetadata metadata = GraphMetadata.parseJson(
                    new ByteArrayInputStream(metadataWriter.toString().getBytes(StandardCharsets.UTF_8)));
            return new DiagramRender(svg, metadata);
        }
    }
}
