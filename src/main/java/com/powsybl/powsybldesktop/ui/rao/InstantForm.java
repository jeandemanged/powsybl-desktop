/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.ui.rao;

import com.powsybl.openrao.data.crac.api.Crac;
import com.powsybl.openrao.data.crac.api.Instant;
import com.powsybl.powsybldesktop.rao.UsageLimitEdits;
import com.powsybl.powsybldesktop.rao.UsageLimitEdits.TsoLimits;
import com.powsybl.powsybldesktop.rao.UsageLimitEdits.UsageLimitsSpec;
import com.powsybl.powsybldesktop.ui.controls.RowsTableEditor;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * An instant of the CRAC: its id and kind, and the remedial action usage limits at it, editable
 * ({@link UsageLimitEdits#set}), applied at once (Enter or focus loss for the maximum number of remedial actions, each
 * edit for the TSOs' limits) and told {@code onEdited}; a refused change is told below the field, which is reverted
 * (the TSOs' limits kept as typed, to be fixed). The outage instant has no usage limits.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
final class InstantForm {

    private final Crac crac;
    private final Instant instant;
    private final Runnable onEdited;
    private boolean refreshing;
    private final VBox root;
    final TextField maxRaField = new TextField();
    final RowsTableEditor tsoLimitsEditor = new RowsTableEditor(List.of(Messages.get("crac.usageLimits.column.tso"),
            Messages.get("crac.usageLimits.column.maxTopo"), Messages.get("crac.usageLimits.column.maxPst"),
            Messages.get("crac.usageLimits.column.maxRa"), Messages.get("crac.usageLimits.column.maxElementary")));
    final Label errorLabel = new Label();

    InstantForm(Crac crac, Instant instant, Runnable onEdited) {
        this.crac = crac;
        this.instant = instant;
        this.onEdited = onEdited;

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(6);
        grid.addRow(0, FormFields.boldLabel("crac.details.id"), new Label(instant.getId()));
        grid.addRow(1, FormFields.boldLabel("crac.instant.kind"),
                new Label(Messages.get("crac.instant.kind." + instant.getKind().name().toLowerCase(Locale.ROOT))));
        root = new VBox(8, grid);
        if (!UsageLimitEdits.hasUsageLimits(instant)) {
            Label none = new Label(Messages.get("crac.usageLimits.outage"));
            FormFields.wrap(none);
            root.getChildren().add(none);
            return;
        }
        maxRaField.setOnAction(_ -> apply());
        maxRaField.focusedProperty().addListener((_, wasFocused, focused) -> {
            if (Boolean.TRUE.equals(wasFocused) && !focused) {
                apply();
            }
        });
        tsoLimitsEditor.setOnEdit(rows -> {
            List<TsoLimits> perTso = UsageLimitEdits.parseTsoRows(rows);
            if (!refreshing) {
                set(new UsageLimitsSpec(UsageLimitEdits.of(crac, instant).maxRa(), perTso));
            }
        });
        Label help = new Label(Messages.get("crac.usageLimits.help"));
        FormFields.wrap(help);
        FormFields.wrap(errorLabel);
        errorLabel.getStyleClass().add("alert-label");
        errorLabel.setVisible(false);
        GridPane limits = new GridPane();
        limits.setHgap(10);
        limits.addRow(0, FormFields.boldLabel("crac.usageLimits.maxRa"), maxRaField);
        root.getChildren().addAll(FormFields.boldLabel("crac.usageLimits"), limits, errorLabel, FormFields.boldLabel("crac.usageLimits.perTso"),
                tsoLimitsEditor, help);
        refresh();
    }

    Node root() {
        return root;
    }

    Instant instant() {
        return instant;
    }

    private void refresh() {
        refreshing = true;
        try {
            UsageLimitsSpec limits = UsageLimitEdits.of(crac, instant);
            maxRaField.setText(FormFields.text(limits.maxRa()));
            tsoLimitsEditor.setRows(UsageLimitEdits.tsoRows(limits.perTso()));
        } finally {
            refreshing = false;
        }
    }

    // the field's change: the TSOs' limits are the CRAC's, those of the table being applied by their own edits
    private void apply() {
        if (refreshing) {
            return;
        }
        try {
            UsageLimitsSpec limits = UsageLimitEdits.of(crac, instant);
            Integer maxRa = FormFields.integer(maxRaField, "crac.usageLimits.maxRa");
            if (!Objects.equals(maxRa, limits.maxRa())) {
                set(new UsageLimitsSpec(maxRa, limits.perTso()));
            }
            errorLabel.setVisible(false);
        } catch (IllegalArgumentException e) {
            errorLabel.setText(e.getMessage());
            errorLabel.setVisible(true);
            refresh();
        }
    }

    // throws when refused, the limits as they were
    private void set(UsageLimitsSpec limits) {
        UsageLimitEdits.set(crac, instant, limits);
        errorLabel.setVisible(false);
        onEdited.run();
    }
}
