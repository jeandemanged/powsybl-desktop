/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network;

import com.google.common.io.ByteStreams;
import com.powsybl.iidm.network.*;
import com.powsybl.powsybldesktop.MainModel;
import com.powsybl.powsybldesktop.diagram.DiagramPaneController;
import com.powsybl.powsybldesktop.navigation.*;
import com.powsybl.powsybldesktop.network.search.NetworkSearch;
import com.powsybl.powsybldesktop.network.search.SearchBoxController;
import com.powsybl.powsybldesktop.network.tables.BoundaryLinesController;
import com.powsybl.powsybldesktop.network.tables.BusbarSectionsController;
import com.powsybl.powsybldesktop.network.tables.BusesBusBreakerViewController;
import com.powsybl.powsybldesktop.network.tables.BusesBusViewController;
import com.powsybl.powsybldesktop.network.tables.EmbeddableEquipmentTable;
import com.powsybl.powsybldesktop.network.tables.GeneratorsController;
import com.powsybl.powsybldesktop.network.tables.LinesController;
import com.powsybl.powsybldesktop.network.tables.LoadsController;
import com.powsybl.powsybldesktop.network.tables.ShuntCompensatorsController;
import com.powsybl.powsybldesktop.network.tables.StaticVarCompensatorsController;
import com.powsybl.powsybldesktop.network.tables.SwitchesController;
import com.powsybl.powsybldesktop.network.tables.TieLinesController;
import com.powsybl.powsybldesktop.network.tables.TransformersController;
import com.powsybl.powsybldesktop.utils.AbstractDisposableController;
import com.powsybl.powsybldesktop.utils.Labels;
import com.powsybl.powsybldesktop.utils.Messages;
import com.powsybl.powsybldesktop.utils.TreeItems;
import com.powsybl.sld.svg.GraphMetadata;
import javafx.fxml.FXML;
import javafx.geometry.Orientation;
import javafx.scene.control.*;
import netscape.javascript.JSObject;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class SubstationsController extends AbstractDisposableController {

    private static final Logger LOGGER = LoggerFactory.getLogger(SubstationsController.class);

    @FXML
    private TreeView<Object> substationsTreeView;
    @FXML
    private MenuButton groupingMenuButton;
    @FXML
    private Menu groupByAreaMenu;
    @FXML
    private SearchBoxController searchBoxController;
    @FXML
    private TabPane diagramTabPane;
    @FXML
    private Tab singleLineDiagramTab;
    @FXML
    private Tab areaDiagramTab;
    @FXML
    private DiagramPaneController sldPaneController;
    @FXML
    private DiagramPaneController nadPaneController;
    private Slider nadDepthSlider;
    // last depth actually rendered with; the slider only re-renders when this changes, since it fires
    // continuously while dragging and re-rendering the area diagram is expensive
    private int nadDepth = 1;

    // one tab per equipment table embedded in this view (see EquipmentTab), each shown only while the
    // selected container has at least one row for it - everything but the two diagram tabs above
    @FXML
    private Tab switchesTab;
    @FXML
    private Tab generatorsTab;
    @FXML
    private Tab shuntCompensatorsTab;
    @FXML
    private Tab staticVarCompensatorsTab;
    @FXML
    private Tab loadsTab;
    @FXML
    private Tab linesTab;
    @FXML
    private Tab transformersTab;
    @FXML
    private Tab tieLinesTab;
    @FXML
    private Tab boundaryLinesTab;
    @FXML
    private Tab busbarSectionsTab;
    @FXML
    private Tab busesBusViewTab;
    @FXML
    private Tab busesBusBreakerViewTab;
    @FXML
    private SwitchesController switchesEmbeddedController;
    @FXML
    private GeneratorsController generatorsEmbeddedController;
    @FXML
    private ShuntCompensatorsController shuntCompensatorsEmbeddedController;
    @FXML
    private StaticVarCompensatorsController staticVarCompensatorsEmbeddedController;
    @FXML
    private LoadsController loadsEmbeddedController;
    @FXML
    private LinesController linesEmbeddedController;
    @FXML
    private TransformersController transformersEmbeddedController;
    @FXML
    private TieLinesController tieLinesEmbeddedController;
    @FXML
    private BoundaryLinesController boundaryLinesEmbeddedController;
    @FXML
    private BusbarSectionsController busbarSectionsEmbeddedController;
    @FXML
    private BusesBusViewController busesBusViewEmbeddedController;
    @FXML
    private BusesBusBreakerViewController busesBusBreakerViewEmbeddedController;

    /**
     * One embedded equipment table tab: which {@link ContainerNavigationState.ContainerTab} it is, its
     * {@link Tab}, and its controller (used to filter it to the selected container and to decide whether
     * it currently has rows to show).
     */
    private record EquipmentTab(ContainerNavigationState.ContainerTab kind, Tab tab, EmbeddableEquipmentTable controller) {
    }

    private List<EquipmentTab> equipmentTabs;

    private boolean programmaticSelection;
    private GroupingMode groupingMode;
    // only meaningful when groupingMode == AREA: which of the network's area types to group by
    private String selectedAreaType;
    // metadata for the currently displayed single line diagram, used to map an SVG node id back to
    // equipment (see onSwitchClick/onFeederTopBottomClick, called from sld.js)
    private GraphMetadata sldMetadata;
    // the tree selection, kept regardless of which tab is active
    private Container<?> currentContainer;
    // area diagram is rendered lazily (only while its tab is showing): set whenever the selection or
    // depth changes, cleared once renderAreaDiagram() has run
    private boolean nadStale;

    /**
     * Top-level tree grouping criterion, selectable via {@link #groupingMenuButton}.
     */
    private enum GroupingMode {
        SUBNETWORK, COUNTRY, AREA;

        @Override
        public String toString() {
            return switch (this) {
                case SUBNETWORK -> Messages.get("substations.grouping.subnetwork");
                case COUNTRY -> Messages.get("substations.grouping.country");
                case AREA -> Messages.get("substations.grouping.area");
            };
        }
    }

    /**
     * A tree node that groups containers instead of representing one (a subnetwork/country, or the
     * "no subnetwork"/"no country"/"no substation" placeholder group, rendered greyed via {@code placeholder}).
     */
    private record GroupKey(String label, boolean placeholder) {
    }

    private static final Comparator<GroupKey> GROUP_KEY_COMPARATOR =
            Comparator.comparing(GroupKey::placeholder).thenComparing(GroupKey::label, String.CASE_INSENSITIVE_ORDER);

    // click-to-navigate JS bridge (window.controller, see initialize()); the area diagram has no
    // interactivity yet, so its pane keeps DiagramPaneController's default (non-interactive) shell
    private static final String SLD_HTML_SHELL = """
                <html>
                    <script type="text/javascript">%__JS__%</script>
                    <style>
                        .sld-top-feeder,
                        .sld-bottom-feeder,
                        .sld-load-break-switch,
                        .sld-breaker,
                        .sld-disconnector {
                            cursor: pointer;
                        }
                    </style>
                    <body style='margin: 0'>
                        <div id="svgContainer"></div>
                    </body>
                </html>
            """;

    private String js;

    private MainModel mainModel;

    public void setMainModel(MainModel mainModel) {
        this.mainModel = Objects.requireNonNull(mainModel);
        // area diagram depth is remembered across navigation/views (see MainModel), like the single line
        // diagram's zoom/fit-to-screen below
        nadDepth = mainModel.getDiagramAreaDepth();
        nadDepthSlider.setValue(nadDepth);
        refreshAreaTypesMenu(mainModel.getNetwork());
        setGroupingMode(defaultGroupingMode(mainModel.getNetwork()));
        listenerManager.listen(mainModel.networkProperty(), (observable, oldNetwork, newNetwork) -> {
            refreshAreaTypesMenu(newNetwork);
            setGroupingMode(defaultGroupingMode(newNetwork));
        });
        searchBoxController.bind(mainModel, this::onSearchMatch);

        // registered before our own updateProperty listener below, so that a topology change (switch open/
        // close) refreshes each embedded table's full equipment list (dropping now-invalidated bus-view
        // buses) before update() re-filters them to the selected container - JavaFX fires listeners on the
        // same property in registration order, and update()'s setContainer() call would otherwise filter
        // stale, possibly-invalidated data
        equipmentTabs.forEach(equipmentTab -> equipmentTab.controller().setMainModel(mainModel));
        listenerManager.listen(mainModel.updateProperty(), (observable, oldValue, newValue) -> this.update());

        sldPaneController.loadShell(SLD_HTML_SHELL.replace("%__JS__%", js));
        // the single line diagram's zoom/fit-to-screen state is remembered across navigation/views (see
        // MainModel); the area diagram pane keeps its own default (1.0/not fitted), reset each session
        sldPaneController.zoomProperty().addListener((observable, oldValue, newValue) -> mainModel.setDiagramZoom(newValue.doubleValue()));
        sldPaneController.fitToScreenProperty().addListener((observable, oldValue, newValue) -> mainModel.setDiagramFitToScreen(newValue));
        sldPaneController.restoreZoom(mainModel.getDiagramZoom(), mainModel.isDiagramFitToScreen());
    }

    private static GroupingMode defaultGroupingMode(Network network) {
        return network != null && !network.getSubnetworks().isEmpty() ? GroupingMode.SUBNETWORK : GroupingMode.COUNTRY;
    }

    private void setGroupingMode(GroupingMode mode) {
        setGroupingMode(mode, null);
    }

    private void setGroupingMode(GroupingMode mode, String areaType) {
        this.groupingMode = mode;
        this.selectedAreaType = areaType;
        String label = mode == GroupingMode.AREA && areaType != null ? mode + " (" + areaType + ")" : mode.toString();
        groupingMenuButton.setText(label);
        updateSubstationTree();
    }

    @FXML
    private void onGroupByCountry() {
        setGroupingMode(GroupingMode.COUNTRY);
    }

    @FXML
    private void onGroupBySubnetwork() {
        setGroupingMode(GroupingMode.SUBNETWORK);
    }

    private void onGroupByArea(String areaType) {
        setGroupingMode(GroupingMode.AREA, areaType);
    }

    // area types are per-network, so the submenu is rebuilt whenever the selected network changes
    private void refreshAreaTypesMenu(Network network) {
        groupByAreaMenu.getItems().clear();
        List<String> areaTypes = network == null ? List.of() : network.getAreaTypeStream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
        areaTypes.forEach(areaType -> {
            MenuItem item = new MenuItem(areaType);
            item.setOnAction(event -> onGroupByArea(areaType));
            groupByAreaMenu.getItems().add(item);
        });
        groupByAreaMenu.setDisable(areaTypes.isEmpty());
    }

    private void updateSubstationTree() {
        Network network = mainModel.getNetwork();
        TreeItem<Object> rootItem = new TreeItem<>(network);
        if (network != null) {
            buildTopLevelGroups(network, groupingMode).forEach(rootItem.getChildren()::add);
        }
        substationsTreeView.setRoot(rootItem);
    }

    private List<TreeItem<Object>> buildTopLevelGroups(Network network, GroupingMode mode) {
        if (mode == GroupingMode.AREA) {
            return buildAreaGroups(network);
        }

        Map<Object, List<Substation>> substationsByGroup = new HashMap<>();
        network.getSubstationStream().forEach(substation ->
                substationsByGroup.computeIfAbsent(rawGroupKeyFor(substation, mode, network), k -> new ArrayList<>()).add(substation));

        // VoltageLevel-s without substation, grouped by the same criterion, then under a "no substation" placeholder
        Map<Object, List<VoltageLevel>> orphanVlsByGroup = new HashMap<>();
        network.getVoltageLevelStream().filter(vl -> vl.getSubstation().isEmpty()).forEach(vl ->
                orphanVlsByGroup.computeIfAbsent(rawGroupKeyFor(vl, mode, network), k -> new ArrayList<>()).add(vl));

        Set<Object> rawKeys = new HashSet<>(substationsByGroup.keySet());
        rawKeys.addAll(orphanVlsByGroup.keySet());

        // one label per distinct group rather than one per substation/voltage level: a label can be
        // expensive to compute (formatSubnetworkLabel scans the subnetwork's substations for its countries)
        Map<Object, GroupKey> labelsByRawKey = new HashMap<>();
        rawKeys.forEach(rawKey -> labelsByRawKey.put(rawKey, groupKeyLabel(rawKey, mode)));

        // a single top-level group is expanded by default; several are collapsed, so the tree isn't
        // overwhelming on a network with many countries/subnetworks
        boolean expandGroups = rawKeys.size() <= 1;

        return rawKeys.stream()
                .sorted(Comparator.comparing(labelsByRawKey::get, GROUP_KEY_COMPARATOR))
                .map(rawKey -> buildGroupItem(labelsByRawKey.get(rawKey),
                        substationsByGroup.getOrDefault(rawKey, List.of()), orphanVlsByGroup.getOrDefault(rawKey, List.of()), expandGroups))
                .toList();
    }

    /**
     * Only voltage levels are (optionally) linked to an area of a given type, not substations directly, so a
     * substation is grouped under every area that at least one of its voltage levels belongs to - it can
     * therefore legitimately appear under several areas. Substations/voltage levels linked to none of the
     * network's areas of {@link #selectedAreaType} fall under a "no area" placeholder, like the other modes.
     */
    private List<TreeItem<Object>> buildAreaGroups(Network network) {
        if (selectedAreaType == null) {
            return List.of();
        }

        Map<Area, Set<Substation>> substationsByArea = new HashMap<>();
        Set<Substation> substationsInAnArea = new HashSet<>();
        Map<Area, List<VoltageLevel>> orphanVlsByArea = new HashMap<>();
        List<VoltageLevel> orphanVlsWithNoArea = new ArrayList<>();

        network.getVoltageLevelStream().forEach(vl -> {
            List<Area> areas = vl.getAreasStream().filter(area -> selectedAreaType.equals(area.getAreaType())).toList();
            Optional<Substation> substation = vl.getSubstation();
            if (substation.isPresent()) {
                if (!areas.isEmpty()) {
                    substationsInAnArea.add(substation.get());
                    areas.forEach(area -> substationsByArea.computeIfAbsent(area, a -> new HashSet<>()).add(substation.get()));
                }
            } else if (areas.isEmpty()) {
                orphanVlsWithNoArea.add(vl);
            } else {
                areas.forEach(area -> orphanVlsByArea.computeIfAbsent(area, a -> new ArrayList<>()).add(vl));
            }
        });

        List<Substation> substationsWithNoArea = network.getSubstationStream()
                .filter(s -> !substationsInAnArea.contains(s))
                .toList();

        Set<Area> areas = new HashSet<>(substationsByArea.keySet());
        areas.addAll(orphanVlsByArea.keySet());

        // one label per distinct area rather than one per substation/voltage level, same reasoning as groupKeyLabel()
        Map<Area, GroupKey> labelsByArea = new HashMap<>();
        areas.forEach(area -> labelsByArea.put(area, new GroupKey(formatAreaLabel(area), false)));

        boolean hasNoAreaGroup = !substationsWithNoArea.isEmpty() || !orphanVlsWithNoArea.isEmpty();
        boolean expandGroups = areas.size() + (hasNoAreaGroup ? 1 : 0) <= 1;

        List<TreeItem<Object>> groups = new ArrayList<>(areas.stream()
                .sorted(Comparator.comparing(labelsByArea::get, GROUP_KEY_COMPARATOR))
                .map(area -> buildGroupItem(labelsByArea.get(area), new ArrayList<>(substationsByArea.getOrDefault(area, Set.of())),
                        orphanVlsByArea.getOrDefault(area, List.of()), expandGroups))
                .toList());
        if (hasNoAreaGroup) {
            groups.add(buildGroupItem(new GroupKey(Messages.get("substations.tree.noArea"), true),
                    substationsWithNoArea, orphanVlsWithNoArea, expandGroups));
        }
        return groups;
    }

    private TreeItem<Object> buildGroupItem(GroupKey key, List<Substation> substations, List<VoltageLevel> orphanVls, boolean expanded) {
        TreeItem<Object> groupItem = new TreeItem<>(key);
        groupItem.setExpanded(expanded);
        substations.stream()
                .sorted(Comparator.comparing(Identifiable::getNameOrId))
                .forEach(substation -> groupItem.getChildren().add(buildSubstationItem(substation)));
        if (!orphanVls.isEmpty()) {
            TreeItem<Object> noSubstationItem = new TreeItem<>(new GroupKey(Messages.get("substations.tree.noSubstation"), true));
            noSubstationItem.setExpanded(true);
            orphanVls.stream()
                    .sorted(Comparator.comparing(Identifiable::getNameOrId))
                    .forEach(vl -> noSubstationItem.getChildren().add(new TreeItem<>(vl)));
            groupItem.getChildren().add(noSubstationItem);
        }
        return groupItem;
    }

    private TreeItem<Object> buildSubstationItem(Substation substation) {
        TreeItem<Object> substationItem = new TreeItem<>(substation);
        substationItem.setExpanded(true);
        substation.getVoltageLevelStream().sorted(Comparator.comparing(VoltageLevel::getNominalV).reversed())
                .forEach(vl -> substationItem.getChildren().add(new TreeItem<>(vl)));
        return substationItem;
    }

    // cheap per-item key (a subnetwork reference or a Country, null meaning "no group"); see groupKeyLabel()
    // for the (possibly expensive) label, computed once per distinct key rather than once per item.
    // Not used for AREA, which is handled separately by buildAreaGroups() since one item can map to several groups.
    private Object rawGroupKeyFor(Identifiable<?> item, GroupingMode mode, Network network) {
        return switch (mode) {
            case SUBNETWORK -> {
                Network parent = item.getParentNetwork();
                yield parent == network ? null : parent;
            }
            case COUNTRY -> item instanceof Substation substation ? substation.getCountry().orElse(null) : null;
            case AREA -> throw new IllegalStateException("AREA grouping is handled by buildAreaGroups()");
        };
    }

    private GroupKey groupKeyLabel(Object rawKey, GroupingMode mode) {
        if (rawKey == null) {
            return switch (mode) {
                case SUBNETWORK -> new GroupKey(Messages.get("substations.tree.noSubnetwork"), true);
                case COUNTRY -> new GroupKey(Messages.get("substations.tree.noCountry"), true);
                case AREA -> throw new IllegalStateException("AREA grouping is handled by buildAreaGroups()");
            };
        }
        return switch (mode) {
            case SUBNETWORK -> new GroupKey(formatSubnetworkLabel((Network) rawKey), false);
            case COUNTRY -> new GroupKey(((Country) rawKey).name(), false);
            case AREA -> throw new IllegalStateException("AREA grouping is handled by buildAreaGroups()");
        };
    }

    private static final int TREE_COUNTRIES_MAX_LISTED = 5;

    // Mirrors NetworksController's subnetwork tree label (name/id + its countries), for consistency across views
    private static String formatSubnetworkLabel(Network subnetwork) {
        String countries = formatCountriesForTree(subnetwork.getCountries());
        return subnetwork.getNameOrId() + (countries.isEmpty() ? "" : " (" + countries + ")");
    }

    // an Area has no getCountries() of its own: derive it from its voltage levels' substations
    private static String formatAreaLabel(Area area) {
        Set<Country> countries = area.getVoltageLevelStream()
                .map(VoltageLevel::getSubstation)
                .flatMap(Optional::stream)
                .map(Substation::getCountry)
                .flatMap(Optional::stream)
                .collect(Collectors.toSet());
        String countriesText = formatCountriesForTree(countries);
        String label = Labels.truncateForTree(area.getNameOrId());
        return countriesText.isEmpty() ? label : label + " (" + countriesText + ")";
    }

    private static String formatCountriesForTree(Set<Country> countries) {
        if (countries.size() > TREE_COUNTRIES_MAX_LISTED) {
            return Messages.get("networks.tree.countriesCount", countries.size());
        }
        return countries.stream().map(Object::toString).sorted().collect(Collectors.joining(", "));
    }

    @FXML
    private void initialize() throws IOException {
        js = new String(ByteStreams.toByteArray(Objects.requireNonNull(getClass().getResourceAsStream("sld.js"))));
        sldPaneController.setOnEngineLoaded(() -> {
            JSObject window = (JSObject) sldPaneController.getWebView().getEngine().executeScript("window");
            window.setMember("controller", this);
        });
        sldPaneController.setDiagramFileNameSupplier(this::selectedContainerName);
        nadPaneController.setDiagramFileNameSupplier(this::selectedContainerName);

        initializeAreaDiagramDepthControl();
        initializeDiagramParametersControls();

        equipmentTabs = List.of(
                new EquipmentTab(ContainerNavigationState.ContainerTab.SWITCHES, switchesTab, switchesEmbeddedController),
                new EquipmentTab(ContainerNavigationState.ContainerTab.GENERATORS, generatorsTab, generatorsEmbeddedController),
                new EquipmentTab(ContainerNavigationState.ContainerTab.SHUNT_COMPENSATORS, shuntCompensatorsTab, shuntCompensatorsEmbeddedController),
                new EquipmentTab(ContainerNavigationState.ContainerTab.STATIC_VAR_COMPENSATORS, staticVarCompensatorsTab, staticVarCompensatorsEmbeddedController),
                new EquipmentTab(ContainerNavigationState.ContainerTab.LOADS, loadsTab, loadsEmbeddedController),
                new EquipmentTab(ContainerNavigationState.ContainerTab.LINES, linesTab, linesEmbeddedController),
                new EquipmentTab(ContainerNavigationState.ContainerTab.TRANSFORMERS, transformersTab, transformersEmbeddedController),
                new EquipmentTab(ContainerNavigationState.ContainerTab.TIE_LINES, tieLinesTab, tieLinesEmbeddedController),
                new EquipmentTab(ContainerNavigationState.ContainerTab.BOUNDARY_LINES, boundaryLinesTab, boundaryLinesEmbeddedController),
                new EquipmentTab(ContainerNavigationState.ContainerTab.BUSBAR_SECTIONS, busbarSectionsTab, busbarSectionsEmbeddedController),
                new EquipmentTab(ContainerNavigationState.ContainerTab.BUSES_BUS_VIEW, busesBusViewTab, busesBusViewEmbeddedController),
                new EquipmentTab(ContainerNavigationState.ContainerTab.BUSES_BUS_BREAKER_VIEW, busesBusBreakerViewTab, busesBusBreakerViewEmbeddedController));

        diagramTabPane.getSelectionModel().selectedItemProperty().addListener((observable, oldTab, newTab) -> {
            if (newTab == areaDiagramTab && nadStale) {
                renderAreaDiagram();
            }
        });

        initializeTreeViews();
        initializeListeners();
    }

    // the area diagram tab has one toolbar control the single line diagram tab doesn't: a depth
    // slider. diagram-pane.fxml only declares the controls common to both, so this one is appended
    // to the area diagram pane's toolbar instead.
    private void initializeAreaDiagramDepthControl() {
        nadDepthSlider = new Slider(1, 6, 1);
        nadDepthSlider.setBlockIncrement(1);
        nadDepthSlider.setMajorTickUnit(1);
        nadDepthSlider.setMinorTickCount(0);
        nadDepthSlider.setSnapToTicks(true);
        nadDepthSlider.setShowTickLabels(true);
        nadDepthSlider.setPrefWidth(120);
        nadDepthSlider.setTooltip(new Tooltip(Messages.get("substations.diagram.depth")));
        nadDepthSlider.valueProperty().addListener((observable, oldValue, newValue) -> {
            int depth = newValue.intValue();
            if (depth != nadDepth) {
                nadDepth = depth;
                mainModel.setDiagramAreaDepth(depth);
                renderAreaDiagram();
            }
        });
        nadPaneController.getToolBar().getItems().addAll(
                new Separator(Orientation.VERTICAL), new Label(Messages.get("substations.diagram.depth")), nadDepthSlider);
    }

    // one parameters button per diagram type, since SLD and NAD parameters are unrelated PowSyBl objects
    private void initializeDiagramParametersControls() {
        Button sldParametersButton = new Button();
        sldParametersButton.setGraphic(new FontIcon("mdi2c-cog"));
        sldParametersButton.setTooltip(new Tooltip(Messages.get("substations.diagram.sldParameters.tooltip")));
        sldParametersButton.setOnAction(event -> SldParametersDialog.show(
                sldPaneController.getToolBar().getScene().getWindow(), mainModel, this::update));
        sldPaneController.getToolBar().getItems().addAll(new Separator(Orientation.VERTICAL), sldParametersButton);

        Button nadParametersButton = new Button();
        nadParametersButton.setGraphic(new FontIcon("mdi2c-cog"));
        nadParametersButton.setTooltip(new Tooltip(Messages.get("substations.diagram.nadParameters.tooltip")));
        nadParametersButton.setOnAction(event -> NadParametersDialog.show(
                nadPaneController.getToolBar().getScene().getWindow(), mainModel, this::renderAreaDiagram));
        nadPaneController.getToolBar().getItems().addAll(new Separator(Orientation.VERTICAL), nadParametersButton);
    }

    // name of the substation/voltage level currently selected in the tree, used as the default export filename
    private String selectedContainerName() {
        TreeItem<Object> selectedItem = substationsTreeView.getSelectionModel().getSelectedItem();
        return selectedItem != null && selectedItem.getValue() instanceof Identifiable<?> identifiable
                ? identifiable.getNameOrId() : null;
    }

    @FXML
    private void onExpandAll() {
        expandRootChildren(true);
    }

    @FXML
    private void onCollapseAll() {
        expandRootChildren(false);
    }

    // root itself must stay expanded: showRoot="false" means collapsing it would hide all its children too
    private void expandRootChildren(boolean expanded) {
        TreeItem<Object> root = substationsTreeView.getRoot();
        if (root != null) {
            root.getChildren().forEach(child -> setExpandedRecursively(child, expanded));
        }
    }

    private static void setExpandedRecursively(TreeItem<?> item, boolean expanded) {
        item.setExpanded(expanded);
        item.getChildren().forEach(child -> setExpandedRecursively(child, expanded));
    }

    @Override
    public void dispose() {
        searchBoxController.dispose();
        equipmentTabs.forEach(equipmentTab -> equipmentTab.controller().dispose());
        super.dispose();
    }

    /**
     * Reveals the container for a match from the search box: the match itself if it's a substation/voltage level,
     * or its voltage level if it's a generator (the substations tree doesn't display generators as separate rows).
     */
    private void onSearchMatch(Identifiable<?> match) {
        selectContainerInTree(NetworkSearch.containerOf(match));
    }

    @SuppressWarnings("unused") // used by JS
    public void onFeederTopBottomClick(String id) {
        GraphMetadata.NodeMetadata node = sldMetadata.getNodeMetadata(id);
        if (Objects.nonNull(node)) {
            // the click only ever happens from the single line diagram tab, but this state isn't
            // guaranteed to already be in history (e.g. reached via search, which doesn't push one) -
            // record it explicitly so navigating back from the equipment tab below lands back on it
            mainModel.addNavigationEvent(NavigationEvent.create(NavigationType.SUBSTATIONS,
                    ContainerNavigationState.create(currentContainer, selectedTab())), false);
            if (Objects.nonNull(node.getNextVId())) {
                mainModel.addNavigationEvent(NavigationEvent.create(NavigationType.SUBSTATIONS,
                        ContainerNavigationState.create(this.mainModel.getNetwork().getNetwork().getVoltageLevel(node.getNextVId()))));
            }
            update();
            Identifiable<?> equipment = this.mainModel.getNetwork().getNetwork().getIdentifiable(node.getEquipmentId());
            if (equipment instanceof Generator generator) {
                selectTab(ContainerNavigationState.ContainerTab.GENERATORS);
                generatorsEmbeddedController.goToGenerator(generator);
            } else if (equipment instanceof Load load) {
                selectTab(ContainerNavigationState.ContainerTab.LOADS);
                loadsEmbeddedController.goToLoad(load);
            } else if (equipment instanceof ShuntCompensator shunt) {
                selectTab(ContainerNavigationState.ContainerTab.SHUNT_COMPENSATORS);
                shuntCompensatorsEmbeddedController.goToShuntCompensator(shunt);
            } else if (equipment instanceof StaticVarCompensator svc) {
                selectTab(ContainerNavigationState.ContainerTab.STATIC_VAR_COMPENSATORS);
                staticVarCompensatorsEmbeddedController.goToStaticVarCompensator(svc);
            }
        } else {
            update();
        }
    }

    @SuppressWarnings("unused") // used by JS
    public void onSwitchClick(String id) {
        Objects.requireNonNull(id);
        GraphMetadata.NodeMetadata nodeMetadata = sldMetadata.getNodeMetadata(id);
        Objects.requireNonNull(nodeMetadata);
        String switchId = nodeMetadata.getEquipmentId();
        Switch aSwitch = mainModel.getNetwork().getSwitch(switchId);
        Objects.requireNonNull(aSwitch);
        aSwitch.setOpen(!aSwitch.isOpen());
        mainModel.setUpdate(aSwitch.getVoltageLevel());
        update();
    }

    private void initializeTreeViews() {

        substationsTreeView.setCellFactory(dummy -> new TreeCell<>() {
            @Override
            public void updateItem(Object item, boolean empty) {
                super.updateItem(item, empty);
                updateSubstationTreeCell(this, item, empty);
            }
        });
    }

    private static void updateSubstationTreeCell(TreeCell<Object> cell, Object item, boolean empty) {
        cell.getStyleClass().remove("substations-tree-group-placeholder");
        if (empty) {
            cell.setText(null);
        } else if (item instanceof GroupKey group) {
            cell.setText(group.label());
            if (group.placeholder()) {
                cell.getStyleClass().add("substations-tree-group-placeholder");
            }
        } else if (item instanceof VoltageLevel vl) {
            cell.setText(Labels.truncateForTree(vl.getNameOrId()) + " (" + formatNominalV(vl.getNominalV()) + " kV)");
        } else if (item instanceof Identifiable<?> identifiable) {
            cell.setText(Labels.truncateForTree(identifiable.getNameOrId()));
        } else {
            cell.setText(null);
        }
    }

    private static String formatNominalV(double nominalV) {
        if (nominalV == Math.rint(nominalV)) {
            return String.format(Locale.ROOT, "%.0f", nominalV);
        }
        return String.format(Locale.ROOT, "%.1f", nominalV);
    }

    private void initializeListeners() {
        // guarded by programmaticSelection so selection changes made by navigateTo() (main controller)
        // don't re-trigger a navigation event
        substationsTreeView.getSelectionModel().selectedItemProperty().addListener((observable, oldItem, newItem) -> {
            if (programmaticSelection) {
                return;
            }
            // always refresh first, even for a group node (country/subnetwork/area, or a "no X"
            // placeholder): update() is what clears the stale diagram, shows the "no selection" label,
            // and settles which tab ends up selected (an equipment tab open on the old container may not
            // exist on the new one) - selectedTab() below must reflect that, not the pre-switch tab
            update();
            if (newItem != null && newItem.getValue() instanceof Container<?> container) {
                mainModel.addNavigationEvent(NavigationEvent.create(NavigationType.SUBSTATIONS,
                        ContainerNavigationState.create(container, selectedTab())), false);
            }
        });
        // same guard: switching tabs is itself a navigable moment, but only once a container is actually
        // selected, to avoid polluting history with tab changes on an empty view
        diagramTabPane.getSelectionModel().selectedItemProperty().addListener((observable, oldTab, newTab) -> {
            if (programmaticSelection || currentContainer == null) {
                return;
            }
            mainModel.addNavigationEvent(NavigationEvent.create(NavigationType.SUBSTATIONS,
                    ContainerNavigationState.create(currentContainer, selectedTab())), false);
        });
    }

    private ContainerNavigationState.ContainerTab selectedTab() {
        Tab selected = diagramTabPane.getSelectionModel().getSelectedItem();
        if (selected == areaDiagramTab) {
            return ContainerNavigationState.ContainerTab.AREA;
        }
        if (selected == singleLineDiagramTab) {
            return ContainerNavigationState.ContainerTab.SINGLE_LINE;
        }
        return equipmentTabs.stream().filter(equipmentTab -> equipmentTab.tab() == selected)
                .map(EquipmentTab::kind).findFirst().orElse(ContainerNavigationState.ContainerTab.SINGLE_LINE);
    }

    private Tab tabFor(ContainerNavigationState.ContainerTab kind) {
        return switch (kind) {
            case SINGLE_LINE -> singleLineDiagramTab;
            case AREA -> areaDiagramTab;
            default -> equipmentTabs.stream().filter(equipmentTab -> equipmentTab.kind() == kind)
                    .map(EquipmentTab::tab).findFirst().orElse(singleLineDiagramTab);
        };
    }

    // falls back to the single line diagram tab if the target tab isn't currently shown (e.g. restoring
    // history to a container that no longer has that equipment)
    private void selectTab(ContainerNavigationState.ContainerTab kind) {
        Tab tab = tabFor(kind);
        diagramTabPane.getSelectionModel().select(diagramTabPane.getTabs().contains(tab) ? tab : singleLineDiagramTab);
    }

    private void update() {
        var selectedItem = substationsTreeView.getSelectionModel().getSelectedItem();
        Container<?> container = selectedItem != null && selectedItem.getValue() instanceof Container<?> c
                && (c instanceof VoltageLevel || c instanceof Substation) ? c : null;
        currentContainer = container;
        if (container != null) {
            try {
                SubstationDiagramRenderer.DiagramRender render = SubstationDiagramRenderer.render(container, mainModel.sldParametersProperty().getValue());
                sldMetadata = render.metadata();
                sldPaneController.showDiagram(render.svg());
            } catch (IOException e) {
                LOGGER.error(e.getMessage(), e);
            }
        } else {
            // nothing selected, or a group node (country/subnetwork/area, or one of their "no X" placeholders)
            sldMetadata = null;
            sldPaneController.showNoSelection();
        }
        updateAreaDiagram();
        updateEquipmentTabs(container);
    }

    // rendered lazily: only while the area diagram tab is showing, since it's a separate (possibly
    // costly) computation from the single line diagram shown by default
    private void updateAreaDiagram() {
        nadStale = true;
        if (diagramTabPane.getSelectionModel().getSelectedItem() == areaDiagramTab) {
            renderAreaDiagram();
        }
    }

    private void renderAreaDiagram() {
        if (currentContainer == null) {
            nadPaneController.showNoSelection();
            return;
        }
        try {
            nadPaneController.showDiagram(NetworkAreaDiagramRenderer.render(currentContainer, nadDepth, mainModel.nadParametersProperty().getValue()));
            nadStale = false;
        } catch (IOException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    // filters every embedded equipment table to the selected container, then shows only the tabs (among
    // the two diagrams, always shown, and the equipment tables) that currently have something to show,
    // preserving the previously selected tab when it's still one of them
    private void updateEquipmentTabs(Container<?> container) {
        equipmentTabs.forEach(equipmentTab -> equipmentTab.controller().setContainer(container));

        ContainerNavigationState.ContainerTab previouslySelected = selectedTab();
        programmaticSelection = true;
        try {
            diagramTabPane.getTabs().setAll(Stream.concat(Stream.of(singleLineDiagramTab, areaDiagramTab),
                    equipmentTabs.stream().filter(equipmentTab -> equipmentTab.controller().hasRows()).map(EquipmentTab::tab)).toList());
            selectTab(previouslySelected);
        } finally {
            programmaticSelection = false;
        }
    }

    public void navigateTo(Container<?> container, ContainerNavigationState.ContainerTab tab) {
        navigateTo(container);
        programmaticSelection = true;
        try {
            selectTab(tab);
        } finally {
            programmaticSelection = false;
        }
    }

    public void navigateTo(Container<?> container) {
        if (Objects.isNull(container)) {
            programmaticSelection = true;
            try {
                this.substationsTreeView.getSelectionModel().select(null);
                update();
            } finally {
                programmaticSelection = false;
            }
            return;
        }
        selectContainerInTree(container);
    }

    /**
     * Selects the tree item for the given container without pushing a navigation event
     * (used both by navigateTo() and by the search previous/next navigation).
     */
    private void selectContainerInTree(Container<?> container) {
        programmaticSelection = true;
        try {
            String containerId = container.getId();
            TreeItem<Object> found = TreeItems.find(this.substationsTreeView.getRoot(),
                    value -> value instanceof Container<?> c && Objects.equals(c.getId(), containerId));
            if (found != null) {
                this.substationsTreeView.getSelectionModel().select(found);
                this.substationsTreeView.scrollTo(this.substationsTreeView.getSelectionModel().getSelectedIndex());
                update();
            }
        } finally {
            programmaticSelection = false;
        }
    }
}
