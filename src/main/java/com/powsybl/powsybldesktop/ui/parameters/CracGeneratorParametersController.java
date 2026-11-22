/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.ui.parameters;

import com.powsybl.iidm.network.Country;
import com.powsybl.openrao.data.crac.io.network.parameters.CriticalElements;
import com.powsybl.powsybldesktop.parameters.ParametersModel;
import com.powsybl.powsybldesktop.rao.CracGenerator;
import com.powsybl.powsybldesktop.rao.CracGeneratorSettings;
import com.powsybl.powsybldesktop.ui.controls.RowsTableEditor;
import com.powsybl.powsybldesktop.ui.controls.SearchableCheckComboBox;
import com.powsybl.powsybldesktop.utils.Labels;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.Region;
import javafx.util.StringConverter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Edits the application's {@link CracGeneratorSettings}: how a CRAC is generated from the network (OpenRAO's CRAC
 * creation from the network, by the CRAC view's Generate button, see {@link CracGenerator}). Instants, contingencies,
 * CNECs and their thresholds, and the range actions (PSTs, redispatching, counter-trading, balancing). Tables are
 * {@link RowsTableEditor}s parsed by {@link CracGenerator}'s {@code parseXxx} methods: an invalid row leaves the
 * settings unchanged. The problems the generator would refuse with, which need the instants as a whole, are told at
 * the top. No JSON import/export: OpenRAO has no format for them; they are saved with all the parameters.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class CracGeneratorParametersController extends AbstractDiagramParametersController<CracGeneratorSettings> {

    private static final String CAT_INSTANTS = "instants";
    private static final String CAT_CNECS = "cnecs";
    private static final String CAT_RANGE_ACTIONS = "rangeActions";

    private final Map<String, RowsTableEditor> tables = new LinkedHashMap<>();
    final Label problemsLabel = new Label();

    @FXML
    private void initialize() {
        Map<String, String> titles = new LinkedHashMap<>();
        for (String category : new String[] {CAT_INSTANTS, CAT_CNECS, CAT_RANGE_ACTIONS}) {
            titles.put(category, Messages.get("cracGenerator.category." + category));
        }
        buildCategoryList(titles);
    }

    public void setParametersModel(ParametersModel parametersModel) {
        setParametersProperty(parametersModel.cracGeneratorSettingsProperty());
        setOnChange(() -> {
            refreshProblems();
            parametersModel.parametersChanged();
        });
    }

    RowsTableEditor table(String param) {
        return tables.get(param);
    }

    @Override
    protected void refresh() {
        super.refresh();
        refreshProblems();
    }

    private void refreshProblems() {
        List<String> problems = parametersProperty == null ? List.of() : CracGenerator.problems(parametersProperty.getValue());
        problemsLabel.setText(String.join("\n", problems));
        problemsLabel.setVisible(!problems.isEmpty());
    }

    @Override
    protected void buildFields() {
        problemsLabel.getStyleClass().add("alert-label");
        problemsLabel.setWrapText(true);
        problemsLabel.setMinHeight(Region.USE_PREF_SIZE);
        problemsLabel.managedProperty().bind(problemsLabel.visibleProperty());
        categoryGrids.get(CAT_INSTANTS).add(problemsLabel, 0, 0, 2, 1);

        addTable(CAT_INSTANTS, "autoInstants", List.of("instant"), s -> rows(s.getAutoInstants()),
                (s, rows) -> s.setAutoInstants(CracGenerator.parseValues(rows)));
        addTable(CAT_INSTANTS, "curativeInstants", List.of("instant"), s -> rows(s.getCurativeInstants()),
                (s, rows) -> s.setCurativeInstants(CracGenerator.parseValues(rows)));
        addTable(CAT_INSTANTS, "rangeActionGroups", List.of("rangeActions"), s -> rows(s.getRangeActionGroups()),
                (s, rows) -> s.setRangeActionGroups(CracGenerator.parseRangeActionGroups(rows)));
        addCountries(CAT_INSTANTS, "contingencyCountries", CracGeneratorSettings::getContingencyCountries, CracGeneratorSettings::setContingencyCountries);
        addOptionalDouble(CAT_INSTANTS, "contingencyMinV", CracGeneratorSettings::getContingencyMinV, CracGeneratorSettings::setContingencyMinV);
        addOptionalDouble(CAT_INSTANTS, "contingencyMaxV", CracGeneratorSettings::getContingencyMaxV, CracGeneratorSettings::setContingencyMaxV);
        addTable(CAT_INSTANTS, "excludedContingencyBranches", List.of("branch"), s -> rows(s.getExcludedContingencyBranches()),
                (s, rows) -> s.setExcludedContingencyBranches(CracGenerator.parseValues(rows)));

        addCountries(CAT_CNECS, "cnecCountries", CracGeneratorSettings::getCnecCountries, CracGeneratorSettings::setCnecCountries);
        addOptionalDouble(CAT_CNECS, "optimizedMinV", CracGeneratorSettings::getOptimizedMinV, CracGeneratorSettings::setOptimizedMinV);
        addOptionalDouble(CAT_CNECS, "optimizedMaxV", CracGeneratorSettings::getOptimizedMaxV, CracGeneratorSettings::setOptimizedMaxV);
        addBooleanField(CAT_CNECS, label("monitoredElements"), tooltip("monitoredElements"), CracGeneratorSettings::isMonitoredElements,
                CracGeneratorSettings::setMonitoredElements);
        addOptionalDouble(CAT_CNECS, "monitoredMinV", CracGeneratorSettings::getMonitoredMinV, CracGeneratorSettings::setMonitoredMinV);
        addOptionalDouble(CAT_CNECS, "monitoredMaxV", CracGeneratorSettings::getMonitoredMaxV, CracGeneratorSettings::setMonitoredMaxV);
        ChoiceBox<CriticalElements.ThresholdDefinition> thresholds =
                new ChoiceBox<>(FXCollections.observableArrayList(CriticalElements.ThresholdDefinition.values()));
        thresholds.setConverter(converter(definition -> Messages.get("cracGenerator.thresholdDefinition." + definition.name())));
        refreshers.add(() -> thresholds.setValue(parametersProperty.getValue().getThresholdDefinition()));
        thresholds.valueProperty().addListener((_, _, definition) -> {
            if (!refreshing && definition != null) {
                parametersProperty.getValue().setThresholdDefinition(definition);
                notifyChange();
            }
        });
        addRow(CAT_CNECS, label("thresholdDefinition"), tooltip("thresholdDefinition"), thresholds);
        addTable(CAT_CNECS, "limitMultipliers", List.of("instant", "multiplier"), CracGeneratorSettings::getLimitMultipliers, (s, rows) -> {
            CracGenerator.parseInstantValues(rows, false, 1);
            s.setLimitMultipliers(rows);
        });
        addTable(CAT_CNECS, "limitMultipliersPerNominalV", List.of("instant", "nominalV", "multiplier"), CracGeneratorSettings::getLimitMultipliersPerNominalV,
                (s, rows) -> {
                    CracGenerator.parseInstantNominalVValues(rows);
                    s.setLimitMultipliersPerNominalV(rows);
                });
        addTable(CAT_CNECS, "limitDurations", List.of("instant", "duration"), CracGeneratorSettings::getLimitDurations, (s, rows) -> {
            CracGenerator.parseInstantValues(rows, true, Double.MAX_VALUE);
            s.setLimitDurations(rows);
        });
        addTable(CAT_CNECS, "limitDurationsPerNominalV", List.of("instant", "nominalV", "duration"), CracGeneratorSettings::getLimitDurationsPerNominalV,
                (s, rows) -> {
                    CracGenerator.parseInstantNominalVValues(rows);
                    s.setLimitDurationsPerNominalV(rows);
                });

        addBooleanField(CAT_RANGE_ACTIONS, label("pstRangeActions"), tooltip("pstRangeActions"), CracGeneratorSettings::isPstRangeActions,
                CracGeneratorSettings::setPstRangeActions);
        addCountries(CAT_RANGE_ACTIONS, "pstCountries", CracGeneratorSettings::getPstCountries, CracGeneratorSettings::setPstCountries);
        addTable(CAT_RANGE_ACTIONS, "excludedPsts", List.of("pst"), s -> rows(s.getExcludedPsts()),
                (s, rows) -> s.setExcludedPsts(CracGenerator.parseValues(rows)));
        addTable(CAT_RANGE_ACTIONS, "pstTapRanges", List.of("instant", "minTap", "maxTap", "rangeType"), CracGeneratorSettings::getPstTapRanges, (s, rows) -> {
            CracGenerator.parseTapRanges(rows);
            s.setPstTapRanges(rows);
        });
        addBooleanField(CAT_RANGE_ACTIONS, label("redispatching"), tooltip("redispatching"), CracGeneratorSettings::isRedispatching,
                CracGeneratorSettings::setRedispatching);
        addCountries(CAT_RANGE_ACTIONS, "redispatchingCountries", CracGeneratorSettings::getRedispatchingCountries,
                CracGeneratorSettings::setRedispatchingCountries);
        addTable(CAT_RANGE_ACTIONS, "excludedRedispatchingGenerators", List.of("generator"), s -> rows(s.getExcludedRedispatchingGenerators()),
                (s, rows) -> s.setExcludedRedispatchingGenerators(CracGenerator.parseValues(rows)));
        List<String> rangeAndCosts = List.of("instant", "min", "max", "activationCost", "upCost", "downCost");
        addTable(CAT_RANGE_ACTIONS, "redispatchingRangesAndCosts", rangeAndCosts, CracGeneratorSettings::getRedispatchingRangesAndCosts, (s, rows) -> {
            CracGenerator.parseInstantRangesAndCosts(rows);
            s.setRedispatchingRangesAndCosts(rows);
        });
        addTable(CAT_RANGE_ACTIONS, "injectionCombinations", List.of("combination", "injections"), CracGeneratorSettings::getInjectionCombinations,
                (s, rows) -> {
                    CracGenerator.parseInjectionCombinations(rows);
                    s.setInjectionCombinations(rows);
                });
        addTable(CAT_RANGE_ACTIONS, "countertrading", List.of("country", "instant", "min", "max", "activationCost", "upCost", "downCost"),
                CracGeneratorSettings::getCountertrading, (s, rows) -> {
                    CracGenerator.parseCountryRangesAndCosts(rows);
                    s.setCountertrading(rows);
                });
        addTable(CAT_RANGE_ACTIONS, "balancing", rangeAndCosts, CracGeneratorSettings::getBalancing, (s, rows) -> {
            CracGenerator.parseInstantRangesAndCosts(rows);
            s.setBalancing(rows);
        });
    }

    private static List<List<String>> rows(List<String> values) {
        return values.stream().map(List::of).toList();
    }

    // the rows are read on each refresh; a change parses them, the settings unchanged when one is invalid
    private void addTable(String category, String param, List<String> columns, Function<CracGeneratorSettings, List<List<String>>> rows,
                          BiConsumer<CracGeneratorSettings, List<List<String>>> write) {
        RowsTableEditor editor = new RowsTableEditor(columns.stream().map(column -> Messages.get("cracGenerator.column." + column)).toList());
        refreshers.add(() -> editor.setRows(rows.apply(parametersProperty.getValue())));
        editor.setOnEdit(newRows -> {
            write.accept(parametersProperty.getValue(), newRows);
            notifyChange();
        });
        tables.put(param, editor);
        addRow(category, label(param), tooltip(param), editor);
    }

    // none checked: all of them
    private void addCountries(String category, String param, Function<CracGeneratorSettings, List<Country>> getter,
                              BiConsumer<CracGeneratorSettings, List<Country>> setter) {
        SearchableCheckComboBox<Country> countries = new SearchableCheckComboBox<>(List.of(Country.values()), Labels.countryFormat());
        countries.setTitle(Messages.get("cracGenerator.countries.all"));
        refreshers.add(() -> countries.setChecked(getter.apply(parametersProperty.getValue())));
        countries.getCheckedItems().addListener((ListChangeListener<Country>) _ -> {
            if (!refreshing) {
                setter.accept(parametersProperty.getValue(), List.copyOf(countries.getCheckedItems()));
                notifyChange();
            }
        });
        addRow(category, label(param), tooltip(param), countries);
    }

    // blank: none; an invalid number is reverted
    private void addOptionalDouble(String category, String param, Function<CracGeneratorSettings, Double> getter, BiConsumer<CracGeneratorSettings, Double> setter) {
        TextField field = new TextField();
        refreshers.add(() -> {
            Double value = getter.apply(parametersProperty.getValue());
            field.setText(value == null ? "" : Double.toString(value));
        });
        Consumer<String> commit = text -> {
            if (refreshing) {
                return;
            }
            try {
                Double value = text.isBlank() ? null : Double.valueOf(text.trim());
                if (value != null && !Double.isFinite(value)) {
                    throw new NumberFormatException(text);
                }
                setter.accept(parametersProperty.getValue(), value);
                notifyChange();
            } catch (NumberFormatException _) {
                refresh();
            }
        };
        bindCommit(field, () -> commit.accept(field.getText()));
        addRow(category, label(param), tooltip(param), field);
    }

    private static <T> StringConverter<T> converter(Function<T, String> toString) {
        return new StringConverter<>() {
            @Override
            public String toString(T value) {
                return value == null ? "" : toString.apply(value);
            }

            @Override
            public T fromString(String string) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static String label(String param) {
        return Messages.get("cracGenerator.param." + param + ".label");
    }

    private static String tooltip(String param) {
        return Messages.get("cracGenerator.param." + param + ".tooltip");
    }
}
