/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.ui.limitscaling;

import com.powsybl.commons.PowsyblException;
import com.powsybl.contingency.ContingencyContext;
import com.powsybl.contingency.ContingencyContextType;
import com.powsybl.iidm.criteria.AbstractNetworkElementEquipmentCriterion;
import com.powsybl.iidm.criteria.AtLeastOneCountryCriterion;
import com.powsybl.iidm.criteria.AtLeastOneNominalVoltageCriterion;
import com.powsybl.iidm.criteria.BoundaryLineCriterion;
import com.powsybl.iidm.criteria.Criterion;
import com.powsybl.iidm.criteria.IdentifiableCriterion;
import com.powsybl.iidm.criteria.LineCriterion;
import com.powsybl.iidm.criteria.NetworkElementCriterion;
import com.powsybl.iidm.criteria.NetworkElementIdListCriterion;
import com.powsybl.iidm.criteria.SingleCountryCriterion;
import com.powsybl.iidm.criteria.SingleNominalVoltageCriterion;
import com.powsybl.iidm.criteria.ThreeNominalVoltageCriterion;
import com.powsybl.iidm.criteria.ThreeWindingsTransformerCriterion;
import com.powsybl.iidm.criteria.TieLineCriterion;
import com.powsybl.iidm.criteria.TwoCountriesCriterion;
import com.powsybl.iidm.criteria.TwoNominalVoltageCriterion;
import com.powsybl.iidm.criteria.TwoWindingsTransformerCriterion;
import com.powsybl.iidm.criteria.VoltageInterval;
import com.powsybl.iidm.criteria.duration.AllTemporaryDurationCriterion;
import com.powsybl.iidm.criteria.duration.EqualityTemporaryDurationCriterion;
import com.powsybl.iidm.criteria.duration.IntervalTemporaryDurationCriterion;
import com.powsybl.iidm.criteria.duration.LimitDurationCriterion;
import com.powsybl.iidm.criteria.duration.PermanentDurationCriterion;
import com.powsybl.iidm.network.Branch;
import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.LimitType;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.ThreeWindingsTransformer;
import com.powsybl.powsybldesktop.limitscaling.CriterionKind;
import com.powsybl.powsybldesktop.limitscaling.LimitScalingTexts;
import com.powsybl.powsybldesktop.limitscaling.LimitScalings;
import com.powsybl.powsybldesktop.limitscaling.OlfLimitScalingSupport;
import com.powsybl.powsybldesktop.ui.controls.CountryGroup;
import com.powsybl.powsybldesktop.ui.controls.SearchableCheckComboBox;
import com.powsybl.powsybldesktop.ui.controls.VoltageIntervalGroup;
import com.powsybl.powsybldesktop.utils.Messages;
import com.powsybl.security.limitscaling.LimitScaling;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Inline editor of one {@link LimitScaling}: every committed change rebuilds the
 * immutable scaling and pushes it through {@code onReplace}, like the contingency list forms. An invalid value (e.g. a
 * negative factor) keeps the last valid scaling.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class LimitScalingFormController {

    enum TemporaryMode {
        NONE("limitScalings.durations.temporary.none"),
        ALL("limitScalings.durations.allTemporary"),
        EQUAL("limitScalings.durations.temporary.equal"),
        INTERVAL("limitScalings.durations.temporary.interval");

        private final String labelKey;

        TemporaryMode(String labelKey) {
            this.labelKey = labelKey;
        }
    }

    @FXML
    Label olfLabel;
    @FXML
    ChoiceBox<LimitType> limitTypeChoiceBox;
    @FXML
    TextField valueField;
    @FXML
    CheckBox monitoringOnlyCheckBox;
    @FXML
    ChoiceBox<ContingencyContextType> contextChoiceBox;
    @FXML
    ComboBox<String> contingencyComboBox;
    @FXML
    MenuButton addCriterionMenuButton;
    @FXML
    private Button removeCriterionButton;
    @FXML
    ListView<NetworkElementCriterion> criteriaListView;
    @FXML
    private VBox criterionEditor;
    @FXML
    CheckBox allDurationsCheckBox;
    @FXML
    private VBox durationsBox;
    @FXML
    CheckBox permanentCheckBox;
    @FXML
    ChoiceBox<TemporaryMode> temporaryChoiceBox;
    @FXML
    TextField durationField;
    @FXML
    private HBox durationIntervalBox;
    @FXML
    TextField durationLowField;
    @FXML
    CheckBox durationLowClosedCheckBox;
    @FXML
    TextField durationHighField;
    @FXML
    CheckBox durationHighClosedCheckBox;
    @FXML
    private HBox limitsGroupsBox;

    private final ObservableList<NetworkElementCriterion> criteria = FXCollections.observableArrayList();
    private SearchableCheckComboBox<String> limitsGroupsComboBox;
    private Consumer<LimitScaling> onReplace;
    // populating the controls fires their listeners: nothing is committed until the form is loaded
    private boolean loading = true;
    // see ContingenciesController.applyingReplace: a criterion edit must not rebuild its own editor
    private boolean replacingCriterion;
    // the selected criterion's editor, rebuilding it from its controls
    private Function<NetworkElementCriterion, NetworkElementCriterion> criterionRebuilder;

    @FXML
    private void initialize() {
        limitTypeChoiceBox.setItems(FXCollections.observableArrayList(LimitScalings.LIMIT_TYPES));
        limitTypeChoiceBox.setConverter(converter(LimitScalingTexts::limitType));
        contextChoiceBox.setItems(FXCollections.observableArrayList(ContingencyContextType.ALL, ContingencyContextType.NONE,
                ContingencyContextType.ONLY_CONTINGENCIES, ContingencyContextType.SPECIFIC));
        contextChoiceBox.setConverter(converter(type -> Messages.get(switch (type) {
            case ALL -> "limitScalings.context.all";
            case NONE -> "limitScalings.context.none";
            case ONLY_CONTINGENCIES -> "limitScalings.context.onlyContingencies";
            case SPECIFIC -> "limitScalings.context.specific";
        })));
        contingencyComboBox.visibleProperty().bind(contextChoiceBox.valueProperty().isEqualTo(ContingencyContextType.SPECIFIC));
        contingencyComboBox.managedProperty().bind(contingencyComboBox.visibleProperty());
        temporaryChoiceBox.setItems(FXCollections.observableArrayList(TemporaryMode.values()));
        temporaryChoiceBox.setConverter(converter(mode -> Messages.get(mode.labelKey)));
        durationsBox.disableProperty().bind(allDurationsCheckBox.selectedProperty());
        durationField.visibleProperty().bind(temporaryChoiceBox.valueProperty().isEqualTo(TemporaryMode.EQUAL));
        durationField.managedProperty().bind(durationField.visibleProperty());
        durationIntervalBox.visibleProperty().bind(temporaryChoiceBox.valueProperty().isEqualTo(TemporaryMode.INTERVAL));
        durationIntervalBox.managedProperty().bind(durationIntervalBox.visibleProperty());

        for (CriterionKind kind : CriterionKind.values()) {
            MenuItem item = new MenuItem(kind.label());
            item.setOnAction(_ -> {
                criteria.add(kind.createDefault());
                criteriaListView.getSelectionModel().selectLast();
                commit();
            });
            addCriterionMenuButton.getItems().add(item);
        }
        criteriaListView.setItems(criteria);
        criteriaListView.setCellFactory(_ -> new ListCell<>() {
            @Override
            protected void updateItem(NetworkElementCriterion item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : LimitScalingTexts.criterion(item));
            }
        });
        criteriaListView.getSelectionModel().selectedItemProperty().addListener((_, _, newValue) -> {
            if (!replacingCriterion) {
                showCriterionEditor(newValue);
            }
        });
        removeCriterionButton.disableProperty().bind(criteriaListView.getSelectionModel().selectedItemProperty().isNull());

        limitTypeChoiceBox.valueProperty().addListener((_, _, _) -> commit());
        VoltageIntervalGroup.commitOnChange(valueField, this::commit);
        monitoringOnlyCheckBox.selectedProperty().addListener((_, _, _) -> commit());
        contextChoiceBox.valueProperty().addListener((_, _, _) -> commit());
        contingencyComboBox.valueProperty().addListener((_, _, _) -> commit());
        allDurationsCheckBox.selectedProperty().addListener((_, _, _) -> commit());
        permanentCheckBox.selectedProperty().addListener((_, _, _) -> commit());
        temporaryChoiceBox.valueProperty().addListener((_, _, _) -> commit());
        for (TextField field : List.of(durationField, durationLowField, durationHighField)) {
            VoltageIntervalGroup.commitOnChange(field, this::commit);
        }
        durationLowClosedCheckBox.selectedProperty().addListener((_, _, _) -> commit());
        durationHighClosedCheckBox.selectedProperty().addListener((_, _, _) -> commit());
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

    /**
     * @param contingencyIds the contingencies offered for a specific contingency context
     */
    public void setLimitScaling(LimitScaling scaling, Network network, List<String> contingencyIds,
                                Consumer<LimitScaling> onReplace) {
        limitTypeChoiceBox.setValue(scaling.getLimitType());
        valueField.setText(formatPercent(scaling.getValue()));
        monitoringOnlyCheckBox.setSelected(scaling.isMonitoringOnly());
        ContingencyContext context = scaling.getContingencyContext();
        contextChoiceBox.setValue(context.getContextType());
        contingencyComboBox.setItems(FXCollections.observableArrayList(contingencyIds));
        contingencyComboBox.setValue(context.getContextType() == ContingencyContextType.SPECIFIC ? context.getContingencyId() : null);
        criteria.setAll(scaling.getNetworkElementCriteria());
        setDurations(scaling.getDurationCriteria());
        Set<String> groupIds = new TreeSet<>(groupIds(network));
        groupIds.addAll(scaling.getOperationalLimitsGroupIdsSelection());
        limitsGroupsComboBox = new SearchableCheckComboBox<>(List.copyOf(groupIds), converter(id -> id));
        limitsGroupsComboBox.setChecked(scaling.getOperationalLimitsGroupIdsSelection());
        limitsGroupsComboBox.setTitle(Messages.get("limitScalings.limitsGroups.all"));
        limitsGroupsComboBox.getCheckedItems().addListener((ListChangeListener<String>) _ -> commit());
        limitsGroupsBox.getChildren().setAll(limitsGroupsComboBox);
        showOlfSupport(scaling);
        this.onReplace = onReplace;
        loading = false;
    }

    private static Set<String> groupIds(Network network) {
        Set<String> ids = new TreeSet<>();
        for (Branch<?> branch : network.getBranches()) {
            branch.getOperationalLimitsGroups1().forEach(group -> ids.add(group.getId()));
            branch.getOperationalLimitsGroups2().forEach(group -> ids.add(group.getId()));
        }
        network.getThreeWindingsTransformerStream().forEach(transformer -> {
            for (ThreeWindingsTransformer.Leg leg : List.of(transformer.getLeg1(), transformer.getLeg2(), transformer.getLeg3())) {
                leg.getOperationalLimitsGroups().forEach(group -> ids.add(group.getId()));
            }
        });
        return ids;
    }

    private static String formatPercent(double factor) {
        return String.valueOf(Math.round(factor * 100 * 1e6) / 1e6);
    }

    private void showOlfSupport(LimitScaling scaling) {
        OlfLimitScalingSupport.Support support = OlfLimitScalingSupport.of(scaling);
        boolean shown = support.status() != OlfLimitScalingSupport.Status.APPLIED;
        olfLabel.setText(shown ? Messages.get("limitScalings.olf.status." + support.status().name()) + ": " + String.join(" ", support.reasons()) : "");
        olfLabel.setVisible(shown);
        olfLabel.setManaged(shown);
    }

    private void setDurations(List<LimitDurationCriterion> durations) {
        allDurationsCheckBox.setSelected(durations.isEmpty());
        permanentCheckBox.setSelected(durations.stream().anyMatch(PermanentDurationCriterion.class::isInstance));
        durationLowClosedCheckBox.setSelected(true);
        durationHighClosedCheckBox.setSelected(true);
        TemporaryMode mode = TemporaryMode.NONE;
        for (LimitDurationCriterion duration : durations) {
            switch (duration) {
                case AllTemporaryDurationCriterion _ -> mode = TemporaryMode.ALL;
                case EqualityTemporaryDurationCriterion c -> {
                    mode = TemporaryMode.EQUAL;
                    durationField.setText(String.valueOf(c.getDurationEqualityValue()));
                }
                case IntervalTemporaryDurationCriterion c -> {
                    mode = TemporaryMode.INTERVAL;
                    durationLowField.setText(c.getLowBound().map(String::valueOf).orElse(""));
                    durationLowClosedCheckBox.setSelected(c.isLowClosed());
                    durationHighField.setText(c.getHighBound().map(String::valueOf).orElse(""));
                    durationHighClosedCheckBox.setSelected(c.isHighClosed());
                }
                default -> {
                    // permanent, set above
                }
            }
        }
        temporaryChoiceBox.setValue(mode);
    }

    private void commit() {
        if (loading || onReplace == null) {
            return;
        }
        LimitScaling scaling;
        try {
            scaling = rebuild();
        } catch (PowsyblException | IllegalArgumentException _) {
            // e.g. a negative factor, or an interval with its low bound above its high bound: keep the last valid one
            return;
        }
        showOlfSupport(scaling);
        onReplace.accept(scaling);
    }

    private LimitScaling rebuild() {
        double value = Double.parseDouble(valueField.getText().trim()) / 100;
        return LimitScaling.builder(limitTypeChoiceBox.getValue(), value)
                .withMonitoringOnly(monitoringOnlyCheckBox.isSelected())
                .withContingencyContext(context())
                .withNetworkElementCriteria(List.copyOf(criteria))
                .withLimitDurationCriteria(durations())
                .withOperationalLimitsGroupIdSelection(List.copyOf(limitsGroupsComboBox.getCheckedItems()))
                .build();
    }

    private ContingencyContext context() {
        return switch (contextChoiceBox.getValue()) {
            case ALL -> ContingencyContext.all();
            case NONE -> ContingencyContext.none();
            case ONLY_CONTINGENCIES -> ContingencyContext.onlyContingencies();
            case SPECIFIC -> {
                String id = contingencyComboBox.getValue();
                if (id == null || id.isBlank()) {
                    throw new IllegalArgumentException("No contingency");
                }
                yield ContingencyContext.specificContingency(id.trim());
            }
        };
    }

    private List<LimitDurationCriterion> durations() {
        if (allDurationsCheckBox.isSelected()) {
            return List.of();
        }
        List<LimitDurationCriterion> durations = new ArrayList<>();
        if (permanentCheckBox.isSelected()) {
            durations.add(new PermanentDurationCriterion());
        }
        switch (temporaryChoiceBox.getValue()) {
            case ALL -> durations.add(new AllTemporaryDurationCriterion());
            case EQUAL -> durations.add(new EqualityTemporaryDurationCriterion(Integer.parseInt(durationField.getText().trim())));
            case INTERVAL -> {
                Integer low = parseInt(durationLowField.getText());
                Integer high = parseInt(durationHighField.getText());
                IntervalTemporaryDurationCriterion.Builder builder = IntervalTemporaryDurationCriterion.builder();
                if (low != null) {
                    builder.setLowBound(low, durationLowClosedCheckBox.isSelected());
                }
                if (high != null) {
                    builder.setHighBound(high, durationHighClosedCheckBox.isSelected());
                }
                durations.add(builder.build());
            }
            case NONE -> {
                // permanent limits only
            }
        }
        if (durations.isEmpty()) {
            // no limit at all: not a scaling
            throw new IllegalArgumentException("No limit selected");
        }
        return durations;
    }

    private static Integer parseInt(String text) {
        return text == null || text.isBlank() ? null : Integer.valueOf(text.trim());
    }

    @FXML
    private void onRemoveCriterion() {
        int index = criteriaListView.getSelectionModel().getSelectedIndex();
        if (index >= 0) {
            criteria.remove(index);
            commit();
        }
    }

    private void showCriterionEditor(NetworkElementCriterion criterion) {
        criterionEditor.getChildren().clear();
        criterionRebuilder = null;
        if (criterion == null) {
            return;
        }
        CriterionKind kind = CriterionKind.of(criterion);
        if (kind == CriterionKind.ID_LIST) {
            TextArea idsArea = new TextArea(String.join("\n", ((NetworkElementIdListCriterion) criterion).getNetworkElementIds()));
            idsArea.setPrefRowCount(4);
            idsArea.focusedProperty().addListener((_, _, focused) -> {
                if (!focused) {
                    commitCriterion();
                }
            });
            criterionRebuilder = previous -> new NetworkElementIdListCriterion(previous.getName(), new LinkedHashSet<>(
                    Arrays.stream(idsArea.getText().split("[\\s,;]+")).filter(id -> !id.isBlank()).toList()));
            criterionEditor.getChildren().addAll(new Label(Messages.get("limitScalings.criterion.idList.hint")), idsArea);
            return;
        }
        boolean wasLoading = loading;
        loading = true;
        List<CountryGroup> countryGroups = new ArrayList<>();
        List<VoltageIntervalGroup> voltageGroups = new ArrayList<>();
        Runnable onCommit = this::commitCriterion;
        if (kind == CriterionKind.ANY_EQUIPMENT) {
            countryGroups.add(new CountryGroup("limitScalings.criterion.country", onCommit));
            voltageGroups.add(new VoltageIntervalGroup("limitScalings.criterion.voltage", onCommit));
        } else {
            String[] countryKeys = kind.countryArity() == 1 ? new String[] {"desktop.common.column.country"}
                    : new String[] {"contingencies.criterion.country1", "contingencies.criterion.country2"};
            String[] voltageKeys = kind.voltageArity() == 1 ? new String[] {"desktop.common.column.nominalVoltage"}
                    : new String[] {"contingencies.criterion.voltage1", "contingencies.criterion.voltage2", "contingencies.criterion.voltage3"};
            for (int i = 0; i < kind.countryArity(); i++) {
                countryGroups.add(new CountryGroup(countryKeys[i], onCommit));
            }
            for (int i = 0; i < kind.voltageArity(); i++) {
                voltageGroups.add(new VoltageIntervalGroup(voltageKeys[i], onCommit));
            }
        }
        AbstractNetworkElementEquipmentCriterion equipment = (AbstractNetworkElementEquipmentCriterion) criterion;
        List<List<Country>> countries = countries(equipment.getCountryCriterion());
        for (int i = 0; i < countryGroups.size() && i < countries.size(); i++) {
            countryGroups.get(i).setValue(countries.get(i));
        }
        List<VoltageInterval> intervals = intervals(equipment.getNominalVoltageCriterion());
        for (int i = 0; i < voltageGroups.size() && i < intervals.size(); i++) {
            voltageGroups.get(i).setValue(intervals.get(i));
        }
        countryGroups.forEach(group -> criterionEditor.getChildren().add(group.root()));
        voltageGroups.forEach(group -> criterionEditor.getChildren().add(group.root()));
        criterionRebuilder = previous -> rebuildCriterion(kind, previous.getName(),
                countryGroups.stream().map(CountryGroup::getChecked).toList(),
                voltageGroups.stream().map(VoltageIntervalGroup::toInterval).toList());
        loading = wasLoading;
    }

    private static List<List<Country>> countries(Criterion criterion) {
        return switch (criterion) {
            case SingleCountryCriterion c -> List.of(c.getCountries());
            case AtLeastOneCountryCriterion c -> List.of(c.getCountries());
            case TwoCountriesCriterion c -> List.of(c.getCountries1(), c.getCountries2());
            case null, default -> List.of();
        };
    }

    private static List<VoltageInterval> intervals(Criterion criterion) {
        return switch (criterion) {
            case SingleNominalVoltageCriterion c -> Arrays.asList(c.getVoltageInterval());
            case AtLeastOneNominalVoltageCriterion c -> Arrays.asList(c.getVoltageInterval());
            case TwoNominalVoltageCriterion c -> Arrays.asList(c.getVoltageInterval1().orElse(null), c.getVoltageInterval2().orElse(null));
            case ThreeNominalVoltageCriterion c -> Arrays.asList(c.getVoltageInterval1().orElse(null), c.getVoltageInterval2().orElse(null),
                    c.getVoltageInterval3().orElse(null));
            case null, default -> List.of();
        };
    }

    private static NetworkElementCriterion rebuildCriterion(CriterionKind kind, String name, List<List<Country>> countries, List<VoltageInterval> intervals) {
        return switch (kind) {
            case ANY_EQUIPMENT -> anyEquipment(name, countries.getFirst(), intervals.getFirst());
            case LINE -> new LineCriterion(name, twoCountries(countries), twoVoltages(intervals));
            case TIE_LINE -> new TieLineCriterion(name, twoCountries(countries), twoVoltages(intervals));
            case TWO_WINDINGS_TRANSFORMER -> new TwoWindingsTransformerCriterion(name, singleCountry(countries), twoVoltages(intervals));
            case THREE_WINDINGS_TRANSFORMER -> new ThreeWindingsTransformerCriterion(name, singleCountry(countries),
                    intervals.stream().allMatch(i -> i == null) ? null : new ThreeNominalVoltageCriterion(intervals.getFirst(), intervals.get(1), intervals.getLast()));
            case BOUNDARY_LINE -> new BoundaryLineCriterion(name, singleCountry(countries),
                    intervals.getFirst() == null ? null : new SingleNominalVoltageCriterion(intervals.getFirst()));
            case ID_LIST -> throw new IllegalStateException("Edited as a text");
        };
    }

    // an IdentifiableCriterion needs a country or a nominal voltage criterion
    private static IdentifiableCriterion anyEquipment(String name, List<Country> countries, VoltageInterval interval) {
        if (countries.isEmpty() && interval == null) {
            throw new IllegalArgumentException("Neither countries nor nominal voltages");
        }
        if (interval == null) {
            return new IdentifiableCriterion(name, new AtLeastOneCountryCriterion(countries));
        }
        AtLeastOneNominalVoltageCriterion voltage = new AtLeastOneNominalVoltageCriterion(interval);
        return countries.isEmpty() ? new IdentifiableCriterion(name, voltage)
                : new IdentifiableCriterion(name, new AtLeastOneCountryCriterion(countries), voltage);
    }

    private static SingleCountryCriterion singleCountry(List<List<Country>> countries) {
        return countries.getFirst().isEmpty() ? null : new SingleCountryCriterion(countries.getFirst());
    }

    private static TwoCountriesCriterion twoCountries(List<List<Country>> countries) {
        return countries.getFirst().isEmpty() && countries.getLast().isEmpty() ? null : new TwoCountriesCriterion(countries.getFirst(), countries.getLast());
    }

    private static TwoNominalVoltageCriterion twoVoltages(List<VoltageInterval> intervals) {
        return intervals.getFirst() == null && intervals.getLast() == null ? null : new TwoNominalVoltageCriterion(intervals.getFirst(), intervals.getLast());
    }

    private void commitCriterion() {
        int index = criteriaListView.getSelectionModel().getSelectedIndex();
        if (loading || criterionRebuilder == null || index < 0) {
            return;
        }
        NetworkElementCriterion rebuilt;
        try {
            rebuilt = criterionRebuilder.apply(criteria.get(index));
        } catch (PowsyblException | IllegalArgumentException _) {
            // e.g. an interval with its low bound above its high bound: keep the last valid criterion
            return;
        }
        replacingCriterion = true;
        try {
            criteria.set(index, rebuilt);
            criteriaListView.getSelectionModel().select(index);
        } finally {
            replacingCriterion = false;
        }
        commit();
    }
}
