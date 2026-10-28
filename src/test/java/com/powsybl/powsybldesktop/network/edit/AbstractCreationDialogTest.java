/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.edit;

import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Substation;
import com.powsybl.iidm.network.SwitchKind;
import com.powsybl.iidm.network.TopologyKind;
import com.powsybl.powsybldesktop.MainModel;
import com.powsybl.powsybldesktop.SceneModel;
import com.powsybl.powsybldesktop.testutil.AbstractHeadlessApplicationTest;
import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.AfterEach;
import org.testfx.util.WaitForAsyncUtils;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Opens a creation dialog over an empty stage, and confirms it as the user would.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
abstract class AbstractCreationDialogTest extends AbstractHeadlessApplicationTest {

    protected Stage stage;
    // the primary stage's, shared by every test class of the JVM: restored for those that follow
    private double originalX;
    private double originalY;

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        originalX = stage.getX();
        originalY = stage.getY();
        stage.setScene(new Scene(new StackPane(), 200, 200));
        stage.show();
        // dialogs are centered on it: they must fit the headless screen (blitting outside fails)
        Rectangle2D screen = Screen.getPrimary().getBounds();
        stage.setX((screen.getWidth() - stage.getWidth()) / 2);
        stage.setY((screen.getHeight() - stage.getHeight()) / 2);
    }

    // e.g. an edit error's popup, which would outlive the test, and the moved primary stage
    @AfterEach
    void closeOtherWindows() {
        interact(() -> {
            List.copyOf(Window.getWindows()).stream().filter(window -> window != stage).forEach(Window::hide);
            stage.setX(originalX);
            stage.setY(originalY);
        });
    }

    // two node breaker voltage levels of 2 parallel busbars in one substation, and a third, bus breaker one
    protected static Network network() {
        Network network = Network.create("test", ContainerCreations.SOURCE_FORMAT);
        Substation substation = network.newSubstation().setId("S").add();
        for (String id : new String[] {"VL400", "VL225"}) {
            substation.newVoltageLevel().setId(id).setNominalV("VL400".equals(id) ? 400 : 225).setTopologyKind(TopologyKind.NODE_BREAKER).add();
            ContainerCreations.createTopology(network, id, TopologyKind.NODE_BREAKER, 2, 1, SwitchKind.BREAKER, true);
        }
        substation.newVoltageLevel().setId("VL63").setNominalV(63).setTopologyKind(TopologyKind.BUS_BREAKER).add();
        ContainerCreations.createTopology(network, "VL63", TopologyKind.BUS_BREAKER, 1, 1, SwitchKind.BREAKER, false);
        return network;
    }

    protected static SceneModel sceneModel(Network network) {
        MainModel mainModel = new MainModel();
        mainModel.addNetwork(network);
        return SceneModel.main(mainModel);
    }

    /**
     * Opens the dialog, lets {@code fill} change its fields, confirms it, and returns what it created - null if it
     * stayed open.
     */
    protected <T> T openAndConfirm(Function<SceneModel, Optional<T>> open, Network network, Consumer<DialogPane> fill) {
        SceneModel sceneModel = sceneModel(network);
        AtomicReference<Optional<T>> created = new AtomicReference<>();
        Platform.runLater(() -> created.set(open.apply(sceneModel)));
        WaitForAsyncUtils.waitForFxEvents();
        DialogPane dialogPane = lookup(".dialog-pane").queryAs(DialogPane.class);
        interact(() -> fill.accept(dialogPane));
        // fired rather than clicked: an error popup may cover the button
        interact(() -> ((Button) dialogPane.lookupButton(ButtonType.OK)).fire());
        WaitForAsyncUtils.waitForFxEvents();
        return created.get() == null ? null : created.get().orElse(null);
    }

    protected <T> T openAndConfirm(Function<SceneModel, Optional<T>> open, Network network) {
        return openAndConfirm(open, network, dialogPane -> { });
    }
}
