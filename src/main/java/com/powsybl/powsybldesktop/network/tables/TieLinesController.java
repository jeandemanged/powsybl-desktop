/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.tables;

import com.powsybl.iidm.network.BoundaryLine;
import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.TieLine;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.powsybldesktop.navigation.BoundaryLineNavigationState;
import com.powsybl.powsybldesktop.navigation.ContainerNavigationState;
import com.powsybl.powsybldesktop.navigation.NavigationEvent;
import com.powsybl.powsybldesktop.navigation.NavigationType;
import com.powsybl.powsybldesktop.navigation.TieLineNavigationState;
import com.powsybl.powsybldesktop.network.NetworkTieLines;
import com.powsybl.powsybldesktop.network.search.NetworkSearch;
import com.powsybl.powsybldesktop.network.search.SearchBoxController;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Flat table of all tie lines in the network, one row per tie line, with the substation and voltage level columns
 * stacking each side's value in a single cell, like Connected/CC/SC/P/Q (substation is optional: a voltage level
 * need not belong to one). On a
 * subnetwork, this also includes a tie line straddling two subnetworks as soon as one of its boundary lines
 * belongs to the selected subnetwork (see {@link com.powsybl.powsybldesktop.network.NetworkTieLines}).
 * Pairing key is a single value shared by the tie line; the boundary line column is per-side and stacks a
 * link to each side's boundary line (in the boundary lines view) in a single cell, like Connected/CC/SC/P/Q.
 * The "at boundary" P/Q/V magnitude/V angle columns are the values at the shared fictitious X-node bus, read
 * off boundary line 1 only since both boundary lines report the same values there. Clicking a substation or
 * voltage level navigates to it in the substations view.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class TieLinesController extends AbstractEquipmentTableController<TieLine> {

    @FXML
    public TableView<TieLine> tieLinesTableView;
    @FXML
    private SearchBoxController searchBoxController;
    @FXML
    private ColumnVisibilityToolbarController columnVisibilityToolbarController;

    @FXML
    TableColumn<TieLine, TieLine> substationColumn;
    @FXML
    TableColumn<TieLine, TieLine> voltageLevelColumn;
    @FXML
    TableColumn<TieLine, String> nameColumn;
    @FXML
    TableColumn<TieLine, String> pairingKeyColumn;
    @FXML
    TableColumn<TieLine, TieLine> boundaryLineColumn;
    @FXML
    TableColumn<TieLine, TieLine> connectedColumn;
    @FXML
    TableColumn<TieLine, TieLine> connectedComponentColumn;
    @FXML
    TableColumn<TieLine, TieLine> synchronousComponentColumn;
    @FXML
    TableColumn<TieLine, Double> rColumn;
    @FXML
    TableColumn<TieLine, Double> xColumn;
    @FXML
    TableColumn<TieLine, Double> g1Column;
    @FXML
    TableColumn<TieLine, Double> b1Column;
    @FXML
    TableColumn<TieLine, Double> g2Column;
    @FXML
    TableColumn<TieLine, Double> b2Column;
    @FXML
    TableColumn<TieLine, TieLine> pColumn;
    @FXML
    TableColumn<TieLine, TieLine> qColumn;
    @FXML
    TableColumn<TieLine, TieLine> iColumn;
    @FXML
    TableColumn<TieLine, Boolean> patlIViolationColumn;
    @FXML
    TableColumn<TieLine, Double> pBoundaryColumn;
    @FXML
    TableColumn<TieLine, Double> qBoundaryColumn;
    @FXML
    TableColumn<TieLine, Double> iBoundaryColumn;
    @FXML
    TableColumn<TieLine, Double> vBoundaryColumn;
    @FXML
    TableColumn<TieLine, Double> angleBoundaryColumn;

    @FXML
    private void initialize() {
        initializeTable();

        TableColumnSupport.configureTwoSidedContainerColumn(substationColumn,
                tieLine -> tieLine.getTerminal1().getVoltageLevel().getSubstation(),
                tieLine -> tieLine.getTerminal2().getVoltageLevel().getSubstation(), this::containerCell);
        TableColumnSupport.configureTwoSidedContainerColumn(voltageLevelColumn,
                tieLine -> Optional.of(tieLine.getTerminal1().getVoltageLevel()),
                tieLine -> Optional.of(tieLine.getTerminal2().getVoltageLevel()), this::containerCell);
        nameColumn.setCellValueFactory(cellData -> new ReadOnlyStringWrapper(cellData.getValue().getNameOrId()));
        pairingKeyColumn.setCellValueFactory(cellData -> new ReadOnlyStringWrapper(cellData.getValue().getPairingKey()));
        TableColumnSupport.configureTwoSidedLinkColumn(boundaryLineColumn,
                TieLine::getBoundaryLine1, TieLine::getBoundaryLine2, this::boundaryLineLink);

        TableColumnSupport.configureTwoSidedConnectedColumn(connectedColumn,
                TieLine::getTerminal1, TieLine::getTerminal2, terminal -> mainModel.setUpdate(terminal.getVoltageLevel()));
        TableColumnSupport.configureTwoSidedComponentColumn(connectedComponentColumn,
                tieLine -> tieLine.getTerminal1().getBusView().getBus(), tieLine -> tieLine.getTerminal2().getBusView().getBus(),
                Bus::getConnectedComponent);
        TableColumnSupport.configureTwoSidedComponentColumn(synchronousComponentColumn,
                tieLine -> tieLine.getTerminal1().getBusView().getBus(), tieLine -> tieLine.getTerminal2().getBusView().getBus(),
                Bus::getSynchronousComponent);

        TableColumnSupport.configureNullableDoubleColumn(rColumn, TieLine::getR, 2);
        TableColumnSupport.configureNullableDoubleColumn(xColumn, TieLine::getX, 2);
        TableColumnSupport.configureNullableDoubleColumn(g1Column, TieLine::getG1, 6);
        TableColumnSupport.configureNullableDoubleColumn(b1Column, TieLine::getB1, 6);
        TableColumnSupport.configureNullableDoubleColumn(g2Column, TieLine::getG2, 6);
        TableColumnSupport.configureNullableDoubleColumn(b2Column, TieLine::getB2, 6);

        TableColumnSupport.configureTwoSidedDoubleColumn(pColumn,
                tieLine -> tieLine.getTerminal1().getP(), tieLine -> tieLine.getTerminal2().getP());
        TableColumnSupport.configureTwoSidedDoubleColumn(qColumn,
                tieLine -> tieLine.getTerminal1().getQ(), tieLine -> tieLine.getTerminal2().getQ());
        TableColumnSupport.configureTwoSidedDoubleColumn(iColumn,
                tieLine -> tieLine.getTerminal1().getI(), tieLine -> tieLine.getTerminal2().getI());
        TableColumnSupport.configureOverloadColumn(patlIViolationColumn, TieLine::isOverloaded);

        // the boundary (the shared fictitious X-node bus) is a single point, so both boundary lines report the
        // same values here - reading them off boundary line 1 is enough, unlike the per-side P/Q columns above
        TableColumnSupport.configureNullableDoubleColumn(pBoundaryColumn, tieLine -> tieLine.getBoundaryLine1().getBoundary().getP());
        TableColumnSupport.configureNullableDoubleColumn(qBoundaryColumn, tieLine -> tieLine.getBoundaryLine1().getBoundary().getQ());
        TableColumnSupport.configureNullableDoubleColumn(iBoundaryColumn, tieLine -> tieLine.getBoundaryLine1().getBoundary().getI());
        TableColumnSupport.configureNullableDoubleColumn(vBoundaryColumn, tieLine -> tieLine.getBoundaryLine1().getBoundary().getV());
        TableColumnSupport.configureNullableDoubleColumn(angleBoundaryColumn, tieLine -> tieLine.getBoundaryLine1().getBoundary().getAngle());

        columnVisibilityToolbarController.configure(List.of(
                ColumnVisibilityToolbarController.ColumnGroup.of("network.columnGroup.parameters", false,
                        rColumn, xColumn, g1Column, b1Column, g2Column, b2Column),
                ColumnVisibilityToolbarController.ColumnGroup.of("network.columnGroup.solvedValues", true,
                        pColumn, qColumn, iColumn, patlIViolationColumn),
                ColumnVisibilityToolbarController.ColumnGroup.of("network.columnGroup.solvedValuesAtBoundary", false,
                        pBoundaryColumn, qBoundaryColumn, iBoundaryColumn, vBoundaryColumn, angleBoundaryColumn)));
    }

    private Hyperlink boundaryLineLink(BoundaryLine boundaryLine, TieLine item) {
        Hyperlink link = new Hyperlink(boundaryLine.getNameOrId());
        link.getStyleClass().add("container-link");
        link.setOnAction(event -> {
            if (isEmbedded()) {
                mainModel.addNavigationEvent(NavigationEvent.create(NavigationType.SUBSTATIONS,
                        ContainerNavigationState.create(boundaryLine.getTerminal().getVoltageLevel(), ContainerNavigationState.ContainerTab.BOUNDARY_LINES)));
            } else {
                mainModel.addNavigationEvent(ownNavigationEvent(item), false);
                mainModel.addNavigationEvent(NavigationEvent.create(NavigationType.NETWORK_TABLE_BOUNDARY_LINES, BoundaryLineNavigationState.create(boundaryLine)));
            }
        });
        return link;
    }

    @Override
    TableView<TieLine> tableView() {
        return tieLinesTableView;
    }

    @Override
    SearchBoxController searchBox() {
        return searchBoxController;
    }

    @Override
    EnumSet<NetworkSearch.Kind> searchKinds() {
        return EnumSet.of(NetworkSearch.Kind.SUBSTATION, NetworkSearch.Kind.VOLTAGE_LEVEL, NetworkSearch.Kind.TIE_LINE);
    }

    @Override
    Stream<TieLine> networkItems(Network network) {
        return NetworkTieLines.of(network);
    }

    @Override
    List<VoltageLevel> voltageLevelsOf(TieLine tieLine) {
        return List.of(tieLine.getTerminal1().getVoltageLevel(), tieLine.getTerminal2().getVoltageLevel());
    }

    @Override
    Optional<TieLine> asOwnEntity(Identifiable<?> match) {
        return match instanceof TieLine tieLine ? Optional.of(tieLine) : Optional.empty();
    }

    @Override
    TableColumn<TieLine, ?> nameColumn() {
        return nameColumn;
    }

    @Override
    TableColumn<TieLine, ?> substationColumn() {
        return substationColumn;
    }

    @Override
    TableColumn<TieLine, ?> voltageLevelColumn() {
        return voltageLevelColumn;
    }

    // both ends of a tie line can be genuinely different places, unlike single-terminal equipment - keep
    // the far side's link clickable even when the near side is the container we're already positioned on
    @Override
    protected boolean disableContainerLinks() {
        return false;
    }

    public void goToTieLine(TieLine tieLine) {
        goToItem(tieLine);
    }

    @Override
    NavigationEvent ownNavigationEvent(TieLine tieLine) {
        return NavigationEvent.create(NavigationType.NETWORK_TABLE_TIE_LINES, TieLineNavigationState.create(tieLine));
    }
}
