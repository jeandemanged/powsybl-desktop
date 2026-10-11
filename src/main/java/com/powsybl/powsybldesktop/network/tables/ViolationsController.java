/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.tables;

import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.IdentifiableType;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.powsybldesktop.MainModel;
import com.powsybl.powsybldesktop.SceneModel;
import com.powsybl.powsybldesktop.SceneView;
import com.powsybl.powsybldesktop.parameters.GuiParameters;
import com.powsybl.powsybldesktop.utils.AbstractDisposableController;
import com.powsybl.powsybldesktop.utils.Messages;
import com.powsybl.powsybldesktop.utils.TableAutoFitLimiter;
import com.powsybl.powsybldesktop.violations.FlowViolation;
import com.powsybl.powsybldesktop.violations.Loading;
import com.powsybl.powsybldesktop.violations.NetworkViolations;
import com.powsybl.powsybldesktop.violations.VoltageViolation;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.Tab;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.util.StringConverter;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * The network's limit violations, as cached by its study (see {@link NetworkViolations}): flow violations, one row
 * per equipment showing its most severe violation, all of them in {@link FlowViolationsDialog}; and voltage
 * violations, one row per bus of the bus view. Both are sorted most severe first. When a subnetwork is selected, only
 * its own equipment's violations are shown.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class ViolationsController extends AbstractDisposableController implements SceneView {

    @FXML
    Tab flowTab;
    @FXML
    public TableView<FlowRow> flowTableView;
    @FXML
    TableColumn<FlowRow, FlowRow> flowSubjectColumn;
    @FXML
    TableColumn<FlowRow, String> flowTypeColumn;
    @FXML
    TableColumn<FlowRow, Integer> flowSideColumn;
    @FXML
    TableColumn<FlowRow, String> flowLimitTypeColumn;
    @FXML
    TableColumn<FlowRow, String> flowLimitNameColumn;
    @FXML
    TableColumn<FlowRow, Integer> flowAcceptableDurationColumn;
    @FXML
    TableColumn<FlowRow, Double> flowValueColumn;
    @FXML
    TableColumn<FlowRow, Double> flowPermanentLimitColumn;
    @FXML
    TableColumn<FlowRow, Double> flowLimitReductionColumn;
    @FXML
    TableColumn<FlowRow, Loading> flowLoadingColumn;

    @FXML
    Tab voltageTab;
    @FXML
    public TableView<VoltageViolation> voltageTableView;
    @FXML
    TableColumn<VoltageViolation, VoltageViolation> voltageSubjectColumn;
    @FXML
    TableColumn<VoltageViolation, String> voltageBusColumn;
    @FXML
    TableColumn<VoltageViolation, String> voltageLimitTypeColumn;
    @FXML
    TableColumn<VoltageViolation, Double> voltageValueColumn;
    @FXML
    TableColumn<VoltageViolation, Double> voltageLimitColumn;
    @FXML
    TableColumn<VoltageViolation, Double> voltageNominalColumn;
    @FXML
    TableColumn<VoltageViolation, Double> voltagePercentOfNominalColumn;
    @FXML
    TableColumn<VoltageViolation, VoltageRange> voltageRangeColumn;

    private final ObservableList<FlowRow> flowData = FXCollections.observableArrayList();
    private final ObservableList<VoltageViolation> voltageData = FXCollections.observableArrayList();

    private SceneModel sceneModel;
    private MainModel mainModel;

    /**
     * An equipment's flow violations, most severe first.
     */
    record FlowRow(Identifiable<?> equipment, List<FlowViolation> violations) {
        FlowViolation worst() {
            return violations.getFirst();
        }
    }

    @FXML
    private void initialize() {
        SortedList<FlowRow> sortedFlows = new SortedList<>(flowData);
        sortedFlows.comparatorProperty().bind(flowTableView.comparatorProperty());
        flowTableView.setItems(sortedFlows);
        TableAutoFitLimiter.install(flowTableView);

        flowSubjectColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue()));
        flowSubjectColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(FlowRow row, boolean empty) {
                super.updateItem(row, empty);
                setGraphic(empty || row == null ? null : SecurityAnalysisResultsController.subjectNode(sceneModel, row.worst().violation()));
            }
        });
        flowSubjectColumn.setComparator(Comparator.comparing(row -> row.equipment().getNameOrId()));
        TableColumnSupport.configureNullableColumn(flowTypeColumn, row -> equipmentTypeText(row.equipment().getType()));
        TableColumnSupport.configureNullableIntColumn(flowSideColumn, row -> row.worst().violation().getSide() == null
                ? null : row.worst().violation().getSide().getNum());
        TableColumnSupport.configureNullableColumn(flowLimitTypeColumn, row -> TableColumnSupport.limitViolationTypeText(row.worst().violation().getLimitType()));
        TableColumnSupport.configureNullableColumn(flowLimitNameColumn, row -> row.worst().violation().getLimitName());
        // a permanent limit violation's Integer.MAX_VALUE shown as "-", sorted last
        TableColumnSupport.configureNullableIntColumn(flowAcceptableDurationColumn, row -> row.worst().violation().getAcceptableDuration() == Integer.MAX_VALUE
                ? null : row.worst().violation().getAcceptableDuration());
        TableColumnSupport.configureNullableDoubleColumn(flowValueColumn, row -> row.worst().violation().getValue(), this::flowFormat);
        TableColumnSupport.configureNullableDoubleColumn(flowPermanentLimitColumn, row -> row.worst().permanentLimit(), this::flowFormat);
        TableColumnSupport.configureNullableDoubleColumn(flowLimitReductionColumn, row -> row.worst().violation().getLimitReduction(),
                format(GuiParameters.Quantity.LIMIT_REDUCTION));
        TableColumnSupport.configureLoadingColumn(flowLoadingColumn, row -> Loading.of(row.worst()), this::guiParameters,
                (window, row) -> FlowViolationsDialog.show(window, row.equipment(), row.violations(), guiParameters()));

        SortedList<VoltageViolation> sortedVoltages = new SortedList<>(voltageData);
        sortedVoltages.comparatorProperty().bind(voltageTableView.comparatorProperty());
        voltageTableView.setItems(sortedVoltages);
        TableAutoFitLimiter.install(voltageTableView);

        voltageSubjectColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue()));
        voltageSubjectColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(VoltageViolation violation, boolean empty) {
                super.updateItem(violation, empty);
                setGraphic(empty || violation == null ? null : SecurityAnalysisResultsController.subjectNode(sceneModel, violation.violation()));
            }
        });
        voltageSubjectColumn.setComparator(Comparator.comparing(v -> v.violation().getSubjectId()));
        TableColumnSupport.configureNullableColumn(voltageBusColumn, VoltageViolation::busId);
        TableColumnSupport.configureNullableColumn(voltageLimitTypeColumn, v -> TableColumnSupport.limitViolationTypeText(v.violation().getLimitType()));
        TableColumnSupport.configureNullableDoubleColumn(voltageValueColumn, v -> v.violation().getValue(), format(GuiParameters.Quantity.VOLTAGE));
        TableColumnSupport.configureNullableDoubleColumn(voltageLimitColumn, v -> v.violation().getLimit(), format(GuiParameters.Quantity.VOLTAGE));
        TableColumnSupport.configureNullableDoubleColumn(voltageNominalColumn, VoltageViolation::nominalV, format(GuiParameters.Quantity.VOLTAGE));
        TableColumnSupport.configureNullableDoubleColumn(voltagePercentOfNominalColumn, VoltageViolation::percentOfNominal,
                format(GuiParameters.Quantity.PERCENTAGE));
        TableColumnSupport.configureVoltageRangeColumn(voltageRangeColumn, v -> {
            Network network = sceneModel.getNetwork();
            VoltageLevel voltageLevel = network == null ? null : network.getNetwork().getVoltageLevel(v.violation().getSubjectId());
            return voltageLevel == null ? null : VoltageRange.of(v.violation().getValue(), voltageLevel, v);
        }, this::guiParameters);
    }

    private static String equipmentTypeText(IdentifiableType type) {
        return switch (type) {
            case LINE -> Messages.get("desktop.common.equipment.line");
            case TIE_LINE -> Messages.get("desktop.common.equipment.tieLine");
            case TWO_WINDINGS_TRANSFORMER, THREE_WINDINGS_TRANSFORMER -> Messages.get("desktop.common.equipment.transformer");
            case BOUNDARY_LINE -> Messages.get("desktop.common.equipment.boundaryLine");
            default -> type.name();
        };
    }

    @Override
    public void setSceneModel(SceneModel sceneModel) {
        this.sceneModel = Objects.requireNonNull(sceneModel);
        this.mainModel = sceneModel.getMainModel();
        updateViolations();
        listenerManager.listen(sceneModel.networkProperty(), (observable, oldValue, newValue) -> updateViolations());
        listenerManager.listen(this.mainModel.updateProperty(), (observable, oldValue, newValue) -> updateViolations());
        // for the decimal places, read by the cells on every render
        listenerManager.listen(this.mainModel.getParametersModel().guiParametersRevisionProperty(), (observable, oldValue, newValue) -> {
            flowTableView.refresh();
            voltageTableView.refresh();
        });
    }

    private void updateViolations() {
        Network network = sceneModel.getNetwork();
        NetworkViolations violations = network == null ? NetworkViolations.EMPTY : mainModel.getStudy(network).getViolations();
        flowData.setAll(violations.getFlowViolations().entrySet().stream()
                .map(entry -> {
                    Identifiable<?> equipment = shown(network, entry.getKey());
                    return equipment == null ? null : new FlowRow(equipment, entry.getValue());
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(FlowRow::worst))
                .toList());
        voltageData.setAll(violations.getVoltageViolations().stream()
                .filter(v -> shown(network, v.violation().getSubjectId()) != null)
                .sorted()
                .toList());
        flowTab.setText(Messages.get("violations.tab.flow", flowData.size()));
        voltageTab.setText(Messages.get("violations.tab.voltage", voltageData.size()));
    }

    // null if gone from the network (moved away by a subnetwork detach), or not part of the selected subnetwork
    private static Identifiable<?> shown(Network network, String id) {
        Identifiable<?> identifiable = network.getNetwork().getIdentifiable(id);
        if (identifiable == null || network != network.getNetwork() && identifiable.getParentNetwork() != network) {
            return null;
        }
        return identifiable;
    }

    // read fresh: a parameters reset/import replaces the GuiParameters instance
    private GuiParameters guiParameters() {
        return mainModel.getParametersModel().getGuiParameters();
    }

    private StringConverter<Double> flowFormat(FlowRow row) {
        return FlowViolationsDialog.format(row.worst(), guiParameters());
    }

    private StringConverter<Double> format(GuiParameters.Quantity quantity) {
        return TableColumnSupport.doubleFormat(this::guiParameters, quantity);
    }
}
