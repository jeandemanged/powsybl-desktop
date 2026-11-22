/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.ui.rao;

import com.powsybl.powsybldesktop.utils.Messages;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.Region;

/**
 * The fields of the CRAC view's remedial action forms: their labels, and their text as numbers.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
final class FormFields {

    private FormFields() {
    }

    static Label boldLabel(String key) {
        Label label = new Label(Messages.get(key));
        label.getStyleClass().add("crac-details-label");
        return label;
    }

    // wrapped, and out of the layout while hidden
    static void wrap(Label label) {
        label.setWrapText(true);
        label.setMinHeight(Region.USE_PREF_SIZE);
        label.managedProperty().bind(label.visibleProperty());
    }

    static String text(Number value) {
        return value == null ? "" : value instanceof Double d && d == Math.rint(d) && Math.abs(d) < 1e15 ? Long.toString(d.longValue()) : value.toString();
    }

    static String text(String value) {
        return value == null ? "" : value;
    }

    static String trimmed(TextField field) {
        String text = field.getText().trim();
        return text.isEmpty() ? null : text;
    }

    /**
     * The field's integer, none when blank.
     *
     * @param key the key of the field's label, told when it is no integer
     */
    static Integer integer(TextField field, String key) {
        String text = trimmed(field);
        try {
            return text == null ? null : Integer.valueOf(text);
        } catch (NumberFormatException _) {
            throw new IllegalArgumentException(Messages.get("crac.cnec.notANumber", Messages.get(key), text));
        }
    }

    /**
     * The field's finite number, none when blank.
     *
     * @param key the key of the field's label, told when it is no number
     */
    static Double number(TextField field, String key) {
        String text = trimmed(field);
        if (text == null) {
            return null;
        }
        try {
            double value = Double.parseDouble(text);
            if (!Double.isFinite(value)) {
                throw new NumberFormatException(text);
            }
            return value;
        } catch (NumberFormatException _) {
            throw new IllegalArgumentException(Messages.get("crac.cnec.notANumber", Messages.get(key), text));
        }
    }
}
