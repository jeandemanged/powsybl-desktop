/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.loadflow.parameters;

import com.powsybl.commons.parameters.Parameter;
import com.powsybl.commons.parameters.ParameterType;
import com.powsybl.iidm.network.Country;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.json.JsonLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.powsybldesktop.utils.AbstractDisposableController;
import com.powsybl.powsybldesktop.utils.FileChooserPreferences;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.beans.property.ObjectProperty;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import org.controlsfx.control.CheckComboBox;

import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Builds one category per OpenLoadFlow parameter category (base {@link LoadFlowParameters} fields plus
 * {@link OpenLoadFlowParameters#SPECIFIC_PARAMETERS} metadata), and picks a control per {@link ParameterType}.
 * Categories are presented as a side list + detail pane rather than a tab strip: with 21 categories, a
 * single-row TabPane would overflow the window width and JavaFX has no built-in wrapped/multi-row tab layout.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class LoadFlowParametersController extends AbstractDisposableController {

    // Category key -> title, in display order. Base LoadFlowParameters fields not covered by
    // OpenLoadFlowParameters.BASE_PARAMETERS_CATEGORY (i.e. debugDir) are placed under Debug.
    private static final Map<String, String> CATEGORY_TITLES = buildCategoryTitles();

    @FXML
    public SplitPane splitPane;

    private ObjectProperty<LoadFlowParameters> loadFlowParametersProperty;
    private final Map<String, GridPane> categoryGrids = new LinkedHashMap<>();
    private final List<Runnable> refreshers = new ArrayList<>();
    private boolean refreshing;

    static Map<String, String> buildCategoryTitles() {
        Map<String, String> titles = new LinkedHashMap<>();
        titles.put(OpenLoadFlowParameters.MODEL_CATEGORY_KEY, Messages.get("loadflow.category.model"));
        titles.put(OpenLoadFlowParameters.DC_CATEGORY_KEY, Messages.get("loadflow.category.dc"));
        titles.put(OpenLoadFlowParameters.SLACK_DISTRIBUTION_CATEGORY_KEY, Messages.get("loadflow.category.slackDistribution"));
        titles.put(OpenLoadFlowParameters.REFERENCE_BUS_CATEGORY_KEY, Messages.get("loadflow.category.referenceBus"));
        titles.put(OpenLoadFlowParameters.VOLTAGE_CONTROLS_CATEGORY_KEY, Messages.get("loadflow.category.voltageControls"));
        titles.put(OpenLoadFlowParameters.GENERATOR_VOLTAGE_CONTROL_CATEGORY_KEY, Messages.get("loadflow.category.generatorVoltageControl"));
        titles.put(OpenLoadFlowParameters.TRANSFORMER_VOLTAGE_CONTROL_CATEGORY_KEY, Messages.get("loadflow.category.transformerVoltageControl"));
        titles.put(OpenLoadFlowParameters.SHUNT_VOLTAGE_CONTROL_CATEGORY_KEY, Messages.get("loadflow.category.shuntVoltageControl"));
        titles.put(OpenLoadFlowParameters.PHASE_CONTROL_CATEGORY_KEY, Messages.get("loadflow.category.phaseControl"));
        titles.put(OpenLoadFlowParameters.REACTIVE_POWER_CONTROL_CATEGORY_KEY, Messages.get("loadflow.category.reactivePowerControl"));
        titles.put(OpenLoadFlowParameters.VOLTAGE_INIT_CATEGORY_KEY, Messages.get("loadflow.category.voltageInit"));
        titles.put(OpenLoadFlowParameters.NEWTON_RAPHSON_CATEGORY_KEY, Messages.get("loadflow.category.newtonRaphson"));
        titles.put(OpenLoadFlowParameters.NEWTON_KRYLOV_CATEGORY_KEY, Messages.get("loadflow.category.newtonKrylov"));
        titles.put(OpenLoadFlowParameters.SOLVER_CATEGORY_KEY, Messages.get("loadflow.category.solver"));
        titles.put(OpenLoadFlowParameters.OUTER_LOOPS_CATEGORY_KEY, Messages.get("loadflow.category.outerLoops"));
        titles.put(OpenLoadFlowParameters.FAST_RESTART_CATEGORY_KEY, Messages.get("loadflow.category.fastRestart"));
        titles.put(OpenLoadFlowParameters.AUTOMATION_CATEGORY_KEY, Messages.get("loadflow.category.automation"));
        titles.put(OpenLoadFlowParameters.HVDC_CATEGORY_KEY, Messages.get("loadflow.category.hvdc"));
        titles.put(OpenLoadFlowParameters.PERFORMANCE_CATEGORY_KEY, Messages.get("loadflow.category.performance"));
        titles.put(OpenLoadFlowParameters.DEBUG_CATEGORY_KEY, Messages.get("loadflow.category.debug"));
        titles.put(OpenLoadFlowParameters.REPORTING_CATEGORY_KEY, Messages.get("loadflow.category.reporting"));
        return titles;
    }

    public void setLoadFlowParametersProperty(ObjectProperty<LoadFlowParameters> loadFlowParametersProperty) {
        this.loadFlowParametersProperty = Objects.requireNonNull(loadFlowParametersProperty);
        refresh();
        listenerManager.listen(loadFlowParametersProperty, (observable, oldValue, newValue) -> refresh());
    }

    private void refresh() {
        refreshing = true;
        try {
            refreshers.forEach(Runnable::run);
        } finally {
            refreshing = false;
        }
    }

    @FXML
    private void initialize() {
        CATEGORY_TITLES.keySet().forEach(category -> categoryGrids.put(category, createCategoryGrid()));

        addBaseParameterFields();
        addSpecificParameterFields();

        ListView<String> categoryListView = new ListView<>();
        Map<String, ScrollPane> categoryPanes = new LinkedHashMap<>();
        CATEGORY_TITLES.forEach((category, title) -> {
            GridPane grid = categoryGrids.get(category);
            if (grid.getChildren().isEmpty()) {
                return;
            }
            ScrollPane scrollPane = new ScrollPane(grid);
            scrollPane.setFitToWidth(true);
            categoryPanes.put(title, scrollPane);
            categoryListView.getItems().add(title);
        });

        StackPane detailPane = new StackPane();
        categoryListView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                detailPane.getChildren().setAll(categoryPanes.get(newValue));
            }
        });
        categoryListView.setPrefWidth(220);
        categoryListView.getSelectionModel().selectFirst();

        splitPane.getItems().addAll(categoryListView, detailPane);
        splitPane.setDividerPositions(0.18);
    }

    private GridPane createCategoryGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(10));
        ColumnConstraints labelColumn = new ColumnConstraints();
        labelColumn.setHalignment(HPos.RIGHT);
        labelColumn.setPrefWidth(320);
        labelColumn.setMaxWidth(320);
        ColumnConstraints controlColumn = new ColumnConstraints();
        controlColumn.setHalignment(HPos.LEFT);
        controlColumn.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(labelColumn, controlColumn);
        return grid;
    }

    private void addRow(String category, String labelText, String tooltipText, Control control) {
        GridPane grid = categoryGrids.get(category);
        Label label = new Label(labelText);
        label.setWrapText(true);
        if (tooltipText != null) {
            Tooltip tooltip = new Tooltip(tooltipText);
            label.setTooltip(tooltip);
            control.setTooltip(tooltip);
        }
        grid.addRow(grid.getRowCount(), label, control);
    }

    private static void bindCommit(TextField textField, Runnable commit) {
        textField.setOnAction(event -> commit.run());
        textField.focusedProperty().addListener((observable, wasFocused, isFocused) -> {
            if (Boolean.TRUE.equals(wasFocused) && Boolean.FALSE.equals(isFocused)) {
                commit.run();
            }
        });
    }

    private static String formatValues(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(Object::toString).collect(Collectors.joining(","));
        }
        return value.toString();
    }

    // ----- Base LoadFlowParameters fields (no rich metadata available, hand-wired) -----

    private void addBaseParameterFields() {
        addAcDcField();
        addBooleanField(OpenLoadFlowParameters.MODEL_CATEGORY_KEY, Messages.get("loadflow.param.twtSplitShuntAdmittance.label"),
                Messages.get("loadflow.param.twtSplitShuntAdmittance.tooltip"),
                LoadFlowParameters::isTwtSplitShuntAdmittance, LoadFlowParameters::setTwtSplitShuntAdmittance);
        addEnumField(OpenLoadFlowParameters.PERFORMANCE_CATEGORY_KEY, Messages.get("loadflow.param.componentMode.label"),
                Messages.get("loadflow.param.componentMode.tooltip"), LoadFlowParameters.ComponentMode.class,
                LoadFlowParameters::getComponentMode, LoadFlowParameters::setComponentMode);
        addBooleanField(OpenLoadFlowParameters.TRANSFORMER_VOLTAGE_CONTROL_CATEGORY_KEY, Messages.get("loadflow.param.transformerVoltageControl.label"),
                Messages.get("loadflow.param.transformerVoltageControl.tooltip"),
                LoadFlowParameters::isTransformerVoltageControlOn, LoadFlowParameters::setTransformerVoltageControlOn);
        addBooleanField(OpenLoadFlowParameters.PHASE_CONTROL_CATEGORY_KEY, Messages.get("loadflow.param.phaseShifterRegulation.label"),
                Messages.get("loadflow.param.phaseShifterRegulation.tooltip"),
                LoadFlowParameters::isPhaseShifterRegulationOn, LoadFlowParameters::setPhaseShifterRegulationOn);
        addBooleanField(OpenLoadFlowParameters.SHUNT_VOLTAGE_CONTROL_CATEGORY_KEY, Messages.get("loadflow.param.shuntVoltageControl.label"),
                Messages.get("loadflow.param.shuntVoltageControl.tooltip"),
                LoadFlowParameters::isShuntCompensatorVoltageControlOn, LoadFlowParameters::setShuntCompensatorVoltageControlOn);
        addBooleanField(OpenLoadFlowParameters.SLACK_DISTRIBUTION_CATEGORY_KEY, Messages.get("loadflow.param.distributedSlack.label"),
                Messages.get("loadflow.param.distributedSlack.tooltip"),
                LoadFlowParameters::isDistributedSlack, LoadFlowParameters::setDistributedSlack);
        addEnumField(OpenLoadFlowParameters.SLACK_DISTRIBUTION_CATEGORY_KEY, Messages.get("loadflow.param.balanceType.label"),
                Messages.get("loadflow.param.balanceType.tooltip"), LoadFlowParameters.BalanceType.class,
                LoadFlowParameters::getBalanceType, LoadFlowParameters::setBalanceType);
        addBooleanField(OpenLoadFlowParameters.SLACK_DISTRIBUTION_CATEGORY_KEY, Messages.get("loadflow.param.readSlackBus.label"),
                Messages.get("loadflow.param.readSlackBus.tooltip"),
                LoadFlowParameters::isReadSlackBus, LoadFlowParameters::setReadSlackBus);
        addBooleanField(OpenLoadFlowParameters.SLACK_DISTRIBUTION_CATEGORY_KEY, Messages.get("loadflow.param.writeSlackBus.label"),
                Messages.get("loadflow.param.writeSlackBus.tooltip"),
                LoadFlowParameters::isWriteSlackBus, LoadFlowParameters::setWriteSlackBus);
        addCountriesToBalanceField();
        addVoltageInitField();
        addBooleanField(OpenLoadFlowParameters.VOLTAGE_CONTROLS_CATEGORY_KEY, Messages.get("loadflow.param.useReactiveLimits.label"),
                Messages.get("loadflow.param.useReactiveLimits.tooltip"),
                LoadFlowParameters::isUseReactiveLimits, LoadFlowParameters::setUseReactiveLimits);
        addBooleanField(OpenLoadFlowParameters.DC_CATEGORY_KEY, Messages.get("loadflow.param.dcUseTransformerRatio.label"),
                Messages.get("loadflow.param.dcUseTransformerRatio.tooltip"),
                LoadFlowParameters::isDcUseTransformerRatio, LoadFlowParameters::setDcUseTransformerRatio);
        addDcPowerFactorField();
        addBooleanField(OpenLoadFlowParameters.HVDC_CATEGORY_KEY, Messages.get("loadflow.param.hvdcAcEmulation.label"),
                Messages.get("loadflow.param.hvdcAcEmulation.tooltip"),
                LoadFlowParameters::isHvdcAcEmulation, LoadFlowParameters::setHvdcAcEmulation);
        addDebugDirField();
    }

    private void addAcDcField() {
        RadioButton ac = new RadioButton("AC");
        RadioButton dc = new RadioButton("DC");
        ToggleGroup group = new ToggleGroup();
        ac.setToggleGroup(group);
        dc.setToggleGroup(group);
        refreshers.add(() -> {
            boolean isDc = loadFlowParametersProperty.getValue().isDc();
            dc.setSelected(isDc);
            ac.setSelected(!isDc);
        });
        dc.selectedProperty().addListener((observable, oldValue, newValue) -> {
            if (!refreshing) {
                loadFlowParametersProperty.getValue().setDc(newValue);
            }
        });
        HBox hBox = new HBox(20, ac, dc);
        hBox.setAlignment(Pos.CENTER_LEFT);
        Tooltip tooltip = new Tooltip(Messages.get("loadflow.param.model.tooltip"));
        ac.setTooltip(tooltip);
        dc.setTooltip(tooltip);
        Label label = new Label(Messages.get("loadflow.param.model.label"));
        label.setTooltip(tooltip);
        GridPane grid = categoryGrids.get(OpenLoadFlowParameters.MODEL_CATEGORY_KEY);
        grid.addRow(grid.getRowCount(), label, hBox);
    }

    private void addBooleanField(String category, String label, String tooltip,
                                  Predicate<LoadFlowParameters> getter, BiConsumer<LoadFlowParameters, Boolean> setter) {
        CheckBox checkBox = new CheckBox();
        refreshers.add(() -> checkBox.setSelected(getter.test(loadFlowParametersProperty.getValue())));
        checkBox.selectedProperty().addListener((observable, oldValue, newValue) -> {
            if (!refreshing) {
                setter.accept(loadFlowParametersProperty.getValue(), newValue);
            }
        });
        addRow(category, label, tooltip, checkBox);
    }

    private <E extends Enum<E>> void addEnumField(String category, String label, String tooltip, Class<E> enumClass,
                                                    Function<LoadFlowParameters, E> getter, BiConsumer<LoadFlowParameters, E> setter) {
        ChoiceBox<E> choiceBox = new ChoiceBox<>(FXCollections.observableArrayList(enumClass.getEnumConstants()));
        refreshers.add(() -> choiceBox.setValue(getter.apply(loadFlowParametersProperty.getValue())));
        choiceBox.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (!refreshing && newValue != null) {
                setter.accept(loadFlowParametersProperty.getValue(), newValue);
            }
        });
        addRow(category, label, tooltip, choiceBox);
    }

    private void addCountriesToBalanceField() {
        CheckComboBox<String> checkComboBox = new CheckComboBox<>(FXCollections.observableArrayList(
                Arrays.stream(Country.values()).map(Country::name).sorted().toList()));
        checkComboBox.setTitle(Messages.get("loadflow.param.countriesToBalance.title"));
        refreshers.add(() -> {
            Set<String> selected = loadFlowParametersProperty.getValue().getCountriesToBalance().stream()
                    .map(Country::name).collect(Collectors.toSet());
            checkComboBox.getCheckModel().clearChecks();
            checkComboBox.getItems().stream().filter(selected::contains).forEach(checkComboBox.getCheckModel()::check);
        });
        checkComboBox.getCheckModel().getCheckedItems().addListener((javafx.collections.ListChangeListener<String>) change -> {
            if (!refreshing) {
                Set<Country> countries = checkComboBox.getCheckModel().getCheckedItems().stream()
                        .map(Country::valueOf).collect(Collectors.toSet());
                loadFlowParametersProperty.getValue().setCountriesToBalance(countries);
            }
        });
        addRow(OpenLoadFlowParameters.SLACK_DISTRIBUTION_CATEGORY_KEY, Messages.get("loadflow.param.countriesToBalance.label"),
                Messages.get("loadflow.param.countriesToBalance.tooltip"), checkComboBox);
    }

    private void addVoltageInitField() {
        ChoiceBox<String> choiceBox = new ChoiceBox<>(FXCollections.observableArrayList(
                LoadFlowParameters.VoltageInitMode.UNIFORM_VALUES.toString(),
                LoadFlowParameters.VoltageInitMode.DC_VALUES.toString(),
                LoadFlowParameters.VoltageInitMode.PREVIOUS_VALUES.toString(),
                OpenLoadFlowParameters.VoltageInitModeOverride.VOLTAGE_MAGNITUDE.toString(),
                OpenLoadFlowParameters.VoltageInitModeOverride.FULL_VOLTAGE.toString()
        ));
        refreshers.add(() -> {
            LoadFlowParameters parameters = loadFlowParametersProperty.getValue();
            OpenLoadFlowParameters parametersExt = OpenLoadFlowParameters.get(parameters);
            if (parametersExt.getVoltageInitModeOverride() == OpenLoadFlowParameters.VoltageInitModeOverride.NONE) {
                choiceBox.setValue(parameters.getVoltageInitMode().toString());
            } else {
                choiceBox.setValue(parametersExt.getVoltageInitModeOverride().toString());
            }
        });
        choiceBox.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (refreshing || newValue == null) {
                return;
            }
            LoadFlowParameters parameters = loadFlowParametersProperty.getValue();
            OpenLoadFlowParameters parametersExt = OpenLoadFlowParameters.get(parameters);
            try {
                LoadFlowParameters.VoltageInitMode mode = LoadFlowParameters.VoltageInitMode.valueOf(newValue);
                parameters.setVoltageInitMode(mode);
                parametersExt.setVoltageInitModeOverride(OpenLoadFlowParameters.VoltageInitModeOverride.NONE);
            } catch (IllegalArgumentException ignored) {
                OpenLoadFlowParameters.VoltageInitModeOverride mode = OpenLoadFlowParameters.VoltageInitModeOverride.valueOf(newValue);
                parameters.setVoltageInitMode(LoadFlowParameters.VoltageInitMode.UNIFORM_VALUES);
                parametersExt.setVoltageInitModeOverride(mode);
            }
        });
        addRow(OpenLoadFlowParameters.VOLTAGE_INIT_CATEGORY_KEY, Messages.get("loadflow.param.voltageInitMode.label"),
                Messages.get("loadflow.param.voltageInitMode.tooltip"),
                choiceBox);
    }

    private void addDcPowerFactorField() {
        TextField textField = new TextField();
        refreshers.add(() -> textField.setText(Double.toString(loadFlowParametersProperty.getValue().getDcPowerFactor())));
        Runnable commit = () -> {
            if (refreshing) {
                return;
            }
            try {
                loadFlowParametersProperty.getValue().setDcPowerFactor(Double.parseDouble(textField.getText()));
            } catch (NumberFormatException e) {
                refresh();
            }
        };
        bindCommit(textField, commit);
        addRow(OpenLoadFlowParameters.DC_CATEGORY_KEY, Messages.get("loadflow.param.dcPowerFactor.label"),
                Messages.get("loadflow.param.dcPowerFactor.tooltip"), textField);
    }

    private void addDebugDirField() {
        TextField textField = new TextField();
        refreshers.add(() -> textField.setText(Objects.toString(loadFlowParametersProperty.getValue().getDebugDir(), "")));
        Runnable commit = () -> {
            if (refreshing) {
                return;
            }
            String text = textField.getText();
            loadFlowParametersProperty.getValue().setDebugDir(text.isEmpty() ? null : text);
        };
        bindCommit(textField, commit);
        addRow(OpenLoadFlowParameters.DEBUG_CATEGORY_KEY, Messages.get("loadflow.param.debugDir.label"),
                Messages.get("loadflow.param.debugDir.tooltip"), textField);
    }

    // ----- OpenLoadFlowParameters specific parameters, driven entirely by their metadata -----

    private void addSpecificParameterFields() {
        for (Parameter parameter : OpenLoadFlowParameters.SPECIFIC_PARAMETERS) {
            if (OpenLoadFlowParameters.VOLTAGE_INIT_MODE_OVERRIDE_PARAM_NAME.equals(parameter.getName())) {
                continue; // merged into the combined Voltage Initialization Mode control above
            }
            if (!categoryGrids.containsKey(parameter.getCategoryKey())) {
                continue;
            }
            addSpecificParameterField(parameter);
        }
    }

    private void addSpecificParameterField(Parameter parameter) {
        Control control = switch (parameter.getType()) {
            case BOOLEAN -> buildBooleanControl(parameter);
            case STRING -> parameter.getPossibleValues() != null ? buildChoiceControl(parameter) : buildStringControl(parameter);
            case INTEGER, DOUBLE -> buildNumberControl(parameter);
            case STRING_LIST -> parameter.getPossibleValues() != null ? buildCheckListControl(parameter) : buildListControl(parameter);
        };
        String tooltip = parameter.getDescription()
                + Messages.get("parameter.tooltip.suffix", parameter.getName(), parameter.getDefaultValue());
        addRow(parameter.getCategoryKey(), parameter.getDescription(), tooltip, control);
    }

    private Object currentSpecificValue(Parameter parameter) {
        return OpenLoadFlowParameters.get(loadFlowParametersProperty.getValue()).toMap().get(parameter.getName());
    }

    private void applySpecificUpdate(Parameter parameter, String value) {
        Map<String, String> update = new HashMap<>();
        update.put(parameter.getName(), value);
        OpenLoadFlowParameters.get(loadFlowParametersProperty.getValue()).update(update);
    }

    private CheckBox buildBooleanControl(Parameter parameter) {
        CheckBox checkBox = new CheckBox();
        refreshers.add(() -> checkBox.setSelected(Boolean.TRUE.equals(currentSpecificValue(parameter))));
        checkBox.selectedProperty().addListener((observable, oldValue, newValue) -> {
            if (!refreshing) {
                applySpecificUpdate(parameter, newValue.toString());
            }
        });
        return checkBox;
    }

    private ChoiceBox<String> buildChoiceControl(Parameter parameter) {
        ChoiceBox<String> choiceBox = new ChoiceBox<>(FXCollections.observableArrayList(
                parameter.getPossibleValues().stream().map(Object::toString).toList()));
        refreshers.add(() -> choiceBox.setValue(Objects.toString(currentSpecificValue(parameter), null)));
        choiceBox.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (!refreshing && newValue != null) {
                applySpecificUpdate(parameter, newValue);
            }
        });
        return choiceBox;
    }

    // covers debugDir and other free-form strings; empty text maps to null only when null is a valid
    // (and distinct-from-empty-string) value for that parameter, i.e. its metadata default is null
    private TextField buildStringControl(Parameter parameter) {
        TextField textField = new TextField();
        boolean nullable = parameter.getDefaultValue() == null;
        refreshers.add(() -> textField.setText(Objects.toString(currentSpecificValue(parameter), "")));
        Runnable commit = () -> {
            if (refreshing) {
                return;
            }
            String text = textField.getText();
            applySpecificUpdate(parameter, nullable && text.isEmpty() ? null : text);
        };
        bindCommit(textField, commit);
        return textField;
    }

    private TextField buildNumberControl(Parameter parameter) {
        TextField textField = new TextField();
        refreshers.add(() -> textField.setText(Objects.toString(currentSpecificValue(parameter), "")));
        Runnable commit = () -> {
            if (refreshing) {
                return;
            }
            String text = textField.getText();
            try {
                if (parameter.getType() == ParameterType.INTEGER) {
                    Integer.parseInt(text);
                } else {
                    Double.parseDouble(text);
                }
                applySpecificUpdate(parameter, text);
            } catch (NumberFormatException e) {
                refresh(); // revert display to the last valid value
            }
        };
        bindCommit(textField, commit);
        return textField;
    }

    // free-form string list (e.g. equipment IDs); no closed set of possible values to check against
    private TextField buildListControl(Parameter parameter) {
        TextField textField = new TextField();
        textField.setPromptText("comma or colon separated list");
        boolean nullable = parameter.getDefaultValue() == null;
        refreshers.add(() -> textField.setText(formatValues(currentSpecificValue(parameter))));
        Runnable commit = () -> {
            if (refreshing) {
                return;
            }
            String text = textField.getText();
            applySpecificUpdate(parameter, nullable && text.isBlank() ? null : text);
        };
        bindCommit(textField, commit);
        return textField;
    }

    // string list with a closed set of possible values (enum-backed): a checkable dropdown
    private CheckComboBox<String> buildCheckListControl(Parameter parameter) {
        CheckComboBox<String> checkComboBox = new CheckComboBox<>(FXCollections.observableArrayList(
                parameter.getPossibleValues().stream().map(Object::toString).toList()));
        refreshers.add(() -> {
            Object current = currentSpecificValue(parameter);
            Set<String> selected = current instanceof Collection<?> collection
                    ? collection.stream().map(Object::toString).collect(Collectors.toSet())
                    : Set.of();
            checkComboBox.getCheckModel().clearChecks();
            checkComboBox.getItems().stream().filter(selected::contains).forEach(checkComboBox.getCheckModel()::check);
        });
        checkComboBox.getCheckModel().getCheckedItems().addListener((javafx.collections.ListChangeListener<String>) change -> {
            if (!refreshing) {
                applySpecificUpdate(parameter, String.join(",", checkComboBox.getCheckModel().getCheckedItems()));
            }
        });
        return checkComboBox;
    }

    public void onImport(ActionEvent actionEvent) {
        FileChooser fileChooser = new FileChooser();
        addJsonExtensionFilters(fileChooser);
        FileChooserPreferences.applyLastDirectory(fileChooser);
        File selectedFile = fileChooser.showOpenDialog(this.splitPane.getScene().getWindow());
        if (selectedFile != null) {
            FileChooserPreferences.saveLastDirectory(selectedFile);
            importFrom(selectedFile.toPath());
        }
    }

    public void onExport(ActionEvent actionEvent) {
        FileChooser fileChooser = new FileChooser();
        addJsonExtensionFilters(fileChooser);
        FileChooserPreferences.applyLastDirectory(fileChooser);
        File selectedFile = fileChooser.showSaveDialog(this.splitPane.getScene().getWindow());
        if (selectedFile != null) {
            FileChooserPreferences.saveLastDirectory(selectedFile);
            exportTo(selectedFile.toPath());
        }
    }

    private static void addJsonExtensionFilters(FileChooser fileChooser) {
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                Messages.get("networks.file.supportedFiles", "json"), "*.json"));
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(Messages.get("networks.file.allFiles"), "*.*"));
    }

    public void importFrom(Path path) {
        loadFlowParametersProperty.setValue(JsonLoadFlowParameters.read(path));
    }

    public void exportTo(Path path) {
        JsonLoadFlowParameters.write(loadFlowParametersProperty.getValue(), path);
    }
}
