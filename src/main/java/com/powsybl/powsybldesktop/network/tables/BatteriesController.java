/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.tables;

import com.powsybl.iidm.network.Battery;
import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.iidm.network.regulation.RegulationMode;
import com.powsybl.powsybldesktop.navigation.BatteryNavigationState;
import com.powsybl.powsybldesktop.navigation.NavigationEvent;
import com.powsybl.powsybldesktop.navigation.NavigationType;
import com.powsybl.powsybldesktop.network.detail.EquipmentDetails;
import com.powsybl.powsybldesktop.network.search.NetworkSearch;
import com.powsybl.powsybldesktop.network.search.SearchBoxController;
import com.powsybl.powsybldesktop.parameters.GuiParameters;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Flat table of all batteries in the network, with their substation and voltage level shown as leftmost
 * columns (substation is optional: a voltage level need not belong to one). Clicking the substation
 * or voltage level navigates to it in the substations view.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class BatteriesController extends AbstractEquipmentTableController<Battery> {

    @FXML
    public TableView<Battery> batteriesTableView;
    @FXML
    private SearchBoxController searchBoxController;
    @FXML
    private ColumnVisibilityToolbarController columnVisibilityToolbarController;

    @FXML
    TableColumn<Battery, Battery> substationColumn;
    @FXML
    TableColumn<Battery, Battery> voltageLevelColumn;
    @FXML
    TableColumn<Battery, String> nameColumn;
    @FXML
    TableColumn<Battery, Battery> connectedColumn;
    @FXML
    TableColumn<Battery, Integer> connectedComponentColumn;
    @FXML
    TableColumn<Battery, Integer> synchronousComponentColumn;
    @FXML
    TableColumn<Battery, Double> targetPColumn;
    @FXML
    TableColumn<Battery, Double> targetQColumn;
    @FXML
    TableColumn<Battery, Double> minPColumn;
    @FXML
    TableColumn<Battery, Double> maxPColumn;
    @FXML
    TableColumn<Battery, Boolean> regulatingColumn;
    @FXML
    TableColumn<Battery, RegulationMode> regulationModeColumn;
    @FXML
    TableColumn<Battery, Double> targetVColumn;
    @FXML
    TableColumn<Battery, Double> pColumn;
    @FXML
    TableColumn<Battery, Double> qColumn;
    @FXML
    TableColumn<Battery, Double> iColumn;
    @FXML
    TableColumn<Battery, Double> regulatedBusVoltageColumn;

    @FXML
    private void initialize() {
        initializeTable();

        TableColumnSupport.configureContainerColumn(substationColumn,
                battery -> battery.getTerminal().getVoltageLevel().getSubstation(), this::containerCell);
        TableColumnSupport.configureContainerColumn(voltageLevelColumn,
                battery -> Optional.of(battery.getTerminal().getVoltageLevel()), this::containerCell);
        nameColumn.setCellValueFactory(cellData -> new ReadOnlyStringWrapper(cellData.getValue().getNameOrId()));
        TableColumnSupport.configureDetailNameColumn(nameColumn, item -> EquipmentDetails.open(sceneModel, item));
        TableColumnSupport.configureConnectedColumn(connectedColumn, Battery::getTerminal, terminal -> mainModel.setUpdate(terminal.getVoltageLevel()));
        TableColumnSupport.configureComponentColumn(connectedComponentColumn,
                battery -> battery.getTerminal().getBusView().getBus(), Bus::getConnectedComponent);
        TableColumnSupport.configureComponentColumn(synchronousComponentColumn,
                battery -> battery.getTerminal().getBusView().getBus(), Bus::getSynchronousComponent);

        targetPColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().getTargetP()));
        TableColumnSupport.configureDoubleColumn(targetPColumn, format(GuiParameters.Quantity.POWER));
        targetPColumn.setOnEditCommit(event -> event.getRowValue().setTargetP(event.getNewValue()));

        targetQColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(VoltageRegulations.regulatingTargetQ(cellData.getValue())));
        TableColumnSupport.configureDoubleColumn(targetQColumn, format(GuiParameters.Quantity.POWER));
        targetQColumn.setOnEditCommit(event -> VoltageRegulations.setRegulatingTargetQ(event.getRowValue(), event.getNewValue()));

        minPColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().getMinP()));
        TableColumnSupport.configureDoubleColumn(minPColumn, format(GuiParameters.Quantity.POWER));
        minPColumn.setOnEditCommit(event -> event.getRowValue().setMinP(event.getNewValue()));

        maxPColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().getMaxP()));
        TableColumnSupport.configureDoubleColumn(maxPColumn, format(GuiParameters.Quantity.POWER));
        maxPColumn.setOnEditCommit(event -> event.getRowValue().setMaxP(event.getNewValue()));

        TableColumnSupport.configureEditableBooleanColumn(regulatingColumn,
                Battery::isRegulating, (battery, on) -> VoltageRegulations.setRegulating(battery, RegulationMode.VOLTAGE, on));

        TableColumnSupport.configureChoiceColumn(regulationModeColumn, VoltageRegulations::mode, VoltageRegulations::setMode,
                List.of(RegulationMode.VOLTAGE, RegulationMode.REACTIVE_POWER), VoltageRegulations.MODE_FORMAT);

        targetVColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().getRegulatingTargetV()));
        TableColumnSupport.configureDoubleColumn(targetVColumn, format(GuiParameters.Quantity.VOLTAGE));
        targetVColumn.setOnEditCommit(event -> VoltageRegulations.setRegulatingTargetV(event.getRowValue(), event.getNewValue()));

        pColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().getTerminal().getP()));
        TableColumnSupport.configureDoubleColumn(pColumn, format(GuiParameters.Quantity.POWER));

        qColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().getTerminal().getQ()));
        TableColumnSupport.configureDoubleColumn(qColumn, format(GuiParameters.Quantity.POWER));

        iColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().getTerminal().getI()));
        TableColumnSupport.configureDoubleColumn(iColumn, format(GuiParameters.Quantity.CURRENT));

        TableColumnSupport.configureNullableDoubleColumn(regulatedBusVoltageColumn, battery -> {
            Bus bus = battery.getRegulatingTerminal().getBusView().getBus();
            return bus == null ? null : bus.getV();
        }, format(GuiParameters.Quantity.VOLTAGE));

        columnVisibilityToolbarController.configure(List.of(
                ColumnVisibilityToolbarController.ColumnGroup.of("desktop.common.columnGroup.solvedValues", true,
                        pColumn, qColumn, iColumn, regulatedBusVoltageColumn)));
    }

    @Override
    TableView<Battery> tableView() {
        return batteriesTableView;
    }

    @Override
    SearchBoxController searchBox() {
        return searchBoxController;
    }

    @Override
    EnumSet<NetworkSearch.Kind> searchKinds() {
        return EnumSet.of(NetworkSearch.Kind.SUBSTATION, NetworkSearch.Kind.VOLTAGE_LEVEL, NetworkSearch.Kind.BATTERY);
    }

    @Override
    Stream<Battery> networkItems(Network network) {
        return network.getBatteryStream();
    }

    @Override
    List<VoltageLevel> voltageLevelsOf(Battery battery) {
        return List.of(battery.getTerminal().getVoltageLevel());
    }

    @Override
    Optional<Battery> asOwnEntity(Identifiable<?> match) {
        return match instanceof Battery battery ? Optional.of(battery) : Optional.empty();
    }

    @Override
    TableColumn<Battery, ?> nameColumn() {
        return nameColumn;
    }

    @Override
    TableColumn<Battery, ?> substationColumn() {
        return substationColumn;
    }

    @Override
    TableColumn<Battery, ?> voltageLevelColumn() {
        return voltageLevelColumn;
    }

    public void goToBattery(Battery battery) {
        goToItem(battery);
    }

    @Override
    NavigationEvent ownNavigationEvent(Battery battery) {
        return NavigationEvent.create(NavigationType.NETWORK_TABLE_BATTERIES, BatteryNavigationState.create(battery));
    }
}
