/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.navigation;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class NavigationHistory {
    private static final int MAX_PAST_SIZE = 30;

    private final ObservableList<NavigationEvent> modifiablePast = FXCollections.observableArrayList();
    private final ObservableList<NavigationEvent> modifiableFuture = FXCollections.observableArrayList();
    // Unmodifiable views, cached rather than wrapped on every getter call: the wrapper forwards
    // changes via a WeakListChangeListener on the backing list, so a fresh, unreferenced wrapper
    // is liable to be garbage-collected and silently stop forwarding to whoever listens on it.
    private final ObservableList<NavigationEvent> past = FXCollections.unmodifiableObservableList(modifiablePast);
    private final ObservableList<NavigationEvent> future = FXCollections.unmodifiableObservableList(modifiableFuture);
    private final ObjectProperty<NavigationEvent> currentEvent = new SimpleObjectProperty<>();

    public ObjectProperty<NavigationEvent> currentEventProperty() {
        return currentEvent;
    }

    public void navigate(NavigationEvent navigationEvent, boolean notify) {
        Objects.requireNonNull(navigationEvent);
        if (!modifiablePast.isEmpty() && modifiablePast.getLast().equals(navigationEvent)) {
            return;
        }
        if (notify) {
            dispatch(navigationEvent);
        }
        modifiablePast.add(navigationEvent);
        while (modifiablePast.size() > MAX_PAST_SIZE) {
            modifiablePast.removeFirst();
        }
        modifiableFuture.clear();
    }

    public void navigate(NavigationEvent navigationEvent) {
        navigate(navigationEvent, true);
    }

    // NavigationEvent/ContainerNavigationState etc. are records/override equals(), and this property is only
    // ever read via a ChangeListener (MainController) - JavaFX suppresses that listener when the new value
    // content-equals the currently held one (e.g. navigating back to a container+tab that was itself the
    // last *notified* event, with only notify=false pushes in between), silently dropping the navigation.
    // Routing every dispatch through a transient null forces the listener to fire regardless of equality.
    private void dispatch(NavigationEvent navigationEvent) {
        currentEvent.setValue(null);
        currentEvent.setValue(navigationEvent);
    }

    public ObservableList<NavigationEvent> getPast() {
        return past;
    }

    public ObservableList<NavigationEvent> getFuture() {
        return future;
    }

    public void navigateBackward() {
        if (modifiablePast.size() > 1) {
            navigateBackwardToIndex(modifiablePast.size() - 2);
        }
    }

    public void navigateForward() {
        if (!modifiableFuture.isEmpty()) {
            navigateForwardToIndex(modifiableFuture.size() - 1);
        }
    }

    public void navigateBackwardToIndex(int index) {
        if (index < 0 || index >= modifiablePast.size() - 1) {
            return;
        }
        while (modifiablePast.size() - 1 > index) {
            modifiableFuture.add(modifiablePast.removeLast());
        }
        dispatch(modifiablePast.getLast());
    }

    public void navigateForwardToIndex(int index) {
        if (index < 0 || index >= modifiableFuture.size()) {
            return;
        }
        while (modifiableFuture.size() - 1 >= index) {
            modifiablePast.add(modifiableFuture.removeLast());
        }
        dispatch(modifiablePast.getLast());
    }

    /**
     * Forgets the events matching {@code filter}, e.g. those of a removed network.
     */
    public void removeIf(Predicate<NavigationEvent> filter) {
        modifiablePast.removeIf(filter);
        modifiableFuture.removeIf(filter);
        if (currentEvent.get() != null && filter.test(currentEvent.get())) {
            // a language reload re-fires the current event, which would otherwise navigate back to it
            currentEvent.setValue(null);
        }
    }
}
