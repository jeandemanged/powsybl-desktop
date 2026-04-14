/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.tables;

import com.powsybl.iidm.network.Container;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Substation;
import com.powsybl.iidm.network.Switch;
import com.powsybl.iidm.network.SwitchKind;
import com.powsybl.iidm.network.TopologyKind;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.powsybldesktop.MainModel;
import com.powsybl.powsybldesktop.utils.AbstractDisposableController;
import com.powsybl.powsybldesktop.utils.TableAutoFitLimiter;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.util.Comparator;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Flat table of the switches belonging to the substation/voltage level currently selected in
 * {@link com.powsybl.powsybldesktop.network.SubstationsController}'s tree. Unlike the other equipment
 * tabs there, this one has no standalone whole-network counterpart in the Tables menu: listing every
 * switch of a real transmission network would be far too many rows to be of any use.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class SwitchesController extends AbstractDisposableController implements EmbeddableEquipmentTable {

    @FXML
    TableView<Switch> switchesTableView;
    @FXML
    TableColumn<Switch, String> nameColumn;
    @FXML
    TableColumn<Switch, SwitchKind> kindColumn;
    @FXML
    TableColumn<Switch, Boolean> openColumn;
    @FXML
    TableColumn<Switch, Boolean> retainedColumn;

    private final ObservableList<Switch> data = FXCollections.observableArrayList();

    private MainModel mainModel;
    private Container<?> container;

    @FXML
    private void initialize() {
        switchesTableView.getSelectionModel().setCellSelectionEnabled(true);
        SortedList<Switch> sorted = new SortedList<>(data);
        sorted.comparatorProperty().bind(switchesTableView.comparatorProperty());
        switchesTableView.setItems(sorted);
        TableAutoFitLimiter.install(switchesTableView);

        nameColumn.setCellValueFactory(cellData -> new ReadOnlyStringWrapper(cellData.getValue().getNameOrId()));
        kindColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().getKind()));
        TableColumnSupport.configureEditableBooleanColumn(openColumn, Switch::isOpen, this::setOpen);
        // retain status only means anything in a node/breaker voltage level (see Switch::setRetained) -
        // greyed out and non-interactive otherwise, rather than throwing on every edit attempt.
        TableColumnSupport.configureEditableBooleanColumn(retainedColumn, Switch::isRetained, this::setRetained,
                aSwitch -> aSwitch.getVoltageLevel().getTopologyKind() == TopologyKind.NODE_BREAKER);
    }

    private void setOpen(Switch aSwitch, boolean open) {
        aSwitch.setOpen(open);
        mainModel.setUpdate(aSwitch.getVoltageLevel());
    }

    private void setRetained(Switch aSwitch, boolean retained) {
        aSwitch.setRetained(retained);
        mainModel.setUpdate(aSwitch.getVoltageLevel());
    }

    @Override
    public void setMainModel(MainModel mainModel) {
        this.mainModel = Objects.requireNonNull(mainModel);
        listenerManager.listen(mainModel.updateProperty(), (observable, oldValue, newValue) -> refresh());
    }

    @Override
    public void setContainer(Container<?> container) {
        this.container = container;
        refresh();
    }

    @Override
    public boolean hasRows() {
        return !data.isEmpty();
    }

    private void refresh() {
        data.setAll(switchesOf(container).sorted(Comparator.comparing(Identifiable::getNameOrId)).toList());
    }

    private static Stream<Switch> switchesOf(Container<?> container) {
        if (container instanceof VoltageLevel voltageLevel) {
            return switchStreamOf(voltageLevel);
        }
        if (container instanceof Substation substation) {
            return substation.getVoltageLevelStream().flatMap(SwitchesController::switchStreamOf);
        }
        return Stream.empty();
    }

    private static Stream<Switch> switchStreamOf(VoltageLevel voltageLevel) {
        return StreamSupport.stream(voltageLevel.getSwitches().spliterator(), false);
    }
}
