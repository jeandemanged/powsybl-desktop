/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.ui.controls;

import javafx.beans.InvalidationListener;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;

/**
 * Menu entries whose content is a label (e.g. to drag, or with a tooltip), clickable on their whole row. JavaFX fires
 * a {@link CustomMenuItem} only on a click on its content, which it lays out at its preferred width, left aligned,
 * inside the row's padding ({@code ContextMenuContent}, JavaFX 27): a click elsewhere in the row, highlighted all the
 * same, would do nothing (nor would a click moving a few pixels off the label, then taken for a drag to open). Here
 * the row has no padding, the label carrying it instead (inline styles: they prevail over modena's, with no stylesheet
 * to load in every scene showing a menu), and the label is as wide as the row.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class FullRowMenuItems {

    private FullRowMenuItems() {
    }

    /**
     * An entry showing {@code text} and {@code graphic} (or none); its content is the label, e.g. to add a tooltip.
     */
    public static CustomMenuItem create(String text, Node graphic) {
        return new CustomMenuItem(new RowLabel(text, graphic));
    }

    /**
     * The label of an entry made by {@link #create}.
     */
    public static Label label(CustomMenuItem item) {
        return (Label) item.getContent();
    }

    // as wide as its natural width or its row's, the menu's: the widest entry's, so the menu doesn't grow from it
    private static final class RowLabel extends Label {

        // modena's .menu-item padding (4 5 4 5), plus its .menu-item > .label right padding (0.5em)
        private static final String PADDING = "-fx-padding: 0.333333em 0.91777em 0.333333em 0.41777em;";

        // a new width of the row changes this preferred width, cached until a layout is requested
        private final InvalidationListener rowResized = _ -> requestLayout();

        private RowLabel(String text, Node graphic) {
            super(text, graphic);
            setMnemonicParsing(false);
            setStyle(PADDING);
            parentProperty().addListener((_, oldParent, newParent) -> {
                if (oldParent instanceof Region row) {
                    row.widthProperty().removeListener(rowResized);
                }
                if (newParent instanceof Region row) {
                    row.setStyle("-fx-padding: 0;");
                    row.widthProperty().addListener(rowResized);
                }
            });
        }

        @Override
        protected double computePrefWidth(double height) {
            double natural = super.computePrefWidth(height);
            Parent parent = getParent();
            if (parent instanceof Region row) {
                return Math.max(natural, row.getWidth() - row.snappedLeftInset() - row.snappedRightInset());
            }
            return natural;
        }
    }
}
