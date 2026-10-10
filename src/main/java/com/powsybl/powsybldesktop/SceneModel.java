/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop;

import com.powsybl.iidm.network.Network;
import com.powsybl.powsybldesktop.navigation.NavigationEvent;
import com.powsybl.powsybldesktop.window.SeparateWindows;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;

import java.util.Objects;
import java.util.Optional;

/**
 * What a view controller sees of the application: the shared {@link MainModel}, plus what depends on the window
 * showing the view.
 * <p>
 * The main window's scene follows the selected network and records its own navigation. A separate window's scene
 * shows one view in a fixed context: its network is the one selected when it was opened - such windows are closed
 * before the selection changes to another network, so the selection-derived {@link MainModel} state (busy, search
 * index) still applies to it - and its navigation drives the main window without recording its own state.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class SceneModel {

    private final MainModel mainModel;
    private final ReadOnlyObjectProperty<Network> network;
    private final boolean separate;
    // brings the main window to front after a navigation from a separate window
    private final Runnable onNavigate;
    private final DoubleProperty diagramZoom;
    private final BooleanProperty diagramFitToScreen;
    private final IntegerProperty diagramAreaDepth;
    // null where views can't be opened in separate windows: separate windows themselves, tests
    private final SeparateWindows separateWindows;

    private SceneModel(MainModel mainModel, ReadOnlyObjectProperty<Network> network, boolean separate, Runnable onNavigate,
                       DoubleProperty diagramZoom, BooleanProperty diagramFitToScreen, IntegerProperty diagramAreaDepth,
                       SeparateWindows separateWindows) {
        this.mainModel = Objects.requireNonNull(mainModel);
        this.separateWindows = separateWindows;
        this.network = network;
        this.separate = separate;
        this.onNavigate = onNavigate;
        this.diagramZoom = diagramZoom;
        this.diagramFitToScreen = diagramFitToScreen;
        this.diagramAreaDepth = diagramAreaDepth;
    }

    public static SceneModel main(MainModel mainModel) {
        return main(mainModel, null);
    }

    public static SceneModel main(MainModel mainModel, SeparateWindows separateWindows) {
        return new SceneModel(mainModel, mainModel.networkProperty(), false, () -> { },
                mainModel.diagramZoomProperty(), mainModel.diagramFitToScreenProperty(), mainModel.diagramAreaDepthProperty(),
                separateWindows);
    }

    /**
     * A separate window's scene, fixed on the currently selected network, its diagram state starting from the main
     * window's.
     */
    public static SceneModel separate(MainModel mainModel, Runnable onNavigate) {
        return new SceneModel(mainModel, new ReadOnlyObjectWrapper<>(mainModel.getNetwork()).getReadOnlyProperty(), true,
                Objects.requireNonNull(onNavigate),
                new SimpleDoubleProperty(mainModel.getDiagramZoom()), new SimpleBooleanProperty(mainModel.isDiagramFitToScreen()),
                new SimpleIntegerProperty(mainModel.getDiagramAreaDepth()), null);
    }

    public MainModel getMainModel() {
        return mainModel;
    }

    public boolean isSeparate() {
        return separate;
    }

    /**
     * Where this scene's views can open parts of themselves in separate windows, if they can.
     */
    public Optional<SeparateWindows> getSeparateWindows() {
        return Optional.ofNullable(separateWindows);
    }

    public Network getNetwork() {
        return network.get();
    }

    public ReadOnlyObjectProperty<Network> networkProperty() {
        return network;
    }

    /**
     * Navigates the main window to {@code event}.
     */
    public void navigate(NavigationEvent event) {
        mainModel.getNavigationHistory().navigate(event);
        onNavigate.run();
    }

    /**
     * Records {@code event} as the main window's current state without navigating, e.g. a selection made in the view
     * itself. A no-op in a separate window, whose state isn't the main window's.
     */
    public void record(NavigationEvent event) {
        if (!separate) {
            mainModel.getNavigationHistory().navigate(event, false);
        }
    }

    public DoubleProperty diagramZoomProperty() {
        return diagramZoom;
    }

    public BooleanProperty diagramFitToScreenProperty() {
        return diagramFitToScreen;
    }

    public IntegerProperty diagramAreaDepthProperty() {
        return diagramAreaDepth;
    }
}
