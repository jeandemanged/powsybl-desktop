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
import com.powsybl.iidm.network.BoundaryLine;
import com.powsybl.iidm.network.Container;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Line;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Substation;
import com.powsybl.iidm.network.Terminal;
import com.powsybl.iidm.network.TieLine;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.iidm.network.extensions.Coordinate;
import com.powsybl.iidm.network.extensions.LinePosition;
import com.powsybl.iidm.network.extensions.SubstationPosition;
import com.powsybl.powsybldesktop.AbstractNetworkTask;
import com.powsybl.powsybldesktop.MainModel;
import com.powsybl.powsybldesktop.NetworkStudy;
import com.powsybl.powsybldesktop.SceneModel;
import com.powsybl.powsybldesktop.SceneView;
import com.powsybl.powsybldesktop.diagram.BaseVoltages;
import com.powsybl.powsybldesktop.network.ContainerDiagram;
import com.powsybl.powsybldesktop.network.detail.EquipmentDetails;
import com.powsybl.powsybldesktop.network.edit.ContainerCreations;
import com.powsybl.powsybldesktop.network.edit.EditMenus;
import com.powsybl.powsybldesktop.network.edit.NetworkEdits;
import com.powsybl.powsybldesktop.network.edit.PositionEdits;
import com.powsybl.powsybldesktop.network.search.NetworkSearch;
import com.powsybl.powsybldesktop.network.search.SearchBoxController;
import com.powsybl.powsybldesktop.parameters.GuiParameters;
import com.powsybl.powsybldesktop.utils.AbstractDisposableController;
import com.powsybl.powsybldesktop.utils.Messages;
import com.powsybl.powsybldesktop.window.SeparateWindows;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.concurrent.Worker;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import javafx.util.StringConverter;
import netscape.javascript.JSObject;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

/**
 * Shows substations and lines on a basemap, using the coordinates carried by the IIDM
 * {@link SubstationPosition}/{@link LinePosition} network extensions - a substation without position isn't drawn, a
 * line without route is drawn thin and translucent straight between its substations if both have one. A line
 * disconnected on at least one side is dashed. Clicking a substation marker opens its single line diagram, clicking a
 * line, tie line or boundary line opens its detail view, as a tab of the latest separate window created (see
 * {@link SeparateWindows#openInLatestWindow}).
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
 * Substations and lines are colored by base voltage (see {@link BaseVoltages}). Each base voltage can be hidden
 * from an overlay checkbox. The basemap and hidden base voltages are {@link GuiParameters}, also editable from the
 * parameters view.
 * <p>
 * The search box zooms to its current match: a line, tie line or boundary line to fit it whole, anything else to its
 * substation, with some neighbouring substations around it, and marks it: a line halfway along, anything else at its
 * substation. A match with nothing drawn to zoom to leaves the view as it is, unmarked.
 * <p>
 * Positions are edited here too (see {@link PositionEdits}): substations without position are listed, and placed by
 * a click on the map, one after the other; the context menu moves a substation or edits a line's route, both drawn
 * by map.js over the network tiles until applied (or cancelled), and gives lines straight routes.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class MapController extends AbstractDisposableController implements SceneView {

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
                        /* a black ring outlined in white, visible on any basemap and voltage color */
                        .search-highlight {
                            position: absolute;
                            left: 50%%;
                            top: 50%%;
                            width: 100%%;
                            height: 100%%;
                            transform: translate(-50%%, -50%%);
                            box-sizing: border-box;
                            border: 3px solid #000;
                            border-radius: 50%%;
                            box-shadow: 0 0 0 2px #fff, inset 0 0 0 2px #fff;
                        }
                    </style>
                    <script>%s</script>
                </head>
                <body>
                    <div id="map"></div>
                    <script>%s</script>
                </body>
            </html>
            """;

    /** One core is left to the FX thread. */
    private static final int TILE_THREADS = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);

    public enum Basemap {
        OFFLINE("offline", "map.basemap.offline"),
        OPEN_STREET_MAP("osm", "map.basemap.openStreetMap");

        private final String jsName;
        private final String labelKey;

        Basemap(String jsName, String labelKey) {
            this.jsName = jsName;
            this.labelKey = labelKey;
        }

        public String getLabel() {
            return Messages.get(labelKey);
        }
    }

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final BaseVoltages baseVoltages = new BaseVoltages();
    private final MapBaseVoltages mapBaseVoltages = new MapBaseVoltages(baseVoltages);
    private MapBaseVoltages.CheckBoxes baseVoltageCheckBoxes;

    @FXML
    private WebView webView;
    private final ContextMenu contextMenu = new ContextMenu();
    private double contextMenuX;
    private double contextMenuY;

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

    @FXML
    private SearchBoxController searchBoxController;

    @FXML
    private TitledPane positionsPane;

    @FXML
    private TextField unplacedFilterField;

    @FXML
    private ListView<MapNetworkData.UnplacedSubstation> unplacedListView;

    @FXML
    private Label placementHintLabel;

    @FXML
    private Hyperlink straightRoutesLink;

    @FXML
    private Node editBar;

    @FXML
    private Label editTitleLabel;

    @FXML
    private Label editHintLabel;

    @FXML
    private Button editStraightButton;

    private final ObservableList<MapNetworkData.UnplacedSubstation> unplacedSubstations = FXCollections.observableArrayList();
    private final FilteredList<MapNetworkData.UnplacedSubstation> filteredUnplacedSubstations = new FilteredList<>(unplacedSubstations);
    // the substation selected in the list is placed by a click on the map
    private boolean placing;
    // where in the list the substation just placed was: the one taking its place is selected next, once the list shows it
    private int nextPlacement = -1;
    // what is being edited on the map: a substation, a line or a boundary line (a tie line half), null for nothing
    private Identifiable<?> edited;
    private Network editedNetwork;

    /**
     * Builds the {@link MapNetworkData} off the FX thread. Restarting it on each refresh cancels a build still
     * running, whose result is then dropped.
     */
    private final Service<MapNetworkData> networkDataService = new Service<>() {
        @Override
        protected Task<MapNetworkData> createTask() {
            Network network = network();
            buildingNetwork = network;
            return new AbstractNetworkTask<>(mainModel, network) {
                @Override
                protected MapNetworkData compute() {
                    return MapNetworkData.build(network, baseVoltages::name, baseVoltages::color);
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

    private SceneModel sceneModel;
    private MainModel mainModel;
    private boolean engineLoaded;
    private JSObject jsWindow;

    @FXML
    private void initialize() {
        webView.setContextMenuEnabled(false);
        webView.addEventFilter(MouseEvent.MOUSE_PRESSED, _ -> contextMenu.hide());
        webView.addEventHandler(ContextMenuEvent.CONTEXT_MENU_REQUESTED, this::onContextMenuRequested);
        basemapComboBox.getItems().setAll(Basemap.values());
        basemapComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(Basemap basemap) {
                return basemap == null ? "" : basemap.getLabel();
            }

            @Override
            public Basemap fromString(String string) {
                return null;
            }
        });
        basemapUnreachableLabel.managedProperty().bind(basemapUnreachableLabel.visibleProperty());
        webView.addEventFilter(KeyEvent.KEY_PRESSED, this::onKeyPressed);
        initializePositions();

        String html = HTML_SHELL.formatted(readResource("leaflet.css"), readResource("leaflet.js"), readResource("map.js"));
        webView.getEngine().getLoadWorker().stateProperty().addListener((_, _, newValue) -> {
            if (newValue == Worker.State.SUCCEEDED) {
                jsWindow = (JSObject) webView.getEngine().executeScript("window");
                jsWindow.setMember("controller", this);
                engineLoaded = true;
                jsWindow.call("addNetworkLayer");
                jsWindow.call("addFitToNetworkControl", Messages.get("map.fitToNetwork"));
                applyBasemap();
                invalidateMapSize();
                refresh();
            }
        });
        networkDataService.setOnSucceeded(_ -> {
            networkData = networkDataService.getValue();
            displayedNetwork = buildingNetwork;
            loadingPane.setVisible(false);
            showPositions();
            NetworkStudy.MapView mapView = displayedNetwork == null ? null : mainModel.getStudy(displayedNetwork).getMapView(displayedNetwork);
            try {
                // passed as JS string arguments rather than spliced into a script, so they need no escaping
                jsWindow.call("renderNetwork", objectMapper.writeValueAsString(networkData.latLngBounds()),
                        objectMapper.writeValueAsString(mapView));
            } catch (JsonProcessingException e) {
                LOGGER.error(e.getMessage(), e);
            }
        });
        networkDataService.setOnFailed(_ -> {
            LOGGER.error(networkDataService.getException().getMessage(), networkDataService.getException());
            loadingPane.setVisible(false);
        });
        webView.getEngine().loadContent(html);

        // Leaflet computes its container size once, at L.map() init time - which happens as soon as the
        // WebView finishes loading, well before this AnchorPane has been laid out to its final size in the
        // BorderPane center (it's still at the FXML's placeholder prefWidth/prefHeight then). Without this,
        // tiles/markers past that stale boundary are positioned using outdated pixel math (visible as
        // misplaced tile blocks and markers rendered outside the map viewport).
        webView.widthProperty().addListener((_, _, _) -> invalidateMapSize());
        webView.heightProperty().addListener((_, _, _) -> invalidateMapSize());
    }

    private void invalidateMapSize() {
        if (engineLoaded) {
            webView.getEngine().executeScript("if (window.map) { window.map.invalidateSize({animate: false, pan: false}); }");
        }
    }

    // map.js redraws the network tiles, which draw the offline basemap or not
    private void applyBasemap() {
        basemapUnreachableLabel.setVisible(false);
        if (engineLoaded) {
            webView.getEngine().executeScript("setBasemap('" + basemapComboBox.getValue().jsName + "')");
        }
    }

    @Override
    public void setSceneModel(SceneModel sceneModel) {
        this.sceneModel = Objects.requireNonNull(sceneModel);
        this.mainModel = sceneModel.getMainModel();
        hiddenBaseVoltages = gui().getMapHiddenBaseVoltages();
        basemapComboBox.setValue(gui().getMapBasemap());
        basemapComboBox.valueProperty().addListener((_, _, newValue) -> {
            if (gui().getMapBasemap() != newValue) {
                gui().setMapBasemap(newValue);
                mainModel.getParametersModel().guiParametersChanged();
            }
            applyBasemap();
        });
        listenerManager.listen(sceneModel.networkProperty(), (_, oldValue, newValue) -> {
            if (root(newValue) != root(oldValue)) {
                stopEdit();
                unplacedSubstations.clear();
                refresh();
            }
        });
        listenerManager.listen(mainModel.updateProperty(), (_, _, _) -> refresh());
        createBaseVoltageCheckBoxes();
        listenerManager.listen(mainModel.getParametersModel().guiParametersRevisionProperty(), (_, _, _) -> applyGuiParameters());
        searchBoxController.bind(sceneModel, this::onSearchMatch);
        searchBoxController.setOnNoMatch(this::clearSearchHighlight);
    }

    /**
     * Where to zoom for a search match, and where to put its highlight markers.
     *
     * @param bounds south, west, north and east
     * @param highlights latitude and longitude of each marker
     */
    private record SearchFocus(double[] bounds, List<double[]> highlights) {
    }

    // the selected network's root: the map shows a network with all its subnetworks
    private Network network() {
        return root(sceneModel.getNetwork());
    }

    private static Network root(Network network) {
        return network == null ? null : network.getNetwork();
    }

    private void onSearchMatch(Identifiable<?> match) {
        // not drawn yet, or still drawn from the previous network
        if (networkData == null || displayedNetwork != network()) {
            return;
        }
        SearchFocus focus = searchFocus(match);
        if (focus == null) {
            // a marker left on the previous match would read as this one's
            clearSearchHighlight();
            return;
        }
        try {
            jsWindow.call("zoomTo", objectMapper.writeValueAsString(focus.bounds()));
            jsWindow.call("highlight", objectMapper.writeValueAsString(focus.highlights()));
        } catch (JsonProcessingException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    private void clearSearchHighlight() {
        if (engineLoaded) {
            jsWindow.call("highlight", "[]");
        }
    }

    private SearchFocus searchFocus(Identifiable<?> match) {
        // a boundary line paired into a tie line is drawn as that tie line
        Identifiable<?> shown = match instanceof BoundaryLine boundaryLine && boundaryLine.getTieLine().isPresent()
                ? boundaryLine.getTieLine().get() : match;
        List<Terminal> terminals;
        if (shown instanceof Line line) {
            terminals = List.of(line.getTerminal1(), line.getTerminal2());
        } else if (shown instanceof TieLine tieLine) {
            terminals = List.of(tieLine.getTerminal1(), tieLine.getTerminal2());
        } else if (shown instanceof BoundaryLine boundaryLine) {
            terminals = List.of(boundaryLine.getTerminal());
        } else {
            Container<?> container = NetworkSearch.containerOf(shown);
            return substationFocus(container instanceof VoltageLevel voltageLevel
                    ? voltageLevel.getSubstation().orElse(null)
                    : (Substation) container);
        }
        double[] bounds = networkData.lineLatLngBounds(shown.getId());
        if (bounds != null) {
            return new SearchFocus(bounds, List.of(networkData.lineMiddleLatLng(shown.getId())));
        }
        // no position of its own: from one end's substation to the other's, both marked
        List<Substation> substations = terminals.stream()
                .map(terminal -> terminal.getVoltageLevel().getSubstation())
                .flatMap(Optional::stream)
                .toList();
        bounds = networkData.substationsLatLngBounds(substations.stream().map(Substation::getId).collect(Collectors.toSet()));
        if (bounds != null && (bounds[0] != bounds[2] || bounds[1] != bounds[3])) {
            return new SearchFocus(bounds, substations.stream()
                    .map(substation -> networkData.substationLatLng(substation.getId()))
                    .filter(Objects::nonNull)
                    .toList());
        }
        return substations.stream().map(this::substationFocus).filter(Objects::nonNull).findFirst().orElse(null);
    }

    private SearchFocus substationFocus(Substation substation) {
        double[] bounds = substation == null ? null : networkData.substationNeighbourhoodLatLngBounds(substation.getId());
        return bounds == null ? null : new SearchFocus(bounds, List.of(networkData.substationLatLng(substation.getId())));
    }

    private GuiParameters gui() {
        return mainModel.getParametersModel().getGuiParameters();
    }

    // the network is redrawn by applyGuiParameters, once the change has gone through the GUI parameters
    private void createBaseVoltageCheckBoxes() {
        baseVoltageCheckBoxes = mapBaseVoltages.createCheckBoxes(hidden -> {
            if (!hidden.equals(gui().getMapHiddenBaseVoltages())) {
                gui().setMapHiddenBaseVoltages(hidden);
                mainModel.getParametersModel().guiParametersChanged();
            }
        });
        baseVoltageCheckBoxes.setHidden(hiddenBaseVoltages);
        checkAllBaseVoltagesLink.setOnAction(_ -> baseVoltageCheckBoxes.setAllSelected(true));
        checkNoBaseVoltagesLink.setOnAction(_ -> baseVoltageCheckBoxes.setAllSelected(false));
        baseVoltagesBox.getChildren().addAll(baseVoltageCheckBoxes.getCheckBoxes());
    }

    // edited from this view or the parameters view, or replaced by a parameters reset or import
    private void applyGuiParameters() {
        basemapComboBox.setValue(gui().getMapBasemap());
        baseVoltageCheckBoxes.setHidden(gui().getMapHiddenBaseVoltages());
        if (!hiddenBaseVoltages.equals(gui().getMapHiddenBaseVoltages())) {
            hiddenBaseVoltages = gui().getMapHiddenBaseVoltages();
            if (engineLoaded) {
                webView.getEngine().executeScript("redrawNetwork()");
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
     * Called from map.js for each network tile Leaflet needs: draws it on a tile thread, then hands it back to
     * map.js' {@code onTileRendered}, as a PNG data URL, or an empty string for a fully transparent tile.
     */
    @SuppressWarnings("unused") // called from map.js
    public void requestTile(int id, int zoom, int x, int y, double ratio) {
        // the WebView outlives dispose() and Leaflet keeps requesting tiles from it
        if (tileExecutor.isShutdown()) {
            return;
        }
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
            mainModel.getStudy(displayedNetwork).setMapView(displayedNetwork, new NetworkStudy.MapView(latitude, longitude, zoom));
        }
    }

    @SuppressWarnings("unused") // called from map.js
    public void onMapClick(double latitude, double longitude, double zoom) {
        MapNetworkData.Hit hit = networkData == null ? null : networkData.hitTest(latitude, longitude, zoom, hiddenBaseVoltages);
        Network network = network();
        if (hit == null || network == null) {
            return;
        }
        Identifiable<?> identifiable = network.getIdentifiable(hit.id());
        if (identifiable instanceof Substation substation) {
            sceneModel.getDetailWindows().ifPresent(windows ->
                    ContainerDiagram.open(windows, true, ContainerDiagram.Kind.SINGLE_LINE, substation));
        } else {
            EquipmentDetails.open(sceneModel, identifiable);
        }
    }

    // the clicked substation's and its voltage levels' diagrams, then creation in it, or of a substation at the clicked place
    @SuppressWarnings("unused") // called from map.js
    public void onMapContextMenu(double latitude, double longitude, double zoom) {
        Network network = network();
        if (network == null) {
            return;
        }
        MapNetworkData.Hit hit = networkData == null ? null : networkData.hitTest(latitude, longitude, zoom, hiddenBaseVoltages);
        Identifiable<?> identifiable = hit == null ? null : network.getIdentifiable(hit.id());
        List<MenuItem> items = new ArrayList<>();
        if (identifiable instanceof Substation substation) {
            items.add(diagramItem(substation, substation.getNameOrId()));
            substation.getVoltageLevelStream()
                    .sorted(Comparator.comparingDouble(VoltageLevel::getNominalV).reversed())
                    .forEach(voltageLevel -> items.add(diagramItem(voltageLevel, voltageLevel.getNameOrId() + " ("
                            + gui().format(GuiParameters.Quantity.VOLTAGE, voltageLevel.getNominalV()) + " kV)")));
            items.add(new SeparatorMenuItem());
        }
        MenuItem newSubstation = new MenuItem(Messages.get("edit.menu.newSubstationHere"), new FontIcon("mdi2p-plus"));
        newSubstation.setDisable(mainModel.isBusy(network));
        newSubstation.setOnAction(_ -> ContainerCreations.newSubstation(sceneModel, webView.getScene().getWindow(), network,
                new Coordinate(latitude, longitude), MapTileRenderer.countryAt(latitude, longitude)));
        items.add(newSubstation);
        if (identifiable != null) {
            List<MenuItem> positions = positionItems(network, identifiable, hit.implied());
            if (!positions.isEmpty()) {
                items.add(new SeparatorMenuItem());
                items.addAll(positions);
            }
            List<MenuItem> edits = EditMenus.items(sceneModel, () -> webView.getScene().getWindow(), network, identifiable);
            if (!edits.isEmpty()) {
                items.add(new SeparatorMenuItem());
                items.addAll(edits);
            }
        }
        contextMenu.getItems().setAll(items);
        // not from within the JavaScript call
        Platform.runLater(() -> contextMenu.show(webView, contextMenuX, contextMenuY));
    }

    private MenuItem diagramItem(Container<?> container, String text) {
        MenuItem item = new MenuItem(Messages.get("map.contextMenu.singleLineDiagram", text));
        item.setMnemonicParsing(false);
        item.setOnAction(_ -> sceneModel.getDetailWindows().ifPresent(windows ->
                ContainerDiagram.open(windows, true, ContainerDiagram.Kind.SINGLE_LINE, container)));
        return item;
    }

    // the WebView, its own context menu disabled, doesn't dispatch contextmenu events to the page: map.js finds the
    // place under the pointer, given in CSS pixels of the viewport (the WebView's zoom scales them)
    private void onContextMenuRequested(ContextMenuEvent event) {
        if (engineLoaded) {
            contextMenuX = event.getScreenX();
            contextMenuY = event.getScreenY();
            double zoom = webView.getZoom();
            webView.getEngine().executeScript("mapContextMenuAt(" + event.getX() / zoom + ", " + event.getY() / zoom + ")");
        }
        event.consume();
    }

    private void initializePositions() {
        unplacedListView.setItems(filteredUnplacedSubstations);
        unplacedFilterField.textProperty().addListener((_, _, newValue) -> {
            String filter = newValue.trim().toLowerCase(Locale.ROOT);
            filteredUnplacedSubstations.setPredicate(substation -> filter.isEmpty()
                    || substation.text().toLowerCase(Locale.ROOT).contains(filter) || substation.id().toLowerCase(Locale.ROOT).contains(filter));
        });
        unplacedListView.getSelectionModel().selectedItemProperty().addListener((_, _, _) -> updatePlacement());
        positionsPane.expandedProperty().addListener((_, _, _) -> updatePlacement());
        positionsPane.managedProperty().bind(positionsPane.visibleProperty());
        positionsPane.setVisible(false);
        for (Node node : List.of(unplacedFilterField, unplacedListView, placementHintLabel, straightRoutesLink, editStraightButton)) {
            node.managedProperty().bind(node.visibleProperty());
        }
        updatePlacement();
    }

    // the unplaced substations and unrouted lines of the network just drawn, and the edit if what it edits is gone
    private void showPositions() {
        if (edited != null && (editedNetwork != displayedNetwork || editedNetwork.getIdentifiable(edited.getId()) != edited)) {
            stopEdit();
        }
        MapNetworkData.UnplacedSubstation selected = unplacedListView.getSelectionModel().getSelectedItem();
        unplacedSubstations.setAll(networkData.unplacedSubstations());
        int index = nextPlacement >= 0 ? Math.min(nextPlacement, filteredUnplacedSubstations.size() - 1)
                : selected == null ? -1 : filteredUnplacedSubstations.indexOf(selected);
        nextPlacement = -1;
        if (index >= 0) {
            unplacedListView.getSelectionModel().select(index);
            unplacedListView.scrollTo(index);
        }
        int unplaced = networkData.unplacedSubstations().size();
        int unrouted = networkData.unroutedCount();
        positionsPane.setVisible(unplaced > 0 || unrouted > 0);
        positionsPane.setText(Messages.get("map.position.title", unplaced, unrouted));
        unplacedFilterField.setVisible(unplaced > 0);
        unplacedListView.setVisible(unplaced > 0);
        placementHintLabel.setVisible(unplaced > 0);
        straightRoutesLink.setVisible(unrouted > 0);
        straightRoutesLink.setText(Messages.get("map.position.straightRoutes", unrouted));
    }

    // a substation selected in the open list is placed by the next click on the map, unless something is being edited
    private void updatePlacement() {
        MapNetworkData.UnplacedSubstation selected = unplacedListView.getSelectionModel().getSelectedItem();
        placing = selected != null && positionsPane.isExpanded() && edited == null;
        placementHintLabel.setText(placing ? Messages.get("map.position.placing", selected.text()) : Messages.get("map.position.placeHint"));
        if (engineLoaded) {
            jsWindow.call(placing ? "startPlacement" : "stopPlacement");
        }
    }

    /**
     * Called from map.js on a click while placing the substation selected in the list.
     */
    @SuppressWarnings("unused") // called from map.js
    public void onPlace(double latitude, double longitude) {
        // not from within the JavaScript call: the edit refreshes the map
        Platform.runLater(() -> {
            MapNetworkData.UnplacedSubstation selected = unplacedListView.getSelectionModel().getSelectedItem();
            Network network = network();
            Substation substation = selected == null || network == null ? null : network.getSubstation(selected.id());
            if (!placing || substation == null) {
                return;
            }
            int index = unplacedListView.getSelectionModel().getSelectedIndex();
            if (NetworkEdits.apply(mainModel, network, _ -> PositionEdits.setSubstationPosition(substation, new Coordinate(latitude, longitude)))) {
                // until the refreshed list shows the next one: a click meanwhile would move this one instead
                unplacedListView.getSelectionModel().clearSelection();
                nextPlacement = index;
            }
        });
    }

    @FXML
    private void createStraightRoutes() {
        Network network = network();
        if (network == null || networkData == null) {
            return;
        }
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.initOwner(webView.getScene().getWindow());
        alert.setTitle(Messages.get("edit.menu.straightRoute"));
        alert.setHeaderText(Messages.get("map.position.straightRoutes.confirm", networkData.unroutedCount()));
        if (alert.showAndWait().filter(ButtonType.OK::equals).isPresent()) {
            NetworkEdits.apply(mainModel, network, PositionEdits::createStraightRoutes);
        }
    }

    // moving a substation or editing a route on the map, and changing a position at once
    private List<MenuItem> positionItems(Network network, Identifiable<?> identifiable, boolean implied) {
        List<MenuItem> items = new ArrayList<>();
        switch (identifiable) {
            case Substation substation -> {
                items.add(menuItem("map.edit.moveSubstation", () -> startSubstationMove(network, substation)));
                items.add(menuItem("map.edit.removePosition", () -> NetworkEdits.apply(mainModel, network,
                        _ -> PositionEdits.removeSubstationPosition(substation))));
            }
            case TieLine tieLine -> {
                List<BoundaryLine> halves = List.of(tieLine.getBoundaryLine1(), tieLine.getBoundaryLine2());
                Menu editRoute = new Menu(Messages.get(implied ? "map.edit.defineRoute" : "map.edit.editRoute"));
                for (int i = 0; i < halves.size(); i++) {
                    BoundaryLine half = halves.get(i);
                    MenuItem item = new MenuItem(Messages.get("map.edit.routeSide", i + 1, half.getNameOrId()));
                    item.setOnAction(_ -> startRouteEdit(network, half));
                    editRoute.getItems().add(item);
                }
                items.add(editRoute);
                MenuItem straight = menuItem("edit.menu.straightRoute", () -> NetworkEdits.apply(mainModel, network,
                        _ -> halves.forEach(half -> PositionEdits.straightRoute(half).ifPresent(route -> PositionEdits.setRoute(half, route)))));
                straight.setDisable(PositionEdits.straightRoute(halves.getFirst()).isEmpty());
                items.add(straight);
                if (!implied) {
                    items.add(menuItem("map.edit.removeRoute", () -> NetworkEdits.apply(mainModel, network,
                            _ -> halves.forEach(half -> PositionEdits.setRoute(half, List.of())))));
                }
            }
            case Line line -> items.addAll(routeItems(network, line, implied));
            case BoundaryLine boundaryLine -> items.addAll(routeItems(network, boundaryLine, false));
            default -> {
                // no position
            }
        }
        if (mainModel.isBusy(network)) {
            items.forEach(item -> item.setDisable(true));
        }
        return items;
    }

    private List<MenuItem> routeItems(Network network, Identifiable<?> line, boolean implied) {
        List<MenuItem> items = new ArrayList<>();
        items.add(menuItem(implied ? "map.edit.defineRoute" : "map.edit.editRoute", () -> startRouteEdit(network, line)));
        List<Coordinate> straightRoute = PositionEdits.straightRoute(line).orElse(null);
        MenuItem straight = menuItem("edit.menu.straightRoute", () -> NetworkEdits.apply(mainModel, network,
                _ -> PositionEdits.setRoute(line, straightRoute)));
        straight.setDisable(straightRoute == null);
        items.add(straight);
        if (!implied) {
            items.add(menuItem("map.edit.removeRoute", () -> NetworkEdits.apply(mainModel, network, _ -> PositionEdits.setRoute(line, List.of()))));
        }
        return items;
    }

    private static MenuItem menuItem(String key, Runnable action) {
        MenuItem item = new MenuItem(Messages.get(key));
        item.setOnAction(_ -> action.run());
        return item;
    }

    private void startSubstationMove(Network network, Substation substation) {
        PositionEdits.substationCoordinate(substation).ifPresent(coordinate -> {
            startEdit(network, substation, Messages.get("map.edit.moving", substation.getNameOrId()));
            jsWindow.call("startSubstationMove", coordinate.getLatitude(), coordinate.getLongitude());
            onEditChanged(1);
        });
    }

    /**
     * Starts from its route, or else a straight one, or else, for a boundary line, its substation: the user clicks the
     * rest. Its ends snap to their substations.
     */
    private void startRouteEdit(Network network, Identifiable<?> line) {
        List<Coordinate> route = PositionEdits.route(line);
        List<VoltageLevel> ends = switch (line) {
            case Line branch -> List.of(branch.getTerminal1().getVoltageLevel(), branch.getTerminal2().getVoltageLevel());
            case BoundaryLine boundaryLine -> List.of(boundaryLine.getTerminal().getVoltageLevel());
            default -> List.of();
        };
        List<Coordinate> snaps = ends.stream().map(PositionEdits::substationCoordinate).flatMap(Optional::stream).toList();
        if (route.isEmpty()) {
            route = PositionEdits.straightRoute(line).orElse(line instanceof BoundaryLine ? snaps : List.of());
        }
        startEdit(network, line, Messages.get("map.edit.route", line.getNameOrId()));
        jsWindow.call("startRouteEdit", toJson(route), toJson(snaps));
        onEditChanged(route.size());
    }

    private void startEdit(Network network, Identifiable<?> identifiable, String title) {
        edited = identifiable;
        editedNetwork = network;
        updatePlacement();
        clearSearchHighlight();
        editTitleLabel.setText(title);
        editStraightButton.setVisible(!(identifiable instanceof Substation));
        editBar.setVisible(true);
    }

    /**
     * Called from map.js whenever the edited points changed, with how many there are now.
     */
    @SuppressWarnings("unused") // called from map.js
    public void onEditChanged(int points) {
        editHintLabel.setText(Messages.get(edited instanceof Substation ? "map.edit.moveHint"
                : points < 2 ? "map.edit.drawHint" : "map.edit.routeHint"));
    }

    @FXML
    private void applyEdit() {
        if (edited == null) {
            return;
        }
        Identifiable<?> target = edited;
        List<Coordinate> points;
        try {
            points = Arrays.stream(objectMapper.readValue((String) jsWindow.call("getEditPoints"), double[][].class))
                    .map(point -> new Coordinate(point[0], point[1]))
                    .toList();
        } catch (JsonProcessingException e) {
            LOGGER.error(e.getMessage(), e);
            return;
        }
        if (NetworkEdits.apply(mainModel, editedNetwork, _ -> {
            if (target instanceof Substation substation) {
                PositionEdits.setSubstationPosition(substation, points.getFirst());
            } else {
                PositionEdits.setRoute(target, points);
            }
        })) {
            stopEdit();
        }
    }

    @FXML
    private void cancelEdit() {
        stopEdit();
    }

    @FXML
    private void straightenEdit() {
        if (edited != null) {
            PositionEdits.straightRoute(edited).ifPresent(route -> jsWindow.call("setEditPoints", toJson(route)));
        }
    }

    private void stopEdit() {
        if (edited == null) {
            return;
        }
        edited = null;
        editedNetwork = null;
        editBar.setVisible(false);
        if (engineLoaded) {
            jsWindow.call("stopEdit");
        }
        updatePlacement();
    }

    /**
     * Called from map.js on a right-click while editing, with the index of the point under the pointer, -1 for none.
     */
    @SuppressWarnings("unused") // called from map.js
    public void onEditContextMenu(int point) {
        List<MenuItem> items = new ArrayList<>();
        if (point >= 0 && !(edited instanceof Substation)) {
            items.add(menuItem("map.edit.deletePoint", () -> jsWindow.call("deleteEditPoint", point)));
        }
        if (!(edited instanceof Substation)) {
            MenuItem straight = menuItem("edit.menu.straightRoute", this::straightenEdit);
            straight.setDisable(edited == null || PositionEdits.straightRoute(edited).isEmpty());
            items.add(straight);
        }
        items.add(new SeparatorMenuItem());
        items.add(menuItem("detail.action.apply", this::applyEdit));
        items.add(menuItem("desktop.common.action.cancel", this::cancelEdit));
        contextMenu.getItems().setAll(items);
        // not from within the JavaScript call
        Platform.runLater(() -> contextMenu.show(webView, contextMenuX, contextMenuY));
    }

    // Enter applies an edit, Escape cancels it, or stops placing substations
    private void onKeyPressed(KeyEvent event) {
        if (edited != null && event.getCode() == KeyCode.ENTER) {
            applyEdit();
            event.consume();
        } else if (edited != null && event.getCode() == KeyCode.ESCAPE) {
            stopEdit();
            event.consume();
        } else if (placing && event.getCode() == KeyCode.ESCAPE) {
            unplacedListView.getSelectionModel().clearSelection();
            event.consume();
        }
    }

    private String toJson(List<Coordinate> coordinates) {
        try {
            return objectMapper.writeValueAsString(coordinates.stream()
                    .map(coordinate -> new double[] {coordinate.getLatitude(), coordinate.getLongitude()})
                    .toList());
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void dispose() {
        searchBoxController.dispose();
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

    private String readResource(String name) {
        try (var stream = getClass().getResourceAsStream(name)) {
            return new String(Objects.requireNonNull(stream).readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
