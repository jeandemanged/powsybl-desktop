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
import com.powsybl.iidm.network.ShuntCompensator;
import com.powsybl.iidm.network.ShuntCompensatorLinearModel;
import com.powsybl.iidm.network.ShuntCompensatorModelType;
import com.powsybl.iidm.network.ShuntCompensatorNonLinearModel;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.powsybldesktop.navigation.NavigationEvent;
import com.powsybl.powsybldesktop.navigation.NavigationType;
import com.powsybl.powsybldesktop.navigation.ShuntCompensatorNavigationState;
import com.powsybl.powsybldesktop.network.search.NetworkSearch;
import com.powsybl.powsybldesktop.network.search.SearchBoxController;
import com.powsybl.powsybldesktop.utils.Messages;
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
 * Flat table of all shunt compensators in the network, with their substation and voltage level shown as leftmost
 * columns (substation is optional: a voltage level need not belong to one). Clicking the substation
 * or voltage level navigates to it in the substations view.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class ShuntCompensatorsController extends AbstractEquipmentTableController<ShuntCompensator> {

    @FXML
    public TableView<ShuntCompensator> shuntCompensatorsTableView;
    @FXML
    private SearchBoxController searchBoxController;
    @FXML
    private ColumnVisibilityToolbarController columnVisibilityToolbarController;

    @FXML
    TableColumn<ShuntCompensator, ShuntCompensator> substationColumn;
    @FXML
    TableColumn<ShuntCompensator, ShuntCompensator> voltageLevelColumn;
    @FXML
    TableColumn<ShuntCompensator, String> nameColumn;
    @FXML
    TableColumn<ShuntCompensator, ShuntCompensator> connectedColumn;
    @FXML
    TableColumn<ShuntCompensator, Integer> connectedComponentColumn;
    @FXML
    TableColumn<ShuntCompensator, Integer> synchronousComponentColumn;
    @FXML
    TableColumn<ShuntCompensator, String> typeColumn;
    @FXML
    TableColumn<ShuntCompensator, ShuntCompensator> modelTypeColumn;
    @FXML
    TableColumn<ShuntCompensator, ShuntCompensator> sectionColumn;
    @FXML
    TableColumn<ShuntCompensator, Integer> maximumSectionColumn;
    @FXML
    TableColumn<ShuntCompensator, Boolean> voltageRegulatorOnColumn;
    @FXML
    TableColumn<ShuntCompensator, Double> targetVColumn;
    @FXML
    TableColumn<ShuntCompensator, Double> targetDeadbandColumn;
    @FXML
    TableColumn<ShuntCompensator, Double> regulatedBusVoltageColumn;
    @FXML
    TableColumn<ShuntCompensator, Integer> solvedSectionColumn;
    @FXML
    TableColumn<ShuntCompensator, Double> pColumn;
    @FXML
    TableColumn<ShuntCompensator, Double> qColumn;
    @FXML
    TableColumn<ShuntCompensator, Double> iColumn;

    @FXML
    private void initialize() {
        initializeTable();

        TableColumnSupport.configureContainerColumn(substationColumn,
                shuntCompensator -> shuntCompensator.getTerminal().getVoltageLevel().getSubstation(), this::containerCell);
        TableColumnSupport.configureContainerColumn(voltageLevelColumn,
                shuntCompensator -> Optional.of(shuntCompensator.getTerminal().getVoltageLevel()), this::containerCell);
        nameColumn.setCellValueFactory(cellData -> new ReadOnlyStringWrapper(cellData.getValue().getNameOrId()));
        TableColumnSupport.configureConnectedColumn(connectedColumn, ShuntCompensator::getTerminal, terminal -> mainModel.setUpdate(terminal.getVoltageLevel()));
        TableColumnSupport.configureComponentColumn(connectedComponentColumn,
                shuntCompensator -> shuntCompensator.getTerminal().getBusView().getBus(), Bus::getConnectedComponent);
        TableColumnSupport.configureComponentColumn(synchronousComponentColumn,
                shuntCompensator -> shuntCompensator.getTerminal().getBusView().getBus(), Bus::getSynchronousComponent);

        typeColumn.setCellValueFactory(cellData -> new ReadOnlyStringWrapper(typeLabel(cellData.getValue())));

        TableColumnSupport.configureInfoButtonColumn(modelTypeColumn, shuntCompensator -> modelTypeLabel(shuntCompensator.getModelType()),
                "shuntCompensators.sections.tooltip", ShuntCompensatorSectionsDialog::show);

        TableColumnSupport.configureSpinnerIntColumn(sectionColumn, ShuntCompensator::getSectionCount,
                ShuntCompensator::getMaximumSectionCount, ShuntCompensator::setSectionCount);
        TableColumnSupport.configureNullableIntColumn(maximumSectionColumn, ShuntCompensator::getMaximumSectionCount);

        TableColumnSupport.configureEditableBooleanColumn(voltageRegulatorOnColumn,
                ShuntCompensator::isVoltageRegulatorOn, ShuntCompensator::setVoltageRegulatorOn);

        targetVColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().getTargetV()));
        TableColumnSupport.configureDoubleColumn(targetVColumn);
        targetVColumn.setOnEditCommit(event -> event.getRowValue().setTargetV(event.getNewValue()));

        targetDeadbandColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().getTargetDeadband()));
        TableColumnSupport.configureDoubleColumn(targetDeadbandColumn);
        targetDeadbandColumn.setOnEditCommit(event -> event.getRowValue().setTargetDeadband(event.getNewValue()));

        TableColumnSupport.configureNullableDoubleColumn(regulatedBusVoltageColumn, shuntCompensator -> {
            Bus bus = shuntCompensator.getRegulatingTerminal().getBusView().getBus();
            return bus == null ? null : bus.getV();
        });

        TableColumnSupport.configureNullableIntColumn(solvedSectionColumn, ShuntCompensator::getSolvedSectionCount);

        pColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().getTerminal().getP()));
        TableColumnSupport.configureDoubleColumn(pColumn);

        qColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().getTerminal().getQ()));
        TableColumnSupport.configureDoubleColumn(qColumn);

        iColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().getTerminal().getI()));
        TableColumnSupport.configureDoubleColumn(iColumn);

        columnVisibilityToolbarController.configure(List.of(
                ColumnVisibilityToolbarController.ColumnGroup.of("network.columnGroup.solvedValues", true,
                        pColumn, qColumn, iColumn, regulatedBusVoltageColumn)));
    }

    // Package-private: reused by ShuntCompensatorSectionsDialog's header, which repeats the row's model type
    // and type next to the shunt's name.
    static String modelTypeLabel(ShuntCompensatorModelType modelType) {
        return Messages.get(modelType == ShuntCompensatorModelType.LINEAR
                ? "shuntCompensators.modelType.linear" : "shuntCompensators.modelType.nonLinear");
    }

    // A linear shunt has a single bPerSection for every section, so its sign alone decides reactor vs. capacitor.
    // A non-linear shunt stores each section's B independently, so all of them must agree on the sign.
    static String typeLabel(ShuntCompensator shuntCompensator) {
        if (shuntCompensator.getModelType() == ShuntCompensatorModelType.LINEAR) {
            double bPerSection = shuntCompensator.getModel(ShuntCompensatorLinearModel.class).getBPerSection();
            return Messages.get(bPerSection < 0 ? "shuntCompensators.type.reactor" : "shuntCompensators.type.capacitor");
        }
        List<ShuntCompensatorNonLinearModel.Section> sections = shuntCompensator.getModel(ShuntCompensatorNonLinearModel.class).getAllSections();
        if (sections.stream().allMatch(section -> section.getB() < 0)) {
            return Messages.get("shuntCompensators.type.reactor");
        }
        if (sections.stream().allMatch(section -> section.getB() > 0)) {
            return Messages.get("shuntCompensators.type.capacitor");
        }
        return Messages.get("shuntCompensators.type.unknown");
    }

    @Override
    TableView<ShuntCompensator> tableView() {
        return shuntCompensatorsTableView;
    }

    @Override
    SearchBoxController searchBox() {
        return searchBoxController;
    }

    @Override
    EnumSet<NetworkSearch.Kind> searchKinds() {
        return EnumSet.of(NetworkSearch.Kind.SUBSTATION, NetworkSearch.Kind.VOLTAGE_LEVEL, NetworkSearch.Kind.SHUNT_COMPENSATOR);
    }

    @Override
    Stream<ShuntCompensator> networkItems(Network network) {
        return network.getShuntCompensatorStream();
    }

    @Override
    List<VoltageLevel> voltageLevelsOf(ShuntCompensator shuntCompensator) {
        return List.of(shuntCompensator.getTerminal().getVoltageLevel());
    }

    @Override
    Optional<ShuntCompensator> asOwnEntity(Identifiable<?> match) {
        return match instanceof ShuntCompensator shuntCompensator ? Optional.of(shuntCompensator) : Optional.empty();
    }

    @Override
    TableColumn<ShuntCompensator, ?> nameColumn() {
        return nameColumn;
    }

    @Override
    TableColumn<ShuntCompensator, ?> substationColumn() {
        return substationColumn;
    }

    @Override
    TableColumn<ShuntCompensator, ?> voltageLevelColumn() {
        return voltageLevelColumn;
    }

    public void goToShuntCompensator(ShuntCompensator shuntCompensator) {
        goToItem(shuntCompensator);
    }

    @Override
    NavigationEvent ownNavigationEvent(ShuntCompensator shuntCompensator) {
        return NavigationEvent.create(NavigationType.NETWORK_TABLE_SHUNT_COMPENSATORS, ShuntCompensatorNavigationState.create(shuntCompensator));
    }
}
