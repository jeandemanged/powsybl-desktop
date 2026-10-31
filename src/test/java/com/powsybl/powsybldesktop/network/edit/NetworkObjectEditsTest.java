/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.edit;

import com.powsybl.iidm.network.Area;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.OverloadManagementSystem;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DialogPane;
import org.controlsfx.control.CheckListView;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class NetworkObjectEditsTest extends AbstractCreationDialogTest {

    @Test
    void areaWithTheCheckedVoltageLevels() {
        Network network = EurostagTutorialExample1Factory.create();

        Area area = openAndConfirm(sceneModel -> NetworkObjectEdits.newArea(sceneModel, stage, network), network,
                dialogPane -> from(dialogPane).lookup((Node node) -> node instanceof CheckListView<?>).queryAs(CheckListView.class).getCheckModel().check(0));

        assertNotNull(area);
        assertEquals("ControlArea", area.getAreaType());
        assertEquals(1, area.getVoltageLevelStream().count());
    }

    @Test
    void overloadManagementSystemNeedsATripping() {
        Network network = EurostagTutorialExample1Factory.create();

        assertNull(openAndConfirm(sceneModel -> NetworkObjectEdits.newOverloadManagementSystem(sceneModel, stage, network.getSubstation("P2")), network));
        assertEquals(0, network.getOverloadManagementSystemCount());
        // still open
        interact(() -> ((Button) lookup(".dialog-pane").queryAs(DialogPane.class).lookupButton(ButtonType.CANCEL)).fire());

        OverloadManagementSystem system = openAndConfirm(sceneModel -> NetworkObjectEdits.newOverloadManagementSystem(sceneModel, stage,
                        network.getSubstation("P2")), network,
                dialogPane -> {
                    // P2's bus breaker voltage levels have no switch to trip
                    from(dialogPane).lookup(".combo-box").queryAllAs(ComboBox.class).stream()
                            .filter(comboBox -> comboBox.getValue() instanceof OverloadManagementSystem.Tripping.Type)
                            .findFirst().orElseThrow().setValue(OverloadManagementSystem.Tripping.Type.BRANCH_TRIPPING);
                    from(dialogPane).lookup(".button").queryAllAs(Button.class).stream()
                            .filter(button -> Messages.get("desktop.common.action.add").equals(button.getText()))
                            .findFirst().orElseThrow().fire();
                });

        assertNotNull(system);
        assertEquals(1, system.getTrippings().size());
        assertEquals(OverloadManagementSystem.Tripping.Type.BRANCH_TRIPPING, system.getTrippings().getFirst().getType());
    }
}
