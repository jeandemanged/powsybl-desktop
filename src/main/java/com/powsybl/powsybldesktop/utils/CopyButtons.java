/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.utils;

import javafx.geometry.Side;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.function.Supplier;

/**
 * Icon buttons copying text, e.g. an id or a name, to the clipboard in a single click. They edit nothing, so they stay
 * enabled while the network is busy.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class CopyButtons {

    /**
     * The style class of a button available while the network is busy, e.g. in a table cell where EditLockTest
     * otherwise takes a button for an editor.
     */
    public static final String NON_EDITING_BUTTON = "non-editing-button";

    private CopyButtons() {
    }

    /**
     * Copies {@code text}.
     */
    public static Button copy(Supplier<String> text) {
        Button button = new Button();
        decorate(button, "desktop.common.action.copyToClipboard");
        button.setOnAction(_ -> copyToClipboard(text.get()));
        return button;
    }

    /**
     * Copies {@code id}, or, if {@code name} isn't null, shows a menu copying either; the menu is also the button's
     * context menu.
     */
    public static Button copyIdOrName(Supplier<String> id, Supplier<String> name) {
        MenuItem copyId = new MenuItem(Messages.get("desktop.common.action.copyId"));
        copyId.setOnAction(_ -> copyToClipboard(id.get()));
        MenuItem copyName = new MenuItem(Messages.get("desktop.common.action.copyName"));
        copyName.setOnAction(_ -> copyToClipboard(name.get()));
        ContextMenu menu = new ContextMenu(copyId, copyName);
        menu.setOnShowing(_ -> copyName.setDisable(name.get() == null));
        Button button = new Button();
        decorate(button, "desktop.common.action.copyToClipboard");
        button.setContextMenu(menu);
        button.setOnAction(_ -> {
            if (name.get() == null) {
                copyToClipboard(id.get());
            } else {
                menu.show(button, Side.BOTTOM, 0, 0);
            }
        });
        return button;
    }

    public static void copyToClipboard(String text) {
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
    }

    private static void decorate(Button button, String tooltipKey) {
        button.setGraphic(new FontIcon("mdi2c-content-copy"));
        button.getStyleClass().addAll("icon-button", NON_EDITING_BUTTON);
        button.setTooltip(new Tooltip(Messages.get(tooltipKey)));
    }
}
