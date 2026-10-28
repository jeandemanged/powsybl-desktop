/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.tables;

import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.OverloadManagementSystem;
import com.powsybl.powsybldesktop.SceneModel;
import com.powsybl.powsybldesktop.network.detail.EquipmentDetails;
import com.powsybl.powsybldesktop.network.edit.EditMenus;
import com.powsybl.powsybldesktop.network.edit.TableEditActions;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Flat table of the network's overload management systems: their substation, whether enabled (editable), the
 * monitored element and side, the number of trippings.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class OverloadManagementSystemsController extends AbstractNetworkObjectTableController<OverloadManagementSystem> {

    @FXML
    public TableView<OverloadManagementSystem> overloadManagementSystemsTableView;
    @FXML
    TableColumn<OverloadManagementSystem, String> nameColumn;
    @FXML
    TableColumn<OverloadManagementSystem, String> substationColumn;
    @FXML
    TableColumn<OverloadManagementSystem, Boolean> enabledColumn;
    @FXML
    TableColumn<OverloadManagementSystem, String> monitoredElementColumn;
    @FXML
    TableColumn<OverloadManagementSystem, String> monitoredSideColumn;
    @FXML
    TableColumn<OverloadManagementSystem, Integer> trippingCountColumn;

    @FXML
    private void initialize() {
        initializeTable();
        nameColumn.setCellValueFactory(cellData -> new ReadOnlyStringWrapper(cellData.getValue().getNameOrId()));
        TableColumnSupport.configureDetailNameColumn(nameColumn, item -> EquipmentDetails.open(sceneModel, item));
        substationColumn.setCellValueFactory(cellData -> new ReadOnlyStringWrapper(cellData.getValue().getSubstation().getNameOrId()));
        TableColumnSupport.configureEditableBooleanColumn(enabledColumn, OverloadManagementSystem::isEnabled, OverloadManagementSystem::setEnabled);
        monitoredElementColumn.setCellValueFactory(cellData -> new ReadOnlyStringWrapper(cellData.getValue().getMonitoredElementId()));
        monitoredSideColumn.setCellValueFactory(cellData -> new ReadOnlyStringWrapper(cellData.getValue().getMonitoredSide().name()));
        trippingCountColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().getTrippings().size()));
    }

    @Override
    public void setSceneModel(SceneModel sceneModel) {
        super.setSceneModel(sceneModel);
        TableEditActions.install(overloadManagementSystemsTableView, sceneModel, () -> {
            OverloadManagementSystem selected = overloadManagementSystemsTableView.getSelectionModel().getSelectedItem();
            return List.of(EditMenus.overloadManagementSystemItem(sceneModel, () -> overloadManagementSystemsTableView.getScene().getWindow(),
                    sceneModel.getNetwork(), selected == null ? null : selected.getSubstation()));
        });
    }

    @Override
    TableView<OverloadManagementSystem> tableView() {
        return overloadManagementSystemsTableView;
    }

    @Override
    Stream<OverloadManagementSystem> networkItems(Network network) {
        return network.getOverloadManagementSystemStream().sorted(Comparator.comparing(Identifiable::getNameOrId));
    }

    public void goToOverloadManagementSystem(OverloadManagementSystem system) {
        selectInTable(system);
    }
}
