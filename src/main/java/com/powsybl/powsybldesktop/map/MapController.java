/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.iidm.network.Line;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Substation;
import com.powsybl.iidm.network.extensions.LinePosition;
import com.powsybl.iidm.network.extensions.SubstationPosition;
import com.powsybl.powsybldesktop.MainModel;
import com.powsybl.powsybldesktop.navigation.ContainerNavigationState;
import com.powsybl.powsybldesktop.navigation.LineNavigationState;
import com.powsybl.powsybldesktop.navigation.NavigationEvent;
import com.powsybl.powsybldesktop.navigation.NavigationType;
import com.powsybl.powsybldesktop.utils.AbstractDisposableController;
import javafx.concurrent.Worker;
import javafx.fxml.FXML;
import javafx.scene.web.WebView;
import netscape.javascript.JSObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

/**
 * Shows substations and lines on an OpenStreetMap basemap, using the coordinates carried by the IIDM
 * {@link SubstationPosition}/{@link LinePosition} network extensions - equipment without one of those
 * extensions simply isn't drawn. Clicking a substation marker or a line navigates to it in the
 * substations view / lines table, same as any other cross-view link in this app.
 * <p>
 * Built the same way as the single line diagram ({@code SubstationsController}/{@code sld.js}): the
 * stylesheet and scripts are injected into a {@link WebView} shell, with a {@code window.controller}
 * bridge for click callbacks from {@code map.js}. {@code leaflet.js}/{@code leaflet.css} aren't checked
 * in - they're unpacked into this package from the {@code org.webjars:leaflet} artifact at build time
 * (see the {@code unpack-leaflet} execution in {@code pom.xml}), so only the version in {@code pom.xml}
 * pins them.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class MapController extends AbstractDisposableController {

    private static final Logger LOGGER = LoggerFactory.getLogger(MapController.class);

    private static final String HTML_SHELL = """
            <html>
                <head>
                    <style>%s</style>
                    <style>
                        html, body { height: 100%%; margin: 0; padding: 0; overflow: hidden; }
                        #map { position: absolute; top: 0; right: 0; bottom: 0; left: 0; }
                        /* Leaflet tags every tile with mix-blend-mode: plus-lighter, a Chromium
                           fade-seam workaround. It makes WebKit isolate each tile into its own
                           transparency layer, and Prism then fails to hand out that many offscreen
                           textures - "IllegalArgumentException: Texture must be non-null" thrown
                           from the render thread on every repaint. Tile fade animation is off here
                           anyway, so there are no seams for it to hide. */
                        .leaflet-container img.leaflet-tile { mix-blend-mode: normal; }
                    </style>
                    <script>%s</script>
                </head>
                <body>
                    <div id="map"></div>
                    <script>%s</script>
                </body>
            </html>
            """;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @FXML
    private WebView webView;

    private MainModel mainModel;
    private boolean engineLoaded;

    @FXML
    private void initialize() {
        webView.setContextMenuEnabled(false);
        String html = HTML_SHELL.formatted(readResource("leaflet.css"), readResource("leaflet.js"), readResource("map.js"));
        webView.getEngine().getLoadWorker().stateProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == Worker.State.SUCCEEDED) {
                JSObject window = (JSObject) webView.getEngine().executeScript("window");
                window.setMember("controller", this);
                engineLoaded = true;
                invalidateMapSize();
                refresh();
            }
        });
        webView.getEngine().loadContent(html);

        // Leaflet computes its container size once, at L.map() init time - which happens as soon as the
        // WebView finishes loading, well before this AnchorPane has been laid out to its final size in the
        // BorderPane center (it's still at the FXML's placeholder prefWidth/prefHeight then). Without this,
        // tiles/markers past that stale boundary are positioned using outdated pixel math (visible as
        // misplaced tile blocks and markers rendered outside the map viewport).
        webView.widthProperty().addListener((observable, oldValue, newValue) -> invalidateMapSize());
        webView.heightProperty().addListener((observable, oldValue, newValue) -> invalidateMapSize());
    }

    private void invalidateMapSize() {
        if (engineLoaded) {
            webView.getEngine().executeScript("if (window.map) { window.map.invalidateSize({animate: false, pan: false}); }");
        }
    }

    /**
     * Whether {@code network} has anything this view could draw, i.e. at least one substation or line
     * carrying a position extension (only networks imported with the CGMES geographical layout profile do).
     * Used by {@code MainController} to hide the Map toolbar button for networks without one.
     */
    public static boolean hasPositions(Network network) {
        return network != null
                && (network.getSubstationStream().anyMatch(substation -> substation.getExtension(SubstationPosition.class) != null)
                || network.getLineStream().anyMatch(line -> line.getExtension(LinePosition.class) != null));
    }

    public void setMainModel(MainModel mainModel) {
        this.mainModel = Objects.requireNonNull(mainModel);
        listenerManager.listen(mainModel.networkProperty(), (observable, oldValue, newValue) -> refresh());
        listenerManager.listen(mainModel.updateProperty(), (observable, oldValue, newValue) -> refresh());
    }

    @SuppressWarnings("unused") // called from map.js
    public void onSubstationClick(String substationId) {
        Network network = mainModel.getNetwork();
        Substation substation = network == null ? null : network.getSubstation(substationId);
        if (substation != null) {
            mainModel.addNavigationEvent(NavigationEvent.create(NavigationType.SUBSTATIONS, ContainerNavigationState.create(substation)));
        }
    }

    @SuppressWarnings("unused") // called from map.js
    public void onLineClick(String lineId) {
        Network network = mainModel.getNetwork();
        Line line = network == null ? null : network.getLine(lineId);
        if (line != null) {
            mainModel.addNavigationEvent(NavigationEvent.create(NavigationType.NETWORK_TABLE_LINES, LineNavigationState.create(line)));
        }
    }

    private void refresh() {
        if (!engineLoaded) {
            return;
        }
        Network network = mainModel.getNetwork();
        List<MarkerData> substations = new ArrayList<>();
        List<LineData> lines = new ArrayList<>();
        if (network != null) {
            network.getSubstationStream().forEach(substation -> {
                SubstationPosition position = substation.getExtension(SubstationPosition.class);
                if (position != null) {
                    substations.add(new MarkerData(substation.getId(),
                            position.getCoordinate().getLatitude(), position.getCoordinate().getLongitude(),
                            substation.getNameOrId()));
                }
            });
            network.getLineStream().forEach(line -> {
                LinePosition<Line> position = line.getExtension(LinePosition.class);
                if (position != null && !position.getCoordinates().isEmpty()) {
                    List<double[]> points = position.getCoordinates().stream()
                            .map(coordinate -> new double[] {coordinate.getLatitude(), coordinate.getLongitude()})
                            .toList();
                    lines.add(new LineData(line.getId(), points, line.getNameOrId()));
                }
            });
        }
        renderNetwork(new MapData(substations, lines));
    }

    private void renderNetwork(MapData data) {
        try {
            String json = objectMapper.writeValueAsString(data);
            String base64 = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
            webView.getEngine().executeScript("renderNetwork('" + base64 + "')");
        } catch (JsonProcessingException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    private String readResource(String name) {
        try (var stream = getClass().getResourceAsStream(name)) {
            return new String(Objects.requireNonNull(stream).readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private record MarkerData(String id, double lat, double lng, String text) {
    }

    private record LineData(String id, List<double[]> points, String text) {
    }

    private record MapData(List<MarkerData> substations, List<LineData> lines) {
    }
}
