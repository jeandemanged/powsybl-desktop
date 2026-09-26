/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.about;

import com.powsybl.tools.Version;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
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
 * Content of the "About" dialog: the app logo, links to the PowSyBl website/documentation/GitHub, and a
 * table of every PowSyBl module on the classpath - {@link Version#list()} discovers one entry per
 * repository (including this app's own, registered by {@code PowsyblDesktopVersion}) via {@code @AutoService}.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class AboutController {

    private static final Logger LOGGER = LoggerFactory.getLogger(AboutController.class);

    private static final int SHORT_COMMIT_LENGTH = 7;

    private static final DateTimeFormatter BUILD_TIMESTAMP_FORMATTER = new DateTimeFormatterBuilder()
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
            .appendLiteral('.')
            .appendValue(MILLI_OF_SECOND, 3)
            .appendLiteral(' ')
            .appendOffset("+HH:MM:ss", "+00:00")
            .toFormatter(Locale.getDefault());

    @FXML
    private Hyperlink websiteLink;
    @FXML
    private Hyperlink documentationLink;
    @FXML
    private Hyperlink githubLink;

    @FXML
    private TableView<Version> versionsTableView;
    @FXML
    private TableColumn<Version, String> moduleNameColumn;
    @FXML
    private TableColumn<Version, String> versionColumn;
    @FXML
    private TableColumn<Version, String> commitColumn;
    @FXML
    private TableColumn<Version, String> buildTimestampColumn;

    @FXML
    private void initialize() {
        websiteLink.setOnAction(event -> openLink(websiteLink.getText()));
        documentationLink.setOnAction(event -> openLink(documentationLink.getText()));
        githubLink.setOnAction(event -> openLink(githubLink.getText()));

        moduleNameColumn.setCellValueFactory(cellData -> new ReadOnlyStringWrapper(cellData.getValue().getRepositoryName()));
        versionColumn.setCellValueFactory(cellData -> new ReadOnlyStringWrapper(cellData.getValue().getMavenProjectVersion()));
        commitColumn.setCellValueFactory(cellData -> new ReadOnlyStringWrapper(shortCommit(cellData.getValue().getGitVersion())));
        buildTimestampColumn.setCellValueFactory(cellData -> new ReadOnlyStringWrapper(formatBuildTimestamp(cellData.getValue().getBuildTimestamp())));

        versionsTableView.setItems(FXCollections.observableArrayList(
                Version.list().stream().sorted(Comparator.comparing(Version::getRepositoryName)).toList()));
    }

    private static String shortCommit(String gitVersion) {
        return gitVersion.substring(0, Math.min(SHORT_COMMIT_LENGTH, gitVersion.length()));
    }

    private static String formatBuildTimestamp(long buildTimestamp) {
        return BUILD_TIMESTAMP_FORMATTER.format(ZonedDateTime.ofInstant(Instant.ofEpochMilli(buildTimestamp), ZoneId.systemDefault()));
    }

    private static void openLink(String url) {
        try {
            Desktop.getDesktop().browse(new URI(url));
        } catch (IOException | URISyntaxException e) {
            LOGGER.warn("Could not open link: {}", url, e);
        }
    }
}
