/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.detail;

import com.powsybl.iidm.network.Container;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.ReactiveLimitsHolder;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.powsybldesktop.MainModel;
import com.powsybl.powsybldesktop.SceneModel;
import com.powsybl.powsybldesktop.SceneView;
import com.powsybl.powsybldesktop.navigation.ContainerNavigationState;
import com.powsybl.powsybldesktop.navigation.NavigationEvent;
import com.powsybl.powsybldesktop.navigation.NavigationType;
import com.powsybl.powsybldesktop.network.edit.Removals;
import com.powsybl.powsybldesktop.network.limits.OperationalLimitsPane;
import com.powsybl.powsybldesktop.parameters.GuiParameters;
import com.powsybl.powsybldesktop.utils.AbstractDisposableController;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.beans.binding.BooleanBinding;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/**
 * Detail view of one equipment, non-modal (see {@link EquipmentDetails}), one subclass per equipment type, all on
 * {@code equipment-detail-view.fxml}. This base builds what every type shares: the title and voltage level links,
 * the main tab's form starting with the general section (id, editable, name, fictitious), the extensions, properties
 * and aliases tabs; subclasses add their main rows, an {@link EquipmentSymbol} above the tabs, a main tab side node
 * (e.g. a P/Q diagram) and tabs of their own.
 * Edits write straight to the equipment, as in the tables; every {@link #register registered} part is re-read on
 * each network update and read-only while the network is busy.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public abstract class AbstractEquipmentDetailController<T extends Identifiable<T>> extends AbstractDisposableController implements SceneView {

    @FXML
    Label titleLabel;
    @FXML
    HBox linksBox;
    @FXML
    private HBox symbolHost;
    @FXML
    TabPane tabPane;
    @FXML
    private SplitPane mainSplitPane;
    @FXML
    private ScrollPane mainScrollPane;
    @FXML
    private BorderPane mainSideHost;
    @FXML
    private ScrollPane extensionsScrollPane;
    @FXML
    private VBox propertiesBox;

    protected T equipment;
    protected SceneModel sceneModel;
    protected MainModel mainModel;
    DetailForm form;
    ExtensionsPane<T> extensionsPane;
    KeyValueTablePane propertiesPane;
    KeyValueTablePane aliasesPane;
    private final List<DetailPart> parts = new ArrayList<>();
    private BooleanBinding editable;
    private String typeKey;

    /**
     * @param typeKey the equipment type's name key, e.g. {@code desktop.common.equipment.generator}
     */
    public void setEquipment(T equipment, String typeKey) {
        this.equipment = Objects.requireNonNull(equipment);
        this.typeKey = Objects.requireNonNull(typeKey);
    }

    public T getEquipment() {
        return equipment;
    }

    /**
     * The voltage levels the equipment is connected to, refreshed after an edit, and by default linked in the header.
     */
    protected abstract List<VoltageLevel> voltageLevels();

    /**
     * The containers linked in the header, opening the {@link #containerTab} of each in the substations view.
     */
    protected List<? extends Container<?>> linkedContainers() {
        return voltageLevels();
    }

    /**
     * The substations view tab listing this type, where the header's links select the equipment, or a diagram tab.
     */
    protected abstract ContainerNavigationState.ContainerTab containerTab();

    /**
     * Adds the type's rows to the main form, after the general section's id, name and fictitious rows.
     */
    protected abstract void buildMain(DetailForm form);

    /**
     * Shown next to the main form, e.g. a P/Q diagram; none by default.
     */
    protected Node mainSide() {
        return null;
    }

    /**
     * Drawn above the tabs, e.g. {@code newSymbol().injection(...)}; none by default.
     */
    protected EquipmentSymbol symbol() {
        return null;
    }

    protected EquipmentSymbol newSymbol() {
        return new EquipmentSymbol(() -> mainModel.getParametersModel().sldParametersProperty().getValue().getComponentLibrary(),
                guiParameters(), onEdit());
    }

    /**
     * Tabs between the main tab and the extensions tab.
     */
    protected List<Tab> extraTabs() {
        return List.of();
    }

    protected abstract List<ExtensionsPane.Editor<T>> extensionEditors();

    /**
     * Refreshes what isn't a registered part, e.g. a chart, after every network update.
     */
    protected void onRefresh() {
    }

    @Override
    public void setSceneModel(SceneModel sceneModel) {
        this.sceneModel = Objects.requireNonNull(sceneModel);
        mainModel = sceneModel.getMainModel();
        // not while a background job uses the network (see MainModel.markBusy)
        editable = mainModel.networkBusyProperty().not();

        form = register(newForm());
        form.section("detail.section.general")
                .textField("desktop.common.column.id", equipment::getId, this::setId)
                .copyButton(equipment::getId)
                .textField("desktop.common.column.name", () -> equipment.getOptionalName().orElse(null), equipment::setName)
                .copyButton(() -> equipment.getOptionalName().orElse(null))
                .booleanField("detail.fictitious", equipment::isFictitious, equipment::setFictitious);
        buildMain(form);
        mainScrollPane.setContent(form.getNode());
        Node side = mainSide();
        if (side != null) {
            mainSideHost.setCenter(side);
        } else {
            mainSplitPane.getItems().remove(mainSideHost);
        }
        tabPane.getTabs().addAll(1, extraTabs());
        EquipmentSymbol symbol = symbol();
        if (symbol != null) {
            symbolHost.getChildren().setAll(register(symbol));
        } else {
            ((Pane) symbolHost.getParent()).getChildren().remove(symbolHost);
        }

        extensionsPane = register(new ExtensionsPane<>(equipment, extensionEditors(), guiParameters(), onEdit()));
        extensionsScrollPane.setContent(extensionsPane);
        propertiesPane = register(KeyValueTablePane.properties(equipment, onEdit()));
        aliasesPane = register(KeyValueTablePane.aliases(equipment, onEdit()));
        VBox.setVgrow(propertiesPane, Priority.ALWAYS);
        VBox.setVgrow(aliasesPane, Priority.ALWAYS);
        propertiesBox.getChildren().setAll(propertiesPane, aliasesPane);

        ContainerNavigationState.ContainerTab tab = containerTab();
        for (Container<?> container : linkedContainers()) {
            Hyperlink link = new Hyperlink(container.getNameOrId());
            link.getStyleClass().add("container-link");
            // a diagram tab selects no row
            link.setOnAction(_ -> sceneModel.navigate(NavigationEvent.create(NavigationType.SUBSTATIONS,
                    ContainerNavigationState.create(container, tab, tab == ContainerNavigationState.ContainerTab.SINGLE_LINE ? null : equipment))));
            linksBox.getChildren().add(link);
        }
        if (Removals.supports(equipment)) {
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            Button removeButton = new Button(Messages.get("desktop.common.action.remove"), new FontIcon("mdi2d-delete"));
            removeButton.disableProperty().bind(editable.not());
            removeButton.setOnAction(_ -> Removals.remove(mainModel, removeButton.getScene().getWindow(), equipment));
            linksBox.getChildren().addAll(spacer, removeButton);
        }

        listenerManager.listen(mainModel.updateProperty(), (_, _, _) -> refresh());
        listenerManager.listen(mainModel.getParametersModel().guiParametersRevisionProperty(), (_, _, _) -> refresh());
        // the symbol's component library
        listenerManager.listen(mainModel.getParametersModel().sldParametersProperty(), (_, _, _) -> refresh());
        refresh();
    }

    /**
     * Re-read on every network update, editable only while the network isn't busy.
     */
    protected <P extends DetailPart> P register(P part) {
        part.editableProperty().bind(editable);
        parts.add(part);
        return part;
    }

    protected DetailForm newForm() {
        return new DetailForm(guiParameters(), onEdit());
    }

    protected Supplier<GuiParameters> guiParameters() {
        return () -> mainModel.getParametersModel().getGuiParameters();
    }

    /**
     * After every applied edit: the network update every view refreshes on, confined to the equipment's voltage level
     * if it has a single one.
     */
    protected Runnable onEdit() {
        return () -> {
            List<VoltageLevel> voltageLevels = voltageLevels();
            if (voltageLevels.size() == 1) {
                mainModel.setUpdate(voltageLevels.getFirst());
            } else {
                mainModel.setUpdate();
            }
        };
    }

    /**
     * A P/Q diagram of {@code holder}'s reactive limits and operating points, e.g. for {@link #mainSide}.
     */
    protected Node capabilityChart(ReactiveLimitsHolder holder, DoubleSupplier minP, DoubleSupplier maxP,
                                   Supplier<ReactiveLimitsPane.OperatingPoints> operatingPoints) {
        CapabilityChart chart = new CapabilityChart();
        register(DetailPart.of(() -> {
            chart.setLimits(CapabilityChart.points(holder.getReactiveLimits(), minP.getAsDouble(), maxP.getAsDouble()));
            ReactiveLimitsPane.OperatingPoints points = operatingPoints.get();
            chart.setOperatingPoints(points.targetP(), points.targetQ(), points.solvedP(), points.solvedQ());
        }));
        return chart;
    }

    protected Tab reactiveLimitsTab(ReactiveLimitsHolder holder, DoubleSupplier minP, DoubleSupplier maxP,
                                    Supplier<ReactiveLimitsPane.OperatingPoints> operatingPoints) {
        return tab("detail.tab.reactiveLimits", register(new ReactiveLimitsPane(holder, minP, maxP, operatingPoints, guiParameters(), onEdit())));
    }

    /**
     * The editable flow limits of a branch, three-winding transformer or boundary line.
     */
    protected Tab limitsTab() {
        return tab("detail.tab.limits", scrolling(register(OperationalLimitsPane.editable(equipment, guiParameters(), onEdit()))));
    }

    protected static Tab tab(String titleKey, Node content) {
        return new Tab(Messages.get(titleKey), content);
    }

    protected static ScrollPane scrolling(Node content) {
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setPadding(new Insets(8));
        return scrollPane;
    }

    /**
     * IIDM checks the id is new; the separate window's tab is re-keyed to it.
     */
    private void setId(String id) {
        equipment.setId(id);
        sceneModel.getDetailWindows().ifPresent(windows -> windows.retarget(this, EquipmentDetails.key(equipment)));
    }

    private void refresh() {
        titleLabel.setText(Messages.get("detail.title", Messages.get(typeKey), equipment.getNameOrId()));
        parts.forEach(DetailPart::refresh);
        onRefresh();
    }
}
