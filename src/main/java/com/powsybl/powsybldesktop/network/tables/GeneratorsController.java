/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.tables;

import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Generator;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.powsybldesktop.navigation.GeneratorNavigationState;
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
 * Flat table of all generators in the network, with their substation and voltage level shown as leftmost
 * columns (substation is optional: a voltage level need not belong to one). Clicking the substation
 * or voltage level navigates to it in the substations view.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class GeneratorsController extends AbstractEquipmentTableController<Generator> {

    @FXML
    public TableView<Generator> generatorsTableView;
    @FXML
    private SearchBoxController searchBoxController;
    @FXML
    private ColumnVisibilityToolbarController columnVisibilityToolbarController;

    @FXML
    TableColumn<Generator, Generator> substationColumn;
    @FXML
    TableColumn<Generator, Generator> voltageLevelColumn;
    @FXML
    TableColumn<Generator, String> nameColumn;
    @FXML
    TableColumn<Generator, Generator> connectedColumn;
    @FXML
    TableColumn<Generator, Integer> connectedComponentColumn;
    @FXML
    TableColumn<Generator, Integer> synchronousComponentColumn;
    @FXML
    TableColumn<Generator, Double> targetPColumn;
    @FXML
    TableColumn<Generator, Double> targetQColumn;
    @FXML
    TableColumn<Generator, Double> minPColumn;
    @FXML
    TableColumn<Generator, Double> maxPColumn;
    @FXML
    TableColumn<Generator, Boolean> voltageControlEnabledColumn;
    @FXML
    TableColumn<Generator, Double> targetVColumn;
    @FXML
    TableColumn<Generator, Double> pColumn;
    @FXML
    TableColumn<Generator, Double> qColumn;
    @FXML
    TableColumn<Generator, Double> iColumn;
    @FXML
    TableColumn<Generator, Double> regulatedBusVoltageColumn;

    @FXML
    private void initialize() {
        initializeTable();

        TableColumnSupport.configureContainerColumn(substationColumn,
                generator -> generator.getTerminal().getVoltageLevel().getSubstation(), this::containerCell);
        TableColumnSupport.configureContainerColumn(voltageLevelColumn,
                generator -> Optional.of(generator.getTerminal().getVoltageLevel()), this::containerCell);
        nameColumn.setCellValueFactory(cellData -> new ReadOnlyStringWrapper(cellData.getValue().getNameOrId()));
        TableColumnSupport.configureConnectedColumn(connectedColumn, Generator::getTerminal, terminal -> mainModel.setUpdate(terminal.getVoltageLevel()));
        TableColumnSupport.configureComponentColumn(connectedComponentColumn,
                generator -> generator.getTerminal().getBusView().getBus(), Bus::getConnectedComponent);
        TableColumnSupport.configureComponentColumn(synchronousComponentColumn,
                generator -> generator.getTerminal().getBusView().getBus(), Bus::getSynchronousComponent);

        targetPColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().getTargetP()));
        TableColumnSupport.configureDoubleColumn(targetPColumn);
        targetPColumn.setOnEditCommit(event -> event.getRowValue().setTargetP(event.getNewValue()));

        targetQColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().getTargetQ()));
        TableColumnSupport.configureDoubleColumn(targetQColumn);
        targetQColumn.setOnEditCommit(event -> event.getRowValue().setTargetQ(event.getNewValue()));

        minPColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().getMinP()));
        TableColumnSupport.configureDoubleColumn(minPColumn);
        minPColumn.setOnEditCommit(event -> event.getRowValue().setMinP(event.getNewValue()));

        maxPColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().getMaxP()));
        TableColumnSupport.configureDoubleColumn(maxPColumn);
        maxPColumn.setOnEditCommit(event -> event.getRowValue().setMaxP(event.getNewValue()));

        TableColumnSupport.configureEditableBooleanColumn(voltageControlEnabledColumn,
                Generator::isVoltageRegulatorOn, Generator::setVoltageRegulatorOn);

        targetVColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().getTargetV()));
        TableColumnSupport.configureDoubleColumn(targetVColumn);
        targetVColumn.setOnEditCommit(event -> event.getRowValue().setTargetV(event.getNewValue()));

        pColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().getTerminal().getP()));
        TableColumnSupport.configureDoubleColumn(pColumn);

        qColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().getTerminal().getQ()));
        TableColumnSupport.configureDoubleColumn(qColumn);

        iColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().getTerminal().getI()));
        TableColumnSupport.configureDoubleColumn(iColumn);

        TableColumnSupport.configureNullableDoubleColumn(regulatedBusVoltageColumn, generator -> {
            Bus bus = generator.getRegulatingTerminal().getBusView().getBus();
            return bus == null ? null : bus.getV();
        });

        columnVisibilityToolbarController.configure(List.of(
                ColumnVisibilityToolbarController.ColumnGroup.of("network.columnGroup.solvedValues", true,
                        pColumn, qColumn, iColumn, regulatedBusVoltageColumn)));
    }

    @Override
    TableView<Generator> tableView() {
        return generatorsTableView;
    }

    @Override
    SearchBoxController searchBox() {
        return searchBoxController;
    }

    @Override
    EnumSet<NetworkSearch.Kind> searchKinds() {
        return EnumSet.of(NetworkSearch.Kind.SUBSTATION, NetworkSearch.Kind.VOLTAGE_LEVEL, NetworkSearch.Kind.GENERATOR);
    }

    @Override
    Stream<Generator> networkItems(Network network) {
        return network.getGeneratorStream();
    }

    @Override
    List<VoltageLevel> voltageLevelsOf(Generator generator) {
        return List.of(generator.getTerminal().getVoltageLevel());
    }

    @Override
    Optional<Generator> asOwnEntity(Identifiable<?> match) {
        return match instanceof Generator generator ? Optional.of(generator) : Optional.empty();
    }

    @Override
    TableColumn<Generator, ?> nameColumn() {
        return nameColumn;
    }

    @Override
    TableColumn<Generator, ?> substationColumn() {
        return substationColumn;
    }

    @Override
    TableColumn<Generator, ?> voltageLevelColumn() {
        return voltageLevelColumn;
    }

    public void goToGenerator(Generator generator) {
        goToItem(generator);
    }

    @Override
    NavigationEvent ownNavigationEvent(Generator generator) {
        return NavigationEvent.create(NavigationType.NETWORK_TABLE_GENERATORS, GeneratorNavigationState.create(generator));
    }
}
