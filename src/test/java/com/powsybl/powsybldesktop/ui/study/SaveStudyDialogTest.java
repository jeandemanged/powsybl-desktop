/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.ui.study;

import com.powsybl.ieeecdf.converter.IeeeCdfNetworkFactory;
import com.powsybl.iidm.network.Network;
import com.powsybl.powsybldesktop.study.Study;
import com.powsybl.powsybldesktop.ui.testutil.AbstractHeadlessApplicationTest;
import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class SaveStudyDialogTest extends AbstractHeadlessApplicationTest {

    private Stage stage;
    // the primary stage's, shared by every test class of the JVM: restored for those that follow
    private double originalX;
    private double originalY;
    private final Network ieee14 = IeeeCdfNetworkFactory.create14();
    private final Network ieee9 = IeeeCdfNetworkFactory.create9();
    private final AtomicReference<Optional<SaveStudyDialog.Choice>> choice = new AtomicReference<>();
    private DialogPane dialogPane;

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        originalX = stage.getX();
        originalY = stage.getY();
        stage.setScene(new Scene(new StackPane(), 200, 200));
        stage.show();
        // the dialog is centered on it: it must fit the headless screen (blitting outside fails)
        Rectangle2D screen = Screen.getPrimary().getBounds();
        stage.setX((screen.getWidth() - stage.getWidth()) / 2);
        stage.setY((screen.getHeight() - stage.getHeight()) / 2);
    }

    @AfterEach
    void closeOtherWindows() {
        interact(() -> {
            List.copyOf(Window.getWindows()).stream().filter(window -> window != stage).forEach(Window::hide);
            stage.setX(originalX);
            stage.setY(originalY);
        });
    }

    private void open() {
        Platform.runLater(() -> choice.set(SaveStudyDialog.show(stage, List.of(ieee14, ieee9))));
        WaitForAsyncUtils.waitForFxEvents();
        dialogPane = lookup(".dialog-pane").queryAs(DialogPane.class);
    }

    // the data group's buttons come first, then the networks group's
    private Button button(String text, int group) {
        HBox header = ((VBox) dialogPane.getContent()).getChildren().stream()
                .filter(HBox.class::isInstance).map(HBox.class::cast).toList().get(group);
        return header.getChildren().stream()
                .filter(node -> node instanceof Button button && text.equals(button.getText()))
                .map(Button.class::cast).findFirst().orElseThrow();
    }

    private Node saveButton() {
        return dialogPane.getButtonTypes().stream()
                .filter(type -> type.getButtonData() == ButtonBar.ButtonData.OK_DONE)
                .map(dialogPane::lookupButton)
                .findFirst().orElseThrow();
    }

    @Test
    void savesEverythingByDefault() {
        open();
        interact(() -> ((Button) saveButton()).fire());
        WaitForAsyncUtils.waitForFxEvents();

        SaveStudyDialog.Choice saved = choice.get().orElseThrow();
        assertEquals(List.of(ieee14, ieee9), saved.networks());
        assertEquals(EnumSet.allOf(Study.Part.class), saved.parts());
    }

    @Test
    void noneAndAllButtonsSelectEachGroup() {
        open();
        interact(() -> button("None", 0).fire());
        interact(() -> button("None", 1).fire());
        assertTrue(saveButton().isDisabled());

        interact(() -> button("All", 1).fire());
        assertFalse(saveButton().isDisabled());
        interact(() -> ((Button) saveButton()).fire());
        WaitForAsyncUtils.waitForFxEvents();

        SaveStudyDialog.Choice saved = choice.get().orElseThrow();
        assertEquals(List.of(ieee14, ieee9), saved.networks());
        assertTrue(saved.parts().isEmpty());
    }

    @Test
    void cancelSavesNothing() {
        open();
        interact(() -> ((Button) dialogPane.lookupButton(ButtonType.CANCEL)).fire());
        WaitForAsyncUtils.waitForFxEvents();

        assertTrue(choice.get().isEmpty());
    }
}
