/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network;

import com.powsybl.powsybldesktop.MainModel;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.stage.Modality;
import javafx.stage.Window;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Modal popup editing {@link MainModel#sldParametersProperty()} in place - {@code WINDOW_MODAL} only
 * blocks input to the owner window, it doesn't hide it, so the substations view (and its single-line
 * diagram) stays visible and refreshes live as fields are edited, via the {@code onChange} callback.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
final class SldParametersDialog {

    private SldParametersDialog() {
    }

    static void show(Window owner, MainModel mainModel, Runnable onChange) {
        FXMLLoader loader = new FXMLLoader(SldParametersDialog.class.getResource("sld-parameters.fxml"), Messages.bundle());
        Parent content;
        try {
            content = loader.load();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        SldParametersController controller = loader.getController();
        controller.setParametersProperty(mainModel.sldParametersProperty());
        controller.setOnChange(onChange);

        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.setResizable(true);
        dialog.setTitle(Messages.get("substations.sldParameters.dialogTitle"));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        dialog.showAndWait();
        controller.dispose();
    }
}
