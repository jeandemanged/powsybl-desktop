/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.ui.rao;

import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.Network;
import com.powsybl.openrao.data.crac.api.Crac;
import com.powsybl.openrao.data.crac.api.Instant;
import com.powsybl.openrao.data.crac.api.cnec.Cnec;
import com.powsybl.powsybldesktop.rao.CracEdits;
import com.powsybl.powsybldesktop.rao.RaoTestCases;
import com.powsybl.powsybldesktop.rao.UsageRuleEdits.Kind;
import com.powsybl.powsybldesktop.rao.UsageRuleEdits.UsageRuleSpec;
import com.powsybl.powsybldesktop.ui.testutil.AbstractHeadlessApplicationTest;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Add Usage Rule dialog, built, not shown: its controls are driven and its OK action called directly.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class UsageRuleDialogTest extends AbstractHeadlessApplicationTest {

    private final Network network = RaoTestCases.network();
    private final Crac crac = RaoTestCases.crac(network);
    private Stage stage;

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        stage.setScene(new Scene(new StackPane(), 200, 200));
        stage.show();
    }

    private UsageRuleDialog dialog() {
        return dialog(crac);
    }

    private UsageRuleDialog dialog(Crac crac) {
        AtomicReference<UsageRuleDialog> dialog = new AtomicReference<>();
        interact(() -> dialog.set(new UsageRuleDialog(stage, crac, network, state -> state.getId())));
        return dialog.get();
    }

    private static List<String> instants(UsageRuleDialog dialog) {
        return dialog.instantChoiceBox.getItems().stream().map(Instant::getId).toList();
    }

    @Test
    void asksWhatEachKindNeeds() {
        UsageRuleDialog dialog = dialog();
        // at an instant: any but the outage one
        assertEquals(List.of("preventive", "curative"), instants(dialog));
        assertFalse(dialog.contingencyComboBox.isVisible());
        assertEquals(UsageRuleSpec.onInstant("preventive"), dialog.rule());
        assertTrue(dialog.check());

        // after a contingency: not at the preventive instant
        interact(() -> dialog.kindChoiceBox.setValue(Kind.ON_CONTINGENCY_STATE));
        assertEquals(List.of("curative"), instants(dialog));
        assertTrue(dialog.contingencyComboBox.isVisible());
        assertFalse(dialog.cnecComboBox.isVisible());
        assertEquals(UsageRuleSpec.onState(RaoTestCases.CONTINGENCY, "curative"), dialog.rule());

        // on a constraint: the CNECs at the instant or after it
        interact(() -> dialog.kindChoiceBox.setValue(Kind.ON_CONSTRAINT));
        assertTrue(dialog.cnecComboBox.isVisible());
        assertEquals(List.of("L1 curative"), dialog.cnecComboBox.getItems().stream().map(Cnec::getId).toList());
        interact(() -> dialog.instantChoiceBox.setValue(crac.getInstant("preventive")));
        assertEquals(List.of("L1 preventive", "L1 curative"), dialog.cnecComboBox.getItems().stream().map(Cnec::getId).toList());
        // the CNEC selected kept
        assertEquals(UsageRuleSpec.onConstraint("L1 curative", "preventive"), dialog.rule());

        // on a flow constraint in a country: after any contingency, or one
        interact(() -> dialog.kindChoiceBox.setValue(Kind.ON_FLOW_CONSTRAINT_IN_COUNTRY));
        assertTrue(dialog.countryChoiceBox.isVisible());
        assertEquals(List.of(Country.FR), dialog.countryChoiceBox.getItems());
        assertEquals(List.of(UsageRuleDialog.ANY_CONTINGENCY, RaoTestCases.CONTINGENCY), dialog.contingencyComboBox.getItems());
        // the contingency selected kept
        assertEquals(UsageRuleSpec.onCountry(Country.FR, RaoTestCases.CONTINGENCY, "preventive"), dialog.rule());
        interact(() -> dialog.contingencyComboBox.setValue(UsageRuleDialog.ANY_CONTINGENCY));
        assertEquals(UsageRuleSpec.onCountry(Country.FR, null, "preventive"), dialog.rule());
        assertTrue(dialog.check());
    }

    @Test
    void staysOpenOnARefusedRule() {
        // no contingency to be after
        UsageRuleDialog dialog = dialog(CracEdits.newCrac("new", null, List.of("curative")));
        interact(() -> dialog.kindChoiceBox.setValue(Kind.ON_CONTINGENCY_STATE));
        assertFalse(dialog.check());
        assertTrue(dialog.errorLabel.isVisible());
    }
}
