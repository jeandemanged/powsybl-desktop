/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.tables;

import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Container;
import com.powsybl.iidm.network.HvdcLine;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.LccConverterStation;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.powsybldesktop.navigation.ContainerNavigationState;
import com.powsybl.powsybldesktop.navigation.HvdcLineNavigationState;
import com.powsybl.powsybldesktop.navigation.LccConverterStationNavigationState;
import com.powsybl.powsybldesktop.navigation.NavigationEvent;
import com.powsybl.powsybldesktop.navigation.NavigationType;
import com.powsybl.powsybldesktop.network.detail.EquipmentDetails;
import com.powsybl.powsybldesktop.network.edit.EditMenus;
import com.powsybl.powsybldesktop.network.edit.InjectionCreations;
import com.powsybl.powsybldesktop.network.search.NetworkSearch;
import com.powsybl.powsybldesktop.network.search.SearchBoxController;
import com.powsybl.powsybldesktop.parameters.GuiParameters;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Flat table of all LCC converter stations in the network, with their substation and voltage level shown as
 * leftmost columns (substation is optional: a voltage level need not belong to one). Clicking the substation
 * or voltage level navigates to it in the substations view; a station's HVDC line links to the HVDC line table.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class LccConverterStationsController extends AbstractEquipmentTableController<LccConverterStation> {

    @FXML
    public TableView<LccConverterStation> lccConverterStationsTableView;
    @FXML
    private SearchBoxController searchBoxController;
    @FXML
    private ColumnVisibilityToolbarController columnVisibilityToolbarController;

    @FXML
    TableColumn<LccConverterStation, LccConverterStation> substationColumn;
    @FXML
    TableColumn<LccConverterStation, LccConverterStation> voltageLevelColumn;
    @FXML
    TableColumn<LccConverterStation, String> nameColumn;
    @FXML
    TableColumn<LccConverterStation, LccConverterStation> hvdcLineColumn;
    @FXML
    TableColumn<LccConverterStation, LccConverterStation> connectedColumn;
    @FXML
    TableColumn<LccConverterStation, Integer> connectedComponentColumn;
    @FXML
    TableColumn<LccConverterStation, Integer> synchronousComponentColumn;
    @FXML
    TableColumn<LccConverterStation, Double> lossFactorColumn;
    @FXML
    TableColumn<LccConverterStation, Double> powerFactorColumn;
    @FXML
    TableColumn<LccConverterStation, Double> pColumn;
    @FXML
    TableColumn<LccConverterStation, Double> qColumn;
    @FXML
    TableColumn<LccConverterStation, Double> iColumn;

    @FXML
    private void initialize() {
        initializeTable();

        TableColumnSupport.configureContainerColumn(substationColumn,
                station -> station.getTerminal().getVoltageLevel().getSubstation(), this::containerCell);
        TableColumnSupport.configureContainerColumn(voltageLevelColumn,
                station -> Optional.of(station.getTerminal().getVoltageLevel()), this::containerCell);
        nameColumn.setCellValueFactory(cellData -> new ReadOnlyStringWrapper(cellData.getValue().getNameOrId()));
        TableColumnSupport.configureDetailNameColumn(nameColumn, item -> EquipmentDetails.open(sceneModel, item));

        hvdcLineColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue()));
        hvdcLineColumn.setCellFactory(col -> new TableCell<LccConverterStation, LccConverterStation>() {
            @Override
            protected void updateItem(LccConverterStation item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty || item == null || item.getHvdcLine() == null ? null : hvdcLineLink(item.getHvdcLine(), item));
            }
        });

        TableColumnSupport.configureConnectedColumn(connectedColumn, LccConverterStation::getTerminal, terminal -> mainModel.setUpdate(terminal.getVoltageLevel()));
        TableColumnSupport.configureComponentColumn(connectedComponentColumn,
                station -> station.getTerminal().getBusView().getBus(), Bus::getConnectedComponent);
        TableColumnSupport.configureComponentColumn(synchronousComponentColumn,
                station -> station.getTerminal().getBusView().getBus(), Bus::getSynchronousComponent);

        lossFactorColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>((double) cellData.getValue().getLossFactor()));
        TableColumnSupport.configureDoubleColumn(lossFactorColumn, format(GuiParameters.Quantity.PERCENTAGE));
        lossFactorColumn.setOnEditCommit(event -> event.getRowValue().setLossFactor(event.getNewValue().floatValue()));

        powerFactorColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>((double) cellData.getValue().getPowerFactor()));
        TableColumnSupport.configureDoubleColumn(powerFactorColumn, format(GuiParameters.Quantity.POWER_FACTOR));
        powerFactorColumn.setOnEditCommit(event -> event.getRowValue().setPowerFactor(event.getNewValue().floatValue()));

        pColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().getTerminal().getP()));
        TableColumnSupport.configureDoubleColumn(pColumn, format(GuiParameters.Quantity.POWER));

        qColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().getTerminal().getQ()));
        TableColumnSupport.configureDoubleColumn(qColumn, format(GuiParameters.Quantity.POWER));

        iColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().getTerminal().getI()));
        TableColumnSupport.configureDoubleColumn(iColumn, format(GuiParameters.Quantity.CURRENT));

        columnVisibilityToolbarController.configure(List.of(
                ColumnVisibilityToolbarController.ColumnGroup.of("desktop.common.columnGroup.solvedValues", true,
                        pColumn, qColumn, iColumn)));
    }

    private Hyperlink hvdcLineLink(HvdcLine hvdcLine, LccConverterStation item) {
        Hyperlink link = new Hyperlink(hvdcLine.getNameOrId());
        link.getStyleClass().add("container-link");
        link.setOnAction(event -> {
            if (isEmbedded()) {
                sceneModel.navigate(NavigationEvent.create(NavigationType.SUBSTATIONS,
                        ContainerNavigationState.create(item.getTerminal().getVoltageLevel(), ContainerNavigationState.ContainerTab.HVDC_LINES)));
            } else {
                sceneModel.record(ownNavigationEvent(item));
                sceneModel.navigate(NavigationEvent.create(NavigationType.NETWORK_TABLE_HVDC_LINES, HvdcLineNavigationState.create(hvdcLine)));
            }
        });
        return link;
    }

    @Override
    TableView<LccConverterStation> tableView() {
        return lccConverterStationsTableView;
    }

    @Override
    SearchBoxController searchBox() {
        return searchBoxController;
    }

    @Override
    EnumSet<NetworkSearch.Kind> searchKinds() {
        return EnumSet.of(NetworkSearch.Kind.SUBSTATION, NetworkSearch.Kind.VOLTAGE_LEVEL, NetworkSearch.Kind.LCC_CONVERTER_STATION);
    }

    @Override
    Stream<LccConverterStation> networkItems(Network network) {
        return network.getLccConverterStationStream();
    }

    @Override
    List<VoltageLevel> voltageLevelsOf(LccConverterStation station) {
        return List.of(station.getTerminal().getVoltageLevel());
    }

    @Override
    Optional<LccConverterStation> asOwnEntity(Identifiable<?> match) {
        return match instanceof LccConverterStation station ? Optional.of(station) : Optional.empty();
    }

    @Override
    TableColumn<LccConverterStation, ?> nameColumn() {
        return nameColumn;
    }

    @Override
    TableColumn<LccConverterStation, ?> substationColumn() {
        return substationColumn;
    }

    @Override
    TableColumn<LccConverterStation, ?> voltageLevelColumn() {
        return voltageLevelColumn;
    }

    public void goToLccConverterStation(LccConverterStation station) {
        goToItem(station);
    }

    @Override
    NavigationEvent ownNavigationEvent(LccConverterStation station) {
        return NavigationEvent.create(NavigationType.NETWORK_TABLE_LCC_CONVERTER_STATIONS, LccConverterStationNavigationState.create(station));
    }

    @Override
    protected List<MenuItem> creationItems(Container<?> context) {
        return EditMenus.injectionItems(sceneModel, () -> tableView().getScene().getWindow(), sceneModel.getNetwork(), context,
                InjectionCreations.Kind.LCC_CONVERTER_STATION);
    }
}
