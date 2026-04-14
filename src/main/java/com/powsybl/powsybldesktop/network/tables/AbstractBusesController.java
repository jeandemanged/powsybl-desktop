/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.tables;

import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.powsybldesktop.network.search.NetworkSearch;
import com.powsybl.powsybldesktop.network.search.SearchBoxController;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

/**
 * Shared behaviour of the bus/branch and bus/breaker view buses tables ({@link BusesBusViewController},
 * {@link BusesBusBreakerViewController}): the common substation/voltage-level/name/CC/SC/V/angle/fictitious
 * P0-Q0 columns. Subclasses supply their bus stream ({@link #networkItems}) and how a search match is
 * resolved to one of their own rows ({@link #asOwnEntity}): a match is either a bus/branch view (merged)
 * bus or, for a BUS_BREAKER-topology voltage level, a configured (bus/breaker view) bus (see
 * {@link NetworkSearch}) - the bus/breaker view table shows its own rows directly for the latter and maps
 * the former down to one of its configured buses.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
abstract class AbstractBusesController extends AbstractEquipmentTableController<Bus> {

    @FXML
    public TableView<Bus> busesTableView;
    @FXML
    private SearchBoxController searchBoxController;
    @FXML
    private ColumnVisibilityToolbarController columnVisibilityToolbarController;

    @FXML
    TableColumn<Bus, Bus> substationColumn;
    @FXML
    TableColumn<Bus, Bus> voltageLevelColumn;
    @FXML
    TableColumn<Bus, String> nameColumn;
    @FXML
    TableColumn<Bus, Integer> connectedComponentColumn;
    @FXML
    TableColumn<Bus, Integer> synchronousComponentColumn;
    @FXML
    TableColumn<Bus, Double> vColumn;
    @FXML
    TableColumn<Bus, Double> angleColumn;
    @FXML
    TableColumn<Bus, Double> fictitiousP0Column;
    @FXML
    TableColumn<Bus, Double> fictitiousQ0Column;
    @FXML
    TableColumn<Bus, VoltageViolation> voltageViolationColumn;

    final void initializeCommonColumns() {
        initializeTable();

        TableColumnSupport.configureContainerColumn(substationColumn, bus -> bus.getVoltageLevel().getSubstation(), this::containerCell);
        TableColumnSupport.configureContainerColumn(voltageLevelColumn, bus -> Optional.of(bus.getVoltageLevel()), this::containerCell);
        nameColumn.setCellValueFactory(cellData -> new ReadOnlyStringWrapper(cellData.getValue().getNameOrId()));
        TableColumnSupport.configureComponentColumn(connectedComponentColumn, bus -> bus, Bus::getConnectedComponent);
        TableColumnSupport.configureComponentColumn(synchronousComponentColumn, bus -> bus, Bus::getSynchronousComponent);
        TableColumnSupport.configureNullableDoubleColumn(vColumn, Bus::getV);
        TableColumnSupport.configureNullableDoubleColumn(angleColumn, Bus::getAngle);

        fictitiousP0Column.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().getFictitiousP0()));
        TableColumnSupport.configureDoubleColumn(fictitiousP0Column);
        fictitiousP0Column.setOnEditCommit(event -> {
            event.getRowValue().setFictitiousP0(event.getNewValue());
            mainModel.setUpdate(event.getRowValue().getVoltageLevel());
        });

        fictitiousQ0Column.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().getFictitiousQ0()));
        TableColumnSupport.configureDoubleColumn(fictitiousQ0Column);
        fictitiousQ0Column.setOnEditCommit(event -> {
            event.getRowValue().setFictitiousQ0(event.getNewValue());
            mainModel.setUpdate(event.getRowValue().getVoltageLevel());
        });

        TableColumnSupport.configureVoltageViolationColumn(voltageViolationColumn, Bus::getV, Bus::getVoltageLevel);

        columnVisibilityToolbarController.configure(List.of(
                ColumnVisibilityToolbarController.ColumnGroup.of("network.columnGroup.solvedValues", true,
                        vColumn, angleColumn, voltageViolationColumn)));
    }

    @Override
    final TableView<Bus> tableView() {
        return busesTableView;
    }

    @Override
    final SearchBoxController searchBox() {
        return searchBoxController;
    }

    @Override
    final EnumSet<NetworkSearch.Kind> searchKinds() {
        return EnumSet.of(NetworkSearch.Kind.SUBSTATION, NetworkSearch.Kind.VOLTAGE_LEVEL, NetworkSearch.Kind.BUS,
                NetworkSearch.Kind.CONFIGURED_BUS);
    }

    @Override
    final List<VoltageLevel> voltageLevelsOf(Bus bus) {
        return List.of(bus.getVoltageLevel());
    }

    @Override
    final TableColumn<Bus, ?> nameColumn() {
        return nameColumn;
    }

    @Override
    final TableColumn<Bus, ?> substationColumn() {
        return substationColumn;
    }

    @Override
    final TableColumn<Bus, ?> voltageLevelColumn() {
        return voltageLevelColumn;
    }
}
