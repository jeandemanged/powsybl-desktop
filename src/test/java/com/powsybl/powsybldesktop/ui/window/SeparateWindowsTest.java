/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.ui.window;

import com.powsybl.ieeecdf.converter.IeeeCdfNetworkFactory;
import com.powsybl.iidm.network.Network;
import com.powsybl.powsybldesktop.core.MainModel;
import com.powsybl.powsybldesktop.ui.testutil.AbstractHeadlessApplicationTest;
import com.powsybl.powsybldesktop.ui.testutil.SubnetworksTestFactory;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.scene.Scene;
import javafx.scene.control.SplitMenuButton;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class SeparateWindowsTest extends AbstractHeadlessApplicationTest {

    private final Network network = IeeeCdfNetworkFactory.create14();
    private final Network otherNetwork = IeeeCdfNetworkFactory.create9();

    private MainModel mainModel;
    private SeparateWindows separateWindows;

    @Override
    public void start(Stage stage) {
        mainModel = new MainModel();
        mainModel.addNetwork(network);
        mainModel.addNetwork(otherNetwork);
        mainModel.setNetwork(network);
        separateWindows = new SeparateWindows(mainModel, () -> stage);
        stage.setScene(new Scene(new StackPane(), 200, 100));
        stage.show();
    }

    @AfterEach
    void tearDown() {
        interact(separateWindows::closeAll);
    }

    private static List<Stage> windows(String title) {
        return Window.getWindows().stream()
                .filter(window -> window instanceof Stage stage && stage.getTitle() != null && stage.getTitle().endsWith(title))
                .map(Stage.class::cast)
                .toList();
    }

    private void openLines() {
        interact(() -> openPlain("Lines", true, new AtomicInteger()));
    }

    @Test
    void networkBoundWindowClosesWhenTheSelectionChanges() {
        openLines();
        assertEquals(1, windows("Lines").size());

        interact(() -> mainModel.setNetwork(otherNetwork));

        assertTrue(windows("Lines").isEmpty());
    }

    @Test
    void networkBoundWindowClosesWhenItsNetworkIsClosed() {
        openLines();

        interact(() -> mainModel.removeNetwork(network));

        assertTrue(windows("Lines").isEmpty());
    }

    // navigation crosses subnetworks, e.g. to a tie line held by the root network
    @Test
    void subnetworkBoundWindowStaysOpenWhileTheSelectionStaysInItsRootNetwork() {
        Network root = SubnetworksTestFactory.create();
        Network nodeBreaker = root.getSubnetwork(SubnetworksTestFactory.NODE_BREAKER);
        Network busBreaker = root.getSubnetwork(SubnetworksTestFactory.BUS_BREAKER);
        interact(() -> {
            mainModel.addNetwork(root);
            mainModel.setNetwork(nodeBreaker);
        });
        openLines();

        assertTrue(separateWindows.confirmNetworkChange(root));
        assertTrue(separateWindows.confirmNetworkChange(busBreaker));
        interact(() -> mainModel.setNetwork(root));
        interact(() -> mainModel.setNetwork(busBreaker));
        assertEquals(1, windows("Lines").size());

        interact(() -> mainModel.setNetwork(otherNetwork));
        assertTrue(windows("Lines").isEmpty());
    }

    @Test
    void noConfirmationIsNeededWithoutWindowsOnAnotherNetwork() {
        assertTrue(separateWindows.confirmNetworkChange(otherNetwork));
        openLines();
        assertTrue(separateWindows.confirmNetworkChange(network));
    }

    @Test
    void singleInstanceWindowIsReusedAndSurvivesNetworkChanges() {
        AtomicInteger disposed = new AtomicInteger();
        interact(() -> openPlain("Logs", false, disposed));
        interact(() -> openPlain("Logs", false, disposed));
        assertEquals(1, windows("Logs").size());

        interact(() -> mainModel.setNetwork(otherNetwork));

        assertEquals(1, windows("Logs").size());
    }

    @Test
    void wholeNetworkViewIsOpenedOnceAndListed() {
        openLines();
        openLines();

        assertEquals(1, windows("Lines").size());
        assertEquals(List.of(List.of("Lines")), separateWindows.getOpenWindows().stream().map(SeparateWindows.DetachedWindow::getTabTitles).toList());

        interact(() -> windows("Lines").getFirst().close());

        assertTrue(separateWindows.getOpenWindows().isEmpty());
    }

    @Test
    void windowsCanBeFocusedOrClosedByKey() throws Exception {
        assertFalse(WaitForAsyncUtils.asyncFx(() -> separateWindows.focusOpen("Lines")).get());
        openLines();

        assertTrue(WaitForAsyncUtils.asyncFx(() -> separateWindows.focusOpen("Lines")).get());

        interact(() -> separateWindows.close("Lines"));

        assertTrue(windows("Lines").isEmpty());
    }

    @Test
    void closeAllClosesEveryWindow() {
        openLines();

        interact(separateWindows::closeAll);

        assertTrue(windows("Lines").isEmpty());
    }

    // a view whose controller counts its disposals in disposed
    private void openPlain(String key, boolean bound, AtomicInteger disposed) {
        Function<SceneModel, SeparateWindows.View> factory = _ -> new SeparateWindows.View(new StackPane(), disposed::incrementAndGet);
        if (bound) {
            separateWindows.open(key, () -> key, factory);
        } else {
            separateWindows.openSingleInstance(key, () -> key, factory);
        }
    }

    private void openPlainIn(SeparateWindows.DetachedWindow window, String key, boolean bound, AtomicInteger disposed) {
        ToolBar toolBar = new ToolBar();
        separateWindows.addOpenButton(toolBar, () -> openPlain(key, bound, disposed));
        SplitMenuButton button = (SplitMenuButton) toolBar.getItems().getLast();
        button.getOnShowing().handle(null);
        button.getItems().stream().filter(item -> item.getText().equals(window.getName())).findFirst().orElseThrow().fire();
    }

    private static String defaultName(int n) {
        return Messages.get("separateWindows.defaultName", n);
    }

    private List<String> names() {
        return separateWindows.getOpenWindows().stream().map(SeparateWindows.DetachedWindow::getName).toList();
    }

    @Test
    void windowsGetTheLowestFreeDefaultName() {
        AtomicInteger disposed = new AtomicInteger();
        interact(() -> {
            openPlain("a", false, disposed);
            openPlain("b", false, disposed);
        });
        assertEquals(List.of(defaultName(1), defaultName(2)), names());

        interact(() -> separateWindows.close("a"));
        interact(() -> openPlain("c", false, disposed));

        assertEquals(List.of(defaultName(2), defaultName(1)), names());
    }

    @Test
    void renamingRejectsBlankAndTakenNames() throws Exception {
        AtomicInteger disposed = new AtomicInteger();
        interact(() -> {
            openPlain("a", false, disposed);
            openPlain("b", false, disposed);
        });
        SeparateWindows.DetachedWindow first = separateWindows.getOpenWindows().getFirst();

        assertFalse(WaitForAsyncUtils.asyncFx(() -> first.rename("  ")).get());
        assertFalse(WaitForAsyncUtils.asyncFx(() -> first.rename(defaultName(2))).get());
        assertTrue(WaitForAsyncUtils.asyncFx(() -> first.rename(" Diagrams ")).get());

        assertEquals(List.of("Diagrams", defaultName(2)), names());
        assertEquals(1, windows("Diagrams - a").size());
    }

    @Test
    void openInLatestWindowAddsATabToTheLatestWindowCreatedOrANewOne() {
        AtomicInteger disposed = new AtomicInteger();
        Function<SceneModel, SeparateWindows.View> factory = _ -> new SeparateWindows.View(new StackPane(), disposed::incrementAndGet);
        interact(() -> separateWindows.openInLatestWindow("detail:a", () -> "a", factory));
        assertEquals(List.of(List.of("a")), separateWindows.getOpenWindows().stream().map(SeparateWindows.DetachedWindow::getTabTitles).toList());

        interact(() -> {
            openPlain("other", true, disposed);
            separateWindows.openInLatestWindow("detail:b", () -> "b", factory);
        });
        assertEquals(List.of(List.of("a"), List.of("other", "b")),
                separateWindows.getOpenWindows().stream().map(SeparateWindows.DetachedWindow::getTabTitles).toList());

        interact(() -> {
            List.of("detail:a", "other", "detail:b").forEach(separateWindows::close);
            separateWindows.openInLatestWindow("detail:c", () -> "c", factory);
        });
        assertEquals(List.of(defaultName(1)), names());
    }

    @Test
    void viewsCanBeOpenedAsTabsOfAnExistingWindowAndFocusedByKey() throws Exception {
        AtomicInteger disposed = new AtomicInteger();
        interact(() -> openPlain("a", false, disposed));
        SeparateWindows.DetachedWindow window = separateWindows.getOpenWindows().getFirst();

        interact(() -> openPlainIn(window, "b", false, disposed));

        assertEquals(1, separateWindows.getOpenWindows().size());
        assertEquals(List.of("a", "b"), window.getTabTitles());
        assertEquals(1, windows(defaultName(1) + " - b").size());

        assertTrue(WaitForAsyncUtils.asyncFx(() -> separateWindows.focusOpen("a")).get());
        assertEquals(1, windows(defaultName(1) + " - a").size());
    }

    @Test
    void movingTheLastTabClosesItsWindowWithoutDisposingTheView() {
        AtomicInteger disposed = new AtomicInteger();
        interact(() -> {
            openPlain("a", false, disposed);
            openPlain("b", false, disposed);
        });
        SeparateWindows.DetachedWindow first = separateWindows.getOpenWindows().getFirst();
        SeparateWindows.DetachedWindow second = separateWindows.getOpenWindows().getLast();

        interact(() -> separateWindows.move("b", first, 0));

        assertEquals(List.of(first), separateWindows.getOpenWindows());
        assertEquals(List.of("b", "a"), first.getTabTitles());
        assertEquals(0, disposed.get());
        assertFalse(second.getTabTitles().contains("b"));

        interact(() -> separateWindows.move("a", null, 0));

        assertEquals(2, separateWindows.getOpenWindows().size());
        assertEquals(List.of("a"), separateWindows.getOpenWindows().getLast().getTabTitles());
        assertEquals(0, disposed.get());
    }

    @Test
    void droppedOverAWindowOpensANewTabOfIt() {
        AtomicInteger disposed = new AtomicInteger();
        interact(() -> openPlain("a", false, disposed));
        Stage stage = windows("a").getFirst();

        interact(() -> separateWindows.dropToOpen("b", () -> openPlain("b", false, disposed), stage.getX() + stage.getWidth() - 10, stage.getY() + 50));

        assertEquals(1, separateWindows.getOpenWindows().size());
        assertEquals(List.of("a", "b"), separateWindows.getOpenWindows().getFirst().getTabTitles());
    }

    @Test
    void droppedOutsideAnyWindowOpensANewOneThere() {
        AtomicInteger disposed = new AtomicInteger();
        interact(() -> openPlain("a", false, disposed));
        Stage stage = windows("a").getFirst();
        double x = stage.getX() + stage.getWidth() + 10;

        interact(() -> separateWindows.dropToOpen("b", () -> openPlain("b", false, disposed), x, 20));

        assertEquals(2, separateWindows.getOpenWindows().size());
        assertEquals(x, windows("b").getFirst().getX());
    }

    // the view already open moves, as its tab would
    @Test
    void droppedWhileOpenMovesTheView() {
        AtomicInteger disposed = new AtomicInteger();
        interact(() -> {
            openPlain("a", false, disposed);
            openPlain("b", false, disposed);
        });
        Stage first = windows("a").getFirst();
        first.setX(0);
        first.setY(0);
        Stage second = windows("b").getFirst();
        interact(() -> {
            second.setX(first.getWidth() + 10);
            second.setY(0);
        });

        interact(() -> separateWindows.dropToOpen("b", () -> fail("already open"), first.getWidth() - 10, 50));

        assertEquals(1, separateWindows.getOpenWindows().size());
        assertEquals(List.of("a", "b"), separateWindows.getOpenWindows().getFirst().getTabTitles());
        assertEquals(0, disposed.get());
    }

    @Test
    void networkChangeClosesOnlyBoundTabs() {
        AtomicInteger disposed = new AtomicInteger();
        interact(() -> openPlain("logs", false, disposed));
        SeparateWindows.DetachedWindow window = separateWindows.getOpenWindows().getFirst();
        interact(() -> openPlainIn(window, "lines", true, disposed));
        interact(() -> openPlain("map", true, disposed));
        assertEquals(2, separateWindows.getOpenWindows().size());

        interact(() -> mainModel.setNetwork(otherNetwork));

        assertEquals(List.of(window), separateWindows.getOpenWindows());
        assertEquals(List.of("logs"), window.getTabTitles());
        assertEquals(2, disposed.get());
    }

    @Test
    void viewFollowingTheSelectionStaysOpenOnItsNetworkAndIsRetitled() {
        AtomicInteger disposed = new AtomicInteger();
        AtomicReference<SceneModel> scene = new AtomicReference<>();
        interact(() -> separateWindows.openFollowingSelection("map", () -> "map " + mainModel.getNetwork().getId(), sceneModel -> {
            scene.set(sceneModel);
            return new SeparateWindows.View(new StackPane(), disposed::incrementAndGet);
        }));
        assertTrue(separateWindows.confirmNetworkChange(otherNetwork));

        interact(() -> mainModel.setNetwork(otherNetwork));

        assertEquals(List.of("map " + otherNetwork.getId()), separateWindows.getOpenWindows().getFirst().getTabTitles());
        assertSame(otherNetwork, scene.get().getNetwork());
        assertEquals(0, disposed.get());
    }

    @Test
    void closeAllDisposesEveryView() {
        AtomicInteger disposed = new AtomicInteger();
        interact(() -> openPlain("a", false, disposed));
        SeparateWindows.DetachedWindow window = separateWindows.getOpenWindows().getFirst();
        interact(() -> {
            openPlainIn(window, "b", false, disposed);
            openPlain("c", true, disposed);
        });

        interact(separateWindows::closeAll);

        assertTrue(separateWindows.getOpenWindows().isEmpty());
        assertEquals(3, disposed.get());
    }

    @Test
    void relocalizeRebuildsTheViewsAndTranslatesTheirTitlesAndDefaultNames() {
        AtomicInteger built = new AtomicInteger();
        AtomicInteger disposed = new AtomicInteger();
        Function<SceneModel, SeparateWindows.View> factory = _ -> {
            built.incrementAndGet();
            return new SeparateWindows.View(new StackPane(), disposed::incrementAndGet);
        };
        interact(() -> {
            separateWindows.open("a", () -> Messages.get("separateWindows.newWindow"), factory);
            separateWindows.openSingleInstance("b", () -> "b", factory);
        });
        SeparateWindows.DetachedWindow first = separateWindows.getOpenWindows().getFirst();
        SeparateWindows.DetachedWindow second = separateWindows.getOpenWindows().getLast();
        interact(() -> second.rename("Mine"));
        String oldName = defaultName(1);

        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault("fr".equals(previous.getLanguage()) ? Locale.ENGLISH : Locale.FRENCH);
            interact(separateWindows::relocalize);

            assertNotEquals(oldName, defaultName(1));
            assertEquals(List.of(defaultName(1), "Mine"), names());
            assertEquals(List.of(Messages.get("separateWindows.newWindow")), first.getTabTitles());
            assertEquals(1, windows(defaultName(1) + " - " + Messages.get("separateWindows.newWindow")).size());
            assertEquals(4, built.get());
            assertEquals(2, disposed.get());
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void openButtonGoesInTheViewsTopToolbarOrANewOne() {
        ToolBar toolBar = new ToolBar();
        VBox withToolBar = new VBox(toolBar);
        VBox withoutToolBar = new VBox(new StackPane());

        separateWindows.addOpenButton(withToolBar, () -> { });
        separateWindows.addOpenButton(withoutToolBar, () -> { });

        assertInstanceOf(SplitMenuButton.class, toolBar.getItems().getLast());
        assertInstanceOf(ToolBar.class, withoutToolBar.getChildren().getFirst());
    }
}
