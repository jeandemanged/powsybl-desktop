/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.ui.rao;

import com.powsybl.commons.PowsyblException;
import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.list.ContingencyList;
import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Network;
import com.powsybl.openrao.commons.OpenRaoException;
import com.powsybl.openrao.data.crac.api.Crac;
import com.powsybl.openrao.data.crac.api.CracCreationContext;
import com.powsybl.openrao.data.crac.api.Instant;
import com.powsybl.openrao.data.crac.api.RemedialAction;
import com.powsybl.openrao.data.crac.api.State;
import com.powsybl.openrao.data.crac.api.cnec.AngleCnec;
import com.powsybl.openrao.data.crac.api.cnec.Cnec;
import com.powsybl.openrao.data.crac.api.cnec.FlowCnec;
import com.powsybl.openrao.data.crac.api.cnec.VoltageCnec;
import com.powsybl.openrao.data.crac.api.networkaction.NetworkAction;
import com.powsybl.openrao.data.crac.api.parameters.CracCreationParameters;
import com.powsybl.openrao.data.crac.api.rangeaction.CounterTradeRangeAction;
import com.powsybl.openrao.data.crac.api.rangeaction.HvdcRangeAction;
import com.powsybl.openrao.data.crac.api.rangeaction.InjectionRangeAction;
import com.powsybl.openrao.data.crac.api.rangeaction.PstRangeAction;
import com.powsybl.openrao.data.crac.api.rangeaction.RangeAction;
import com.powsybl.openrao.data.crac.api.threshold.BranchThreshold;
import com.powsybl.openrao.data.crac.api.threshold.Threshold;
import com.powsybl.powsybldesktop.contingency.ContingencyNames;
import com.powsybl.powsybldesktop.core.AbstractNetworkTask;
import com.powsybl.powsybldesktop.core.MainModel;
import com.powsybl.powsybldesktop.core.NetworkStudy;
import com.powsybl.powsybldesktop.network.ElementCandidate;
import com.powsybl.powsybldesktop.notification.Notification;
import com.powsybl.powsybldesktop.notification.NotificationAction;
import com.powsybl.powsybldesktop.rao.CnecEdits;
import com.powsybl.powsybldesktop.rao.CracEdits;
import com.powsybl.powsybldesktop.rao.CracElements;
import com.powsybl.powsybldesktop.rao.CracGenerator;
import com.powsybl.powsybldesktop.rao.CracGeneratorSettings;
import com.powsybl.powsybldesktop.rao.CracIO;
import com.powsybl.powsybldesktop.rao.CracImportParameters;
import com.powsybl.powsybldesktop.rao.NetworkActionEdits;
import com.powsybl.powsybldesktop.rao.RangeActionEdits;
import com.powsybl.powsybldesktop.rao.RaoResultRows;
import com.powsybl.powsybldesktop.rao.UsageRuleEdits;
import com.powsybl.powsybldesktop.ui.AbstractDisposableController;
import com.powsybl.powsybldesktop.ui.controls.FileChooserPreferences;
import com.powsybl.powsybldesktop.ui.form.ElementPickerDialog;
import com.powsybl.powsybldesktop.ui.window.SceneModel;
import com.powsybl.powsybldesktop.ui.window.SceneView;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The CRAC of the selected network (its root's, the one a RAO runs on): its instants, contingencies, CNECs and remedial
 * actions, the details of the selected one (an instant's usage limits, a contingency's, a flow CNEC's and a remedial
 * action's editable with its usage rules, {@link InstantForm}, {@link ContingencyForm}, {@link FlowCnecForm},
 * {@link NetworkActionForm}, {@link RangeActionForm}, {@link UsageRuleDialog}), and the report of its import. Creates an
 * empty CRAC ({@link NewCracDialog}), imports a CRAC file of any format OpenRAO reads (asking the format's parameters,
 * {@link CracImportDialog}), generates one from the network, exports it in OpenRAO's JSON format, removes it. Adds
 * instants ({@link NewInstantDialog}), contingencies, flow CNECs ({@link AddFlowCnecsDialog}) and network actions,
 * removes contingencies, CNECs and remedial actions. Each edit bumps the study's CRAC revision, which this view
 * refreshes on. Objects whose network elements are missing from the network (edited since the import) are
 * marked, with the missing ids.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class CracController extends AbstractDisposableController implements SceneView {

    private static final Logger LOGGER = LoggerFactory.getLogger(CracController.class);

    @FXML
    Button newButton;
    @FXML
    Button importButton;
    @FXML
    Button generateButton;
    @FXML
    Button exportButton;
    @FXML
    MenuButton addMenuButton;
    @FXML
    MenuItem addInstantItem;
    @FXML
    MenuItem addContingencyItem;
    @FXML
    MenuItem addContingenciesFromListsItem;
    @FXML
    MenuItem addFlowCnecsItem;
    @FXML
    MenuItem addNetworkActionItem;
    @FXML
    MenuItem addPstRangeActionItem;
    @FXML
    MenuItem addHvdcRangeActionItem;
    @FXML
    MenuItem addInjectionRangeActionItem;
    @FXML
    MenuItem addCounterTradeRangeActionItem;
    @FXML
    Button removeSelectedButton;
    @FXML
    Button removeButton;
    @FXML
    Label headerLabel;
    @FXML
    TabPane tabPane;
    @FXML
    Tab contingenciesTab;
    @FXML
    Tab cnecsTab;
    @FXML
    Tab remedialActionsTab;
    @FXML
    Tab instantsTab;
    @FXML
    Tab reportTab;
    @FXML
    ListView<Instant> instantsListView;
    @FXML
    ListView<Contingency> contingenciesListView;
    @FXML
    ListView<Cnec<?>> cnecsListView;
    @FXML
    ListView<RemedialAction<?>> remedialActionsListView;
    @FXML
    ListView<String> reportListView;
    @FXML
    StackPane detailsHost;

    // the revision, not the CRAC property: it changes on each replacement (OpenRAO's CRACs are equal by id, so replacing
    // one by another of the same id is no change of the property) and on each edit
    private final ChangeListener<Number> cracListener = (_, _, _) -> refreshCrac();
    private ReadOnlyIntegerProperty watchedRevision;
    // the form of the selected contingency, kept while it is selected so that an edit keeps its focus
    ContingencyForm contingencyForm;
    // the form of the selected flow CNEC, kept likewise
    FlowCnecForm flowCnecForm;
    // the form of the selected network action, kept likewise
    NetworkActionForm networkActionForm;
    // the form of the selected range action, kept likewise
    RangeActionForm rangeActionForm;
    // the form of the selected instant, kept likewise
    InstantForm instantForm;
    // the contingency, the CNEC and the remedial action to select at the next refresh: one just added, renamed or replaced
    private String contingencyToSelect;
    private String cnecToSelect;
    private String actionToSelect;
    // while the lists are refreshed: their selection is cleared then restored, the details shown once done
    private boolean refreshing;
    private Network network;
    private SceneModel sceneModel;
    private MainModel mainModel;
    // the parameters of the last import, by format: its time stamp and region are offered again
    private final Map<String, CracCreationParameters> lastImportParameters = new HashMap<>();

    @FXML
    private void initialize() {
        instantsListView.setCellFactory(_ -> new ListCell<>() {
            @Override
            protected void updateItem(Instant instant, boolean empty) {
                super.updateItem(instant, empty);
                setText(empty || instant == null ? null : Messages.get("crac.instant.item", instant.getId(),
                        Messages.get("crac.instant.kind." + instant.getKind().name().toLowerCase(Locale.ROOT))));
            }
        });
        contingenciesListView.setCellFactory(_ -> new ProblemCell<>(contingency -> contingency.getName().orElse(contingency.getId()),
                contingency -> CracEdits.contingencyProblem(network, contingency)));
        cnecsListView.setCellFactory(_ -> new ProblemCell<>(cnec -> Messages.get("crac.cnec.item", cnec.getName(), stateName(cnec.getState())),
                cnec -> missingProblem(CracElements.missing(network, cnec))));
        remedialActionsListView.setCellFactory(_ -> new ProblemCell<>(action -> Messages.get("crac.remedialAction.item", action.getName(), actionType(action)),
                action -> missingProblem(CracElements.missing(network, action))));
        reportListView.setPlaceholder(new Label(Messages.get("crac.report.none")));
        removeSelectedButton.disableProperty().bind(Bindings.createBooleanBinding(() -> selectedToRemove().isEmpty(),
                tabPane.getSelectionModel().selectedItemProperty(), contingenciesListView.getSelectionModel().selectedItemProperty(),
                cnecsListView.getSelectionModel().selectedItemProperty(), remedialActionsListView.getSelectionModel().selectedItemProperty()));
        contingenciesListView.getSelectionModel().selectedItemProperty().addListener((_, _, _) -> onSelection());
        cnecsListView.getSelectionModel().selectedItemProperty().addListener((_, _, _) -> onSelection());
        remedialActionsListView.getSelectionModel().selectedItemProperty().addListener((_, _, _) -> onSelection());
        instantsListView.getSelectionModel().selectedItemProperty().addListener((_, _, _) -> onSelection());
        tabPane.getSelectionModel().selectedItemProperty().addListener((_, _, _) -> onSelection());
    }

    @Override
    public void setSceneModel(SceneModel sceneModel) {
        this.sceneModel = Objects.requireNonNull(sceneModel);
        this.mainModel = sceneModel.getMainModel();
        listenerManager.listen(sceneModel.networkProperty(), (_, _, newValue) -> refreshForNetwork(newValue));
        // a network edit may remove or restore elements of the CRAC
        listenerManager.listen(mainModel.updateProperty(), (_, _, _) -> refreshProblems());
        refreshForNetwork(sceneModel.getNetwork());
    }

    private NetworkStudy study() {
        return network == null ? null : mainModel.getStudy(network);
    }

    // the CRAC is the root network's, the one a RAO runs on
    private void refreshForNetwork(Network selectedNetwork) {
        Network newNetwork = selectedNetwork == null ? null : selectedNetwork.getNetwork();
        if (newNetwork != null && newNetwork == network) {
            return;
        }
        network = newNetwork;
        watch(network == null ? null : study().cracRevisionProperty());
        newButton.setDisable(network == null);
        importButton.setDisable(network == null);
        generateButton.setDisable(network == null);
        refreshCrac();
    }

    private void watch(ReadOnlyIntegerProperty revision) {
        if (watchedRevision != revision) {
            if (watchedRevision != null) {
                watchedRevision.removeListener(cracListener);
            }
            watchedRevision = revision;
            if (revision != null) {
                revision.addListener(cracListener);
            }
        }
    }

    private Crac crac() {
        return network == null ? null : study().getCrac();
    }

    // the selection of each list is kept, by id: an edit replaces the lists' items
    private void refreshCrac() {
        Crac crac = crac();
        exportButton.setDisable(crac == null);
        removeButton.setDisable(crac == null);
        addMenuButton.setDisable(crac == null);
        headerLabel.setText(network == null ? Messages.get("crac.noNetwork") : crac == null ? Messages.get("crac.none") : header(crac));
        String selectedContingency = contingencyToSelect != null ? contingencyToSelect
                : Optional.ofNullable(contingenciesListView.getSelectionModel().getSelectedItem()).map(Contingency::getId).orElse(null);
        contingencyToSelect = null;
        String selectedCnec = cnecToSelect;
        cnecToSelect = null;
        String selectedAction = actionToSelect;
        actionToSelect = null;
        refreshing = true;
        try {
            setItems(instantsListView, crac == null ? List.of() : crac.getSortedInstants(), Instant::getId, null);
            setItems(contingenciesListView, crac == null ? List.of() : crac.getContingencies().stream().sorted(Comparator.comparing(Contingency::getId)).toList(),
                    Contingency::getId, selectedContingency);
            setItems(cnecsListView, crac == null ? List.of() : crac.getCnecs().stream().<Cnec<?>>map(cnec -> cnec)
                    .sorted(Comparator.<Cnec<?>, State>comparing(Cnec::getState, RaoResultRows.STATE_ORDER).thenComparing(Cnec::getId)).toList(), Cnec::getId, selectedCnec);
            setItems(remedialActionsListView, crac == null ? List.of() : CracElements.remedialActions(crac).sorted(Comparator.comparing(RemedialAction::getId)).toList(),
                    RemedialAction::getId, selectedAction);
        } finally {
            refreshing = false;
        }
        reportListView.setItems(FXCollections.observableArrayList(crac == null ? List.of() : study().getCracCreationReport()));
        String empty = Messages.get(crac == null ? "crac.empty.noCrac" : "crac.empty.none");
        instantsListView.setPlaceholder(new Label(empty));
        contingenciesListView.setPlaceholder(new Label(empty));
        cnecsListView.setPlaceholder(new Label(empty));
        remedialActionsListView.setPlaceholder(new Label(empty));
        showDetails();
    }

    // selects the item of id {@code selectedId}, or else the one of the selected item's id
    private static <T> void setItems(ListView<T> listView, List<T> items, Function<T, String> id, String selectedId) {
        String toSelect = selectedId != null ? selectedId : Optional.ofNullable(listView.getSelectionModel().getSelectedItem()).map(id).orElse(null);
        listView.setItems(FXCollections.observableArrayList(items));
        if (toSelect != null) {
            items.stream().filter(item -> id.apply(item).equals(toSelect)).findFirst().ifPresent(item -> {
                listView.getSelectionModel().select(item);
                listView.scrollTo(item);
            });
        }
    }

    private static Optional<String> missingProblem(List<String> missingIds) {
        return missingIds.isEmpty() ? Optional.empty() : Optional.of(Messages.get("crac.missingElements", String.join(", ", missingIds)));
    }

    private void refreshProblems() {
        // the forms tell their problems too
        contingencyForm = null;
        flowCnecForm = null;
        networkActionForm = null;
        rangeActionForm = null;
        instantForm = null;
        contingenciesListView.refresh();
        cnecsListView.refresh();
        remedialActionsListView.refresh();
        showDetails();
    }

    private static String header(Crac crac) {
        String name = crac.getName().equals(crac.getId()) ? crac.getId() : crac.getName() + " (" + crac.getId() + ")";
        String instants = crac.getSortedInstants().stream().map(Instant::getId).collect(Collectors.joining(", "));
        return crac.getTimestamp()
                .map(timestamp -> Messages.get("crac.header.timestamp", name, timestamp.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME), instants))
                .orElseGet(() -> Messages.get("crac.header", name, instants));
    }

    String stateName(State state) {
        return stateName(CnecEdits.StateRef.of(state));
    }

    String stateName(CnecEdits.StateRef state) {
        if (state.isPreventive()) {
            return Messages.get("raoResults.preventive");
        }
        return Messages.get("raoResults.stateName", contingencyName(state.contingencyId()), state.instantId());
    }

    private String contingencyName(String id) {
        Contingency contingency = crac() == null ? null : crac().getContingency(id);
        return contingency == null ? id : ContingencyNames.displayName(contingency, network);
    }

    static String actionType(RemedialAction<?> action) {
        return Messages.get(switch (action) {
            case NetworkAction _ -> "raoResults.actionType.network";
            case PstRangeAction _ -> "raoResults.actionType.pst";
            case HvdcRangeAction _ -> "raoResults.actionType.hvdc";
            case CounterTradeRangeAction _ -> "raoResults.actionType.counterTrade";
            case InjectionRangeAction _ -> "raoResults.actionType.injection";
            default -> "raoResults.actionType.range";
        });
    }

    private String elementName(String id) {
        Identifiable<?> identifiable = network == null ? null : network.getIdentifiable(id);
        if (identifiable == null) {
            return Messages.get("crac.details.missingElement", id);
        }
        return identifiable.getNameOrId().equals(id) ? id : identifiable.getNameOrId() + " (" + id + ")";
    }

    // ----- details of the selected object -----

    private void onSelection() {
        if (!refreshing) {
            showDetails();
        }
    }

    private void showDetails() {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        Instant instant = instantsListView.getSelectionModel().getSelectedItem();
        if (tab == instantsTab && instant != null) {
            showInstantForm(instant);
            return;
        }
        instantForm = null;
        Contingency contingency = contingenciesListView.getSelectionModel().getSelectedItem();
        if (tab == contingenciesTab && contingency != null) {
            showContingencyForm(contingency);
            return;
        }
        contingencyForm = null;
        if (tab == cnecsTab && cnecsListView.getSelectionModel().getSelectedItem() instanceof FlowCnec flowCnec) {
            showFlowCnecForm(flowCnec);
            return;
        }
        flowCnecForm = null;
        if (tab == remedialActionsTab && remedialActionsListView.getSelectionModel().getSelectedItem() instanceof NetworkAction networkAction) {
            showNetworkActionForm(networkAction);
            return;
        }
        networkActionForm = null;
        if (tab == remedialActionsTab && remedialActionsListView.getSelectionModel().getSelectedItem() instanceof RangeAction<?> rangeAction
                && RangeActionEdits.RangeActionSpec.of(rangeAction).isPresent()) {
            showRangeActionForm(rangeAction);
            return;
        }
        rangeActionForm = null;
        List<String[]> rows = new ArrayList<>();
        if (tab == cnecsTab && cnecsListView.getSelectionModel().getSelectedItem() != null) {
            cnecDetails(cnecsListView.getSelectionModel().getSelectedItem(), rows);
        } else if (tab == remedialActionsTab && remedialActionsListView.getSelectionModel().getSelectedItem() != null) {
            remedialActionDetails(remedialActionsListView.getSelectionModel().getSelectedItem(), rows);
        }
        if (rows.isEmpty()) {
            detailsHost.getChildren().clear();
            return;
        }
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(6);
        for (String[] row : rows) {
            Label label = new Label(row[0]);
            label.getStyleClass().add("crac-details-label");
            Label value = new Label(row[1]);
            value.setWrapText(true);
            grid.addRow(grid.getRowCount(), label, value);
        }
        detailsHost.getChildren().setAll(grid);
    }

    private static void row(List<String[]> rows, String key, String value) {
        rows.add(new String[] {Messages.get(key), value});
    }

    // the form in place is kept while it edits the selected contingency, so that the field being edited keeps its focus
    private void showContingencyForm(Contingency contingency) {
        if (contingencyForm == null || contingencyForm.contingency() != contingency) {
            Crac crac = crac();
            contingencyForm = new ContingencyForm(crac, network, contingency, edited -> {
                contingencyToSelect = edited.getId();
                study().cracEdited();
            }, this::pickContingencyElements);
            detailsHost.getChildren().setAll(contingencyForm.root());
        }
    }

    private void showFlowCnecForm(FlowCnec cnec) {
        if (flowCnecForm == null || flowCnecForm.cnec() != cnec) {
            flowCnecForm = new FlowCnecForm(crac(), network, cnec, edited -> {
                cnecToSelect = edited.getId();
                study().cracEdited();
            }, this::stateName, () -> pickBranches(Messages.get("crac.cnec.changeBranch.title")).stream().findFirst());
            detailsHost.getChildren().setAll(flowCnecForm.root());
        }
    }

    private void showNetworkActionForm(NetworkAction action) {
        if (networkActionForm == null || networkActionForm.action() != action) {
            networkActionForm = new NetworkActionForm(crac(), network, action, edited -> {
                actionToSelect = edited.getId();
                study().cracEdited();
            }, usageRulesContext(), this::pickActionElements);
            detailsHost.getChildren().setAll(networkActionForm.root());
        }
    }

    private void showRangeActionForm(RangeAction<?> action) {
        if (rangeActionForm == null || rangeActionForm.action() != action) {
            rangeActionForm = new RangeActionForm(crac(), network, action, edited -> {
                actionToSelect = edited.getId();
                study().cracEdited();
            }, usageRulesContext(), this::pickRangeActionElements);
            detailsHost.getChildren().setAll(rangeActionForm.root());
        }
    }

    private void showInstantForm(Instant instant) {
        if (instantForm == null || instantForm.instant() != instant) {
            instantForm = new InstantForm(crac(), instant, () -> study().cracEdited());
            detailsHost.getChildren().setAll(instantForm.root());
        }
    }

    private UsageRulesPane.Context usageRulesContext() {
        return new UsageRulesPane.Context(this::usageRuleText, this::askUsageRule);
    }

    // a usage rule to add; overridable by a test, which can't answer a modal dialog
    Optional<UsageRuleEdits.UsageRuleSpec> askUsageRule() {
        return new UsageRuleDialog(detailsHost.getScene().getWindow(), crac(), network, this::stateName).showAndWait();
    }

    // the ids of the branches picked; overridable by a test, which can't answer a modal dialog
    List<String> pickBranches(String title) {
        return new ElementPickerDialog(detailsHost.getScene().getWindow(), title, ElementCandidate.of(network.getBranchStream().map(branch -> (Identifiable<?>) branch)), Set.of())
                .showAndWait().orElse(List.of());
    }

    // the ids of the network objects picked to be added to a contingency; overridable by a test, which can't answer a
    // modal dialog
    List<String> pickContingencyElements(Set<String> present) {
        return new ElementPickerDialog(detailsHost.getScene().getWindow(), Messages.get("crac.contingency.addElements.title"),
                ElementCandidate.of(CracElements.contingencyElementCandidates(network)), present).showAndWait().orElse(List.of());
    }

    private void cnecDetails(Cnec<?> cnec, List<String[]> rows) {
        row(rows, "crac.details.id", cnec.getId());
        row(rows, "crac.details.name", cnec.getName());
        row(rows, "crac.details.cnecType", Messages.get(switch (cnec) {
            case FlowCnec _ -> "crac.cnecType.flow";
            case AngleCnec _ -> "crac.cnecType.angle";
            case VoltageCnec _ -> "crac.cnecType.voltage";
            default -> "crac.cnecType.other";
        }));
        row(rows, "crac.details.elements", cnec.getNetworkElementsIds().stream().sorted().map(this::elementName).collect(Collectors.joining("\n")));
        row(rows, "crac.details.state", stateName(cnec.getState()));
        row(rows, "crac.details.role", Messages.get(cnec.isOptimized()
                ? cnec.isMonitored() ? "raoResults.role.optimizedAndMonitored" : "raoResults.role.optimized"
                : cnec.isMonitored() ? "raoResults.role.monitored" : "raoResults.role.none"));
        row(rows, "crac.details.thresholds", thresholds(cnec));
        row(rows, "crac.details.reliabilityMargin", Double.toString(cnec.getReliabilityMargin()));
        row(rows, "crac.details.operator", Objects.toString(cnec.getOperator(), "-"));
    }

    private static String thresholds(Cnec<?> cnec) {
        List<? extends Threshold> thresholds = switch (cnec) {
            case FlowCnec flowCnec -> List.copyOf(flowCnec.getThresholds());
            case AngleCnec angleCnec -> List.copyOf(angleCnec.getThresholds());
            case VoltageCnec voltageCnec -> List.copyOf(voltageCnec.getThresholds());
            default -> List.of();
        };
        return thresholds.stream().map(threshold -> {
            String side = threshold instanceof BranchThreshold branchThreshold ? Messages.get("crac.details.side", branchThreshold.getSide().getNum()) + " " : "";
            return side + Messages.get("crac.details.threshold", threshold.min().map(String::valueOf).orElse("-"),
                    threshold.max().map(String::valueOf).orElse("-"), threshold.getUnit());
        }).sorted().collect(Collectors.joining("\n"));
    }

    private void remedialActionDetails(RemedialAction<?> action, List<String[]> rows) {
        row(rows, "crac.details.id", action.getId());
        row(rows, "crac.details.name", action.getName());
        row(rows, "crac.details.actionType", actionType(action));
        row(rows, "crac.details.elements", action.getNetworkElementsIds().stream().sorted().map(this::elementName).collect(Collectors.joining("\n")));
        row(rows, "crac.details.usageRules", UsageRuleEdits.of(action).stream().map(this::usageRuleText).collect(Collectors.joining("\n")));
        row(rows, "crac.details.operator", Objects.toString(action.getOperator(), "-"));
        action.getActivationCost().ifPresent(cost -> row(rows, "crac.details.activationCost", Double.toString(cost)));
        action.getSpeed().ifPresent(speed -> row(rows, "crac.details.speed", Integer.toString(speed)));
    }

    String usageRuleText(UsageRuleEdits.UsageRuleSpec rule) {
        return switch (rule.kind()) {
            case ON_INSTANT -> Messages.get("crac.usageRule.onInstant", rule.instantId());
            case ON_CONTINGENCY_STATE -> Messages.get("crac.usageRule.onState", stateName(new CnecEdits.StateRef(rule.contingencyId(), rule.instantId())));
            case ON_CONSTRAINT -> {
                Cnec<?> cnec = crac() == null ? null : crac().getCnec(rule.cnecId());
                yield Messages.get("crac.usageRule.onConstraint", rule.instantId(), cnec == null ? rule.cnecId() : cnec.getName());
            }
            case ON_FLOW_CONSTRAINT_IN_COUNTRY -> rule.contingencyId() == null
                    ? Messages.get("crac.usageRule.onCountry", rule.instantId(), rule.country())
                    : Messages.get("crac.usageRule.onCountryAfter", rule.instantId(), rule.country(), contingencyName(rule.contingencyId()));
        };
    }

    private static final class ProblemCell<T> extends ListCell<T> {
        private final Function<T, String> text;
        private final Function<T, Optional<String>> problem;

        ProblemCell(Function<T, String> text, Function<T, Optional<String>> problem) {
            this.text = text;
            this.problem = problem;
        }

        @Override
        protected void updateItem(T item, boolean empty) {
            super.updateItem(item, empty);
            setTooltip(null);
            setGraphic(null);
            if (empty || item == null) {
                setText(null);
                return;
            }
            setText(text.apply(item));
            problem.apply(item).ifPresent(message -> {
                FontIcon icon = new FontIcon("mdi2a-alert");
                icon.getStyleClass().add("alert-icon");
                setGraphic(icon);
                setTooltip(new Tooltip(message));
            });
        }
    }

    // ----- edits -----

    @FXML
    private void onNew() {
        if (network == null) {
            return;
        }
        new NewCracDialog(detailsHost.getScene().getWindow(), network.getId(), crac() != null).showAndWait().ifPresent(this::setNewCrac);
    }

    void setNewCrac(Crac crac) {
        study().setCrac(crac, List.of());
    }

    @FXML
    private void onAddInstant() {
        Crac crac = crac();
        if (crac != null && new NewInstantDialog(detailsHost.getScene().getWindow(), crac).showAndWait().orElse(false)) {
            study().cracEdited();
        }
    }

    // with no elements yet: its form, selected, offers to add them
    @FXML
    void onAddContingency() {
        Crac crac = crac();
        if (crac == null) {
            return;
        }
        Contingency contingency = CracEdits.addContingency(crac, CracEdits.newContingencyId(crac, Messages.get("crac.contingency.newId")), null, List.of());
        tabPane.getSelectionModel().select(contingenciesTab);
        contingencyToSelect = contingency.getId();
        study().cracEdited();
    }

    // those of the enabled contingency lists, as a security or sensitivity analysis resolves them
    @FXML
    void onAddContingenciesFromLists() {
        Crac crac = crac();
        if (crac == null) {
            return;
        }
        java.time.Instant start = java.time.Instant.now();
        NetworkStudy study = study();
        List<ContingencyList> lists = study.getContingencyLists().stream().filter(list -> study.contingencyListEnabledProperty(list).get()).toList();
        if (lists.isEmpty()) {
            mainModel.getNotificationsModel().add(Notification.createError(start, "crac.contingency.fromLists.none"));
            return;
        }
        List<Contingency> contingencies = new ArrayList<>();
        for (ContingencyList list : lists) {
            try {
                contingencies.addAll(list.getContingencies(network));
            } catch (PowsyblException | IllegalArgumentException e) {
                LOGGER.warn("Contingency list '{}' could not be evaluated: {}", list.getName(), e.toString());
            }
        }
        CracEdits.Added added = CracEdits.addContingencies(crac, network, ContingencyNames.deduplicate(contingencies));
        tabPane.getSelectionModel().select(contingenciesTab);
        study.cracEdited();
        mainModel.getNotificationsModel().add(Notification.createSuccess(start, "crac.contingency.fromLists.completed")
                .withMessageArgs(added.added().size(), added.skipped().size()));
    }

    @FXML
    void onRemoveSelected() {
        Optional<Object> selected = selectedToRemove();
        if (selected.isEmpty()) {
            return;
        }
        try {
            switch (selected.get()) {
                case Contingency contingency -> CracEdits.removeContingency(crac(), contingency.getId());
                case Cnec<?> cnec -> CnecEdits.remove(crac(), cnec.getId());
                case RangeAction<?> action -> RangeActionEdits.remove(crac(), action.getId());
                case RemedialAction<?> action -> crac().removeRemedialAction(action.getId());
                default -> throw new IllegalStateException();
            }
        } catch (IllegalArgumentException e) {
            mainModel.getNotificationsModel().add(Notification.createError(java.time.Instant.now(), "crac.removeSelected.failed").withMessageArgs(e.getMessage()));
            return;
        }
        study().cracEdited();
    }

    // the object of the selected tab the remove button removes: a contingency, a CNEC or a remedial action
    private Optional<Object> selectedToRemove() {
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        if (tab == contingenciesTab) {
            return Optional.ofNullable(contingenciesListView.getSelectionModel().getSelectedItem());
        }
        if (tab == cnecsTab) {
            return Optional.ofNullable(cnecsListView.getSelectionModel().getSelectedItem());
        }
        return tab == remedialActionsTab ? Optional.ofNullable(remedialActionsListView.getSelectionModel().getSelectedItem()) : Optional.empty();
    }

    // on branches picked, at the states and sides asked, as OpenRAO's CRAC generator makes them
    @FXML
    void onAddFlowCnecs() {
        Crac crac = crac();
        if (crac == null) {
            return;
        }
        List<String> branchIds = pickBranches(Messages.get("crac.cnec.add.branches"));
        if (!branchIds.isEmpty()) {
            askFlowCnecs(branchIds.size()).ifPresent(request -> addFlowCnecs(branchIds, request));
        }
    }

    // overridable by a test, which can't answer a modal dialog
    Optional<AddFlowCnecsDialog.Request> askFlowCnecs(int branchCount) {
        return new AddFlowCnecsDialog(detailsHost.getScene().getWindow(), branchCount, CnecEdits.states(crac()), this::stateName,
                mainModel.getParametersModel().cracCreationParametersProperty().get().getDefaultMonitoredSides()).showAndWait();
    }

    void addFlowCnecs(List<String> branchIds, AddFlowCnecsDialog.Request request) {
        java.time.Instant start = java.time.Instant.now();
        CnecEdits.Added added = CnecEdits.addFlowCnecs(crac(), network, branchIds, request.states(), request.sides());
        tabPane.getSelectionModel().select(cnecsTab);
        added.added().stream().findFirst().ifPresent(first -> cnecToSelect = first.getId());
        study().cracEdited();
        NotificationAction details = new NotificationAction("crac.cnec.add.viewSkipped", _ -> showLines(Messages.get("crac.cnec.add.skippedTitle"), added.skipped()));
        mainModel.getNotificationsModel().add(added.skipped().isEmpty()
                ? Notification.createSuccess(start, "crac.cnec.add.completed").withMessageArgs(added.added().size(), 0)
                : Notification.createPartialSuccess(start, "crac.cnec.add.completed", details).withMessageArgs(added.added().size(), added.skipped().size()));
    }

    // acting on network elements picked, each as {@link NetworkActionEdits#defaultAction} tells, with no usage rule yet
    @FXML
    void onAddNetworkAction() {
        Crac crac = crac();
        if (crac == null) {
            return;
        }
        List<String> ids = pickActionElements(Set.of());
        if (ids.isEmpty()) {
            return;
        }
        List<NetworkActionEdits.ElementaryActionSpec> actions = ids.stream().map(network::getIdentifiable).filter(Objects::nonNull)
                .map(NetworkActionEdits::defaultAction).flatMap(Optional::stream).toList();
        try {
            NetworkAction action = NetworkActionEdits.add(crac, network, new NetworkActionEdits.NetworkActionSpec(
                    NetworkActionEdits.newId(crac, Messages.get("crac.networkAction.newId")), null, null, null, null, actions), List.of());
            tabPane.getSelectionModel().select(remedialActionsTab);
            actionToSelect = action.getId();
            study().cracEdited();
        } catch (IllegalArgumentException e) {
            mainModel.getNotificationsModel().add(Notification.createError(java.time.Instant.now(), "crac.networkAction.add.failed").withMessageArgs(e.getMessage()));
        }
    }

    @FXML
    private void onAddPstRangeAction() {
        addRangeActions(RangeActionEdits.Type.PST);
    }

    @FXML
    private void onAddHvdcRangeAction() {
        addRangeActions(RangeActionEdits.Type.HVDC);
    }

    @FXML
    private void onAddInjectionRangeAction() {
        addRangeActions(RangeActionEdits.Type.INJECTION);
    }

    @FXML
    private void onAddCounterTradeRangeAction() {
        addRangeActions(RangeActionEdits.Type.COUNTER_TRADE);
    }

    /**
     * Adds range actions of {@code type}, with no usage rule yet, as {@link RangeActionEdits} makes them from the
     * network: one per PST or HVDC line picked, one on all the injections picked, one counter-trade from the country
     * asked.
     */
    void addRangeActions(RangeActionEdits.Type type) {
        Crac crac = crac();
        if (crac == null) {
            return;
        }
        String prefix = Messages.get("crac.rangeAction.newId." + type.code());
        List<RangeAction<?>> added = new ArrayList<>();
        try {
            switch (type) {
                case PST, HVDC -> {
                    for (String elementId : pickRangeActionElements(type, Set.of())) {
                        String id = NetworkActionEdits.newId(crac, prefix);
                        added.add(RangeActionEdits.add(crac, network, type == RangeActionEdits.Type.PST ? RangeActionEdits.pst(network, id, elementId)
                                : RangeActionEdits.hvdc(network, id, elementId), List.of()));
                    }
                }
                case INJECTION -> {
                    List<String> injectionIds = pickRangeActionElements(type, Set.of());
                    if (!injectionIds.isEmpty()) {
                        added.add(RangeActionEdits.add(crac, network, RangeActionEdits.injection(network, NetworkActionEdits.newId(crac, prefix), injectionIds), List.of()));
                    }
                }
                case COUNTER_TRADE -> {
                    Optional<Country> country = askCountry();
                    if (country.isPresent()) {
                        added.add(RangeActionEdits.add(crac, network, RangeActionEdits.counterTrade(network, NetworkActionEdits.newId(crac, prefix), country.get()),
                                List.of()));
                    }
                }
            }
        } catch (IllegalArgumentException e) {
            mainModel.getNotificationsModel().add(Notification.createError(java.time.Instant.now(), "crac.rangeAction.add.failed").withMessageArgs(e.getMessage()));
        }
        if (!added.isEmpty()) {
            tabPane.getSelectionModel().select(remedialActionsTab);
            actionToSelect = added.getFirst().getId();
            study().cracEdited();
        }
    }

    // the ids of the network elements picked for a range action of {@code type}; overridable by a test, which can't
    // answer a modal dialog
    List<String> pickRangeActionElements(RangeActionEdits.Type type, Set<String> present) {
        return new ElementPickerDialog(detailsHost.getScene().getWindow(), Messages.get("crac.rangeAction.pick.title", type.label()),
                ElementCandidate.of(RangeActionEdits.elementCandidates(network, type)), present).showAndWait().orElse(List.of());
    }

    // the country of a counter-trade; overridable by a test
    Optional<Country> askCountry() {
        List<Country> countries = RangeActionEdits.countries(network);
        if (countries.isEmpty()) {
            mainModel.getNotificationsModel().add(Notification.createError(java.time.Instant.now(), "crac.rangeAction.noCountry"));
            return Optional.empty();
        }
        ChoiceDialog<Country> dialog = new ChoiceDialog<>(countries.getFirst(), countries);
        dialog.initOwner(detailsHost.getScene().getWindow());
        dialog.setTitle(Messages.get("crac.rangeAction.country.title"));
        dialog.setHeaderText(null);
        dialog.setContentText(Messages.get("crac.rangeAction.country"));
        return dialog.showAndWait();
    }

    // the ids of the network elements picked to be acted on; overridable by a test, which can't answer a modal dialog
    List<String> pickActionElements(Set<String> present) {
        return new ElementPickerDialog(detailsHost.getScene().getWindow(), Messages.get("crac.networkAction.addActions.title"),
                ElementCandidate.of(NetworkActionEdits.elementCandidates(network)), present).showAndWait().orElse(List.of());
    }

    // ----- import, export, removal -----

    @FXML
    private void onImport() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(Messages.get("crac.import.files"), "*.json", "*.xml", "*.zip"));
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(Messages.get("networks.file.allFiles"), "*.*"));
        FileChooserPreferences.applyLastDirectory(fileChooser);
        File file = fileChooser.showOpenDialog(detailsHost.getScene().getWindow());
        if (file != null) {
            FileChooserPreferences.saveLastDirectory(file);
            importFile(file.toPath());
        }
    }

    private record CracFile(String name, byte[] bytes, Optional<String> format) {
    }

    // the file is read and its format detected (by OpenRAO's importers, which parse it) off the FX thread, then its
    // parameters asked, then the CRAC created against the network
    void importFile(Path path) {
        Network root = network;
        if (root == null) {
            return;
        }
        java.time.Instant start = java.time.Instant.now();
        Service<CracFile> detection = new Service<>() {
            @Override
            protected Task<CracFile> createTask() {
                return new Task<>() {
                    @Override
                    protected CracFile call() throws IOException {
                        String name = path.getFileName().toString();
                        byte[] bytes = Files.readAllBytes(path);
                        return new CracFile(name, bytes, CracIO.format(name, bytes));
                    }
                };
            }
        };
        detection.setOnSucceeded(_ -> {
            CracFile file = detection.getValue();
            if (file.format().isEmpty()) {
                mainModel.getNotificationsModel().add(Notification.createError(start, "crac.import.unknownFormat").withMessageArgs(file.name()));
                return;
            }
            String format = file.format().get();
            askParameters(file.name(), format).ifPresent(parameters -> {
                lastImportParameters.put(format, parameters);
                importCrac(root, file.name(), file.bytes(), parameters);
            });
        });
        detection.setOnFailed(event -> {
            Throwable exception = event.getSource().getException();
            LOGGER.error("Failed to read {}", path, exception);
            mainModel.getNotificationsModel().add(Notification.createError(start, "crac.import.failed").withMessageArgs(path.getFileName(), exception.getMessage()));
        });
        detection.start();
    }

    // the CRAC Creation parameters tab's, with the file's own values asked, if the format has any (a time stamp, a
    // region); overridable by a test, which can't answer a modal dialog
    Optional<CracCreationParameters> askParameters(String fileName, String format) {
        CracCreationParameters parameters = CracImportParameters.copy(mainModel.getParametersModel().cracCreationParametersProperty().get());
        CracCreationParameters last = lastImportParameters.get(format);
        if (last != null && CracImportParameters.needsTimestamp(format) && CracImportParameters.timestamp(last, format) != null) {
            CracImportParameters.setTimestamp(parameters, format, CracImportParameters.timestamp(last, format));
        }
        if (last != null && CracImportParameters.needsCapacityCalculationRegion(format) && CracImportParameters.capacityCalculationRegion(parameters) == null) {
            CracImportParameters.setCapacityCalculationRegion(parameters, CracImportParameters.capacityCalculationRegion(last));
        }
        if (!CracImportParameters.needsTimestamp(format) && !CracImportParameters.needsCapacityCalculationRegion(format)) {
            return Optional.of(parameters);
        }
        return new CracImportDialog(detailsHost.getScene().getWindow(), fileName, format, parameters).showAndWait();
    }

    void importCrac(Network root, String fileName, byte[] bytes, CracCreationParameters parameters) {
        createCrac(root, fileName, () -> CracIO.read(fileName, bytes, root, parameters), "crac.import");
    }

    // an import or a generation, which reads the network: the messages are those of {@code keyPrefix}
    private void createCrac(Network root, String name, Callable<CracCreationContext> create, String keyPrefix) {
        if (mainModel.isBusy(root)) {
            mainModel.getNotificationsModel().add(Notification.createError(java.time.Instant.now(), "main.networkBusy"));
            return;
        }
        Service<CracCreationContext> creation = new Service<>() {
            @Override
            protected Task<CracCreationContext> createTask() {
                return new AbstractNetworkTask<>(mainModel, root) {
                    @Override
                    protected CracCreationContext compute() throws Exception {
                        return create.call();
                    }
                };
            }
        };
        Notification running = Notification.createRunning(keyPrefix + ".running", creation::cancel).withMessageArgs(name);
        mainModel.getNotificationsModel().add(running);
        creation.setOnSucceeded(_ -> {
            CracCreationContext context = creation.getValue();
            List<String> report = CracIO.report(context);
            NotificationAction viewReport = new NotificationAction(keyPrefix + ".viewReport", _ -> showReport(name, report));
            if (context.isCreationSuccessful()) {
                mainModel.getStudy(root).setCrac(context.getCrac(), report);
                mainModel.getNotificationsModel().replace(running,
                        Notification.createSuccess(running.startTimestamp(), keyPrefix + ".completed", viewReport).withMessageArgs(name));
            } else {
                // the CRAC in place is kept
                mainModel.getNotificationsModel().replace(running,
                        Notification.createError(running.startTimestamp(), keyPrefix + ".creationFailed", viewReport).withMessageArgs(name));
            }
        });
        creation.setOnFailed(event -> {
            Throwable exception = event.getSource().getException();
            LOGGER.error("Failed to create the CRAC {}", name, exception);
            mainModel.getNotificationsModel().replace(running,
                    Notification.createError(running.startTimestamp(), keyPrefix + ".failed").withMessageArgs(name, exception.getMessage()));
        });
        creation.setOnCancelled(_ -> mainModel.getNotificationsModel().replace(running,
                Notification.createCancelled(running.startTimestamp(), keyPrefix + ".cancelled").withMessageArgs(name)));
        mainModel.getStudy(root).trackComputation(creation);
        creation.start();
    }

    @FXML
    private void onGenerate() {
        if (network == null) {
            return;
        }
        if (study().getCrac() != null) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, Messages.get("crac.generate.confirm"), ButtonType.OK, ButtonType.CANCEL);
            alert.initOwner(detailsHost.getScene().getWindow());
            alert.setHeaderText(null);
            if (alert.showAndWait().filter(ButtonType.OK::equals).isEmpty()) {
                return;
            }
        }
        List<String> problems = CracGenerator.problems(mainModel.getParametersModel().cracGeneratorSettingsProperty().get());
        if (!problems.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.ERROR, Messages.get("crac.generate.problems", String.join("\n", problems)), ButtonType.OK);
            alert.initOwner(detailsHost.getScene().getWindow());
            alert.setHeaderText(null);
            alert.showAndWait();
            return;
        }
        generate();
    }

    // with copies of the CRAC Generator and CRAC Creation parameters, as they are now
    void generate() {
        Network root = network;
        CracGeneratorSettings settings = mainModel.getParametersModel().cracGeneratorSettingsProperty().get().copy();
        CracCreationParameters base = CracImportParameters.copy(mainModel.getParametersModel().cracCreationParametersProperty().get());
        createCrac(root, root.getNameOrId(), () -> CracGenerator.generate(root, settings, base), "crac.generate");
    }

    private void showReport(String fileName, List<String> report) {
        showLines(Messages.get("crac.report.title", fileName), report.isEmpty() ? List.of(Messages.get("crac.report.none")) : report);
    }

    private void showLines(String title, List<String> lines) {
        TextArea text = new TextArea(String.join("\n", lines));
        text.setEditable(false);
        text.setWrapText(true);
        text.setPrefSize(700, 400);
        Alert alert = new Alert(Alert.AlertType.INFORMATION, null, ButtonType.CLOSE);
        alert.initOwner(detailsHost.getScene() == null ? null : detailsHost.getScene().getWindow());
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.getDialogPane().setContent(text);
        alert.setResizable(true);
        alert.show();
    }

    @FXML
    private void onExport() {
        Crac crac = network == null ? null : study().getCrac();
        if (crac == null) {
            return;
        }
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(Messages.get("networks.file.supportedFiles", "json"), "*.json"));
        fileChooser.setInitialFileName(crac.getId() + ".json");
        FileChooserPreferences.applyLastDirectory(fileChooser);
        File file = fileChooser.showSaveDialog(detailsHost.getScene().getWindow());
        if (file != null) {
            FileChooserPreferences.saveLastDirectory(file);
            exportTo(file.toPath());
        }
    }

    void exportTo(Path path) {
        java.time.Instant start = java.time.Instant.now();
        try (OutputStream outputStream = Files.newOutputStream(path)) {
            CracIO.writeJson(study().getCrac(), outputStream);
        } catch (IOException | UncheckedIOException | PowsyblException | OpenRaoException e) {
            LOGGER.error("Failed to write {}", path, e);
            mainModel.getNotificationsModel().add(Notification.createError(start, "crac.export.failed").withMessageArgs(path, e.getMessage()));
        }
    }

    @FXML
    private void onRemove() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, Messages.get("crac.remove.confirm"), ButtonType.OK, ButtonType.CANCEL);
        alert.initOwner(detailsHost.getScene().getWindow());
        alert.setHeaderText(null);
        if (alert.showAndWait().filter(ButtonType.OK::equals).isPresent()) {
            remove();
        }
    }

    void remove() {
        study().setCrac(null, List.of());
    }

    @Override
    public void dispose() {
        watch(null);
        super.dispose();
    }
}
