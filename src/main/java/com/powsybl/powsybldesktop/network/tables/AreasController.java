/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.tables;

import com.powsybl.iidm.network.Area;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Network;
import com.powsybl.powsybldesktop.SceneModel;
import com.powsybl.powsybldesktop.network.detail.EquipmentDetails;
import com.powsybl.powsybldesktop.network.edit.EditMenus;
import com.powsybl.powsybldesktop.network.edit.TableEditActions;
import com.powsybl.powsybldesktop.parameters.GuiParameters;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Flat table of the network's areas: type, interchange target (editable) and the interchanges of the last load flow,
 * their voltage level and boundary counts.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class AreasController extends AbstractNetworkObjectTableController<Area> {

    @FXML
    public TableView<Area> areasTableView;
    @FXML
    TableColumn<Area, String> nameColumn;
    @FXML
    TableColumn<Area, String> areaTypeColumn;
    @FXML
    TableColumn<Area, Double> interchangeTargetColumn;
    @FXML
    TableColumn<Area, Double> acInterchangeColumn;
    @FXML
    TableColumn<Area, Double> dcInterchangeColumn;
    @FXML
    TableColumn<Area, Double> interchangeColumn;
    @FXML
    TableColumn<Area, Integer> voltageLevelCountColumn;
    @FXML
    TableColumn<Area, Integer> boundaryCountColumn;

    @FXML
    private void initialize() {
        initializeTable();
        nameColumn.setCellValueFactory(cellData -> new ReadOnlyStringWrapper(cellData.getValue().getNameOrId()));
        TableColumnSupport.configureDetailNameColumn(nameColumn, item -> EquipmentDetails.open(sceneModel, item));
        areaTypeColumn.setCellValueFactory(cellData -> new ReadOnlyStringWrapper(cellData.getValue().getAreaType()));
        TableColumnSupport.configureNullableEditableDoubleColumn(interchangeTargetColumn,
                area -> area.getInterchangeTarget().isPresent() ? area.getInterchangeTarget().getAsDouble() : null,
                (area, target) -> area.setInterchangeTarget(target == null ? Double.NaN : target), format(GuiParameters.Quantity.POWER));
        acInterchangeColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().getAcInterchange()));
        TableColumnSupport.configureDoubleColumn(acInterchangeColumn, format(GuiParameters.Quantity.POWER));
        dcInterchangeColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().getDcInterchange()));
        TableColumnSupport.configureDoubleColumn(dcInterchangeColumn, format(GuiParameters.Quantity.POWER));
        interchangeColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().getInterchange()));
        TableColumnSupport.configureDoubleColumn(interchangeColumn, format(GuiParameters.Quantity.POWER));
        voltageLevelCountColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>((int) cellData.getValue().getVoltageLevelStream().count()));
        boundaryCountColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>((int) cellData.getValue().getAreaBoundaryStream().count()));
    }

    @Override
    public void setSceneModel(SceneModel sceneModel) {
        super.setSceneModel(sceneModel);
        TableEditActions.install(areasTableView, sceneModel,
                () -> List.of(EditMenus.areaItem(sceneModel, () -> areasTableView.getScene().getWindow(), sceneModel.getNetwork())));
    }

    @Override
    TableView<Area> tableView() {
        return areasTableView;
    }

    @Override
    Stream<Area> networkItems(Network network) {
        return network.getAreaStream().sorted(Comparator.comparing(Area::getAreaType).thenComparing(Identifiable::getNameOrId));
    }

    public void goToArea(Area area) {
        selectInTable(area);
    }
}
