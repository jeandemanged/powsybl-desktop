/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.tables;

import com.powsybl.iidm.network.Network;
import com.powsybl.powsybldesktop.MainModel;
import com.powsybl.powsybldesktop.SceneModel;
import com.powsybl.powsybldesktop.SceneView;
import com.powsybl.powsybldesktop.parameters.GuiParameters;
import com.powsybl.powsybldesktop.utils.AbstractDisposableController;
import com.powsybl.powsybldesktop.utils.TableAutoFitLimiter;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.scene.control.TableView;
import javafx.util.StringConverter;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * A table of network objects that don't belong to a voltage level, unlike {@link AbstractEquipmentTableController}'s
 * (areas, overload management systems, voltage angle limits): standalone only, without search box, refreshed after
 * every network update.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
abstract class AbstractNetworkObjectTableController<T> extends AbstractDisposableController implements SceneView {

    protected SceneModel sceneModel;
    protected MainModel mainModel;
    private final ObservableList<T> data = FXCollections.observableArrayList();

    abstract TableView<T> tableView();

    /**
     * The rows of {@code network}, in their initial order.
     */
    abstract Stream<T> networkItems(Network network);

    final void initializeTable() {
        tableView().getSelectionModel().setCellSelectionEnabled(true);
        SortedList<T> sorted = new SortedList<>(data);
        sorted.comparatorProperty().bind(tableView().comparatorProperty());
        tableView().setItems(sorted);
        TableAutoFitLimiter.install(tableView());
    }

    @Override
    public void setSceneModel(SceneModel sceneModel) {
        this.sceneModel = Objects.requireNonNull(sceneModel);
        this.mainModel = sceneModel.getMainModel();
        // see AbstractEquipmentTableController.setSceneModel
        if (tableView().isEditable()) {
            tableView().editableProperty().bind(mainModel.networkBusyProperty().not());
        }
        refresh();
        listenerManager.listen(sceneModel.networkProperty(), (observable, oldValue, newValue) -> refresh());
        listenerManager.listen(mainModel.updateProperty(), (observable, oldValue, newValue) -> refresh());
        listenerManager.listen(mainModel.getParametersModel().guiParametersRevisionProperty(), (observable, oldValue, newValue) -> tableView().refresh());
    }

    private void refresh() {
        Network network = sceneModel.getNetwork();
        data.setAll(network == null ? List.of() : networkItems(network).toList());
    }

    final StringConverter<Double> format(GuiParameters.Quantity quantity) {
        return TableColumnSupport.doubleFormat(() -> mainModel.getParametersModel().getGuiParameters(), quantity);
    }

    final void selectInTable(T item) {
        int row = item == null ? -1 : tableView().getItems().indexOf(item);
        if (row < 0) {
            tableView().getSelectionModel().clearSelection();
        } else {
            tableView().getSelectionModel().clearAndSelect(row, tableView().getColumns().getFirst());
            tableView().scrollTo(row);
        }
    }
}
