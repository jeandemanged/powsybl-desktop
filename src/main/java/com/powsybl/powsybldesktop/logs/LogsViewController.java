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
import com.powsybl.powsybldesktop.utils.Messages;
import com.powsybl.powsybldesktop.utils.TableAutoFitLimiter;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.text.Text;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.SignStyle;
import java.util.Comparator;
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
    @FXML
    public ToggleGroup levelToggleGroup;
    @FXML
    public ToggleButton infoToggleButton;
    @FXML
    public ToggleButton warnToggleButton;
    @FXML
    public ToggleButton errorToggleButton;
    @FXML
    public ToggleGroup orderToggleGroup;
    @FXML
    public ToggleButton newestOnTopToggleButton;
    @FXML
    public ToggleButton newestOnBottomToggleButton;

    private LogsModel logsModel;

    private static final Comparator<ILoggingEvent> NEWEST_FIRST =
            Comparator.comparingLong(ILoggingEvent::getSequenceNumber).reversed();

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
        FilteredList<ILoggingEvent> filteredLogs = new FilteredList<>(logsModel.getLogs(), this::isLevelShown);
        SortedList<ILoggingEvent> sortedLogs = new SortedList<>(filteredLogs, NEWEST_FIRST);
        tableView.setItems(sortedLogs);
        TableAutoFitLimiter.install(tableView);

        keepOneSelected(levelToggleGroup);
        levelToggleGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle != null) {
                filteredLogs.setPredicate(this::isLevelShown);
                scrollToNewest();
            }
        });
        keepOneSelected(orderToggleGroup);
        orderToggleGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle != null) {
                sortedLogs.setComparator(newToggle == newestOnTopToggleButton ? NEWEST_FIRST : null);
                scrollToNewest();
            }
        });

        listenerManager.listen(logsModel.getLogs(), change -> Platform.runLater(this::scrollToNewest));
        scrollToNewest();
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
        messageColumn.setCellFactory(column -> new MessageCell());
        tableView.setRowFactory(tv -> new LogRow());
    }

    // clicking the selected toggle would otherwise leave none selected
    private static void keepOneSelected(ToggleGroup group) {
        group.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle == null) {
                oldToggle.setSelected(true);
            }
        });
    }

    private boolean isLevelShown(ILoggingEvent event) {
        Level minLevel;
        if (errorToggleButton.isSelected()) {
            minLevel = Level.ERROR;
        } else if (warnToggleButton.isSelected()) {
            minLevel = Level.WARN;
        } else {
            minLevel = Level.INFO;
        }
        return event.getLevel().isGreaterOrEqual(minLevel);
    }

    @FXML
    private void onClearAll() {
        logsModel.clearLogs();
    }

    private void scrollToNewest() {
        if (tableView.getItems().isEmpty()) {
            return;
        }
        tableView.scrollTo(newestOnTopToggleButton.isSelected() ? 0 : tableView.getItems().size() - 1);
    }

    private static final class LogRow extends TableRow<ILoggingEvent> {
        private final ContextMenu contextMenu;

        private LogRow() {
            MenuItem copyMessageItem = new MenuItem(Messages.get("logs.contextMenu.copyMessage"));
            copyMessageItem.setOnAction(event -> {
                ClipboardContent content = new ClipboardContent();
                content.putString(getItem().getFormattedMessage());
                Clipboard.getSystemClipboard().setContent(content);
            });
            contextMenu = new ContextMenu(copyMessageItem);
        }

        @Override
        protected void updateItem(ILoggingEvent item, boolean empty) {
            super.updateItem(item, empty);

            getStyleClass().removeAll("row-warn", "row-error");

            if (item == null || empty) {
                setContextMenu(null);
                return;
            }
            setContextMenu(contextMenu);

            String levelStr = item.getLevel().levelStr;
            if (Level.WARN.levelStr.equals(levelStr)) {
                getStyleClass().add("row-warn");
            } else if (Level.ERROR.levelStr.equals(levelStr)) {
                getStyleClass().add("row-error");
            }
        }
    }

    /**
     * Never wraps: multi-line messages (e.g. load flow parameter tables) keep one row line per message
     * line, each overflowing line being ellipsized, the full message being shown in a tooltip when so.
     */
    static final class MessageCell extends TableCell<ILoggingEvent, String> {
        private final Tooltip tooltip = new Tooltip();

        MessageCell() {
            tooltip.setWrapText(true);
            tooltip.setMaxWidth(1200);
            tooltip.getStyleClass().add("log-message-tooltip");
        }

        @Override
        protected void updateItem(String message, boolean empty) {
            super.updateItem(message, empty);
            if (empty || message == null) {
                setText(null);
                setTooltip(null);
                return;
            }
            setText(message);
            tooltip.setText(message);
        }

        @Override
        protected void layoutChildren() {
            super.layoutChildren();
            if (isEmpty() || getItem() == null) {
                return;
            }
            // the skin renders the ellipsized string in a child Text node, while getText() keeps the full one
            boolean ellipsized = getChildrenUnmodifiable().stream()
                    .filter(Text.class::isInstance)
                    .anyMatch(node -> !((Text) node).getText().equals(getText()));
            setTooltip(ellipsized ? tooltip : null);
        }
    }
}
