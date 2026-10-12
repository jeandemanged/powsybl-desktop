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
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SplitMenuButton;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.robot.Robot;
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
import java.util.stream.IntStream;

/**
 * The windows showing views in a fixed context, next to the main window: no menu, toolbar nor history of their own,
 * their views' navigation drives the main window (see {@link SceneModel#separate}). Independent stages, so that the
 * main window can come in front of them, all closed with it.
 * <p>
 * Each window is named ("Window 1" by default, renamed by double-clicking its name or from the main window's Windows
 * menu) and shows its views as tabs: a view is opened in a new window or as a tab of an existing one, and its tab can
 * be dragged to another window, reordered, or dropped outside any window to get its own. A window closes with its
 * last tab.
 * <p>
 * A network-bound view shows the network selected when it was opened: before the main window selects another
 * network, {@link #confirmNetworkChange} asks whether to close them, and they are closed anyway once it changes (e.g.
 * the network was closed). Logs and reports views aren't bound, and are single-instance.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class SeparateWindows {

    // a Pane a view can declare (fx:id) to host its "open in new window" button, see addOpenButton
    private static final String BUTTON_HOST_ID = "openInNewWindowHost";

    private static final double DEFAULT_WIDTH = 900;
    private static final double DEFAULT_HEIGHT = 600;

    // only tells drop targets that a tab is dragged, the tab itself being draggedView: a drag never leaves the application
    private static final DataFormat TAB_FORMAT = new DataFormat("application/x-powsybl-desktop-tab");

    /**
     * A separate view's content: its root, the controller disposed when its tab closes, and its new window's size.
     */
    public record View(Parent root, DisposableController controller, double width, double height) {
        public View(Parent root, DisposableController controller) {
            this(root, controller, DEFAULT_WIDTH, DEFAULT_HEIGHT);
        }
    }

    private static final class SeparateView {
        private final String key;
        // re-resolved, as is the content, on a language change, see relocalize
        private final Supplier<String> titleSupplier;
        private final Function<SceneModel, View> viewFactory;
        private final SceneModel sceneModel;
        private final boolean bound;
        private final Network network;
        private final Tab tab = new Tab();
        private final Label label = new Label();
        private String title;
        private DisposableController controller;
        private DetachedWindow window;

        private SeparateView(String key, Supplier<String> titleSupplier, Function<SceneModel, View> viewFactory, SceneModel sceneModel,
                             boolean bound, View view) {
            this.key = key;
            this.titleSupplier = titleSupplier;
            this.viewFactory = viewFactory;
            this.sceneModel = sceneModel;
            this.bound = bound;
            this.network = sceneModel.getNetwork();
            this.title = titleSupplier.get();
            this.controller = view.controller();
            label.setText(title);
            tab.setGraphic(label);
            tab.setContent(view.root());
            tab.setUserData(this);
        }

        private void reload() {
            title = titleSupplier.get();
            label.setText(title);
            controller.dispose();
            View view = viewFactory.apply(sceneModel);
            controller = view.controller();
            tab.setContent(view.root());
        }
    }

    private static SeparateView view(Tab tab) {
        return (SeparateView) tab.getUserData();
    }

    /**
     * A window, as listed to the user: its name and its tabs.
     */
    public final class DetachedWindow {
        private final Stage stage = new Stage();
        // while the window has its default name, its number, to translate it on a language change; 0 once renamed
        private int defaultNumber = defaultNumber();
        private final StringProperty name = new SimpleStringProperty(defaultName(defaultNumber));
        private final TabPane tabPane = new TabPane();
        private final Label nameLabel = new Label();
        private final HBox nameBox = new HBox(nameLabel);

        private DetachedWindow(double width, double height) {
            tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
            tabPane.setOnDragOver(event -> {
                if (draggedView != null && event.getDragboard().hasContent(TAB_FORMAT)) {
                    event.acceptTransferModes(TransferMode.MOVE);
                }
                event.consume();
            });
            tabPane.setOnDragDropped(event -> {
                SeparateView dropped = draggedView;
                if (dropped != null) {
                    draggedView = null;
                    move(dropped, this, dropIndex(dropped, event.getSceneX()));
                    focus(stage);
                }
                event.setDropCompleted(dropped != null);
                event.consume();
            });

            nameLabel.textProperty().bind(name);
            nameLabel.getStyleClass().add("section-title");
            nameLabel.setTooltip(new Tooltip(Messages.get("separateWindows.renameHint")));
            nameLabel.setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                    editName();
                }
            });
            nameBox.setAlignment(Pos.CENTER_LEFT);
            nameBox.setPadding(new Insets(0, 6, 0, 8));
            nameBox.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
            StackPane root = new StackPane(tabPane, nameBox);
            StackPane.setAlignment(nameBox, Pos.TOP_LEFT);

            name.addListener((observable, oldName, newName) -> updateTitle());
            tabPane.getSelectionModel().selectedItemProperty().addListener((observable, oldTab, newTab) -> updateTitle());
            updateTitle();
            stage.getIcons().add(new Image(Objects.requireNonNull(MainApplication.class.getResourceAsStream("logo.png"))));
            Scene scene = new Scene(root, width, height);
            scene.getStylesheets().add(Objects.requireNonNull(MainApplication.class.getResource("styles.css")).toExternalForm());
            stage.setScene(scene);
            // the name sits over the left of the tab strip, whose tabs are shifted right of it
            root.applyCss();
            if (tabPane.lookup(".tab-header-area") instanceof Region header) {
                Insets padding = header.getPadding();
                header.paddingProperty().bind(Bindings.createObjectBinding(
                        () -> new Insets(padding.getTop(), padding.getRight(), padding.getBottom(), nameBox.getWidth()), nameBox.widthProperty()));
                nameBox.prefHeightProperty().bind(header.heightProperty());
            }
            stage.setOnHidden(event -> {
                tabPane.getTabs().forEach(tab -> dispose(view(tab)));
                windows.remove(this);
            });
            windows.add(this);
        }

        public String getName() {
            return name.get();
        }

        /**
         * Renames this window, unless {@code newName} is blank or another window's name.
         */
        public boolean rename(String newName) {
            String trimmed = newName == null ? "" : newName.trim();
            if (trimmed.isEmpty() || windows.stream().anyMatch(window -> window != this && window.getName().equals(trimmed))) {
                return false;
            }
            defaultNumber = 0;
            name.set(trimmed);
            return true;
        }

        public List<String> getTabTitles() {
            return tabPane.getTabs().stream().map(tab -> view(tab).title).toList();
        }

        public void show() {
            focus(stage);
        }

        public void showTab(int index) {
            tabPane.getSelectionModel().select(index);
            focus(stage);
        }

        private void updateTitle() {
            Tab selected = tabPane.getSelectionModel().getSelectedItem();
            stage.setTitle(MainApplication.APP_TITLE + " - " + name.get() + (selected == null ? "" : " - " + view(selected).title));
        }

        private void relocalize() {
            if (defaultNumber > 0) {
                name.set(defaultName(defaultNumber));
            }
            nameLabel.setTooltip(new Tooltip(Messages.get("separateWindows.renameHint")));
            tabPane.getTabs().forEach(tab -> view(tab).reload());
            updateTitle();
        }

        private void add(SeparateView view, int index) {
            view.window = this;
            tabPane.getTabs().add(Math.min(index, tabPane.getTabs().size()), view.tab);
            tabPane.getSelectionModel().select(view.tab);
        }

        private void closeIfEmpty() {
            if (tabPane.getTabs().isEmpty()) {
                stage.close();
            }
        }

        // after the other tabs whose title is left of sceneX
        private int dropIndex(SeparateView dropped, double sceneX) {
            return (int) tabPane.getTabs().stream()
                    .filter(tab -> tab != dropped.tab && tab.getGraphic() != null)
                    .filter(tab -> {
                        Node graphic = tab.getGraphic();
                        Bounds bounds = graphic.localToScene(graphic.getBoundsInLocal());
                        return bounds.getCenterX() < sceneX;
                    })
                    .count();
        }

        private void editName() {
            TextField field = new TextField(name.get());
            field.setPrefColumnCount(12);
            field.setOnAction(event -> endEditName(field, true));
            field.setOnKeyPressed(event -> {
                if (event.getCode() == KeyCode.ESCAPE) {
                    endEditName(field, false);
                }
            });
            field.focusedProperty().addListener((observable, oldValue, focused) -> {
                if (!focused) {
                    endEditName(field, true);
                }
            });
            nameBox.getChildren().setAll(field);
            field.requestFocus();
            field.selectAll();
        }

        private void endEditName(TextField field, boolean commit) {
            // also called back when removing the field takes its focus away
            if (nameBox.getChildren().contains(field)) {
                if (commit) {
                    rename(field.getText());
                }
                nameBox.getChildren().setAll(nameLabel);
            }
        }
    }

    private final MainModel mainModel;
    private Supplier<Window> mainWindow;
    private final List<SeparateView> views = new ArrayList<>();
    private final ObservableList<DetachedWindow> windows = FXCollections.observableArrayList();
    // cached, see NavigationHistory's unmodifiable views
    private final ObservableList<DetachedWindow> openWindows = FXCollections.unmodifiableObservableList(windows);
    private final ChangeListener<Network> networkListener;
    // the window the view being opened goes to, a new one if null, see openIn
    private DetachedWindow destination;
    private SeparateView draggedView;

    public SeparateWindows(MainModel mainModel, Supplier<Window> mainWindow) {
        this.mainModel = Objects.requireNonNull(mainModel);
        this.mainWindow = Objects.requireNonNull(mainWindow);
        networkListener = (observable, oldNetwork, newNetwork) -> close(boundToOtherThan(newNetwork));
        mainModel.networkProperty().addListener(networkListener);
    }

    /**
     * The main window's shell, which supplies it, is rebuilt on a language change, see {@link #relocalize}.
     */
    public void setMainWindow(Supplier<Window> mainWindow) {
        this.mainWindow = Objects.requireNonNull(mainWindow);
    }

    /**
     * Rebuilds the open views, and re-resolves the windows' texts, under the now-current locale: the windows, their
     * tabs and the views' scenes are kept, but the views' own state (e.g. a table's sorting) isn't.
     */
    public void relocalize() {
        windows.forEach(DetachedWindow::relocalize);
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
     * Opens a view bound to the selected network, built for its window's scene.
     */
    public void open(Supplier<String> title, Function<SceneModel, View> viewFactory) {
        open(null, title, viewFactory);
    }

    /**
     * Same as {@link #open(Supplier, Function)}, but brings the view of {@code key} to front instead if already open:
     * a view of the whole network, or of a given container, is shown once.
     */
    public void open(String key, Supplier<String> title, Function<SceneModel, View> viewFactory) {
        if (!focusOpen(key)) {
            SceneModel sceneModel = SceneModel.separate(mainModel, this::bringMainWindowToFront);
            View view = viewFactory.apply(sceneModel);
            show(new SeparateView(key, title, viewFactory, sceneModel, true, view), view);
        }
    }

    /**
     * Opens the single view of {@code key} not bound to a network, or brings it to front if already open.
     */
    public void openSingleInstance(String key, Supplier<String> title, Function<SceneModel, View> viewFactory) {
        if (!focusOpen(Objects.requireNonNull(key))) {
            SceneModel sceneModel = SceneModel.separate(mainModel, this::bringMainWindowToFront);
            View view = viewFactory.apply(sceneModel);
            show(new SeparateView(key, title, viewFactory, sceneModel, false, view), view);
        }
    }

    /**
     * Brings the view of {@code key} to front, if open.
     */
    public boolean focusOpen(String key) {
        Optional<SeparateView> open = find(key);
        open.ifPresent(view -> view.window.showTab(view.window.tabPane.getTabs().indexOf(view.tab)));
        return open.isPresent();
    }

    public void close(String key) {
        close(views.stream().filter(view -> key.equals(view.key)).toList());
    }

    public static void focus(Stage stage) {
        stage.setIconified(false);
        stage.toFront();
        stage.requestFocus();
    }

    /**
     * The open windows, in opening order.
     */
    public ObservableList<DetachedWindow> getOpenWindows() {
        return openWindows;
    }

    /**
     * Moves the view of {@code key} to {@code target} at {@code index}, or to a new window if {@code target} is null.
     */
    void move(String key, DetachedWindow target, int index) {
        SeparateView view = find(key).orElseThrow();
        if (target == null) {
            newWindowFor(view, view.window.stage.getScene().getWidth(), view.window.stage.getScene().getHeight()).stage.show();
        } else {
            move(view, target, index);
        }
    }

    private Optional<SeparateView> find(String key) {
        return key == null ? Optional.empty() : views.stream().filter(view -> key.equals(view.key)).findFirst();
    }

    private void openIn(DetachedWindow window, Runnable open) {
        destination = window;
        try {
            open.run();
        } finally {
            destination = null;
        }
    }

    private void show(SeparateView view, View content) {
        Label label = view.label;
        label.setOnDragDetected(event -> {
            Dragboard dragboard = label.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent clipboardContent = new ClipboardContent();
            clipboardContent.put(TAB_FORMAT, view.title);
            dragboard.setContent(clipboardContent);
            dragboard.setDragView(label.snapshot(null, null));
            draggedView = view;
            event.consume();
        });
        label.setOnDragDone(event -> {
            // still set: no window took it
            if (draggedView == view) {
                draggedView = null;
                tearOff(view, new Robot().getMousePosition());
            }
            event.consume();
        });
        view.tab.setOnClosed(event -> {
            dispose(view);
            view.window.closeIfEmpty();
        });
        views.add(view);
        if (destination != null && windows.contains(destination)) {
            destination.add(view, Integer.MAX_VALUE);
            destination.show();
        } else {
            DetachedWindow window = new DetachedWindow(content.width(), content.height());
            window.add(view, 0);
            window.stage.show();
        }
    }

    private void move(SeparateView view, DetachedWindow target, int index) {
        DetachedWindow source = view.window;
        source.tabPane.getTabs().remove(view.tab);
        target.add(view, index);
        if (source != target) {
            source.closeIfEmpty();
        }
    }

    private DetachedWindow newWindowFor(SeparateView view, double width, double height) {
        DetachedWindow window = new DetachedWindow(width, height);
        move(view, window, 0);
        return window;
    }

    private void tearOff(SeparateView view, Point2D position) {
        Stage stage = view.window.stage;
        if (view.window.tabPane.getTabs().size() > 1) {
            stage = newWindowFor(view, stage.getScene().getWidth(), stage.getScene().getHeight()).stage;
        }
        stage.setX(position.getX());
        stage.setY(position.getY());
        stage.show();
    }

    private void dispose(SeparateView view) {
        if (views.remove(view)) {
            view.controller.dispose();
        }
    }

    // the lowest N such that no window is named "Window N"
    private int defaultNumber() {
        return IntStream.iterate(1, n -> n + 1)
                .filter(n -> windows.stream().noneMatch(window -> window.getName().equals(defaultName(n))))
                .findFirst()
                .orElseThrow();
    }

    private static String defaultName(int number) {
        return Messages.get("separateWindows.defaultName", number);
    }

    private void bringMainWindowToFront() {
        if (mainWindow.get() instanceof Stage stage) {
            stage.toFront();
        }
    }

    /**
     * Whether the main window may select {@code target}: true if no view is bound to another network, or if the
     * user confirms closing them (then closed).
     */
    public boolean confirmNetworkChange(Network target) {
        List<SeparateView> toClose = boundToOtherThan(target);
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

    private List<SeparateView> boundToOtherThan(Network network) {
        return views.stream().filter(view -> view.bound && view.network != network).toList();
    }

    private void close(List<SeparateView> toClose) {
        toClose.forEach(view -> {
            view.window.tabPane.getTabs().remove(view.tab);
            dispose(view);
            view.window.closeIfEmpty();
        });
    }

    public void closeAll() {
        mainModel.networkProperty().removeListener(networkListener);
        List.copyOf(windows).forEach(window -> window.stage.close());
    }

    /**
     * Adds an "open in new window" button to a view: in the Pane with id {@value #BUTTON_HOST_ID} if it declares
     * one, else at the end of its top toolbar (a BorderPane's top or a VBox's first child), else in a new toolbar at
     * the top of a VBox. Its menu opens the view in an existing window instead.
     */
    public void addOpenButton(Parent viewRoot, Runnable open) {
        SplitMenuButton button = openButton(open);
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

    public void addOpenButton(ToolBar toolBar, Runnable open) {
        addTo(toolBar, openButton(open));
    }

    private SplitMenuButton openButton(Runnable open) {
        SplitMenuButton button = new SplitMenuButton();
        button.setGraphic(new FontIcon("mdi2o-open-in-new"));
        button.setTooltip(new Tooltip(Messages.get("separateWindows.open")));
        button.setOnAction(event -> openIn(null, open));
        MenuItem newWindow = new MenuItem(Messages.get("separateWindows.newWindow"));
        newWindow.setOnAction(event -> openIn(null, open));
        button.getItems().setAll(newWindow);
        button.setOnShowing(event -> {
            button.getItems().setAll(newWindow);
            windows.forEach(window -> {
                MenuItem item = new MenuItem(window.getName());
                item.setMnemonicParsing(false);
                item.setOnAction(itemEvent -> openIn(window, open));
                button.getItems().add(item);
            });
        });
        return button;
    }

    // right-aligned
    private static void addTo(ToolBar toolBar, SplitMenuButton button) {
        Pane spacer = new Pane();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        toolBar.getItems().addAll(spacer, button);
    }
}
