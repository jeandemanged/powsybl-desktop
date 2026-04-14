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
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.StaticVarCompensator;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.powsybldesktop.navigation.NavigationEvent;
import com.powsybl.powsybldesktop.navigation.NavigationType;
import com.powsybl.powsybldesktop.navigation.StaticVarCompensatorNavigationState;
import com.powsybl.powsybldesktop.network.search.NetworkSearch;
import com.powsybl.powsybldesktop.network.search.SearchBoxController;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.util.StringConverter;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Flat table of all static var compensators in the network, with their substation and voltage level shown as
 * leftmost columns (substation is optional: a voltage level need not belong to one). Clicking the substation
 * or voltage level navigates to it in the substations view.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class StaticVarCompensatorsController extends AbstractEquipmentTableController<StaticVarCompensator> {

    private static final StringConverter<StaticVarCompensator.RegulationMode> REGULATION_MODE_FORMAT = new StringConverter<>() {
        @Override
        public String toString(StaticVarCompensator.RegulationMode value) {
            return value == null ? "" : regulationModeLabel(value);
        }

        @Override
        public StaticVarCompensator.RegulationMode fromString(String text) {
            return Arrays.stream(StaticVarCompensator.RegulationMode.values())
                    .filter(mode -> toString(mode).equals(text))
                    .findFirst().orElseThrow();
        }
    };

    @FXML
    public TableView<StaticVarCompensator> staticVarCompensatorsTableView;
    @FXML
    private SearchBoxController searchBoxController;
    @FXML
    private ColumnVisibilityToolbarController columnVisibilityToolbarController;

    @FXML
    TableColumn<StaticVarCompensator, StaticVarCompensator> substationColumn;
    @FXML
    TableColumn<StaticVarCompensator, StaticVarCompensator> voltageLevelColumn;
    @FXML
    TableColumn<StaticVarCompensator, String> nameColumn;
    @FXML
    TableColumn<StaticVarCompensator, StaticVarCompensator> connectedColumn;
    @FXML
    TableColumn<StaticVarCompensator, Integer> connectedComponentColumn;
    @FXML
    TableColumn<StaticVarCompensator, Integer> synchronousComponentColumn;
    @FXML
    TableColumn<StaticVarCompensator, Boolean> regulatingColumn;
    @FXML
    TableColumn<StaticVarCompensator, StaticVarCompensator.RegulationMode> regulationModeColumn;
    @FXML
    TableColumn<StaticVarCompensator, Double> targetVColumn;
    @FXML
    TableColumn<StaticVarCompensator, Double> targetQColumn;
    @FXML
    TableColumn<StaticVarCompensator, Double> qColumn;
    @FXML
    TableColumn<StaticVarCompensator, Double> iColumn;
    @FXML
    TableColumn<StaticVarCompensator, Double> regulatedBusVoltageColumn;

    @FXML
    private void initialize() {
        initializeTable();

        TableColumnSupport.configureContainerColumn(substationColumn,
                staticVarCompensator -> staticVarCompensator.getTerminal().getVoltageLevel().getSubstation(), this::containerCell);
        TableColumnSupport.configureContainerColumn(voltageLevelColumn,
                staticVarCompensator -> Optional.of(staticVarCompensator.getTerminal().getVoltageLevel()), this::containerCell);
        nameColumn.setCellValueFactory(cellData -> new ReadOnlyStringWrapper(cellData.getValue().getNameOrId()));
        TableColumnSupport.configureConnectedColumn(connectedColumn, StaticVarCompensator::getTerminal, terminal -> mainModel.setUpdate(terminal.getVoltageLevel()));
        TableColumnSupport.configureComponentColumn(connectedComponentColumn,
                staticVarCompensator -> staticVarCompensator.getTerminal().getBusView().getBus(), Bus::getConnectedComponent);
        TableColumnSupport.configureComponentColumn(synchronousComponentColumn,
                staticVarCompensator -> staticVarCompensator.getTerminal().getBusView().getBus(), Bus::getSynchronousComponent);

        TableColumnSupport.configureEditableBooleanColumn(regulatingColumn, StaticVarCompensator::isRegulating, StaticVarCompensator::setRegulating);

        TableColumnSupport.configureChoiceColumn(regulationModeColumn, StaticVarCompensator::getRegulationMode, StaticVarCompensator::setRegulationMode,
                List.of(StaticVarCompensator.RegulationMode.values()), REGULATION_MODE_FORMAT);

        targetVColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().getVoltageSetpoint()));
        TableColumnSupport.configureDoubleColumn(targetVColumn);
        targetVColumn.setOnEditCommit(event -> event.getRowValue().setVoltageSetpoint(event.getNewValue()));

        targetQColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().getReactivePowerSetpoint()));
        TableColumnSupport.configureDoubleColumn(targetQColumn);
        targetQColumn.setOnEditCommit(event -> event.getRowValue().setReactivePowerSetpoint(event.getNewValue()));

        qColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().getTerminal().getQ()));
        TableColumnSupport.configureDoubleColumn(qColumn);

        iColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().getTerminal().getI()));
        TableColumnSupport.configureDoubleColumn(iColumn);

        TableColumnSupport.configureNullableDoubleColumn(regulatedBusVoltageColumn, staticVarCompensator -> {
            Bus bus = staticVarCompensator.getRegulatingTerminal().getBusView().getBus();
            return bus == null ? null : bus.getV();
        });

        columnVisibilityToolbarController.configure(List.of(
                ColumnVisibilityToolbarController.ColumnGroup.of("network.columnGroup.solvedValues", true,
                        qColumn, iColumn, regulatedBusVoltageColumn)));
    }

    private static String regulationModeLabel(StaticVarCompensator.RegulationMode mode) {
        return Messages.get(mode == StaticVarCompensator.RegulationMode.VOLTAGE
                ? "staticVarCompensators.regulationMode.voltage" : "staticVarCompensators.regulationMode.reactivePower");
    }

    @Override
    TableView<StaticVarCompensator> tableView() {
        return staticVarCompensatorsTableView;
    }

    @Override
    SearchBoxController searchBox() {
        return searchBoxController;
    }

    @Override
    EnumSet<NetworkSearch.Kind> searchKinds() {
        return EnumSet.of(NetworkSearch.Kind.SUBSTATION, NetworkSearch.Kind.VOLTAGE_LEVEL, NetworkSearch.Kind.STATIC_VAR_COMPENSATOR);
    }

    @Override
    Stream<StaticVarCompensator> networkItems(Network network) {
        return network.getStaticVarCompensatorStream();
    }

    @Override
    List<VoltageLevel> voltageLevelsOf(StaticVarCompensator staticVarCompensator) {
        return List.of(staticVarCompensator.getTerminal().getVoltageLevel());
    }

    @Override
    Optional<StaticVarCompensator> asOwnEntity(Identifiable<?> match) {
        return match instanceof StaticVarCompensator staticVarCompensator ? Optional.of(staticVarCompensator) : Optional.empty();
    }

    @Override
    TableColumn<StaticVarCompensator, ?> nameColumn() {
        return nameColumn;
    }

    @Override
    TableColumn<StaticVarCompensator, ?> substationColumn() {
        return substationColumn;
    }

    @Override
    TableColumn<StaticVarCompensator, ?> voltageLevelColumn() {
        return voltageLevelColumn;
    }

    public void goToStaticVarCompensator(StaticVarCompensator staticVarCompensator) {
        goToItem(staticVarCompensator);
    }

    @Override
    NavigationEvent ownNavigationEvent(StaticVarCompensator staticVarCompensator) {
        return NavigationEvent.create(NavigationType.NETWORK_TABLE_STATIC_VAR_COMPENSATORS, StaticVarCompensatorNavigationState.create(staticVarCompensator));
    }
}
