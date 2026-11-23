/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.ui.network.tables;

import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Network;
import com.powsybl.openrao.commons.PhysicalParameter;
import com.powsybl.openrao.commons.Unit;
import com.powsybl.openrao.data.crac.api.Crac;
import com.powsybl.openrao.data.crac.api.RemedialAction;
import com.powsybl.openrao.data.crac.api.State;
import com.powsybl.openrao.data.crac.api.cnec.FlowCnec;
import com.powsybl.openrao.data.crac.api.networkaction.NetworkAction;
import com.powsybl.openrao.data.crac.api.rangeaction.CounterTradeRangeAction;
import com.powsybl.openrao.data.crac.api.rangeaction.HvdcRangeAction;
import com.powsybl.openrao.data.crac.api.rangeaction.InjectionRangeAction;
import com.powsybl.openrao.data.crac.api.rangeaction.PstRangeAction;
import com.powsybl.openrao.data.raoresult.api.ComputationStatus;
import com.powsybl.openrao.data.raoresult.api.RaoResult;
import com.powsybl.powsybldesktop.contingency.ContingencyNames;
import com.powsybl.powsybldesktop.core.MainModel;
import com.powsybl.powsybldesktop.core.NetworkStudy;
import com.powsybl.powsybldesktop.parameters.GuiParameters;
import com.powsybl.powsybldesktop.rao.RaoResultRows;
import com.powsybl.powsybldesktop.ui.AbstractDisposableController;
import com.powsybl.powsybldesktop.ui.controls.TableAutoFitLimiter;
import com.powsybl.powsybldesktop.ui.form.TableColumnSupport;
import com.powsybl.powsybldesktop.ui.window.SceneModel;
import com.powsybl.powsybldesktop.ui.window.SceneView;
import com.powsybl.powsybldesktop.utils.ListenerManager;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.beans.InvalidationListener;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;

/**
 * The network's RAO result: a summary, then its flow CNECs (all states or one), with their thresholds, flows and
 * margins before optimization, after the preventive one and after the optimization of their own instant, most limiting
 * first (a negative margin is a constraint); the remedial actions it activated, state by state, with the set points and
 * taps of the range actions; and its costs, instant by instant. The result refers to the run's copy of the CRAC
 * ({@link NetworkStudy#getRaoCrac()}); it is outdated once the study's CRAC is replaced. The result is the root
 * network's, whichever subnetwork is selected.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class RaoResultsController extends AbstractDisposableController implements SceneView {

    @FXML
    Label alertLabel;
    @FXML
    Label summaryLabel;
    @FXML
    TabPane tabPane;
    @FXML
    Tab cnecsTab;
    @FXML
    Tab actionsTab;
    @FXML
    Tab costsTab;
    @FXML
    ChoiceBox<StateChoice> stateChoiceBox;
    @FXML
    ChoiceBox<Unit> unitChoiceBox;
    @FXML
    TableView<RaoResultRows.CnecRow> cnecsTable;
    @FXML
    TableView<RaoResultRows.ActionRow> actionsTable;
    @FXML
    TableView<RaoResultRows.CostRow> costsTable;

    // a choice box item can't be null: a null state shows them all
    record StateChoice(State state) {
        static final StateChoice ALL = new StateChoice(null);
    }

    private final ObservableList<RaoResultRows.CnecRow> cnecRows = FXCollections.observableArrayList();
    private final ObservableList<RaoResultRows.ActionRow> actionRows = FXCollections.observableArrayList();
    private final ObservableList<RaoResultRows.CostRow> costRows = FXCollections.observableArrayList();
    private final List<TableColumn<RaoResultRows.CostRow, ?>> virtualCostColumns = new ArrayList<>();
    // not a ChangeListener: OpenRAO's CRACs are equal by id, so replacing one by another of the same id is no change
    private final InvalidationListener cracListener = _ -> updateAlert();
    private TableColumn<RaoResultRows.CnecRow, ?> marginColumn;
    // those of the watched study, detached when another is watched
    private final ListenerManager studyListeners = listenerManager.child();
    private NetworkStudy watchedStudy;
    private boolean updating;

    private SceneModel sceneModel;
    private MainModel mainModel;

    @FXML
    private void initialize() {
        alertLabel.managedProperty().bind(alertLabel.visibleProperty());
        stateChoiceBox.setConverter(converter(choice -> choice == null ? "" : choice.state() == null ? Messages.get("raoResults.allStates") : stateName(choice.state())));
        unitChoiceBox.setConverter(converter(unit -> unit == null ? "" : Messages.get(unit == Unit.AMPERE ? "raoResults.unit.ampere" : "raoResults.unit.megawatt")));
        unitChoiceBox.getItems().setAll(Unit.AMPERE, Unit.MEGAWATT);
        stateChoiceBox.valueProperty().addListener((_, _, _) -> updateCnecRows());
        unitChoiceBox.valueProperty().addListener((_, _, _) -> updateCnecRows());

        cnecsTable.setItems(cnecRows);
        TableAutoFitLimiter.install(cnecsTable);
        cnecsTable.setPlaceholder(new Label(Messages.get("raoResults.none")));
        addTextColumn(cnecsTable, "cnec", "raoResults.column.cnec", 200, row -> row.cnec().getName());
        addTextColumn(cnecsTable, "element", "raoResults.column.element", 150, row -> equipmentName(row.cnec().getNetworkElementId()));
        addTextColumn(cnecsTable, "state", "raoResults.column.state", 180, row -> stateName(row.cnec().getState()));
        addTextColumn(cnecsTable, "side", "raoResults.column.side", 60, row -> Messages.get("raoResults.side." + row.side().getNum()));
        addTextColumn(cnecsTable, "role", "raoResults.column.role", 110, row -> role(row.cnec()));
        addCnecValueColumn("lowerBound", "raoResults.column.lowerBound", RaoResultRows.CnecRow::lowerBound, false);
        addCnecValueColumn("upperBound", "raoResults.column.upperBound", RaoResultRows.CnecRow::upperBound, false);
        addCnecValueColumn("initialFlow", "raoResults.column.initialFlow", RaoResultRows.CnecRow::initialFlow, false);
        addCnecValueColumn("preventiveFlow", "raoResults.column.preventiveFlow", RaoResultRows.CnecRow::preventiveFlow, false);
        addCnecValueColumn("flow", "raoResults.column.flow", RaoResultRows.CnecRow::flow, false);
        addCnecValueColumn("initialMargin", "raoResults.column.initialMargin", RaoResultRows.CnecRow::initialMargin, true);
        addCnecValueColumn("preventiveMargin", "raoResults.column.preventiveMargin", RaoResultRows.CnecRow::preventiveMargin, true);
        marginColumn = addCnecValueColumn("margin", "raoResults.column.margin", RaoResultRows.CnecRow::margin, true);

        actionsTable.setItems(actionRows);
        TableAutoFitLimiter.install(actionsTable);
        actionsTable.setPlaceholder(new Label(Messages.get("raoResults.noActions")));
        addTextColumn(actionsTable, "state", "raoResults.column.state", 180, row -> stateName(row.state()));
        addTextColumn(actionsTable, "action", "raoResults.column.action", 200, row -> row.action().getName());
        addTextColumn(actionsTable, "type", "raoResults.column.actionType", 140, row -> actionType(row.action()));
        addNumberColumn(actionsTable, "initialSetPoint", "raoResults.column.initialSetPoint", row -> row.initialSetPoint() == null ? Double.NaN : row.initialSetPoint(),
                row -> setPointQuantity(row.action()));
        addNumberColumn(actionsTable, "setPoint", "raoResults.column.setPoint", row -> row.setPoint() == null ? Double.NaN : row.setPoint(),
                row -> setPointQuantity(row.action()));
        addTextColumn(actionsTable, "initialTap", "raoResults.column.initialTap", 90, row -> row.initialTap() == null ? "-" : Integer.toString(row.initialTap()));
        addTextColumn(actionsTable, "tap", "raoResults.column.tap", 90, row -> row.tap() == null ? "-" : Integer.toString(row.tap()));

        costsTable.setItems(costRows);
        TableAutoFitLimiter.install(costsTable);
        costsTable.setPlaceholder(new Label(Messages.get("raoResults.none")));
        addTextColumn(costsTable, "instant", "raoResults.column.instant", 140, row -> row.instant() == null ? Messages.get("raoResults.initial") : row.instant().getId());
        addNumberColumn(costsTable, "functionalCost", "raoResults.column.functionalCost", RaoResultRows.CostRow::functionalCost, _ -> GuiParameters.Quantity.POWER);
        addNumberColumn(costsTable, "virtualCost", "raoResults.column.virtualCost", RaoResultRows.CostRow::virtualCost, _ -> GuiParameters.Quantity.POWER);
        addNumberColumn(costsTable, "cost", "raoResults.column.cost", row -> row.functionalCost() + row.virtualCost(), _ -> GuiParameters.Quantity.POWER);
    }

    private static <T> StringConverter<T> converter(Function<T, String> toString) {
        return new StringConverter<>() {
            @Override
            public String toString(T value) {
                return toString.apply(value);
            }

            @Override
            public T fromString(String string) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private String stateName(State state) {
        if (state.isPreventive()) {
            return Messages.get("raoResults.preventive");
        }
        Network network = rootNetwork();
        return Messages.get("raoResults.stateName", state.getContingency().map(contingency -> ContingencyNames.displayName(contingency, network)).orElse(""),
                state.getInstant().getId());
    }

    private static String role(FlowCnec cnec) {
        if (cnec.isOptimized()) {
            return Messages.get(cnec.isMonitored() ? "raoResults.role.optimizedAndMonitored" : "raoResults.role.optimized");
        }
        return Messages.get(cnec.isMonitored() ? "raoResults.role.monitored" : "raoResults.role.none");
    }

    private static String actionType(RemedialAction<?> action) {
        return Messages.get(switch (action) {
            case NetworkAction _ -> "raoResults.actionType.network";
            case PstRangeAction _ -> "raoResults.actionType.pst";
            case HvdcRangeAction _ -> "raoResults.actionType.hvdc";
            case CounterTradeRangeAction _ -> "raoResults.actionType.counterTrade";
            case InjectionRangeAction _ -> "raoResults.actionType.injection";
            default -> "raoResults.actionType.range";
        });
    }

    // a PST's set point is its angle
    private static GuiParameters.Quantity setPointQuantity(RemedialAction<?> action) {
        return action instanceof PstRangeAction ? GuiParameters.Quantity.ANGLE : GuiParameters.Quantity.POWER;
    }

    private <R> void addTextColumn(TableView<R> table, String id, String titleKey, double width, Function<R, String> value) {
        TableColumn<R, String> column = new TableColumn<>(Messages.get(titleKey));
        column.setId(id);
        column.setPrefWidth(width);
        column.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(value.apply(cellData.getValue())));
        table.getColumns().add(column);
    }

    private <R> TableColumn<R, R> addNumberColumn(TableView<R> table, String id, String titleKey, ToDoubleFunction<R> value,
                                                  Function<R, GuiParameters.Quantity> quantity) {
        return addNumberColumn(table, id, titleKey, value, quantity, false);
    }

    private <R> TableColumn<R, R> addNumberColumn(TableView<R> table, String id, String titleKey, ToDoubleFunction<R> value,
                                                  Function<R, GuiParameters.Quantity> quantity, boolean margin) {
        TableColumn<R, R> column = new TableColumn<>(Messages.get(titleKey));
        column.setId(id);
        // wide enough for the longest headers ("Margin After Preventive")
        column.setPrefWidth(Math.max(110, 9 * column.getText().length()));
        column.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue()));
        // NaN, not in the result, last
        column.setComparator(Comparator.comparingDouble(row -> {
            double v = value.applyAsDouble(row);
            return Double.isNaN(v) ? Double.MAX_VALUE : v;
        }));
        column.setCellFactory(_ -> new NumberCell<>(value, quantity, margin));
        table.getColumns().add(column);
        return column;
    }

    // flows, thresholds and margins in the unit shown, negative margins marked
    private TableColumn<RaoResultRows.CnecRow, RaoResultRows.CnecRow> addCnecValueColumn(String id, String titleKey,
                                                                                       ToDoubleFunction<RaoResultRows.CnecRow> value, boolean margin) {
        return addNumberColumn(cnecsTable, id, titleKey, value,
                _ -> unitChoiceBox.getValue() == Unit.MEGAWATT ? GuiParameters.Quantity.POWER : GuiParameters.Quantity.CURRENT, margin);
    }

    private final class NumberCell<R> extends TableCell<R, R> {
        private final ToDoubleFunction<R> value;
        private final Function<R, GuiParameters.Quantity> quantity;
        private final boolean margin;

        private NumberCell(ToDoubleFunction<R> value, Function<R, GuiParameters.Quantity> quantity, boolean margin) {
            this.value = value;
            this.quantity = quantity;
            this.margin = margin;
        }

        @Override
        protected void updateItem(R row, boolean empty) {
            super.updateItem(row, empty);
            getStyleClass().remove("rao-negative-margin");
            if (empty || row == null) {
                setText(null);
                return;
            }
            double v = value.applyAsDouble(row);
            Supplier<GuiParameters> guiParameters = () -> mainModel.getParametersModel().getGuiParameters();
            setText(TableColumnSupport.doubleFormat(guiParameters, quantity.apply(row)).toString(v));
            if (margin && v < 0) {
                getStyleClass().add("rao-negative-margin");
            }
        }
    }

    private Network rootNetwork() {
        Network network = sceneModel == null ? null : sceneModel.getNetwork();
        return network == null ? null : network.getNetwork();
    }

    private String equipmentName(String id) {
        Network network = rootNetwork();
        Identifiable<?> identifiable = network == null ? null : network.getIdentifiable(id);
        return identifiable == null ? id : identifiable.getNameOrId();
    }

    @Override
    public void setSceneModel(SceneModel sceneModel) {
        this.sceneModel = Objects.requireNonNull(sceneModel);
        this.mainModel = sceneModel.getMainModel();
        updateResults();
        listenerManager.listen(sceneModel.networkProperty(), (_, _, _) -> updateResults());
        listenerManager.listen(mainModel.updateProperty(), (_, _, _) -> updateResults());
        // for the decimal places, read by the cells on every render
        listenerManager.listen(mainModel.getParametersModel().guiParametersRevisionProperty(), (_, _, _) -> {
            cnecsTable.refresh();
            actionsTable.refresh();
            costsTable.refresh();
        });
    }

    private NetworkStudy study() {
        Network network = sceneModel.getNetwork();
        return network == null ? null : mainModel.getStudy(network);
    }

    // keeps the shown state and unit when they are still there
    private void updateResults() {
        NetworkStudy study = study();
        watch(study);
        RaoResult result = study == null ? null : study.getRaoResult();
        Crac crac = result == null ? null : study.getRaoCrac();
        StateChoice shownState = stateChoiceBox.getValue();
        updating = true;
        try {
            List<StateChoice> states = new ArrayList<>();
            if (crac != null) {
                states.add(StateChoice.ALL);
                crac.getStates().stream().sorted(RaoResultRows.STATE_ORDER).map(StateChoice::new).forEach(states::add);
            }
            stateChoiceBox.getItems().setAll(states);
            stateChoiceBox.setValue(states.stream()
                    .filter(choice -> choice.state() != null && shownState != null && shownState.state() != null
                            && choice.state().getId().equals(shownState.state().getId()))
                    .findFirst().orElse(crac == null ? null : StateChoice.ALL));
            if (unitChoiceBox.getValue() == null) {
                unitChoiceBox.setValue(Unit.AMPERE);
            }
        } finally {
            updating = false;
        }
        stateChoiceBox.setDisable(result == null);
        unitChoiceBox.setDisable(result == null);
        summaryLabel.setText(result == null ? Messages.get("raoResults.none") : summary(result, crac, study.isRaoDc()));
        actionRows.setAll(result == null ? List.of() : RaoResultRows.actionRows(result, crac));
        updateCosts(result, crac);
        updateAlert();
        updateCnecRows();
    }

    private static String summary(RaoResult result, Crac crac, boolean dc) {
        String status = Messages.get(result.getComputationStatus() == ComputationStatus.PARTIAL_FAILURE ? "raoResults.status.partialFailure" : "raoResults.status.completed");
        String security = crac.getFlowCnecs().isEmpty() ? ""
                : Messages.get(result.isSecure(PhysicalParameter.FLOW) ? "raoResults.flowsSecure" : "raoResults.flowsNotSecure");
        return Messages.get("raoResults.summary", status, security, Messages.get(dc ? "raoResults.mode.dc" : "raoResults.mode.ac"),
                Objects.toString(result.getExecutionDetails(), ""));
    }

    // a column per virtual cost, the result's own
    private void updateCosts(RaoResult result, Crac crac) {
        costsTable.getColumns().removeAll(virtualCostColumns);
        virtualCostColumns.clear();
        List<RaoResultRows.CostRow> rows = result == null ? List.of() : RaoResultRows.costRows(result, crac);
        rows.stream().findFirst().ifPresent(first -> first.virtualCosts().keySet().forEach(name -> {
            TableColumn<RaoResultRows.CostRow, ?> column = addNumberColumn(costsTable, "virtualCost-" + name, "raoResults.column.virtualCostNamed",
                    row -> row.virtualCosts().getOrDefault(name, Double.NaN), _ -> GuiParameters.Quantity.POWER);
            column.setText(Messages.get("raoResults.column.virtualCostNamed", name));
            column.setPrefWidth(Math.max(110, 9 * column.getText().length()));
            virtualCostColumns.add(column);
        }));
        costRows.setAll(rows);
    }

    // the CRAC replaced or edited
    private void watch(NetworkStudy study) {
        if (watchedStudy != study) {
            studyListeners.dispose();
            watchedStudy = study;
            if (study != null) {
                studyListeners.listenInvalidations(study.cracProperty(), cracListener);
                studyListeners.listenInvalidations(study.cracRevisionProperty(), cracListener);
            }
        }
    }

    private void updateAlert() {
        NetworkStudy study = study();
        boolean outdated = study != null && study.isRaoResultOutdated();
        alertLabel.setText(outdated ? Messages.get("raoResults.outdated") : "");
        alertLabel.setVisible(outdated);
    }

    private void updateCnecRows() {
        if (updating) {
            return;
        }
        NetworkStudy study = study();
        RaoResult result = study == null ? null : study.getRaoResult();
        if (result == null) {
            cnecRows.clear();
            return;
        }
        State state = stateChoiceBox.getValue() == null ? null : stateChoiceBox.getValue().state();
        cnecRows.setAll(RaoResultRows.cnecRows(result, study.getRaoCrac(), unitChoiceBox.getValue()).stream()
                .filter(row -> state == null || row.cnec().getState().equals(state))
                .toList());
        // most limiting first
        if (cnecsTable.getSortOrder().isEmpty()) {
            marginColumn.setSortType(TableColumn.SortType.ASCENDING);
            cnecsTable.getSortOrder().add(marginColumn);
        }
        cnecsTable.sort();
        cnecsTable.refresh();
    }
}
