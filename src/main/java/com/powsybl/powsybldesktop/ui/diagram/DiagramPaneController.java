/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.ui.diagram;

import com.powsybl.powsybldesktop.core.UnexpectedErrors;
import com.powsybl.powsybldesktop.ui.controls.FileChooserPreferences;
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
import javafx.fxml.FXML;
import javafx.scene.Cursor;
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
import netscape.javascript.JSObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
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

    // The SVG is drawn on a canvas by the page's own WebKit, so the PNG looks like the view without being limited to
    // its viewport and screen resolution. PNG_SCALE times the diagram's own pixel size, within what a canvas can hold.
    private static final double PNG_SCALE = 2;
    private static final int PNG_MAX_SIDE = 8192;
    private static final String RASTERIZE_SCRIPT = """
            (function() {
                var bridge = window.pngExport;
                var svg = window.pngExportSvg;
                window.pngExportSvg = null;
                var img = new Image();
                img.onload = function() {
                    try {
                        var scale = Math.min(%s, %d / Math.max(img.width, img.height));
                        var canvas = document.createElement('canvas');
                        canvas.width = Math.round(img.width * scale);
                        canvas.height = Math.round(img.height * scale);
                        var context = canvas.getContext('2d');
                        // clipboard targets often show transparency as black
                        context.fillStyle = 'white';
                        context.fillRect(0, 0, canvas.width, canvas.height);
                        context.drawImage(img, 0, 0, canvas.width, canvas.height);
                        bridge.done(canvas.toDataURL('image/png'));
                    } catch (e) {
                        bridge.failed(String(e));
                    }
                };
                img.onerror = function() { bridge.failed('the SVG could not be loaded as an image'); };
                img.src = 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(svg);
            })();
            """.formatted(PNG_SCALE, PNG_MAX_SIDE);
    private static final String PNG_DATA_URL_PREFIX = "data:image/png;base64,";

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
    private Runnable onInjected = () -> { };
    private Supplier<String> fileNameSupplier = () -> null;
    private boolean panning;
    private double panStartScreenX;
    private double panStartScreenY;
    private double panStartScrollX;
    private double panStartScrollY;
    // held here: the page only keeps a weak reference to the Java objects it is given
    private final PngExportBridge pngExportBridge = new PngExportBridge();

    @FXML
    private void initialize() {
        webView.setContextMenuEnabled(false);
        webView.getEngine().loadContent(DEFAULT_HTML_SHELL);
        webView.getEngine().getLoadWorker().stateProperty().addListener((_, _, newValue) -> {
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
        webView.widthProperty().addListener((_, _, _) -> {
            if (fitToScreenToggleButton.isSelected()) {
                applyFitToScreen();
            }
        });
        webView.heightProperty().addListener((_, _, _) -> {
            if (fitToScreenToggleButton.isSelected()) {
                applyFitToScreen();
            }
        });
        webView.addEventFilter(ScrollEvent.SCROLL, this::onScroll);
        webView.addEventFilter(MouseEvent.MOUSE_PRESSED, this::onMousePressed);
        webView.addEventFilter(MouseEvent.MOUSE_DRAGGED, this::onMouseDragged);
        webView.addEventFilter(MouseEvent.MOUSE_RELEASED, this::onMouseReleased);

        svg.addListener((_, _, newValue) -> {
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

    /**
     * Called right after each diagram is injected into the page, e.g. to make its elements easier to point at: in the
     * same FX thread call, so that no other script sees the diagram before.
     */
    public void setOnInjected(Runnable onInjected) {
        this.onInjected = Objects.requireNonNull(onInjected);
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
            // kept in sync with the toggle, or a later untick wouldn't change the property nor notify listeners
            fitToScreen.set(fitToScreenValue);
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

    // set as a JS value rather than spliced into script source, where backslashes, backticks or "${" in
    // equipment names would be interpreted as JavaScript
    // on the classpath, JSObject resolves to JDK 25's jdk.jsobject, deprecated for removal, rather than JavaFX's own copy
    @SuppressWarnings("removal")
    private void inject(String content) {
        JSObject container = (JSObject) webView.getEngine().executeScript("document.getElementById('svgContainer')");
        container.setMember("innerHTML", content);
        onInjected.run();
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
        double oldZoom = webView.getZoom();
        double newZoom = oldZoom;
        if (deltaY < 0) {
            newZoom /= ZOOM_STEP_FACTOR;
        } else if (deltaY > 0) {
            newZoom *= ZOOM_STEP_FACTOR;
        }
        // keep the document point under the pointer at the same viewport position across the zoom change
        double pointerX = scrollOffset("left") + e.getX() / oldZoom;
        double pointerY = scrollOffset("top") + e.getY() / oldZoom;
        setZoom(newZoom, true);
        scrollTo(pointerX - e.getX() / newZoom, pointerY - e.getY() / newZoom);
        e.consume();
    }

    private void onMousePressed(MouseEvent e) {
        if (e.getButton() == MouseButton.MIDDLE) {
            panning = true;
            panStartScreenX = e.getScreenX();
            panStartScreenY = e.getScreenY();
            panStartScrollX = scrollOffset("left");
            panStartScrollY = scrollOffset("top");
            webView.setCursor(Cursor.MOVE);
            e.consume();
        }
    }

    private void onMouseDragged(MouseEvent e) {
        if (panning) {
            // relative to the drag start rather than incremental scrollBy() calls, so that the per-event
            // rounding of scrollTo() (see below) doesn't accumulate or swallow sub-pixel moves
            double zoomValue = webView.getZoom();
            scrollTo(panStartScrollX - (e.getScreenX() - panStartScreenX) / zoomValue,
                    panStartScrollY - (e.getScreenY() - panStartScreenY) / zoomValue);
            e.consume();
        }
    }

    // Scroll offsets are in document (CSS) pixels, which the WebView zoom scales. window.scrollX/Y
    // round the actual offset (a whole number of device pixels) to whole CSS pixels, whereas the root
    // element's bounding rect reports it exactly.
    private double scrollOffset(String side) {
        return -((Number) webView.getEngine().executeScript(
                "document.documentElement.getBoundingClientRect()." + side)).doubleValue();
    }

    // window.scrollTo() truncates its arguments to whole CSS pixels, then floors the result to whole
    // device pixels: pick the whole CSS pixel offset that lands closest to the target.
    private void scrollTo(double x, double y) {
        double zoomValue = webView.getZoom();
        webView.getEngine().executeScript("window.scrollTo(" + closestScrollArgument(x, zoomValue) + ", "
                + closestScrollArgument(y, zoomValue) + ")");
    }

    private static long closestScrollArgument(double target, double zoomValue) {
        long best = Math.round(target);
        for (long candidate = best - 1; candidate <= best + 1; candidate++) {
            if (Math.abs(Math.floor(candidate * zoomValue) / zoomValue - target)
                    < Math.abs(Math.floor(best * zoomValue) / zoomValue - target)) {
                best = candidate;
            }
        }
        return best;
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
        rasterize(png -> {
            ClipboardContent content = new ClipboardContent();
            content.putImage(new Image(new ByteArrayInputStream(png)));
            Clipboard.getSystemClipboard().setContent(content);
        });
    }

    @FXML
    private void onCopyDiagramSvg() {
        ClipboardContent content = new ClipboardContent();
        content.putString(svg.get());
        Clipboard.getSystemClipboard().setContent(content);
    }

    @FXML
    private void onSaveDiagramPng() {
        chooseDiagramFile("png").ifPresent(file -> rasterize(png -> {
            try {
                Files.write(file.toPath(), png);
            } catch (IOException e) {
                LOGGER.error(e.getMessage(), e);
            }
        }));
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

    /** Renders the whole diagram as a PNG, handed to {@code onPng} later on the FX thread. */
    // on the classpath, JSObject resolves to JDK 25's jdk.jsobject, deprecated for removal, rather than JavaFX's own copy
    @SuppressWarnings("removal")
    void rasterize(Consumer<byte[]> onPng) {
        pngExportBridge.onPng = onPng;
        JSObject window = (JSObject) webView.getEngine().executeScript("window");
        window.setMember("pngExport", pngExportBridge);
        // set as a JS value rather than spliced into script source, see inject
        window.setMember("pngExportSvg", svg.get());
        webView.getEngine().executeScript(RASTERIZE_SCRIPT);
    }

    /** Called back by {@link #RASTERIZE_SCRIPT}: public for the page to reach it. */
    public static final class PngExportBridge {
        private Consumer<byte[]> onPng;

        public void done(String dataUrl) {
            UnexpectedErrors.guard(() -> onPng.accept(Base64.getDecoder().decode(dataUrl.substring(PNG_DATA_URL_PREFIX.length()))));
        }

        // notified rather than only logged: the user would otherwise wait for a PNG that never comes
        public void failed(String message) {
            UnexpectedErrors.report(new IllegalStateException("Diagram PNG export failed: " + message));
        }
    }
}
