/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.tables;

import com.powsybl.contingency.ContingencyContext;
import com.powsybl.contingency.violations.LimitViolation;
import com.powsybl.iidm.network.BoundaryLineFilter;
import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.BusbarSection;
import com.powsybl.iidm.network.Container;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.LimitType;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.ThreeSides;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.powsybldesktop.MainModel;
import com.powsybl.powsybldesktop.SceneModel;
import com.powsybl.powsybldesktop.SceneView;
import com.powsybl.powsybldesktop.contingency.ContingencyNames;
import com.powsybl.powsybldesktop.navigation.ContainerNavigationState;
import com.powsybl.powsybldesktop.navigation.NavigationEvent;
import com.powsybl.powsybldesktop.navigation.NavigationType;
import com.powsybl.powsybldesktop.network.search.NetworkSearch;
import com.powsybl.powsybldesktop.parameters.GuiParameters;
import com.powsybl.powsybldesktop.utils.AbstractDisposableController;
import com.powsybl.powsybldesktop.utils.Messages;
import com.powsybl.powsybldesktop.utils.TableAutoFitLimiter;
import com.powsybl.powsybldesktop.violations.FlowViolation;
import com.powsybl.powsybldesktop.violations.Loading;
import com.powsybl.powsybldesktop.violations.NetworkViolations;
import com.powsybl.powsybldesktop.violations.VoltageViolation;
import com.powsybl.security.PostContingencyComputationStatus;
import com.powsybl.security.SecurityAnalysisResult;
import com.powsybl.security.monitor.StateMonitor;
import com.powsybl.security.results.BranchResult;
import com.powsybl.security.results.BusResult;
import com.powsybl.security.results.NetworkResult;
import com.powsybl.security.results.PostContingencyResult;
import com.powsybl.security.results.PreContingencyResult;
import com.powsybl.security.results.ThreeWindingsTransformerResult;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The network's security analysis result, like the Violations view: its pre- and post-contingency states with their
 * computation status and violation counts; flow violations, one row per state and equipment showing its most severe
 * violation, and one row per equipment showing its most severe violation over all states, all of them in
 * {@link FlowViolationsDialog} along the pre-contingency ones; and voltage violations, one row per state and bus. All
 * sorted most severe first, the pre-contingency rows always first. Post-contingency rows also show the
 * pre-contingency value and loading. Voltage angle, short-circuit current and other limit violations, and {@code actionsTaken},
 * are out of scope. Post-contingency states aren't computed when the pre-contingency state doesn't converge, and a
 * state that doesn't converge has no violations. When a subnetwork is selected, only its own equipment's violations
 * are shown.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class SecurityAnalysisResultsController extends AbstractDisposableController implements SceneView {

    @FXML
    Tab contingenciesTab;
    @FXML
    public TableView<ContingencyRow> contingenciesTableView;
    @FXML
    Tab flowByContingencyTab;
    @FXML
    public TableView<ContingencyFlowRow> flowByContingencyTableView;
    @FXML
    Tab flowByEquipmentTab;
    @FXML
    public TableView<EquipmentFlowRow> flowByEquipmentTableView;
    @FXML
    Tab voltageTab;
    @FXML
    public TableView<VoltageRow> voltageTableView;

    private final ObservableList<ContingencyRow> contingenciesData = FXCollections.observableArrayList();
    private final ObservableList<ContingencyFlowRow> flowByContingencyData = FXCollections.observableArrayList();
    private final ObservableList<EquipmentFlowRow> flowByEquipmentData = FXCollections.observableArrayList();
    private final ObservableList<VoltageRow> voltageData = FXCollections.observableArrayList();
    // by equipment id, shown along the post-contingency violations
    private final Map<String, ContingencyFlowRow> preContingencyFlows = new HashMap<>();
    private NetworkResult preContingencyNetworkResult = NetworkResult.empty();
    private Map<String, Double> preContingencyVoltages = Map.of();
    // by bus of the bus view
    private final Map<String, VoltageViolation> preContingencyVoltageViolations = new HashMap<>();

    private SceneModel sceneModel;
    private MainModel mainModel;

    /**
     * The pre-contingency state or a post-contingency one, and its computation status. A failed state has no
     * violations.
     */
    record State(String name, boolean preContingency, String statusMessageKey, boolean failed) {
    }

    /**
     * A state's flow violations of an equipment, most severe first.
     */
    record ContingencyFlowRow(State state, Identifiable<?> equipment, List<FlowViolation> violations) {
        FlowViolation worst() {
            return violations.getFirst();
        }
    }

    /**
     * An equipment's flow violations of all states, most severe first.
     */
    record EquipmentFlowRow(Identifiable<?> equipment, List<ContingencyFlowRow> rows) {
        ContingencyFlowRow worstRow() {
            return rows.getFirst();
        }

        int contingencyCount() {
            return (int) rows.stream().filter(row -> !row.state().preContingency()).count();
        }
    }

    record VoltageRow(State state, VoltageViolation violation) {
    }

    /**
     * A state, its violation counts (equipment with flow violations, buses with voltage violations) and its most
     * severe flow violation's row, null if none.
     */
    record ContingencyRow(State state, int flowCount, int voltageCount, ContingencyFlowRow worstFlow) {
        FlowViolation worst() {
            return worstFlow == null ? null : worstFlow.worst();
        }
    }

    @FXML
    private void initialize() {
        install(contingenciesTableView, contingenciesData, row -> row.state().preContingency());
        ViolationColumns<ContingencyRow> contingencyColumns = new ViolationColumns<>(contingenciesTableView, () -> sceneModel, this::guiParameters)
                .text("contingency", "securityAnalysisResults.column.contingency", row -> row.state().name());
        configureStatusColumn(contingencyColumns.add("status", "desktop.common.column.status", 180, null));
        contingencyColumns
                .count("flowCount", "securityAnalysisResults.column.flowViolations", ContingencyRow::flowCount)
                .count("voltageCount", "securityAnalysisResults.column.voltageViolations", ContingencyRow::voltageCount)
                .subject("securityAnalysisResults.column.worstFlowViolation", row -> row.worst() == null ? null : row.worst().violation())
                .loading("securityAnalysisResults.column.worstLoading", ContingencyRow::worst,
                        (window, row) -> showFlowViolations(window, row.worstFlow()));

        install(flowByContingencyTableView, flowByContingencyData, row -> row.state().preContingency());
        new ViolationColumns<>(flowByContingencyTableView, () -> sceneModel, this::guiParameters)
                .text("contingency", "securityAnalysisResults.column.contingency", row -> row.state().name())
                .subject("securityAnalysisResults.column.subject", row -> row.worst().violation())
                .equipmentType(ContingencyFlowRow::equipment)
                .flowViolation(ContingencyFlowRow::worst)
                .loading("desktop.common.column.loading", ContingencyFlowRow::worst, this::showFlowViolations)
                .flowValue("preContingencyValue", "securityAnalysisResults.column.preContingencyValue", this::preContingencyValue,
                        ContingencyFlowRow::worst)
                .loading("preContingencyLoading", "securityAnalysisResults.column.preContingencyLoading", this::preContingencyLoading, null)
                .moveBefore("preContingencyValue", "value")
                .moveBefore("preContingencyLoading", "loading");

        install(flowByEquipmentTableView, flowByEquipmentData, row -> false);
        new ViolationColumns<>(flowByEquipmentTableView, () -> sceneModel, this::guiParameters)
                .subject("securityAnalysisResults.column.subject", row -> row.worstRow().worst().violation())
                .equipmentType(EquipmentFlowRow::equipment)
                .count("contingencyCount", "securityAnalysisResults.column.contingencyCount", EquipmentFlowRow::contingencyCount)
                .text("contingency", "securityAnalysisResults.column.worstContingency", row -> row.worstRow().state().name())
                .flowViolation(row -> row.worstRow().worst())
                .loading("desktop.common.column.loading", row -> row.worstRow().worst(), this::showFlowViolations)
                .flowValue("preContingencyValue", "securityAnalysisResults.column.preContingencyValue",
                        row -> preContingencyValue(row.worstRow()), row -> row.worstRow().worst())
                .loading("preContingencyLoading", "securityAnalysisResults.column.preContingencyLoading",
                        row -> preContingencyLoading(row.worstRow()), null)
                .moveBefore("preContingencyValue", "value")
                .moveBefore("preContingencyLoading", "loading");

        install(voltageTableView, voltageData, row -> row.state().preContingency());
        ViolationColumns<VoltageRow> voltageColumns = new ViolationColumns<>(voltageTableView, () -> sceneModel, this::guiParameters);
        voltageColumns
                .text("contingency", "securityAnalysisResults.column.contingency", row -> row.state().name())
                .subject("desktop.common.equipment.voltageLevel", row -> row.violation().violation())
                .voltageViolation(VoltageRow::violation)
                .voltageValue("preContingencyValue", "securityAnalysisResults.column.preContingencyValue", this::preContingencyVoltage)
                .voltageRange("preContingencyVoltageRange", "securityAnalysisResults.column.preContingencyVoltageRange", row -> {
                    Double v = preContingencyVoltage(row);
                    VoltageLevel voltageLevel = v == null ? null : voltageColumns.voltageLevel(row.violation());
                    return voltageLevel == null ? null : VoltageRange.of(v, voltageLevel, preContingencyVoltageViolations.get(row.violation().busId()));
                })
                .moveBefore("preContingencyValue", "value")
                .moveBefore("preContingencyVoltageRange", "voltageRange");
    }

    // the pre-contingency rows first, whichever the columns the table is sorted by
    private static <S> void install(TableView<S> table, ObservableList<S> data, Predicate<S> preContingency) {
        SortedList<S> sorted = new SortedList<>(data);
        sorted.comparatorProperty().bind(Bindings.createObjectBinding(() -> Comparator.comparing((S row) -> !preContingency.test(row))
                .thenComparing(Objects.requireNonNullElse(table.getComparator(), (a, b) -> 0)), table.comparatorProperty()));
        // the default policy rejects a SortedList whose comparator isn't bound to the table's
        table.setSortPolicy(t -> true);
        table.setItems(sorted);
        TableAutoFitLimiter.install(table);
    }

    private static void configureStatusColumn(TableColumn<ContingencyRow, ContingencyRow> column) {
        column.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue()));
        column.setCellFactory(col -> new StatusTableCell());
        column.setComparator(Comparator.comparing((ContingencyRow row) -> !row.state().failed())
                .thenComparing(row -> Messages.get(row.state().statusMessageKey())));
    }

    private static final class StatusTableCell extends TableCell<ContingencyRow, ContingencyRow> {
        private final Region marker = new Region();
        private final Label label = new Label();
        private final HBox box = new HBox(6, marker, label);

        private StatusTableCell() {
            marker.getStyleClass().addAll("component-status", "component-status-other");
            box.setAlignment(Pos.CENTER_LEFT);
        }

        @Override
        protected void updateItem(ContingencyRow row, boolean empty) {
            super.updateItem(row, empty);
            if (empty || row == null) {
                setGraphic(null);
            } else {
                marker.setVisible(row.state().failed());
                label.setText(Messages.get(row.state().statusMessageKey()));
                setGraphic(box);
            }
        }
    }

    /**
     * Monitors the pre-contingency flows and voltages of all equipment, shown along the post-contingency violations:
     * a single state, unlike monitoring the post-contingency ones.
     */
    public static StateMonitor preContingencyMonitor(Network network) {
        Set<String> branchIds = Stream.concat(network.getBranchStream(), network.getBoundaryLineStream(BoundaryLineFilter.UNPAIRED))
                .map(Identifiable::getId)
                .collect(Collectors.toSet());
        return new StateMonitor(ContingencyContext.none(), branchIds,
                network.getVoltageLevelStream().map(Identifiable::getId).collect(Collectors.toSet()),
                network.getThreeWindingsTransformerStream().map(Identifiable::getId).collect(Collectors.toSet()));
    }

    // A post-contingency row's pre-contingency value: of the pre-contingency violation on the same side, limit type and
    // operational limits group if any, otherwise as monitored (see preContingencyMonitor). Null for
    // a pre-contingency row, or if unknown.
    private Double preContingencyValue(ContingencyFlowRow row) {
        if (row.state().preContingency()) {
            return null;
        }
        FlowViolation preContingency = preContingencyViolation(row);
        double value = preContingency != null ? preContingency.violation().getValue() : monitoredFlow(row.worst().violation());
        return Double.isNaN(value) ? null : value;
    }

    // relative to the same limit as the row's
    private Loading preContingencyLoading(ContingencyFlowRow row) {
        FlowViolation preContingency = row.state().preContingency() ? null : preContingencyViolation(row);
        if (preContingency != null) {
            return Loading.of(preContingency);
        }
        Double value = preContingencyValue(row);
        return value == null ? null : new Loading(row.worst().percentOf(value), null);
    }

    private FlowViolation preContingencyViolation(ContingencyFlowRow row) {
        ContingencyFlowRow preContingency = preContingencyFlows.get(row.equipment().getId());
        LimitViolation violation = row.worst().violation();
        return preContingency == null ? null : preContingency.violations().stream()
                .filter(v -> v.violation().getSide() == violation.getSide()
                        && v.violation().getLimitType() == violation.getLimitType()
                        && Objects.equals(v.violation().getOperationalLimitsGroupId(), violation.getOperationalLimitsGroupId()))
                .findFirst().orElse(null);
    }

    private double monitoredFlow(LimitViolation violation) {
        LimitType type = LimitType.valueOf(violation.getLimitType().name());
        ThreeSides side = Objects.requireNonNullElse(violation.getSide(), ThreeSides.ONE);
        BranchResult branch = preContingencyNetworkResult.getBranchResult(violation.getSubjectId());
        if (branch != null) {
            return side == ThreeSides.TWO ? NetworkViolations.flowValue(branch.getI2(), branch.getP2(), branch.getQ2(), type)
                    : NetworkViolations.flowValue(branch.getI1(), branch.getP1(), branch.getQ1(), type);
        }
        ThreeWindingsTransformerResult transformer = preContingencyNetworkResult.getThreeWindingsTransformerResult(violation.getSubjectId());
        if (transformer != null) {
            return switch (side) {
                case ONE -> NetworkViolations.flowValue(transformer.getI1(), transformer.getP1(), transformer.getQ1(), type);
                case TWO -> NetworkViolations.flowValue(transformer.getI2(), transformer.getP2(), transformer.getQ2(), type);
                case THREE -> NetworkViolations.flowValue(transformer.getI3(), transformer.getP3(), transformer.getQ3(), type);
            };
        }
        return Double.NaN;
    }

    private Double preContingencyVoltage(VoltageRow row) {
        if (row.state().preContingency()) {
            return null;
        }
        Double v = preContingencyVoltages.get(row.violation().busId());
        VoltageViolation preContingency = preContingencyVoltageViolations.get(row.violation().busId());
        if ((v == null || Double.isNaN(v)) && preContingency != null) {
            v = preContingency.violation().getValue();
        }
        return v == null || Double.isNaN(v) ? null : v;
    }

    // by bus of the bus view
    static Map<String, Double> busViewVoltages(NetworkResult networkResult, Network root) {
        Map<String, Double> voltages = new HashMap<>();
        Map<String, Map<String, Bus>> busViewBusesByBusBreakerViewId = new HashMap<>();
        for (BusResult bus : networkResult.getBusResults()) {
            Bus busViewBus = busViewBus(bus, root, busViewBusesByBusBreakerViewId);
            if (busViewBus != null) {
                voltages.putIfAbsent(busViewBus.getId(), bus.getV());
            }
        }
        return voltages;
    }

    // Depending on the provider, its parameters and the topology kind, a bus result's id is a bus breaker view bus' (as
    // OpenLoadFlow's by default, several per bus of the bus view in a node/breaker topology), a busbar section's or a bus
    // view bus' own
    private static Bus busViewBus(BusResult bus, Network root, Map<String, Map<String, Bus>> busViewBusesByBusBreakerViewId) {
        BusbarSection busbarSection = root.getBusbarSection(bus.getBusId());
        if (busbarSection != null) {
            return busbarSection.getTerminal().getBusView().getBus();
        }
        VoltageLevel voltageLevel = root.getVoltageLevel(bus.getVoltageLevelId());
        if (voltageLevel == null) {
            return null;
        }
        // BusView.getMergedBus throws on some node/breaker topology buses
        Bus busViewBus = busViewBusesByBusBreakerViewId.computeIfAbsent(voltageLevel.getId(), id -> {
            Map<String, Bus> busViewBuses = new HashMap<>();
            voltageLevel.getBusView().getBusStream().forEach(busViewBusOf -> voltageLevel.getBusBreakerView()
                    .getBusStreamFromBusViewBusId(busViewBusOf.getId())
                    .forEach(busBreakerViewBus -> busViewBuses.put(busBreakerViewBus.getId(), busViewBusOf)));
            return busViewBuses;
        }).get(bus.getBusId());
        return busViewBus != null ? busViewBus : voltageLevel.getBusView().getBus(bus.getBusId());
    }

    // the row's state's violations of the equipment, along its pre-contingency ones
    private void showFlowViolations(Window window, ContingencyFlowRow row) {
        List<FlowViolationsDialog.Entry> entries = new ArrayList<>(entries(row));
        ContingencyFlowRow preContingency = preContingencyFlows.get(row.equipment().getId());
        if (preContingency != null && preContingency != row) {
            entries.addAll(entries(preContingency));
        }
        FlowViolationsDialog.showStates(window, row.equipment(), entries, row.worst(), guiParameters());
    }

    private void showFlowViolations(Window window, EquipmentFlowRow row) {
        List<FlowViolationsDialog.Entry> entries = row.rows().stream().flatMap(r -> entries(r).stream()).toList();
        FlowViolationsDialog.showStates(window, row.equipment(), entries, row.worstRow().worst(), guiParameters());
    }

    private static List<FlowViolationsDialog.Entry> entries(ContingencyFlowRow row) {
        return row.violations().stream().map(v -> new FlowViolationsDialog.Entry(row.state().name(), v)).toList();
    }

    // also used by the Violations view
    static Node subjectNode(SceneModel scene, LimitViolation violation) {
        Network network = scene.getNetwork();
        Identifiable<?> identifiable = network == null ? null : network.getNetwork().getIdentifiable(violation.getSubjectId());
        String subjectName = subjectName(network, violation);
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
        link.setOnAction(event -> scene.navigate(
                NavigationEvent.create(NavigationType.SUBSTATIONS, ContainerNavigationState.create(target))));
        return link;
    }

    static String subjectName(Network network, LimitViolation violation) {
        Identifiable<?> identifiable = network == null ? null : network.getNetwork().getIdentifiable(violation.getSubjectId());
        // TODO: improve in powsybl-open-loadflow - LimitViolationManager builds its LimitViolations without a
        // subjectName, so the name is recovered from the current network instead, falling back to subjectName then
        // subjectId when the subject is gone from the network (or isn't an identifiable, e.g. a voltage angle limit).
        return identifiable != null ? identifiable.getNameOrId()
                : Objects.requireNonNullElse(violation.getSubjectName(), violation.getSubjectId());
    }

    @Override
    public void setSceneModel(SceneModel sceneModel) {
        this.sceneModel = Objects.requireNonNull(sceneModel);
        this.mainModel = sceneModel.getMainModel();
        updateResults();
        listenerManager.listen(sceneModel.networkProperty(), (observable, oldValue, newValue) -> updateResults());
        listenerManager.listen(this.mainModel.updateProperty(), (observable, oldValue, newValue) -> updateResults());
        // for the decimal places, read by the cells on every render
        listenerManager.listen(this.mainModel.getParametersModel().guiParametersRevisionProperty(), (observable, oldValue, newValue) -> {
            contingenciesTableView.refresh();
            flowByContingencyTableView.refresh();
            flowByEquipmentTableView.refresh();
            voltageTableView.refresh();
        });
    }

    private void updateResults() {
        Network network = sceneModel.getNetwork();
        SecurityAnalysisResult result = network == null ? null : mainModel.getStudy(network).getSecurityAnalysisResult();
        List<ContingencyRow> contingencies = new ArrayList<>();
        List<ContingencyFlowRow> flows = new ArrayList<>();
        List<VoltageRow> voltages = new ArrayList<>();
        preContingencyFlows.clear();
        preContingencyVoltageViolations.clear();
        preContingencyNetworkResult = NetworkResult.empty();
        preContingencyVoltages = Map.of();
        if (result != null) {
            PreContingencyResult preContingencyResult = result.getPreContingencyResult();
            preContingencyNetworkResult = Objects.requireNonNullElse(preContingencyResult.getNetworkResult(), NetworkResult.empty());
            preContingencyVoltages = busViewVoltages(preContingencyNetworkResult, network.getNetwork());
            LoadFlowResult.ComponentResult.Status preContingencyStatus = preContingencyResult.getStatus();
            State preContingency = new State(Messages.get("securityAnalysisResults.preContingency"), true,
                    preContingencyStatusMessageKey(preContingencyStatus), preContingencyStatus != LoadFlowResult.ComponentResult.Status.CONVERGED);
            contingencies.add(addState(preContingency, preContingencyResult.getLimitViolationsResult().getLimitViolations(), network, flows, voltages));
            flows.forEach(row -> preContingencyFlows.put(row.equipment().getId(), row));
            voltages.forEach(row -> preContingencyVoltageViolations.put(row.violation().busId(), row.violation()));
            if (!preContingency.failed()) {
                for (PostContingencyResult postContingencyResult : result.getPostContingencyResults()) {
                    PostContingencyComputationStatus status = postContingencyResult.getStatus();
                    State state = new State(ContingencyNames.displayName(postContingencyResult.getContingency(), network), false,
                            postContingencyStatusMessageKey(status),
                            status != PostContingencyComputationStatus.CONVERGED && status != PostContingencyComputationStatus.NO_IMPACT);
                    contingencies.add(addState(state, postContingencyResult.getLimitViolationsResult().getLimitViolations(), network, flows, voltages));
                }
            }
        }
        contingenciesData.setAll(contingencies.stream()
                .sorted(Comparator.comparing((ContingencyRow row) -> !row.state().failed())
                        .thenComparing(ContingencyRow::worst, Comparator.nullsLast(FlowViolation.SEVERITY)))
                .toList());
        flowByContingencyData.setAll(flows.stream().sorted(Comparator.comparing(ContingencyFlowRow::worst)).toList());
        flowByEquipmentData.setAll(equipmentRows(flows));
        voltageData.setAll(voltages.stream().sorted(Comparator.comparing(VoltageRow::violation)).toList());
        contingenciesTab.setText(Messages.get("securityAnalysisResults.tab.contingencies", contingenciesData.size()));
        flowByContingencyTab.setText(Messages.get("securityAnalysisResults.tab.flowByContingency", flowByContingencyData.size()));
        flowByEquipmentTab.setText(Messages.get("securityAnalysisResults.tab.flowByEquipment", flowByEquipmentData.size()));
        voltageTab.setText(Messages.get("securityAnalysisResults.tab.voltage", voltageData.size()));
    }

    private static ContingencyRow addState(State state, List<LimitViolation> violations, Network network,
                                           List<ContingencyFlowRow> flows, List<VoltageRow> voltages) {
        Network root = network.getNetwork();
        Map<String, List<FlowViolation>> flowViolations = new LinkedHashMap<>();
        int voltageCount = 0;
        for (LimitViolation violation : state.failed() ? List.<LimitViolation>of() : violations) {
            if (ViolationsController.shown(network, violation.getSubjectId()) == null) {
                continue;
            }
            switch (violation.getLimitType()) {
                case CURRENT, ACTIVE_POWER, APPARENT_POWER -> flowViolations
                        .computeIfAbsent(violation.getSubjectId(), id -> new ArrayList<>())
                        .add(FlowViolation.of(violation, root));
                case LOW_VOLTAGE, HIGH_VOLTAGE -> {
                    VoltageViolation voltageViolation = VoltageViolation.of(violation, root);
                    if (voltageViolation != null) {
                        voltages.add(new VoltageRow(state, voltageViolation));
                        voltageCount++;
                    }
                }
                default -> {
                    // voltage angle, short-circuit current and other limit violations are out of scope
                }
            }
        }
        List<ContingencyFlowRow> stateFlows = flowViolations.entrySet().stream()
                .map(entry -> new ContingencyFlowRow(state, root.getIdentifiable(entry.getKey()),
                        entry.getValue().stream().sorted(FlowViolation.SEVERITY).toList()))
                .sorted(Comparator.comparing(ContingencyFlowRow::worst))
                .toList();
        flows.addAll(stateFlows);
        return new ContingencyRow(state, stateFlows.size(), voltageCount, stateFlows.isEmpty() ? null : stateFlows.getFirst());
    }

    private static List<EquipmentFlowRow> equipmentRows(List<ContingencyFlowRow> flows) {
        Map<Identifiable<?>, List<ContingencyFlowRow>> byEquipment = new LinkedHashMap<>();
        flows.forEach(row -> byEquipment.computeIfAbsent(row.equipment(), equipment -> new ArrayList<>()).add(row));
        return byEquipment.entrySet().stream()
                .map(entry -> new EquipmentFlowRow(entry.getKey(), entry.getValue().stream().sorted(Comparator.comparing(ContingencyFlowRow::worst)).toList()))
                .sorted(Comparator.comparing(row -> row.worstRow().worst()))
                .toList();
    }

    private static String preContingencyStatusMessageKey(LoadFlowResult.ComponentResult.Status status) {
        return switch (status) {
            case CONVERGED -> "desktop.common.computationStatus.converged";
            case MAX_ITERATION_REACHED -> "desktop.common.computationStatus.maxIterationReached";
            case FAILED -> "desktop.common.computationStatus.failed";
            case NO_CALCULATION -> "desktop.common.computationStatus.noCalculation";
        };
    }

    private static String postContingencyStatusMessageKey(PostContingencyComputationStatus status) {
        return switch (status) {
            case CONVERGED -> "desktop.common.computationStatus.converged";
            case MAX_ITERATION_REACHED -> "desktop.common.computationStatus.maxIterationReached";
            case SOLVER_FAILED -> "desktop.common.computationStatus.solverFailed";
            case FAILED -> "desktop.common.computationStatus.failed";
            case NO_IMPACT -> "desktop.common.computationStatus.noImpact";
        };
    }

    // read fresh: a parameters reset/import replaces the GuiParameters instance
    private GuiParameters guiParameters() {
        return mainModel.getParametersModel().getGuiParameters();
    }
}
