/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.diagram;

import com.powsybl.powsybldesktop.utils.FileChooserPreferences;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.concurrent.Worker;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.scene.Cursor;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToolBar;
import javafx.scene.image.Image;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * One diagram viewport: a toolbar (zoom in/out/preset, fit-to-screen, PNG/SVG export) plus a
 * {@link WebView} that displays an SVG diagram injected into an HTML shell. Reusable via
 * {@code fx:include} — the substations view uses two instances (single line diagram, area diagram),
 * each just supplying its own HTML shell/svg content instead of duplicating this logic.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class DiagramPaneController {

    private static final Logger LOGGER = LoggerFactory.getLogger(DiagramPaneController.class);

    private static final List<Integer> ZOOM_PRESETS = List.of(25, 50, 75, 100, 125, 150, 175, 200);
    private static final double ZOOM_STEP_FACTOR = 1.1;
    // svgWidthAndHeightAdded(true)-equivalent parameters write the diagram's pixel size as the root
    // <svg> element's plain (unitless) width/height attributes. Serializers write attributes in
    // alphabetical order (height before width), so these are matched independently rather than with
    // a single ordered pattern.
    private static final Pattern SVG_ROOT_TAG_PATTERN = Pattern.compile("<svg\\b[^>]*>");
    private static final Pattern WIDTH_ATTR_PATTERN = Pattern.compile("\\bwidth=\"([\\d.]+)\"");
    private static final Pattern HEIGHT_ATTR_PATTERN = Pattern.compile("\\bheight=\"([\\d.]+)\"");
    // platform-forbidden filename characters (superset covering Windows/Linux/macOS) plus control chars
    private static final Pattern FORBIDDEN_FILENAME_CHARS_PATTERN = Pattern.compile("[\\\\/:*?\"<>|\\x00-\\x1F]");

    // no interactivity by default; callers with click-handling JS (e.g. the single line diagram) call loadShell()
    private static final String DEFAULT_HTML_SHELL = """
                <html>
                    <body style='margin: 0'>
                        <div id="svgContainer"></div>
                    </body>
                </html>
            """;

    private static final StringConverter<Integer> ZOOM_PERCENT_CONVERTER = new StringConverter<>() {
        @Override
        public String toString(Integer percent) {
            return percent == null ? "" : percent + "%";
        }

        @Override
        public Integer fromString(String string) {
            return null;
        }
    };

    @FXML
    private ToolBar toolBar;
    @FXML
    private WebView webView;
    @FXML
    private Label noSelectionLabel;
    @FXML
    private ComboBox<Integer> zoomComboBox;
    @FXML
    private ToggleButton fitToScreenToggleButton;
    @FXML
    private MenuButton exportMenuButton;

    private final StringProperty svg = new SimpleStringProperty("");
    private final DoubleProperty zoom = new SimpleDoubleProperty(1.0);
    private final BooleanProperty fitToScreen = new SimpleBooleanProperty(false);
    private String pendingContent;
    private double diagramWidth;
    private double diagramHeight;
    // guards zoomComboBox/fitToScreenToggleButton updates driven by setZoom()/restoreZoom() so they
    // don't re-trigger onZoomPresetSelected()/onFitToScreenToggle() as if the user had acted
    private boolean programmaticZoomChange;
    private Runnable onEngineLoaded;
    private Supplier<String> fileNameSupplier = () -> null;
    private boolean panning;
    private double lastPanScreenX;
    private double lastPanScreenY;

    @FXML
    private void initialize() {
        webView.setContextMenuEnabled(false);
        webView.getEngine().loadContent(DEFAULT_HTML_SHELL);
        webView.getEngine().getLoadWorker().stateProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == Worker.State.SUCCEEDED) {
                if (pendingContent != null) {
                    inject(pendingContent);
                    pendingContent = null;
                }
                if (onEngineLoaded != null) {
                    onEngineLoaded.run();
                }
            }
        });

        zoomComboBox.getItems().setAll(ZOOM_PRESETS);
        zoomComboBox.setConverter(ZOOM_PERCENT_CONVERTER);
        exportMenuButton.disableProperty().bind(svg.isEmpty());
        // matches the zoom/fitToScreen properties' own defaults (1.0/not fitted); otherwise the combo
        // box shows nothing until a caller explicitly restores a persisted value (see SubstationsController)
        setZoom(zoom.get(), false);

        // re-fit whenever the diagram viewport is resized (e.g. the split pane divider is dragged)
        webView.widthProperty().addListener((observable, oldValue, newValue) -> {
            if (fitToScreenToggleButton.isSelected()) {
                applyFitToScreen();
            }
        });
        webView.heightProperty().addListener((observable, oldValue, newValue) -> {
            if (fitToScreenToggleButton.isSelected()) {
                applyFitToScreen();
            }
        });
        webView.addEventFilter(ScrollEvent.SCROLL, this::onScroll);
        webView.addEventFilter(MouseEvent.MOUSE_PRESSED, this::onMousePressed);
        webView.addEventFilter(MouseEvent.MOUSE_DRAGGED, this::onMouseDragged);
        webView.addEventFilter(MouseEvent.MOUSE_RELEASED, this::onMouseReleased);

        svg.addListener((observable, oldValue, newValue) -> {
            if (!newValue.isEmpty()) {
                parseDiagramSize(newValue);
                if (fitToScreenToggleButton.isSelected()) {
                    applyFitToScreen();
                }
            }
            showContent(newValue);
        });
    }

    /**
     * Replaces the default (non-interactive) HTML shell, e.g. to add click-handling JS. Must keep a
     * {@code svgContainer} element, since that's where {@link #showDiagram} injects the SVG.
     */
    public void loadShell(String html) {
        webView.getEngine().loadContent(html);
    }

    /** Called every time the page finishes loading: the initial shell, {@link #loadShell}, or a future reload. */
    public void setOnEngineLoaded(Runnable onEngineLoaded) {
        this.onEngineLoaded = onEngineLoaded;
    }

    /** Supplies the default file name (sanitized) offered by the PNG/SVG save dialogs; may return null. */
    public void setDiagramFileNameSupplier(Supplier<String> fileNameSupplier) {
        this.fileNameSupplier = fileNameSupplier;
    }

    public WebView getWebView() {
        return webView;
    }

    /** The shared zoom/fit-to-screen/export toolbar, exposed so a caller can append pane-specific controls to it. */
    public ToolBar getToolBar() {
        return toolBar;
    }

    public StringProperty svgProperty() {
        return svg;
    }

    public ReadOnlyDoubleProperty zoomProperty() {
        return zoom;
    }

    public ReadOnlyBooleanProperty fitToScreenProperty() {
        return fitToScreen;
    }

    public void showDiagram(String svgContent) {
        noSelectionLabel.setVisible(false);
        svg.setValue(svgContent);
    }

    public void showNoSelection() {
        noSelectionLabel.setVisible(true);
        svg.setValue("");
    }

    public void restoreZoom(double zoomValue, boolean fitToScreenValue) {
        programmaticZoomChange = true;
        try {
            fitToScreenToggleButton.setSelected(fitToScreenValue);
        } finally {
            programmaticZoomChange = false;
        }
        setZoom(zoomValue, false);
    }

    private void parseDiagramSize(String svgContent) {
        var rootTag = SVG_ROOT_TAG_PATTERN.matcher(svgContent);
        if (!rootTag.find()) {
            return;
        }
        String svgTag = rootTag.group();
        var width = WIDTH_ATTR_PATTERN.matcher(svgTag);
        var height = HEIGHT_ATTR_PATTERN.matcher(svgTag);
        if (width.find() && height.find()) {
            diagramWidth = Double.parseDouble(width.group(1));
            diagramHeight = Double.parseDouble(height.group(1));
        }
    }

    private void showContent(String content) {
        if (webView.getEngine().getLoadWorker().getState() == Worker.State.SUCCEEDED) {
            inject(content);
        } else {
            pendingContent = content;
        }
    }

    private void inject(String content) {
        webView.getEngine().executeScript("document.getElementById('svgContainer').innerHTML = `" + content + "`;");
    }

    @FXML
    private void onZoomIn() {
        setZoom(webView.getZoom() * ZOOM_STEP_FACTOR, true);
    }

    @FXML
    private void onZoomOut() {
        setZoom(webView.getZoom() / ZOOM_STEP_FACTOR, true);
    }

    @FXML
    private void onZoomPresetSelected() {
        Integer percent = zoomComboBox.getValue();
        if (!programmaticZoomChange && percent != null) {
            setZoom(percent / 100.0, true);
        }
    }

    @FXML
    private void onFitToScreenToggle() {
        if (programmaticZoomChange) {
            return;
        }
        boolean selected = fitToScreenToggleButton.isSelected();
        fitToScreen.set(selected);
        if (selected) {
            applyFitToScreen();
        }
    }

    private void applyFitToScreen() {
        if (diagramWidth <= 0 || diagramHeight <= 0 || webView.getWidth() <= 0 || webView.getHeight() <= 0) {
            return;
        }
        setZoom(Math.min(webView.getWidth() / diagramWidth, webView.getHeight() / diagramHeight), false);
    }

    private void setZoom(double zoomValue, boolean disableFitToScreen) {
        programmaticZoomChange = true;
        try {
            webView.setZoom(zoomValue);
            zoom.set(zoomValue);
            // setValue() (rather than the selection model) displays the value via the converter even
            // when it isn't one of the preset items, e.g. after a wheel/button zoom or a fit-to-screen
            zoomComboBox.setValue((int) Math.round(zoomValue * 100));
            if (disableFitToScreen && fitToScreenToggleButton.isSelected()) {
                fitToScreenToggleButton.setSelected(false);
                fitToScreen.set(false);
            }
        } finally {
            programmaticZoomChange = false;
        }
    }

    private void onScroll(ScrollEvent e) {
        double deltaY = e.getDeltaY();
        double zoomValue = webView.getZoom();
        if (deltaY < 0) {
            zoomValue /= ZOOM_STEP_FACTOR;
        } else if (deltaY > 0) {
            zoomValue *= ZOOM_STEP_FACTOR;
        }
        setZoom(zoomValue, true);
        e.consume();
    }

    private void onMousePressed(MouseEvent e) {
        if (e.getButton() == MouseButton.MIDDLE) {
            panning = true;
            lastPanScreenX = e.getScreenX();
            lastPanScreenY = e.getScreenY();
            webView.setCursor(Cursor.MOVE);
            e.consume();
        }
    }

    private void onMouseDragged(MouseEvent e) {
        if (panning) {
            double deltaX = e.getScreenX() - lastPanScreenX;
            double deltaY = e.getScreenY() - lastPanScreenY;
            lastPanScreenX = e.getScreenX();
            lastPanScreenY = e.getScreenY();
            webView.getEngine().executeScript("window.scrollBy(" + (-deltaX) + ", " + (-deltaY) + ")");
            e.consume();
        }
    }

    private void onMouseReleased(MouseEvent e) {
        if (e.getButton() == MouseButton.MIDDLE) {
            panning = false;
            webView.setCursor(Cursor.DEFAULT);
            e.consume();
        }
    }

    @FXML
    private void onCopyDiagramPng() {
        ClipboardContent content = new ClipboardContent();
        content.putImage(snapshot());
        Clipboard.getSystemClipboard().setContent(content);
    }

    @FXML
    private void onCopyDiagramSvg() {
        ClipboardContent content = new ClipboardContent();
        content.putString(svg.get());
        Clipboard.getSystemClipboard().setContent(content);
    }

    @FXML
    private void onSaveDiagramPng() {
        chooseDiagramFile("png").ifPresent(file -> {
            try {
                ImageIO.write(SwingFXUtils.fromFXImage(snapshot(), null), "png", file);
            } catch (IOException e) {
                LOGGER.error(e.getMessage(), e);
            }
        });
    }

    @FXML
    private void onSaveDiagramSvg() {
        chooseDiagramFile("svg").ifPresent(file -> {
            try {
                Files.writeString(file.toPath(), svg.get(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                LOGGER.error(e.getMessage(), e);
            }
        });
    }

    private Optional<File> chooseDiagramFile(String extension) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                Messages.get("networks.file.supportedFiles", extension), "*." + extension));
        fileChooser.setInitialFileName(diagramFileName() + "." + extension);
        FileChooserPreferences.applyLastDirectory(fileChooser);
        File file = fileChooser.showSaveDialog(webView.getScene().getWindow());
        if (file == null) {
            return Optional.empty();
        }
        FileChooserPreferences.saveLastDirectory(file);
        return Optional.of(file);
    }

    private String diagramFileName() {
        String name = fileNameSupplier.get();
        String sanitized = name == null ? "" : FORBIDDEN_FILENAME_CHARS_PATTERN.matcher(name).replaceAll("_").strip();
        return sanitized.isEmpty() ? "diagram" : sanitized;
    }

    // rasterizes exactly what's currently displayed (current zoom/scroll), like a screenshot of the diagram view
    private Image snapshot() {
        return webView.snapshot(new SnapshotParameters(), null);
    }
}
