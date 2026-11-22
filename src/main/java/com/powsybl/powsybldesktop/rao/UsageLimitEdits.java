/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.rao;

import com.powsybl.openrao.commons.OpenRaoException;
import com.powsybl.openrao.data.crac.api.Crac;
import com.powsybl.openrao.data.crac.api.Instant;
import com.powsybl.openrao.data.crac.api.RaUsageLimits;
import com.powsybl.powsybldesktop.utils.Messages;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * The edits of a CRAC's remedial action usage limits at an instant: the maximum number of remedial actions, and per
 * TSO those of topological actions, PSTs, remedial actions and elementary actions. Negative limits are refused (OpenRAO
 * would take them for 0), as are those OpenRAO refuses (a TSO's remedial actions fewer than its topological actions or
 * PSTs). No limit at all is no usage limits for the instant.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class UsageLimitEdits {

    private UsageLimitEdits() {
    }

    /**
     * A TSO's limits, each null when there is none.
     */
    public record TsoLimits(String tso, Integer maxTopo, Integer maxPst, Integer maxRa, Integer maxElementary) {

        public TsoLimits {
            tso = tso == null ? "" : tso.trim();
        }
    }

    /**
     * The limits at an instant: the maximum number of remedial actions, null for none, and the TSOs'.
     */
    public record UsageLimitsSpec(Integer maxRa, List<TsoLimits> perTso) {

        public UsageLimitsSpec {
            perTso = List.copyOf(perTso);
        }

        public boolean isEmpty() {
            return maxRa == null && perTso.isEmpty();
        }
    }

    /**
     * Whether remedial actions can be used at {@code instant}, which has usage limits then: any but the outage one.
     */
    public static boolean hasUsageLimits(Instant instant) {
        return !instant.isOutage();
    }

    /**
     * The usage limits of {@code crac} at {@code instant}, the TSOs sorted.
     */
    public static UsageLimitsSpec of(Crac crac, Instant instant) {
        RaUsageLimits limits = crac.getRaUsageLimitsPerInstant().get(instant);
        if (limits == null) {
            return new UsageLimitsSpec(null, List.of());
        }
        Set<String> tsos = new TreeSet<>();
        Stream.of(limits.getMaxTopoPerTso(), limits.getMaxPstPerTso(), limits.getMaxRaPerTso(), limits.getMaxElementaryActionsPerTso())
                .forEach(map -> tsos.addAll(map.keySet()));
        return new UsageLimitsSpec(limits.getMaxRa(), tsos.stream().map(tso -> new TsoLimits(tso, limits.getMaxTopoPerTso().get(tso),
                limits.getMaxPstPerTso().get(tso), limits.getMaxRaPerTso().get(tso), limits.getMaxElementaryActionsPerTso().get(tso))).toList());
    }

    /**
     * Sets the usage limits of {@code crac} at {@code instant}; none when {@code spec} has none.
     */
    public static void set(Crac crac, Instant instant, UsageLimitsSpec spec) {
        if (!hasUsageLimits(instant)) {
            throw new IllegalArgumentException(Messages.get("usageLimitEdits.outage", instant.getId()));
        }
        checkNotNegative(spec.maxRa());
        Set<String> tsos = new HashSet<>();
        for (TsoLimits limits : spec.perTso()) {
            if (limits.tso().isEmpty()) {
                throw new IllegalArgumentException(Messages.get("usageLimitEdits.tsoRequired"));
            }
            if (!tsos.add(limits.tso())) {
                throw new IllegalArgumentException(Messages.get("usageLimitEdits.tsoTwice", limits.tso()));
            }
            Stream.of(limits.maxTopo(), limits.maxPst(), limits.maxRa(), limits.maxElementary()).forEach(UsageLimitEdits::checkNotNegative);
        }
        if (spec.isEmpty()) {
            // the CRAC's own map: OpenRAO has no removal of an instant's limits
            crac.getRaUsageLimitsPerInstant().remove(instant);
            return;
        }
        try {
            crac.newRaUsageLimits(instant.getId())
                    .withMaxRa(spec.maxRa())
                    .withMaxTopoPerTso(perTso(spec, TsoLimits::maxTopo))
                    .withMaxPstPerTso(perTso(spec, TsoLimits::maxPst))
                    .withMaxRaPerTso(perTso(spec, TsoLimits::maxRa))
                    .withMaxElementaryActionPerTso(perTso(spec, TsoLimits::maxElementary))
                    .add();
        } catch (OpenRaoException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    private static void checkNotNegative(Integer limit) {
        if (limit != null && limit < 0) {
            throw new IllegalArgumentException(Messages.get("usageLimitEdits.negative", limit));
        }
    }

    private static Map<String, Integer> perTso(UsageLimitsSpec spec, Function<TsoLimits, Integer> limit) {
        Map<String, Integer> map = new HashMap<>();
        spec.perTso().forEach(limits -> {
            Integer value = limit.apply(limits);
            if (value != null) {
                map.put(limits.tso(), value);
            }
        });
        return map;
    }

    // ----- the TSOs' limits as table rows: TSO, max topological actions, max PSTs, max remedial actions, max elementary actions -----

    public static List<List<String>> tsoRows(List<TsoLimits> perTso) {
        return perTso.stream().map(limits -> List.of(limits.tso(), TableRows.text(limits.maxTopo()), TableRows.text(limits.maxPst()),
                TableRows.text(limits.maxRa()), TableRows.text(limits.maxElementary()))).toList();
    }

    /**
     * The rows' TSO limits: the TSO, then the limits, blank for none.
     */
    public static List<TsoLimits> parseTsoRows(List<List<String>> rows) {
        List<TsoLimits> perTso = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            perTso.add(new TsoLimits(TableRows.required(row, 0, i), TableRows.integer(row, 1, i), TableRows.integer(row, 2, i),
                    TableRows.integer(row, 3, i), TableRows.integer(row, 4, i)));
        }
        return perTso;
    }
}
