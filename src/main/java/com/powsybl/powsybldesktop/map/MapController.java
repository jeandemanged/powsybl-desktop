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
import com.powsybl.iidm.network.extensions.Coordinate;
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
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.concurrent.Worker;
import javafx.fxml.FXML;
import javafx.scene.Node;
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
import java.util.Arrays;
import java.util.Collections;
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
    /** Leaflet's {@code L.Projection.SphericalMercator.MAX_LATITUDE}. */
    private static final double MAX_LATITUDE = 85.0511287798;
    /** Width of the world at zoom 0, in pixels. */
    private static final double WORLD_SIZE = 256;
    /**
     * Projected coordinates are rounded to this, still 0.05 px at Leaflet's max zoom 19, to keep the JSON short:
     * Jackson writes the shortest representation of each double.
     */
    private static final double COORDINATE_PRECISION = 1e7;
    /** The hit-testing grid has this many cells along each axis. */
    private static final int GRID_SIZE = 128;
    /** Nearest neighbour distances are measured on at most this many substations, enough for a median. */
    private static final int SPACING_SAMPLE_SIZE = 1000;
    /** At most this many substations and this many lines per chunk sent to map.js. */
    private static final int CHUNK_SIZE = 2500;

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
    private Node loadingPane;

    /**
     * Builds the JSON sent to map.js off the FX thread, so the loading indicator and the basemap are painted
     * meanwhile. Restarting it on each refresh cancels a build still running, whose result is then dropped.
     */
    private final Service<List<String>> networkDataService = new Service<>() {
        @Override
        protected Task<List<String>> createTask() {
            Network network = mainModel.getNetwork();
            return new Task<>() {
                @Override
                protected List<String> call() throws JsonProcessingException {
                    return buildNetworkData(network);
                }
            };
        }
    };

    @FXML
    private Hyperlink checkAllBaseVoltagesLink;

    @FXML
    private Hyperlink checkNoBaseVoltagesLink;

    private MainModel mainModel;
    private boolean engineLoaded;
    private JSObject jsWindow;

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
                jsWindow = (JSObject) webView.getEngine().executeScript("window");
                jsWindow.setMember("controller", this);
                engineLoaded = true;
                applyBasemap();
                applyHiddenBaseVoltages();
                invalidateMapSize();
                refresh();
            }
        });
        networkDataService.setOnSucceeded(event -> {
            // Passed as JS string arguments rather than spliced into a script, so they need no escaping. map.js
            // only queues the chunks here, and applies them one by one afterwards, see onNetworkRendered.
            List<String> jsons = networkDataService.getValue();
            jsWindow.call("renderNetwork", jsons.getFirst());
            jsons.subList(1, jsons.size() - 1).forEach(chunk -> jsWindow.call("addNetworkChunk", chunk));
            jsWindow.call("endNetwork", jsons.getLast());
        });
        networkDataService.setOnFailed(event -> {
            LOGGER.error(networkDataService.getException().getMessage(), networkDataService.getException());
            loadingPane.setVisible(false);
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

    /**
     * Called from map.js once it has applied the last piece of the network sent by the latest refresh.
     */
    @SuppressWarnings("unused") // called from map.js
    public void onNetworkRendered() {
        loadingPane.setVisible(false);
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

    @Override
    public void dispose() {
        networkDataService.cancel();
        super.dispose();
    }

    private void refresh() {
        if (!engineLoaded) {
            return;
        }
        loadingPane.setVisible(true);
        networkDataService.restart();
    }

    /**
     * Runs off the FX thread: must not touch the scene graph or the WebView. Everything map.js would otherwise
     * compute per element is done here - the projection to Leaflet's zoom 0 pixel coordinates, line bounds, color
     * indices, the hit-testing grid, the substation spacing - as the FX thread runs map.js.
     * <p>
     * The result is sent in pieces map.js applies one at a time, painting in between: a {@link MapHeader}, then
     * {@link MapChunk}s of at most {@value #CHUNK_SIZE} substations and lines each, then the {@link GridData}.
     */
    private List<String> buildNetworkData(Network network) throws JsonProcessingException {
        List<MarkerData> substations = new ArrayList<>();
        List<LineData> lines = new ArrayList<>();
        if (network != null) {
            network.getSubstationStream().forEach(substation -> {
                SubstationPosition position = substation.getExtension(SubstationPosition.class);
                if (position != null) {
                    String baseVoltage = baseVoltageName(substation.getVoltageLevelStream()
                            .mapToDouble(VoltageLevel::getNominalV).max().orElse(Double.NaN));
                    substations.add(new MarkerData(substation.getId(),
                            projectX(position.getCoordinate().getLongitude()), projectY(position.getCoordinate().getLatitude()),
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
        return toJsonPieces(toMapData(substations, lines));
    }

    private List<String> toJsonPieces(MapData data) throws JsonProcessingException {
        int substationCount = data.substationIds().length;
        int lineCount = data.lineIds().length;
        GridData grid = data.grid();
        List<String> jsons = new ArrayList<>();
        jsons.add(objectMapper.writeValueAsString(new MapHeader(data.colors(), substationCount, lineCount, data.pointX().length,
                grid == null ? null : new double[] {grid.minX(), grid.minY(), grid.maxX(), grid.maxY()},
                substationSpacing(grid, data.substationX(), data.substationY()))));
        int chunkCount = Math.ceilDiv(Math.max(substationCount, lineCount), CHUNK_SIZE);
        for (int i = 0; i < chunkCount; i++) {
            int substationFrom = (int) ((long) substationCount * i / chunkCount);
            int substationTo = (int) ((long) substationCount * (i + 1) / chunkCount);
            int lineFrom = (int) ((long) lineCount * i / chunkCount);
            int lineTo = (int) ((long) lineCount * (i + 1) / chunkCount);
            int pointFrom = data.lineStart()[lineFrom];
            int pointTo = data.lineStart()[lineTo];
            jsons.add(objectMapper.writeValueAsString(new MapChunk(
                    substationFrom,
                    Arrays.copyOfRange(data.substationIds(), substationFrom, substationTo),
                    Arrays.copyOfRange(data.substationTexts(), substationFrom, substationTo),
                    Arrays.copyOfRange(data.substationX(), substationFrom, substationTo),
                    Arrays.copyOfRange(data.substationY(), substationFrom, substationTo),
                    Arrays.copyOfRange(data.substationColor(), substationFrom, substationTo),
                    Arrays.copyOfRange(data.substationBaseVoltages(), substationFrom, substationTo),
                    lineFrom,
                    Arrays.copyOfRange(data.lineIds(), lineFrom, lineTo),
                    Arrays.copyOfRange(data.lineTexts(), lineFrom, lineTo),
                    Arrays.copyOfRange(data.lineStart(), lineFrom, lineTo + 1),
                    Arrays.copyOfRange(data.lineBounds(), lineFrom * 4, lineTo * 4),
                    Arrays.copyOfRange(data.lineColor(), lineFrom, lineTo),
                    Arrays.copyOfRange(data.lineBaseVoltages(), lineFrom, lineTo),
                    Arrays.copyOfRange(data.lineDisconnected(), lineFrom, lineTo),
                    pointFrom,
                    Arrays.copyOfRange(data.pointX(), pointFrom, pointTo),
                    Arrays.copyOfRange(data.pointY(), pointFrom, pointTo),
                    Arrays.copyOfRange(data.pointLine(), pointFrom, pointTo))));
        }
        jsons.add(objectMapper.writeValueAsString(grid));
        return jsons;
    }

    /**
     * Median distance, at zoom 0, from a substation to its nearest neighbour, which map.js sizes substation markers
     * from; null with fewer than two distinct positions.
     */
    private static Double substationSpacing(GridData grid, double[] substationX, double[] substationY) {
        if (grid == null || substationX.length < 2) {
            return null;
        }
        int step = Math.max(1, substationX.length / SPACING_SAMPLE_SIZE);
        List<Double> distances = new ArrayList<>();
        for (int i = 0; i < substationX.length; i += step) {
            double squaredDistance = nearestSquaredDistance(grid, substationX, substationY, i);
            if (squaredDistance < Double.POSITIVE_INFINITY) {
                distances.add(Math.sqrt(squaredDistance));
            }
        }
        if (distances.isEmpty()) {
            return null;
        }
        Collections.sort(distances);
        return distances.get(distances.size() / 2);
    }

    /**
     * Searched through the grid ring by ring around substation {@code i}: a substation in ring r + 1 or beyond is
     * at least r cells away, so the search stops once the best distance is below that. Substations sharing a
     * position are skipped, they don't make the view any denser.
     */
    private static double nearestSquaredDistance(GridData grid, double[] substationX, double[] substationY, int i) {
        double x = substationX[i];
        double y = substationY[i];
        int cx = cell(x, grid.minX(), grid.cellWidth());
        int cy = cell(y, grid.minY(), grid.cellHeight());
        double cellSize = Math.min(grid.cellWidth(), grid.cellHeight());
        double best = Double.POSITIVE_INFINITY;
        for (int r = 0; r < GRID_SIZE && best > (r - 1) * cellSize * (r - 1) * cellSize; r++) {
            for (int gy = Math.max(0, cy - r); gy <= Math.min(GRID_SIZE - 1, cy + r); gy++) {
                for (int gx = Math.max(0, cx - r); gx <= Math.min(GRID_SIZE - 1, cx + r); gx++) {
                    if (Math.max(Math.abs(gx - cx), Math.abs(gy - cy)) == r) {
                        best = Math.min(best, nearestSquaredDistanceInCell(grid, substationX, substationY, x, y, gy * GRID_SIZE + gx));
                    }
                }
            }
        }
        return best;
    }

    private static double nearestSquaredDistanceInCell(GridData grid, double[] substationX, double[] substationY,
                                                       double x, double y, int cell) {
        double best = Double.POSITIVE_INFINITY;
        for (int j = grid.cellStart()[cell]; j < grid.cellStart()[cell + 1]; j++) {
            int item = grid.items()[j];
            if (item >= 0) {
                double dx = substationX[item] - x;
                double dy = substationY[item] - y;
                double distance = dx * dx + dy * dy;
                if (distance > 0 && distance < best) {
                    best = distance;
                }
            }
        }
        return best;
    }

    // Leaflet's EPSG:3857 (L.CRS.EPSG3857.latLngToPoint) at zoom 0
    private static double projectX(double longitude) {
        return round(WORLD_SIZE * (longitude / 360 + 0.5));
    }

    private static double projectY(double latitude) {
        double sin = Math.sin(Math.toRadians(Math.clamp(latitude, -MAX_LATITUDE, MAX_LATITUDE)));
        return round(WORLD_SIZE * (0.5 - Math.log((1 + sin) / (1 - sin)) / (4 * Math.PI)));
    }

    private static double round(double coordinate) {
        return Math.round(coordinate * COORDINATE_PRECISION) / COORDINATE_PRECISION;
    }

    private static MapData toMapData(List<MarkerData> substations, List<LineData> lines) {
        List<String> colors = new ArrayList<>();
        Map<String, Integer> colorIndices = new HashMap<>();
        int substationCount = substations.size();
        String[] substationIds = new String[substationCount];
        String[] substationTexts = new String[substationCount];
        String[] substationBaseVoltages = new String[substationCount];
        double[] substationX = new double[substationCount];
        double[] substationY = new double[substationCount];
        int[] substationColor = new int[substationCount];
        for (int i = 0; i < substationCount; i++) {
            MarkerData substation = substations.get(i);
            substationIds[i] = substation.id();
            substationTexts[i] = substation.text();
            substationBaseVoltages[i] = substation.baseVoltage();
            substationX[i] = substation.x();
            substationY[i] = substation.y();
            substationColor[i] = colorIndex(substation.color(), colors, colorIndices);
        }

        int lineCount = lines.size();
        int pointCount = lines.stream().mapToInt(line -> line.points().length / 2).sum();
        String[] lineIds = new String[lineCount];
        String[] lineTexts = new String[lineCount];
        String[] lineBaseVoltages = new String[lineCount];
        int[] lineColor = new int[lineCount];
        boolean[] lineDisconnected = new boolean[lineCount];
        int[] lineStart = new int[lineCount + 1];
        double[] lineBounds = new double[lineCount * 4];
        double[] pointX = new double[pointCount];
        double[] pointY = new double[pointCount];
        int[] pointLine = new int[pointCount];
        int k = 0;
        for (int i = 0; i < lineCount; i++) {
            LineData line = lines.get(i);
            lineIds[i] = line.id();
            lineTexts[i] = line.text();
            lineBaseVoltages[i] = line.baseVoltage();
            lineColor[i] = colorIndex(line.color(), colors, colorIndices);
            lineDisconnected[i] = line.disconnected();
            lineStart[i] = k;
            double minX = Double.POSITIVE_INFINITY;
            double minY = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY;
            double maxY = Double.NEGATIVE_INFINITY;
            for (int p = 0; p < line.points().length; p += 2) {
                double x = line.points()[p];
                double y = line.points()[p + 1];
                pointX[k] = x;
                pointY[k] = y;
                pointLine[k] = i;
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
                k++;
            }
            lineBounds[i * 4] = minX;
            lineBounds[i * 4 + 1] = minY;
            lineBounds[i * 4 + 2] = maxX;
            lineBounds[i * 4 + 3] = maxY;
        }
        lineStart[lineCount] = k;
        return new MapData(colors, substationIds, substationTexts, substationX, substationY, substationColor, substationBaseVoltages,
                lineIds, lineTexts, lineStart, lineBounds, lineColor, lineBaseVoltages, lineDisconnected, pointX, pointY, pointLine,
                buildGrid(substationX, substationY, lineStart, pointX, pointY));
    }

    /**
     * Uniform grid over all substations and line points: each cell lists the substations in it (their index,
     * {@code >= 0}) and the line segments crossing its bounds (their start point index {@code k}, as
     * {@code -(k + 1)}). Null when there is nothing to draw.
     */
    private static GridData buildGrid(double[] substationX, double[] substationY, int[] lineStart, double[] pointX, double[] pointY) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < substationX.length; i++) {
            minX = Math.min(minX, substationX[i]);
            minY = Math.min(minY, substationY[i]);
            maxX = Math.max(maxX, substationX[i]);
            maxY = Math.max(maxY, substationY[i]);
        }
        for (int k = 0; k < pointX.length; k++) {
            minX = Math.min(minX, pointX[k]);
            minY = Math.min(minY, pointY[k]);
            maxX = Math.max(maxX, pointX[k]);
            maxY = Math.max(maxY, pointY[k]);
        }
        if (minX == Double.POSITIVE_INFINITY) {
            return null;
        }
        GridData bounds = new GridData(GRID_SIZE, minX, minY, maxX, maxY,
                Math.max((maxX - minX) / GRID_SIZE, 1e-9), Math.max((maxY - minY) / GRID_SIZE, 1e-9), null, null);
        // cells as one flat item array, cell c's items from cellStart[c] to cellStart[c + 1]: a first pass counts
        // each cell's items, a second one fills them in
        int[] cellStart = new int[GRID_SIZE * GRID_SIZE + 1];
        forEachGridItem(bounds, substationX, substationY, lineStart, pointX, pointY, (item, cell) -> cellStart[cell + 1]++);
        for (int cell = 0; cell < GRID_SIZE * GRID_SIZE; cell++) {
            cellStart[cell + 1] += cellStart[cell];
        }
        int[] items = new int[cellStart[GRID_SIZE * GRID_SIZE]];
        int[] cursor = Arrays.copyOf(cellStart, GRID_SIZE * GRID_SIZE);
        forEachGridItem(bounds, substationX, substationY, lineStart, pointX, pointY, (item, cell) -> items[cursor[cell]++] = item);
        return new GridData(GRID_SIZE, minX, minY, maxX, maxY, bounds.cellWidth(), bounds.cellHeight(), cellStart, items);
    }

    private interface GridItemConsumer {
        void accept(int item, int cell);
    }

    private static void forEachGridItem(GridData grid, double[] substationX, double[] substationY, int[] lineStart,
                                        double[] pointX, double[] pointY, GridItemConsumer consumer) {
        for (int i = 0; i < substationX.length; i++) {
            forEachCell(grid, i, substationX[i], substationY[i], substationX[i], substationY[i], consumer);
        }
        for (int line = 0; line + 1 < lineStart.length; line++) {
            for (int k = lineStart[line]; k < lineStart[line + 1] - 1; k++) {
                forEachCell(grid, -(k + 1),
                        Math.min(pointX[k], pointX[k + 1]), Math.min(pointY[k], pointY[k + 1]),
                        Math.max(pointX[k], pointX[k + 1]), Math.max(pointY[k], pointY[k + 1]), consumer);
            }
        }
    }

    private static void forEachCell(GridData grid, int item, double x0, double y0, double x1, double y1, GridItemConsumer consumer) {
        int cx0 = cell(x0, grid.minX(), grid.cellWidth());
        int cx1 = cell(x1, grid.minX(), grid.cellWidth());
        int cy0 = cell(y0, grid.minY(), grid.cellHeight());
        int cy1 = cell(y1, grid.minY(), grid.cellHeight());
        for (int cy = cy0; cy <= cy1; cy++) {
            for (int cx = cx0; cx <= cx1; cx++) {
                consumer.accept(item, cy * GRID_SIZE + cx);
            }
        }
    }

    // same as map.js' cellX/cellY, which look points up in this grid
    private static int cell(double coordinate, double min, double cellSize) {
        return Math.clamp((long) Math.floor((coordinate - min) / cellSize), 0, GRID_SIZE - 1);
    }

    private static int colorIndex(String color, List<String> colors, Map<String, Integer> colorIndices) {
        return colorIndices.computeIfAbsent(color, c -> {
            colors.add(c);
            return colors.size() - 1;
        });
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
            List<Coordinate> coordinates = position.getCoordinates();
            double[] points = new double[coordinates.size() * 2];
            for (int i = 0; i < coordinates.size(); i++) {
                points[2 * i] = projectX(coordinates.get(i).getLongitude());
                points[2 * i + 1] = projectY(coordinates.get(i).getLatitude());
            }
            String baseVoltage = baseVoltageName(nominalV);
            lines.add(new LineData(shown.getId(), points, shown.getNameOrId(), baseVoltage, color(baseVoltage, DEFAULT_LINE_COLOR),
                    disconnected));
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

    private record MarkerData(String id, double x, double y, String text, String baseVoltage, String color) {
    }

    /**
     * @param points projected coordinates, x and y interleaved
     */
    private record LineData(String id, double[] points, String text, String baseVoltage, String color, boolean disconnected) {
    }

    /**
     * The arrays map.js draws from, named as there, sent in {@link MapChunk} slices: line {@code i}'s points are {@code lineStart[i]} (inclusive)
     * to {@code lineStart[i + 1]} (exclusive), its bounds {@code lineBounds[4 * i]} to {@code [4 * i + 3]} as
     * min x, min y, max x, max y, and {@code substationColor}/{@code lineColor} index {@code colors}.
     */
    private record MapData(List<String> colors,
                           String[] substationIds, String[] substationTexts, double[] substationX, double[] substationY,
                           int[] substationColor, String[] substationBaseVoltages,
                           String[] lineIds, String[] lineTexts, int[] lineStart, double[] lineBounds, int[] lineColor,
                           String[] lineBaseVoltages, boolean[] lineDisconnected,
                           double[] pointX, double[] pointY, int[] pointLine, GridData grid) {
    }

    /**
     * Sizes the arrays map.js fills from the chunks, and fits the view to {@code bounds} (min x, min y, max x,
     * max y; null when there is nothing to draw) before they arrive.
     */
    private record MapHeader(List<String> colors, int substationCount, int lineCount, int pointCount, double[] bounds,
                             Double substationSpacing) {
    }

    /**
     * A slice of {@link MapData}: substations from {@code substationFrom}, lines from {@code lineFrom} and their
     * points from {@code pointFrom}. {@code lineStart} has one more entry than {@code lineIds}, the end of the last
     * line's points.
     */
    private record MapChunk(int substationFrom, String[] substationIds, String[] substationTexts, double[] substationX,
                            double[] substationY, int[] substationColor, String[] substationBaseVoltages,
                            int lineFrom, String[] lineIds, String[] lineTexts, int[] lineStart, double[] lineBounds,
                            int[] lineColor, String[] lineBaseVoltages, boolean[] lineDisconnected,
                            int pointFrom, double[] pointX, double[] pointY, int[] pointLine) {
    }

    private record GridData(int size, double minX, double minY, double maxX, double maxY, double cellWidth, double cellHeight,
                            int[] cellStart, int[] items) {
    }
}
