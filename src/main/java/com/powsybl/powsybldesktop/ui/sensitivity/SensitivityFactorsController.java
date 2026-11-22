/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.ui.sensitivity;

import com.powsybl.commons.PowsyblException;
import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.ContingencyContext;
import com.powsybl.contingency.ContingencyContextType;
import com.powsybl.contingency.list.ContingencyList;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.powsybldesktop.contingency.ContingencyNames;
import com.powsybl.powsybldesktop.core.MainModel;
import com.powsybl.powsybldesktop.core.NetworkStudy;
import com.powsybl.powsybldesktop.notification.Notification;
import com.powsybl.powsybldesktop.parameters.ParametersModel;
import com.powsybl.powsybldesktop.sensitivity.SensitivityAnalysisRun;
import com.powsybl.powsybldesktop.sensitivity.SensitivityElements;
import com.powsybl.powsybldesktop.sensitivity.SensitivityFactorMatrix;
import com.powsybl.powsybldesktop.sensitivity.SensitivityFactorsIO;
import com.powsybl.powsybldesktop.sensitivity.SensitivityTexts;
import com.powsybl.powsybldesktop.ui.AbstractDisposableController;
import com.powsybl.powsybldesktop.ui.controls.FileChooserPreferences;
import com.powsybl.powsybldesktop.ui.window.SceneModel;
import com.powsybl.powsybldesktop.ui.window.SceneView;
import com.powsybl.powsybldesktop.utils.Messages;
import com.powsybl.sensitivity.SensitivityFunctionType;
import com.powsybl.sensitivity.SensitivityVariableSet;
import com.powsybl.sensitivity.SensitivityVariableType;
import com.powsybl.sensitivity.WeightedSensitivityVariable;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;
import javafx.util.converter.DoubleStringConverter;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The current root network's sensitivity factor matrices and variable sets: a list of each, the selected one's form
 * beneath, edited in place (the immutable matrices and variable sets are replaced in the study's lists). Elements are
 * picked among the network's eligible ones ({@link ElementPickerDialog}); a matrix or variable set Open Load Flow would
 * fail on, or skip, is marked and tells why ({@link SensitivityAnalysisRun#problems}), with the sensitivity analysis'
 * current AC/DC and the enabled contingency lists. Import and export in the app's own format for matrices, PowSyBl's
 * for factors and variable sets.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class SensitivityFactorsController extends AbstractDisposableController implements SceneView {

    private static final Logger LOGGER = LoggerFactory.getLogger(SensitivityFactorsController.class);

    @FXML
    MenuButton addMenuButton;
    @FXML
    Button removeButton;
    @FXML
    MenuButton importMenuButton;
    @FXML
    MenuButton exportMenuButton;
    @FXML
    TabPane tabPane;
    @FXML
    Tab matricesTab;
    @FXML
    Tab variableSetsTab;
    @FXML
    ListView<SensitivityFactorMatrix> matricesListView;
    @FXML
    ListView<SensitivityVariableSet> variableSetsListView;
    @FXML
    StackPane formHost;

    private SceneModel sceneModel;
    private MainModel mainModel;
    private Network network;
    private List<String> contingencyIds = List.of();
    // the shown form's problems, recomputed on any change they depend on
    private Runnable refreshFormProblems = () -> { };
    private final ListChangeListener<Object> studyListener = _ -> refreshProblems();
    // resolving them may take a while: only on their own changes
    private final ListChangeListener<Object> contingencyListsListener = _ -> refreshContingencyIds();
    private final List<Runnable> unlistenStudy = new ArrayList<>();
    // set while the study's lists are changed by the shown form, whose selection must not be rebuilt
    private boolean applyingReplace;

    @FXML
    private void initialize() {
        addMenuButton.getItems().addAll(menuItem("sensitivityFactors.add.matrix", this::addMatrix),
                menuItem("sensitivityFactors.add.variableSet", this::addVariableSet));
        importMenuButton.getItems().addAll(menuItem("sensitivityFactors.io.matrices", () -> importFile(this::importMatrices)),
                menuItem("sensitivityFactors.io.factors", () -> importFile(this::importFactors)),
                menuItem("sensitivityFactors.io.variableSets", () -> importFile(this::importVariableSets)));
        exportMenuButton.getItems().addAll(menuItem("sensitivityFactors.io.matrices", () -> exportFile(path -> SensitivityFactorsIO.writeMatrices(study().getSensitivityFactorMatrices(), path))),
                menuItem("sensitivityFactors.io.factors", () -> exportFile(path -> SensitivityFactorsIO.writeFactors(study().getSensitivityFactorMatrices(), path))),
                menuItem("sensitivityFactors.io.variableSets", () -> exportFile(path -> SensitivityFactorsIO.writeVariableSets(study().getSensitivityVariableSets(), path))));
        matricesTab.setText(Messages.get("sensitivityFactors.tab.matrices"));
        variableSetsTab.setText(Messages.get("sensitivityFactors.tab.variableSets"));
        matricesListView.setCellFactory(_ -> new ProblemCell<>(matrix -> Messages.get("sensitivityFactors.matrix.item", matrix.name(), matrix.factorCount()),
                this::problems));
        variableSetsListView.setCellFactory(_ -> new ProblemCell<>(set -> Messages.get("sensitivityFactors.variableSet.item", set.getId(), set.getVariables().size()),
                set -> SensitivityElements.problems(network, set)));
        matricesListView.getSelectionModel().selectedItemProperty().addListener((_, _, matrix) -> {
            if (!applyingReplace && matrix != null) {
                showMatrixForm(matrix);
            }
        });
        variableSetsListView.getSelectionModel().selectedItemProperty().addListener((_, _, set) -> {
            if (!applyingReplace && set != null) {
                showVariableSetForm(set);
            }
        });
        tabPane.getSelectionModel().selectedItemProperty().addListener((_, _, _) -> showSelectedForm());
        removeButton.setOnAction(_ -> onRemove());
    }

    private static MenuItem menuItem(String key, Runnable action) {
        MenuItem item = new MenuItem(Messages.get(key));
        item.setOnAction(_ -> action.run());
        return item;
    }

    @Override
    public void setSceneModel(SceneModel sceneModel) {
        this.sceneModel = Objects.requireNonNull(sceneModel);
        this.mainModel = sceneModel.getMainModel();
        listenerManager.listen(sceneModel.networkProperty(), (_, _, newValue) -> refreshForNetwork(newValue));
        // AC or DC
        listenerManager.listen(mainModel.getParametersModel().parametersRevisionProperty(), (_, _, _) -> refreshProblems());
        refreshForNetwork(sceneModel.getNetwork());
    }

    private NetworkStudy study() {
        return mainModel.getStudy(network);
    }

    // factors belong to the root network, the one a sensitivity analysis runs on
    private void refreshForNetwork(Network selectedNetwork) {
        Network newNetwork = selectedNetwork == null ? null : selectedNetwork.getNetwork();
        if (newNetwork != null && newNetwork == network) {
            return;
        }
        network = newNetwork;
        unlistenStudy();
        boolean hasNetwork = network != null;
        addMenuButton.setDisable(!hasNetwork);
        importMenuButton.setDisable(!hasNetwork);
        exportMenuButton.setDisable(!hasNetwork);
        removeButton.setDisable(!hasNetwork);
        matricesListView.setItems(hasNetwork ? study().getSensitivityFactorMatrices() : FXCollections.observableArrayList());
        variableSetsListView.setItems(hasNetwork ? study().getSensitivityVariableSets() : FXCollections.observableArrayList());
        if (hasNetwork) {
            listenStudy(study().getSensitivityFactorMatrices(), studyListener);
            listenStudy(study().getSensitivityVariableSets(), studyListener);
            listenStudy(study().getContingencyLists(), contingencyListsListener);
        }
        refreshContingencyIds();
        showSelectedForm();
    }

    @SuppressWarnings("unchecked")
    private void listenStudy(ObservableList<?> list, ListChangeListener<Object> listener) {
        ObservableList<Object> objects = (ObservableList<Object>) list;
        objects.addListener(listener);
        unlistenStudy.add(() -> objects.removeListener(listener));
    }

    private void unlistenStudy() {
        unlistenStudy.forEach(Runnable::run);
        unlistenStudy.clear();
    }

    private void refreshContingencyIds() {
        contingencyIds = network == null ? List.of() : contingencyIds(network);
        refreshProblems();
    }

    private void refreshProblems() {
        matricesListView.refresh();
        variableSetsListView.refresh();
        refreshFormProblems.run();
    }

    // the enabled contingency lists' ones, as a run resolves them
    private List<String> contingencyIds(Network root) {
        NetworkStudy study = mainModel.getStudy(root);
        List<Contingency> contingencies = new ArrayList<>();
        for (ContingencyList list : study.getContingencyLists()) {
            if (study.contingencyListEnabledProperty(list).get()) {
                try {
                    contingencies.addAll(list.getContingencies(root));
                } catch (PowsyblException | IllegalArgumentException e) {
                    LOGGER.warn("Contingency list '{}' could not be evaluated: {}", list.getName(), e.toString());
                }
            }
        }
        return ContingencyNames.deduplicate(contingencies).stream().map(Contingency::getId).toList();
    }

    private List<String> problems(SensitivityFactorMatrix matrix) {
        return SensitivityAnalysisRun.problems(network, matrix, study().getSensitivityVariableSets(), contingencyIds, loadFlowParameters());
    }

    // as the sensitivity analysis will run
    private LoadFlowParameters loadFlowParameters() {
        ParametersModel parameters = mainModel.getParametersModel();
        LoadFlowParameters loadFlowParameters = parameters.loadFlowParametersProperty().get().copy();
        return loadFlowParameters.setDc(parameters.sensitivityAnalysisModeProperty().get().isDc(parameters.loadFlowParametersProperty().get()));
    }

    private void showSelectedForm() {
        formHost.getChildren().clear();
        refreshFormProblems = () -> { };
        if (network == null) {
            return;
        }
        if (tabPane.getSelectionModel().getSelectedItem() == matricesTab) {
            SensitivityFactorMatrix matrix = matricesListView.getSelectionModel().getSelectedItem();
            if (matrix != null) {
                showMatrixForm(matrix);
            }
        } else {
            SensitivityVariableSet set = variableSetsListView.getSelectionModel().getSelectedItem();
            if (set != null) {
                showVariableSetForm(set);
            }
        }
    }

    // ----- Matrices -----

    private void addMatrix() {
        List<SensitivityFactorMatrix> matrices = study().getSensitivityFactorMatrices();
        SensitivityFactorMatrix matrix = new SensitivityFactorMatrix(Messages.get("sensitivityFactors.matrix.defaultName", matrices.size() + 1),
                SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, List.of(), SensitivityVariableType.INJECTION_ACTIVE_POWER, List.of(), false,
                ContingencyContext.all());
        matrices.add(matrix);
        tabPane.getSelectionModel().select(matricesTab);
        matricesListView.getSelectionModel().select(matrices.size() - 1);
    }

    // by identity: two matrices may be equal
    private boolean replaceMatrix(SensitivityFactorMatrix previous, SensitivityFactorMatrix replacement) {
        return replace(study().getSensitivityFactorMatrices(), matricesListView, previous, replacement);
    }

    private <T> boolean replace(List<T> list, ListView<T> listView, T previous, T replacement) {
        int index = indexOf(list, previous);
        if (index < 0) {
            return false;
        }
        boolean wasSelected = listView.getSelectionModel().getSelectedItem() == previous;
        applyingReplace = true;
        try {
            list.set(index, replacement);
            if (wasSelected) {
                listView.getSelectionModel().select(index);
            }
        } finally {
            applyingReplace = false;
        }
        return true;
    }

    private static <T> int indexOf(List<T> list, T element) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i) == element) {
                return i;
            }
        }
        return -1;
    }

    private void showMatrixForm(SensitivityFactorMatrix matrix) {
        SensitivityFactorMatrix[] current = {matrix};
        Label problemsLabel = problemsLabel();
        Label countLabel = new Label();
        Runnable refresh = () -> {
            showProblems(problemsLabel, problems(current[0]));
            countLabel.setText(Messages.get("sensitivityFactors.matrix.factorCount", current[0].factorCount()));
        };
        Consumer<SensitivityFactorMatrix> update = replacement -> {
            if (replaceMatrix(current[0], replacement)) {
                current[0] = replacement;
            }
            refresh.run();
        };

        TextField nameField = new TextField(matrix.name());
        commitOnEnterOrFocusLost(nameField, () -> {
            if (!nameField.getText().equals(current[0].name())) {
                update.accept(current[0].withName(nameField.getText()));
            }
        });

        ChoiceBox<SensitivityFunctionType> functionTypeBox = choiceBox(SensitivityFunctionType.values(), SensitivityTexts::function, matrix.functionType());
        ElementList functions = new ElementList(Messages.get("sensitivityFactors.matrix.functions"), matrix.functionIds(), this::elementName,
                () -> SensitivityElements.candidates(network, current[0].functionType()),
                ids -> update.accept(current[0].withFunctions(current[0].functionType(), ids)));
        functionTypeBox.valueProperty().addListener((_, _, type) -> update.accept(current[0].withFunctions(type, current[0].functionIds())));

        ChoiceBox<SensitivityVariableType> variableTypeBox = choiceBox(SensitivityVariableType.values(), SensitivityTexts::variable, matrix.variableType());
        CheckBox variableSetBox = new CheckBox(Messages.get("sensitivityFactors.matrix.variableSet"));
        variableSetBox.setSelected(matrix.variableSet());
        ElementList variables = new ElementList(Messages.get("sensitivityFactors.matrix.variables"), matrix.variableIds(),
                id -> current[0].variableSet() ? id : elementName(id),
                () -> current[0].variableSet() ? variableSetCandidates() : SensitivityElements.candidates(network, current[0].variableType()),
                ids -> update.accept(current[0].withVariables(current[0].variableType(), ids, current[0].variableSet())));
        variableTypeBox.valueProperty().addListener((_, _, type) ->
                update.accept(current[0].withVariables(type, current[0].variableIds(), current[0].variableSet())));
        // variable set ids and element ids don't mix
        variableSetBox.selectedProperty().addListener((_, _, variableSet) -> {
            update.accept(current[0].withVariables(current[0].variableType(), List.of(), variableSet));
            variables.setIds(List.of());
        });

        GridPane grid = formGrid();
        grid.addRow(0, new Label(Messages.get("sensitivityFactors.matrix.name")), nameField);
        grid.addRow(1, new Label(Messages.get("sensitivityFactors.matrix.functionType")), functionTypeBox);
        grid.addRow(2, new Label(), functions.node());
        grid.addRow(3, new Label(Messages.get("sensitivityFactors.matrix.variableType")), new HBox(10, variableTypeBox, variableSetBox));
        grid.addRow(4, new Label(), variables.node());
        grid.addRow(5, new Label(Messages.get("sensitivityFactors.matrix.contingencyContext")), contingencyContextNode(current, update));
        grid.addRow(6, new Label(), countLabel);
        showForm(grid, problemsLabel, refresh);
    }

    private List<SensitivityElements.Candidate> variableSetCandidates() {
        return study().getSensitivityVariableSets().stream()
                .map(set -> new SensitivityElements.Candidate(set.getId(), set.getId(), Messages.get("sensitivityFactors.variableSet.kind")))
                .toList();
    }

    private Node contingencyContextNode(SensitivityFactorMatrix[] current, Consumer<SensitivityFactorMatrix> update) {
        ContingencyContext context = current[0].contingencyContext();
        ChoiceBox<ContingencyContextType> typeBox = choiceBox(ContingencyContextType.values(),
                type -> Messages.get("sensitivityFactors.context." + type.name().toLowerCase(Locale.ROOT)), context.getContextType());
        List<String> ids = new ArrayList<>(contingencyIds);
        if (context.getContingencyId() != null && !ids.contains(context.getContingencyId())) {
            ids.add(context.getContingencyId());
        }
        ChoiceBox<String> contingencyBox = new ChoiceBox<>(FXCollections.observableArrayList(ids));
        contingencyBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(String id) {
                return id == null ? "" : ContingencyNames.displayName(id, network);
            }

            @Override
            public String fromString(String string) {
                throw new UnsupportedOperationException();
            }
        });
        contingencyBox.setValue(context.getContingencyId());
        contingencyBox.visibleProperty().bind(typeBox.valueProperty().isEqualTo(ContingencyContextType.SPECIFIC));
        Runnable apply = () -> {
            ContingencyContextType type = typeBox.getValue();
            String id = Objects.requireNonNullElse(contingencyBox.getValue(), ids.isEmpty() ? "" : ids.getFirst());
            update.accept(current[0].withContingencyContext(type == ContingencyContextType.SPECIFIC
                    ? ContingencyContext.specificContingency(id) : ContingencyContext.create(null, type)));
        };
        typeBox.valueProperty().addListener((_, _, _) -> apply.run());
        contingencyBox.valueProperty().addListener((_, _, _) -> apply.run());
        return new HBox(10, typeBox, contingencyBox);
    }

    // ----- Variable sets -----

    private void addVariableSet() {
        List<SensitivityVariableSet> sets = study().getSensitivityVariableSets();
        Set<String> ids = new HashSet<>(sets.stream().map(SensitivityVariableSet::getId).toList());
        int number = sets.size() + 1;
        while (ids.contains(Messages.get("sensitivityFactors.variableSet.defaultName", number))) {
            number++;
        }
        sets.add(new SensitivityVariableSet(Messages.get("sensitivityFactors.variableSet.defaultName", number), List.of()));
        tabPane.getSelectionModel().select(variableSetsTab);
        variableSetsListView.getSelectionModel().select(sets.size() - 1);
    }

    private void showVariableSetForm(SensitivityVariableSet set) {
        SensitivityVariableSet[] current = {set};
        Label problemsLabel = problemsLabel();
        Runnable refresh = () -> showProblems(problemsLabel, SensitivityElements.problems(network, current[0]));
        TableView<WeightedSensitivityVariable> table = new TableView<>(FXCollections.observableArrayList(set.getVariables()));
        Consumer<List<WeightedSensitivityVariable>> updateVariables = variables -> {
            SensitivityVariableSet replacement = new SensitivityVariableSet(current[0].getId(), variables);
            if (replace(study().getSensitivityVariableSets(), variableSetsListView, current[0], replacement)) {
                current[0] = replacement;
            }
            table.getItems().setAll(variables);
            refresh.run();
        };

        TextField idField = new TextField(set.getId());
        commitOnEnterOrFocusLost(idField, () -> {
            String id = idField.getText();
            if (id.equals(current[0].getId())) {
                return;
            }
            if (id.isBlank() || study().getSensitivityVariableSets().stream().anyMatch(other -> other.getId().equals(id))) {
                // Open Load Flow fails on duplicated ids
                idField.setText(current[0].getId());
                return;
            }
            rename(current, id);
            refresh.run();
        });

        TableColumn<WeightedSensitivityVariable, String> elementColumn = new TableColumn<>(Messages.get("sensitivityFactors.variableSet.element"));
        elementColumn.setCellValueFactory(cellData -> new ReadOnlyStringWrapper(elementName(cellData.getValue().getId())));
        elementColumn.setPrefWidth(320);
        TableColumn<WeightedSensitivityVariable, Double> weightColumn = new TableColumn<>(Messages.get("sensitivityFactors.variableSet.weight"));
        weightColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().getWeight()));
        weightColumn.setCellFactory(TextFieldTableCell.forTableColumn(new DoubleStringConverter()));
        weightColumn.setOnEditCommit(event -> {
            List<WeightedSensitivityVariable> variables = new ArrayList<>(current[0].getVariables());
            int index = event.getTablePosition().getRow();
            if (event.getNewValue() != null && Double.isFinite(event.getNewValue())) {
                variables.set(index, new WeightedSensitivityVariable(variables.get(index).getId(), event.getNewValue()));
            }
            updateVariables.accept(variables);
        });
        table.getColumns().addAll(List.of(elementColumn, weightColumn));
        table.setEditable(true);
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        table.setPrefHeight(320);

        Button add = new Button(Messages.get("sensitivityFactors.add.elements"));
        add.setOnAction(_ -> {
            Set<String> present = new HashSet<>(current[0].getVariables().stream().map(WeightedSensitivityVariable::getId).toList());
            List<String> picked = pick(Messages.get("sensitivityFactors.variableSet.add.title"), SensitivityElements.variableSetCandidates(network), present);
            if (!picked.isEmpty()) {
                List<WeightedSensitivityVariable> variables = new ArrayList<>(current[0].getVariables());
                picked.forEach(id -> variables.add(new WeightedSensitivityVariable(id, 1)));
                updateVariables.accept(variables);
            }
        });
        Button remove = new Button(Messages.get("desktop.common.action.remove"));
        remove.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        remove.setOnAction(_ -> {
            List<WeightedSensitivityVariable> removed = List.copyOf(table.getSelectionModel().getSelectedItems());
            updateVariables.accept(current[0].getVariables().stream().filter(v -> !removed.contains(v)).toList());
        });

        GridPane grid = formGrid();
        grid.addRow(0, new Label(Messages.get("sensitivityFactors.variableSet.id")), idField);
        grid.addRow(1, new Label(Messages.get("sensitivityFactors.variableSet.variables")), new VBox(4, table, new HBox(6, add, remove)));
        showForm(grid, problemsLabel, refresh);
    }

    // the matrices using the set follow it
    private void rename(SensitivityVariableSet[] current, String id) {
        String previousId = current[0].getId();
        SensitivityVariableSet renamed = new SensitivityVariableSet(id, List.copyOf(current[0].getVariables()));
        if (replace(study().getSensitivityVariableSets(), variableSetsListView, current[0], renamed)) {
            current[0] = renamed;
        }
        ObservableList<SensitivityFactorMatrix> matrices = study().getSensitivityFactorMatrices();
        for (int i = 0; i < matrices.size(); i++) {
            SensitivityFactorMatrix matrix = matrices.get(i);
            if (matrix.variableSet() && matrix.variableIds().contains(previousId)) {
                matrices.set(i, matrix.withVariables(matrix.variableType(),
                        matrix.variableIds().stream().map(v -> v.equals(previousId) ? id : v).toList(), true));
            }
        }
    }

    // ----- Shared -----

    private void onRemove() {
        if (tabPane.getSelectionModel().getSelectedItem() == matricesTab) {
            SensitivityFactorMatrix matrix = matricesListView.getSelectionModel().getSelectedItem();
            if (matrix != null) {
                study().getSensitivityFactorMatrices().remove(indexOf(study().getSensitivityFactorMatrices(), matrix));
            }
        } else {
            SensitivityVariableSet set = variableSetsListView.getSelectionModel().getSelectedItem();
            if (set != null) {
                study().getSensitivityVariableSets().remove(set);
            }
        }
        showSelectedForm();
    }

    // the problems first, where they are seen
    private void showForm(GridPane grid, Label problemsLabel, Runnable refresh) {
        refreshFormProblems = refresh;
        refresh.run();
        grid.getChildren().forEach(child -> GridPane.setValignment(child, VPos.TOP));
        VBox form = new VBox(8, problemsLabel, grid);
        form.setPadding(new Insets(10));
        formHost.getChildren().setAll(form);
    }

    private static GridPane formGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        return grid;
    }

    private static Label problemsLabel() {
        Label label = new Label();
        label.setWrapText(true);
        label.getStyleClass().add("alert-label");
        label.managedProperty().bind(label.visibleProperty());
        return label;
    }

    private static void showProblems(Label label, List<String> problems) {
        label.setText(String.join("\n", problems));
        label.setVisible(!problems.isEmpty());
    }

    private static <T> ChoiceBox<T> choiceBox(T[] values, Function<T, String> text, T value) {
        ChoiceBox<T> box = new ChoiceBox<>(FXCollections.observableArrayList(values));
        box.setConverter(new StringConverter<>() {
            @Override
            public String toString(T t) {
                return t == null ? "" : text.apply(t);
            }

            @Override
            public T fromString(String string) {
                throw new UnsupportedOperationException();
            }
        });
        box.setValue(value);
        return box;
    }

    private static void commitOnEnterOrFocusLost(TextField field, Runnable commit) {
        field.setOnAction(_ -> commit.run());
        field.focusedProperty().addListener((_, wasFocused, focused) -> {
            if (Boolean.TRUE.equals(wasFocused) && Boolean.FALSE.equals(focused)) {
                commit.run();
            }
        });
    }

    private String elementName(String id) {
        Identifiable<?> identifiable = network.getIdentifiable(id);
        return identifiable == null || identifiable.getNameOrId().equals(id) ? id : identifiable.getNameOrId() + " (" + id + ")";
    }

    private List<String> pick(String title, List<SensitivityElements.Candidate> candidates, Set<String> present) {
        return new ElementPickerDialog(formHost.getScene().getWindow(), title, candidates, present).showAndWait().orElse(List.of());
    }

    /**
     * An editable list of element ids: added from a picker, removed by selection.
     */
    private final class ElementList {
        private final ListView<String> listView = new ListView<>();
        private final VBox node;

        ElementList(String title, List<String> ids, Function<String, String> name, Supplier<List<SensitivityElements.Candidate>> candidates,
                    Consumer<List<String>> onChange) {
            listView.getItems().setAll(ids);
            listView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
            listView.setPrefHeight(130);
            listView.setCellFactory(_ -> new ListCell<>() {
                @Override
                protected void updateItem(String id, boolean empty) {
                    super.updateItem(id, empty);
                    setText(empty || id == null ? null : name.apply(id));
                }
            });
            Button add = new Button(Messages.get("sensitivityFactors.add.elements"));
            add.setOnAction(_ -> {
                List<String> picked = pick(title, candidates.get(), new HashSet<>(listView.getItems()));
                if (!picked.isEmpty()) {
                    listView.getItems().addAll(picked);
                    onChange.accept(List.copyOf(listView.getItems()));
                }
            });
            Button remove = new Button(Messages.get("desktop.common.action.remove"));
            remove.disableProperty().bind(listView.getSelectionModel().selectedItemProperty().isNull());
            remove.setOnAction(_ -> {
                listView.getItems().removeAll(List.copyOf(listView.getSelectionModel().getSelectedItems()));
                onChange.accept(List.copyOf(listView.getItems()));
            });
            Label titleLabel = new Label(title);
            node = new VBox(4, titleLabel, listView, new HBox(6, add, remove));
            HBox.setHgrow(node, Priority.ALWAYS);
        }

        void setIds(List<String> ids) {
            listView.getItems().setAll(ids);
        }

        Node node() {
            return node;
        }
    }

    /**
     * An item and, when Open Load Flow would fail on it, a warning icon telling why.
     */
    private static final class ProblemCell<T> extends ListCell<T> {
        private final Function<T, String> text;
        private final Function<T, List<String>> problems;

        ProblemCell(Function<T, String> text, Function<T, List<String>> problems) {
            this.text = text;
            this.problems = problems;
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
            List<String> itemProblems = problems.apply(item);
            if (!itemProblems.isEmpty()) {
                FontIcon icon = new FontIcon("mdi2a-alert");
                icon.getStyleClass().add("alert-icon");
                setGraphic(icon);
                setTooltip(new Tooltip(String.join("\n", itemProblems)));
            }
        }
    }

    // ----- Import / export -----

    interface FileAction {
        void run(Path path) throws IOException;
    }

    private void importFile(FileAction action) {
        File file = fileChooser().showOpenDialog(formHost.getScene().getWindow());
        if (file != null) {
            FileChooserPreferences.saveLastDirectory(file);
            run(action, file.toPath(), "sensitivityFactors.import.failed");
        }
    }

    private void exportFile(FileAction action) {
        File file = fileChooser().showSaveDialog(formHost.getScene().getWindow());
        if (file != null) {
            FileChooserPreferences.saveLastDirectory(file);
            run(action, file.toPath(), "sensitivityFactors.export.failed");
        }
    }

    void run(FileAction action, Path path, String failedKey) {
        Instant start = Instant.now();
        try {
            action.run(path);
        } catch (IOException | UncheckedIOException | PowsyblException | IllegalArgumentException e) {
            LOGGER.error("Failed to read or write {}", path, e);
            mainModel.getNotificationsModel().add(Notification.createError(start, failedKey).withMessageArgs(path));
        }
    }

    void importMatrices(Path path) throws IOException {
        study().getSensitivityFactorMatrices().addAll(SensitivityFactorsIO.readMatrices(path));
    }

    void importFactors(Path path) throws IOException {
        String fileName = path.getFileName().toString();
        String name = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
        study().getSensitivityFactorMatrices().addAll(SensitivityFactorsIO.readFactors(path, name));
    }

    // a variable set replaces the one of the same id, ids being unique
    void importVariableSets(Path path) throws IOException {
        ObservableList<SensitivityVariableSet> sets = study().getSensitivityVariableSets();
        for (SensitivityVariableSet set : SensitivityFactorsIO.readVariableSets(path)) {
            int index = sets.stream().map(SensitivityVariableSet::getId).toList().indexOf(set.getId());
            if (index >= 0) {
                sets.set(index, set);
            } else {
                sets.add(set);
            }
        }
    }

    private static FileChooser fileChooser() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(Messages.get("networks.file.supportedFiles", "json"), "*.json"));
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(Messages.get("networks.file.allFiles"), "*.*"));
        FileChooserPreferences.applyLastDirectory(fileChooser);
        return fileChooser;
    }

    @Override
    public void dispose() {
        unlistenStudy();
        super.dispose();
    }
}
