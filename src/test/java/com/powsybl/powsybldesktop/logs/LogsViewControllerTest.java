/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.logs;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.powsybl.powsybldesktop.testutil.AbstractHeadlessApplicationTest;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TableColumn;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testfx.util.WaitForAsyncUtils;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link LogsModel} attaches a real, non-detachable Logback appender to the root logger on
 * construction. One instance is shared for this whole test class (via the static field, which
 * is initialized exactly once regardless of how many test methods run) to avoid accumulating
 * appenders on the root logger across test methods.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class LogsViewControllerTest extends AbstractHeadlessApplicationTest {

    private static final LogsModel LOGS_MODEL = new LogsModel();
    private static final Logger LOGGER = LoggerFactory.getLogger(LogsViewControllerTest.class);

    private LogsViewController controller;

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(
                "/com/powsybl/powsybldesktop/logs/logs-view.fxml"), Messages.bundle());
        Parent root = loader.load();
        controller = loader.getController();
        controller.setLogsModel(LOGS_MODEL);

        stage.setScene(new Scene(root));
        stage.show();
    }

    @Test
    void emittingALogAddsARowVisibleThroughTheTable() throws TimeoutException {
        int sizeBefore = controller.tableView.getItems().size();
        LOGGER.warn("row test message");
        ILoggingEvent event = waitForNextLogRow(sizeBefore);
        assertEquals("row test message", event.getFormattedMessage());
    }

    @Test
    void levelAndMessageCellValueFactoriesFormatCorrectly() throws TimeoutException {
        int sizeBefore = controller.tableView.getItems().size();
        LOGGER.error("boom");
        ILoggingEvent event = waitForNextLogRow(sizeBefore);

        String level = controller.levelColumn.getCellValueFactory()
                .call(new TableColumn.CellDataFeatures<>(controller.tableView, controller.levelColumn, event))
                .getValue();
        String message = controller.messageColumn.getCellValueFactory()
                .call(new TableColumn.CellDataFeatures<>(controller.tableView, controller.messageColumn, event))
                .getValue();

        assertEquals("ERROR", level);
        assertEquals("boom", message);
    }

    // LogsModel flushes buffered log events onto the observable list once per second (see
    // LogsModel.flushTimeline) rather than immediately on append, and appends at the end (not the
    // front) of the shared, cross-test LOGS_MODEL list.
    private ILoggingEvent waitForNextLogRow(int sizeBefore) throws TimeoutException {
        WaitForAsyncUtils.waitFor(2, TimeUnit.SECONDS, () -> controller.tableView.getItems().size() > sizeBefore);
        return controller.tableView.getItems().getLast();
    }
}
