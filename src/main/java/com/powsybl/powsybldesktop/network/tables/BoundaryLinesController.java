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
import com.powsybl.powsybldesktop.network.search.NetworkSearch;
import com.powsybl.powsybldesktop.network.search.SearchBoxController;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import java.util.stream.Stream;

/**
 * Flat table of all boundary lines in the network (paired or not), with their substation and voltage level shown
 * as leftmost columns (substation is optional: a voltage level need not belong to one). Clicking the substation
 * or voltage level navigates to it in the substations view; a paired boundary line's tie line links to the tie
 * lines view. The generation part (MinP/MaxP/voltage regulation/TargetV/TargetQ) is optional, shown as "-" and
 * not editable when absent. P/Q at the terminal (network side) and at the boundary are shown as separate,
 * distinctly named columns since both can be solved values.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class BoundaryLinesController extends AbstractEquipmentTableController<BoundaryLine> {

    @FXML
    public TableView<BoundaryLine> boundaryLinesTableView;
    @FXML
    private SearchBoxController searchBoxController;
    @FXML
    private ColumnVisibilityToolbarController columnVisibilityToolbarController;

    @FXML
    TableColumn<BoundaryLine, BoundaryLine> substationColumn;
    @FXML
    TableColumn<BoundaryLine, BoundaryLine> voltageLevelColumn;
    @FXML
    TableColumn<BoundaryLine, String> nameColumn;
    @FXML
    TableColumn<BoundaryLine, String> pairingKeyColumn;
    @FXML
    TableColumn<BoundaryLine, BoundaryLine> tieLineColumn;
    @FXML
    TableColumn<BoundaryLine, BoundaryLine> connectedColumn;
    @FXML
    TableColumn<BoundaryLine, Integer> connectedComponentColumn;
    @FXML
    TableColumn<BoundaryLine, Integer> synchronousComponentColumn;
    @FXML
    TableColumn<BoundaryLine, Double> rColumn;
    @FXML
    TableColumn<BoundaryLine, Double> xColumn;
    @FXML
    TableColumn<BoundaryLine, Double> gColumn;
    @FXML
    TableColumn<BoundaryLine, Double> bColumn;
    @FXML
    TableColumn<BoundaryLine, Double> p0Column;
    @FXML
    TableColumn<BoundaryLine, Double> q0Column;
    @FXML
    TableColumn<BoundaryLine, Double> minPColumn;
    @FXML
    TableColumn<BoundaryLine, Double> maxPColumn;
    @FXML
    TableColumn<BoundaryLine, Boolean> voltageRegulatorOnColumn;
    @FXML
    TableColumn<BoundaryLine, Double> targetVColumn;
    @FXML
    TableColumn<BoundaryLine, Double> targetQColumn;
    @FXML
    TableColumn<BoundaryLine, Double> pNetColumn;
    @FXML
    TableColumn<BoundaryLine, Double> qNetColumn;
    @FXML
    TableColumn<BoundaryLine, Double> iNetColumn;
    @FXML
    TableColumn<BoundaryLine, Double> pBoundaryColumn;
    @FXML
    TableColumn<BoundaryLine, Double> qBoundaryColumn;
    @FXML
    TableColumn<BoundaryLine, Double> iBoundaryColumn;
    @FXML
    TableColumn<BoundaryLine, Double> vBoundaryColumn;
    @FXML
    TableColumn<BoundaryLine, Double> angleBoundaryColumn;
    @FXML
    TableColumn<BoundaryLine, Boolean> patlIViolationColumn;

    @FXML
    private void initialize() {
        initializeTable();

        TableColumnSupport.configureContainerColumn(substationColumn,
                boundaryLine -> boundaryLine.getTerminal().getVoltageLevel().getSubstation(), this::containerCell);
        TableColumnSupport.configureContainerColumn(voltageLevelColumn,
                boundaryLine -> Optional.of(boundaryLine.getTerminal().getVoltageLevel()), this::containerCell);
        nameColumn.setCellValueFactory(cellData -> new ReadOnlyStringWrapper(cellData.getValue().getNameOrId()));
        pairingKeyColumn.setCellValueFactory(cellData -> new ReadOnlyStringWrapper(cellData.getValue().getPairingKey()));

        tieLineColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue()));
        tieLineColumn.setCellFactory(col -> new TableCell<BoundaryLine, BoundaryLine>() {
            @Override
            protected void updateItem(BoundaryLine item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty || item == null ? null : item.getTieLine().map(tieLine -> tieLineLink(tieLine, item)).orElse(null));
            }
        });

        TableColumnSupport.configureConnectedColumn(connectedColumn, BoundaryLine::getTerminal, terminal -> mainModel.setUpdate(terminal.getVoltageLevel()));
        TableColumnSupport.configureComponentColumn(connectedComponentColumn,
                boundaryLine -> boundaryLine.getTerminal().getBusView().getBus(), Bus::getConnectedComponent);
        TableColumnSupport.configureComponentColumn(synchronousComponentColumn,
                boundaryLine -> boundaryLine.getTerminal().getBusView().getBus(), Bus::getSynchronousComponent);

        rColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().getR()));
        TableColumnSupport.configureDoubleColumn(rColumn, 2);
        rColumn.setOnEditCommit(event -> event.getRowValue().setR(event.getNewValue()));

        xColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().getX()));
        TableColumnSupport.configureDoubleColumn(xColumn, 2);
        xColumn.setOnEditCommit(event -> event.getRowValue().setX(event.getNewValue()));

        gColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().getG()));
        TableColumnSupport.configureDoubleColumn(gColumn, 6);
        gColumn.setOnEditCommit(event -> event.getRowValue().setG(event.getNewValue()));

        bColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().getB()));
        TableColumnSupport.configureDoubleColumn(bColumn, 6);
        bColumn.setOnEditCommit(event -> event.getRowValue().setB(event.getNewValue()));

        p0Column.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().getP0()));
        TableColumnSupport.configureDoubleColumn(p0Column);
        p0Column.setOnEditCommit(event -> event.getRowValue().setP0(event.getNewValue()));

        q0Column.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().getQ0()));
        TableColumnSupport.configureDoubleColumn(q0Column);
        q0Column.setOnEditCommit(event -> event.getRowValue().setQ0(event.getNewValue()));

        TableColumnSupport.configureNullableEditableDoubleColumn(minPColumn,
                boundaryLine -> generationDouble(boundaryLine, BoundaryLine.Generation::getMinP),
                (boundaryLine, value) -> boundaryLine.getGeneration().setMinP(value));
        TableColumnSupport.configureNullableEditableDoubleColumn(maxPColumn,
                boundaryLine -> generationDouble(boundaryLine, BoundaryLine.Generation::getMaxP),
                (boundaryLine, value) -> boundaryLine.getGeneration().setMaxP(value));
        TableColumnSupport.configureNullableEditableBooleanColumn(voltageRegulatorOnColumn,
                boundaryLine -> generationBoolean(boundaryLine, BoundaryLine.Generation::isVoltageRegulationOn),
                (boundaryLine, value) -> boundaryLine.getGeneration().setVoltageRegulationOn(value));
        TableColumnSupport.configureNullableEditableDoubleColumn(targetVColumn,
                boundaryLine -> generationDouble(boundaryLine, BoundaryLine.Generation::getTargetV),
                (boundaryLine, value) -> boundaryLine.getGeneration().setTargetV(value));
        TableColumnSupport.configureNullableEditableDoubleColumn(targetQColumn,
                boundaryLine -> generationDouble(boundaryLine, BoundaryLine.Generation::getTargetQ),
                (boundaryLine, value) -> boundaryLine.getGeneration().setTargetQ(value));

        pNetColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().getTerminal().getP()));
        TableColumnSupport.configureDoubleColumn(pNetColumn);

        qNetColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().getTerminal().getQ()));
        TableColumnSupport.configureDoubleColumn(qNetColumn);

        iNetColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().getTerminal().getI()));
        TableColumnSupport.configureDoubleColumn(iNetColumn);

        TableColumnSupport.configureNullableDoubleColumn(pBoundaryColumn, boundaryLine -> boundaryLine.getBoundary().getP());
        TableColumnSupport.configureNullableDoubleColumn(qBoundaryColumn, boundaryLine -> boundaryLine.getBoundary().getQ());
        TableColumnSupport.configureNullableDoubleColumn(iBoundaryColumn, boundaryLine -> boundaryLine.getBoundary().getI());
        TableColumnSupport.configureNullableDoubleColumn(vBoundaryColumn, boundaryLine -> boundaryLine.getBoundary().getV());
        TableColumnSupport.configureNullableDoubleColumn(angleBoundaryColumn, boundaryLine -> boundaryLine.getBoundary().getAngle());
        TableColumnSupport.configureOverloadColumn(patlIViolationColumn, BoundaryLinesController::isOverloaded);

        columnVisibilityToolbarController.configure(List.of(
                ColumnVisibilityToolbarController.ColumnGroup.of("network.columnGroup.parameters", false,
                        rColumn, xColumn, gColumn, bColumn),
                ColumnVisibilityToolbarController.ColumnGroup.of("network.columnGroup.solvedValues", true,
                        pNetColumn, qNetColumn, iNetColumn, patlIViolationColumn),
                ColumnVisibilityToolbarController.ColumnGroup.of("network.columnGroup.solvedValuesAtBoundary", false,
                        pBoundaryColumn, qBoundaryColumn, iBoundaryColumn, vBoundaryColumn, angleBoundaryColumn)));
    }

    // BoundaryLine has no isOverloaded() of its own (unlike Branch/ThreeWindingsTransformer) - it only exposes
    // FlowsLimitsHolder's current limits plus a single Terminal, so the permanent-limit check is done by hand here.
    private static boolean isOverloaded(BoundaryLine boundaryLine) {
        double i = boundaryLine.getTerminal().getI();
        return !Double.isNaN(i) && boundaryLine.getCurrentLimits().map(limits -> i > limits.getPermanentLimit()).orElse(false);
    }

    private static Double generationDouble(BoundaryLine boundaryLine, ToDoubleFunction<BoundaryLine.Generation> getter) {
        BoundaryLine.Generation generation = boundaryLine.getGeneration();
        return generation == null ? null : getter.applyAsDouble(generation);
    }

    private static Boolean generationBoolean(BoundaryLine boundaryLine, Predicate<BoundaryLine.Generation> getter) {
        BoundaryLine.Generation generation = boundaryLine.getGeneration();
        return generation == null ? null : getter.test(generation);
    }

    private Hyperlink tieLineLink(TieLine tieLine, BoundaryLine item) {
        Hyperlink link = new Hyperlink(tieLine.getNameOrId());
        link.getStyleClass().add("container-link");
        link.setOnAction(event -> {
            if (isEmbedded()) {
                mainModel.addNavigationEvent(NavigationEvent.create(NavigationType.SUBSTATIONS,
                        ContainerNavigationState.create(item.getTerminal().getVoltageLevel(), ContainerNavigationState.ContainerTab.TIE_LINES)));
            } else {
                mainModel.addNavigationEvent(ownNavigationEvent(item), false);
                mainModel.addNavigationEvent(NavigationEvent.create(NavigationType.NETWORK_TABLE_TIE_LINES, TieLineNavigationState.create(tieLine)));
            }
        });
        return link;
    }

    @Override
    TableView<BoundaryLine> tableView() {
        return boundaryLinesTableView;
    }

    @Override
    SearchBoxController searchBox() {
        return searchBoxController;
    }

    @Override
    EnumSet<NetworkSearch.Kind> searchKinds() {
        return EnumSet.of(NetworkSearch.Kind.SUBSTATION, NetworkSearch.Kind.VOLTAGE_LEVEL, NetworkSearch.Kind.BOUNDARY_LINE);
    }

    @Override
    Stream<BoundaryLine> networkItems(Network network) {
        return network.getBoundaryLineStream();
    }

    @Override
    List<VoltageLevel> voltageLevelsOf(BoundaryLine boundaryLine) {
        return List.of(boundaryLine.getTerminal().getVoltageLevel());
    }

    @Override
    Optional<BoundaryLine> asOwnEntity(Identifiable<?> match) {
        return match instanceof BoundaryLine boundaryLine ? Optional.of(boundaryLine) : Optional.empty();
    }

    @Override
    TableColumn<BoundaryLine, ?> nameColumn() {
        return nameColumn;
    }

    @Override
    TableColumn<BoundaryLine, ?> substationColumn() {
        return substationColumn;
    }

    @Override
    TableColumn<BoundaryLine, ?> voltageLevelColumn() {
        return voltageLevelColumn;
    }

    public void goToBoundaryLine(BoundaryLine boundaryLine) {
        goToItem(boundaryLine);
    }

    @Override
    NavigationEvent ownNavigationEvent(BoundaryLine boundaryLine) {
        return NavigationEvent.create(NavigationType.NETWORK_TABLE_BOUNDARY_LINES, BoundaryLineNavigationState.create(boundaryLine));
    }
}
