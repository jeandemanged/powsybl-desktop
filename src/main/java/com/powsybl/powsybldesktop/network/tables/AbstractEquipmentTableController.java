/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.tables;

import com.powsybl.iidm.network.Container;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Substation;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.powsybldesktop.MainModel;
import com.powsybl.powsybldesktop.navigation.ContainerNavigationState;
import com.powsybl.powsybldesktop.navigation.NavigationEvent;
import com.powsybl.powsybldesktop.navigation.NavigationType;
import com.powsybl.powsybldesktop.network.search.NetworkSearch;
import com.powsybl.powsybldesktop.network.search.SearchBoxController;
import com.powsybl.powsybldesktop.utils.AbstractDisposableController;
import com.powsybl.powsybldesktop.utils.TableAutoFitLimiter;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.scene.Node;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Shared behaviour of every flat "one row per equipment of a given kind in the network" table controller
 * (Generators, Loads, Lines, Transformers, ...): sorted/searchable data loading, the substation/voltage
 * level link columns, and row-selection helpers. A concrete subclass only supplies its own column
 * configuration (its {@code initialize()} still does that) and the hooks below.
 * <p>
 * A table is either standalone (its own toolbar view, listing the whole network, container links always
 * clickable) or embedded in {@link com.powsybl.powsybldesktop.network.SubstationsController}'s tabs, once
 * {@link #setContainer(Container)} has been called at least once: rows are then filtered to one
 * substation/voltage level, and - unless {@link #disableContainerLinks()} says otherwise - the substation/
 * voltage level cells render as plain text instead of a link, since navigating to the container we're
 * already positioned on there would be redundant.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
abstract class AbstractEquipmentTableController<T extends Identifiable<?>> extends AbstractDisposableController implements EmbeddableEquipmentTable {

    protected MainModel mainModel;

    private final ObservableList<T> data = FXCollections.observableArrayList();
    private List<T> allSorted = List.of();
    protected List<T> currentItems = List.of();
    private Container<?> scopeContainer;
    private boolean filtering;

    abstract TableView<T> tableView();

    abstract SearchBoxController searchBox();

    abstract EnumSet<NetworkSearch.Kind> searchKinds();

    /**
     * Every instance of this table's equipment kind in {@code network}, unsorted and unfiltered - what
     * a standalone table lists in full.
     */
    abstract Stream<T> networkItems(Network network);

    /**
     * The voltage level(s) {@code item} touches - one for single-terminal equipment, two or three for
     * multi-terminal equipment (lines, tie lines, transformers) - used to resolve which substation/
     * voltage level it belongs to.
     */
    abstract List<VoltageLevel> voltageLevelsOf(T item);

    /**
     * {@code match} as this table's own entity type, if it is one - the direct-hit case of a search match
     * (as opposed to a substation/voltage level match, resolved instead via {@link #firstItemIn}).
     */
    abstract Optional<T> asOwnEntity(Identifiable<?> match);

    abstract TableColumn<T, ?> nameColumn();

    abstract TableColumn<T, ?> substationColumn();

    abstract TableColumn<T, ?> voltageLevelColumn();

    /**
     * This table's own navigation event for {@code item}'s row, pushed (without notifying, see
     * {@link MainModel#addNavigationEvent(NavigationEvent, boolean)}) just before a container link
     * navigates away from it (see {@link #containerCell}) - so that navigating back restores this exact
     * row instead of the table's default (unselected) state.
     */
    abstract NavigationEvent ownNavigationEvent(T item);

    /**
     * Whether the substation/voltage level link columns render as plain text once embedded (see class
     * doc). {@code true} by default; overridden to {@code false} by equipment that can span two
     * different substations/voltage levels (Lines, TieLines, Transformers), where the far side stays a
     * useful link even when the near side is the container we're already positioned on.
     */
    protected boolean disableContainerLinks() {
        return true;
    }

    final void initializeTable() {
        tableView().getSelectionModel().setCellSelectionEnabled(true);
        SortedList<T> sorted = new SortedList<>(data);
        sorted.comparatorProperty().bind(tableView().comparatorProperty());
        tableView().setItems(sorted);
        TableAutoFitLimiter.install(tableView());
    }

    @Override
    public void setMainModel(MainModel mainModel) {
        this.mainModel = Objects.requireNonNull(mainModel);
        refreshAll();
        listenerManager.listen(this.mainModel.networkProperty(), (observable, oldValue, newValue) -> refreshAll());
        listenerManager.listen(this.mainModel.updateProperty(), (observable, oldValue, newValue) -> refreshAll());
        searchBox().bind(mainModel, searchKinds(), this::onSearchMatch);
    }

    @Override
    public void setContainer(Container<?> container) {
        filtering = true;
        scopeContainer = container;
        searchBox().hide();
        refreshFiltered();
    }

    @Override
    public boolean hasRows() {
        return !currentItems.isEmpty();
    }

    /**
     * Whether this instance is embedded in {@link com.powsybl.powsybldesktop.network.SubstationsController}'s
     * tabs (see class doc) rather than its own standalone top-level view - used by a subclass's own
     * cross-navigation links (e.g. bus/breaker view to bus/branch view) to decide, like
     * {@link #containerCell}, whether this table has a navigation history entry of its own worth updating.
     */
    final boolean isEmbedded() {
        return filtering;
    }

    @Override
    public void dispose() {
        searchBox().dispose();
        super.dispose();
    }

    private void refreshAll() {
        Network network = mainModel.getNetwork();
        allSorted = network == null ? List.of() : networkItems(network)
                .sorted(Comparator.comparing(Identifiable::getNameOrId))
                .toList();
        refreshFiltered();
    }

    // not filtering (standalone): everything. Filtering with no container selected: nothing.
    private void refreshFiltered() {
        currentItems = !filtering ? allSorted
                : scopeContainer == null ? List.of()
                : allSorted.stream().filter(item -> belongsTo(item, scopeContainer)).toList();
        data.setAll(currentItems);
    }

    final boolean belongsTo(T item, Container<?> container) {
        List<VoltageLevel> voltageLevels = voltageLevelsOf(item);
        if (container instanceof VoltageLevel voltageLevel) {
            return voltageLevels.contains(voltageLevel);
        }
        if (container instanceof Substation substation) {
            return voltageLevels.stream().anyMatch(vl -> vl.getSubstation().map(substation::equals).orElse(false));
        }
        return false;
    }

    final T firstItemIn(Container<?> container) {
        return currentItems.stream().filter(item -> belongsTo(item, container)).findFirst().orElse(null);
    }

    final void selectInTable(T item, TableColumn<T, ?> column) {
        int row = item == null ? -1 : tableView().getItems().indexOf(item);
        if (row < 0) {
            tableView().getSelectionModel().clearSelection();
        } else {
            tableView().getSelectionModel().clearAndSelect(row, column);
            tableView().scrollTo(row);
        }
    }

    final void goToItem(T item) {
        selectInTable(item, nameColumn());
    }

    final Node containerCell(Container<?> container, T item) {
        if (filtering && disableContainerLinks()) {
            return new Label(container.getNameOrId());
        }
        Hyperlink link = new Hyperlink(container.getNameOrId());
        link.getStyleClass().add("container-link");
        link.setOnAction(event -> {
            // only when standalone (not embedded in SubstationsController, see class doc): leaving this
            // table's own row selection in history is meaningless for an embedded instance, which has no
            // navigation history entry of its own to update
            if (!filtering) {
                mainModel.addNavigationEvent(ownNavigationEvent(item), false);
            }
            mainModel.addNavigationEvent(NavigationEvent.create(NavigationType.SUBSTATIONS, ContainerNavigationState.create(container)));
        });
        return link;
    }

    private void onSearchMatch(Identifiable<?> match) {
        asOwnEntity(match).ifPresentOrElse(
                item -> selectInTable(item, nameColumn()),
                () -> {
                    TableColumn<T, ?> column = match instanceof VoltageLevel ? voltageLevelColumn() : substationColumn();
                    selectInTable(firstItemIn(NetworkSearch.containerOf(match)), column);
                });
    }
}
