/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network;

import com.powsybl.cgmes.conformity.Cgmes3Catalog;
import com.powsybl.cgmes.conformity.CgmesConformity1Catalog;
import com.powsybl.cgmes.conformity.CgmesConformity1NetworkCatalog;
import com.powsybl.cgmes.conformity.CgmesConformity2Catalog;
import com.powsybl.cgmes.conformity.CgmesConformity3Catalog;
import com.powsybl.cgmes.conformity.ReliCapGridCatalog;
import com.powsybl.cgmes.model.GridModelReference;
import com.powsybl.commons.datasource.DataSource;
import com.powsybl.commons.datasource.ReadOnlyDataSource;
import com.powsybl.commons.report.ReportNode;
import com.powsybl.ieeecdf.converter.IeeeCdfNetworkFactory;
import com.powsybl.iidm.network.BoundaryLine;
import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Exporter;
import com.powsybl.iidm.network.Exporters;
import com.powsybl.iidm.network.Importer;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.NetworkFactory;
import com.powsybl.iidm.network.test.BatteryNetworkFactory;
import com.powsybl.iidm.network.test.BoundaryLineNetworkFactory;
import com.powsybl.iidm.network.test.DcDetailedNetworkFactory;
import com.powsybl.iidm.network.test.EuropeanLvTestFeederFactory;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.iidm.network.test.FictitiousSwitchFactory;
import com.powsybl.iidm.network.test.FourSubstationsNodeBreakerFactory;
import com.powsybl.iidm.network.test.FourSubstationsNodeBreakerWithExtensionsFactory;
import com.powsybl.iidm.network.test.HvdcTestNetwork;
import com.powsybl.iidm.network.test.PhaseShifterTestCaseFactory;
import com.powsybl.iidm.network.test.ReactiveLimitsTestNetworkFactory;
import com.powsybl.iidm.network.test.ScadaNetworkFactory;
import com.powsybl.iidm.network.test.SecurityAnalysisTestNetworkFactory;
import com.powsybl.iidm.network.test.ShuntTestCaseFactory;
import com.powsybl.iidm.network.test.SvcTestCaseFactory;
import com.powsybl.iidm.network.test.ThreeWindingsTransformerNetworkFactory;
import com.powsybl.iidm.network.test.TwoVoltageLevelNetworkFactory;
import com.powsybl.iidm.serde.test.MetrixTutorialSixBusesFactory;
import com.powsybl.powsybldesktop.MainModel;
import com.powsybl.powsybldesktop.navigation.NavigationEvent;
import com.powsybl.powsybldesktop.navigation.NavigationType;
import com.powsybl.powsybldesktop.navigation.NetworkNavigationState;
import com.powsybl.powsybldesktop.navigation.ReportNavigationState;
import com.powsybl.powsybldesktop.notification.Notification;
import com.powsybl.powsybldesktop.notification.NotificationAction;
import com.powsybl.powsybldesktop.utils.AbstractDisposableController;
import com.powsybl.powsybldesktop.utils.Labels;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class NetworksController extends AbstractDisposableController {

    private static final Logger LOGGER = LoggerFactory.getLogger(NetworksController.class);

    private static final List<String> IIDM_FORMATS = List.of("XIIDM", "BIIDM", "JIIDM");

    @FXML
    private TreeView<Network> networksTreeView;

    @FXML
    private Label noNetworksLabel;

    @FXML
    private MenuButton importMenuButton;

    @FXML
    private MenuButton exportMenuButton;

    @FXML
    private Button closeButton;

    @FXML
    private Button closeAllButton;

    @FXML
    private Button detachButton;

    @FXML
    private Button mergeButton;

    @FXML
    private BorderPane networkDetailsPane;

    @FXML
    private StackPane noNetworkSelectedPane;

    @FXML
    private StackPane multipleNetworksSelectedPane;

    @FXML
    private Label idLabel;

    @FXML
    private TextField nameField;

    @FXML
    private Label sourceFormatLabel;

    @FXML
    private Label countriesCaptionLabel;

    @FXML
    private Label countriesLabel;

    @FXML
    private TableView<Pair<String, String>> networkInfoTable;

    @FXML
    private TableColumn<Pair<String, String>, String> propertyColumn;

    @FXML
    private TableColumn<Pair<String, String>, String> valueColumn;

    private MainModel mainModel;
    private Network selectedNetwork;

    public void setMainModel(MainModel mainModel) {
        this.mainModel = Objects.requireNonNull(mainModel);
        updateNetworkList();
        updateNetworkInfo(mainModel.getNetwork());
        updateSelectionState();
        listenerManager.listen(mainModel.getNetworks(), c -> updateNetworkList());
        listenerManager.listen(mainModel.networkProperty(), (obs, oldNetwork, newNetwork) -> updateNetworkInfo(newNetwork));
        listenerManager.listen(networksTreeView.getSelectionModel().getSelectedItems(), c -> updateSelectionState());
    }

    private List<Network> getSelectedNetworks() {
        return networksTreeView.getSelectionModel().getSelectedItems().stream()
                .map(TreeItem::getValue)
                .filter(Objects::nonNull)
                .toList();
    }

    private void updateSelectionState() {
        List<Network> selectedNetworks = getSelectedNetworks();
        boolean multipleSelected = selectedNetworks.size() > 1;
        multipleNetworksSelectedPane.setVisible(multipleSelected);
        importMenuButton.setDisable(multipleSelected);
        if (multipleSelected) {
            networkDetailsPane.setVisible(false);
            noNetworkSelectedPane.setVisible(false);
            exportMenuButton.setDisable(true);
            closeButton.setDisable(!canCloseAll(selectedNetworks));
        } else if (selectedNetworks.size() == 1) {
            Network network = selectedNetworks.getFirst();
            if (network != mainModel.getNetwork()) {
                mainModel.setNetwork(network);
                mainModel.addNavigationEvent(NavigationEvent.create(NavigationType.NETWORKS, NetworkNavigationState.create(network)), false);
            }
            updateNetworkInfo(network);
        }
        mergeButton.setDisable(!canMerge(selectedNetworks));
        detachButton.setDisable(!canDetach(selectedNetworks));
    }

    private boolean canMerge(List<Network> selectedNetworks) {
        return selectedNetworks.size() > 1
                && selectedNetworks.stream().allMatch(network -> isParentNetwork(network) && network.getSubnetworks().isEmpty());
    }

    private boolean canDetach(List<Network> selectedNetworks) {
        if (selectedNetworks.isEmpty()) {
            return false;
        }
        if (selectedNetworks.size() == 1) {
            Network network = selectedNetworks.getFirst();
            return !isParentNetwork(network) || !network.getSubnetworks().isEmpty();
        }
        return selectedNetworks.stream().noneMatch(this::isParentNetwork);
    }

    private boolean canCloseAll(List<Network> selectedNetworks) {
        return !selectedNetworks.isEmpty() && selectedNetworks.stream().allMatch(this::isParentNetwork);
    }

    private void commitNameChange() {
        if (selectedNetwork == null) {
            return;
        }
        String newName = nameField.getText();
        if (newName == null || newName.isBlank()) {
            return;
        }
        if (!newName.equals(selectedNetwork.getOptionalName().orElse(""))) {
            selectedNetwork.setName(newName);
            networksTreeView.refresh();
        }
    }

    private void updateNetworkInfo(Network network) {
        this.selectedNetwork = network;
        boolean closable = network != null && isParentNetwork(network);
        exportMenuButton.setDisable(!closable);
        closeButton.setDisable(!closable);
        networkDetailsPane.setVisible(network != null);
        noNetworkSelectedPane.setVisible(network == null);
        if (network == null) {
            nameField.setText("");
            sourceFormatLabel.setText("");
            countriesLabel.setText("");
            networkInfoTable.setItems(FXCollections.observableArrayList());
            return;
        }
        idLabel.setText(network.getId());
        nameField.setText(network.getOptionalName().orElse(""));
        sourceFormatLabel.setText(network.getSourceFormat());
        String countries = network.getCountries().stream().map(Object::toString).sorted().collect(Collectors.joining(", "));
        countriesLabel.setText(countries);
        countriesCaptionLabel.setVisible(!countries.isEmpty());
        countriesCaptionLabel.setManaged(!countries.isEmpty());
        countriesLabel.setVisible(!countries.isEmpty());
        countriesLabel.setManaged(!countries.isEmpty());

        ObservableList<Pair<String, String>> rows = FXCollections.observableArrayList();
        addBusCount(rows, Messages.get("networks.info.busesBusBreaker"),
                network.getVoltageLevelStream().flatMap(vl -> vl.getBusBreakerView().getBusStream()));
        addBusCount(rows, Messages.get("networks.info.busesBusBranch"),
                network.getVoltageLevelStream().flatMap(vl -> vl.getBusView().getBusStream()));
        addCount(rows, Messages.get("networks.info.subnetworks"), network.getSubnetworks().size());
        addCount(rows, Messages.get("networks.info.substations"), network.getSubstationCount());
        addCount(rows, Messages.get("networks.info.voltageLevels"), network.getVoltageLevelCount());
        addCount(rows, Messages.get("networks.info.lines"), network.getLineCount());
        addCount(rows, Messages.get("networks.info.tieLines"), NetworkTieLines.countOf(network));
        addCount(rows, Messages.get("networks.info.twoWindingsTransformers"), network.getTwoWindingsTransformerCount());
        addCount(rows, Messages.get("networks.info.threeWindingsTransformers"), network.getThreeWindingsTransformerCount());
        addCount(rows, Messages.get("networks.info.generators"), network.getGeneratorCount());
        addCount(rows, Messages.get("networks.info.batteries"), network.getBatteryCount());
        addCount(rows, Messages.get("networks.info.loads"), network.getLoadCount());
        addCount(rows, Messages.get("networks.info.shuntCompensators"), network.getShuntCompensatorCount());
        addCount(rows, Messages.get("networks.info.staticVarCompensators"), network.getStaticVarCompensatorCount());
        addCount(rows, Messages.get("networks.info.switches"), network.getSwitchCount());
        addCount(rows, Messages.get("networks.info.busbarSections"), network.getBusbarSectionCount());
        addBoundaryLineCount(rows, network);
        addCount(rows, Messages.get("networks.info.hvdcLines"), network.getHvdcLineCount());
        addCount(rows, Messages.get("networks.info.lccConverterStations"), network.getLccConverterStationCount());
        addCount(rows, Messages.get("networks.info.vscConverterStations"), network.getVscConverterStationCount());
        addCount(rows, Messages.get("networks.info.grounds"), network.getGroundCount());
        addCount(rows, Messages.get("networks.info.overloadManagementSystems"), network.getOverloadManagementSystemCount());
        networkInfoTable.setItems(rows);
    }

    private void addCount(ObservableList<Pair<String, String>> rows, String label, int count) {
        if (count > 0) {
            rows.add(new Pair<>(label, String.valueOf(count)));
        }
    }

    private void addBusCount(ObservableList<Pair<String, String>> rows, String label, Stream<Bus> busStream) {
        List<Bus> buses = busStream.toList();
        if (buses.isEmpty()) {
            return;
        }
        long inMainConnectedComponent = buses.stream().filter(Bus::isInMainConnectedComponent).count();
        rows.add(new Pair<>(label, Messages.get("networks.info.busCountDetail", buses.size(), inMainConnectedComponent)));
    }

    private void addBoundaryLineCount(ObservableList<Pair<String, String>> rows, Network network) {
        List<BoundaryLine> boundaryLines = network.getBoundaryLineStream().toList();
        if (boundaryLines.isEmpty()) {
            return;
        }
        long pairedCount = boundaryLines.stream().filter(BoundaryLine::isPaired).count();
        long tieLineCount = boundaryLines.stream().filter(BoundaryLine::isPaired)
                .map(boundaryLine -> boundaryLine.getTieLine().orElseThrow()).distinct().count();
        long unpairedCount = boundaryLines.size() - pairedCount;
        rows.add(new Pair<>(Messages.get("networks.info.boundaryLines"),
                Messages.get("networks.info.boundaryLineCountDetail", boundaryLines.size(), pairedCount, tieLineCount, unpairedCount)));
    }

    private void updateNetworkList() {
        Network currentNetwork = mainModel.getNetwork();
        if (currentNetwork == null && mainModel.getNetworks().size() == 1) {
            currentNetwork = mainModel.getNetworks().getFirst();
            mainModel.setNetwork(currentNetwork);
        }
        final Network treeSelectedNetwork = currentNetwork;
        final TreeItem[] selectedNetworkTreeItem = new TreeItem[]{null};
        Map<Network, Boolean> expandedStates = new IdentityHashMap<>();
        TreeItem<Network> previousRoot = networksTreeView.getRoot();
        if (previousRoot != null) {
            previousRoot.getChildren().forEach(item -> expandedStates.put((Network) item.getValue(), item.isExpanded()));
        }
        // dumb rebuild everything
        TreeItem<Network> rootItem = new TreeItem<>(null);
        mainModel.getNetworks().forEach(network -> {
            TreeItem<Network> item = new TreeItem<>(network);
            item.setExpanded(expandedStates.getOrDefault(network, true));
            if (network == treeSelectedNetwork) {
                selectedNetworkTreeItem[0] = item;
            }
            network.getSubnetworks().forEach(subnetwork -> {
                TreeItem<Network> snItem = new TreeItem<>(subnetwork);
                snItem.setExpanded(true);
                if (subnetwork == treeSelectedNetwork) {
                    selectedNetworkTreeItem[0] = snItem;
                }
                item.getChildren().add(snItem);
            });
            rootItem.getChildren().add(item);
        });
        networksTreeView.setRoot(rootItem);
        networksTreeView.getSelectionModel().select(selectedNetworkTreeItem[0]);
        noNetworksLabel.setVisible(mainModel.getNetworks().isEmpty());
        closeAllButton.setDisable(mainModel.getNetworks().isEmpty());
    }

    private static final int TREE_COUNTRIES_MAX_LISTED = 5;

    private static String formatCountriesForTree(Network network) {
        var countries = network.getCountries();
        if (countries.size() > TREE_COUNTRIES_MAX_LISTED) {
            return Messages.get("networks.tree.countriesCount", countries.size());
        }
        return countries.stream().map(Object::toString).sorted().collect(Collectors.joining(", "));
    }

    @FXML
    private void initialize() {
        networksTreeView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        networksTreeView.setCellFactory(dummy -> new TreeCell<>() {
            @Override
            public void updateItem(Network item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    String countries = formatCountriesForTree(item);
                    setText(Labels.truncateForTree(item.getNameOrId()) + (countries.isEmpty() ? "" : " (" + countries + ")"));
                }
            }
        });
        initializeImportMenu();
        initializeExportMenu();
        propertyColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getKey()));
        valueColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getValue()));
        nameField.setOnAction(event -> commitNameChange());
        nameField.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (Boolean.FALSE.equals(isFocused)) {
                commitNameChange();
            }
        });
    }

    private boolean isParentNetwork(Network network) {
        return mainModel.getNetworks().contains(network);
    }

    private void initializeImportMenu() {
        MenuItem iidmItem = new MenuItem("IIDM");
        iidmItem.setOnAction(event -> importNetwork(IIDM_FORMATS));
        importMenuButton.getItems().add(iidmItem);

        Importer.getFormats().stream()
                .filter(format -> !IIDM_FORMATS.contains(format))
                .sorted()
                .forEach(format -> {
                    MenuItem formatItem = new MenuItem(format);
                    formatItem.setOnAction(event -> importNetwork(List.of(format)));
                    importMenuButton.getItems().add(formatItem);
                });
        importMenuButton.getItems().add(new SeparatorMenuItem());

        Menu sampleNetworksMenu = new Menu(Messages.get("networks.toolbar.import.sampleNetworks"));
        addFactoryMenu(sampleNetworksMenu, "IeeeCdfNetworkFactory", List.of(
                new Pair<>("create9", IeeeCdfNetworkFactory::create9),
                new Pair<>("create14", IeeeCdfNetworkFactory::create14),
                new Pair<>("create30", IeeeCdfNetworkFactory::create30),
                new Pair<>("create33", IeeeCdfNetworkFactory::create33),
                new Pair<>("create57", IeeeCdfNetworkFactory::create57),
                new Pair<>("create69", IeeeCdfNetworkFactory::create69),
                new Pair<>("create118", IeeeCdfNetworkFactory::create118),
                new Pair<>("create300", IeeeCdfNetworkFactory::create300)));
        addFactoryMenu(sampleNetworksMenu, "MetrixTutorialSixBusesFactory", List.of(
                new Pair<>("create", MetrixTutorialSixBusesFactory::create)));
        addFactoryMenu(sampleNetworksMenu, "BatteryNetworkFactory", List.of(
                new Pair<>("create", BatteryNetworkFactory::create)));
        addFactoryMenu(sampleNetworksMenu, "BoundaryLineNetworkFactory", List.of(
                new Pair<>("create", BoundaryLineNetworkFactory::create),
                new Pair<>("createWithGeneration", BoundaryLineNetworkFactory::createWithGeneration)));
        addFactoryMenu(sampleNetworksMenu, "DcDetailedNetworkFactory", List.of(
                new Pair<>("createLccMonopoleGroundReturn", DcDetailedNetworkFactory::createLccMonopoleGroundReturn),
                new Pair<>("createLccMonopoleMetallicReturn", DcDetailedNetworkFactory::createLccMonopoleMetallicReturn),
                new Pair<>("createLccBipoleGroundReturn", DcDetailedNetworkFactory::createLccBipoleGroundReturn),
                new Pair<>("createLccBipoleGroundReturnNegativePoleOutage", DcDetailedNetworkFactory::createLccBipoleGroundReturnNegativePoleOutage),
                new Pair<>("createLccBipoleGroundReturnWithDcLineSegments", DcDetailedNetworkFactory::createLccBipoleGroundReturnWithDcLineSegments),
                new Pair<>("createVscSymmetricalMonopole", DcDetailedNetworkFactory::createVscSymmetricalMonopole),
                new Pair<>("createVscAsymmetricalMonopole", DcDetailedNetworkFactory::createVscAsymmetricalMonopole),
                new Pair<>("createSimple2NodesDcSwitch", DcDetailedNetworkFactory::createSimple2NodesDcSwitch),
                new Pair<>("createSimple4NodesDcLinesSwitchLine", DcDetailedNetworkFactory::createSimple4NodesDcLinesSwitchLine)));
        addFactoryMenu(sampleNetworksMenu, "EuropeanLvTestFeederFactory", List.of(
                new Pair<>("create", EuropeanLvTestFeederFactory::create)));
        addFactoryMenu(sampleNetworksMenu, "EurostagTutorialExample1Factory", List.of(
                new Pair<>("create", EurostagTutorialExample1Factory::create),
                new Pair<>("createWithTieLine", EurostagTutorialExample1Factory::createWithTieLine),
                new Pair<>("createWithLFResults", EurostagTutorialExample1Factory::createWithLFResults),
                new Pair<>("createWithMoreGenerators", EurostagTutorialExample1Factory::createWithMoreGenerators),
                new Pair<>("createWithFixedCurrentLimits", EurostagTutorialExample1Factory::createWithFixedCurrentLimits),
                new Pair<>("createWithMultipleSelectedFixedCurrentLimits", EurostagTutorialExample1Factory::createWithMultipleSelectedFixedCurrentLimits),
                new Pair<>("createWithHighAndLowCurrentLimits", EurostagTutorialExample1Factory::createWithHighAndLowCurrentLimits),
                new Pair<>("createWithMultipleSelectedFixedActivePowerLimits", EurostagTutorialExample1Factory::createWithMultipleSelectedFixedActivePowerLimits),
                new Pair<>("createWithMultipleSelectedFixedApparentPowerLimits", EurostagTutorialExample1Factory::createWithMultipleSelectedFixedApparentPowerLimits),
                new Pair<>("createWithFixedLimits", EurostagTutorialExample1Factory::createWithFixedLimits),
                new Pair<>("createWithFixedCurrentLimitsOnBoundaryLines", EurostagTutorialExample1Factory::createWithFixedCurrentLimitsOnBoundaryLines),
                new Pair<>("createWithFixedLimitsOnBoundaryLines", EurostagTutorialExample1Factory::createWithFixedLimitsOnBoundaryLines),
                new Pair<>("createWithMultipleConnectedComponents", EurostagTutorialExample1Factory::createWithMultipleConnectedComponents),
                new Pair<>("createWithTerminalMockExt", EurostagTutorialExample1Factory::createWithTerminalMockExt),
                new Pair<>("createWithVoltageAngleLimit", EurostagTutorialExample1Factory::createWithVoltageAngleLimit),
                new Pair<>("createWithTieLinesAndAreas", EurostagTutorialExample1Factory::createWithTieLinesAndAreas),
                new Pair<>("createWithReactiveTcc", EurostagTutorialExample1Factory::createWithReactiveTcc),
                new Pair<>("createRemoteReactiveTcc", EurostagTutorialExample1Factory::createRemoteReactiveTcc),
                new Pair<>("createRemoteVoltageTcc", EurostagTutorialExample1Factory::createRemoteVoltageTcc),
                new Pair<>("createWithoutRtcControl", EurostagTutorialExample1Factory::createWithoutRtcControl),
                new Pair<>("createWith3wTransformer", EurostagTutorialExample1Factory::createWith3wTransformer),
                new Pair<>("createWith3wWithVoltageControl", EurostagTutorialExample1Factory::createWith3wWithVoltageControl),
                new Pair<>("createWith3wWithoutControl", EurostagTutorialExample1Factory::createWith3wWithoutControl),
                new Pair<>("create3wWithReactiveTcc", EurostagTutorialExample1Factory::create3wWithReactiveTcc),
                new Pair<>("create3wRemoteReactiveTcc", EurostagTutorialExample1Factory::create3wRemoteReactiveTcc),
                new Pair<>("create3wRemoteVoltageTcc", EurostagTutorialExample1Factory::create3wRemoteVoltageTcc),
                new Pair<>("createWithRemoteVoltageGenerator", EurostagTutorialExample1Factory::createWithRemoteVoltageGenerator),
                new Pair<>("createWithRemoteReactiveGenerator", EurostagTutorialExample1Factory::createWithRemoteReactiveGenerator),
                new Pair<>("createWithLocalReactiveGenerator", EurostagTutorialExample1Factory::createWithLocalReactiveGenerator),
                new Pair<>("createWithRemoteReactiveAndVoltageGenerators", EurostagTutorialExample1Factory::createWithRemoteReactiveAndVoltageGenerators),
                new Pair<>("createWithLocalReactiveAndVoltageGenerator", EurostagTutorialExample1Factory::createWithLocalReactiveAndVoltageGenerator),
                new Pair<>("createWithoutControl", EurostagTutorialExample1Factory::createWithoutControl),
                new Pair<>("createRemoteWithoutControl", EurostagTutorialExample1Factory::createRemoteWithoutControl)));
        addFactoryMenu(sampleNetworksMenu, "FictitiousSwitchFactory", List.of(
                new Pair<>("create", FictitiousSwitchFactory::create)));
        addFactoryMenu(sampleNetworksMenu, "FourSubstationsNodeBreakerFactory", List.of(
                new Pair<>("create", FourSubstationsNodeBreakerFactory::create)));
        addFactoryMenu(sampleNetworksMenu, "FourSubstationsNodeBreakerWithExtensionsFactory", List.of(
                new Pair<>("create", FourSubstationsNodeBreakerWithExtensionsFactory::create)));
        addFactoryMenu(sampleNetworksMenu, "HvdcTestNetwork", List.of(
                new Pair<>("createBase", HvdcTestNetwork::createBase),
                new Pair<>("createVsc", HvdcTestNetwork::createVsc),
                new Pair<>("createLcc", HvdcTestNetwork::createLcc)));
        addFactoryMenu(sampleNetworksMenu, "PhaseShifterTestCaseFactory", List.of(
                new Pair<>("create", PhaseShifterTestCaseFactory::create),
                new Pair<>("createWithTargetDeadband", PhaseShifterTestCaseFactory::createWithTargetDeadband),
                new Pair<>("createRegulatingWithoutMode", PhaseShifterTestCaseFactory::createRegulatingWithoutMode),
                new Pair<>("createLocalActivePowerWithTargetDeadband", PhaseShifterTestCaseFactory::createLocalActivePowerWithTargetDeadband),
                new Pair<>("createLocalCurrentLimiterWithTargetDeadband", PhaseShifterTestCaseFactory::createLocalCurrentLimiterWithTargetDeadband),
                new Pair<>("createRemoteActivePowerWithTargetDeadband", PhaseShifterTestCaseFactory::createRemoteActivePowerWithTargetDeadband),
                new Pair<>("createRemoteCurrentLimiterWithTargetDeadband", PhaseShifterTestCaseFactory::createRemoteCurrentLimiterWithTargetDeadband)));
        addFactoryMenu(sampleNetworksMenu, "ReactiveLimitsTestNetworkFactory", List.of(
                new Pair<>("create", ReactiveLimitsTestNetworkFactory::create),
                new Pair<>("createWithShape", ReactiveLimitsTestNetworkFactory::createWithShape)));
        addFactoryMenu(sampleNetworksMenu, "ScadaNetworkFactory", List.of(
                new Pair<>("create", ScadaNetworkFactory::create)));
        addFactoryMenu(sampleNetworksMenu, "SecurityAnalysisTestNetworkFactory", List.of(
                new Pair<>("create", SecurityAnalysisTestNetworkFactory::create),
                new Pair<>("createWithFixedCurrentLimits", SecurityAnalysisTestNetworkFactory::createWithFixedCurrentLimits),
                new Pair<>("createWithFixedPowerLimits", SecurityAnalysisTestNetworkFactory::createWithFixedPowerLimits)));
        addFactoryMenu(sampleNetworksMenu, "ShuntTestCaseFactory", List.of(
                new Pair<>("create", ShuntTestCaseFactory::create),
                new Pair<>("createWithActivePower", ShuntTestCaseFactory::createWithActivePower),
                new Pair<>("createNonLinear", ShuntTestCaseFactory::createNonLinear),
                new Pair<>("createLocalLinear", ShuntTestCaseFactory::createLocalLinear),
                new Pair<>("createDisabledRemoteLinear", ShuntTestCaseFactory::createDisabledRemoteLinear),
                new Pair<>("createDisabledLocalLinear", ShuntTestCaseFactory::createDisabledLocalLinear),
                new Pair<>("createDisabledRemoteNonLinear", ShuntTestCaseFactory::createDisabledRemoteNonLinear),
                new Pair<>("createDisabledLocalNonLinear", ShuntTestCaseFactory::createDisabledLocalNonLinear),
                new Pair<>("createRemoteLinearNoTarget", ShuntTestCaseFactory::createRemoteLinearNoTarget),
                new Pair<>("createRemoteNonLinearNoTarget", ShuntTestCaseFactory::createRemoteNonLinearNoTarget),
                new Pair<>("createLocalLinearNoTarget", ShuntTestCaseFactory::createLocalLinearNoTarget),
                new Pair<>("createLocalNonLinearNoTarget", ShuntTestCaseFactory::createLocalNonLinearNoTarget),
                new Pair<>("createLocalNonLinear", ShuntTestCaseFactory::createLocalNonLinear)));
        addFactoryMenu(sampleNetworksMenu, "SvcTestCaseFactory", List.of(
                new Pair<>("create", SvcTestCaseFactory::create),
                new Pair<>("createWithMoreSVCs", SvcTestCaseFactory::createWithMoreSVCs),
                new Pair<>("createWithRemoteRegulatingTerminal", SvcTestCaseFactory::createWithRemoteRegulatingTerminal),
                new Pair<>("createLocalVoltageControl", SvcTestCaseFactory::createLocalVoltageControl),
                new Pair<>("createRemoteVoltageControl", SvcTestCaseFactory::createRemoteVoltageControl),
                new Pair<>("createLocalReactiveControl", SvcTestCaseFactory::createLocalReactiveControl),
                new Pair<>("createRemoteReactiveControl", SvcTestCaseFactory::createRemoteReactiveControl),
                new Pair<>("createLocalOffReactiveTarget", SvcTestCaseFactory::createLocalOffReactiveTarget),
                new Pair<>("createRemoteOffReactiveTarget", SvcTestCaseFactory::createRemoteOffReactiveTarget),
                new Pair<>("createLocalOffVoltageTarget", SvcTestCaseFactory::createLocalOffVoltageTarget),
                new Pair<>("createRemoteOffVoltageTarget", SvcTestCaseFactory::createRemoteOffVoltageTarget),
                new Pair<>("createLocalOffBothTarget", SvcTestCaseFactory::createLocalOffBothTarget),
                new Pair<>("createRemoteOffBothTarget", SvcTestCaseFactory::createRemoteOffBothTarget),
                new Pair<>("createLocalOffNoTarget", SvcTestCaseFactory::createLocalOffNoTarget),
                new Pair<>("createRemoteOffNoTarget", SvcTestCaseFactory::createRemoteOffNoTarget)));
        addFactoryMenu(sampleNetworksMenu, "ThreeWindingsTransformerNetworkFactory", List.of(
                new Pair<>("create", ThreeWindingsTransformerNetworkFactory::create),
                new Pair<>("createWithCurrentLimits", ThreeWindingsTransformerNetworkFactory::createWithCurrentLimits),
                new Pair<>("createWithUnsortedEndsAndCurrentLimits", ThreeWindingsTransformerNetworkFactory::createWithUnsortedEndsAndCurrentLimits),
                new Pair<>("createWithApparentPowerLimits", ThreeWindingsTransformerNetworkFactory::createWithApparentPowerLimits),
                new Pair<>("createWithActivePowerLimits", ThreeWindingsTransformerNetworkFactory::createWithActivePowerLimits),
                new Pair<>("createWithCurrentLimitsAndTerminalsPAndQ", ThreeWindingsTransformerNetworkFactory::createWithCurrentLimitsAndTerminalsPAndQ)));
        addFactoryMenu(sampleNetworksMenu, "TwoVoltageLevelNetworkFactory", List.of(
                new Pair<>("create", TwoVoltageLevelNetworkFactory::create),
                new Pair<>("createWithGrounds", TwoVoltageLevelNetworkFactory::createWithGrounds)));

        addFactoryMenu(sampleNetworksMenu, "Cgmes3Catalog", List.of(
                new Pair<>("microGrid", cgmes(Cgmes3Catalog::microGrid)),
                new Pair<>("microGridWithoutTpSv", cgmes(Cgmes3Catalog::microGridWithoutTpSv)),
                new Pair<>("miniGrid", cgmes(Cgmes3Catalog::miniGrid)),
                new Pair<>("miniGridWithoutTpSv", cgmes(Cgmes3Catalog::miniGridWithoutTpSv)),
                new Pair<>("smallGrid", cgmes(Cgmes3Catalog::smallGrid)),
                new Pair<>("smallGridWithoutTpSv", cgmes(Cgmes3Catalog::smallGridWithoutTpSv)),
                new Pair<>("svedala", cgmes(Cgmes3Catalog::svedala)),
                new Pair<>("svedalaWithoutTpSv", cgmes(Cgmes3Catalog::svedalaWithoutTpSv))));
        addFactoryMenu(sampleNetworksMenu, "CgmesConformity1Catalog", List.of(
                new Pair<>("microGridBaseCaseBE", cgmes(CgmesConformity1Catalog::microGridBaseCaseBE)),
                new Pair<>("microGridType4BE", cgmes(CgmesConformity1Catalog::microGridType4BE)),
                new Pair<>("microGridType4BEOnlyEqTpSsh", cgmes(CgmesConformity1Catalog::microGridType4BEOnlyEqTpSsh)),
                new Pair<>("microGridBaseCaseNL", cgmes(CgmesConformity1Catalog::microGridBaseCaseNL)),
                new Pair<>("microGridBaseCaseAssembled", cgmes(CgmesConformity1Catalog::microGridBaseCaseAssembled)),
                new Pair<>("miniBusBranch", cgmes(CgmesConformity1Catalog::miniBusBranch)),
                new Pair<>("miniNodeBreaker", cgmes(CgmesConformity1Catalog::miniNodeBreaker)),
                new Pair<>("miniNodeBreakerOnlyEQ", cgmes(CgmesConformity1Catalog::miniNodeBreakerOnlyEQ)),
                new Pair<>("smallBusBranch", cgmes(CgmesConformity1Catalog::smallBusBranch)),
                new Pair<>("smallBusBranchEqTp", cgmes(CgmesConformity1Catalog::smallBusBranchEqTp)),
                new Pair<>("smallNodeBreaker", cgmes(CgmesConformity1Catalog::smallNodeBreaker)),
                new Pair<>("smallNodeBreakerEqTp", cgmes(CgmesConformity1Catalog::smallNodeBreakerEqTp)),
                new Pair<>("smallNodeBreakerEqTpSsh", cgmes(CgmesConformity1Catalog::smallNodeBreakerEqTpSsh)),
                new Pair<>("smallNodeBreakerHvdcEqTp", cgmes(CgmesConformity1Catalog::smallNodeBreakerHvdcEqTp)),
                new Pair<>("smallNodeBreakerHvdc", cgmes(CgmesConformity1Catalog::smallNodeBreakerHvdc)),
                new Pair<>("smallNodeBreakerOnlyEQ", cgmes(CgmesConformity1Catalog::smallNodeBreakerOnlyEQ)),
                new Pair<>("smallNodeBreakerHvdcOnlyEQ", cgmes(CgmesConformity1Catalog::smallNodeBreakerHvdcOnlyEQ))));
        addFactoryMenu(sampleNetworksMenu, "CgmesConformity1NetworkCatalog", List.of(
                new Pair<>("microBaseCaseBE", CgmesConformity1NetworkCatalog::microBaseCaseBE),
                new Pair<>("microType4BE", CgmesConformity1NetworkCatalog::microType4BE)));
        addFactoryMenu(sampleNetworksMenu, "CgmesConformity2Catalog", List.of(
                new Pair<>("microGridType2Assembled", cgmes(CgmesConformity2Catalog::microGridType2Assembled))));
        addFactoryMenu(sampleNetworksMenu, "CgmesConformity3Catalog", List.of(
                new Pair<>("microGridBaseCaseBE", cgmes(CgmesConformity3Catalog::microGridBaseCaseBE)),
                new Pair<>("microGridBaseCaseNL", cgmes(CgmesConformity3Catalog::microGridBaseCaseNL)),
                new Pair<>("microGridBaseCaseAssembled", cgmes(CgmesConformity3Catalog::microGridBaseCaseAssembled))));
        addFactoryMenu(sampleNetworksMenu, "ReliCapGridCatalog", List.of(
                new Pair<>("belgovia", cgmes(ReliCapGridCatalog::belgovia)),
                new Pair<>("britheim", cgmes(ReliCapGridCatalog::britheim)),
                new Pair<>("espheim", cgmes(ReliCapGridCatalog::espheim)),
                new Pair<>("galia", cgmes(ReliCapGridCatalog::galia)),
                new Pair<>("nordheim", cgmes(ReliCapGridCatalog::nordheim)),
                new Pair<>("svedala", cgmes(ReliCapGridCatalog::svedala)),
                new Pair<>("hvdcEspheimSvedala", cgmes(ReliCapGridCatalog::hvdcEspheimSvedala)),
                new Pair<>("hvdcNordheimGalia", cgmes(ReliCapGridCatalog::hvdcNordheimGalia)),
                new Pair<>("nineRealms", cgmes(ReliCapGridCatalog::nineRealms))));

        importMenuButton.getItems().add(sampleNetworksMenu);
    }

    private void addFactoryMenu(Menu parent, String factoryName, List<Pair<String, Supplier<Network>>> networks) {
        Menu factoryMenu = new Menu(factoryName);
        for (Pair<String, Supplier<Network>> pair : networks) {
            MenuItem item = new MenuItem(pair.getKey());
            item.setOnAction(event -> loadSample(pair.getValue()));
            factoryMenu.getItems().add(item);
        }
        parent.getItems().add(factoryMenu);
    }

    private static Supplier<Network> cgmes(Supplier<? extends GridModelReference> gridModel) {
        return () -> Network.read(gridModel.get().dataSource());
    }

    private void initializeExportMenu() {
        Stream.concat(
                IIDM_FORMATS.stream().filter(Exporter.getFormats()::contains),
                Exporter.getFormats().stream().filter(format -> !IIDM_FORMATS.contains(format)).sorted()
        ).forEach(format -> {
            MenuItem formatItem = new MenuItem(format);
            formatItem.setOnAction(event -> exportNetwork(selectedNetwork, format));
            exportMenuButton.getItems().add(formatItem);
        });
    }

    private void importNetwork(List<String> formats) {
        List<Importer> importers = formats.stream().map(Importer::find).filter(Objects::nonNull).toList();
        if (importers.isEmpty()) {
            return;
        }
        String dialogFormat = importers.size() > 1 ? "IIDM" : formats.getFirst();
        ImportNetworkDialog.show(networksTreeView.getScene().getWindow(), dialogFormat, importers)
                .ifPresent(request -> runImport(importers, request));
    }

    private void runImport(List<Importer> importers, ImportNetworkDialog.Request request) {
        Service<NetworkAndReport> networkImportService = new Service<>() {
            @Override
            protected Task<NetworkAndReport> createTask() {
                return new Task<>() {
                    @Override
                    protected NetworkAndReport call() {
                        ReportNode reportNode = ReportNode.newRootReportNode()
                                .withAllResourceBundlesFromClasspath()
                                .withMessageTemplate("powsybl.desktop.network.import")
                                .withTimestamp()
                                .build();
                        ReadOnlyDataSource dataSource = Exporters.createDataSource(request.inputPath());
                        Importer importer = importers.size() == 1 ? importers.getFirst()
                                : importers.stream().filter(candidate -> candidate.exists(dataSource)).findFirst().orElse(importers.getFirst());
                        Network network = importer.importData(dataSource, NetworkFactory.findDefault(), request.parameters(), reportNode);
                        return new NetworkAndReport(network, reportNode);
                    }
                };
            }
        };
        Notification runningNotification = Notification.createRunning("main.networkImport.running", networkImportService::cancel);
        mainModel.addNotification(runningNotification);

        networkImportService.setOnSucceeded(event -> {
            NetworkAndReport networkAndReport = (NetworkAndReport) event.getSource().getValue();
            mainModel.addNetwork(networkAndReport.network());
            mainModel.addReport(networkAndReport.reportNode());

            NotificationAction viewReportAction = new NotificationAction("main.report.viewReport", e ->
                    mainModel.addNavigationEvent(NavigationEvent.create(NavigationType.REPORTS,
                            ReportNavigationState.create(networkAndReport.reportNode()))));

            mainModel.replaceNotification(runningNotification,
                    Notification.createSuccess(runningNotification.startTimestamp(), "main.networkImport.completed", viewReportAction));
        });
        networkImportService.setOnFailed(event -> {
            Throwable exception = event.getSource().getException();
            LOGGER.error(exception.toString(), exception);

            NotificationAction viewLogsAction = new NotificationAction("main.viewLogs", e ->
                    mainModel.addNavigationEvent(NavigationEvent.create(NavigationType.LOGS)));

            mainModel.replaceNotification(runningNotification,
                    Notification.createError(runningNotification.startTimestamp(), "main.networkImport.failed", viewLogsAction));
        });
        networkImportService.setOnCancelled(event -> mainModel.replaceNotification(runningNotification,
                Notification.createCancelled(runningNotification.startTimestamp(), "main.networkImport.cancelled")));
        networkImportService.start();
    }

    private void loadSample(Supplier<Network> supplier) {
        Service<Network> networkLoadingService = new Service<>() {
            @Override
            protected Task<Network> createTask() {
                return new Task<>() {
                    @Override
                    protected Network call() {
                        return supplier.get();
                    }
                };
            }
        };
        Notification runningNotification = Notification.createRunning("main.sampleNetwork.running", networkLoadingService::cancel);
        mainModel.addNotification(runningNotification);

        networkLoadingService.setOnSucceeded(event -> {
            mainModel.addNetwork((Network) event.getSource().getValue());
            mainModel.replaceNotification(runningNotification,
                    Notification.createSuccess(runningNotification.startTimestamp(), "main.sampleNetwork.completed"));
        });
        networkLoadingService.setOnFailed(event -> {
            Throwable exception = event.getSource().getException();
            LOGGER.error(exception.toString(), exception);

            NotificationAction viewLogsAction = new NotificationAction("main.viewLogs", e ->
                    mainModel.addNavigationEvent(NavigationEvent.create(NavigationType.LOGS)));

            mainModel.replaceNotification(runningNotification,
                    Notification.createError(runningNotification.startTimestamp(), "main.sampleNetwork.failed", viewLogsAction));
        });
        networkLoadingService.setOnCancelled(event -> mainModel.replaceNotification(runningNotification,
                Notification.createCancelled(runningNotification.startTimestamp(), "main.sampleNetwork.cancelled")));
        networkLoadingService.start();
    }

    @FXML
    private void onClose() {
        if (confirmClose(Messages.get("networks.close.confirmMessage"))) {
            getSelectedNetworks().forEach(mainModel::removeNetwork);
        }
    }

    @FXML
    private void onCloseAll() {
        if (confirmClose(Messages.get("networks.closeAll.confirmMessage"))) {
            mainModel.removeAllNetworks();
        }
    }

    private boolean confirmClose(String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.YES, ButtonType.NO);
        alert.setHeaderText(null);
        return alert.showAndWait().filter(ButtonType.YES::equals).isPresent();
    }

    @FXML
    private void onMerge() {
        List<Network> selectedNetworks = getSelectedNetworks();
        Network merged = Network.merge(selectedNetworks.toArray(new Network[0]));
        selectedNetworks.forEach(mainModel::removeNetwork);
        mainModel.addNetwork(merged);
    }

    @FXML
    private void onDetach() {
        List<Network> selectedNetworks = getSelectedNetworks();
        // network.getSubnetworks() is emptied as it's iterated over by detach(), so snapshot it first
        List<Network> subnetworksToDetach = selectedNetworks.size() == 1 && isParentNetwork(selectedNetworks.getFirst())
                ? List.copyOf(selectedNetworks.getFirst().getSubnetworks())
                : selectedNetworks;
        subnetworksToDetach.forEach(subnetwork -> mainModel.addNetwork(subnetwork.detach()));
    }

    private void exportNetwork(Network network, String format) {
        Exporter exporter = Exporter.find(format);
        if (exporter == null) {
            return;
        }
        ExportNetworkDialog.show(networksTreeView.getScene().getWindow(), network, format, exporter)
                .ifPresent(request -> runExport(network, exporter, request));
    }

    private void runExport(Network network, Exporter exporter, ExportNetworkDialog.Request request) {
        ReportNode reportNode = ReportNode.newRootReportNode()
                .withAllResourceBundlesFromClasspath()
                .withMessageTemplate("powsybl.desktop.network.export")
                .withTimestamp()
                .build();
        Service<Void> exportService = new Service<>() {
            @Override
            protected Task<Void> createTask() {
                return new Task<>() {
                    @Override
                    protected Void call() {
                        DataSource dataSource = Exporters.createDataSource(request.outputPath());
                        exporter.export(network, request.parameters(), dataSource, reportNode);
                        return null;
                    }
                };
            }
        };
        Notification runningNotification = Notification.createRunning("main.networkExport.running", exportService::cancel);
        mainModel.addNotification(runningNotification);

        exportService.setOnSucceeded(event -> {
            mainModel.addReport(reportNode);

            NotificationAction viewReportAction = new NotificationAction("main.report.viewReport", e ->
                    mainModel.addNavigationEvent(NavigationEvent.create(NavigationType.REPORTS,
                            ReportNavigationState.create(reportNode))));

            mainModel.replaceNotification(runningNotification,
                    Notification.createSuccess(runningNotification.startTimestamp(), "main.networkExport.completed", viewReportAction));
        });
        exportService.setOnFailed(event -> {
            Throwable exception = event.getSource().getException();
            LOGGER.error(exception.toString(), exception);

            NotificationAction viewLogsAction = new NotificationAction("main.viewLogs", e ->
                    mainModel.addNavigationEvent(NavigationEvent.create(NavigationType.LOGS)));

            mainModel.replaceNotification(runningNotification,
                    Notification.createError(runningNotification.startTimestamp(), "main.networkExport.failed", viewLogsAction));
        });
        exportService.setOnCancelled(event -> mainModel.replaceNotification(runningNotification,
                Notification.createCancelled(runningNotification.startTimestamp(), "main.networkExport.cancelled")));
        exportService.start();
    }

    private TreeItem<Network> findTreeViewItem(TreeItem<Network> item, Network network) {
        if (network != null) {
            if (Objects.equals(item.getValue(), network)) {
                return item;
            }
            for (TreeItem<Network> child : item.getChildren()) {
                TreeItem<Network> n = findTreeViewItem(child, network);
                if (n != null) {
                    return n;
                }
            }
        }
        return null;
    }

    public void navigateTo(Network selectedNetwork) {
        // the tree's MULTIPLE selection mode (for close/merge/detach) means select() adds to the current
        // selection rather than replacing it, so it must be paired with clearSelection() here
        if (Objects.isNull(selectedNetwork)) {
            this.networksTreeView.getSelectionModel().clearSelection();
            return;
        }
        this.networksTreeView.getRoot().getChildren().forEach(child -> {
            TreeItem<Network> found = findTreeViewItem(child, selectedNetwork);
            if (found != null) {
                this.networksTreeView.getSelectionModel().clearAndSelect(this.networksTreeView.getRow(found));
                this.networksTreeView.scrollTo(this.networksTreeView.getSelectionModel().getSelectedIndex());
            }
        });
    }
}
