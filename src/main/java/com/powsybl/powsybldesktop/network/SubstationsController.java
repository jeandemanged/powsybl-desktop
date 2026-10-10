/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network;

import com.powsybl.iidm.network.*;
import com.powsybl.powsybldesktop.MainModel;
import com.powsybl.powsybldesktop.SceneModel;
import com.powsybl.powsybldesktop.SceneView;
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
import com.powsybl.powsybldesktop.parameters.GuiParameters;
import com.powsybl.powsybldesktop.utils.AbstractDisposableController;
import com.powsybl.powsybldesktop.utils.Labels;
import com.powsybl.powsybldesktop.utils.Messages;
import com.powsybl.powsybldesktop.utils.TreeItems;
import com.powsybl.powsybldesktop.window.SeparateWindows;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class SubstationsController extends AbstractDisposableController implements SceneView {

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
    private ContainerDiagram singleLineDiagram;
    private ContainerDiagram areaDiagram;

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
    private record EquipmentTab(ContainerNavigationState.ContainerTab kind, Tab tab, EmbeddableEquipmentTable controller, String fxml) {
    }

    private List<EquipmentTab> equipmentTabs;

    private boolean programmaticSelection;
    private GroupingMode groupingMode;
    // only meaningful when groupingMode == AREA: which of the network's area types to group by
    private String selectedAreaType;
    // the tree selection, kept regardless of which tab is active
    private Container<?> currentContainer;

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

    private SceneModel sceneModel;
    private MainModel mainModel;

    @Override
    public void setSceneModel(SceneModel sceneModel) {
        this.sceneModel = Objects.requireNonNull(sceneModel);
        this.mainModel = sceneModel.getMainModel();
        refreshAreaTypesMenu(sceneModel.getNetwork());
        setGroupingMode(defaultGroupingMode(sceneModel.getNetwork()));
        listenerManager.listen(sceneModel.networkProperty(), (observable, oldNetwork, newNetwork) -> {
            refreshAreaTypesMenu(newNetwork);
            setGroupingMode(defaultGroupingMode(newNetwork));
        });
        searchBoxController.bind(mainModel, this::onSearchMatch);

        // registered before our own updateProperty listener below, so that a topology change (switch open/
        // close) refreshes each embedded table's full equipment list (dropping now-invalidated bus-view
        // buses) before update() re-filters them to the selected container - JavaFX fires listeners on the
        // same property in registration order, and update()'s setContainer() call would otherwise filter
        // stale, possibly-invalidated data
        equipmentTabs.forEach(equipmentTab -> equipmentTab.controller().setSceneModel(sceneModel));
        listenerManager.listen(mainModel.updateProperty(), (observable, oldValue, newValue) -> this.update());
        // the voltage levels' nominal voltage follows the decimals
        listenerManager.listen(mainModel.getParametersModel().guiParametersRevisionProperty(), (observable, oldValue, newValue) -> substationsTreeView.refresh());

        singleLineDiagram = new ContainerDiagram(ContainerDiagram.Kind.SINGLE_LINE, sldPaneController, sceneModel);
        areaDiagram = new ContainerDiagram(ContainerDiagram.Kind.AREA, nadPaneController, sceneModel);
        sceneModel.getSeparateWindows().ifPresent(separateWindows -> {
            SeparateWindows.addOpenButton(sldPaneController.getToolBar(), () -> openDiagram(separateWindows, ContainerDiagram.Kind.SINGLE_LINE));
            SeparateWindows.addOpenButton(nadPaneController.getToolBar(), () -> openDiagram(separateWindows, ContainerDiagram.Kind.AREA));
            equipmentTabs.forEach(equipmentTab -> SeparateWindows.addOpenButton((Parent) equipmentTab.tab().getContent(),
                    () -> openEquipmentTab(separateWindows, equipmentTab)));
        });
        updateDiagramsShowing();
        update();
    }

    private void openDiagram(SeparateWindows separateWindows, ContainerDiagram.Kind kind) {
        Container<?> container = currentContainer;
        if (container == null) {
            return;
        }
        String title = Messages.get(kind == ContainerDiagram.Kind.SINGLE_LINE ? "desktop.common.singleLineDiagram" : "desktop.common.networkAreaDiagram");
        separateWindows.open(kind + "/" + container.getId(), title + " - " + container.getNameOrId(), scene -> {
            FXMLLoader loader = new FXMLLoader(DiagramPaneController.class.getResource("diagram-pane.fxml"), Messages.bundle());
            try {
                Parent root = loader.load();
                ContainerDiagram diagram = new ContainerDiagram(kind, loader.getController(), scene);
                diagram.setContainer(container);
                diagram.setShowing(true);
                return new SeparateWindows.View(root, diagram);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    private void openEquipmentTab(SeparateWindows separateWindows, EquipmentTab equipmentTab) {
        Container<?> container = currentContainer;
        if (container == null) {
            return;
        }
        separateWindows.open(equipmentTab.kind() + "/" + container.getId(), equipmentTab.tab().getText() + " - " + container.getNameOrId(),
                scene -> SeparateWindows.<EmbeddableEquipmentTable>load(equipmentTab.fxml(), controller -> {
                    controller.setSceneModel(scene);
                    controller.setContainer(container);
                }));
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
        Network network = sceneModel.getNetwork();
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
    private void initialize() {
        equipmentTabs = List.of(
                new EquipmentTab(ContainerNavigationState.ContainerTab.SWITCHES, switchesTab, switchesEmbeddedController, "network/tables/switches-view.fxml"),
                new EquipmentTab(ContainerNavigationState.ContainerTab.GENERATORS, generatorsTab, generatorsEmbeddedController, "network/tables/generators-view.fxml"),
                new EquipmentTab(ContainerNavigationState.ContainerTab.SHUNT_COMPENSATORS, shuntCompensatorsTab, shuntCompensatorsEmbeddedController, "network/tables/shunt-compensators-view.fxml"),
                new EquipmentTab(ContainerNavigationState.ContainerTab.STATIC_VAR_COMPENSATORS, staticVarCompensatorsTab, staticVarCompensatorsEmbeddedController,
                        "network/tables/static-var-compensators-view.fxml"),
                new EquipmentTab(ContainerNavigationState.ContainerTab.LOADS, loadsTab, loadsEmbeddedController, "network/tables/loads-view.fxml"),
                new EquipmentTab(ContainerNavigationState.ContainerTab.LINES, linesTab, linesEmbeddedController, "network/tables/lines-view.fxml"),
                new EquipmentTab(ContainerNavigationState.ContainerTab.TRANSFORMERS, transformersTab, transformersEmbeddedController, "network/tables/transformers-view.fxml"),
                new EquipmentTab(ContainerNavigationState.ContainerTab.TIE_LINES, tieLinesTab, tieLinesEmbeddedController, "network/tables/tie-lines-view.fxml"),
                new EquipmentTab(ContainerNavigationState.ContainerTab.BOUNDARY_LINES, boundaryLinesTab, boundaryLinesEmbeddedController, "network/tables/boundary-lines-view.fxml"),
                new EquipmentTab(ContainerNavigationState.ContainerTab.BUSBAR_SECTIONS, busbarSectionsTab, busbarSectionsEmbeddedController, "network/tables/busbar-sections-view.fxml"),
                new EquipmentTab(ContainerNavigationState.ContainerTab.BUSES_BUS_VIEW, busesBusViewTab, busesBusViewEmbeddedController, "network/tables/buses-bus-view.fxml"),
                new EquipmentTab(ContainerNavigationState.ContainerTab.BUSES_BUS_BREAKER_VIEW, busesBusBreakerViewTab, busesBusBreakerViewEmbeddedController,
                        "network/tables/buses-bus-breaker-view.fxml"));

        diagramTabPane.getSelectionModel().selectedItemProperty().addListener((observable, oldTab, newTab) -> updateDiagramsShowing());

        initializeTreeViews();
        initializeListeners();
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
        if (singleLineDiagram != null) {
            singleLineDiagram.dispose();
            areaDiagram.dispose();
        }
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

    private void initializeTreeViews() {

        substationsTreeView.setCellFactory(dummy -> new TreeCell<>() {
            @Override
            public void updateItem(Object item, boolean empty) {
                super.updateItem(item, empty);
                updateSubstationTreeCell(this, item, empty);
            }
        });
    }

    private void updateSubstationTreeCell(TreeCell<Object> cell, Object item, boolean empty) {
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

    private String formatNominalV(double nominalV) {
        return mainModel.getParametersModel().getGuiParameters().format(GuiParameters.Quantity.VOLTAGE, nominalV);
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
                sceneModel.record(NavigationEvent.create(NavigationType.SUBSTATIONS,
                        ContainerNavigationState.create(container, selectedTab())));
            }
        });
        // same guard: switching tabs is itself a navigable moment, but only once a container is actually
        // selected, to avoid polluting history with tab changes on an empty view
        diagramTabPane.getSelectionModel().selectedItemProperty().addListener((observable, oldTab, newTab) -> {
            if (programmaticSelection || currentContainer == null) {
                return;
            }
            sceneModel.record(NavigationEvent.create(NavigationType.SUBSTATIONS,
                    ContainerNavigationState.create(currentContainer, selectedTab())));
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
        if (singleLineDiagram != null) {
            singleLineDiagram.setContainer(container);
            areaDiagram.setContainer(container);
        }
        updateEquipmentTabs(container);
    }

    // rendered lazily: only while their tab is showing, so that editing in an equipment tab doesn't keep the network
    // busy re-rendering a hidden diagram (see MainModel.markBusy)
    private void updateDiagramsShowing() {
        if (singleLineDiagram != null) {
            Tab selected = diagramTabPane.getSelectionModel().getSelectedItem();
            singleLineDiagram.setShowing(selected == singleLineDiagramTab);
            areaDiagram.setShowing(selected == areaDiagramTab);
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
        navigateTo(container, tab, null);
    }

    /**
     * Also selects {@code equipment}'s row in the {@code tab} equipment tab, if not null.
     */
    public void navigateTo(Container<?> container, ContainerNavigationState.ContainerTab tab, Identifiable<?> equipment) {
        navigateTo(container);
        programmaticSelection = true;
        try {
            selectTab(tab);
        } finally {
            programmaticSelection = false;
        }
        if (equipment != null) {
            equipmentTabs.stream().filter(equipmentTab -> equipmentTab.kind() == tab)
                    .forEach(equipmentTab -> equipmentTab.controller().goTo(equipment));
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
