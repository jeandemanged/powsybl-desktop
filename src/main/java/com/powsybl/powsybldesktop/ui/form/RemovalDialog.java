/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.ui.form;

import com.powsybl.iidm.network.Identifiable;
import com.powsybl.powsybldesktop.core.MainModel;
import com.powsybl.powsybldesktop.network.edit.Kinds;
import com.powsybl.powsybldesktop.network.edit.Removals;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Removes a network object through {@link Removals} after a confirmation listing what goes with it.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class RemovalDialog {

    private static final int MAX_LISTED = 15;

    private RemovalDialog() {
    }

    /**
     * Asks for confirmation, then removes {@code identifiable}; a failure is reported as an error notification.
     */
    public static void confirmThenRemove(MainModel mainModel, Window owner, Identifiable<?> identifiable) {
        if (confirm(owner, Kinds.describe(identifiable), Removals.dependents(identifiable))) {
            Removals.remove(mainModel, identifiable);
        }
    }

    private static boolean confirm(Window owner, String description, List<String> dependents) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.initOwner(owner);
        alert.setTitle(Messages.get("desktop.common.action.remove"));
        alert.setHeaderText(Messages.get("edit.remove.confirm", description));
        if (!dependents.isEmpty()) {
            List<String> listed = dependents.stream().limit(MAX_LISTED).map(dependent -> "- " + dependent).collect(Collectors.toCollection(ArrayList::new));
            if (dependents.size() > MAX_LISTED) {
                listed.add(Messages.get("edit.remove.more", dependents.size() - MAX_LISTED));
            }
            alert.setContentText(Messages.get("edit.remove.alsoRemoved") + "\n" + String.join("\n", listed));
        }
        return alert.showAndWait().filter(ButtonType.OK::equals).isPresent();
    }
}
