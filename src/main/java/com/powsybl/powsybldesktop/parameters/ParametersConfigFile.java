/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.parameters;

import com.fasterxml.jackson.databind.JsonNode;
import com.powsybl.commons.PowsyblException;
import com.powsybl.powsybldesktop.MainModel;
import com.powsybl.powsybldesktop.notification.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

/**
 * The per-user configuration file holding the parameters saved from the parameters view, restored on startup.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class ParametersConfigFile {

    private static final Logger LOGGER = LoggerFactory.getLogger(ParametersConfigFile.class);
    private static final String APP_DIRECTORY = "powsybl-desktop";
    private static final String FILE_NAME = "config.json";
    private static final DateTimeFormatter BACKUP_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private ParametersConfigFile() {
    }

    public static Path defaultPath() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        Path home = Path.of(System.getProperty("user.home"));
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            Path base = appData != null ? Path.of(appData) : home.resolve("AppData").resolve("Roaming");
            return base.resolve(APP_DIRECTORY).resolve(FILE_NAME);
        }
        if (os.contains("mac")) {
            return home.resolve("Library").resolve("Application Support").resolve(APP_DIRECTORY).resolve(FILE_NAME);
        }
        return home.resolve("." + APP_DIRECTORY).resolve(FILE_NAME);
    }

    /**
     * Restores the parameters saved in {@code path}, if any. An unreadable file is moved aside to a timestamped
     * {@code .bak} and replaced by one holding the defaults, the returned notification telling the user so.
     */
    public static Optional<Notification> load(MainModel mainModel, Path path) {
        if (!Files.exists(path)) {
            return Optional.empty();
        }
        Instant start = Instant.now();
        try {
            mainModel.setParameters(DesktopParametersJson.read(path));
            // snapshot of what was actually applied rather than the file's raw content, so a file missing fields
            // or written by another PowSyBl version doesn't show as modified right away
            mainModel.setSavedParameters(DesktopParametersJson.toJson(mainModel.getParameters()));
            return Optional.empty();
        } catch (PowsyblException | UncheckedIOException e) {
            Path backup = path.resolveSibling(path.getFileName() + "." + LocalDateTime.now().format(BACKUP_TIMESTAMP) + ".bak");
            LOGGER.error("Failed to read parameters from {}, moving it to {} and restoring defaults", path, backup, e);
            try {
                Files.move(path, backup);
            } catch (IOException ex) {
                LOGGER.error("Failed to move {} to {}", path, backup, ex);
                return Optional.of(Notification.createError(start, "parameters.config.loadFailedNotMoved").withMessageArgs(path));
            }
            try {
                save(mainModel, path);
            } catch (UncheckedIOException ex) {
                LOGGER.error("Failed to replace {} by default parameters", path, ex);
            }
            return Optional.of(Notification.createError(start, "parameters.config.loadFailed").withMessageArgs(backup));
        }
    }

    public static void save(MainModel mainModel, Path path) {
        JsonNode json = DesktopParametersJson.toJson(mainModel.getParameters());
        DesktopParametersJson.write(json, path);
        mainModel.setSavedParameters(json);
    }

    public static boolean isSaved(MainModel mainModel) {
        return DesktopParametersJson.toJson(mainModel.getParameters()).equals(mainModel.getSavedParameters());
    }
}
