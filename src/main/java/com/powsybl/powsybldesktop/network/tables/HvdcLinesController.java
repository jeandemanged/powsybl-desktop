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
import com.powsybl.iidm.network.HvdcConverterStation;
import com.powsybl.iidm.network.HvdcLine;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.LccConverterStation;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.iidm.network.VscConverterStation;
import com.powsybl.powsybldesktop.navigation.ContainerNavigationState;
import com.powsybl.powsybldesktop.navigation.HvdcLineNavigationState;
import com.powsybl.powsybldesktop.navigation.LccConverterStationNavigationState;
import com.powsybl.powsybldesktop.navigation.NavigationEvent;
import com.powsybl.powsybldesktop.navigation.NavigationType;
import com.powsybl.powsybldesktop.navigation.VscConverterStationNavigationState;
import com.powsybl.powsybldesktop.network.detail.EquipmentDetails;
import com.powsybl.powsybldesktop.network.edit.EditMenus;
import com.powsybl.powsybldesktop.network.search.NetworkSearch;
import com.powsybl.powsybldesktop.network.search.SearchBoxController;
import com.powsybl.powsybldesktop.parameters.GuiParameters;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.util.StringConverter;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Flat table of all HVDC lines in the network, one row per line, its two sides being its two converter stations:
 * the substation, voltage level, converter station, Connected/CC/SC/P/Q/I columns stack each station's value in a
 * single cell, like the Lines table. Clicking a substation or voltage level navigates to it in the substations view;
 * a converter station links to its VSC or LCC converter station table.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class HvdcLinesController extends AbstractEquipmentTableController<HvdcLine> {

    private static final StringConverter<HvdcLine.ConvertersMode> CONVERTERS_MODE_FORMAT = new StringConverter<>() {
        @Override
        public String toString(HvdcLine.ConvertersMode mode) {
            return mode == null ? "" : Messages.get(switch (mode) {
                case SIDE_1_RECTIFIER_SIDE_2_INVERTER -> "hvdcLines.convertersMode.side1RectifierSide2Inverter";
                case SIDE_1_INVERTER_SIDE_2_RECTIFIER -> "hvdcLines.convertersMode.side1InverterSide2Rectifier";
            });
        }

        @Override
        public HvdcLine.ConvertersMode fromString(String text) {
            return Arrays.stream(HvdcLine.ConvertersMode.values())
                    .filter(mode -> toString(mode).equals(text))
                    .findFirst().orElseThrow();
        }
    };

    @FXML
    public TableView<HvdcLine> hvdcLinesTableView;
    @FXML
    private SearchBoxController searchBoxController;
    @FXML
    private ColumnVisibilityToolbarController columnVisibilityToolbarController;

    @FXML
    TableColumn<HvdcLine, HvdcLine> substationColumn;
    @FXML
    TableColumn<HvdcLine, HvdcLine> voltageLevelColumn;
    @FXML
    TableColumn<HvdcLine, String> nameColumn;
    @FXML
    TableColumn<HvdcLine, HvdcLine> converterStationColumn;
    @FXML
    TableColumn<HvdcLine, HvdcLine> connectedColumn;
    @FXML
    TableColumn<HvdcLine, HvdcLine> connectedComponentColumn;
    @FXML
    TableColumn<HvdcLine, HvdcLine> synchronousComponentColumn;
    @FXML
    TableColumn<HvdcLine, HvdcLine> convertersModeColumn;
    @FXML
    TableColumn<HvdcLine, HvdcLine> activePowerSetpointColumn;
    @FXML
    TableColumn<HvdcLine, HvdcLine> maxPColumn;
    @FXML
    TableColumn<HvdcLine, HvdcLine> rColumn;
    @FXML
    TableColumn<HvdcLine, HvdcLine> nominalVColumn;
    @FXML
    TableColumn<HvdcLine, HvdcLine> pColumn;
    @FXML
    TableColumn<HvdcLine, HvdcLine> qColumn;
    @FXML
    TableColumn<HvdcLine, HvdcLine> iColumn;

    @FXML
    private void initialize() {
        initializeTable();

        TableColumnSupport.configureTwoSidedContainerColumn(substationColumn,
                hvdcLine -> hvdcLine.getConverterStation1().getTerminal().getVoltageLevel().getSubstation(),
                hvdcLine -> hvdcLine.getConverterStation2().getTerminal().getVoltageLevel().getSubstation(), this::containerCell);
        TableColumnSupport.configureTwoSidedContainerColumn(voltageLevelColumn,
                hvdcLine -> Optional.of(hvdcLine.getConverterStation1().getTerminal().getVoltageLevel()),
                hvdcLine -> Optional.of(hvdcLine.getConverterStation2().getTerminal().getVoltageLevel()), this::containerCell);
        nameColumn.setCellValueFactory(cellData -> new ReadOnlyStringWrapper(cellData.getValue().getNameOrId()));
        TableColumnSupport.configureDetailNameColumn(nameColumn, item -> EquipmentDetails.open(sceneModel, item));
        TableColumnSupport.configureTwoSidedLinkColumn(converterStationColumn,
                HvdcLine::getConverterStation1, HvdcLine::getConverterStation2, this::converterStationLink);

        TableColumnSupport.configureTwoSidedConnectedColumn(connectedColumn,
                hvdcLine -> hvdcLine.getConverterStation1().getTerminal(), hvdcLine -> hvdcLine.getConverterStation2().getTerminal(),
                terminal -> mainModel.setUpdate(terminal.getVoltageLevel()));
        TableColumnSupport.configureTwoSidedComponentColumn(connectedComponentColumn,
                hvdcLine -> hvdcLine.getConverterStation1().getTerminal().getBusView().getBus(),
                hvdcLine -> hvdcLine.getConverterStation2().getTerminal().getBusView().getBus(), Bus::getConnectedComponent);
        TableColumnSupport.configureTwoSidedComponentColumn(synchronousComponentColumn,
                hvdcLine -> hvdcLine.getConverterStation1().getTerminal().getBusView().getBus(),
                hvdcLine -> hvdcLine.getConverterStation2().getTerminal().getBusView().getBus(), Bus::getSynchronousComponent);

        // single values rendered as single-slot multi-sided columns, like the Lines table's R/X/G/B
        TableColumnSupport.configureSingleSlotChoiceColumn(convertersModeColumn, HvdcLine::getConvertersMode, HvdcLine::setConvertersMode,
                List.of(HvdcLine.ConvertersMode.values()), CONVERTERS_MODE_FORMAT);
        TableColumnSupport.configureMultiSidedEditableDoubleColumn(activePowerSetpointColumn, hvdcLine -> List.of(hvdcLine.getActivePowerSetpoint()),
                (hvdcLine, _, value) -> hvdcLine.setActivePowerSetpoint(value), format(GuiParameters.Quantity.POWER));
        TableColumnSupport.configureMultiSidedEditableDoubleColumn(maxPColumn, hvdcLine -> List.of(hvdcLine.getMaxP()),
                (hvdcLine, _, value) -> hvdcLine.setMaxP(value), format(GuiParameters.Quantity.POWER));
        TableColumnSupport.configureMultiSidedEditableDoubleColumn(rColumn, hvdcLine -> List.of(hvdcLine.getR()),
                (hvdcLine, _, value) -> hvdcLine.setR(value), format(GuiParameters.Quantity.IMPEDANCE));
        TableColumnSupport.configureMultiSidedEditableDoubleColumn(nominalVColumn, hvdcLine -> List.of(hvdcLine.getNominalV()),
                (hvdcLine, _, value) -> hvdcLine.setNominalV(value), format(GuiParameters.Quantity.VOLTAGE));

        TableColumnSupport.configureTwoSidedDoubleColumn(pColumn,
                hvdcLine -> hvdcLine.getConverterStation1().getTerminal().getP(),
                hvdcLine -> hvdcLine.getConverterStation2().getTerminal().getP(), format(GuiParameters.Quantity.POWER));
        TableColumnSupport.configureTwoSidedDoubleColumn(qColumn,
                hvdcLine -> hvdcLine.getConverterStation1().getTerminal().getQ(),
                hvdcLine -> hvdcLine.getConverterStation2().getTerminal().getQ(), format(GuiParameters.Quantity.POWER));
        TableColumnSupport.configureTwoSidedDoubleColumn(iColumn,
                hvdcLine -> hvdcLine.getConverterStation1().getTerminal().getI(),
                hvdcLine -> hvdcLine.getConverterStation2().getTerminal().getI(), format(GuiParameters.Quantity.CURRENT));

        columnVisibilityToolbarController.configure(List.of(
                ColumnVisibilityToolbarController.ColumnGroup.of("desktop.common.parameters", false,
                        rColumn, nominalVColumn),
                ColumnVisibilityToolbarController.ColumnGroup.of("desktop.common.columnGroup.solvedValues", true,
                        pColumn, qColumn, iColumn)));
    }

    private Hyperlink converterStationLink(HvdcConverterStation<?> station, HvdcLine item) {
        Hyperlink link = new Hyperlink(station.getNameOrId());
        link.getStyleClass().add("container-link");
        link.setOnAction(_ -> {
            if (isEmbedded()) {
                ContainerNavigationState.ContainerTab tab = station instanceof VscConverterStation
                        ? ContainerNavigationState.ContainerTab.VSC_CONVERTER_STATIONS
                        : ContainerNavigationState.ContainerTab.LCC_CONVERTER_STATIONS;
                sceneModel.navigate(NavigationEvent.create(NavigationType.SUBSTATIONS,
                        ContainerNavigationState.create(station.getTerminal().getVoltageLevel(), tab)));
            } else {
                sceneModel.record(ownNavigationEvent(item));
                sceneModel.navigate(switch (station) {
                    case VscConverterStation vsc -> NavigationEvent.create(NavigationType.NETWORK_TABLE_VSC_CONVERTER_STATIONS,
                            VscConverterStationNavigationState.create(vsc));
                    case LccConverterStation lcc -> NavigationEvent.create(NavigationType.NETWORK_TABLE_LCC_CONVERTER_STATIONS,
                            LccConverterStationNavigationState.create(lcc));
                    default -> throw new IllegalStateException("Unexpected converter station type: " + station.getHvdcType());
                });
            }
        });
        return link;
    }

    @Override
    TableView<HvdcLine> tableView() {
        return hvdcLinesTableView;
    }

    @Override
    SearchBoxController searchBox() {
        return searchBoxController;
    }

    @Override
    EnumSet<NetworkSearch.Kind> searchKinds() {
        return EnumSet.of(NetworkSearch.Kind.SUBSTATION, NetworkSearch.Kind.VOLTAGE_LEVEL, NetworkSearch.Kind.HVDC_LINE);
    }

    @Override
    Stream<HvdcLine> networkItems(Network network) {
        return network.getHvdcLineStream();
    }

    @Override
    List<VoltageLevel> voltageLevelsOf(HvdcLine hvdcLine) {
        return List.of(hvdcLine.getConverterStation1().getTerminal().getVoltageLevel(),
                hvdcLine.getConverterStation2().getTerminal().getVoltageLevel());
    }

    @Override
    Optional<HvdcLine> asOwnEntity(Identifiable<?> match) {
        return match instanceof HvdcLine hvdcLine ? Optional.of(hvdcLine) : Optional.empty();
    }

    @Override
    TableColumn<HvdcLine, ?> nameColumn() {
        return nameColumn;
    }

    @Override
    TableColumn<HvdcLine, ?> substationColumn() {
        return substationColumn;
    }

    @Override
    TableColumn<HvdcLine, ?> voltageLevelColumn() {
        return voltageLevelColumn;
    }

    // both ends of an HVDC line can be genuinely different places, unlike single-terminal equipment - keep the
    // far side's link clickable even when the near side is the container we're already positioned on
    @Override
    protected boolean disableContainerLinks() {
        return false;
    }

    public void goToHvdcLine(HvdcLine hvdcLine) {
        goToItem(hvdcLine);
    }

    @Override
    NavigationEvent ownNavigationEvent(HvdcLine hvdcLine) {
        return NavigationEvent.create(NavigationType.NETWORK_TABLE_HVDC_LINES, HvdcLineNavigationState.create(hvdcLine));
    }

    @Override
    protected List<MenuItem> creationItems(Container<?> context) {
        return List.of(EditMenus.hvdcLineItem(sceneModel, () -> tableView().getScene().getWindow(), sceneModel.getNetwork(), null));
    }
}
