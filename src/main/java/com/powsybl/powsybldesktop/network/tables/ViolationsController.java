/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.tables;

import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Network;
import com.powsybl.powsybldesktop.MainModel;
import com.powsybl.powsybldesktop.SceneModel;
import com.powsybl.powsybldesktop.SceneView;
import com.powsybl.powsybldesktop.parameters.GuiParameters;
import com.powsybl.powsybldesktop.utils.AbstractDisposableController;
import com.powsybl.powsybldesktop.utils.Messages;
import com.powsybl.powsybldesktop.utils.TableAutoFitLimiter;
import com.powsybl.powsybldesktop.violations.FlowViolation;
import com.powsybl.powsybldesktop.violations.NetworkViolations;
import com.powsybl.powsybldesktop.violations.VoltageViolation;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.Tab;
import javafx.scene.control.TableView;

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
    Tab voltageTab;
    @FXML
    public TableView<VoltageViolation> voltageTableView;

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
        new ViolationColumns<>(flowTableView, () -> sceneModel, this::guiParameters)
                .subject("securityAnalysisResults.column.subject", row -> row.worst().violation())
                .equipmentType(FlowRow::equipment)
                .flowViolation(FlowRow::worst)
                .loading("desktop.common.column.loading", FlowRow::worst,
                        (window, row) -> FlowViolationsDialog.show(window, row.equipment(), row.violations(), guiParameters()));

        SortedList<VoltageViolation> sortedVoltages = new SortedList<>(voltageData);
        sortedVoltages.comparatorProperty().bind(voltageTableView.comparatorProperty());
        voltageTableView.setItems(sortedVoltages);
        TableAutoFitLimiter.install(voltageTableView);
        new ViolationColumns<>(voltageTableView, () -> sceneModel, this::guiParameters)
                .subject("desktop.common.equipment.voltageLevel", VoltageViolation::violation)
                .voltageViolation(v -> v);
    }

    @Override
    public void setSceneModel(SceneModel sceneModel) {
        this.sceneModel = Objects.requireNonNull(sceneModel);
        this.mainModel = sceneModel.getMainModel();
        updateViolations();
        listenerManager.listen(sceneModel.networkProperty(), (_, _, _) -> updateViolations());
        listenerManager.listen(this.mainModel.updateProperty(), (_, _, _) -> updateViolations());
        // for the decimal places, read by the cells on every render
        listenerManager.listen(this.mainModel.getParametersModel().guiParametersRevisionProperty(), (_, _, _) -> {
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
    static Identifiable<?> shown(Network network, String id) {
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
}
