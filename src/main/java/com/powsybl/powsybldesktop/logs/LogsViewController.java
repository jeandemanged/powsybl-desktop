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
import com.powsybl.powsybldesktop.utils.AbstractDisposableController;
import com.powsybl.powsybldesktop.utils.TableAutoFitLimiter;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.SignStyle;
import java.util.Locale;

import static java.time.temporal.ChronoField.*;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class LogsViewController extends AbstractDisposableController {
    @FXML
    public TableColumn<ILoggingEvent, String> timeColumn;
    @FXML
    public TableColumn<ILoggingEvent, String> levelColumn;
    @FXML
    public TableColumn<ILoggingEvent, String> messageColumn;
    @FXML
    public TableView<ILoggingEvent> tableView;

    private LogsModel logsModel;

    private static final DateTimeFormatter DATE_TIME_FORMATTER = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendValue(YEAR, 4, 10, SignStyle.EXCEEDS_PAD)
            .appendLiteral('-')
            .appendValue(MONTH_OF_YEAR, 2)
            .appendLiteral('-')
            .appendValue(DAY_OF_MONTH, 2)
            .appendLiteral(' ')
            .appendValue(HOUR_OF_DAY, 2)
            .appendLiteral(':')
            .appendValue(MINUTE_OF_HOUR, 2)
            .appendLiteral(':')
            .appendValue(SECOND_OF_MINUTE, 2)
            .parseLenient()
            .appendLiteral(' ')
            .appendOffset("+HH:MM:ss", "+00:00")
            .toFormatter(Locale.getDefault());

    public void setLogsModel(LogsModel logsModel) {
        this.logsModel = logsModel;
        tableView.setItems(logsModel.getLogs());
        TableAutoFitLimiter.install(tableView);
        listenerManager.listen(logsModel.getLogs(), change -> Platform.runLater(this::scrollToBottom));
        scrollToBottom();
        timeColumn.setCellValueFactory(cellData -> {
            Instant instant = Instant.ofEpochMilli(cellData.getValue().getTimeStamp());

            return new SimpleStringProperty(DATE_TIME_FORMATTER.format(
                    ZonedDateTime.ofInstant(instant, ZoneId.systemDefault())
            ));
        });
        levelColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getLevel().toString()));
        messageColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getFormattedMessage()));
        tableView.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(ILoggingEvent item, boolean empty) {
                super.updateItem(item, empty);

                getStyleClass().removeAll("row-warn", "row-error");

                if (item == null || empty) {
                    return;
                }

                String levelStr = item.getLevel().levelStr;
                if (Level.WARN.levelStr.equals(levelStr)) {
                    getStyleClass().add("row-warn");
                } else if (Level.ERROR.levelStr.equals(levelStr)) {
                    getStyleClass().add("row-error");
                }
            }
        });
    }

    @FXML
    private void onClearAll() {
        logsModel.clearLogs();
    }

    private void scrollToBottom() {
        if (!tableView.getItems().isEmpty()) {
            tableView.scrollTo(tableView.getItems().size() - 1);
        }
    }
}
