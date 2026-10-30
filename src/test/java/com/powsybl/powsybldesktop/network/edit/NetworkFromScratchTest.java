/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.edit;

import com.powsybl.iidm.network.Generator;
import com.powsybl.iidm.network.Line;
import com.powsybl.iidm.network.Load;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Substation;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.iidm.serde.NetworkSerDe;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.powsybldesktop.network.SubstationDiagramRenderer;
import com.powsybl.sld.SldParameters;
import javafx.scene.control.DialogPane;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A network built from scratch with the creation dialogs, as a user would: two substations with a node breaker
 * voltage level each, a generator, a load and a line between them. It must be valid enough to be solved, drawn and
 * exported.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class NetworkFromScratchTest extends AbstractCreationDialogTest {

    private static void setText(DialogPane dialogPane, int index, String text) {
        ((TextField) dialogPane.lookupAll(".text-field").stream().toList().get(index)).setText(text);
    }

    @Test
    void builtNetworkIsSolvedDrawnAndExported() throws Exception {
        Network network = Network.create("scratch", ContainerCreations.SOURCE_FORMAT);

        Substation s1 = openAndConfirm(sceneModel -> ContainerCreations.newSubstation(sceneModel, stage, network, null, null), network);
        Substation s2 = openAndConfirm(sceneModel -> ContainerCreations.newSubstation(sceneModel, stage, network, null, null), network);
        VoltageLevel vl1 = openAndConfirm(sceneModel -> ContainerCreations.newVoltageLevel(sceneModel, stage, network, s1), network);
        VoltageLevel vl2 = openAndConfirm(sceneModel -> ContainerCreations.newVoltageLevel(sceneModel, stage, network, s2), network);
        // id, name, min P, max P, target P
        Generator generator = (Generator) openAndConfirm(sceneModel -> InjectionCreations.create(sceneModel, stage, InjectionCreations.Kind.GENERATOR,
                vl1, null), network, dialogPane -> setText(dialogPane, 4, "60"));
        // id, name, P0
        Load load = (Load) openAndConfirm(sceneModel -> InjectionCreations.create(sceneModel, stage, InjectionCreations.Kind.LOAD,
                vl2, null), network, dialogPane -> setText(dialogPane, 2, "50"));
        Line line = openAndConfirm(sceneModel -> BranchCreations.newLine(sceneModel, stage, network, vl1, null), network);

        assertEquals(60, generator.getTargetP());
        assertEquals(50, load.getP0());
        assertEquals("S2_VL1", line.getTerminal2().getVoltageLevel().getId());

        LoadFlowResult result = LoadFlow.run(network);
        assertTrue(result.isFullyConverged());
        assertEquals(-50, line.getTerminal2().getP(), 1e-3);

        SubstationDiagramRenderer.DiagramRender render = SubstationDiagramRenderer.render(vl1, new SldParameters());
        assertTrue(render.svg().contains(generator.getId()));

        ByteArrayOutputStream exported = new ByteArrayOutputStream();
        NetworkSerDe.write(network, exported);
        Network reimported = NetworkSerDe.read(new ByteArrayInputStream(exported.toByteArray()));
        assertNotNull(reimported.getLine(line.getId()));
        assertEquals(network.getSwitchCount(), reimported.getSwitchCount());
    }
}
