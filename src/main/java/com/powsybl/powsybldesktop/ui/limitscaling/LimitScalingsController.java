/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.ui.limitscaling;

import com.google.re2j.PatternSyntaxException;
import com.powsybl.commons.PowsyblException;
import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.list.ContingencyList;
import com.powsybl.iidm.network.Container;
import com.powsybl.iidm.network.LimitType;
import com.powsybl.iidm.network.Network;
import com.powsybl.powsybldesktop.contingency.ContingencyNames;
import com.powsybl.powsybldesktop.core.MainModel;
import com.powsybl.powsybldesktop.core.NetworkStudy;
import com.powsybl.powsybldesktop.limitscaling.AffectedLimits;
import com.powsybl.powsybldesktop.limitscaling.AffectedLimits.AffectedLimit;
import com.powsybl.powsybldesktop.limitscaling.LimitScalingTexts;
import com.powsybl.powsybldesktop.limitscaling.LimitScalings;
import com.powsybl.powsybldesktop.limitscaling.OlfLimitScalingSupport;
import com.powsybl.powsybldesktop.navigation.ContainerNavigationState;
import com.powsybl.powsybldesktop.navigation.NavigationEvent;
import com.powsybl.powsybldesktop.navigation.NavigationType;
import com.powsybl.powsybldesktop.parameters.GuiParameters;
import com.powsybl.powsybldesktop.search.NetworkSearch;
import com.powsybl.powsybldesktop.ui.AbstractDisposableController;
import com.powsybl.powsybldesktop.ui.controls.FileChooserPreferences;
import com.powsybl.powsybldesktop.ui.form.TableColumnSupport;
import com.powsybl.powsybldesktop.ui.window.SceneModel;
import com.powsybl.powsybldesktop.ui.window.SceneView;
import com.powsybl.powsybldesktop.utils.Messages;
import com.powsybl.security.limitscaling.LimitScaling;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * The ordered limit scalings of the current root network ({@link NetworkStudy#getLimitScalings()}): a table with their
 * enabled flags and how Open Load Flow's security analysis treats them, the selected one's inline form beneath it,
 * and the limits it changes in the network on the right.
 * <p>
 * Like the contingency lists, a scaling is immutable: a form edit replaces it, its enabled flag being carried over.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class LimitScalingsController extends AbstractDisposableController implements SceneView {

    private static final Logger LOGGER = LoggerFactory.getLogger(LimitScalingsController.class);

    @FXML
    private Button addButton;
    @FXML
    private Button duplicateButton;
    @FXML
    private Button removeButton;
    @FXML
    private Button moveUpButton;
    @FXML
    private Button moveDownButton;
    @FXML
    private Button enableAllButton;
    @FXML
    private Button disableAllButton;
    @FXML
    private Button importButton;
    @FXML
    private Button exportButton;
    @FXML
    Label olfSummaryLabel;

    @FXML
    TableView<LimitScaling> scalingsTableView;
    @FXML
    private TableColumn<LimitScaling, Boolean> enabledColumn;
    @FXML
    private TableColumn<LimitScaling, LimitScaling> indexColumn;
    @FXML
    private TableColumn<LimitScaling, String> limitTypeColumn;
    @FXML
    private TableColumn<LimitScaling, Double> valueColumn;
    @FXML
    private TableColumn<LimitScaling, String> contextColumn;
    @FXML
    private TableColumn<LimitScaling, String> criteriaColumn;
    @FXML
    private TableColumn<LimitScaling, String> durationsColumn;
    @FXML
    private TableColumn<LimitScaling, String> limitsGroupsColumn;
    @FXML
    private TableColumn<LimitScaling, Boolean> monitoringOnlyColumn;
    @FXML
    private TableColumn<LimitScaling, LimitScaling> olfColumn;

    @FXML
    private StackPane formHost;

    @FXML
    Label affectedTitleLabel;
    @FXML
    TableView<AffectedLimit> affectedTableView;
    @FXML
    private TableColumn<AffectedLimit, AffectedLimit> affectedEquipmentColumn;
    @FXML
    private TableColumn<AffectedLimit, Integer> affectedSideColumn;
    @FXML
    private TableColumn<AffectedLimit, String> affectedGroupColumn;
    @FXML
    private TableColumn<AffectedLimit, Double> affectedPermanentColumn;
    @FXML
    private TableColumn<AffectedLimit, Double> affectedScaledPermanentColumn;
    @FXML
    private TableColumn<AffectedLimit, String> affectedTemporaryColumn;

    private SceneModel sceneModel;
    private MainModel mainModel;
    private Network network;
    // the scalings this controller already listens to the enabled flag of, by identity
    private final Set<LimitScaling> listenedEnabledScalings = Collections.newSetFromMap(new IdentityHashMap<>());
    // see ContingenciesController.applyingReplace
    private boolean applyingReplace;

    @FXML
    private void initialize() {
        scalingsTableView.getSelectionModel().selectedItemProperty().addListener((_, _, newValue) -> showForm(newValue));
        var selected = scalingsTableView.getSelectionModel().selectedItemProperty().isNull();
        duplicateButton.disableProperty().bind(selected);
        removeButton.disableProperty().bind(selected);
        moveUpButton.disableProperty().bind(selected);
        moveDownButton.disableProperty().bind(selected);

        enabledColumn.setCellValueFactory(cellData -> study().limitScalingEnabledProperty(listened(cellData.getValue())));
        enabledColumn.setCellFactory(CheckBoxTableCell.forTableColumn(enabledColumn));
        indexColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue()));
        indexColumn.setCellFactory(_ -> new TableCell<>() {
            @Override
            protected void updateItem(LimitScaling item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : String.valueOf(getIndex() + 1));
            }
        });
        limitTypeColumn.setCellValueFactory(cellData -> new ReadOnlyStringWrapper(LimitScalingTexts.limitType(cellData.getValue().getLimitType())));
        valueColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().getValue() * 100));
        TableColumnSupport.configureDoubleColumn(valueColumn, TableColumnSupport.doubleFormat(this::guiParameters, GuiParameters.Quantity.PERCENTAGE));
        contextColumn.setCellValueFactory(cellData -> new ReadOnlyStringWrapper(LimitScalingTexts.context(cellData.getValue().getContingencyContext())));
        criteriaColumn.setCellValueFactory(cellData -> new ReadOnlyStringWrapper(LimitScalingTexts.criteria(cellData.getValue().getNetworkElementCriteria())));
        durationsColumn.setCellValueFactory(cellData -> new ReadOnlyStringWrapper(LimitScalingTexts.durations(cellData.getValue().getDurationCriteria())));
        limitsGroupsColumn.setCellValueFactory(cellData ->
                new ReadOnlyStringWrapper(LimitScalingTexts.limitsGroups(cellData.getValue().getOperationalLimitsGroupIdsSelection())));
        monitoringOnlyColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().isMonitoringOnly()));
        monitoringOnlyColumn.setCellFactory(_ -> new TableCell<>() {
            @Override
            protected void updateItem(Boolean value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? null : Messages.get(value ? "limitScalings.yes" : "limitScalings.no"));
            }
        });
        olfColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue()));
        olfColumn.setCellFactory(_ -> new OlfSupportCell());

        affectedEquipmentColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue()));
        affectedEquipmentColumn.setCellFactory(_ -> new TableCell<>() {
            @Override
            protected void updateItem(AffectedLimit item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty || item == null ? null : equipmentLink(item));
            }
        });
        affectedSideColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().side().getNum()));
        affectedGroupColumn.setCellValueFactory(cellData -> new ReadOnlyStringWrapper(cellData.getValue().groupId()));
        configureLimitColumn(affectedPermanentColumn, AffectedLimit::permanentLimit);
        configureLimitColumn(affectedScaledPermanentColumn, AffectedLimit::scaledPermanentLimit);
        affectedTemporaryColumn.setCellValueFactory(cellData -> new ReadOnlyStringWrapper(
                cellData.getValue().scaledTemporaryLimitCount() + " / " + cellData.getValue().temporaryLimitCount()));
    }

    private void configureLimitColumn(TableColumn<AffectedLimit, Double> column, Function<AffectedLimit, Double> getter) {
        column.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(getter.apply(cellData.getValue())));
        // a limit of the scaling's type: current or power
        column.setCellFactory(_ -> new TableCell<>() {
            @Override
            protected void updateItem(Double value, boolean empty) {
                super.updateItem(value, empty);
                LimitScaling scaling = scalingsTableView.getSelectionModel().getSelectedItem();
                if (empty || value == null || scaling == null) {
                    setText(null);
                } else {
                    GuiParameters.Quantity quantity = scaling.getLimitType() == LimitType.CURRENT ? GuiParameters.Quantity.CURRENT : GuiParameters.Quantity.POWER;
                    setText(TableColumnSupport.doubleFormat(LimitScalingsController.this::guiParameters, quantity).toString(value));
                }
            }
        });
    }

    private Hyperlink equipmentLink(AffectedLimit item) {
        Hyperlink link = new Hyperlink(item.equipment().getNameOrId());
        link.getStyleClass().add("container-link");
        link.setOnAction(_ -> {
            Container<?> container = NetworkSearch.containerOf(item.equipment());
            sceneModel.navigate(NavigationEvent.create(NavigationType.SUBSTATIONS, ContainerNavigationState.create(container)));
        });
        return link;
    }

    @Override
    public void setSceneModel(SceneModel sceneModel) {
        this.sceneModel = Objects.requireNonNull(sceneModel);
        this.mainModel = sceneModel.getMainModel();
        listenerManager.listen(sceneModel.networkProperty(), (_, _, newValue) -> refreshForNetwork(newValue));
        listenerManager.listen(mainModel.getParametersModel().guiParametersRevisionProperty(), (_, _, _) -> {
            scalingsTableView.refresh();
            affectedTableView.refresh();
        });
        // the limits, edited elsewhere, change the preview
        listenerManager.listen(mainModel.updateProperty(), (_, _, _) -> refreshAffected());
        refreshForNetwork(sceneModel.getNetwork());
    }

    private void refreshForNetwork(Network selectedNetwork) {
        // limit scalings belong to the root network, the one computations run on
        Network newNetwork = selectedNetwork == null ? null : selectedNetwork.getNetwork();
        if (newNetwork != null && newNetwork == this.network) {
            return;
        }
        this.network = newNetwork;
        boolean hasNetwork = newNetwork != null;
        addButton.setDisable(!hasNetwork);
        enableAllButton.setDisable(!hasNetwork);
        disableAllButton.setDisable(!hasNetwork);
        importButton.setDisable(!hasNetwork);
        exportButton.setDisable(!hasNetwork);
        listenedEnabledScalings.clear();
        scalingsTableView.setItems(hasNetwork ? study().getLimitScalings() : FXCollections.observableArrayList());
        if (hasNetwork) {
            // leaked per network visited, like ContingenciesController's; harmless, refreshing the current network's
            listenerManager.listen(study().getLimitScalings(), _ -> {
                scalingsTableView.refresh();
                refreshOlfSummary();
            });
        }
        showForm(null);
        refreshOlfSummary();
    }

    private NetworkStudy study() {
        return mainModel.getStudy(network);
    }

    private LimitScaling listened(LimitScaling scaling) {
        if (listenedEnabledScalings.add(scaling)) {
            // the property is held by NetworkStudy and outlives this view
            listenerManager.listen(study().limitScalingEnabledProperty(scaling), (_, _, _) -> refreshOlfSummary());
        }
        return scaling;
    }

    private GuiParameters guiParameters() {
        return mainModel.getParametersModel().getGuiParameters();
    }

    private void refreshOlfSummary() {
        if (network == null) {
            olfSummaryLabel.setText("");
            return;
        }
        List<LimitScaling> enabled = study().getEnabledLimitScalings();
        long notApplied = enabled.stream().filter(s -> OlfLimitScalingSupport.of(s).status() != OlfLimitScalingSupport.Status.APPLIED).count();
        olfSummaryLabel.setText(notApplied == 0 ? "" : Messages.get("limitScalings.olf.summary", notApplied, enabled.size()));
    }

    private void showForm(LimitScaling selected) {
        if (applyingReplace) {
            return;
        }
        formHost.getChildren().clear();
        refreshAffected();
        if (selected == null || network == null) {
            return;
        }
        LimitScaling[] current = {selected};
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("limit-scaling-form.fxml"), Messages.bundle());
            formHost.getChildren().add(loader.load());
            LimitScalingFormController controller = loader.getController();
            controller.setLimitScaling(selected, network, contingencyIds(), replacement -> {
                if (replace(current[0], replacement)) {
                    current[0] = replacement;
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // the contingencies of the enabled lists, offered for a specific contingency context
    private List<String> contingencyIds() {
        NetworkStudy study = study();
        List<ContingencyList> lists = study.getContingencyLists().stream().filter(list -> study.contingencyListEnabledProperty(list).get()).toList();
        try {
            return ContingencyNames.deduplicate(lists.stream().flatMap(list -> list.getContingencies(network).stream()).toList()).stream()
                    .map(Contingency::getId)
                    .sorted()
                    .toList();
        } catch (PowsyblException | IllegalArgumentException | PatternSyntaxException e) {
            // a list the contingencies view can't evaluate either: nothing to offer, the id can still be typed
            LOGGER.warn("Contingencies could not be evaluated: {}", e.toString());
            return List.of();
        }
    }

    private boolean replace(LimitScaling previous, LimitScaling replacement) {
        ObservableList<LimitScaling> scalings = study().getLimitScalings();
        int index = indexOf(scalings, previous);
        if (index < 0) {
            return false;
        }
        study().transferLimitScalingEnabled(previous, replacement);
        if (listenedEnabledScalings.remove(previous)) {
            listenedEnabledScalings.add(replacement);
        }
        boolean wasSelected = scalingsTableView.getSelectionModel().getSelectedItem() == previous;
        applyingReplace = true;
        try {
            scalings.set(index, replacement);
            if (wasSelected) {
                scalingsTableView.getSelectionModel().select(index);
            }
        } finally {
            applyingReplace = false;
        }
        refreshAffected();
        return true;
    }

    // by identity: LimitScaling has no equals, but two scalings may still be the same instance after an import
    private static int indexOf(List<LimitScaling> scalings, LimitScaling scaling) {
        for (int i = 0; i < scalings.size(); i++) {
            if (scalings.get(i) == scaling) {
                return i;
            }
        }
        return -1;
    }

    private void refreshAffected() {
        LimitScaling selected = scalingsTableView.getSelectionModel().getSelectedItem();
        if (selected == null || network == null) {
            affectedTableView.setItems(FXCollections.observableArrayList());
            affectedTitleLabel.setText(Messages.get("limitScalings.affected.title"));
            return;
        }
        List<AffectedLimit> affected = AffectedLimits.compute(network, selected);
        affectedTableView.setItems(FXCollections.observableArrayList(affected));
        affectedTitleLabel.setText(Messages.get("limitScalings.affected.title.count", affected.size(),
                affected.stream().map(AffectedLimit::equipment).distinct().count()));
    }

    @FXML
    private void onAdd() {
        if (network == null) {
            return;
        }
        LimitScaling created = new LimitScaling(LimitType.CURRENT, 1.0);
        study().getLimitScalings().add(created);
        scalingsTableView.getSelectionModel().select(created);
    }

    @FXML
    private void onDuplicate() {
        LimitScaling selected = scalingsTableView.getSelectionModel().getSelectedItem();
        if (selected == null || network == null) {
            return;
        }
        LimitScaling copy = LimitScalings.toBuilder(selected).build();
        int index = indexOf(study().getLimitScalings(), selected);
        study().getLimitScalings().add(index + 1, copy);
        study().limitScalingEnabledProperty(copy).set(study().limitScalingEnabledProperty(selected).get());
        scalingsTableView.getSelectionModel().select(index + 1);
    }

    @FXML
    private void onRemove() {
        LimitScaling selected = scalingsTableView.getSelectionModel().getSelectedItem();
        if (selected == null || network == null) {
            return;
        }
        study().getLimitScalings().remove(indexOf(study().getLimitScalings(), selected));
        listenedEnabledScalings.remove(selected);
    }

    @FXML
    private void onMoveUp() {
        move(-1);
    }

    @FXML
    private void onMoveDown() {
        move(1);
    }

    private void move(int offset) {
        LimitScaling selected = scalingsTableView.getSelectionModel().getSelectedItem();
        if (selected == null || network == null) {
            return;
        }
        ObservableList<LimitScaling> scalings = study().getLimitScalings();
        int index = indexOf(scalings, selected);
        int target = index + offset;
        if (target < 0 || target >= scalings.size()) {
            return;
        }
        List<LimitScaling> reordered = new ArrayList<>(scalings);
        Collections.swap(reordered, index, target);
        scalings.setAll(reordered);
        scalingsTableView.getSelectionModel().select(target);
    }

    @FXML
    private void onEnableAll() {
        setAllEnabled(true);
    }

    @FXML
    private void onDisableAll() {
        setAllEnabled(false);
    }

    private void setAllEnabled(boolean enabled) {
        if (network == null) {
            return;
        }
        study().getLimitScalings().forEach(scaling -> study().limitScalingEnabledProperty(scaling).set(enabled));
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
        File selectedFile = fileChooser.showOpenDialog(scalingsTableView.getScene().getWindow());
        if (selectedFile == null) {
            return;
        }
        FileChooserPreferences.saveLastDirectory(selectedFile);
        List<LimitScaling> loaded;
        try {
            loaded = LimitScalings.read(selectedFile.toPath());
        } catch (UncheckedIOException | PowsyblException e) {
            LOGGER.error(e.toString(), e);
            Alert alert = new Alert(Alert.AlertType.ERROR, Messages.get("limitScalings.import.failed", e.getMessage()), ButtonType.OK);
            alert.initOwner(scalingsTableView.getScene().getWindow());
            alert.setHeaderText(null);
            alert.showAndWait();
            return;
        }
        ObservableList<LimitScaling> scalings = study().getLimitScalings();
        if (!scalings.isEmpty() && !loaded.isEmpty()) {
            ButtonType append = new ButtonType(Messages.get("limitScalings.import.append"));
            ButtonType replace = new ButtonType(Messages.get("limitScalings.import.replace"));
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, Messages.get("limitScalings.import.question", loaded.size()), append, replace, ButtonType.CANCEL);
            alert.initOwner(scalingsTableView.getScene().getWindow());
            alert.setHeaderText(null);
            ButtonType choice = alert.showAndWait().orElse(ButtonType.CANCEL);
            if (choice == replace) {
                scalings.setAll(loaded);
            } else if (choice == append) {
                scalings.addAll(loaded);
            }
        } else {
            scalings.addAll(loaded);
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
        File selectedFile = fileChooser.showSaveDialog(scalingsTableView.getScene().getWindow());
        if (selectedFile == null) {
            return;
        }
        FileChooserPreferences.saveLastDirectory(selectedFile);
        try {
            // all of them, enabled or not: PowSyBl's format has no enabled flag
            LimitScalings.write(List.copyOf(study().getLimitScalings()), selectedFile.toPath());
        } catch (UncheckedIOException e) {
            LOGGER.error(e.toString(), e);
        }
    }

    private static final class OlfSupportCell extends TableCell<LimitScaling, LimitScaling> {
        @Override
        protected void updateItem(LimitScaling item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                setText(null);
                setTooltip(null);
                return;
            }
            OlfLimitScalingSupport.Support support = OlfLimitScalingSupport.of(item);
            String iconLiteral = switch (support.status()) {
                case APPLIED -> "mdi2c-check-circle-outline";
                case PARTIALLY_APPLIED, NOT_APPLIED -> "mdi2a-alert-outline";
                case FAILS -> "mdi2c-close-octagon-outline";
            };
            FontIcon icon = new FontIcon(iconLiteral);
            if (support.status() != OlfLimitScalingSupport.Status.APPLIED) {
                icon.getStyleClass().add("alert-icon");
            }
            setGraphic(icon);
            setText(Messages.get("limitScalings.olf.status." + support.status().name()));
            setTooltip(support.reasons().isEmpty() ? null : new Tooltip(String.join("\n", support.reasons())));
        }
    }
}
