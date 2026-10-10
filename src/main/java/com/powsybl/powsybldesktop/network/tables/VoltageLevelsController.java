/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.tables;

import com.powsybl.iidm.network.Container;
import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Substation;
import com.powsybl.iidm.network.TopologyKind;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.powsybldesktop.MainModel;
import com.powsybl.powsybldesktop.SceneModel;
import com.powsybl.powsybldesktop.SceneView;
import com.powsybl.powsybldesktop.navigation.ContainerNavigationState;
import com.powsybl.powsybldesktop.navigation.NavigationEvent;
import com.powsybl.powsybldesktop.navigation.NavigationType;
import com.powsybl.powsybldesktop.navigation.VoltageLevelNavigationState;
import com.powsybl.powsybldesktop.network.search.NetworkSearch;
import com.powsybl.powsybldesktop.network.search.SearchBoxController;
import com.powsybl.powsybldesktop.parameters.GuiParameters;
import com.powsybl.powsybldesktop.utils.AbstractDisposableController;
import com.powsybl.powsybldesktop.utils.TableAutoFitLimiter;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.util.StringConverter;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Flat table of all voltage levels in the network, with their substation shown as a leftmost column (optional: a
 * voltage level need not belong to one). Clicking the substation or the voltage level itself navigates to it in
 * the substations (single-line diagram) view.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class VoltageLevelsController extends AbstractDisposableController implements SceneView {

    @FXML
    public TableView<VoltageLevel> voltageLevelsTableView;
    @FXML
    private SearchBoxController searchBoxController;

    @FXML
    TableColumn<VoltageLevel, Country> countryColumn;
    @FXML
    TableColumn<VoltageLevel, VoltageLevel> substationColumn;
    @FXML
    TableColumn<VoltageLevel, VoltageLevel> nameColumn;
    @FXML
    TableColumn<VoltageLevel, TopologyKind> topologyKindColumn;
    @FXML
    TableColumn<VoltageLevel, Double> nominalVoltageColumn;
    @FXML
    TableColumn<VoltageLevel, Double> lowVoltageLimitColumn;
    @FXML
    TableColumn<VoltageLevel, Double> highVoltageLimitColumn;

    private final ObservableList<VoltageLevel> voltageLevelsData = FXCollections.observableArrayList();

    private SceneModel sceneModel;
    private MainModel mainModel;
    List<VoltageLevel> currentVoltageLevels = List.of();

    @FXML
    private void initialize() {
        voltageLevelsTableView.getSelectionModel().setCellSelectionEnabled(true);

        SortedList<VoltageLevel> sortedVoltageLevels = new SortedList<>(voltageLevelsData);
        sortedVoltageLevels.comparatorProperty().bind(voltageLevelsTableView.comparatorProperty());
        voltageLevelsTableView.setItems(sortedVoltageLevels);
        TableAutoFitLimiter.install(voltageLevelsTableView);

        TableColumnSupport.configureNullableColumn(countryColumn,
                voltageLevel -> voltageLevel.getSubstation().map(Substation::getNullableCountry).orElse(null));
        TableColumnSupport.configureContainerColumn(substationColumn, VoltageLevel::getSubstation, this::containerLink);
        TableColumnSupport.configureContainerColumn(nameColumn, voltageLevel -> Optional.of(voltageLevel), this::containerLink);

        topologyKindColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().getTopologyKind()));

        nominalVoltageColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().getNominalV()));
        TableColumnSupport.configureDoubleColumn(nominalVoltageColumn, format(GuiParameters.Quantity.VOLTAGE));
        nominalVoltageColumn.setOnEditCommit(event -> event.getRowValue().setNominalV(event.getNewValue()));

        lowVoltageLimitColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().getLowVoltageLimit()));
        TableColumnSupport.configureDoubleColumn(lowVoltageLimitColumn, format(GuiParameters.Quantity.VOLTAGE));
        lowVoltageLimitColumn.setOnEditCommit(event -> event.getRowValue().setLowVoltageLimit(event.getNewValue()));

        highVoltageLimitColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().getHighVoltageLimit()));
        TableColumnSupport.configureDoubleColumn(highVoltageLimitColumn, format(GuiParameters.Quantity.VOLTAGE));
        highVoltageLimitColumn.setOnEditCommit(event -> event.getRowValue().setHighVoltageLimit(event.getNewValue()));
    }

    @Override
    public void setSceneModel(SceneModel sceneModel) {
        this.sceneModel = Objects.requireNonNull(sceneModel);
        this.mainModel = sceneModel.getMainModel();
        // see AbstractEquipmentTableController.setSceneModel
        voltageLevelsTableView.editableProperty().bind(mainModel.networkBusyProperty().not());
        updateVoltageLevels();
        listenerManager.listen(sceneModel.networkProperty(), (observable, oldValue, newValue) -> updateVoltageLevels());
        listenerManager.listen(this.mainModel.updateProperty(), (observable, oldValue, newValue) -> updateVoltageLevels());
        // for the decimal places, read by the cells on every render
        listenerManager.listen(this.mainModel.getParametersModel().guiParametersRevisionProperty(), (observable, oldValue, newValue) -> voltageLevelsTableView.refresh());
        searchBoxController.bind(mainModel, this::onSearchMatch);
    }

    @Override
    public void dispose() {
        searchBoxController.dispose();
        super.dispose();
    }

    private void updateVoltageLevels() {
        Network network = sceneModel.getNetwork();
        currentVoltageLevels = network == null ? List.of() : network.getVoltageLevelStream()
                .sorted(Comparator.comparing(Identifiable::getNameOrId))
                .toList();
        voltageLevelsData.setAll(currentVoltageLevels);
    }

    // this table is always standalone (never embedded elsewhere), so its own row selection is always
    // worth recording in history before navigating away - unlike AbstractEquipmentTableController.containerCell
    private Hyperlink containerLink(Container<?> container, VoltageLevel item) {
        Hyperlink link = new Hyperlink(container.getNameOrId());
        link.getStyleClass().add("container-link");
        link.setOnAction(event -> {
            sceneModel.record(NavigationEvent.create(
                    NavigationType.NETWORK_TABLE_VOLTAGE_LEVELS, VoltageLevelNavigationState.create(item)));
            sceneModel.navigate(NavigationEvent.create(NavigationType.SUBSTATIONS, ContainerNavigationState.create(container)));
        });
        return link;
    }

    // A substation match is revealed via one of its voltage levels (the substation itself has no row here);
    // any other match (a voltage level, or any equipment found inside one) is revealed as its own/owning row.
    private void onSearchMatch(Identifiable<?> match) {
        Container<?> container = NetworkSearch.containerOf(match);
        if (container instanceof VoltageLevel voltageLevel) {
            selectInTable(voltageLevel, nameColumn);
        } else if (container instanceof Substation substation) {
            selectInTable(firstVoltageLevelIn(substation), substationColumn);
        }
    }

    private VoltageLevel firstVoltageLevelIn(Substation substation) {
        return currentVoltageLevels.stream()
                .filter(voltageLevel -> voltageLevel.getSubstation().map(substation::equals).orElse(false))
                .findFirst().orElse(null);
    }

    public void goToVoltageLevel(VoltageLevel voltageLevel) {
        selectInTable(voltageLevel, nameColumn);
    }

    private void selectInTable(VoltageLevel voltageLevel, TableColumn<VoltageLevel, ?> column) {
        int row = voltageLevel == null ? -1 : voltageLevelsTableView.getItems().indexOf(voltageLevel);
        if (row < 0) {
            voltageLevelsTableView.getSelectionModel().clearSelection();
        } else {
            voltageLevelsTableView.getSelectionModel().clearAndSelect(row, column);
            voltageLevelsTableView.scrollTo(row);
        }
    }

    // read fresh: a parameters reset/import replaces the GuiParameters instance
    private GuiParameters guiParameters() {
        return mainModel.getParametersModel().getGuiParameters();
    }

    private StringConverter<Double> format(GuiParameters.Quantity quantity) {
        return TableColumnSupport.doubleFormat(this::guiParameters, quantity);
    }
}
