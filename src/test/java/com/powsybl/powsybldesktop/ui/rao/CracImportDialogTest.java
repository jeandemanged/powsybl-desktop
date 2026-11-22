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
import com.powsybl.openrao.data.crac.api.parameters.JsonCracCreationParameters;
import com.powsybl.powsybldesktop.rao.CracImportParameters;
import com.powsybl.powsybldesktop.ui.testutil.AbstractHeadlessApplicationTest;
import javafx.scene.Scene;
import javafx.scene.control.ButtonType;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
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

    @TempDir
    Path tempDir;

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

    @Test
    void jsonNeedsNothing() {
        CracImportDialog dialog = dialog(CracImportParameters.JSON, new CracCreationParameters());

        assertFalse(okDisabled(dialog));
        assertFalse(dialog.problemLabel.isVisible());
        interact(() -> dialog.sideChoiceBox.setValue(CracCreationParameters.MonitoredLineSide.MONITOR_LINES_ON_SIDE_TWO));
        CracCreationParameters result = dialog.getResultConverter().call(ButtonType.OK);
        assertEquals(Set.of(TwoSides.TWO), result.getDefaultMonitoredSides());
        assertNull(dialog.getResultConverter().call(ButtonType.CANCEL));
    }

    @Test
    void ncNeedsATimestampAndARegion() {
        CracImportDialog dialog = dialog(CracImportParameters.NC, new CracCreationParameters());
        assertTrue(okDisabled(dialog));
        assertTrue(dialog.problemLabel.isVisible());

        interact(() -> {
            dialog.datePicker.setValue(LocalDate.of(2026, 11, 22));
            dialog.timeField.setText("10:30");
        });
        assertTrue(okDisabled(dialog), "no region yet");
        interact(() -> dialog.timeField.setText("25:00"));
        interact(() -> dialog.regionChoiceBox.setValue(CapacityCalculationRegion.SOUTH_WESTERN_EUROPE));
        assertTrue(okDisabled(dialog), "an invalid time");
        interact(() -> dialog.timeField.setText("10:30"));
        assertFalse(okDisabled(dialog));

        CracCreationParameters result = dialog.getResultConverter().call(ButtonType.OK);
        assertEquals(OffsetDateTime.parse("2026-11-22T10:30Z"), CracImportParameters.timestamp(result, CracImportParameters.NC));
        assertEquals(CapacityCalculationRegion.SOUTH_WESTERN_EUROPE, CracImportParameters.capacityCalculationRegion(result));
    }

    @Test
    void flowBasedConstraintHasIcsCosts() {
        CracImportDialog dialog = dialog(CracImportParameters.FLOW_BASED_CONSTRAINT, new CracCreationParameters());
        interact(() -> {
            dialog.datePicker.setValue(LocalDate.of(2026, 11, 22));
            dialog.timeField.setText("00:30");
            dialog.icsCostUpField.setText("abc");
        });
        assertTrue(okDisabled(dialog));

        interact(() -> dialog.icsCostUpField.setText("12.5"));
        assertFalse(okDisabled(dialog));
        assertEquals(12.5, CracImportParameters.flowBasedConstraint(dialog.getResultConverter().call(ButtonType.OK)).getIcsCostUp());
    }

    // given values, those of the last import of the format, are shown, in UTC, and not changed until confirmed
    @Test
    void showsTheGivenParametersAndThoseOfAFile() {
        CracCreationParameters given = new CracCreationParameters();
        CracImportParameters.setTimestamp(given, CracImportParameters.CIM, OffsetDateTime.parse("2026-11-22T12:00+02:00"));
        CracImportDialog dialog = dialog(CracImportParameters.CIM, given);
        assertEquals(LocalDate.of(2026, 11, 22), dialog.datePicker.getValue());
        assertEquals("10:00", dialog.timeField.getText());

        CracCreationParameters file = new CracCreationParameters();
        CracImportParameters.setTimestamp(file, CracImportParameters.CIM, OffsetDateTime.parse("2027-01-01T05:15Z"));
        file.setDefaultMonitoredLineSide(CracCreationParameters.MonitoredLineSide.MONITOR_LINES_ON_SIDE_ONE);
        Path path = tempDir.resolve("parameters.json");
        JsonCracCreationParameters.write(file, path);
        interact(() -> dialog.loadParametersFile(path));

        assertEquals("parameters.json", dialog.parametersFileLabel.getText());
        assertEquals(LocalDate.of(2027, 1, 1), dialog.datePicker.getValue());
        assertEquals("05:15", dialog.timeField.getText());
        assertEquals(CracCreationParameters.MonitoredLineSide.MONITOR_LINES_ON_SIDE_ONE, dialog.sideChoiceBox.getValue());
        assertEquals(OffsetDateTime.parse("2026-11-22T12:00+02:00"), CracImportParameters.timestamp(given, CracImportParameters.CIM));
    }
}
