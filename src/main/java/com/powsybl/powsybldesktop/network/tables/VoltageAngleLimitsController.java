/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.tables;

import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Terminal;
import com.powsybl.iidm.network.VoltageAngleLimit;
import com.powsybl.powsybldesktop.SceneModel;
import com.powsybl.powsybldesktop.network.edit.NetworkObjectEdits;
import com.powsybl.powsybldesktop.parameters.GuiParameters;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.geometry.Orientation;
import javafx.scene.control.Button;
import javafx.scene.control.Separator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.Comparator;
import java.util.List;
import java.util.OptionalDouble;
import java.util.stream.Stream;

/**
 * Flat table of the network's voltage angle limits: the terminals whose voltage angle difference they bound, and their
 * bounds. IIDM can't change a voltage angle limit (nor give it a name, properties or extensions, it isn't an
 * identifiable): it is created and removed here.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class VoltageAngleLimitsController extends AbstractNetworkObjectTableController<VoltageAngleLimit> {

    @FXML
    public TableView<VoltageAngleLimit> voltageAngleLimitsTableView;
    @FXML
    private ToolBar toolBar;
    @FXML
    TableColumn<VoltageAngleLimit, String> idColumn;
    @FXML
    TableColumn<VoltageAngleLimit, String> fromColumn;
    @FXML
    TableColumn<VoltageAngleLimit, String> toColumn;
    @FXML
    TableColumn<VoltageAngleLimit, Double> lowLimitColumn;
    @FXML
    TableColumn<VoltageAngleLimit, Double> highLimitColumn;

    @FXML
    private void initialize() {
        initializeTable();
        idColumn.setCellValueFactory(cellData -> new ReadOnlyStringWrapper(cellData.getValue().getId()));
        TableColumnSupport.configureCopyableColumn(idColumn);
        fromColumn.setCellValueFactory(cellData -> new ReadOnlyStringWrapper(describe(cellData.getValue().getTerminalFrom())));
        toColumn.setCellValueFactory(cellData -> new ReadOnlyStringWrapper(describe(cellData.getValue().getTerminalTo())));
        TableColumnSupport.configureNullableDoubleColumn(lowLimitColumn, limit -> valueOrNull(limit.getLowLimit()), format(GuiParameters.Quantity.ANGLE));
        TableColumnSupport.configureNullableDoubleColumn(highLimitColumn, limit -> valueOrNull(limit.getHighLimit()), format(GuiParameters.Quantity.ANGLE));
    }

    private static Double valueOrNull(OptionalDouble value) {
        return value.isPresent() ? value.getAsDouble() : null;
    }

    // its equipment, and its side for a multi-terminal one
    static String describe(Terminal terminal) {
        String equipment = terminal.getConnectable().getNameOrId();
        return terminal.getSide() == null ? equipment : equipment + " (" + Messages.get("limits.side", terminal.getSide().getNum()) + ")";
    }

    @Override
    public void setSceneModel(SceneModel sceneModel) {
        super.setSceneModel(sceneModel);
        Button addButton = new Button(null, new FontIcon("mdi2p-plus"));
        addButton.setTooltip(new Tooltip(Messages.get("edit.menu.new")));
        addButton.disableProperty().bind(mainModel.networkBusyProperty().or(sceneModel.networkProperty().isNull()));
        addButton.setOnAction(_ -> NetworkObjectEdits.newVoltageAngleLimit(sceneModel, toolBar.getScene().getWindow(), sceneModel.getNetwork()));
        Button removeButton = new Button(null, new FontIcon("mdi2d-delete"));
        removeButton.setTooltip(new Tooltip(Messages.get("desktop.common.action.remove")));
        removeButton.disableProperty().bind(mainModel.networkBusyProperty().or(voltageAngleLimitsTableView.getSelectionModel().selectedItemProperty().isNull()));
        removeButton.setOnAction(_ -> NetworkObjectEdits.removeVoltageAngleLimit(mainModel, toolBar.getScene().getWindow(),
                voltageAngleLimitsTableView.getSelectionModel().getSelectedItem()));
        toolBar.getItems().addAll(0, List.of(addButton, removeButton, new Separator(Orientation.VERTICAL)));
    }

    @Override
    TableView<VoltageAngleLimit> tableView() {
        return voltageAngleLimitsTableView;
    }

    @Override
    Stream<VoltageAngleLimit> networkItems(Network network) {
        return network.getVoltageAngleLimitsStream().sorted(Comparator.comparing(VoltageAngleLimit::getId));
    }
}
