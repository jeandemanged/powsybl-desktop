/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.memory;

import com.powsybl.powsybldesktop.utils.Messages;
import javafx.util.StringConverter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.management.ManagementFactory;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JVM max heap ({@code -Xmx}, in GB) of the packaged application, stored in the jpackage launcher config
 * ({@code <launcher name>.cfg}, next to the application jars): the launcher reads it before starting the JVM, so a
 * change applies at the next start. A per-user file can't be used instead: jpackage would fail to start the JVM
 * whenever it doesn't exist yet, and nothing creates it before the first start.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class MaxHeapConfig {

    /** No {@code -Xmx}: the JVM default, 25% of physical memory. */
    public static final int AUTOMATIC = 0;

    private static final List<Integer> CHOICES_GB = List.of(4, 8, 12, 24, 32, 40, 48, 64, 128);

    private static final long BYTES_PER_GB = 1024L * 1024 * 1024;

    private static final String JAVA_OPTIONS_SECTION = "[JavaOptions]";

    private static final String JAVA_OPTION_PREFIX = "java-options=";

    private static final Pattern XMX_GB = Pattern.compile("-Xmx(\\d+)[gG]");

    private MaxHeapConfig() {
    }

    /** Empty when not run from a packaged app-image (e.g. {@code mvn javafx:run}). */
    public static Optional<Path> launcherConfigFile() {
        // set by the jpackage launcher: path of its executable, named after the launcher like its config
        String appPath = System.getProperty("jpackage.app-path");
        if (appPath == null) {
            return Optional.empty();
        }
        String launcherName = Path.of(appPath).getFileName().toString().replaceFirst("(?i)\\.exe$", "");
        Path appDirectory;
        try {
            appDirectory = Path.of(MaxHeapConfig.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent();
        } catch (URISyntaxException _) {
            return Optional.empty();
        }
        return Optional.of(appDirectory.resolve(launcherName + ".cfg")).filter(Files::isRegularFile);
    }

    public static List<Integer> availableChoices() {
        return availableChoices(physicalMemoryBytes());
    }

    // a heap above physical memory could prevent the JVM from starting; the smallest choice is always offered
    static List<Integer> availableChoices(long physicalMemoryBytes) {
        List<Integer> choices = new ArrayList<>();
        choices.add(AUTOMATIC);
        for (int gb : CHOICES_GB) {
            if (choices.size() == 1 || gb * BYTES_PER_GB <= physicalMemoryBytes) {
                choices.add(gb);
            }
        }
        return choices;
    }

    public static int running() {
        return maxHeapGb(ManagementFactory.getRuntimeMXBean().getInputArguments());
    }

    public static int read(Path config) {
        try {
            return maxHeapGb(Files.readAllLines(config, StandardCharsets.UTF_8).stream()
                    .filter(line -> line.startsWith(JAVA_OPTION_PREFIX))
                    .map(line -> line.substring(JAVA_OPTION_PREFIX.length()))
                    .toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void write(Path config, int gb) throws IOException {
        String content = Files.readString(config, StandardCharsets.UTF_8);
        String lineSeparator = content.contains("\r\n") ? "\r\n" : "\n";
        List<String> lines = withMaxHeap(content.lines().toList(), gb);
        Files.writeString(config, String.join(lineSeparator, lines) + lineSeparator, StandardCharsets.UTF_8);
    }

    // the last -Xmx wins, as for the JVM; an -Xmx not in GB is none of the offered choices
    static int maxHeapGb(List<String> jvmOptions) {
        int gb = AUTOMATIC;
        for (String option : jvmOptions) {
            if (option.startsWith("-Xmx")) {
                Matcher matcher = XMX_GB.matcher(option);
                gb = matcher.matches() ? Integer.parseInt(matcher.group(1)) : AUTOMATIC;
            }
        }
        return gb;
    }

    static List<String> withMaxHeap(List<String> configLines, int gb) {
        List<String> lines = new ArrayList<>(configLines.stream()
                .filter(line -> !line.startsWith(JAVA_OPTION_PREFIX + "-Xmx"))
                .toList());
        if (gb != AUTOMATIC) {
            int section = lines.indexOf(JAVA_OPTIONS_SECTION);
            if (section < 0) {
                lines.add(JAVA_OPTIONS_SECTION);
                section = lines.size() - 1;
            }
            lines.add(section + 1, JAVA_OPTION_PREFIX + "-Xmx" + gb + "g");
        }
        return lines;
    }

    public static StringConverter<Integer> choiceConverter() {
        double automaticGb = physicalMemoryBytes() / 4.0 / BYTES_PER_GB;
        return new StringConverter<>() {
            @Override
            public String toString(Integer gb) {
                if (gb == null) {
                    return "";
                }
                return gb == AUTOMATIC ? Messages.get("memory.automatic", automaticGb) : Messages.get("memory.gb", gb);
            }

            @Override
            public Integer fromString(String string) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static long physicalMemoryBytes() {
        return ((com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean()).getTotalMemorySize();
    }
}
