/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.rao;

import com.powsybl.commons.PowsyblException;
import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.ContingencyElement;
import com.powsybl.contingency.ContingencyElementFactory;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Network;
import com.powsybl.openrao.commons.OpenRaoException;
import com.powsybl.openrao.data.crac.api.ContingencyAdder;
import com.powsybl.openrao.data.crac.api.Crac;
import com.powsybl.openrao.data.crac.api.CracFactory;
import com.powsybl.openrao.data.crac.api.InstantKind;
import com.powsybl.openrao.data.crac.api.RemedialAction;
import com.powsybl.openrao.data.crac.api.cnec.Cnec;
import com.powsybl.openrao.data.crac.api.usagerule.OnContingencyState;
import com.powsybl.openrao.data.crac.api.usagerule.OnFlowConstraintInCountry;
import com.powsybl.powsybldesktop.utils.Messages;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Edits of a CRAC within what OpenRAO's API allows (7.5.1): a new empty CRAC, instants appended in their kind's order
 * (preventive and outage first, then at most one auto instant, then curative ones; never removed), contingencies added,
 * their elements changed in place (PowSyBl's {@code Contingency} is mutable, the CRAC's states and CNECs keep referring
 * to it), renamed or removed only when no CNEC or usage rule refers to them (OpenRAO refuses otherwise). A refused
 * edit throws an {@link IllegalArgumentException} telling why. Toolkit-free for testing.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class CracEdits {

    private CracEdits() {
    }

    // ----- CRAC and instants -----

    /**
     * A CRAC with no object yet, and its instants: preventive, outage, then {@code autoInstant} if not null, then the
     * curative ones.
     */
    public static Crac newCrac(String id, String autoInstant, List<String> curativeInstants) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException(Messages.get("cracEdits.idRequired"));
        }
        Crac crac = CracFactory.findDefault().create(id.trim());
        try {
            crac.newInstant(CracGenerator.PREVENTIVE, InstantKind.PREVENTIVE).newInstant(CracGenerator.OUTAGE, InstantKind.OUTAGE);
            if (autoInstant != null) {
                crac.newInstant(autoInstant, InstantKind.AUTO);
            }
            curativeInstants.forEach(instant -> crac.newInstant(instant, InstantKind.CURATIVE));
        } catch (OpenRaoException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
        return crac;
    }

    /**
     * The kinds of the instants that can be added to {@code crac}: an auto one while it has neither auto nor curative
     * instant, curative ones always.
     */
    public static List<InstantKind> addableInstantKinds(Crac crac) {
        boolean auto = !crac.hasAutoInstant() && crac.getInstants(InstantKind.CURATIVE).isEmpty();
        return auto ? List.of(InstantKind.AUTO, InstantKind.CURATIVE) : List.of(InstantKind.CURATIVE);
    }

    public static void addInstant(Crac crac, String id, InstantKind kind) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException(Messages.get("cracEdits.idRequired"));
        }
        if (!addableInstantKinds(crac).contains(kind)) {
            throw new IllegalArgumentException(Messages.get("cracEdits.instant.kindNotAddable", Messages.get("crac.instant.kind." + kind.name().toLowerCase(Locale.ROOT))));
        }
        try {
            crac.newInstant(id.trim(), kind);
        } catch (OpenRaoException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    // ----- contingencies -----

    /**
     * An id like "{@code prefix} n", n the smallest number from 1 that no contingency of {@code crac} has.
     */
    public static String newContingencyId(Crac crac, String prefix) {
        int i = 1;
        while (crac.getContingency(prefix + " " + i) != null) {
            i++;
        }
        return prefix + " " + i;
    }

    public static Contingency addContingency(Crac crac, String id, String name, List<ContingencyElement> elements) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException(Messages.get("cracEdits.idRequired"));
        }
        if (crac.getContingency(id) != null) {
            throw new IllegalArgumentException(Messages.get("cracEdits.contingency.exists", id));
        }
        ContingencyAdder adder = crac.newContingency().withId(id);
        if (name != null && !name.isBlank()) {
            adder.withName(name);
        }
        elements.forEach(element -> adder.withContingencyElement(element.getId(), element.getType()));
        try {
            return adder.add();
        } catch (OpenRaoException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    /**
     * The contingency element of {@code identifiable}, of its type.
     *
     * @throws IllegalArgumentException when it can't be a contingency element
     */
    public static ContingencyElement element(Identifiable<?> identifiable) {
        try {
            return ContingencyElementFactory.create(identifiable);
        } catch (PowsyblException e) {
            throw new IllegalArgumentException(Messages.get("cracEdits.contingency.notAnElement", identifiable.getId()), e);
        }
    }

    /**
     * Replaces the elements of {@code contingency}, in place: whatever refers to it keeps doing so.
     */
    public static void setElements(Contingency contingency, List<ContingencyElement> elements) {
        List.copyOf(contingency.getElements()).forEach(contingency::removeElement);
        elements.forEach(contingency::addElement);
    }

    /**
     * What refers to the contingency {@code id}: its CNECs and the remedial actions with a usage rule on it, by name.
     */
    public static List<String> references(Crac crac, String id) {
        List<String> references = new ArrayList<>();
        crac.getCnecs().stream().<Cnec<?>>map(cnec -> cnec)
                .filter(cnec -> cnec.getState().getContingency().map(contingency -> contingency.getId().equals(id)).orElse(false))
                .map(Cnec::getName).sorted().forEach(references::add);
        crac.getRemedialActions().stream()
                .filter(action -> action.getUsageRules().stream().anyMatch(rule -> switch (rule) {
                    case OnContingencyState onState -> onState.getContingency().getId().equals(id);
                    case OnFlowConstraintInCountry onCountry -> onCountry.getContingency().map(contingency -> contingency.getId().equals(id)).orElse(false);
                    default -> false;
                }))
                .map(RemedialAction::getName).sorted().forEach(references::add);
        return references;
    }

    /**
     * Gives the contingency {@code contingency} a new id and name, as a new contingency with the same elements: only
     * when nothing refers to it.
     */
    public static Contingency rename(Crac crac, Contingency contingency, String id, String name) {
        Objects.requireNonNull(contingency);
        String newId = id == null ? "" : id.trim();
        if (newId.isEmpty()) {
            throw new IllegalArgumentException(Messages.get("cracEdits.idRequired"));
        }
        String newName = name == null || name.isBlank() ? null : name.trim();
        // OpenRAO names a contingency without a name by its id
        if (newId.equals(contingency.getId()) && Objects.requireNonNullElse(newName, newId).equals(contingency.getName().orElse(contingency.getId()))) {
            return contingency;
        }
        List<String> references = references(crac, contingency.getId());
        if (!references.isEmpty()) {
            throw new IllegalArgumentException(Messages.get("cracEdits.contingency.referenced", contingency.getId(), String.join(", ", references)));
        }
        if (!newId.equals(contingency.getId()) && crac.getContingency(newId) != null) {
            throw new IllegalArgumentException(Messages.get("cracEdits.contingency.exists", newId));
        }
        List<ContingencyElement> elements = List.copyOf(contingency.getElements());
        crac.removeContingency(contingency.getId());
        return addContingency(crac, newId, newName, elements);
    }

    public static void removeContingency(Crac crac, String id) {
        List<String> references = references(crac, id);
        if (!references.isEmpty()) {
            throw new IllegalArgumentException(Messages.get("cracEdits.contingency.referenced", id, String.join(", ", references)));
        }
        crac.removeContingency(id);
    }

    /**
     * The result of {@link #addContingencies}: the contingencies added, and the ids skipped, already in the CRAC.
     */
    public record Added(List<Contingency> added, List<String> skipped) {
    }

    /**
     * Adds {@code contingencies} (resolved from contingency lists against {@code network}), skipping those whose id the
     * CRAC already has and those with an element missing from the network.
     */
    public static Added addContingencies(Crac crac, Network network, List<Contingency> contingencies) {
        List<Contingency> added = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        contingencies.stream().sorted(Comparator.comparing(Contingency::getId)).forEach(contingency -> {
            if (crac.getContingency(contingency.getId()) != null || !contingency.isValid(network)) {
                skipped.add(contingency.getId());
            } else {
                added.add(addContingency(crac, contingency.getId(), contingency.getName().orElse(null), contingency.getElements()));
            }
        });
        return new Added(added, skipped);
    }

    /**
     * Why {@code contingency} is not usable: no elements, or elements missing from {@code network}.
     */
    public static Optional<String> contingencyProblem(Network network, Contingency contingency) {
        if (contingency.getElements().isEmpty()) {
            return Optional.of(Messages.get("cracEdits.contingency.empty"));
        }
        List<String> missing = CracElements.missing(network, contingency);
        return missing.isEmpty() ? Optional.empty() : Optional.of(Messages.get("crac.missingElements", String.join(", ", missing)));
    }
}
