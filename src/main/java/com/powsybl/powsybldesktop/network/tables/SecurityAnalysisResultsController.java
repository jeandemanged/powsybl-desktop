/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.tables;

import com.powsybl.contingency.violations.LimitViolation;
import com.powsybl.contingency.violations.LimitViolationType;
import com.powsybl.iidm.network.Container;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.powsybldesktop.MainModel;
import com.powsybl.powsybldesktop.contingency.ContingencyNames;
import com.powsybl.powsybldesktop.navigation.ContainerNavigationState;
import com.powsybl.powsybldesktop.navigation.NavigationEvent;
import com.powsybl.powsybldesktop.navigation.NavigationType;
import com.powsybl.powsybldesktop.network.search.NetworkSearch;
import com.powsybl.powsybldesktop.utils.AbstractDisposableController;
import com.powsybl.powsybldesktop.utils.Messages;
import com.powsybl.powsybldesktop.utils.TableAutoFitLimiter;
import com.powsybl.security.PostContingencyComputationStatus;
import com.powsybl.security.SecurityAnalysisResult;
import com.powsybl.security.results.PostContingencyResult;
import com.powsybl.security.results.PreContingencyResult;
import javafx.beans.property.ReadOnlyObjectWrapper;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Flat table of the network's security analysis result: pre-contingency limit violations first, then
 * post-contingency limit violations grouped by contingency. Only {@link LimitViolation}s are shown -
 * {@code actionsTaken} is out of scope. A non-converged pre-contingency state is reported as a single row and
 * nothing else, since post-contingency results are meaningless without a valid base case. A post-contingency
 * whose computation didn't converge is reported as a single row too, without any violation.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class SecurityAnalysisResultsController extends AbstractDisposableController {

    @FXML
    public TableView<ResultRow> resultsTableView;

    @FXML
    TableColumn<ResultRow, String> contingencyColumn;
    @FXML
    TableColumn<ResultRow, ResultRow> statusColumn;
    @FXML
    TableColumn<ResultRow, ResultRow> subjectColumn;
    @FXML
    TableColumn<ResultRow, LimitViolationType> limitTypeColumn;
    @FXML
    TableColumn<ResultRow, Integer> sideColumn;
    @FXML
    TableColumn<ResultRow, Double> valueColumn;
    @FXML
    TableColumn<ResultRow, Double> limitColumn;
    @FXML
    TableColumn<ResultRow, Double> limitReductionColumn;
    @FXML
    TableColumn<ResultRow, String> limitNameColumn;
    @FXML
    TableColumn<ResultRow, Integer> acceptableDurationColumn;
    @FXML
    TableColumn<ResultRow, String> operationalLimitsGroupIdColumn;

    private final ObservableList<ResultRow> resultsData = FXCollections.observableArrayList();

    private MainModel mainModel;

    private enum RowKind { NOT_CONVERGED, VIOLATION }

    record ResultRow(RowKind kind, String contingencyName, String statusMessageKey, LimitViolation violation) {
        static ResultRow notConverged(String contingencyName, String statusMessageKey) {
            return new ResultRow(RowKind.NOT_CONVERGED, contingencyName, statusMessageKey, null);
        }

        static ResultRow violation(String contingencyName, LimitViolation violation) {
            return new ResultRow(RowKind.VIOLATION, contingencyName, null, violation);
        }

        String contingencyLabel() {
            return contingencyName == null ? Messages.get("securityAnalysisResults.preContingency") : contingencyName;
        }
    }

    @FXML
    private void initialize() {
        SortedList<ResultRow> sortedResults = new SortedList<>(resultsData);
        sortedResults.comparatorProperty().bind(resultsTableView.comparatorProperty());
        resultsTableView.setItems(sortedResults);
        TableAutoFitLimiter.install(resultsTableView);

        contingencyColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().contingencyLabel()));

        configureStatusColumn();
        configureSubjectColumn();
        TableColumnSupport.configureNullableColumn(limitTypeColumn, row -> row.violation() == null ? null : row.violation().getLimitType());
        TableColumnSupport.configureNullableIntColumn(sideColumn, row -> row.violation() == null || row.violation().getSide() == null ? null : row.violation().getSide().getNum());
        TableColumnSupport.configureNullableDoubleColumn(valueColumn, row -> row.violation() == null ? null : row.violation().getValue());
        TableColumnSupport.configureNullableDoubleColumn(limitColumn, row -> row.violation() == null ? null : row.violation().getLimit());
        TableColumnSupport.configureNullableDoubleColumn(limitReductionColumn, row -> row.violation() == null ? null : row.violation().getLimitReduction());
        TableColumnSupport.configureNullableColumn(limitNameColumn, row -> row.violation() == null ? null : row.violation().getLimitName());
        TableColumnSupport.configureNullableIntColumn(acceptableDurationColumn, row -> row.violation() == null ? null : row.violation().getAcceptableDuration());
        TableColumnSupport.configureNullableColumn(operationalLimitsGroupIdColumn, row -> row.violation() == null ? null : row.violation().getOperationalLimitsGroupId());
    }

    private void configureStatusColumn() {
        statusColumn.setSortable(false);
        statusColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue()));
        statusColumn.setCellFactory(col -> new StatusTableCell());
    }

    private static final class StatusTableCell extends TableCell<ResultRow, ResultRow> {
        private final Region marker = new Region();
        private final Label label = new Label();
        private final HBox box = new HBox(6, marker, label);

        private StatusTableCell() {
            marker.getStyleClass().addAll("component-status", "component-status-other");
            box.setAlignment(Pos.CENTER_LEFT);
        }

        @Override
        protected void updateItem(ResultRow row, boolean empty) {
            super.updateItem(row, empty);
            if (row == null || row.kind() != RowKind.NOT_CONVERGED) {
                setGraphic(null);
            } else {
                label.setText(Messages.get(row.statusMessageKey()));
                setGraphic(box);
            }
        }
    }

    private void configureSubjectColumn() {
        subjectColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue()));
        subjectColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(ResultRow row, boolean empty) {
                super.updateItem(row, empty);
                setGraphic(empty || row == null || row.violation() == null ? null : subjectNode(row.violation()));
            }
        });
    }

    private Node subjectNode(LimitViolation violation) {
        Network network = mainModel.getNetwork();
        Identifiable<?> identifiable = network == null ? null : network.getNetwork().getIdentifiable(violation.getSubjectId());
        // TODO: improve in powsybl-open-loadflow - LimitViolationManager builds its LimitViolations without a
        // subjectName, so the name is recovered from the current network instead, falling back to subjectName then
        // subjectId when the subject is gone from the network (or isn't an identifiable, e.g. a voltage angle limit).
        String subjectName = identifiable != null ? identifiable.getNameOrId()
                : Objects.requireNonNullElse(violation.getSubjectName(), violation.getSubjectId());
        Container<?> container = null;
        if (identifiable != null) {
            try {
                container = NetworkSearch.containerOf(identifiable);
            } catch (IllegalArgumentException e) {
                container = null;
            }
        }
        if (container == null) {
            return new Label(subjectName);
        }
        Hyperlink link = new Hyperlink(subjectName);
        link.getStyleClass().add("container-link");
        Container<?> target = container;
        link.setOnAction(event -> mainModel.getNavigationHistory().navigate(
                NavigationEvent.create(NavigationType.SUBSTATIONS, ContainerNavigationState.create(target))));
        return link;
    }

    public void setMainModel(MainModel mainModel) {
        this.mainModel = Objects.requireNonNull(mainModel);
        updateResults();
        listenerManager.listen(this.mainModel.networkProperty(), (observable, oldValue, newValue) -> updateResults());
        listenerManager.listen(this.mainModel.updateProperty(), (observable, oldValue, newValue) -> updateResults());
    }

    private void updateResults() {
        Network network = mainModel.getNetwork();
        SecurityAnalysisResult result = network == null ? null : mainModel.getStudy(network).getSecurityAnalysisResult();
        resultsData.setAll(result == null ? List.of() : buildRows(result, network));
    }

    private static List<ResultRow> buildRows(SecurityAnalysisResult result, Network network) {
        List<ResultRow> rows = new ArrayList<>();
        PreContingencyResult preContingencyResult = result.getPreContingencyResult();
        if (preContingencyResult.getStatus() != LoadFlowResult.ComponentResult.Status.CONVERGED) {
            rows.add(ResultRow.notConverged(null, preContingencyStatusMessageKey(preContingencyResult.getStatus())));
            return rows;
        }
        for (LimitViolation violation : preContingencyResult.getLimitViolationsResult().getLimitViolations()) {
            rows.add(ResultRow.violation(null, violation));
        }
        for (PostContingencyResult postContingencyResult : result.getPostContingencyResults()) {
            String contingencyName = ContingencyNames.displayName(postContingencyResult.getContingency(), network);
            if (postContingencyResult.getStatus() != PostContingencyComputationStatus.CONVERGED) {
                rows.add(ResultRow.notConverged(contingencyName, postContingencyStatusMessageKey(postContingencyResult.getStatus())));
            } else {
                for (LimitViolation violation : postContingencyResult.getLimitViolationsResult().getLimitViolations()) {
                    rows.add(ResultRow.violation(contingencyName, violation));
                }
            }
        }
        return rows;
    }

    private static String preContingencyStatusMessageKey(LoadFlowResult.ComponentResult.Status status) {
        return switch (status) {
            case CONVERGED -> "components.status.converged";
            case MAX_ITERATION_REACHED -> "components.status.maxIterationReached";
            case FAILED -> "components.status.failed";
            case NO_CALCULATION -> "components.status.noCalculation";
        };
    }

    private static String postContingencyStatusMessageKey(PostContingencyComputationStatus status) {
        return switch (status) {
            case CONVERGED -> "components.status.converged";
            case MAX_ITERATION_REACHED -> "securityAnalysisResults.status.maxIterationReached";
            case SOLVER_FAILED -> "securityAnalysisResults.status.solverFailed";
            case FAILED -> "securityAnalysisResults.status.failed";
            case NO_IMPACT -> "securityAnalysisResults.status.noImpact";
        };
    }
}
