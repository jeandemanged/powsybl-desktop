/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.report;

import com.powsybl.commons.report.ReportConstants;
import com.powsybl.commons.report.ReportNode;
import com.powsybl.commons.report.TypedValue;
import javafx.geometry.Pos;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TreeCell;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class ReportNodeTreeCell extends TreeCell<ReportNode> {

    static final List<String> SEVERITIES = List.of("TRACE", "DEBUG", "INFO", "DETAIL", "WARN", "ERROR");

    private static final List<String> SEVERITY_STYLE_CLASSES = SEVERITIES.stream()
            .map(severity -> "severity-" + severity.toLowerCase(Locale.ROOT))
            .toList();

    // Severity is conveyed only by color, on a fixed-size marker, so messages stay aligned regardless of depth.
    private final Region severityMarker = new Region();
    private final Label messageLabel = new Label();
    private final HBox container = new HBox(8, severityMarker, messageLabel);
    private final ContextMenu contextMenu = new ContextMenu();

    public ReportNodeTreeCell() {
        severityMarker.getStyleClass().add("severity-marker");
        container.setAlignment(Pos.CENTER_LEFT);
        setText(null);
        setOnContextMenuRequested(event -> {
            if (!isEmpty() && getItem() != null) {
                contextMenu.hide();
                fillContextMenu(getItem());
                contextMenu.show(this, event.getScreenX(), event.getScreenY());
            }
        });
    }

    @Override
    protected void updateItem(ReportNode item, boolean empty) {
        super.updateItem(item, empty);

        if (empty || item == null) {
            setGraphic(null);
            return;
        }

        String severityStyleClass = "severity-" + severityOf(item).toLowerCase(Locale.ROOT);
        severityMarker.getStyleClass().removeAll(SEVERITY_STYLE_CLASSES);
        severityMarker.getStyleClass().add(severityStyleClass);
        messageLabel.setText(item.getMessage());
        setGraphic(container);
    }

    static String severityOf(ReportNode node) {
        return node.getValue(ReportConstants.SEVERITY_KEY)
                .map(typedValue -> String.valueOf(typedValue.getValue()))
                .orElse("INFO");
    }

    private void fillContextMenu(ReportNode item) {
        contextMenu.getItems().clear();

        MenuItem copyMessage = new MenuItem("Copy message");
        copyMessage.setOnAction(e -> copyToClipboard(item.getMessage()));
        contextMenu.getItems().add(copyMessage);

        Map<String, TypedValue> values = item.getValues();
        if (!values.isEmpty()) {
            contextMenu.getItems().add(new SeparatorMenuItem());
            for (Map.Entry<String, TypedValue> entry : values.entrySet()) {
                String key = entry.getKey();
                String value = valueAsString(entry.getValue());
                MenuItem copyValue = new MenuItem("Copy " + key + " = " + value);
                copyValue.setOnAction(e -> copyToClipboard(value));
                contextMenu.getItems().add(copyValue);
            }
        }
    }

    private static String valueAsString(TypedValue typedValue) {
        return String.valueOf(typedValue.getValue());
    }

    private static void copyToClipboard(String text) {
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
    }
}
