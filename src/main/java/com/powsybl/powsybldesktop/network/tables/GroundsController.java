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
import com.powsybl.iidm.network.Ground;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.powsybldesktop.navigation.GroundNavigationState;
import com.powsybl.powsybldesktop.navigation.NavigationEvent;
import com.powsybl.powsybldesktop.navigation.NavigationType;
import com.powsybl.powsybldesktop.network.detail.EquipmentDetails;
import com.powsybl.powsybldesktop.network.edit.EditMenus;
import com.powsybl.powsybldesktop.network.edit.InjectionCreations;
import com.powsybl.powsybldesktop.network.search.NetworkSearch;
import com.powsybl.powsybldesktop.network.search.SearchBoxController;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Flat table of all grounds (zero impedance connections of a node or bus to the earth) in the network, with their
 * substation and voltage level shown as leftmost columns.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class GroundsController extends AbstractEquipmentTableController<Ground> {

    @FXML
    public TableView<Ground> groundsTableView;
    @FXML
    private SearchBoxController searchBoxController;

    @FXML
    TableColumn<Ground, Ground> substationColumn;
    @FXML
    TableColumn<Ground, Ground> voltageLevelColumn;
    @FXML
    TableColumn<Ground, String> nameColumn;
    @FXML
    TableColumn<Ground, Ground> connectedColumn;
    @FXML
    TableColumn<Ground, Integer> connectedComponentColumn;

    @FXML
    private void initialize() {
        initializeTable();

        TableColumnSupport.configureContainerColumn(substationColumn,
                ground -> ground.getTerminal().getVoltageLevel().getSubstation(), this::containerCell);
        TableColumnSupport.configureContainerColumn(voltageLevelColumn,
                ground -> Optional.of(ground.getTerminal().getVoltageLevel()), this::containerCell);
        nameColumn.setCellValueFactory(cellData -> new ReadOnlyStringWrapper(cellData.getValue().getNameOrId()));
        TableColumnSupport.configureDetailNameColumn(nameColumn, item -> EquipmentDetails.open(sceneModel, item));
        TableColumnSupport.configureConnectedColumn(connectedColumn, Ground::getTerminal, terminal -> mainModel.setUpdate(terminal.getVoltageLevel()));
        TableColumnSupport.configureComponentColumn(connectedComponentColumn,
                ground -> ground.getTerminal().getBusView().getBus(), Bus::getConnectedComponent);
    }

    @Override
    TableView<Ground> tableView() {
        return groundsTableView;
    }

    @Override
    SearchBoxController searchBox() {
        return searchBoxController;
    }

    @Override
    EnumSet<NetworkSearch.Kind> searchKinds() {
        return EnumSet.of(NetworkSearch.Kind.SUBSTATION, NetworkSearch.Kind.VOLTAGE_LEVEL, NetworkSearch.Kind.GROUND);
    }

    @Override
    Stream<Ground> networkItems(Network network) {
        return network.getGroundStream();
    }

    @Override
    List<VoltageLevel> voltageLevelsOf(Ground ground) {
        return List.of(ground.getTerminal().getVoltageLevel());
    }

    @Override
    Optional<Ground> asOwnEntity(Identifiable<?> match) {
        return match instanceof Ground ground ? Optional.of(ground) : Optional.empty();
    }

    @Override
    TableColumn<Ground, ?> nameColumn() {
        return nameColumn;
    }

    @Override
    TableColumn<Ground, ?> substationColumn() {
        return substationColumn;
    }

    @Override
    TableColumn<Ground, ?> voltageLevelColumn() {
        return voltageLevelColumn;
    }

    public void goToGround(Ground ground) {
        goToItem(ground);
    }

    @Override
    NavigationEvent ownNavigationEvent(Ground ground) {
        return NavigationEvent.create(NavigationType.NETWORK_TABLE_GROUNDS, GroundNavigationState.create(ground));
    }

    @Override
    protected List<MenuItem> creationItems(Container<?> context) {
        return EditMenus.injectionItems(sceneModel, () -> tableView().getScene().getWindow(), sceneModel.getNetwork(), context,
                InjectionCreations.Kind.GROUND);
    }
}
