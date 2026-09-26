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
import javafx.application.Platform;
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

import java.awt.Desktop;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shows substations and lines on a basemap, using the coordinates carried by the IIDM
 * {@link SubstationPosition}/{@link LinePosition} network extensions - equipment without one of those
 * extensions simply isn't drawn. A line disconnected on at least one side is dashed. Clicking a substation marker
 * or a line navigates to it in the substations view / lines, tie lines or boundary lines table, same as any other
 * cross-view link in this app.
 * <p>
 * Built the same way as the single line diagram ({@code SubstationsController}/{@code sld.js}): the
 * stylesheet and scripts are injected into a {@link WebView} shell, with a {@code window.controller}
 * bridge for callbacks from {@code map.js}. {@code leaflet.js}/{@code leaflet.css} aren't checked
 * in - they're unpacked into this package from the {@code org.webjars:leaflet} artifact at build time
 * (see the {@code unpack-leaflet} execution in {@code pom.xml}), so only the version in {@code pom.xml}
 * pins them.
 * <p>
 * The network isn't drawn by map.js: the WebView runs JavaScript on the FX thread, where drawing a large network
 * froze the UI on every load, pan and zoom. Instead, map.js shows a Leaflet tile layer whose tiles
 * {@link MapTileRenderer} draws here on background threads, from a {@link MapNetworkData} snapshot also built off
 * the FX thread; hover and clicks are hit-tested here too. While zooming, Leaflet keeps showing the previous zoom
 * level's tiles scaled until the new ones arrive.
 * <p>
 * The default basemap is {@link Basemap#OFFLINE}, drawn in the same tiles below the network, so the view works
 * without internet access. OpenStreetMap tiles are opt-in.
 * <p>
 * Substations and lines are colored by base voltage like single line diagrams: ranges from the
 * {@link BaseVoltagesConfig}, colors from the single line diagram's {@code baseVoltages.css}. The highest base voltage
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
                </head>
                <body>
                    <div id="map"></div>
                    <script>%s</script>
                </body>
            </html>
            """;

    private static final Pattern BASE_VOLTAGE_COLOR = Pattern.compile("\\.sld-(\\w+)\\s*\\{\\s*--sld-vl-color:\\s*(#\\w+)\\s*}");
    /** One core is left to the FX thread. */
    private static final int TILE_THREADS = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);

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

    @FXML
    private Hyperlink checkAllBaseVoltagesLink;

    @FXML
    private Hyperlink checkNoBaseVoltagesLink;

    /**
     * Builds the {@link MapNetworkData} off the FX thread. Restarting it on each refresh cancels a build still
     * running, whose result is then dropped.
     */
    private final Service<MapNetworkData> networkDataService = new Service<>() {
        @Override
        protected Task<MapNetworkData> createTask() {
            Network network = mainModel.getNetwork();
            buildingNetwork = network;
            return new Task<>() {
                @Override
                protected MapNetworkData call() {
                    return MapNetworkData.build(network, MapController.this::baseVoltageName, MapController.this::color);
                }
            };
        }
    };

    private final ExecutorService tileExecutor = Executors.newFixedThreadPool(TILE_THREADS, runnable -> {
        Thread thread = new Thread(runnable, "map-tile");
        thread.setDaemon(true);
        return thread;
    });

    /** Tiles requested by map.js and not delivered yet, by map.js' tile id. FX thread only. */
    private final Map<Integer, Future<?>> pendingTiles = new HashMap<>();

    // What tiles are drawn from, read on the FX thread when a tile is requested and handed to its task
    private MapNetworkData networkData;
    // the network networkData was built from, which the view map.js reports is saved for
    private Network displayedNetwork;
    // the network the running networkDataService build is for: restarting it drops the result of a previous build
    private Network buildingNetwork;
    private Set<String> hiddenBaseVoltages = Set.of();

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

        String html = HTML_SHELL.formatted(readResource("leaflet.css"), readResource("leaflet.js"), readResource("map.js"));
        webView.getEngine().getLoadWorker().stateProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == Worker.State.SUCCEEDED) {
                jsWindow = (JSObject) webView.getEngine().executeScript("window");
                jsWindow.setMember("controller", this);
                engineLoaded = true;
                jsWindow.call("addNetworkLayer");
                applyBasemap();
                invalidateMapSize();
                refresh();
            }
        });
        networkDataService.setOnSucceeded(event -> {
            networkData = networkDataService.getValue();
            displayedNetwork = buildingNetwork;
            loadingPane.setVisible(false);
            MainModel.MapView mapView = displayedNetwork == null ? null : mainModel.getMapView(displayedNetwork);
            try {
                // passed as JS string arguments rather than spliced into a script, so they need no escaping
                jsWindow.call("renderNetwork", objectMapper.writeValueAsString(networkData.latLngBounds()),
                        objectMapper.writeValueAsString(mapView));
            } catch (JsonProcessingException e) {
                LOGGER.error(e.getMessage(), e);
            }
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

    @FXML
    private void onFitToNetwork() {
        if (engineLoaded) {
            webView.getEngine().executeScript("fitToNetwork()");
        }
    }

    // map.js redraws the network tiles, which draw the offline basemap or not
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
        hiddenBaseVoltages = Set.copyOf(mainModel.getMapHiddenBaseVoltages());
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
            checkBox.setGraphic(new Rectangle(10, 10, Color.web(color(baseVoltage.getName(), MapNetworkData.DEFAULT_SUBSTATION_COLOR))));
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

    private void applyHiddenBaseVoltages() {
        hiddenBaseVoltages = Set.copyOf(mainModel.getMapHiddenBaseVoltages());
        if (engineLoaded) {
            webView.getEngine().executeScript("redrawNetwork()");
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
     * Called from map.js for each network tile Leaflet needs: draws it on a tile thread, then hands it back to
     * map.js' {@code onTileRendered}, as a PNG data URL, or an empty string for a fully transparent tile.
     */
    @SuppressWarnings("unused") // called from map.js
    public void requestTile(int id, int zoom, int x, int y, double ratio) {
        MapNetworkData data = networkData;
        boolean showCountries = basemapComboBox.getValue() == Basemap.OFFLINE;
        Set<String> hidden = hiddenBaseVoltages;
        pendingTiles.put(id, tileExecutor.submit(() -> {
            String url = "";
            try {
                url = renderTile(data, showCountries, hidden, zoom, x, y, ratio);
            } finally {
                // even if drawing failed, or Leaflet would wait for the tile forever
                deliverTile(id, url);
            }
        }));
    }

    private static String renderTile(MapNetworkData data, boolean showCountries, Set<String> hidden, int zoom, int x, int y, double ratio) {
        try {
            byte[] png = MapTileRenderer.render(data, showCountries, hidden, zoom, x, y, ratio);
            return png == null ? "" : "data:image/png;base64," + Base64.getEncoder().encodeToString(png);
        } catch (IOException e) {
            LOGGER.error(e.getMessage(), e);
            return "";
        }
    }

    // runs after requestTile returns, so after it registered the tile as pending
    private void deliverTile(int id, String url) {
        Platform.runLater(() -> {
            if (pendingTiles.remove(id) != null) {
                jsWindow.call("onTileRendered", id, url);
            }
        });
    }

    /**
     * Called from map.js when Leaflet drops a tile it requested, e.g. zoomed past before it was drawn.
     */
    @SuppressWarnings("unused") // called from map.js
    public void cancelTile(int id) {
        Future<?> tile = pendingTiles.remove(id);
        if (tile != null) {
            tile.cancel(false);
        }
    }

    /**
     * Called from map.js as the mouse moves: the JSON of the {@link MapNetworkData.Hit} under it, or null.
     */
    @SuppressWarnings("unused") // called from map.js
    public String hitTest(double latitude, double longitude, double zoom) {
        MapNetworkData.Hit hit = networkData == null ? null : networkData.hitTest(latitude, longitude, zoom, hiddenBaseVoltages);
        try {
            return hit == null ? null : objectMapper.writeValueAsString(hit);
        } catch (JsonProcessingException e) {
            LOGGER.error(e.getMessage(), e);
            return null;
        }
    }

    @SuppressWarnings("unused") // called from map.js
    public void openLink(String url) {
        try {
            Desktop.getDesktop().browse(new URI(url));
        } catch (IOException | URISyntaxException e) {
            LOGGER.warn("Could not open link: {}", url, e);
        }
    }

    /**
     * Called from map.js whenever the user moved the view, so that it's restored when navigating back to this view.
     */
    @SuppressWarnings("unused") // called from map.js
    public void onViewChanged(double latitude, double longitude, double zoom) {
        if (displayedNetwork != null) {
            mainModel.setMapView(displayedNetwork, new MainModel.MapView(latitude, longitude, zoom));
        }
    }

    @SuppressWarnings("unused") // called from map.js
    public void onMapClick(double latitude, double longitude, double zoom) {
        MapNetworkData.Hit hit = networkData == null ? null : networkData.hitTest(latitude, longitude, zoom, hiddenBaseVoltages);
        Network network = mainModel.getNetwork();
        if (hit == null || network == null) {
            return;
        }
        Identifiable<?> identifiable = network.getIdentifiable(hit.id());
        if (identifiable instanceof Substation substation) {
            mainModel.addNavigationEvent(NavigationEvent.create(NavigationType.SUBSTATIONS, ContainerNavigationState.create(substation)));
        } else if (identifiable instanceof Line line) {
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
        tileExecutor.shutdownNow();
        pendingTiles.clear();
        super.dispose();
    }

    private void refresh() {
        if (!engineLoaded) {
            return;
        }
        loadingPane.setVisible(true);
        networkDataService.restart();
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
}
