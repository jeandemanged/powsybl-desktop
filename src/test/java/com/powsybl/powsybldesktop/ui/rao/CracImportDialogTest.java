/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.ui.rao;

import com.powsybl.entsoe.utils.CapacityCalculationRegion;
import com.powsybl.iidm.network.TwoSides;
import com.powsybl.openrao.data.crac.api.parameters.CracCreationParameters;
import com.powsybl.powsybldesktop.rao.CracImportParameters;
import com.powsybl.powsybldesktop.ui.testutil.AbstractHeadlessApplicationTest;
import javafx.scene.Scene;
import javafx.scene.control.ButtonType;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class CracImportDialogTest extends AbstractHeadlessApplicationTest {

    private Stage stage;

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        stage.setScene(new Scene(new StackPane(), 200, 200));
        stage.show();
    }

    // built, not shown: its fields are driven and its result read directly
    private CracImportDialog dialog(String format, CracCreationParameters parameters) {
        AtomicReference<CracImportDialog> dialog = new AtomicReference<>();
        interact(() -> dialog.set(new CracImportDialog(stage, "file", format, parameters)));
        return dialog.get();
    }

    private static boolean okDisabled(CracImportDialog dialog) {
        return dialog.getDialogPane().lookupButton(ButtonType.OK).isDisabled();
    }

    // the other parameters, the CRAC Creation tab's, are kept in the result, a copy
    @Test
    void ncNeedsATimestampAndARegion() {
        CracCreationParameters parameters = new CracCreationParameters();
        parameters.setDefaultMonitoredLineSide(CracCreationParameters.MonitoredLineSide.MONITOR_LINES_ON_SIDE_TWO);
        CracImportDialog dialog = dialog(CracImportParameters.NC, parameters);
        assertTrue(okDisabled(dialog));
        assertTrue(dialog.problemLabel.isVisible());

        interact(() -> {
            dialog.datePicker.setValue(LocalDate.of(2026, 11, 22));
            dialog.timeField.setText("10:30");
        });
        assertTrue(okDisabled(dialog), "no region yet");
        interact(() -> {
            dialog.timeField.setText("25:00");
            dialog.regionChoiceBox.setValue(CapacityCalculationRegion.SOUTH_WESTERN_EUROPE);
        });
        assertTrue(okDisabled(dialog), "an invalid time");
        interact(() -> dialog.timeField.setText("10:30"));
        assertFalse(okDisabled(dialog));

        CracCreationParameters result = dialog.getResultConverter().call(ButtonType.OK);
        assertEquals(OffsetDateTime.parse("2026-11-22T10:30Z"), CracImportParameters.timestamp(result, CracImportParameters.NC));
        assertEquals(CapacityCalculationRegion.SOUTH_WESTERN_EUROPE, CracImportParameters.capacityCalculationRegion(result));
        assertEquals(Set.of(TwoSides.TWO), result.getDefaultMonitoredSides());
        assertNull(CracImportParameters.timestamp(parameters, CracImportParameters.NC));
        assertNull(dialog.getResultConverter().call(ButtonType.CANCEL));
    }

    // in UTC
    @Test
    void prefilledWithTheGivenTimestampAndRegion() {
        CracCreationParameters parameters = new CracCreationParameters();
        CracImportParameters.setTimestamp(parameters, CracImportParameters.NC, OffsetDateTime.parse("2026-11-22T12:00+02:00"));
        CracImportParameters.setCapacityCalculationRegion(parameters, CapacityCalculationRegion.CORE);

        CracImportDialog dialog = dialog(CracImportParameters.NC, parameters);

        assertEquals(LocalDate.of(2026, 11, 22), dialog.datePicker.getValue());
        assertEquals("10:00", dialog.timeField.getText());
        assertEquals(CapacityCalculationRegion.CORE, dialog.regionChoiceBox.getValue());
        assertFalse(okDisabled(dialog));
    }

    @Test
    void flowBasedConstraintNeedsATimestampOnly() {
        CracImportDialog dialog = dialog(CracImportParameters.FLOW_BASED_CONSTRAINT, new CracCreationParameters());
        assertTrue(okDisabled(dialog));

        interact(() -> {
            dialog.datePicker.setValue(LocalDate.of(2026, 11, 22));
            dialog.timeField.setText("00:30");
        });

        assertFalse(okDisabled(dialog));
        assertEquals(OffsetDateTime.parse("2026-11-22T00:30Z"),
                CracImportParameters.timestamp(dialog.getResultConverter().call(ButtonType.OK), CracImportParameters.FLOW_BASED_CONSTRAINT));
    }
}
