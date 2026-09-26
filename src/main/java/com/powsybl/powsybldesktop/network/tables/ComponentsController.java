/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.tables;

import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.powsybldesktop.MainModel;
import com.powsybl.powsybldesktop.navigation.ContainerNavigationState;
import com.powsybl.powsybldesktop.navigation.NavigationEvent;
import com.powsybl.powsybldesktop.navigation.NavigationType;
import com.powsybl.powsybldesktop.utils.AbstractDisposableController;
import com.powsybl.powsybldesktop.utils.Messages;
import com.powsybl.powsybldesktop.utils.TableAutoFitLimiter;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Flat table of the network's connected/synchronous components, one row per (connected component, synchronous
 * component) pair actually present among the network's buses. When a load flow has been run on the network, each
 * row is cross-referenced with its {@link LoadFlowResult.ComponentResult} (matched by component numbers) to show
 * the computation outcome for that component.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class ComponentsController extends AbstractDisposableController {

    @FXML
    public TableView<ComponentRow> componentsTableView;

    @FXML
    TableColumn<ComponentRow, Integer> connectedComponentColumn;
    @FXML
    TableColumn<ComponentRow, Integer> synchronousComponentColumn;
    @FXML
    TableColumn<ComponentRow, Integer> busCountColumn;
    @FXML
    TableColumn<ComponentRow, ComponentRow> statusColumn;
    @FXML
    TableColumn<ComponentRow, String> statusTextColumn;
    @FXML
    TableColumn<ComponentRow, Integer> iterationCountColumn;
    @FXML
    TableColumn<ComponentRow, ComponentRow> referenceBusIdColumn;
    @FXML
    TableColumn<ComponentRow, ComponentRow> slackBusesColumn;
    @FXML
    TableColumn<ComponentRow, Double> distributedActivePowerColumn;

    private final ObservableList<ComponentRow> componentsData = FXCollections.observableArrayList();

    private MainModel mainModel;
    List<ComponentRow> currentComponents = List.of();

    record ComponentKey(int connectedComponentNum, int synchronousComponentNum) {
    }

    record ComponentRow(ComponentKey key, int busCount, LoadFlowResult.ComponentResult componentResult) {
    }

    @FXML
    private void initialize() {
        SortedList<ComponentRow> sortedComponents = new SortedList<>(componentsData);
        sortedComponents.comparatorProperty().bind(componentsTableView.comparatorProperty());
        componentsTableView.setItems(sortedComponents);
        TableAutoFitLimiter.install(componentsTableView);

        connectedComponentColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().key().connectedComponentNum()));
        synchronousComponentColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().key().synchronousComponentNum()));
        busCountColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().busCount()));

        configureStatusColumn();
        statusTextColumn.setCellValueFactory(cellData -> new ReadOnlyStringWrapper(statusTextOf(cellData.getValue())));
        TableColumnSupport.configureNullableIntColumn(iterationCountColumn,
                row -> row.componentResult() == null ? null : row.componentResult().getIterationCount());
        configureReferenceBusIdColumn();
        configureSlackBusesColumn();
        TableColumnSupport.configureNullableDoubleColumn(distributedActivePowerColumn,
                row -> row.componentResult() == null ? null : row.componentResult().getDistributedActivePower());
    }

    private void configureStatusColumn() {
        statusColumn.setSortable(false);
        statusColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue()));
        statusColumn.setCellFactory(col -> new StatusTableCell());
    }

    private static final class StatusTableCell extends TableCell<ComponentRow, ComponentRow> {
        private final Region marker = new Region();
        private final Label label = new Label();
        private final HBox box = new HBox(6, marker, label);

        private StatusTableCell() {
            marker.getStyleClass().add("component-status");
            box.setAlignment(Pos.CENTER_LEFT);
        }

        @Override
        protected void updateItem(ComponentRow row, boolean empty) {
            super.updateItem(row, empty);
            LoadFlowResult.ComponentResult.Status status = row == null || row.componentResult() == null ? null : row.componentResult().getStatus();
            if (status == null) {
                setGraphic(null);
            } else {
                marker.getStyleClass().removeIf(styleClass -> styleClass.startsWith("component-status-"));
                marker.getStyleClass().add(statusStyleClass(status));
                label.setText(Messages.get(statusMessageKey(status)));
                setGraphic(box);
            }
        }
    }

    private static String statusStyleClass(LoadFlowResult.ComponentResult.Status status) {
        return switch (status) {
            case CONVERGED -> "component-status-converged";
            case NO_CALCULATION -> "component-status-no-calculation";
            case MAX_ITERATION_REACHED, FAILED -> "component-status-other";
        };
    }

    private static String statusMessageKey(LoadFlowResult.ComponentResult.Status status) {
        return switch (status) {
            case CONVERGED -> "components.status.converged";
            case MAX_ITERATION_REACHED -> "components.status.maxIterationReached";
            case FAILED -> "components.status.failed";
            case NO_CALCULATION -> "components.status.noCalculation";
        };
    }

    private static String statusTextOf(ComponentRow row) {
        return row.componentResult() == null ? null : row.componentResult().getStatusText();
    }

    private void configureReferenceBusIdColumn() {
        referenceBusIdColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue()));
        referenceBusIdColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(ComponentRow row, boolean empty) {
                super.updateItem(row, empty);
                String referenceBusId = row == null || row.componentResult() == null ? null : row.componentResult().getReferenceBusId();
                setGraphic(empty ? null : busIdNode(referenceBusId));
            }
        });
    }

    private void configureSlackBusesColumn() {
        slackBusesColumn.setSortable(false);
        slackBusesColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue()));
        slackBusesColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(ComponentRow row, boolean empty) {
                super.updateItem(row, empty);
                if (empty || row == null || row.componentResult() == null) {
                    setGraphic(null);
                } else {
                    setGraphic(slackBusesBox(row.componentResult().getSlackBusResults()));
                }
            }
        });
    }

    private VBox slackBusesBox(List<LoadFlowResult.SlackBusResult> slackBusResults) {
        VBox box = new VBox();
        box.setAlignment(Pos.CENTER_LEFT);
        for (LoadFlowResult.SlackBusResult slackBusResult : slackBusResults) {
            Label mismatch = new Label(String.format(Locale.ROOT, ": %.3f", slackBusResult.getActivePowerMismatch()));
            box.getChildren().add(new HBox(busIdNode(slackBusResult.getId()), mismatch));
        }
        return box;
    }

    private Node busIdNode(String busId) {
        if (busId == null) {
            return null;
        }
        Network network = mainModel.getNetwork();
        Bus bus = network == null ? null : network.getBusView().getBus(busId);
        if (bus == null) {
            return new Label(busId);
        }
        Hyperlink link = new Hyperlink(busId);
        link.getStyleClass().add("container-link");
        link.setOnAction(event -> mainModel.addNavigationEvent(
                NavigationEvent.create(NavigationType.SUBSTATIONS, ContainerNavigationState.create(bus.getVoltageLevel()))));
        return link;
    }

    public void setMainModel(MainModel mainModel) {
        this.mainModel = Objects.requireNonNull(mainModel);
        updateComponents();
        listenerManager.listen(this.mainModel.networkProperty(), (observable, oldValue, newValue) -> updateComponents());
        listenerManager.listen(this.mainModel.updateProperty(), (observable, oldValue, newValue) -> updateComponents());
    }

    private void updateComponents() {
        Network network = mainModel.getNetwork();
        if (network == null) {
            currentComponents = List.of();
        } else {
            LoadFlowResult loadFlowResult = mainModel.getLoadFlowResult(network);
            Map<ComponentKey, Long> busCountsByComponent = network.getBusView().getBusStream()
                    .filter(bus -> bus.getConnectedComponent() != null && bus.getSynchronousComponent() != null)
                    .collect(Collectors.groupingBy(
                            bus -> new ComponentKey(bus.getConnectedComponent().getNum(), bus.getSynchronousComponent().getNum()),
                            Collectors.counting()));
            currentComponents = busCountsByComponent.entrySet().stream()
                    .map(entry -> new ComponentRow(entry.getKey(), entry.getValue().intValue(), findComponentResult(loadFlowResult, entry.getKey())))
                    .sorted(Comparator.<ComponentRow>comparingInt(row -> row.key().connectedComponentNum())
                            .thenComparingInt(row -> row.key().synchronousComponentNum()))
                    .toList();
        }
        componentsData.setAll(currentComponents);
    }

    private static LoadFlowResult.ComponentResult findComponentResult(LoadFlowResult loadFlowResult, ComponentKey key) {
        if (loadFlowResult == null) {
            return null;
        }
        return loadFlowResult.getComponentResults().stream()
                .filter(result -> result.getConnectedComponentNum() == key.connectedComponentNum()
                        && result.getSynchronousComponentNum() == key.synchronousComponentNum())
                .findFirst()
                .orElse(null);
    }
}
