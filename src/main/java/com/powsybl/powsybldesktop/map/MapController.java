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
import com.powsybl.commons.config.BaseVoltageConfig;
import com.powsybl.commons.config.BaseVoltagesConfig;
import com.powsybl.iidm.network.BoundaryLine;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Line;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Substation;
import com.powsybl.iidm.network.TieLine;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.iidm.network.extensions.LinePosition;
import com.powsybl.iidm.network.extensions.SubstationPosition;
import com.powsybl.powsybldesktop.MainModel;
import com.powsybl.powsybldesktop.navigation.BoundaryLineNavigationState;
import com.powsybl.powsybldesktop.navigation.ContainerNavigationState;
import com.powsybl.powsybldesktop.navigation.LineNavigationState;
import com.powsybl.powsybldesktop.navigation.NavigationEvent;
import com.powsybl.powsybldesktop.navigation.NavigationType;
import com.powsybl.powsybldesktop.navigation.TieLineNavigationState;
import com.powsybl.powsybldesktop.utils.AbstractDisposableController;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.concurrent.Worker;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.web.WebView;
import javafx.util.StringConverter;
import netscape.javascript.JSObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shows substations and lines on a basemap, using the coordinates carried by the IIDM
 * {@link SubstationPosition}/{@link LinePosition} network extensions - equipment without one of those
 * extensions simply isn't drawn. Lines, tie lines and boundary lines are drawn alike: the CGMES geographical
 * layout import puts a tie line's positions on its two boundary line halves, which are then drawn as, and
 * navigate to, that tie line. A line disconnected on at least one side is dashed. Clicking a substation marker or a line navigates to it in the substations view /
 * lines, tie lines or boundary lines table, same as any other cross-view link in this app.
 * <p>
 * Built the same way as the single line diagram ({@code SubstationsController}/{@code sld.js}): the
 * stylesheet and scripts are injected into a {@link WebView} shell, with a {@code window.controller}
 * bridge for click callbacks from {@code map.js}. {@code leaflet.js}/{@code leaflet.css} aren't checked
 * in - they're unpacked into this package from the {@code org.webjars:leaflet} artifact at build time
 * (see the {@code unpack-leaflet} execution in {@code pom.xml}), so only the version in {@code pom.xml}
 * pins them.
 * <p>
 * The default basemap is {@link Basemap#OFFLINE}, country outlines bundled as {@code countries.geojson}
 * (Natural Earth 1:50m admin-0 countries, public domain, properties stripped and coordinates rounded to
 * 0.01 degree), so the view works without internet access. OpenStreetMap tiles are opt-in.
 * <p>
 * Substations and lines are colored by base voltage like single line diagrams: ranges from the
 * {@link BaseVoltagesConfig}, colors from the single line diagram's {@code baseVoltages.css}. A substation
 * takes its highest voltage level nominal voltage, a line the highest of its two ends. The highest base voltage
 * range is open-ended here, so e.g. 750 kV equipment is shown as the 300-500 kV range rather than uncolored.
 * Each base voltage can be hidden from an overlay checkbox, remembered in
 * {@link MainModel#getMapHiddenBaseVoltages()}.
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
                    <script>var COUNTRIES = %s;</script>
                </head>
                <body>
                    <div id="map"></div>
                    <script>%s</script>
                </body>
            </html>
            """;

    private static final Pattern BASE_VOLTAGE_COLOR = Pattern.compile("\\.sld-(\\w+)\\s*\\{\\s*--sld-vl-color:\\s*(#\\w+)\\s*}");
    private static final String DEFAULT_SUBSTATION_COLOR = "#000000";
    private static final String DEFAULT_LINE_COLOR = "#616161";

    private enum Basemap {
        OFFLINE("offline", "map.basemap.offline"),
        OPEN_STREET_MAP("osm", "map.basemap.openStreetMap");

        private final String jsName;
        private final String labelKey;

        Basemap(String jsName, String labelKey) {
            this.jsName = jsName;
            this.labelKey = labelKey;
        }
    }

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final BaseVoltagesConfig baseVoltagesConfig = BaseVoltagesConfig.fromPlatformConfig();
    private final Map<String, String> baseVoltageColors = readBaseVoltageColors();
    private final List<BaseVoltageConfig> baseVoltages = baseVoltagesConfig.getBaseVoltages().stream()
            .filter(baseVoltage -> baseVoltage.getProfile().equals(baseVoltagesConfig.getDefaultProfile()))
            .toList();
    private final BaseVoltageConfig highestBaseVoltage = baseVoltages.stream()
            .max(Comparator.comparingDouble(BaseVoltageConfig::getMaxValue))
            .orElse(null);
    private final List<CheckBox> baseVoltageCheckBoxes = new ArrayList<>();
    private boolean settingAllBaseVoltages;

    @FXML
    private WebView webView;

    @FXML
    private ComboBox<Basemap> basemapComboBox;

    @FXML
    private Label basemapUnreachableLabel;

    @FXML
    private VBox baseVoltagesBox;

    @FXML
    private Hyperlink checkAllBaseVoltagesLink;

    @FXML
    private Hyperlink checkNoBaseVoltagesLink;

    private MainModel mainModel;
    private boolean engineLoaded;

    @FXML
    private void initialize() {
        webView.setContextMenuEnabled(false);
        basemapComboBox.getItems().setAll(Basemap.values());
        basemapComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(Basemap basemap) {
                return basemap == null ? "" : Messages.get(basemap.labelKey);
            }

            @Override
            public Basemap fromString(String string) {
                return null;
            }
        });
        basemapComboBox.setValue(Basemap.OFFLINE);
        basemapComboBox.valueProperty().addListener((observable, oldValue, newValue) -> applyBasemap());
        basemapUnreachableLabel.managedProperty().bind(basemapUnreachableLabel.visibleProperty());

        String html = HTML_SHELL.formatted(readResource("leaflet.css"), readResource("leaflet.js"),
                readResource("countries.geojson"), readResource("map.js"));
        webView.getEngine().getLoadWorker().stateProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == Worker.State.SUCCEEDED) {
                JSObject window = (JSObject) webView.getEngine().executeScript("window");
                window.setMember("controller", this);
                engineLoaded = true;
                applyBasemap();
                applyHiddenBaseVoltages();
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

    private void applyBasemap() {
        basemapUnreachableLabel.setVisible(false);
        if (engineLoaded) {
            webView.getEngine().executeScript("setBasemap('" + basemapComboBox.getValue().jsName + "')");
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
                || network.getLineStream().anyMatch(line -> line.getExtension(LinePosition.class) != null)
                || network.getTieLineStream().anyMatch(tieLine -> tieLine.getExtension(LinePosition.class) != null)
                || network.getBoundaryLineStream().anyMatch(boundaryLine -> boundaryLine.getExtension(LinePosition.class) != null));
    }

    public void setMainModel(MainModel mainModel) {
        this.mainModel = Objects.requireNonNull(mainModel);
        listenerManager.listen(mainModel.networkProperty(), (observable, oldValue, newValue) -> refresh());
        listenerManager.listen(mainModel.updateProperty(), (observable, oldValue, newValue) -> refresh());
        createBaseVoltageCheckBoxes();
    }

    private void createBaseVoltageCheckBoxes() {
        checkAllBaseVoltagesLink.setOnAction(event -> setAllBaseVoltagesSelected(true));
        checkNoBaseVoltagesLink.setOnAction(event -> setAllBaseVoltagesSelected(false));
        for (BaseVoltageConfig baseVoltage : baseVoltages) {
            CheckBox checkBox = new CheckBox(baseVoltage == highestBaseVoltage
                    ? Messages.get("map.baseVoltages.rangeAbove", baseVoltage.getMinValue())
                    : Messages.get("map.baseVoltages.range", baseVoltage.getMinValue(), baseVoltage.getMaxValue()));
            checkBox.setGraphic(new Rectangle(10, 10, Color.web(color(baseVoltage.getName(), DEFAULT_SUBSTATION_COLOR))));
            checkBox.setSelected(!mainModel.getMapHiddenBaseVoltages().contains(baseVoltage.getName()));
            checkBox.selectedProperty().addListener((observable, oldValue, selected) -> {
                if (selected) {
                    mainModel.getMapHiddenBaseVoltages().remove(baseVoltage.getName());
                } else {
                    mainModel.getMapHiddenBaseVoltages().add(baseVoltage.getName());
                }
                if (!settingAllBaseVoltages) {
                    applyHiddenBaseVoltages();
                }
            });
            baseVoltageCheckBoxes.add(checkBox);
            baseVoltagesBox.getChildren().add(checkBox);
        }
    }

    // a single redraw rather than one per checkbox
    private void setAllBaseVoltagesSelected(boolean selected) {
        settingAllBaseVoltages = true;
        baseVoltageCheckBoxes.forEach(checkBox -> checkBox.setSelected(selected));
        settingAllBaseVoltages = false;
        applyHiddenBaseVoltages();
    }

    // filtered in map.js rather than by re-sending the network, which would rebuild all its arrays and its grid
    private void applyHiddenBaseVoltages() {
        if (engineLoaded) {
            try {
                webView.getEngine().executeScript("setHiddenBaseVoltages(" + objectMapper.writeValueAsString(mainModel.getMapHiddenBaseVoltages()) + ")");
            } catch (JsonProcessingException e) {
                LOGGER.error(e.getMessage(), e);
            }
        }
    }

    /**
     * Called from map.js as OpenStreetMap tiles load or fail: WebKit gives no other signal of a missing
     * connection, and a successful tile after a failed one (connection back) clears the warning.
     */
    @SuppressWarnings("unused") // called from map.js
    public void onTileLoad(boolean success) {
        basemapUnreachableLabel.setVisible(!success && basemapComboBox.getValue() == Basemap.OPEN_STREET_MAP);
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
        Identifiable<?> identifiable = network == null ? null : network.getIdentifiable(lineId);
        if (identifiable instanceof Line line) {
            mainModel.addNavigationEvent(NavigationEvent.create(NavigationType.NETWORK_TABLE_LINES, LineNavigationState.create(line)));
        } else if (identifiable instanceof TieLine tieLine) {
            mainModel.addNavigationEvent(NavigationEvent.create(NavigationType.NETWORK_TABLE_TIE_LINES, TieLineNavigationState.create(tieLine)));
        } else if (identifiable instanceof BoundaryLine boundaryLine) {
            mainModel.addNavigationEvent(NavigationEvent.create(NavigationType.NETWORK_TABLE_BOUNDARY_LINES, BoundaryLineNavigationState.create(boundaryLine)));
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
                    String baseVoltage = baseVoltageName(substation.getVoltageLevelStream()
                            .mapToDouble(VoltageLevel::getNominalV).max().orElse(Double.NaN));
                    substations.add(new MarkerData(substation.getId(),
                            position.getCoordinate().getLatitude(), position.getCoordinate().getLongitude(),
                            substation.getNameOrId(), baseVoltage, color(baseVoltage, DEFAULT_SUBSTATION_COLOR)));
                }
            });
            network.getLineStream().forEach(line -> addLine(lines, line, line,
                    Math.max(line.getTerminal1().getVoltageLevel().getNominalV(), line.getTerminal2().getVoltageLevel().getNominalV()),
                    !line.getTerminal1().isConnected() || !line.getTerminal2().isConnected()));
            network.getTieLineStream().forEach(tieLine -> addLine(lines, tieLine, tieLine, nominalV(tieLine), isDisconnected(tieLine)));
            network.getBoundaryLineStream().forEach(boundaryLine -> {
                TieLine tieLine = boundaryLine.getTieLine().orElse(null);
                if (tieLine == null) {
                    addLine(lines, boundaryLine, boundaryLine, boundaryLine.getTerminal().getVoltageLevel().getNominalV(),
                            !boundaryLine.getTerminal().isConnected());
                } else if (tieLine.getExtension(LinePosition.class) == null) {
                    addLine(lines, boundaryLine, tieLine, nominalV(tieLine), isDisconnected(tieLine));
                }
            });
        }
        renderNetwork(new MapData(substations, lines));
    }

    private static double nominalV(TieLine tieLine) {
        return Math.max(tieLine.getBoundaryLine1().getTerminal().getVoltageLevel().getNominalV(),
                tieLine.getBoundaryLine2().getTerminal().getVoltageLevel().getNominalV());
    }

    private static boolean isDisconnected(TieLine tieLine) {
        return !tieLine.getBoundaryLine1().getTerminal().isConnected() || !tieLine.getBoundaryLine2().getTerminal().isConnected();
    }

    /**
     * Adds {@code positioned}'s line position, if any, drawn as and navigating to {@code shown}: they differ for a
     * tie line half.
     */
    private <T extends Identifiable<T>> void addLine(List<LineData> lines, T positioned, Identifiable<?> shown, double nominalV,
                                                     boolean disconnected) {
        LinePosition<T> position = positioned.getExtension(LinePosition.class);
        if (position != null && !position.getCoordinates().isEmpty()) {
            List<double[]> points = position.getCoordinates().stream()
                    .map(coordinate -> new double[] {coordinate.getLatitude(), coordinate.getLongitude()})
                    .toList();
            String baseVoltage = baseVoltageName(nominalV);
            lines.add(new LineData(shown.getId(), points, shown.getNameOrId(), baseVoltage, color(baseVoltage, DEFAULT_LINE_COLOR),
                    disconnected));
        }
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

    private String baseVoltageName(double nominalV) {
        if (highestBaseVoltage != null && nominalV >= highestBaseVoltage.getMinValue()) {
            return highestBaseVoltage.getName();
        }
        return baseVoltagesConfig.getBaseVoltageName(nominalV, baseVoltagesConfig.getDefaultProfile()).orElse(null);
    }

    private String color(String baseVoltage, String defaultColor) {
        return baseVoltage == null ? defaultColor : baseVoltageColors.getOrDefault(baseVoltage, defaultColor);
    }

    private Map<String, String> readBaseVoltageColors() {
        Map<String, String> colors = new HashMap<>();
        Matcher matcher = BASE_VOLTAGE_COLOR.matcher(readResource("/baseVoltages.css"));
        while (matcher.find()) {
            colors.put(matcher.group(1), matcher.group(2));
        }
        return colors;
    }

    private String readResource(String name) {
        try (var stream = getClass().getResourceAsStream(name)) {
            return new String(Objects.requireNonNull(stream).readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private record MarkerData(String id, double lat, double lng, String text, String baseVoltage, String color) {
    }

    private record LineData(String id, List<double[]> points, String text, String baseVoltage, String color, boolean disconnected) {
    }

    private record MapData(List<MarkerData> substations, List<LineData> lines) {
    }
}
