/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.ui.rao;

import com.powsybl.contingency.Contingency;
import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.Network;
import com.powsybl.openrao.data.crac.api.Crac;
import com.powsybl.openrao.data.crac.api.Instant;
import com.powsybl.openrao.data.crac.api.State;
import com.powsybl.openrao.data.crac.api.cnec.Cnec;
import com.powsybl.powsybldesktop.contingency.ContingencyNames;
import com.powsybl.powsybldesktop.rao.RangeActionEdits;
import com.powsybl.powsybldesktop.rao.RaoResultRows;
import com.powsybl.powsybldesktop.rao.UsageRuleEdits;
import com.powsybl.powsybldesktop.rao.UsageRuleEdits.Kind;
import com.powsybl.powsybldesktop.rao.UsageRuleEdits.UsageRuleSpec;
import com.powsybl.powsybldesktop.utils.Labels;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Window;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * Asks a usage rule of a remedial action: its kind, its instant (see {@link UsageRuleEdits#instants}), and as the kind
 * needs the contingency, the CNEC (those at the instant or after it) or the country (the network's). OK checks it
 * ({@link UsageRuleEdits#check}), the dialog staying open with the reason when it is refused. A {@code Dialog} subclass so
 * that its test can reach its controls.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
final class UsageRuleDialog extends Dialog<UsageRuleSpec> {

    // a rule on a flow constraint in a country after any contingency: the contingency box's first item
    static final String ANY_CONTINGENCY = "";

    private final Crac crac;
    final ChoiceBox<Kind> kindChoiceBox = new ChoiceBox<>(FXCollections.observableArrayList(Kind.values()));
    final ChoiceBox<Instant> instantChoiceBox = new ChoiceBox<>();
    final ComboBox<String> contingencyComboBox = new ComboBox<>();
    final ComboBox<Cnec<?>> cnecComboBox = new ComboBox<>();
    final ChoiceBox<Country> countryChoiceBox = new ChoiceBox<>();
    final Label errorLabel = new Label();
    private final List<Node> contingencyRow;
    private final List<Node> cnecRow;
    private final List<Node> countryRow;

    /**
     * @param stateName the name of a CNEC's state
     */
    UsageRuleDialog(Window owner, Crac crac, Network network, Function<State, String> stateName) {
        this.crac = crac;
        initOwner(owner);
        initModality(Modality.WINDOW_MODAL);
        setTitle(Messages.get("crac.usageRules.add.title"));
        setResizable(true);

        kindChoiceBox.setConverter(converter(Kind::label));
        instantChoiceBox.setConverter(converter(instant -> Messages.get("crac.instant.item", instant.getId(),
                Messages.get("crac.instant.kind." + instant.getKind().name().toLowerCase(Locale.ROOT)))));
        contingencyComboBox.setConverter(converter(id -> {
            if (id.equals(ANY_CONTINGENCY)) {
                return Messages.get("crac.usageRules.anyContingency");
            }
            Contingency contingency = crac.getContingency(id);
            return contingency == null ? id : ContingencyNames.displayName(contingency, network);
        }));
        contingencyComboBox.setMaxWidth(Double.MAX_VALUE);
        cnecComboBox.setConverter(converter(cnec -> Messages.get("crac.cnec.item", cnec.getName(), stateName.apply(cnec.getState()))));
        cnecComboBox.setMaxWidth(Double.MAX_VALUE);
        List<Country> countries = RangeActionEdits.countries(network);
        countryChoiceBox.setItems(FXCollections.observableArrayList(countries.isEmpty()
                ? Arrays.stream(Country.values()).sorted(Comparator.comparing(Country::name)).toList() : countries));
        countryChoiceBox.setConverter(Labels.countryFormat());
        if (!countryChoiceBox.getItems().isEmpty()) {
            countryChoiceBox.setValue(countryChoiceBox.getItems().getFirst());
        }
        FormFields.wrap(errorLabel);
        errorLabel.getStyleClass().add("alert-label");
        errorLabel.setVisible(false);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.addRow(0, new Label(Messages.get("crac.usageRules.kind")), kindChoiceBox);
        grid.addRow(1, new Label(Messages.get("crac.usageRules.instant")), instantChoiceBox);
        Label contingencyLabel = new Label(Messages.get("crac.usageRules.contingency"));
        grid.addRow(2, contingencyLabel, contingencyComboBox);
        Label cnecLabel = new Label(Messages.get("crac.usageRules.cnec"));
        grid.addRow(3, cnecLabel, cnecComboBox);
        Label countryLabel = new Label(Messages.get("crac.usageRules.country"));
        grid.addRow(4, countryLabel, countryChoiceBox);
        contingencyRow = List.of(contingencyLabel, contingencyComboBox);
        cnecRow = List.of(cnecLabel, cnecComboBox);
        countryRow = List.of(countryLabel, countryChoiceBox);
        for (Node node : grid.getChildren()) {
            node.managedProperty().bind(node.visibleProperty());
        }
        VBox content = new VBox(10, grid, errorLabel);
        content.setPrefWidth(520);
        getDialogPane().setContent(content);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        getDialogPane().lookupButton(ButtonType.OK).addEventFilter(ActionEvent.ACTION, event -> {
            if (!check()) {
                event.consume();
            }
        });
        setResultConverter(button -> button == ButtonType.OK ? rule() : null);

        kindChoiceBox.valueProperty().addListener((_, _, _) -> onKind());
        instantChoiceBox.valueProperty().addListener((_, _, _) -> refreshCnecs());
        kindChoiceBox.setValue(Kind.ON_INSTANT);
    }

    private static <T> StringConverter<T> converter(Function<T, String> text) {
        return new StringConverter<>() {
            @Override
            public String toString(T value) {
                return value == null ? "" : text.apply(value);
            }

            @Override
            public T fromString(String string) {
                throw new UnsupportedOperationException();
            }
        };
    }

    // the instants and the contingencies the kind takes, the selection kept when it still can be
    private void onKind() {
        Kind kind = kindChoiceBox.getValue();
        Instant instant = instantChoiceBox.getValue();
        List<Instant> instants = UsageRuleEdits.instants(crac, kind);
        instantChoiceBox.setItems(FXCollections.observableArrayList(instants));
        instantChoiceBox.setValue(instants.contains(instant) ? instant : instants.stream().findFirst().orElse(null));

        String contingency = contingencyComboBox.getValue();
        List<String> contingencies = new ArrayList<>();
        if (kind == Kind.ON_FLOW_CONSTRAINT_IN_COUNTRY) {
            contingencies.add(ANY_CONTINGENCY);
        }
        crac.getContingencies().stream().map(Contingency::getId).sorted().forEach(contingencies::add);
        contingencyComboBox.setItems(FXCollections.observableArrayList(contingencies));
        contingencyComboBox.setValue(contingencies.contains(contingency) ? contingency : contingencies.stream().findFirst().orElse(null));

        contingencyRow.forEach(node -> node.setVisible(kind == Kind.ON_CONTINGENCY_STATE || kind == Kind.ON_FLOW_CONSTRAINT_IN_COUNTRY));
        cnecRow.forEach(node -> node.setVisible(kind == Kind.ON_CONSTRAINT));
        countryRow.forEach(node -> node.setVisible(kind == Kind.ON_FLOW_CONSTRAINT_IN_COUNTRY));
        errorLabel.setVisible(false);
        if (getDialogPane().getScene() != null && getDialogPane().getScene().getWindow() != null) {
            getDialogPane().getScene().getWindow().sizeToScene();
        }
    }

    // those at the instant or after it, the only ones OpenRAO accepts
    private void refreshCnecs() {
        Instant instant = instantChoiceBox.getValue();
        Cnec<?> selected = cnecComboBox.getValue();
        List<Cnec<?>> cnecs = crac.getCnecs().stream().<Cnec<?>>map(cnec -> cnec)
                .filter(cnec -> instant == null || !cnec.getState().getInstant().comesBefore(instant))
                .sorted(Comparator.<Cnec<?>, State>comparing(Cnec::getState, RaoResultRows.STATE_ORDER).thenComparing(Cnec::getId)).toList();
        cnecComboBox.setItems(FXCollections.observableArrayList(cnecs));
        cnecComboBox.setValue(cnecs.contains(selected) ? selected : cnecs.stream().findFirst().orElse(null));
    }

    // the rule the controls tell
    UsageRuleSpec rule() {
        String instant = instantChoiceBox.getValue() == null ? null : instantChoiceBox.getValue().getId();
        String contingency = contingencyComboBox.getValue();
        return switch (kindChoiceBox.getValue()) {
            case ON_INSTANT -> UsageRuleSpec.onInstant(instant);
            case ON_CONTINGENCY_STATE -> UsageRuleSpec.onState(contingency, instant);
            case ON_CONSTRAINT -> UsageRuleSpec.onConstraint(cnecComboBox.getValue() == null ? null : cnecComboBox.getValue().getId(), instant);
            case ON_FLOW_CONSTRAINT_IN_COUNTRY -> UsageRuleSpec.onCountry(countryChoiceBox.getValue(),
                    contingency == null || contingency.equals(ANY_CONTINGENCY) ? null : contingency, instant);
        };
    }

    // OK's action: false keeps the dialog open
    boolean check() {
        try {
            UsageRuleEdits.check(crac, rule());
            errorLabel.setVisible(false);
            return true;
        } catch (IllegalArgumentException e) {
            errorLabel.setText(e.getMessage());
            errorLabel.setVisible(true);
            return false;
        }
    }
}
