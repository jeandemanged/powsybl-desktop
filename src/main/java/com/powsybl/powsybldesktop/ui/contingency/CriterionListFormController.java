/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.ui.contingency;

import com.google.re2j.Pattern;
import com.google.re2j.PatternSyntaxException;
import com.powsybl.contingency.list.AbstractEquipmentCriterionContingencyList;
import com.powsybl.contingency.list.ContingencyList;
import com.powsybl.contingency.list.HvdcLineCriterionContingencyList;
import com.powsybl.contingency.list.InjectionCriterionContingencyList;
import com.powsybl.contingency.list.LineCriterionContingencyList;
import com.powsybl.contingency.list.ThreeWindingsTransformerCriterionContingencyList;
import com.powsybl.contingency.list.TieLineCriterionContingencyList;
import com.powsybl.contingency.list.TwoWindingsTransformerCriterionContingencyList;
import com.powsybl.iidm.criteria.PropertyCriterion;
import com.powsybl.iidm.criteria.PropertyCriterion.EquipmentToCheck;
import com.powsybl.iidm.criteria.PropertyCriterion.SideToCheck;
import com.powsybl.iidm.criteria.RegexCriterion;
import com.powsybl.iidm.criteria.SingleCountryCriterion;
import com.powsybl.iidm.criteria.SingleNominalVoltageCriterion;
import com.powsybl.iidm.criteria.ThreeNominalVoltageCriterion;
import com.powsybl.iidm.criteria.TwoCountriesCriterion;
import com.powsybl.iidm.criteria.TwoNominalVoltageCriterion;
import com.powsybl.iidm.criteria.VoltageInterval;
import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.IdentifiableType;
import com.powsybl.powsybldesktop.contingency.ContingencyListKind;
import com.powsybl.powsybldesktop.contingency.PropertyCriterionRow;
import com.powsybl.powsybldesktop.ui.controls.CountryGroup;
import com.powsybl.powsybldesktop.ui.controls.VoltageIntervalGroup;
import com.powsybl.powsybldesktop.ui.form.CellFields;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import javafx.util.converter.DefaultStringConverter;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Generic inline editor reused for all 6 equipment-criterion {@link ContingencyList} types (see
 * {@link ContingencyListKind}): the country/voltage criterion pickers are built dynamically since their arity
 * (0/1/2 sides, 0/1/2/3 voltage intervals) varies by type, everything else (property criteria, regex) is shared.
 * Like {@link DefaultContingencyListFormController}, every field commit rebuilds the immutable concrete list and
 * pushes it via {@code onReplace}.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class CriterionListFormController {

    private static final StringConverter<SideToCheck> SIDE_TO_CHECK_CONVERTER = new StringConverter<>() {
        @Override
        public String toString(SideToCheck value) {
            return value == null ? "-" : value.toString();
        }

        @Override
        public SideToCheck fromString(String string) {
            return null;
        }
    };

    @FXML
    private TextField nameField;

    @FXML
    private VBox criteriaContainer;

    @FXML
    private HBox identifiableTypeBox;

    @FXML
    private ChoiceBox<IdentifiableType> identifiableTypeChoiceBox;

    @FXML
    private TableView<PropertyCriterionRow> propertyCriteriaTableView;

    @FXML
    private TableColumn<PropertyCriterionRow, String> propertyKeyColumn;

    @FXML
    private TableColumn<PropertyCriterionRow, String> propertyValuesColumn;

    @FXML
    private TableColumn<PropertyCriterionRow, EquipmentToCheck> equipmentToCheckColumn;

    @FXML
    private TableColumn<PropertyCriterionRow, SideToCheck> sideToCheckColumn;

    @FXML
    private Button removePropertyCriterionButton;

    @FXML
    private TextField regexField;

    private final ObservableList<PropertyCriterionRow> propertyRows = FXCollections.observableArrayList();
    private final List<CountryGroup> countryGroups = new ArrayList<>();
    private final List<VoltageIntervalGroup> voltageGroups = new ArrayList<>();

    private ContingencyListKind kind;
    private Consumer<ContingencyList> onReplace;

    @FXML
    private void initialize() {
        propertyCriteriaTableView.setItems(propertyRows);
        propertyKeyColumn.setCellValueFactory(cellData -> cellData.getValue().keyProperty());
        CellFields.textColumn(propertyKeyColumn, new DefaultStringConverter(), _ -> true, CellFields.RowKey.ITEM);
        propertyKeyColumn.setOnEditCommit(event -> {
            event.getRowValue().keyProperty().set(event.getNewValue());
            replace();
        });
        propertyValuesColumn.setCellValueFactory(cellData -> cellData.getValue().valuesProperty());
        CellFields.textColumn(propertyValuesColumn, new DefaultStringConverter(), _ -> true, CellFields.RowKey.ITEM);
        propertyValuesColumn.setOnEditCommit(event -> {
            event.getRowValue().valuesProperty().set(event.getNewValue());
            replace();
        });
        equipmentToCheckColumn.setCellValueFactory(cellData -> cellData.getValue().equipmentToCheckProperty());
        CellFields.choiceColumn(equipmentToCheckColumn, List.of(EquipmentToCheck.values()), null);
        equipmentToCheckColumn.setOnEditCommit(event -> {
            event.getRowValue().equipmentToCheckProperty().set(event.getNewValue());
            replace();
        });
        ObservableList<SideToCheck> sideChoices = FXCollections.observableArrayList();
        sideChoices.add(null);
        sideChoices.addAll(List.of(SideToCheck.values()));
        sideToCheckColumn.setCellValueFactory(cellData -> cellData.getValue().sideToCheckProperty());
        CellFields.choiceColumn(sideToCheckColumn, sideChoices, SIDE_TO_CHECK_CONVERTER);
        sideToCheckColumn.setOnEditCommit(event -> {
            event.getRowValue().sideToCheckProperty().set(event.getNewValue());
            replace();
        });
        removePropertyCriterionButton.disableProperty().bind(propertyCriteriaTableView.getSelectionModel().selectedItemProperty().isNull());

        VoltageIntervalGroup.commitOnChange(nameField, this::commit);
        VoltageIntervalGroup.commitOnChange(regexField, this::commit);
    }

    public void setContingencyList(ContingencyListKind kind, AbstractEquipmentCriterionContingencyList list, Consumer<ContingencyList> onReplace) {
        this.kind = kind;
        nameField.setText(list.getName());
        buildCriteriaControls();
        populateFromList(list);
        propertyRows.setAll(list.getPropertyCriteria().stream().map(PropertyCriterionRow::new).toList());
        regexField.setText(list.getRegexCriterion() == null ? "" : list.getRegexCriterion().getRegex());
        boolean showType = kind.hasIdentifiableTypePicker();
        identifiableTypeBox.setVisible(showType);
        identifiableTypeBox.setManaged(showType);
        if (showType) {
            identifiableTypeChoiceBox.setItems(FXCollections.observableArrayList(ContingencyListKind.INJECTION_TYPES));
            identifiableTypeChoiceBox.getSelectionModel().select(list.getIdentifiableType());
            identifiableTypeChoiceBox.valueProperty().addListener((_, _, _) -> commit());
        }
        // Populating the country/voltage controls fires their change listeners - committing then would push a
        // spurious replacement, possibly while the lists are still mid-change (the form is rebuilt from the
        // selection change triggered by a removal), so commit() stays a no-op until the form is fully loaded.
        this.onReplace = onReplace;
    }

    private void buildCriteriaControls() {
        criteriaContainer.getChildren().clear();
        countryGroups.clear();
        voltageGroups.clear();
        Runnable onCommit = this::commit;
        if (kind.countryArity() == 1) {
            countryGroups.add(new CountryGroup("desktop.common.column.country", onCommit));
        } else if (kind.countryArity() == 2) {
            countryGroups.add(new CountryGroup("contingencies.criterion.country1", onCommit));
            countryGroups.add(new CountryGroup("contingencies.criterion.country2", onCommit));
        }
        String[] indexedVoltageKeys = {"contingencies.criterion.voltage1", "contingencies.criterion.voltage2", "contingencies.criterion.voltage3"};
        if (kind.voltageArity() == 1) {
            voltageGroups.add(new VoltageIntervalGroup("desktop.common.column.nominalVoltage", onCommit));
        } else {
            for (int i = 0; i < kind.voltageArity(); i++) {
                voltageGroups.add(new VoltageIntervalGroup(indexedVoltageKeys[i], onCommit));
            }
        }
        countryGroups.forEach(group -> criteriaContainer.getChildren().add(group.root()));
        voltageGroups.forEach(group -> criteriaContainer.getChildren().add(group.root()));
    }

    private void populateFromList(AbstractEquipmentCriterionContingencyList list) {
        if (kind.countryArity() == 1 && list.getCountryCriterion() instanceof SingleCountryCriterion c) {
            countryGroups.getFirst().setValue(c.getCountries());
        } else if (kind.countryArity() == 2 && list.getCountryCriterion() instanceof TwoCountriesCriterion c) {
            countryGroups.getFirst().setValue(c.getCountries1());
            countryGroups.get(1).setValue(c.getCountries2());
        }
        if (kind.voltageArity() == 1 && list.getNominalVoltageCriterion() instanceof SingleNominalVoltageCriterion v) {
            voltageGroups.getFirst().setValue(v.getVoltageInterval());
        } else if (kind.voltageArity() == 2 && list.getNominalVoltageCriterion() instanceof TwoNominalVoltageCriterion v) {
            voltageGroups.getFirst().setValue(v.getVoltageInterval1().orElse(null));
            voltageGroups.get(1).setValue(v.getVoltageInterval2().orElse(null));
        } else if (kind.voltageArity() == 3 && list.getNominalVoltageCriterion() instanceof ThreeNominalVoltageCriterion v) {
            voltageGroups.getFirst().setValue(v.getVoltageInterval1().orElse(null));
            voltageGroups.get(1).setValue(v.getVoltageInterval2().orElse(null));
            voltageGroups.get(2).setValue(v.getVoltageInterval3().orElse(null));
        }
    }

    private void commit() {
        try {
            replace();
        } catch (IllegalArgumentException _) {
            // e.g. a voltage interval with its low bound above its high bound, or a criterion checkEvaluable
            // rejects: keep the last valid list
        }
    }

    // a table cell's commit: an invalid list is reported by its field, see CellFields
    private void replace() {
        if (onReplace != null) {
            onReplace.accept(rebuild());
        }
    }

    private ContingencyList rebuild() {
        String name = nameField.getText();
        List<PropertyCriterion> propertyCriteria = propertyRows.stream().map(PropertyCriterionRow::toCriterion).toList();
        propertyCriteria.forEach(this::checkEvaluable);
        RegexCriterion regex = regexField.getText() == null || regexField.getText().isBlank() ? null : new RegexCriterion(regexField.getText());
        if (regex != null) {
            try {
                // only compiled when the list is evaluated otherwise
                Pattern.compile(regex.getRegex());
            } catch (PatternSyntaxException e) {
                throw new IllegalArgumentException(e.getMessage(), e);
            }
        }
        return switch (kind) {
            case LINE_CRITERION -> new LineCriterionContingencyList(name, twoCountries(), twoVoltage(), propertyCriteria, regex);
            case TIE_LINE_CRITERION -> new TieLineCriterionContingencyList(name, twoCountries(), singleVoltage(), propertyCriteria, regex);
            case INJECTION_CRITERION ->
                new InjectionCriterionContingencyList(name, identifiableTypeChoiceBox.getValue(), singleCountry(), singleVoltage(), propertyCriteria, regex);
            case TWO_WINDINGS_TRANSFORMER_CRITERION ->
                new TwoWindingsTransformerCriterionContingencyList(name, singleCountry(), twoVoltage(), propertyCriteria, regex);
            case THREE_WINDINGS_TRANSFORMER_CRITERION ->
                new ThreeWindingsTransformerCriterionContingencyList(name, singleCountry(), threeVoltage(), propertyCriteria, regex);
            case HVDC_LINE_CRITERION -> new HvdcLineCriterionContingencyList(name, twoCountries(), twoVoltage(), propertyCriteria, regex);
            case DEFAULT -> throw new IllegalStateException("DEFAULT lists are edited by DefaultContingencyListFormController");
        };
    }

    // Combinations that PropertyCriterion.filter only rejects when the list is evaluated (throwing out of the
    // contingencies table refresh and the security analysis run): a voltage level/substation check needs a side
    // on branches (ONE or BOTH only on two-sided ones), and isn't implemented at all for tie lines.
    private void checkEvaluable(PropertyCriterion criterion) {
        if (criterion.getEquipmentToCheck() == EquipmentToCheck.SELF) {
            return;
        }
        SideToCheck side = criterion.getSideToCheck();
        boolean valid = switch (kind) {
            case TIE_LINE_CRITERION -> false;
            case LINE_CRITERION, TWO_WINDINGS_TRANSFORMER_CRITERION, HVDC_LINE_CRITERION -> side == SideToCheck.ONE || side == SideToCheck.BOTH;
            case THREE_WINDINGS_TRANSFORMER_CRITERION -> side != null;
            case INJECTION_CRITERION, DEFAULT -> true;
        };
        if (!valid) {
            throw new IllegalArgumentException("Property criterion on " + criterion.getEquipmentToCheck() + " with side " + side + " can't be evaluated for " + kind);
        }
    }

    private SingleCountryCriterion singleCountry() {
        List<Country> selected = countryGroups.getFirst().getChecked();
        return selected.isEmpty() ? null : new SingleCountryCriterion(selected);
    }

    private TwoCountriesCriterion twoCountries() {
        List<Country> countries1 = countryGroups.getFirst().getChecked();
        List<Country> countries2 = countryGroups.get(1).getChecked();
        return countries1.isEmpty() && countries2.isEmpty() ? null : new TwoCountriesCriterion(countries1, countries2);
    }

    private SingleNominalVoltageCriterion singleVoltage() {
        VoltageInterval interval = voltageGroups.getFirst().toInterval();
        return interval == null ? null : new SingleNominalVoltageCriterion(interval);
    }

    private TwoNominalVoltageCriterion twoVoltage() {
        VoltageInterval interval1 = voltageGroups.getFirst().toInterval();
        VoltageInterval interval2 = voltageGroups.get(1).toInterval();
        return interval1 == null && interval2 == null ? null : new TwoNominalVoltageCriterion(interval1, interval2);
    }

    private ThreeNominalVoltageCriterion threeVoltage() {
        VoltageInterval interval1 = voltageGroups.getFirst().toInterval();
        VoltageInterval interval2 = voltageGroups.get(1).toInterval();
        VoltageInterval interval3 = voltageGroups.get(2).toInterval();
        return interval1 == null && interval2 == null && interval3 == null ? null : new ThreeNominalVoltageCriterion(interval1, interval2, interval3);
    }

    @FXML
    private void onAddPropertyCriterion() {
        propertyRows.add(new PropertyCriterionRow());
    }

    @FXML
    private void onRemovePropertyCriterion() {
        PropertyCriterionRow selected = propertyCriteriaTableView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        propertyRows.remove(selected);
        commit();
    }
}
