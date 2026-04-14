/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.tables;

import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Load;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.powsybldesktop.navigation.LoadNavigationState;
import com.powsybl.powsybldesktop.navigation.NavigationEvent;
import com.powsybl.powsybldesktop.navigation.NavigationType;
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
import java.util.stream.Stream;

/**
 * Flat table of all loads in the network, with their substation and voltage level shown as leftmost
 * columns (substation is optional: a voltage level need not belong to one). Clicking the substation
 * or voltage level navigates to it in the substations view.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class LoadsController extends AbstractEquipmentTableController<Load> {

    @FXML
    public TableView<Load> loadsTableView;
    @FXML
    private SearchBoxController searchBoxController;
    @FXML
    private ColumnVisibilityToolbarController columnVisibilityToolbarController;

    @FXML
    TableColumn<Load, Load> substationColumn;
    @FXML
    TableColumn<Load, Load> voltageLevelColumn;
    @FXML
    TableColumn<Load, String> nameColumn;
    @FXML
    TableColumn<Load, Load> connectedColumn;
    @FXML
    TableColumn<Load, Integer> connectedComponentColumn;
    @FXML
    TableColumn<Load, Integer> synchronousComponentColumn;
    @FXML
    TableColumn<Load, Double> p0Column;
    @FXML
    TableColumn<Load, Double> q0Column;
    @FXML
    TableColumn<Load, Double> pColumn;
    @FXML
    TableColumn<Load, Double> qColumn;
    @FXML
    TableColumn<Load, Double> iColumn;

    @FXML
    private void initialize() {
        initializeTable();

        TableColumnSupport.configureContainerColumn(substationColumn,
                load -> load.getTerminal().getVoltageLevel().getSubstation(), this::containerCell);
        TableColumnSupport.configureContainerColumn(voltageLevelColumn,
                load -> Optional.of(load.getTerminal().getVoltageLevel()), this::containerCell);
        nameColumn.setCellValueFactory(cellData -> new ReadOnlyStringWrapper(cellData.getValue().getNameOrId()));
        TableColumnSupport.configureConnectedColumn(connectedColumn, Load::getTerminal, terminal -> mainModel.setUpdate(terminal.getVoltageLevel()));
        TableColumnSupport.configureComponentColumn(connectedComponentColumn,
                load -> load.getTerminal().getBusView().getBus(), Bus::getConnectedComponent);
        TableColumnSupport.configureComponentColumn(synchronousComponentColumn,
                load -> load.getTerminal().getBusView().getBus(), Bus::getSynchronousComponent);

        p0Column.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().getP0()));
        TableColumnSupport.configureDoubleColumn(p0Column);
        p0Column.setOnEditCommit(event -> event.getRowValue().setP0(event.getNewValue()));

        q0Column.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().getQ0()));
        TableColumnSupport.configureDoubleColumn(q0Column);
        q0Column.setOnEditCommit(event -> event.getRowValue().setQ0(event.getNewValue()));

        pColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().getTerminal().getP()));
        TableColumnSupport.configureDoubleColumn(pColumn);

        qColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().getTerminal().getQ()));
        TableColumnSupport.configureDoubleColumn(qColumn);

        iColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().getTerminal().getI()));
        TableColumnSupport.configureDoubleColumn(iColumn);

        columnVisibilityToolbarController.configure(List.of(
                ColumnVisibilityToolbarController.ColumnGroup.of("network.columnGroup.solvedValues", true,
                        pColumn, qColumn, iColumn)));
    }

    @Override
    TableView<Load> tableView() {
        return loadsTableView;
    }

    @Override
    SearchBoxController searchBox() {
        return searchBoxController;
    }

    @Override
    EnumSet<NetworkSearch.Kind> searchKinds() {
        return EnumSet.of(NetworkSearch.Kind.SUBSTATION, NetworkSearch.Kind.VOLTAGE_LEVEL, NetworkSearch.Kind.LOAD);
    }

    @Override
    Stream<Load> networkItems(Network network) {
        return network.getLoadStream();
    }

    @Override
    List<VoltageLevel> voltageLevelsOf(Load load) {
        return List.of(load.getTerminal().getVoltageLevel());
    }

    @Override
    Optional<Load> asOwnEntity(Identifiable<?> match) {
        return match instanceof Load load ? Optional.of(load) : Optional.empty();
    }

    @Override
    TableColumn<Load, ?> nameColumn() {
        return nameColumn;
    }

    @Override
    TableColumn<Load, ?> substationColumn() {
        return substationColumn;
    }

    @Override
    TableColumn<Load, ?> voltageLevelColumn() {
        return voltageLevelColumn;
    }

    public void goToLoad(Load load) {
        goToItem(load);
    }

    @Override
    NavigationEvent ownNavigationEvent(Load load) {
        return NavigationEvent.create(NavigationType.NETWORK_TABLE_LOADS, LoadNavigationState.create(load));
    }
}
