/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.ui.rao;

import com.powsybl.iidm.network.TwoSides;
import com.powsybl.openrao.data.crac.api.Crac;
import com.powsybl.openrao.data.crac.api.Instant;
import com.powsybl.openrao.data.crac.api.InstantKind;
import com.powsybl.powsybldesktop.rao.CnecEdits;
import com.powsybl.powsybldesktop.rao.CnecEdits.StateRef;
import com.powsybl.powsybldesktop.rao.CracEdits;
import com.powsybl.powsybldesktop.rao.RaoTestCases;
import com.powsybl.powsybldesktop.ui.testutil.AbstractHeadlessApplicationTest;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ButtonType;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The New CRAC and Add Instant dialogs, built, not shown: their fields are driven and their OK action called directly.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class NewCracDialogTest extends AbstractHeadlessApplicationTest {

    private Stage stage;

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        stage.setScene(new Scene(new StackPane(), 200, 200));
        stage.show();
    }

    private static List<String> instants(Crac crac) {
        return crac.getSortedInstants().stream().map(Instant::getId).toList();
    }

    @Test
    void createsACracWithItsInstants() {
        AtomicReference<NewCracDialog> dialog = new AtomicReference<>();
        interact(() -> dialog.set(new NewCracDialog(stage, "network", false)));
        NewCracDialog newCrac = dialog.get();
        assertEquals("network", newCrac.idField.getText());

        AtomicBoolean created = new AtomicBoolean();
        interact(() -> {
            newCrac.curativeField.setText("curative, curative");
            created.set(newCrac.create());
        });
        assertFalse(created.get(), "two instants of the same id");
        assertTrue(newCrac.errorLabel.isVisible());
        assertNull(newCrac.getResultConverter().call(ButtonType.OK));

        interact(() -> {
            newCrac.autoField.setText(" auto ");
            newCrac.curativeField.setText("curative 1, ,curative 2");
            created.set(newCrac.create());
        });
        assertTrue(created.get());
        assertFalse(newCrac.errorLabel.isVisible());
        Crac crac = newCrac.getResultConverter().call(ButtonType.OK);
        assertSame(newCrac.created(), crac);
        assertEquals(List.of("preventive", "outage", "auto", "curative 1", "curative 2"), instants(crac));
        assertNull(newCrac.getResultConverter().call(ButtonType.CANCEL));
    }

    @Test
    void addsAnInstantOfAnAddableKind() {
        Crac crac = CracEdits.newCrac("c", null, List.of("curative"));
        AtomicReference<NewInstantDialog> dialog = new AtomicReference<>();
        interact(() -> dialog.set(new NewInstantDialog(stage, crac)));
        NewInstantDialog newInstant = dialog.get();
        // no auto instant after a curative one
        assertEquals(List.of(InstantKind.CURATIVE), newInstant.kindChoiceBox.getItems());
        assertEquals(Messages.get("crac.instant.kind.curative"), newInstant.kindChoiceBox.getConverter().toString(InstantKind.CURATIVE));

        AtomicBoolean added = new AtomicBoolean();
        interact(() -> {
            newInstant.idField.setText("curative");
            added.set(newInstant.add());
        });
        assertFalse(added.get(), "an existing id");
        assertTrue(newInstant.errorLabel.isVisible());

        interact(() -> {
            newInstant.idField.setText("curative 2");
            added.set(newInstant.add());
        });
        assertTrue(added.get());
        assertEquals(List.of("preventive", "outage", "curative", "curative 2"), instants(crac));
    }

    @Test
    void asksTheStatesAndSidesOfFlowCnecs() {
        Crac crac = RaoTestCases.crac(RaoTestCases.network());
        List<StateRef> states = CnecEdits.states(crac);
        AtomicReference<AddFlowCnecsDialog> dialog = new AtomicReference<>();
        interact(() -> dialog.set(new AddFlowCnecsDialog(stage, 2, states, StateRef::instantId, Set.of(TwoSides.ONE))));
        AddFlowCnecsDialog addFlowCnecs = dialog.get();
        Node ok = addFlowCnecs.getDialogPane().lookupButton(ButtonType.OK);
        assertEquals(new AddFlowCnecsDialog.Request(states, Set.of(TwoSides.ONE)), addFlowCnecs.request());
        assertFalse(ok.isDisabled());

        interact(() -> addFlowCnecs.side1CheckBox.setSelected(false));
        assertTrue(ok.isDisabled(), "no side");
        interact(() -> {
            addFlowCnecs.side2CheckBox.setSelected(true);
            addFlowCnecs.checkedStates.values().forEach(checked -> checked.set(false));
        });
        assertTrue(ok.isDisabled(), "no state");
        interact(() -> addFlowCnecs.checkedStates.get(states.getLast()).set(true));
        assertFalse(ok.isDisabled());
        assertEquals(new AddFlowCnecsDialog.Request(List.of(states.getLast()), Set.of(TwoSides.TWO)), addFlowCnecs.getResultConverter().call(ButtonType.OK));
    }
}
