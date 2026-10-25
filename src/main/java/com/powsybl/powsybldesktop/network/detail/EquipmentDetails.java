/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.detail;

import com.powsybl.iidm.network.Generator;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.powsybldesktop.SceneModel;
import com.powsybl.powsybldesktop.utils.Messages;
import com.powsybl.powsybldesktop.window.SeparateWindows;

/**
 * Opens an equipment's detail view, non-modal, as a tab of a separate window (see {@link SeparateWindows}): one per
 * equipment, brought to front if already open, closed with the other views bound to the network.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class EquipmentDetails {

    private static final double WIDTH = 1050;
    private static final double HEIGHT = 760;

    private EquipmentDetails() {
    }

    public static boolean supports(Identifiable<?> equipment) {
        return equipment instanceof Generator;
    }

    /**
     * A no-op where detail views can't be opened (tests), or for an equipment without detail view.
     */
    public static void open(SceneModel sceneModel, Identifiable<?> equipment) {
        if (!(equipment instanceof Generator generator)) {
            return;
        }
        sceneModel.getDetailWindows().ifPresent(windows -> windows.open("detail:" + generator.getId(),
                () -> Messages.get("detail.title", Messages.get("desktop.common.equipment.generator"), generator.getNameOrId()),
                scene -> {
                    SeparateWindows.View view = SeparateWindows.<GeneratorDetailController>load("network/detail/generator-detail-view.fxml", controller -> {
                        controller.setGenerator(generator);
                        controller.setSceneModel(scene);
                    });
                    return new SeparateWindows.View(view.root(), view.controller(), WIDTH, HEIGHT);
                }));
    }
}
