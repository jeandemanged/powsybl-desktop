/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.core;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * The last saved or loaded study files, most recent first, without duplicates. Persisted with the Java Preferences
 * API when created by {@link #persisted()}; the default instance, used by tests, keeps them in memory only so that it
 * neither shows nor changes the user's own list.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class RecentStudies {

    public static final int MAX_SIZE = 10;

    private static final Logger LOGGER = LoggerFactory.getLogger(RecentStudies.class);

    private static final String KEY_PREFIX = "study";

    private final ObservableList<Path> files = FXCollections.observableArrayList();
    private final ObservableList<Path> unmodifiableFiles = FXCollections.unmodifiableObservableList(files);
    private final Preferences preferences;

    public RecentStudies() {
        this(null);
    }

    RecentStudies(Preferences preferences) {
        this.preferences = preferences;
        if (preferences != null) {
            for (int i = 0; i < MAX_SIZE; i++) {
                String value = preferences.get(KEY_PREFIX + i, null);
                if (value != null) {
                    try {
                        Path file = Path.of(value).toAbsolutePath().normalize();
                        if (!files.contains(file)) {
                            files.add(file);
                        }
                    } catch (InvalidPathException _) {
                        // written on another OS sharing the same preferences, skipped
                    }
                }
            }
        }
    }

    public static RecentStudies persisted() {
        return new RecentStudies(Preferences.userNodeForPackage(RecentStudies.class).node("recentStudies"));
    }

    public ObservableList<Path> getFiles() {
        return unmodifiableFiles;
    }

    /**
     * Puts {@code file} first, removing the oldest beyond {@link #MAX_SIZE}.
     */
    public void add(Path file) {
        Path normalized = file.toAbsolutePath().normalize();
        List<Path> updated = new ArrayList<>(files);
        updated.remove(normalized);
        updated.addFirst(normalized);
        set(updated.subList(0, Math.min(updated.size(), MAX_SIZE)));
    }

    public void remove(Path file) {
        List<Path> updated = new ArrayList<>(files);
        updated.remove(file.toAbsolutePath().normalize());
        set(updated);
    }

    public void clear() {
        set(List.of());
    }

    private void set(List<Path> updated) {
        files.setAll(List.copyOf(updated));
        if (preferences == null) {
            return;
        }
        for (int i = 0; i < MAX_SIZE; i++) {
            if (i < files.size()) {
                preferences.put(KEY_PREFIX + i, files.get(i).toString());
            } else {
                preferences.remove(KEY_PREFIX + i);
            }
        }
        try {
            preferences.flush();
        } catch (BackingStoreException e) {
            // the list still works for this session
            LOGGER.warn("Could not save the recent studies", e);
        }
    }
}
