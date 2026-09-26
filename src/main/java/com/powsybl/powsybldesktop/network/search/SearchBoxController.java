/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.search;

import com.powsybl.iidm.network.Identifiable;
import com.powsybl.powsybldesktop.MainModel;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.animation.PauseTransition;
import javafx.beans.value.ChangeListener;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Reusable search box: debounces text input, queries the {@link MainModel}'s shared, per-network
 * {@link NetworkSearchIndex} off the FX thread, and cycles through matches (ranked best first) with previous/next
 * buttons. Disabled with an "indexing" status while the bound network's index is still being built (see
 * {@link MainModel#searchIndexStateProperty()}), and re-runs whatever query is currently active whenever
 * {@link MainModel#updateProperty()} fires - the index's bus entries are refreshed in place by
 * {@code MainController} on every such change, which a query in progress would otherwise not notice since it
 * doesn't affect {@link MainModel#searchIndexStateProperty()}. When a query has no precise match, {@link NetworkSearchIndex}
 * falls back to fuzzy/typo-tolerant matching and flags the result {@link NetworkSearchIndex.Result#approximate()};
 * this is surfaced by appending an "(Approximate match)"-style hint to the status label. Included via
 * {@code fx:include} in the substations, generators, shunt compensators, static var compensators, loads, lines,
 * transformers, tie lines, boundary lines and map views, each supplying its own way of revealing a match through
 * {@link #bind}.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class SearchBoxController {

    private static final PseudoClass NO_MATCH = PseudoClass.getPseudoClass("no-match");
    private static final Duration SEARCH_DEBOUNCE = Duration.millis(300);

    @FXML
    private VBox root;
    @FXML
    private TextField searchField;
    @FXML
    private Button searchPreviousButton;
    @FXML
    private Button searchNextButton;
    @FXML
    private Label searchStatusLabel;

    private final PauseTransition searchDebounce = new PauseTransition(SEARCH_DEBOUNCE);
    private final SearchService searchService = new SearchService();
    private final ChangeListener<NetworkSearchIndex.State> indexStateListener = (observable, oldState, newState) -> updateIndexState(newState);
    // the cached index is mutated in place by NetworkSearchIndex.refreshBuses() (see MainController) after a
    // topology change, which doesn't touch searchIndexStateProperty (it stays READY throughout) - so a query
    // currently in progress needs its own nudge to pick up buses added/removed/invalidated by that change
    private final ChangeListener<Instant> updateListener = (observable, oldValue, newValue) -> refreshIfSearching();

    private MainModel mainModel;
    private Set<NetworkSearch.Kind> kinds = NetworkSearch.ALL_KINDS;
    private Consumer<Identifiable<?>> onMatchSelected;
    private Runnable onNoMatch = () -> { };
    private List<Identifiable<?>> matches = List.of();
    private boolean approximateMatches;
    private int matchIndex = -1;

    @FXML
    private void initialize() {
        searchDebounce.setOnFinished(event -> runSearch(searchField.getText()));
        searchField.textProperty().addListener((observable, oldValue, newValue) -> searchDebounce.playFromStart());
        searchService.setOnSucceeded(event -> onSearchResult(searchService.getValue()));
        searchService.setOnFailed(event -> onSearchResult(new NetworkSearchIndex.Result(List.of(), false)));
    }

    /**
     * @param mainModel       the currently selected network is read fresh on each search, via its shared index
     * @param onMatchSelected called with the current match whenever it changes (initial search, previous, next)
     */
    public void bind(MainModel mainModel, Consumer<Identifiable<?>> onMatchSelected) {
        bind(mainModel, NetworkSearch.ALL_KINDS, onMatchSelected);
    }

    /**
     * @param mainModel       the currently selected network is read fresh on each search, via its shared index
     * @param kinds           the equipment kinds this view can match (substations/voltage levels should normally
     *                        stay included so a container match can still be revealed)
     * @param onMatchSelected called with the current match whenever it changes (initial search, previous, next)
     */
    public void bind(MainModel mainModel, Set<NetworkSearch.Kind> kinds, Consumer<Identifiable<?>> onMatchSelected) {
        if (this.mainModel != null) {
            this.mainModel.searchIndexStateProperty().removeListener(indexStateListener);
            this.mainModel.updateProperty().removeListener(updateListener);
        }
        this.mainModel = mainModel;
        this.kinds = kinds;
        this.onMatchSelected = onMatchSelected;
        searchField.setPromptText(Messages.get("network.search.prompt", promptKindsLabel(kinds)));
        mainModel.searchIndexStateProperty().addListener(indexStateListener);
        mainModel.updateProperty().addListener(updateListener);
        updateIndexState(mainModel.searchIndexStateProperty().getValue());
    }

    private static String promptKindsLabel(Set<NetworkSearch.Kind> kinds) {
        return List.of(NetworkSearch.Kind.SUBSTATION, NetworkSearch.Kind.VOLTAGE_LEVEL, NetworkSearch.Kind.BUS, NetworkSearch.Kind.CONFIGURED_BUS,
                        NetworkSearch.Kind.GENERATOR, NetworkSearch.Kind.SHUNT_COMPENSATOR, NetworkSearch.Kind.STATIC_VAR_COMPENSATOR, NetworkSearch.Kind.LOAD,
                        NetworkSearch.Kind.LINE, NetworkSearch.Kind.TRANSFORMER, NetworkSearch.Kind.TIE_LINE, NetworkSearch.Kind.BOUNDARY_LINE,
                        NetworkSearch.Kind.BUSBAR_SECTION)
                .stream()
                .filter(kinds::contains)
                .map(kind -> Messages.get(pluralKindMessageKey(kind)))
                .collect(Collectors.joining(" / "));
    }

    private static String pluralKindMessageKey(NetworkSearch.Kind kind) {
        return switch (kind) {
            case SUBSTATION -> "network.search.kind.substation.plural";
            case VOLTAGE_LEVEL -> "network.search.kind.voltageLevel.plural";
            case BUS -> "network.search.kind.bus.plural";
            case CONFIGURED_BUS -> "network.search.kind.configuredBus.plural";
            case GENERATOR -> "network.search.kind.generator.plural";
            case SHUNT_COMPENSATOR -> "network.search.kind.shuntCompensator.plural";
            case STATIC_VAR_COMPENSATOR -> "network.search.kind.staticVarCompensator.plural";
            case LOAD -> "network.search.kind.load.plural";
            case LINE -> "network.search.kind.line.plural";
            case TRANSFORMER -> "network.search.kind.transformer.plural";
            case TIE_LINE -> "network.search.kind.tieLine.plural";
            case BOUNDARY_LINE -> "network.search.kind.boundaryLine.plural";
            case BUSBAR_SECTION -> "network.search.kind.busbarSection.plural";
        };
    }

    /**
     * @param onNoMatch called whenever the search no longer has a current match: query cleared, nothing found, or
     *                  the index being rebuilt
     */
    public void setOnNoMatch(Runnable onNoMatch) {
        this.onNoMatch = onNoMatch;
    }

    /**
     * Hides this search box - used when a table embedded in
     * {@link com.powsybl.powsybldesktop.network.SubstationsController} gets filtered to one substation/voltage
     * level, where searching the whole network from there would be redundant.
     */
    public void hide() {
        root.setVisible(false);
        root.setManaged(false);
    }

    public void dispose() {
        searchDebounce.stop();
        searchService.cancel();
        if (mainModel != null) {
            mainModel.searchIndexStateProperty().removeListener(indexStateListener);
            mainModel.updateProperty().removeListener(updateListener);
        }
    }

    private void updateIndexState(NetworkSearchIndex.State state) {
        boolean building = state == NetworkSearchIndex.State.BUILDING;
        searchField.setDisable(building);
        if (building) {
            matches = List.of();
            approximateMatches = false;
            matchIndex = -1;
            searchPreviousButton.setDisable(true);
            searchNextButton.setDisable(true);
            searchStatusLabel.setText(Messages.get("network.search.indexing"));
            onNoMatch.run();
        } else {
            // re-runs whatever query is currently typed, now that the index is ready (or reflects a failed build)
            runSearch(searchField.getText());
        }
    }

    // no-op while no query is typed - nothing to refresh, and re-running an empty query would be wasted work
    // on every topology change even when this search box isn't being used
    private void refreshIfSearching() {
        String query = searchField.getText();
        if (query != null && !query.isBlank()) {
            runSearch(query);
        }
    }

    private void runSearch(String query) {
        if (mainModel.searchIndexStateProperty().getValue() == NetworkSearchIndex.State.BUILDING) {
            return;
        }
        if (query == null || query.isBlank()) {
            searchService.cancel();
            onSearchResult(new NetworkSearchIndex.Result(List.of(), false));
            return;
        }
        NetworkSearchIndex index = mainModel.getSearchIndex(mainModel.getNetwork());
        if (index == null) {
            onSearchResult(new NetworkSearchIndex.Result(List.of(), false));
            return;
        }
        searchService.setQuery(query.trim());
        searchService.setIndex(index);
        searchService.restart();
    }

    private void onSearchResult(NetworkSearchIndex.Result result) {
        matches = result.matches();
        approximateMatches = result.approximate();
        matchIndex = matches.isEmpty() ? -1 : 0;
        updateStatus();
        if (matches.isEmpty()) {
            onNoMatch.run();
        } else {
            onMatchSelected.accept(matches.get(matchIndex));
        }
    }

    @FXML
    private void onSearchNext() {
        if (matches.isEmpty()) {
            return;
        }
        matchIndex = (matchIndex + 1) % matches.size();
        updateStatus();
        onMatchSelected.accept(matches.get(matchIndex));
    }

    @FXML
    private void onSearchPrevious() {
        if (matches.isEmpty()) {
            return;
        }
        matchIndex = (matchIndex - 1 + matches.size()) % matches.size();
        updateStatus();
        onMatchSelected.accept(matches.get(matchIndex));
    }

    private void updateStatus() {
        boolean noQuery = searchField.getText() == null || searchField.getText().isBlank();
        boolean hasMatches = !matches.isEmpty();
        searchPreviousButton.setDisable(!hasMatches);
        searchNextButton.setDisable(!hasMatches);
        searchField.pseudoClassStateChanged(NO_MATCH, !noQuery && !hasMatches);
        if (noQuery) {
            searchStatusLabel.setText(null);
        } else if (!hasMatches) {
            searchStatusLabel.setText(Messages.get("network.search.noMatch"));
        } else {
            Identifiable<?> current = matches.get(matchIndex);
            String status = Messages.get("network.search.match", matchIndex + 1, matches.size(),
                    Messages.get(kindMessageKey(current)), current.getOptionalName().orElse(current.getId()));
            if (approximateMatches) {
                status += " " + Messages.get("network.search.approximate");
            }
            searchStatusLabel.setText(status);
        }
    }

    private static String kindMessageKey(Identifiable<?> identifiable) {
        return switch (NetworkSearch.kindOf(identifiable)) {
            case SUBSTATION -> "network.search.kind.substation";
            case VOLTAGE_LEVEL -> "network.search.kind.voltageLevel";
            case BUS -> "network.search.kind.bus";
            // kindOf() never actually returns CONFIGURED_BUS (both bus kinds are the same Bus type - see its
            // own javadoc), but the switch must stay exhaustive over every Kind constant
            case CONFIGURED_BUS -> "network.search.kind.bus";
            case GENERATOR -> "network.search.kind.generator";
            case SHUNT_COMPENSATOR -> "network.search.kind.shuntCompensator";
            case STATIC_VAR_COMPENSATOR -> "network.search.kind.staticVarCompensator";
            case LOAD -> "network.search.kind.load";
            case LINE -> "network.search.kind.line";
            case TRANSFORMER -> "network.search.kind.transformer";
            case TIE_LINE -> "network.search.kind.tieLine";
            case BOUNDARY_LINE -> "network.search.kind.boundaryLine";
            case BUSBAR_SECTION -> "network.search.kind.busbarSection";
        };
    }

    private final class SearchService extends Service<NetworkSearchIndex.Result> {
        private String query;
        private NetworkSearchIndex index;

        void setQuery(String query) {
            this.query = query;
        }

        void setIndex(NetworkSearchIndex index) {
            this.index = index;
        }

        @Override
        protected Task<NetworkSearchIndex.Result> createTask() {
            String currentQuery = query;
            NetworkSearchIndex currentIndex = index;
            Set<NetworkSearch.Kind> currentKinds = kinds;
            return new Task<>() {
                @Override
                protected NetworkSearchIndex.Result call() {
                    return currentIndex.search(currentQuery, currentKinds);
                }
            };
        }
    }
}
