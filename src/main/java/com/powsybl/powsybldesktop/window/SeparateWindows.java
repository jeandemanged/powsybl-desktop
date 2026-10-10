/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.window;

import com.powsybl.iidm.network.Network;
import com.powsybl.powsybldesktop.MainApplication;
import com.powsybl.powsybldesktop.MainModel;
import com.powsybl.powsybldesktop.SceneModel;
import com.powsybl.powsybldesktop.utils.DisposableController;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The windows showing one view each in a fixed context, next to the main window: no menu, toolbar nor history of
 * their own, their navigation drives the main window (see {@link SceneModel#separate}). Independent stages, so that
 * the main window can come in front of them, all closed with it.
 * <p>
 * A network-bound window shows the network selected when it was opened: before the main window selects another
 * network, {@link #confirmNetworkChange} asks whether to close them, and they are closed anyway once it changes (e.g.
 * the network was closed). Logs and reports windows aren't bound, and are single-instance.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class SeparateWindows {

    // a Pane a view can declare (fx:id) to host its "open in new window" button, see addOpenButton
    private static final String BUTTON_HOST_ID = "openInNewWindowHost";

    private static final double DEFAULT_WIDTH = 900;
    private static final double DEFAULT_HEIGHT = 600;

    /**
     * A separate window's content: its root, the controller disposed when the window closes, and its initial size.
     */
    public record View(Parent root, DisposableController controller, double width, double height) {
        public View(Parent root, DisposableController controller) {
            this(root, controller, DEFAULT_WIDTH, DEFAULT_HEIGHT);
        }
    }

    /**
     * An open window, as listed to the user.
     */
    public record OpenWindow(String title, Stage stage) {
    }

    private record SeparateWindow(OpenWindow openWindow, boolean bound, Network network, String key) {
        Stage stage() {
            return openWindow.stage();
        }
    }

    private final MainModel mainModel;
    private final Supplier<Window> mainWindow;
    private final List<SeparateWindow> windows = new ArrayList<>();
    private final ObservableList<OpenWindow> modifiableOpenWindows = FXCollections.observableArrayList();
    // cached, see NavigationHistory's unmodifiable views
    private final ObservableList<OpenWindow> openWindows = FXCollections.unmodifiableObservableList(modifiableOpenWindows);
    private final ChangeListener<Network> networkListener;

    public SeparateWindows(MainModel mainModel, Supplier<Window> mainWindow) {
        this.mainModel = Objects.requireNonNull(mainModel);
        this.mainWindow = Objects.requireNonNull(mainWindow);
        networkListener = (observable, oldNetwork, newNetwork) -> close(boundToOtherThan(newNetwork));
        mainModel.networkProperty().addListener(networkListener);
    }

    /**
     * Loads {@code fxml} (relative to the application package), its controller set up by {@code setup}.
     */
    public static <T extends DisposableController> View load(String fxml, Consumer<T> setup) {
        FXMLLoader loader = new FXMLLoader(MainApplication.class.getResource(fxml), Messages.bundle());
        try {
            Parent root = loader.load();
            T controller = loader.getController();
            setup.accept(controller);
            return new View(root, controller);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Opens a window bound to the selected network, its view built for the window's scene.
     */
    public void open(String title, Function<SceneModel, View> viewFactory) {
        open(null, title, viewFactory);
    }

    /**
     * Same as {@link #open(String, Function)}, but brings the window of {@code key} to front instead if already open:
     * a view of the whole network, or of a given container, is shown once.
     */
    public void open(String key, String title, Function<SceneModel, View> viewFactory) {
        if (!focusOpen(key)) {
            SceneModel sceneModel = SceneModel.separate(mainModel, this::bringMainWindowToFront);
            show(new SeparateWindow(new OpenWindow(title, new Stage()), true, sceneModel.getNetwork(), key), viewFactory.apply(sceneModel));
        }
    }

    /**
     * Opens the single window of {@code key} not bound to a network, or brings it to front if already open.
     */
    public void openSingleInstance(String key, String title, Function<SceneModel, View> viewFactory) {
        if (!focusOpen(Objects.requireNonNull(key))) {
            show(new SeparateWindow(new OpenWindow(title, new Stage()), false, null, key),
                    viewFactory.apply(SceneModel.separate(mainModel, this::bringMainWindowToFront)));
        }
    }

    /**
     * Brings the window of {@code key} to front, if open.
     */
    public boolean focusOpen(String key) {
        Optional<SeparateWindow> open = key == null ? Optional.empty() : windows.stream().filter(window -> key.equals(window.key())).findFirst();
        open.ifPresent(window -> focus(window.stage()));
        return open.isPresent();
    }

    public void close(String key) {
        close(windows.stream().filter(window -> key.equals(window.key())).toList());
    }

    public static void focus(Stage stage) {
        stage.setIconified(false);
        stage.toFront();
        stage.requestFocus();
    }

    /**
     * The open windows, in opening order.
     */
    public ObservableList<OpenWindow> getOpenWindows() {
        return openWindows;
    }

    private void show(SeparateWindow window, View view) {
        Stage stage = window.stage();
        stage.setTitle(MainApplication.APP_TITLE + " - " + window.openWindow().title());
        stage.getIcons().add(new Image(Objects.requireNonNull(MainApplication.class.getResourceAsStream("logo.png"))));
        Scene scene = new Scene(view.root(), view.width(), view.height());
        scene.getStylesheets().add(Objects.requireNonNull(MainApplication.class.getResource("styles.css")).toExternalForm());
        stage.setScene(scene);
        stage.setOnHidden(event -> {
            view.controller().dispose();
            windows.remove(window);
            modifiableOpenWindows.remove(window.openWindow());
        });
        windows.add(window);
        modifiableOpenWindows.add(window.openWindow());
        stage.show();
    }

    private void bringMainWindowToFront() {
        if (mainWindow.get() instanceof Stage stage) {
            stage.toFront();
        }
    }

    /**
     * Whether the main window may select {@code target}: true if no window is bound to another network, or if the
     * user confirms closing them (then closed).
     */
    public boolean confirmNetworkChange(Network target) {
        List<SeparateWindow> toClose = boundToOtherThan(target);
        if (toClose.isEmpty()) {
            return true;
        }
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, Messages.get("separateWindows.closeOnNetworkChange", toClose.size()),
                ButtonType.OK, ButtonType.CANCEL);
        alert.initOwner(mainWindow.get());
        alert.setHeaderText(null);
        if (alert.showAndWait().filter(ButtonType.OK::equals).isEmpty()) {
            return false;
        }
        close(toClose);
        return true;
    }

    private List<SeparateWindow> boundToOtherThan(Network network) {
        return windows.stream().filter(window -> window.bound() && window.network() != network).toList();
    }

    private static void close(List<SeparateWindow> toClose) {
        toClose.forEach(window -> window.stage().close());
    }

    public void closeAll() {
        mainModel.networkProperty().removeListener(networkListener);
        close(List.copyOf(windows));
    }

    /**
     * Adds an "open in new window" button to a view: in the Pane with id {@value #BUTTON_HOST_ID} if it declares
     * one, else at the end of its top toolbar (a BorderPane's top or a VBox's first child), else in a new toolbar at
     * the top of a VBox.
     */
    public static void addOpenButton(Parent viewRoot, Runnable open) {
        Button button = openButton(open);
        if (viewRoot.lookup("#" + BUTTON_HOST_ID) instanceof Pane host) {
            host.getChildren().add(button);
        } else if (viewRoot instanceof BorderPane borderPane && borderPane.getTop() instanceof ToolBar toolBar) {
            addTo(toolBar, button);
        } else if (viewRoot instanceof VBox vBox && !vBox.getChildren().isEmpty() && vBox.getChildren().getFirst() instanceof ToolBar toolBar) {
            addTo(toolBar, button);
        } else if (viewRoot instanceof VBox vBox) {
            ToolBar toolBar = new ToolBar();
            addTo(toolBar, button);
            vBox.getChildren().addFirst(toolBar);
        } else {
            throw new IllegalArgumentException("No place for an open in new window button in " + viewRoot);
        }
    }

    public static void addOpenButton(ToolBar toolBar, Runnable open) {
        addTo(toolBar, openButton(open));
    }

    private static Button openButton(Runnable open) {
        Button button = new Button();
        button.setGraphic(new FontIcon("mdi2o-open-in-new"));
        button.setTooltip(new Tooltip(Messages.get("separateWindows.open")));
        button.setOnAction(event -> open.run());
        return button;
    }

    // right-aligned
    private static void addTo(ToolBar toolBar, Button button) {
        Pane spacer = new Pane();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        toolBar.getItems().addAll(spacer, button);
    }
}
