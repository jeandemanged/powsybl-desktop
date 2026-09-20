/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.contingency;

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
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.ChoiceBoxTableCell;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import org.controlsfx.control.CheckComboBox;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

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
        propertyKeyColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        propertyKeyColumn.setOnEditCommit(event -> {
            event.getRowValue().keyProperty().set(event.getNewValue());
            commit();
        });
        propertyValuesColumn.setCellValueFactory(cellData -> cellData.getValue().valuesProperty());
        propertyValuesColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        propertyValuesColumn.setOnEditCommit(event -> {
            event.getRowValue().valuesProperty().set(event.getNewValue());
            commit();
        });
        equipmentToCheckColumn.setCellValueFactory(cellData -> cellData.getValue().equipmentToCheckProperty());
        equipmentToCheckColumn.setCellFactory(ChoiceBoxTableCell.forTableColumn(EquipmentToCheck.values()));
        equipmentToCheckColumn.setOnEditCommit(event -> {
            event.getRowValue().equipmentToCheckProperty().set(event.getNewValue());
            commit();
        });
        ObservableList<SideToCheck> sideChoices = FXCollections.observableArrayList();
        sideChoices.add(null);
        sideChoices.addAll(List.of(SideToCheck.values()));
        sideToCheckColumn.setCellValueFactory(cellData -> cellData.getValue().sideToCheckProperty());
        sideToCheckColumn.setCellFactory(ChoiceBoxTableCell.forTableColumn(SIDE_TO_CHECK_CONVERTER, sideChoices));
        sideToCheckColumn.setOnEditCommit(event -> {
            event.getRowValue().sideToCheckProperty().set(event.getNewValue());
            commit();
        });
        removePropertyCriterionButton.disableProperty().bind(propertyCriteriaTableView.getSelectionModel().selectedItemProperty().isNull());

        commitOnChange(nameField, this::commit);
        commitOnChange(regexField, this::commit);
    }

    public void setContingencyList(ContingencyListKind kind, AbstractEquipmentCriterionContingencyList list, Consumer<ContingencyList> onReplace) {
        this.kind = kind;
        this.onReplace = onReplace;
        nameField.setText(list.getName());
        buildCriteriaControls();
        populateFromList(list);
        propertyRows.setAll(list.getPropertyCriteria().stream().map(PropertyCriterionRow::new).collect(Collectors.toList()));
        regexField.setText(list.getRegexCriterion() == null ? "" : list.getRegexCriterion().getRegex());
        boolean showType = kind.hasIdentifiableTypePicker();
        identifiableTypeBox.setVisible(showType);
        identifiableTypeBox.setManaged(showType);
        if (showType) {
            identifiableTypeChoiceBox.setItems(FXCollections.observableArrayList(ContingencyListKind.INJECTION_TYPES));
            identifiableTypeChoiceBox.getSelectionModel().select(list.getIdentifiableType());
            identifiableTypeChoiceBox.valueProperty().addListener((obs, oldValue, newValue) -> commit());
        }
    }

    private void buildCriteriaControls() {
        criteriaContainer.getChildren().clear();
        countryGroups.clear();
        voltageGroups.clear();
        Runnable onCommit = this::commit;
        if (kind.countryArity() == 1) {
            countryGroups.add(new CountryGroup("contingencies.criterion.country", onCommit));
        } else if (kind.countryArity() == 2) {
            countryGroups.add(new CountryGroup("contingencies.criterion.country1", onCommit));
            countryGroups.add(new CountryGroup("contingencies.criterion.country2", onCommit));
        }
        String[] indexedVoltageKeys = {"contingencies.criterion.voltage1", "contingencies.criterion.voltage2", "contingencies.criterion.voltage3"};
        if (kind.voltageArity() == 1) {
            voltageGroups.add(new VoltageIntervalGroup("contingencies.criterion.voltage", onCommit));
        } else {
            for (int i = 0; i < kind.voltageArity(); i++) {
                voltageGroups.add(new VoltageIntervalGroup(indexedVoltageKeys[i], onCommit));
            }
        }
        countryGroups.forEach(group -> criteriaContainer.getChildren().add(group.root));
        voltageGroups.forEach(group -> criteriaContainer.getChildren().add(group.root));
    }

    private void populateFromList(AbstractEquipmentCriterionContingencyList list) {
        if (kind.countryArity() == 1 && list.getCountryCriterion() instanceof SingleCountryCriterion c) {
            countryGroups.get(0).setValue(c.getCountries());
        } else if (kind.countryArity() == 2 && list.getCountryCriterion() instanceof TwoCountriesCriterion c) {
            countryGroups.get(0).setValue(c.getCountries1());
            countryGroups.get(1).setValue(c.getCountries2());
        }
        if (kind.voltageArity() == 1 && list.getNominalVoltageCriterion() instanceof SingleNominalVoltageCriterion v) {
            voltageGroups.get(0).setValue(v.getVoltageInterval());
        } else if (kind.voltageArity() == 2 && list.getNominalVoltageCriterion() instanceof TwoNominalVoltageCriterion v) {
            voltageGroups.get(0).setValue(v.getVoltageInterval1().orElse(null));
            voltageGroups.get(1).setValue(v.getVoltageInterval2().orElse(null));
        } else if (kind.voltageArity() == 3 && list.getNominalVoltageCriterion() instanceof ThreeNominalVoltageCriterion v) {
            voltageGroups.get(0).setValue(v.getVoltageInterval1().orElse(null));
            voltageGroups.get(1).setValue(v.getVoltageInterval2().orElse(null));
            voltageGroups.get(2).setValue(v.getVoltageInterval3().orElse(null));
        }
    }

    private void commit() {
        if (onReplace == null) {
            return;
        }
        onReplace.accept(rebuild());
    }

    private ContingencyList rebuild() {
        String name = nameField.getText();
        List<PropertyCriterion> propertyCriteria = propertyRows.stream().map(PropertyCriterionRow::toCriterion).collect(Collectors.toList());
        RegexCriterion regex = regexField.getText() == null || regexField.getText().isBlank() ? null : new RegexCriterion(regexField.getText());
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

    private SingleCountryCriterion singleCountry() {
        List<Country> selected = countryGroups.get(0).getChecked();
        return selected.isEmpty() ? null : new SingleCountryCriterion(selected);
    }

    private TwoCountriesCriterion twoCountries() {
        List<Country> countries1 = countryGroups.get(0).getChecked();
        List<Country> countries2 = countryGroups.get(1).getChecked();
        return countries1.isEmpty() && countries2.isEmpty() ? null : new TwoCountriesCriterion(countries1, countries2);
    }

    private SingleNominalVoltageCriterion singleVoltage() {
        VoltageInterval interval = voltageGroups.get(0).toInterval();
        return interval == null ? null : new SingleNominalVoltageCriterion(interval);
    }

    private TwoNominalVoltageCriterion twoVoltage() {
        VoltageInterval interval1 = voltageGroups.get(0).toInterval();
        VoltageInterval interval2 = voltageGroups.get(1).toInterval();
        return interval1 == null && interval2 == null ? null : new TwoNominalVoltageCriterion(interval1, interval2);
    }

    private ThreeNominalVoltageCriterion threeVoltage() {
        VoltageInterval interval1 = voltageGroups.get(0).toInterval();
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

    private static void commitOnChange(TextField field, Runnable onCommit) {
        field.setOnAction(event -> onCommit.run());
        field.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (Boolean.FALSE.equals(isFocused)) {
                onCommit.run();
            }
        });
    }

    private static final class CountryGroup {
        private final CheckComboBox<Country> comboBox = new CheckComboBox<>(FXCollections.observableArrayList(Country.values()));
        private final Node root;

        CountryGroup(String titleKey, Runnable onCommit) {
            comboBox.getCheckModel().getCheckedItems().addListener((ListChangeListener<Country>) change -> onCommit.run());
            root = new VBox(4, new Label(Messages.get(titleKey)), comboBox);
        }

        void setValue(List<Country> countries) {
            comboBox.getCheckModel().clearChecks();
            countries.forEach(comboBox.getCheckModel()::check);
        }

        List<Country> getChecked() {
            return List.copyOf(comboBox.getCheckModel().getCheckedItems());
        }
    }

    private static final class VoltageIntervalGroup {
        private final TextField lowField = new TextField();
        private final CheckBox lowClosedCheckBox = new CheckBox(Messages.get("contingencies.criterion.voltage.lowClosed"));
        private final TextField highField = new TextField();
        private final CheckBox highClosedCheckBox = new CheckBox(Messages.get("contingencies.criterion.voltage.highClosed"));
        private final Node root;

        VoltageIntervalGroup(String titleKey, Runnable onCommit) {
            lowClosedCheckBox.setSelected(true);
            highClosedCheckBox.setSelected(true);
            HBox lowBox = new HBox(4, new Label(Messages.get("contingencies.criterion.voltage.lowBound")), lowField, lowClosedCheckBox);
            HBox highBox = new HBox(4, new Label(Messages.get("contingencies.criterion.voltage.highBound")), highField, highClosedCheckBox);
            root = new VBox(4, new Label(Messages.get(titleKey)), lowBox, highBox);
            commitOnChange(lowField, onCommit);
            commitOnChange(highField, onCommit);
            lowClosedCheckBox.selectedProperty().addListener((obs, oldValue, newValue) -> onCommit.run());
            highClosedCheckBox.selectedProperty().addListener((obs, oldValue, newValue) -> onCommit.run());
        }

        void setValue(VoltageInterval interval) {
            lowField.setText(interval == null ? "" : interval.getNominalVoltageLowBound().map(String::valueOf).orElse(""));
            highField.setText(interval == null ? "" : interval.getNominalVoltageHighBound().map(String::valueOf).orElse(""));
            lowClosedCheckBox.setSelected(interval == null || interval.isLowClosed());
            highClosedCheckBox.setSelected(interval == null || interval.isHighClosed());
        }

        VoltageInterval toInterval() {
            Double low = parse(lowField.getText());
            Double high = parse(highField.getText());
            if (low == null && high == null) {
                return null;
            }
            VoltageInterval.Builder builder = VoltageInterval.builder();
            if (low != null) {
                builder.setLowBound(low, lowClosedCheckBox.isSelected());
            }
            if (high != null) {
                builder.setHighBound(high, highClosedCheckBox.isSelected());
            }
            return builder.build();
        }

        private static Double parse(String text) {
            if (text == null || text.isBlank()) {
                return null;
            }
            try {
                return Double.valueOf(text.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }
}
