/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.logs;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.powsybl.powsybldesktop.testutil.AbstractHeadlessApplicationTest;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TableColumn;
import javafx.scene.input.Clipboard;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testfx.util.WaitForAsyncUtils;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link LogsModel} attaches a real, non-detachable Logback appender to the root logger on
 * construction. One instance is shared for this whole test class (via the static field, which
 * is initialized exactly once regardless of how many test methods run) to avoid accumulating
 * appenders on the root logger across test methods. It is created lazily in {@link #start}
 * rather than in a static initializer because its constructor starts a {@code Timeline}, which
 * needs the JavaFX toolkit, not yet running at class initialization when this class runs alone.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class LogsViewControllerTest extends AbstractHeadlessApplicationTest {

    private static LogsModel logsModel;
    private static final Logger LOGGER = LoggerFactory.getLogger(LogsViewControllerTest.class);

    private LogsViewController controller;

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(
                "/com/powsybl/powsybldesktop/logs/logs-view.fxml"), Messages.bundle());
        Parent root = loader.load();
        controller = loader.getController();
        if (logsModel == null) {
            logsModel = new LogsModel();
        }
        controller.setLogsModel(logsModel);

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

    @Test
    void newestLogIsOnTopByDefaultAndAtTheBottomWhenToggled() throws TimeoutException {
        int sizeBefore = controller.tableView.getItems().size();
        LOGGER.info("older");
        LOGGER.info("newer");
        WaitForAsyncUtils.waitFor(2, TimeUnit.SECONDS, () -> controller.tableView.getItems().size() >= sizeBefore + 2);

        assertEquals("newer", controller.tableView.getItems().getFirst().getFormattedMessage());

        clickOn(controller.newestOnBottomToggleButton);
        assertEquals("newer", controller.tableView.getItems().getLast().getFormattedMessage());
        assertEquals("older", controller.tableView.getItems().get(controller.tableView.getItems().size() - 2).getFormattedMessage());

        // clicking the selected order keeps it selected
        clickOn(controller.newestOnBottomToggleButton);
        assertTrue(controller.newestOnBottomToggleButton.isSelected());

        clickOn(controller.newestOnTopToggleButton);
        assertEquals("newer", controller.tableView.getItems().getFirst().getFormattedMessage());
    }

    @Test
    void levelTogglesShowSelectedLevelAndAbove() throws TimeoutException {
        int sizeBefore = controller.tableView.getItems().size();
        LOGGER.info("filtered info");
        LOGGER.warn("filtered warn");
        LOGGER.error("filtered error");
        WaitForAsyncUtils.waitFor(2, TimeUnit.SECONDS, () -> controller.tableView.getItems().size() >= sizeBefore + 3);
        assertTrue(isShown("filtered info") && isShown("filtered warn") && isShown("filtered error"));

        clickOn(controller.warnToggleButton);
        assertTrue(!isShown("filtered info") && isShown("filtered warn") && isShown("filtered error"));

        clickOn(controller.errorToggleButton);
        assertTrue(!isShown("filtered info") && !isShown("filtered warn") && isShown("filtered error"));
        assertTrue(controller.tableView.getItems().stream().allMatch(e -> e.getLevel() == Level.ERROR));

        // clicking the selected level keeps it selected
        clickOn(controller.errorToggleButton);
        assertTrue(controller.errorToggleButton.isSelected());

        clickOn(controller.infoToggleButton);
        assertTrue(isShown("filtered info") && isShown("filtered warn") && isShown("filtered error"));
    }

    private boolean isShown(String message) {
        return controller.tableView.getItems().stream().anyMatch(e -> message.equals(e.getFormattedMessage()));
    }

    @Test
    void contextMenuCopiesMessageToClipboard() throws TimeoutException {
        int sizeBefore = controller.tableView.getItems().size();
        LOGGER.info("copied message");
        waitForNextLogRow(sizeBefore);
        WaitForAsyncUtils.waitForFxEvents();

        Node firstRow = lookup(".table-row-cell").nth(0).query();
        rightClickOn(firstRow);
        clickOn(Messages.get("logs.contextMenu.copyMessage"));

        assertEquals("copied message", WaitForAsyncUtils.waitForAsyncFx(2000, () -> Clipboard.getSystemClipboard().getString()));
    }

    @Test
    void multiLineMessageGrowsRowAndOnlyTruncatedMessageHasTooltip() throws TimeoutException {
        int sizeBefore = controller.tableView.getItems().size();
        LOGGER.info("single line");
        LOGGER.info("line one\nline two\nline three");
        LOGGER.info("too long ".repeat(200));
        WaitForAsyncUtils.waitFor(2, TimeUnit.SECONDS, () -> controller.tableView.getItems().size() >= sizeBefore + 3);
        WaitForAsyncUtils.waitForFxEvents();

        LogsViewController.MessageCell singleLine = messageCell("single line");
        LogsViewController.MessageCell multiLine = messageCell("line one\nline two\nline three");
        LogsViewController.MessageCell tooLong = messageCell("too long ".repeat(200));

        assertTrue(multiLine.getHeight() > 2 * singleLine.getHeight());
        assertEquals(singleLine.getHeight(), tooLong.getHeight());
        assertNull(singleLine.getTooltip());
        assertNull(multiLine.getTooltip());
        assertNotNull(tooLong.getTooltip());
        assertEquals("too long ".repeat(200), tooLong.getTooltip().getText());
    }

    private LogsViewController.MessageCell messageCell(String message) {
        return lookup(".table-cell").queryAll().stream()
                .filter(LogsViewController.MessageCell.class::isInstance)
                .map(LogsViewController.MessageCell.class::cast)
                .filter(cell -> message.equals(cell.getItem()))
                .findFirst()
                .orElseThrow();
    }

    // LogsModel flushes buffered log events onto the observable list once per second (see
    // LogsModel.flushTimeline) rather than immediately on append; the view shows newest first by default.
    private ILoggingEvent waitForNextLogRow(int sizeBefore) throws TimeoutException {
        WaitForAsyncUtils.waitFor(2, TimeUnit.SECONDS, () -> controller.tableView.getItems().size() > sizeBefore);
        return controller.tableView.getItems().getFirst();
    }
}
