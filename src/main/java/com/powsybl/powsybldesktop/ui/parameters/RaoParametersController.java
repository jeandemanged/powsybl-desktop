/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.ui.parameters;

import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.report.ReportNode;
import com.powsybl.openrao.commons.OpenRaoException;
import com.powsybl.openrao.raoapi.parameters.MnecParameters;
import com.powsybl.openrao.raoapi.parameters.ObjectiveFunctionParameters.ObjectiveFunctionType;
import com.powsybl.openrao.raoapi.parameters.RaoParameters;
import com.powsybl.openrao.raoapi.parameters.extensions.OpenRaoSearchTreeParameters;
import com.powsybl.openrao.raoapi.parameters.extensions.SearchTreeRaoCostlyMinMarginParameters;
import com.powsybl.openrao.raoapi.parameters.extensions.SearchTreeRaoMnecParameters;
import com.powsybl.openrao.raoapi.parameters.extensions.SearchTreeRaoRangeActionsOptimizationParameters;
import com.powsybl.openrao.raoapi.parameters.extensions.SearchTreeRaoRangeActionsOptimizationParameters.LinearOptimizationSolver;
import com.powsybl.openrao.raoapi.parameters.extensions.SearchTreeRaoTopoOptimizationParameters;
import com.powsybl.openrao.raoapi.parameters.extensions.SecondPreventiveRaoParameters;
import com.powsybl.powsybldesktop.parameters.ParametersModel;
import com.powsybl.powsybldesktop.rao.RaoParametersSupport;
import com.powsybl.powsybldesktop.sensitivity.SensitivityAnalysisMode;
import com.powsybl.powsybldesktop.ui.controls.FileChooserPreferences;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Edits OpenRAO's {@link RaoParameters} plus their search tree RAO and FastRAO extensions, and the app's own
 * settings for the RAO: whether it runs through FastRAO (which runs the search tree RAO, so every category applies to
 * it) and its AC/DC {@link SensitivityAnalysisMode}. As for the security and sensitivity analyses, the embedded load
 * flow parameters are not shown: {@link ParametersModel} keeps them wired to the app's single {@code LoadFlowParameters}
 * instance. Loop flows and relative margins, which need inputs the app doesn't provide, aren't offered, nor are the
 * predefined topological action combinations and the embedded sensitivity thresholds: JSON import only.
 * <p>
 * The MNEC and costly min margin sections are optional: their check boxes add or remove them, and editing one of their
 * values adds the section. Removing one replaces the parameters (see {@link RaoParametersSupport}).
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class RaoParametersController extends AbstractDiagramParametersController<RaoParameters> {

    private static final String CAT_GENERAL = "general";
    private static final String CAT_OBJECTIVE = "objective";
    private static final String CAT_RANGE_ACTIONS = "rangeActions";
    private static final String CAT_TOPOLOGICAL_ACTIONS = "topologicalActions";
    private static final String CAT_SECOND_PREVENTIVE = "secondPreventive";
    private static final String CAT_MNEC = "mnec";
    private static final String CAT_FAST_RAO = "fastRao";

    private final ChoiceBox<SensitivityAnalysisMode> modeChoiceBox = new ChoiceBox<>(FXCollections.observableArrayList(SensitivityAnalysisMode.values()));
    private ObjectProperty<SensitivityAnalysisMode> modeProperty;
    private final CheckBox fastRaoCheckBox = new CheckBox();
    private BooleanProperty fastRaoProperty;
    private BiConsumer<Path, RuntimeException> onImportFailed = (_, _) -> { };

    @FXML
    private void initialize() {
        Map<String, String> titles = new LinkedHashMap<>();
        for (String category : new String[] {CAT_GENERAL, CAT_OBJECTIVE, CAT_RANGE_ACTIONS, CAT_TOPOLOGICAL_ACTIONS, CAT_SECOND_PREVENTIVE, CAT_MNEC, CAT_FAST_RAO}) {
            titles.put(category, Messages.get("rao.category." + category));
        }
        buildCategoryList(titles);
    }

    public void setParametersModel(ParametersModel parametersModel) {
        modeProperty = parametersModel.raoModeProperty();
        fastRaoProperty = parametersModel.fastRaoProperty();
        setParametersProperty(parametersModel.raoParametersProperty());
        setOnChange(parametersModel::parametersChanged);
        listenerManager.listen(modeProperty, (_, _, _) -> refresh());
        listenerManager.listen(fastRaoProperty, (_, _, _) -> refresh());
        // the "same as load flow" choice tells whether that is AC or DC
        listenerManager.listen(parametersModel.parametersRevisionProperty(), (_, _, _) -> refreshModeLabels(parametersModel));
        refreshModeLabels(parametersModel);
    }

    public void setOnImportFailed(BiConsumer<Path, RuntimeException> onImportFailed) {
        this.onImportFailed = Objects.requireNonNull(onImportFailed);
    }

    private static OpenRaoSearchTreeParameters searchTree(RaoParameters parameters) {
        return RaoParametersSupport.searchTree(parameters);
    }

    private static SearchTreeRaoRangeActionsOptimizationParameters rangeActions(RaoParameters parameters) {
        return searchTree(parameters).getRangeActionsOptimizationParameters();
    }

    private static LinearOptimizationSolver solver(RaoParameters parameters) {
        return rangeActions(parameters).getLinearOptimizationSolver();
    }

    private static SearchTreeRaoTopoOptimizationParameters topo(RaoParameters parameters) {
        return searchTree(parameters).getTopoOptimizationParameters();
    }

    private static SecondPreventiveRaoParameters secondPreventive(RaoParameters parameters) {
        return searchTree(parameters).getSecondPreventiveRaoParameters();
    }

    // an absent optional section shows OpenRAO's defaults; setting one of its values adds it
    private static <S> S mnec(RaoParameters parameters, Function<RaoParameters, Optional<S>> section, S defaults, boolean adding) {
        if (adding) {
            RaoParametersSupport.addMnec(parameters);
        }
        return section.apply(parameters).orElse(defaults);
    }

    private static SearchTreeRaoCostlyMinMarginParameters costlyMinMargins(RaoParameters parameters, boolean adding) {
        if (adding) {
            RaoParametersSupport.addCostlyMinMargins(parameters);
        }
        return searchTree(parameters).getMinMarginsParameters().orElseGet(SearchTreeRaoCostlyMinMarginParameters::new);
    }

    @Override
    protected void buildFields() {
        refreshers.add(() -> modeChoiceBox.setValue(modeProperty.getValue()));
        modeChoiceBox.valueProperty().addListener((_, _, newValue) -> {
            if (!refreshing && newValue != null) {
                modeProperty.setValue(newValue);
            }
        });
        addRow(CAT_GENERAL, label("mode"), tooltip("mode"), modeChoiceBox);
        addIntField(CAT_GENERAL, label("availableCpus"), tooltip("availableCpus"),
                p -> searchTree(p).getMultithreadingParameters().getAvailableCPUs(),
                (p, v) -> searchTree(p).getMultithreadingParameters().setAvailableCPUs(requirePositive(v)));
        addDoubleField(CAT_GENERAL, label("sensitivityFailureOvercost"), tooltip("sensitivityFailureOvercost"),
                p -> searchTree(p).getLoadFlowAndSensitivityParameters().getSensitivityFailureOvercost(),
                (p, v) -> searchTree(p).getLoadFlowAndSensitivityParameters().setSensitivityFailureOvercost(v));

        addEnumField(CAT_OBJECTIVE, label("objectiveFunction"), tooltip("objectiveFunction"), ObjectiveFunctionType.class,
                p -> p.getObjectiveFunctionParameters().getType(), (p, v) -> p.getObjectiveFunctionParameters().setType(v));
        addBooleanField(CAT_OBJECTIVE, label("enforceCurativeSecurity"), tooltip("enforceCurativeSecurity"),
                p -> p.getObjectiveFunctionParameters().getEnforceCurativeSecurity(), (p, v) -> p.getObjectiveFunctionParameters().setEnforceCurativeSecurity(v));
        addDoubleField(CAT_OBJECTIVE, label("curativeMinObjImprovement"), tooltip("curativeMinObjImprovement"),
                p -> searchTree(p).getObjectiveFunctionParameters().getCurativeMinObjImprovement(),
                (p, v) -> searchTree(p).getObjectiveFunctionParameters().setCurativeMinObjImprovement(v, ReportNode.NO_OP));
        addBooleanField(CAT_OBJECTIVE, label("doNotOptimizeCurativeCnecsForTsosWithoutCras"), tooltip("doNotOptimizeCurativeCnecsForTsosWithoutCras"),
                p -> p.getNotOptimizedCnecsParameters().getDoNotOptimizeCurativeCnecsForTsosWithoutCras(),
                (p, v) -> p.getNotOptimizedCnecsParameters().setDoNotOptimizeCurativeCnecsForTsosWithoutCras(v));
        addBooleanField(CAT_OBJECTIVE, label("costlyMinMargins"), tooltip("costlyMinMargins"),
                RaoParametersSupport::hasCostlyMinMargins, (p, v) -> {
                    if (Boolean.TRUE.equals(v)) {
                        RaoParametersSupport.addCostlyMinMargins(p);
                    } else {
                        parametersProperty.setValue(RaoParametersSupport.withoutCostlyMinMargins(p));
                    }
                });
        addDoubleField(CAT_OBJECTIVE, label("shiftedViolationPenalty"), tooltip("shiftedViolationPenalty"),
                p -> costlyMinMargins(p, false).getShiftedViolationPenalty(), (p, v) -> addingSection(() -> costlyMinMargins(p, true).setShiftedViolationPenalty(v)));
        addDoubleField(CAT_OBJECTIVE, label("shiftedViolationThreshold"), tooltip("shiftedViolationThreshold"),
                p -> costlyMinMargins(p, false).getShiftedViolationThreshold(), (p, v) -> addingSection(() -> costlyMinMargins(p, true).setShiftedViolationThreshold(v)));

        addEnumField(CAT_RANGE_ACTIONS, label("pstModel"), tooltip("pstModel"), SearchTreeRaoRangeActionsOptimizationParameters.PstModel.class,
                p -> rangeActions(p).getPstModel(), (p, v) -> rangeActions(p).setPstModel(v));
        addDoubleField(CAT_RANGE_ACTIONS, label("pstRaMinImpactThreshold"), tooltip("pstRaMinImpactThreshold"),
                p -> p.getRangeActionsOptimizationParameters().getPstRAMinImpactThreshold(), (p, v) -> p.getRangeActionsOptimizationParameters().setPstRAMinImpactThreshold(v));
        addDoubleField(CAT_RANGE_ACTIONS, label("hvdcRaMinImpactThreshold"), tooltip("hvdcRaMinImpactThreshold"),
                p -> p.getRangeActionsOptimizationParameters().getHvdcRAMinImpactThreshold(), (p, v) -> p.getRangeActionsOptimizationParameters().setHvdcRAMinImpactThreshold(v));
        addDoubleField(CAT_RANGE_ACTIONS, label("injectionRaMinImpactThreshold"), tooltip("injectionRaMinImpactThreshold"),
                p -> p.getRangeActionsOptimizationParameters().getInjectionRAMinImpactThreshold(), (p, v) -> p.getRangeActionsOptimizationParameters().setInjectionRAMinImpactThreshold(v));
        addDoubleField(CAT_RANGE_ACTIONS, label("pstSensitivityThreshold"), tooltip("pstSensitivityThreshold"),
                p -> rangeActions(p).getPstSensitivityThreshold(), (p, v) -> rangeActions(p).setPstSensitivityThreshold(v));
        addDoubleField(CAT_RANGE_ACTIONS, label("hvdcSensitivityThreshold"), tooltip("hvdcSensitivityThreshold"),
                p -> rangeActions(p).getHvdcSensitivityThreshold(), (p, v) -> rangeActions(p).setHvdcSensitivityThreshold(v));
        addDoubleField(CAT_RANGE_ACTIONS, label("injectionRaSensitivityThreshold"), tooltip("injectionRaSensitivityThreshold"),
                p -> rangeActions(p).getInjectionRaSensitivityThreshold(), (p, v) -> rangeActions(p).setInjectionRaSensitivityThreshold(v));
        addEnumField(CAT_RANGE_ACTIONS, label("raRangeShrinking"), tooltip("raRangeShrinking"), SearchTreeRaoRangeActionsOptimizationParameters.RaRangeShrinking.class,
                p -> rangeActions(p).getRaRangeShrinking(), (p, v) -> rangeActions(p).setRaRangeShrinking(v));
        addIntField(CAT_RANGE_ACTIONS, label("maxMipIterations"), tooltip("maxMipIterations"),
                p -> rangeActions(p).getMaxMipIterations(), (p, v) -> rangeActions(p).setMaxMipIterations(requirePositive(v)));
        addEnumField(CAT_RANGE_ACTIONS, label("solver"), tooltip("solver"), SearchTreeRaoRangeActionsOptimizationParameters.Solver.class,
                p -> solver(p).getSolver(), (p, v) -> solver(p).setSolver(v));
        addDoubleField(CAT_RANGE_ACTIONS, label("relativeMipGap"), tooltip("relativeMipGap"),
                p -> solver(p).getRelativeMipGap(), (p, v) -> solver(p).setRelativeMipGap(v));
        addStringField(CAT_RANGE_ACTIONS, label("solverSpecificParameters"), tooltip("solverSpecificParameters"),
                p -> solver(p).getSolverSpecificParameters(), (p, v) -> solver(p).setSolverSpecificParameters(v.isEmpty() ? null : v));

        addIntField(CAT_TOPOLOGICAL_ACTIONS, label("maxPreventiveSearchTreeDepth"), tooltip("maxPreventiveSearchTreeDepth"),
                p -> topo(p).getMaxPreventiveSearchTreeDepth(), (p, v) -> topo(p).setMaxPreventiveSearchTreeDepth(requireNonNegative(v)));
        addIntField(CAT_TOPOLOGICAL_ACTIONS, label("maxCurativeSearchTreeDepth"), tooltip("maxCurativeSearchTreeDepth"),
                p -> topo(p).getMaxCurativeSearchTreeDepth(), (p, v) -> topo(p).setMaxCurativeSearchTreeDepth(requireNonNegative(v)));
        addDoubleField(CAT_TOPOLOGICAL_ACTIONS, label("relativeMinImpactThreshold"), tooltip("relativeMinImpactThreshold"),
                p -> p.getTopoOptimizationParameters().getRelativeMinImpactThreshold(), (p, v) -> p.getTopoOptimizationParameters().setRelativeMinImpactThreshold(v));
        addDoubleField(CAT_TOPOLOGICAL_ACTIONS, label("absoluteMinImpactThreshold"), tooltip("absoluteMinImpactThreshold"),
                p -> p.getTopoOptimizationParameters().getAbsoluteMinImpactThreshold(), (p, v) -> p.getTopoOptimizationParameters().setAbsoluteMinImpactThreshold(v));
        addBooleanField(CAT_TOPOLOGICAL_ACTIONS, label("skipActionsFarFromMostLimitingElement"), tooltip("skipActionsFarFromMostLimitingElement"),
                p -> topo(p).getSkipActionsFarFromMostLimitingElement(), (p, v) -> topo(p).setSkipActionsFarFromMostLimitingElement(v));
        addIntField(CAT_TOPOLOGICAL_ACTIONS, label("maxNumberOfBoundariesForSkippingActions"), tooltip("maxNumberOfBoundariesForSkippingActions"),
                p -> topo(p).getMaxNumberOfBoundariesForSkippingActions(), (p, v) -> topo(p).setMaxNumberOfBoundariesForSkippingActions(requireNonNegative(v)));
        addBooleanField(CAT_TOPOLOGICAL_ACTIONS, label("allowElectricalIslandCreation"), tooltip("allowElectricalIslandCreation"),
                p -> topo(p).getAllowElectricalIslandCreation(), (p, v) -> topo(p).setAllowElectricalIslandCreation(v));

        addEnumField(CAT_SECOND_PREVENTIVE, label("executionCondition"), tooltip("executionCondition"), SecondPreventiveRaoParameters.ExecutionCondition.class,
                p -> secondPreventive(p).getExecutionCondition(), (p, v) -> secondPreventive(p).setExecutionCondition(v));
        addBooleanField(CAT_SECOND_PREVENTIVE, label("hintFromFirstPreventiveRao"), tooltip("hintFromFirstPreventiveRao"),
                p -> secondPreventive(p).getHintFromFirstPreventiveRao(), (p, v) -> secondPreventive(p).setHintFromFirstPreventiveRao(v));

        addBooleanField(CAT_MNEC, label("mnec"), tooltip("mnec"), RaoParametersSupport::hasMnec, (p, v) -> {
            if (Boolean.TRUE.equals(v)) {
                RaoParametersSupport.addMnec(p);
            } else {
                parametersProperty.setValue(RaoParametersSupport.withoutMnec(p));
            }
        });
        addDoubleField(CAT_MNEC, label("acceptableMarginDecrease"), tooltip("acceptableMarginDecrease"),
                p -> mnec(p, RaoParameters::getMnecParameters, new MnecParameters(), false).getAcceptableMarginDecrease(),
                (p, v) -> addingSection(() -> mnec(p, RaoParameters::getMnecParameters, new MnecParameters(), true).setAcceptableMarginDecrease(v)));
        addDoubleField(CAT_MNEC, label("mnecViolationCost"), tooltip("mnecViolationCost"),
                p -> mnec(p, q -> searchTree(q).getMnecParameters(), new SearchTreeRaoMnecParameters(), false).getViolationCost(),
                (p, v) -> addingSection(() -> mnec(p, q -> searchTree(q).getMnecParameters(), new SearchTreeRaoMnecParameters(), true).setViolationCost(v)));
        addDoubleField(CAT_MNEC, label("mnecConstraintAdjustmentCoefficient"), tooltip("mnecConstraintAdjustmentCoefficient"),
                p -> mnec(p, q -> searchTree(q).getMnecParameters(), new SearchTreeRaoMnecParameters(), false).getConstraintAdjustmentCoefficient(),
                (p, v) -> addingSection(() -> mnec(p, q -> searchTree(q).getMnecParameters(), new SearchTreeRaoMnecParameters(), true)
                        .setConstraintAdjustmentCoefficient(v)));

        refreshers.add(() -> fastRaoCheckBox.setSelected(fastRaoProperty.get()));
        fastRaoCheckBox.selectedProperty().addListener((_, _, newValue) -> {
            if (!refreshing) {
                fastRaoProperty.set(newValue);
            }
        });
        addRow(CAT_FAST_RAO, label("useFastRao"), tooltip("useFastRao"), fastRaoCheckBox);
        addIntField(CAT_FAST_RAO, label("numberOfCnecsToAdd"), tooltip("numberOfCnecsToAdd"),
                p -> RaoParametersSupport.fastRao(p).getNumberOfCnecsToAdd(), (p, v) -> RaoParametersSupport.fastRao(p).setNumberOfCnecsToAdd(requirePositive(v)));
        addBooleanField(CAT_FAST_RAO, label("addUnsecureCnecs"), tooltip("addUnsecureCnecs"),
                p -> RaoParametersSupport.fastRao(p).getAddUnsecureCnecs(), (p, v) -> RaoParametersSupport.fastRao(p).setAddUnsecureCnecs(v));
        addDoubleField(CAT_FAST_RAO, label("marginLimit"), tooltip("marginLimit"),
                p -> RaoParametersSupport.fastRao(p).getMarginLimit(), (p, v) -> RaoParametersSupport.fastRao(p).setMarginLimit(v));
    }

    // the section's check box shows that it was added
    private void addingSection(Runnable edit) {
        edit.run();
        refresh();
    }

    private static int requirePositive(int value) {
        if (value < 1) {
            throw new IllegalArgumentException("Not positive: " + value);
        }
        return value;
    }

    private static int requireNonNegative(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("Negative: " + value);
        }
        return value;
    }

    private void refreshModeLabels(ParametersModel parametersModel) {
        boolean loadFlowDc = parametersModel.loadFlowParametersProperty().getValue().isDc();
        modeChoiceBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(SensitivityAnalysisMode mode) {
                if (mode == null) {
                    return "";
                }
                return mode == SensitivityAnalysisMode.LOAD_FLOW
                        ? Messages.get("sensitivityAnalysis.mode.loadFlow", Messages.get(loadFlowDc ? "sensitivityAnalysis.mode.dc" : "sensitivityAnalysis.mode.ac"))
                        : Messages.get("sensitivityAnalysis.mode." + mode.name().toLowerCase(Locale.ROOT));
            }

            @Override
            public SensitivityAnalysisMode fromString(String string) {
                throw new UnsupportedOperationException();
            }
        });
    }

    private static String label(String param) {
        return Messages.get("rao.param." + param + ".label");
    }

    private static String tooltip(String param) {
        return Messages.get("rao.param." + param + ".tooltip");
    }

    @FXML
    private void onImport() {
        File file = createFileChooser().showOpenDialog(splitPane.getScene().getWindow());
        if (file != null) {
            FileChooserPreferences.saveLastDirectory(file);
            importFrom(file.toPath());
        }
    }

    @FXML
    private void onExport() {
        File file = createFileChooser().showSaveDialog(splitPane.getScene().getWindow());
        if (file != null) {
            FileChooserPreferences.saveLastDirectory(file);
            exportTo(file.toPath());
        }
    }

    private static FileChooser createFileChooser() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(Messages.get("networks.file.supportedFiles", "json"), "*.json"));
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(Messages.get("networks.file.allFiles"), "*.*"));
        FileChooserPreferences.applyLastDirectory(fileChooser);
        return fileChooser;
    }

    // OpenRAO's format: the mode and FastRAO's use, the app's own, are neither imported nor exported. The imported embedded load flow
    // parameters are replaced by the app's own, as for the security and sensitivity analyses
    void importFrom(Path path) {
        RaoParameters parameters;
        try (InputStream inputStream = Files.newInputStream(path)) {
            parameters = RaoParametersSupport.read(inputStream);
        } catch (IOException e) {
            onImportFailed.accept(path, new UncheckedIOException(e));
            return;
        } catch (PowsyblException | OpenRaoException | UncheckedIOException e) {
            onImportFailed.accept(path, e);
            return;
        }
        parametersProperty.setValue(parameters);
    }

    void exportTo(Path path) {
        try (OutputStream outputStream = Files.newOutputStream(path)) {
            RaoParametersSupport.write(parametersProperty.getValue(), outputStream);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
