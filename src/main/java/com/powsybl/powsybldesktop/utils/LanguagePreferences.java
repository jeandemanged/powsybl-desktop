/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.utils;

import java.util.Locale;
import java.util.prefs.Preferences;

/**
 * Persists the user's chosen UI language across runs, using the Java Preferences API (same
 * mechanism as {@link FileChooserPreferences}). {@link #applyPersisted()} sets it as the JVM
 * default locale at startup; {@code MainController} applies a later change live by rebuilding the
 * shell from FXML (already-open reports keep the locale they were generated in).
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class LanguagePreferences {

    private static final String LANGUAGE_KEY = "language";

    private static final Preferences PREFERENCES = Preferences.userNodeForPackage(LanguagePreferences.class);

    private LanguagePreferences() {
    }

    public static void applyPersisted() {
        String language = PREFERENCES.get(LANGUAGE_KEY, null);
        if (language != null) {
            Locale.setDefault(Locale.forLanguageTag(language));
        }
    }

    public static void save(Locale locale) {
        PREFERENCES.put(LANGUAGE_KEY, locale.toLanguageTag());
    }
}
