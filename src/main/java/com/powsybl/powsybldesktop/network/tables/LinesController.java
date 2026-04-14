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
import com.powsybl.iidm.network.Line;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.powsybldesktop.navigation.LineNavigationState;
import com.powsybl.powsybldesktop.navigation.NavigationEvent;
import com.powsybl.powsybldesktop.navigation.NavigationType;
import com.powsybl.powsybldesktop.network.search.NetworkSearch;
import com.powsybl.powsybldesktop.network.search.SearchBoxController;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Flat table of all lines in the network, one row per line, with the substation and voltage level columns
 * stacking each side's value in a single cell, like Connected/CC/SC/P/Q (substation is optional: a voltage
 * level need not belong to one). Connected/CC/SC/P/Q are per-terminal, so each of those columns stacks the terminal 1 and terminal 2
 * values in a single readonly cell. Clicking a substation or voltage level navigates to it in the
 * substations view.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class LinesController extends AbstractEquipmentTableController<Line> {

    @FXML
    public TableView<Line> linesTableView;
    @FXML
    private SearchBoxController searchBoxController;
    @FXML
    private ColumnVisibilityToolbarController columnVisibilityToolbarController;

    @FXML
    TableColumn<Line, Line> substationColumn;
    @FXML
    TableColumn<Line, Line> voltageLevelColumn;
    @FXML
    TableColumn<Line, String> nameColumn;
    @FXML
    TableColumn<Line, Line> connectedColumn;
    @FXML
    TableColumn<Line, Line> connectedComponentColumn;
    @FXML
    TableColumn<Line, Line> synchronousComponentColumn;
    @FXML
    TableColumn<Line, Line> rColumn;
    @FXML
    TableColumn<Line, Line> xColumn;
    @FXML
    TableColumn<Line, Line> g1Column;
    @FXML
    TableColumn<Line, Line> b1Column;
    @FXML
    TableColumn<Line, Line> g2Column;
    @FXML
    TableColumn<Line, Line> b2Column;
    @FXML
    TableColumn<Line, Line> pColumn;
    @FXML
    TableColumn<Line, Line> qColumn;
    @FXML
    TableColumn<Line, Line> iColumn;
    @FXML
    TableColumn<Line, Boolean> patlIViolationColumn;

    @FXML
    private void initialize() {
        initializeTable();

        TableColumnSupport.configureTwoSidedContainerColumn(substationColumn,
                line -> line.getTerminal1().getVoltageLevel().getSubstation(),
                line -> line.getTerminal2().getVoltageLevel().getSubstation(), this::containerCell);
        TableColumnSupport.configureTwoSidedContainerColumn(voltageLevelColumn,
                line -> Optional.of(line.getTerminal1().getVoltageLevel()),
                line -> Optional.of(line.getTerminal2().getVoltageLevel()), this::containerCell);
        nameColumn.setCellValueFactory(cellData -> new ReadOnlyStringWrapper(cellData.getValue().getNameOrId()));

        TableColumnSupport.configureTwoSidedConnectedColumn(connectedColumn,
                Line::getTerminal1, Line::getTerminal2, terminal -> mainModel.setUpdate(terminal.getVoltageLevel()));
        TableColumnSupport.configureTwoSidedComponentColumn(connectedComponentColumn,
                line -> line.getTerminal1().getBusView().getBus(), line -> line.getTerminal2().getBusView().getBus(),
                Bus::getConnectedComponent);
        TableColumnSupport.configureTwoSidedComponentColumn(synchronousComponentColumn,
                line -> line.getTerminal1().getBusView().getBus(), line -> line.getTerminal2().getBusView().getBus(),
                Bus::getSynchronousComponent);

        // R/X/G1/B1/G2/B2 are single values (not per-terminal like P/Q/I above), but rendered the same way
        // as a multi-sided column with a single slot - like Transformers' R/X/G/B for a two-windings transformer -
        // so the editable-cell affordance stays a small per-value box instead of stretching to fill this table's
        // already-tall (two-sided P/Q/I/CC/SC) row height.
        TableColumnSupport.configureMultiSidedEditableDoubleColumn(rColumn, line -> List.of(line.getR()),
                (line, index, value) -> line.setR(value), 2);
        TableColumnSupport.configureMultiSidedEditableDoubleColumn(xColumn, line -> List.of(line.getX()),
                (line, index, value) -> line.setX(value), 2);
        TableColumnSupport.configureMultiSidedEditableDoubleColumn(g1Column, line -> List.of(line.getG1()),
                (line, index, value) -> line.setG1(value), 6);
        TableColumnSupport.configureMultiSidedEditableDoubleColumn(b1Column, line -> List.of(line.getB1()),
                (line, index, value) -> line.setB1(value), 6);
        TableColumnSupport.configureMultiSidedEditableDoubleColumn(g2Column, line -> List.of(line.getG2()),
                (line, index, value) -> line.setG2(value), 6);
        TableColumnSupport.configureMultiSidedEditableDoubleColumn(b2Column, line -> List.of(line.getB2()),
                (line, index, value) -> line.setB2(value), 6);

        TableColumnSupport.configureTwoSidedDoubleColumn(pColumn,
                line -> line.getTerminal1().getP(), line -> line.getTerminal2().getP());
        TableColumnSupport.configureTwoSidedDoubleColumn(qColumn,
                line -> line.getTerminal1().getQ(), line -> line.getTerminal2().getQ());
        TableColumnSupport.configureTwoSidedDoubleColumn(iColumn,
                line -> line.getTerminal1().getI(), line -> line.getTerminal2().getI());
        TableColumnSupport.configureOverloadColumn(patlIViolationColumn, Line::isOverloaded);

        columnVisibilityToolbarController.configure(List.of(
                ColumnVisibilityToolbarController.ColumnGroup.of("network.columnGroup.parameters", false,
                        rColumn, xColumn, g1Column, b1Column, g2Column, b2Column),
                ColumnVisibilityToolbarController.ColumnGroup.of("network.columnGroup.solvedValues", true,
                        pColumn, qColumn, iColumn, patlIViolationColumn)));
    }

    @Override
    TableView<Line> tableView() {
        return linesTableView;
    }

    @Override
    SearchBoxController searchBox() {
        return searchBoxController;
    }

    @Override
    EnumSet<NetworkSearch.Kind> searchKinds() {
        return EnumSet.of(NetworkSearch.Kind.SUBSTATION, NetworkSearch.Kind.VOLTAGE_LEVEL, NetworkSearch.Kind.LINE);
    }

    @Override
    Stream<Line> networkItems(Network network) {
        return network.getLineStream();
    }

    @Override
    List<VoltageLevel> voltageLevelsOf(Line line) {
        return List.of(line.getTerminal1().getVoltageLevel(), line.getTerminal2().getVoltageLevel());
    }

    @Override
    Optional<Line> asOwnEntity(Identifiable<?> match) {
        return match instanceof Line line ? Optional.of(line) : Optional.empty();
    }

    @Override
    TableColumn<Line, ?> nameColumn() {
        return nameColumn;
    }

    @Override
    TableColumn<Line, ?> substationColumn() {
        return substationColumn;
    }

    @Override
    TableColumn<Line, ?> voltageLevelColumn() {
        return voltageLevelColumn;
    }

    // both ends of a line can be genuinely different places, unlike single-terminal equipment - keep the
    // far side's link clickable even when the near side is the container we're already positioned on
    @Override
    protected boolean disableContainerLinks() {
        return false;
    }

    public void goToLine(Line line) {
        goToItem(line);
    }

    @Override
    NavigationEvent ownNavigationEvent(Line line) {
        return NavigationEvent.create(NavigationType.NETWORK_TABLE_LINES, LineNavigationState.create(line));
    }
}
