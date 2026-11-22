/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.ui.form;

import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.powsybldesktop.network.ElementCandidate;
import com.powsybl.powsybldesktop.ui.testutil.AbstractHeadlessApplicationTest;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class ElementPickerDialogTest extends AbstractHeadlessApplicationTest {

    private final Network network = EurostagTutorialExample1Factory.create();

    @Override
    public void start(Stage stage) {
        // the dialog has its own window
    }

    @Test
    void offersTheCandidatesNotAlreadyPickedAndFiltersThem() {
        List<ElementCandidate> candidates = ElementCandidate.of(network.getLineStream());
        ElementPickerDialog[] dialog = new ElementPickerDialog[1];
        interact(() -> dialog[0] = new ElementPickerDialog(null, "t", candidates, Set.of("NHV1_NHV2_1")));
        assertEquals(candidates.size() - 1, dialog[0].listView.getItems().size());
        interact(() -> {
            dialog[0].searchField.setText("nhv1_nhv2");
            dialog[0].selectShownButton.fire();
        });
        assertEquals(List.of("NHV1_NHV2_2"), dialog[0].listView.getItems().stream().map(ElementCandidate::id).toList());
        assertEquals(List.of("NHV1_NHV2_2"), dialog[0].getResultConverter().call(ButtonType.OK));
        assertEquals(List.of(), dialog[0].getResultConverter().call(ButtonType.CANCEL));
    }

    @Test
    void labelsByNameIdAndKind() {
        assertEquals("NHV1_NHV2_1 — Line", ElementPickerDialog.label(new ElementCandidate("NHV1_NHV2_1", "NHV1_NHV2_1", "Line")));
        assertEquals("North (N1) — Line", ElementPickerDialog.label(new ElementCandidate("N1", "North", "Line")));
    }
}
