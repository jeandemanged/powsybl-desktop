/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.contingency;

import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.list.AbstractEquipmentCriterionContingencyList;
import com.powsybl.contingency.list.ContingencyList;
import com.powsybl.contingency.list.DefaultContingencyList;
import com.powsybl.contingency.list.ListOfContingencyLists;
import com.powsybl.iidm.network.Network;
import com.powsybl.powsybldesktop.MainModel;
import com.powsybl.powsybldesktop.utils.AbstractDisposableController;
import com.powsybl.powsybldesktop.utils.FileChooserPreferences;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Two-pane view: a left list of the current network's {@link ContingencyList} sub-lists (editable via an inline
 * form beneath it) and a right table of the {@link Contingency} instances that {@code ListOfContingencyLists}
 * aggregate resolves to, with a validity indicator computed via {@link ContingencyList#getValidContingencies}.
 * <p>
 * All mutations to a network's contingency-list collection go through this controller's own methods (add/remove/
 * form edits), so the right-pane table is refreshed by explicitly calling {@link #refreshComputedTable()} after
 * each one rather than via a {@code ListChangeListener} - {@link com.powsybl.powsybldesktop.utils.ListenerManager}
 * has no way to detach a single listener when switching networks, so listening directly on each network's list
 * would leak a stale listener per network visited.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class ContingenciesController extends AbstractDisposableController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ContingenciesController.class);

    @FXML
    private MenuButton addMenuButton;

    @FXML
    private Button removeButton;

    @FXML
    private Button importButton;

    @FXML
    private Button exportButton;

    @FXML
    ListView<ContingencyList> contingencyListsListView;

    @FXML
    private StackPane formHost;

    @FXML
    TableView<Contingency> contingenciesTableView;

    @FXML
    private TableColumn<Contingency, String> idColumn;

    @FXML
    private TableColumn<Contingency, String> elementsColumn;

    @FXML
    private TableColumn<Contingency, Boolean> validColumn;

    private MainModel mainModel;
    private Network network;
    private Set<String> validContingencyIds = Set.of();
    // Suppresses showForm() re-entering (and tearing down/rebuilding the currently open form) when the
    // selectedItemProperty change it's about to react to was itself caused by one of that form's own edits
    // (see showForm's onReplace) rather than a genuine user selection change.
    private boolean applyingReplace;

    @FXML
    private void initialize() {
        for (ContingencyListKind kind : ContingencyListKind.values()) {
            MenuItem item = new MenuItem(kind.label());
            item.setOnAction(event -> addContingencyList(kind));
            addMenuButton.getItems().add(item);
        }
        removeButton.disableProperty().bind(contingencyListsListView.getSelectionModel().selectedItemProperty().isNull());
        contingencyListsListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(ContingencyList item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : ContingencyListKind.labelFor(item) + " — " + displayName(item));
            }
        });
        contingencyListsListView.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> showForm(newValue));

        idColumn.setCellValueFactory(cellData -> new ReadOnlyStringWrapper(cellData.getValue().getId()));
        elementsColumn.setCellValueFactory(cellData -> new ReadOnlyStringWrapper(elementsSummary(cellData.getValue())));
        validColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(validContingencyIds.contains(cellData.getValue().getId())));
        validColumn.setCellFactory(col -> new TableCell<>() {
            private final CheckBox checkBox = new CheckBox();

            {
                checkBox.setDisable(true);
            }

            @Override
            protected void updateItem(Boolean value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null) {
                    setGraphic(null);
                } else {
                    checkBox.setSelected(value);
                    setGraphic(checkBox);
                }
            }
        });
    }

    public void setMainModel(MainModel mainModel) {
        this.mainModel = Objects.requireNonNull(mainModel);
        listenerManager.listen(mainModel.networkProperty(), (obs, oldValue, newValue) -> refreshForNetwork(newValue));
        refreshForNetwork(mainModel.getNetwork());
    }

    private void refreshForNetwork(Network newNetwork) {
        this.network = newNetwork;
        boolean hasNetwork = newNetwork != null;
        addMenuButton.setDisable(!hasNetwork);
        importButton.setDisable(!hasNetwork);
        exportButton.setDisable(!hasNetwork);
        contingencyListsListView.setItems(hasNetwork ? mainModel.getContingencyLists(newNetwork) : FXCollections.observableArrayList());
        if (hasNetwork) {
            // Leaked on every network visited (ListenerManager can't detach a single listener), but harmless:
            // a stale listener on a since-abandoned network's list just triggers a same-network no-op refresh
            // here, since refreshComputedTable() always reads mainModel.getContingencyLists(this.network) -
            // and it's what makes every mutation (add/remove/import/a form's onReplace) redraw the right pane
            // without each of those call sites needing to remember to call refreshComputedTable() itself.
            listenerManager.listen(mainModel.getContingencyLists(newNetwork), change -> refreshComputedTable());
        }
        showForm(null);
        refreshComputedTable();
    }

    private void refreshComputedTable() {
        if (network == null) {
            validContingencyIds = Set.of();
            contingenciesTableView.setItems(FXCollections.observableArrayList());
            return;
        }
        List<Contingency> contingencies = mainModel.getContingencyLists(network).stream()
                .flatMap(this::contingenciesOf)
                .collect(Collectors.toList());
        validContingencyIds = ContingencyList.getValidContingencies(contingencies, network).stream()
                .map(Contingency::getId)
                .collect(Collectors.toSet());
        contingenciesTableView.setItems(FXCollections.observableArrayList(contingencies));
    }

    // DefaultContingencyList.getContingencies(Network) silently drops any contingency referencing missing
    // equipment, which would make the "valid" column always show true for whatever remains - its raw, unfiltered
    // getContingencies() is used instead so an invalid explicit entry still shows up (as invalid). The criterion
    // lists can't produce an invalid Contingency by construction (built directly from matching network
    // equipment), so getContingencies(network) is used for those, and for any unsupported imported type.
    private Stream<Contingency> contingenciesOf(ContingencyList list) {
        return list instanceof DefaultContingencyList defaultList
                ? defaultList.getContingencies().stream()
                : list.getContingencies(network).stream();
    }

    private void addContingencyList(ContingencyListKind kind) {
        if (network == null) {
            return;
        }
        ContingencyList created = kind.createDefault("");
        mainModel.getContingencyLists(network).add(created);
        contingencyListsListView.getSelectionModel().select(created);
    }

    @FXML
    private void onRemove() {
        ContingencyList selected = contingencyListsListView.getSelectionModel().getSelectedItem();
        if (selected == null || network == null) {
            return;
        }
        mainModel.getContingencyLists(network).remove(selected);
    }

    // Mutating the network's ObservableList (bound directly to contingencyListsListView's items) re-fires the
    // selection listener with the replacement object at the same index - applyingReplace suppresses that
    // re-entrant call from tearing down and rebuilding the very form that triggered it (which would otherwise
    // lose any in-progress local UI state, e.g. the selected row in DefaultContingencyListFormController's
    // nested elements table); the ContingencyList[] cell tracks the current object across repeated edits from
    // the same still-open form instance, since indexOf needs to find whatever was set last, not the original.
    private void showForm(ContingencyList selected) {
        if (applyingReplace) {
            return;
        }
        formHost.getChildren().clear();
        if (selected == null || network == null) {
            return;
        }
        ContingencyList[] current = {selected};
        Consumer<ContingencyList> onReplace = replacement -> {
            List<ContingencyList> lists = mainModel.getContingencyLists(network);
            int index = lists.indexOf(current[0]);
            if (index >= 0) {
                applyingReplace = true;
                try {
                    lists.set(index, replacement);
                } finally {
                    applyingReplace = false;
                }
                current[0] = replacement;
            }
        };
        if (selected instanceof DefaultContingencyList defaultList) {
            formHost.getChildren().add(loadDefaultForm(defaultList, onReplace));
        } else if (selected instanceof AbstractEquipmentCriterionContingencyList criterionList) {
            ContingencyListKind kind = ContingencyListKind.of(criterionList).orElse(null);
            if (kind == null) {
                formHost.getChildren().add(unsupportedLabel(selected));
            } else {
                formHost.getChildren().add(loadCriterionForm(kind, criterionList, onReplace));
            }
        } else {
            formHost.getChildren().add(unsupportedLabel(selected));
        }
    }

    private Node loadDefaultForm(DefaultContingencyList list, Consumer<ContingencyList> onReplace) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("default-contingency-list-form.fxml"), Messages.bundle());
            Node node = loader.load();
            DefaultContingencyListFormController controller = loader.getController();
            controller.setContingencyList(list, onReplace);
            return node;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Node loadCriterionForm(ContingencyListKind kind, AbstractEquipmentCriterionContingencyList list, Consumer<ContingencyList> onReplace) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("criterion-list-form.fxml"), Messages.bundle());
            Node node = loader.load();
            CriterionListFormController controller = loader.getController();
            controller.setContingencyList(kind, list, onReplace);
            return node;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Label unsupportedLabel(ContingencyList list) {
        Label label = new Label(Messages.get("contingencies.unsupportedType", list.getType()));
        label.setWrapText(true);
        return label;
    }

    private static String displayName(ContingencyList list) {
        return list.getName().isBlank() ? Messages.get("contingencies.unnamed") : list.getName();
    }

    private static String elementsSummary(Contingency contingency) {
        return contingency.getElements().stream()
                .map(element -> element.getType() + ":" + element.getId())
                .collect(Collectors.joining(", "));
    }

    @FXML
    private void onImport() {
        if (network == null) {
            return;
        }
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(Messages.get("networks.file.supportedFiles", "json"), "*.json"));
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(Messages.get("networks.file.allFiles"), "*.*"));
        FileChooserPreferences.applyLastDirectory(fileChooser);
        File selectedFile = fileChooser.showOpenDialog(contingencyListsListView.getScene().getWindow());
        if (selectedFile == null) {
            return;
        }
        FileChooserPreferences.saveLastDirectory(selectedFile);
        try {
            ContingencyList loaded = ContingencyListsIO.read(selectedFile.toPath());
            ObservableList<ContingencyList> target = mainModel.getContingencyLists(network);
            if (loaded instanceof ListOfContingencyLists listOfLists) {
                target.addAll(listOfLists.getContingencyLists());
            } else {
                target.add(loaded);
            }
        } catch (IOException e) {
            LOGGER.error(e.toString(), e);
        }
    }

    @FXML
    private void onExport() {
        if (network == null) {
            return;
        }
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(Messages.get("networks.file.supportedFiles", "json"), "*.json"));
        FileChooserPreferences.applyLastDirectory(fileChooser);
        File selectedFile = fileChooser.showSaveDialog(contingencyListsListView.getScene().getWindow());
        if (selectedFile == null) {
            return;
        }
        FileChooserPreferences.saveLastDirectory(selectedFile);
        try {
            ContingencyListsIO.write(new ListOfContingencyLists(network.getNameOrId(), mainModel.getContingencyLists(network)), selectedFile.toPath());
        } catch (IOException e) {
            LOGGER.error(e.toString(), e);
        }
    }
}
