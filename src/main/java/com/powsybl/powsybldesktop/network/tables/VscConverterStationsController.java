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
import com.powsybl.iidm.network.HvdcLine;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.iidm.network.VscConverterStation;
import com.powsybl.iidm.network.regulation.RegulationMode;
import com.powsybl.powsybldesktop.navigation.ContainerNavigationState;
import com.powsybl.powsybldesktop.navigation.HvdcLineNavigationState;
import com.powsybl.powsybldesktop.navigation.NavigationEvent;
import com.powsybl.powsybldesktop.navigation.NavigationType;
import com.powsybl.powsybldesktop.navigation.VscConverterStationNavigationState;
import com.powsybl.powsybldesktop.network.detail.EquipmentDetails;
import com.powsybl.powsybldesktop.network.edit.EditMenus;
import com.powsybl.powsybldesktop.network.edit.InjectionCreations;
import com.powsybl.powsybldesktop.network.search.NetworkSearch;
import com.powsybl.powsybldesktop.network.search.SearchBoxController;
import com.powsybl.powsybldesktop.parameters.GuiParameters;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Flat table of all VSC converter stations in the network, with their substation and voltage level shown as
 * leftmost columns (substation is optional: a voltage level need not belong to one). Clicking the substation
 * or voltage level navigates to it in the substations view; a station's HVDC line links to the HVDC line table.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class VscConverterStationsController extends AbstractEquipmentTableController<VscConverterStation> {

    @FXML
    public TableView<VscConverterStation> vscConverterStationsTableView;
    @FXML
    private SearchBoxController searchBoxController;
    @FXML
    private ColumnVisibilityToolbarController columnVisibilityToolbarController;

    @FXML
    TableColumn<VscConverterStation, VscConverterStation> substationColumn;
    @FXML
    TableColumn<VscConverterStation, VscConverterStation> voltageLevelColumn;
    @FXML
    TableColumn<VscConverterStation, String> nameColumn;
    @FXML
    TableColumn<VscConverterStation, VscConverterStation> hvdcLineColumn;
    @FXML
    TableColumn<VscConverterStation, VscConverterStation> connectedColumn;
    @FXML
    TableColumn<VscConverterStation, Integer> connectedComponentColumn;
    @FXML
    TableColumn<VscConverterStation, Integer> synchronousComponentColumn;
    @FXML
    TableColumn<VscConverterStation, Double> lossFactorColumn;
    @FXML
    TableColumn<VscConverterStation, Boolean> regulatingColumn;
    @FXML
    TableColumn<VscConverterStation, RegulationMode> regulationModeColumn;
    @FXML
    TableColumn<VscConverterStation, Double> targetVColumn;
    @FXML
    TableColumn<VscConverterStation, Double> targetQColumn;
    @FXML
    TableColumn<VscConverterStation, Double> pColumn;
    @FXML
    TableColumn<VscConverterStation, Double> qColumn;
    @FXML
    TableColumn<VscConverterStation, Double> iColumn;
    @FXML
    TableColumn<VscConverterStation, Double> regulatedBusVoltageColumn;

    @FXML
    private void initialize() {
        initializeTable();

        TableColumnSupport.configureContainerColumn(substationColumn,
                station -> station.getTerminal().getVoltageLevel().getSubstation(), this::containerCell);
        TableColumnSupport.configureContainerColumn(voltageLevelColumn,
                station -> Optional.of(station.getTerminal().getVoltageLevel()), this::containerCell);
        nameColumn.setCellValueFactory(cellData -> new ReadOnlyStringWrapper(cellData.getValue().getNameOrId()));
        TableColumnSupport.configureDetailNameColumn(nameColumn, item -> EquipmentDetails.open(sceneModel, item));

        hvdcLineColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue()));
        hvdcLineColumn.setCellFactory(col -> new TableCell<VscConverterStation, VscConverterStation>() {
            @Override
            protected void updateItem(VscConverterStation item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty || item == null || item.getHvdcLine() == null ? null : hvdcLineLink(item.getHvdcLine(), item));
            }
        });

        TableColumnSupport.configureConnectedColumn(connectedColumn, VscConverterStation::getTerminal, terminal -> mainModel.setUpdate(terminal.getVoltageLevel()));
        TableColumnSupport.configureComponentColumn(connectedComponentColumn,
                station -> station.getTerminal().getBusView().getBus(), Bus::getConnectedComponent);
        TableColumnSupport.configureComponentColumn(synchronousComponentColumn,
                station -> station.getTerminal().getBusView().getBus(), Bus::getSynchronousComponent);

        lossFactorColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>((double) cellData.getValue().getLossFactor()));
        TableColumnSupport.configureDoubleColumn(lossFactorColumn, format(GuiParameters.Quantity.PERCENTAGE));
        lossFactorColumn.setOnEditCommit(event -> event.getRowValue().setLossFactor(event.getNewValue().floatValue()));

        TableColumnSupport.configureEditableBooleanColumn(regulatingColumn, VscConverterStation::isRegulating,
                (station, regulating) -> VoltageRegulations.setRegulating(station, RegulationMode.VOLTAGE, regulating));

        TableColumnSupport.configureChoiceColumn(regulationModeColumn, VoltageRegulations::mode, VoltageRegulations::setMode,
                List.of(RegulationMode.VOLTAGE, RegulationMode.REACTIVE_POWER), VoltageRegulations.MODE_FORMAT);

        targetVColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().getRegulatingTargetV()));
        TableColumnSupport.configureDoubleColumn(targetVColumn, format(GuiParameters.Quantity.VOLTAGE));
        targetVColumn.setOnEditCommit(event -> VoltageRegulations.setRegulatingTargetV(event.getRowValue(), event.getNewValue()));

        targetQColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(VoltageRegulations.regulatingTargetQ(cellData.getValue())));
        TableColumnSupport.configureDoubleColumn(targetQColumn, format(GuiParameters.Quantity.POWER));
        targetQColumn.setOnEditCommit(event -> VoltageRegulations.setRegulatingTargetQ(event.getRowValue(), event.getNewValue()));

        pColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().getTerminal().getP()));
        TableColumnSupport.configureDoubleColumn(pColumn, format(GuiParameters.Quantity.POWER));

        qColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().getTerminal().getQ()));
        TableColumnSupport.configureDoubleColumn(qColumn, format(GuiParameters.Quantity.POWER));

        iColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().getTerminal().getI()));
        TableColumnSupport.configureDoubleColumn(iColumn, format(GuiParameters.Quantity.CURRENT));

        TableColumnSupport.configureNullableDoubleColumn(regulatedBusVoltageColumn, station -> {
            Bus bus = station.getRegulatingTerminal().getBusView().getBus();
            return bus == null ? null : bus.getV();
        }, format(GuiParameters.Quantity.VOLTAGE));

        columnVisibilityToolbarController.configure(List.of(
                ColumnVisibilityToolbarController.ColumnGroup.of("desktop.common.columnGroup.solvedValues", true,
                        pColumn, qColumn, iColumn, regulatedBusVoltageColumn)));
    }

    private Hyperlink hvdcLineLink(HvdcLine hvdcLine, VscConverterStation item) {
        Hyperlink link = new Hyperlink(hvdcLine.getNameOrId());
        link.getStyleClass().add("container-link");
        link.setOnAction(event -> {
            if (isEmbedded()) {
                sceneModel.navigate(NavigationEvent.create(NavigationType.SUBSTATIONS,
                        ContainerNavigationState.create(item.getTerminal().getVoltageLevel(), ContainerNavigationState.ContainerTab.HVDC_LINES)));
            } else {
                sceneModel.record(ownNavigationEvent(item));
                sceneModel.navigate(NavigationEvent.create(NavigationType.NETWORK_TABLE_HVDC_LINES, HvdcLineNavigationState.create(hvdcLine)));
            }
        });
        return link;
    }

    @Override
    TableView<VscConverterStation> tableView() {
        return vscConverterStationsTableView;
    }

    @Override
    SearchBoxController searchBox() {
        return searchBoxController;
    }

    @Override
    EnumSet<NetworkSearch.Kind> searchKinds() {
        return EnumSet.of(NetworkSearch.Kind.SUBSTATION, NetworkSearch.Kind.VOLTAGE_LEVEL, NetworkSearch.Kind.VSC_CONVERTER_STATION);
    }

    @Override
    Stream<VscConverterStation> networkItems(Network network) {
        return network.getVscConverterStationStream();
    }

    @Override
    List<VoltageLevel> voltageLevelsOf(VscConverterStation station) {
        return List.of(station.getTerminal().getVoltageLevel());
    }

    @Override
    Optional<VscConverterStation> asOwnEntity(Identifiable<?> match) {
        return match instanceof VscConverterStation station ? Optional.of(station) : Optional.empty();
    }

    @Override
    TableColumn<VscConverterStation, ?> nameColumn() {
        return nameColumn;
    }

    @Override
    TableColumn<VscConverterStation, ?> substationColumn() {
        return substationColumn;
    }

    @Override
    TableColumn<VscConverterStation, ?> voltageLevelColumn() {
        return voltageLevelColumn;
    }

    public void goToVscConverterStation(VscConverterStation station) {
        goToItem(station);
    }

    @Override
    NavigationEvent ownNavigationEvent(VscConverterStation station) {
        return NavigationEvent.create(NavigationType.NETWORK_TABLE_VSC_CONVERTER_STATIONS, VscConverterStationNavigationState.create(station));
    }

    @Override
    protected List<MenuItem> creationItems(Container<?> context) {
        return EditMenus.injectionItems(sceneModel, () -> tableView().getScene().getWindow(), sceneModel.getNetwork(), context,
                InjectionCreations.Kind.VSC_CONVERTER_STATION);
    }
}
