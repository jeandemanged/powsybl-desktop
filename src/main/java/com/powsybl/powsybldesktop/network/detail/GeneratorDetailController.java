/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.detail;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.EnergySource;
import com.powsybl.iidm.network.Generator;
import com.powsybl.iidm.network.Terminal;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.iidm.network.regulation.RegulationMode;
import com.powsybl.iidm.network.regulation.VoltageRegulation;
import com.powsybl.powsybldesktop.MainModel;
import com.powsybl.powsybldesktop.SceneModel;
import com.powsybl.powsybldesktop.SceneView;
import com.powsybl.powsybldesktop.navigation.ContainerNavigationState;
import com.powsybl.powsybldesktop.navigation.NavigationEvent;
import com.powsybl.powsybldesktop.navigation.NavigationType;
import com.powsybl.powsybldesktop.network.tables.VoltageRegulations;
import com.powsybl.powsybldesktop.parameters.GuiParameters;
import com.powsybl.powsybldesktop.utils.AbstractDisposableController;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Detail view of one generator, non-modal (see {@link EquipmentDetails}): every attribute, editable except solved
 * values, the voltage regulation as IIDM holds it (unlike the Generators table's simplified columns), the reactive
 * limits with their P/Q diagram, the extensions, properties and aliases. Edits write straight to the generator, as
 * in the tables; the view re-reads it on every network update.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class GeneratorDetailController extends AbstractDisposableController implements SceneView {

    private static final StringConverter<EnergySource> ENERGY_SOURCE_FORMAT = new StringConverter<>() {
        @Override
        public String toString(EnergySource value) {
            return value == null ? "" : value.name();
        }

        @Override
        public EnergySource fromString(String string) {
            throw new UnsupportedOperationException();
        }
    };

    @FXML
    Label titleLabel;
    @FXML
    Hyperlink voltageLevelLink;
    @FXML
    TabPane tabPane;
    @FXML
    private ScrollPane mainScrollPane;
    @FXML
    private BorderPane mainChartHost;
    @FXML
    private Tab reactiveLimitsTab;
    @FXML
    private ScrollPane extensionsScrollPane;
    @FXML
    private VBox propertiesBox;

    private Generator generator;
    private SceneModel sceneModel;
    private MainModel mainModel;
    DetailForm form;
    ReactiveLimitsPane reactiveLimitsPane;
    ExtensionsPane<Generator> extensionsPane;
    KeyValueTablePane propertiesPane;
    KeyValueTablePane aliasesPane;
    private final CapabilityChart mainChart = new CapabilityChart();

    public void setGenerator(Generator generator) {
        this.generator = Objects.requireNonNull(generator);
    }

    @Override
    public void setSceneModel(SceneModel sceneModel) {
        this.sceneModel = Objects.requireNonNull(sceneModel);
        mainModel = sceneModel.getMainModel();
        Supplier<GuiParameters> guiParameters = () -> mainModel.getParametersModel().getGuiParameters();
        Runnable onEdit = () -> mainModel.setUpdate(generator.getTerminal().getVoltageLevel());

        form = new DetailForm(guiParameters, onEdit);
        buildForm();
        mainScrollPane.setContent(form.getNode());
        mainChartHost.setCenter(mainChart);

        reactiveLimitsPane = new ReactiveLimitsPane(generator, generator::getMinP, generator::getMaxP, this::operatingPoints, guiParameters, onEdit);
        reactiveLimitsTab.setContent(reactiveLimitsPane);

        extensionsPane = new ExtensionsPane<>(generator, ExtensionEditors.forGenerator(), guiParameters, onEdit);
        extensionsScrollPane.setContent(extensionsPane);

        propertiesPane = KeyValueTablePane.properties(generator, onEdit);
        aliasesPane = KeyValueTablePane.aliases(generator, onEdit);
        VBox.setVgrow(propertiesPane, Priority.ALWAYS);
        VBox.setVgrow(aliasesPane, Priority.ALWAYS);
        propertiesBox.getChildren().setAll(propertiesPane, aliasesPane);

        // not while a background job uses the network (see MainModel.markBusy)
        var editable = mainModel.networkBusyProperty().not();
        form.editableProperty().bind(editable);
        reactiveLimitsPane.editableProperty().bind(editable);
        extensionsPane.editableProperty().bind(editable);
        propertiesPane.editableProperty().bind(editable);
        aliasesPane.editableProperty().bind(editable);

        voltageLevelLink.setOnAction(event -> sceneModel.navigate(NavigationEvent.create(NavigationType.SUBSTATIONS,
                ContainerNavigationState.create(generator.getTerminal().getVoltageLevel(), ContainerNavigationState.ContainerTab.GENERATORS, generator))));

        listenerManager.listen(mainModel.updateProperty(), (observable, oldValue, newValue) -> refresh());
        listenerManager.listen(mainModel.getParametersModel().guiParametersRevisionProperty(), (observable, oldValue, newValue) -> refresh());
        refresh();
    }

    private void buildForm() {
        form.section("detail.section.general")
                .text("desktop.common.column.id", generator::getId)
                .textField("desktop.common.column.name", () -> generator.getOptionalName().orElse(null), generator::setName)
                .choiceField("detail.generator.energySource", Arrays.asList(EnergySource.values()), ENERGY_SOURCE_FORMAT,
                        generator::getEnergySource, generator::setEnergySource)
                .text("detail.generator.condenser", () -> yesNo(generator.isCondenser()))
                .doubleField("detail.generator.ratedS", DetailForm.MVA, GuiParameters.Quantity.POWER, generator::getRatedS, generator::setRatedS)
                .booleanField("detail.fictitious", generator::isFictitious, generator::setFictitious);

        form.section("detail.section.connection")
                .text("desktop.common.equipment.voltageLevel", () -> generator.getTerminal().getVoltageLevel().getNameOrId())
                .text("desktop.common.equipment.bus", () -> {
                    Bus bus = generator.getTerminal().getBusView().getBus();
                    return bus == null ? null : bus.getId();
                })
                .booleanField("desktop.common.column.connected", () -> generator.getTerminal().isConnected(), connected -> {
                    Terminal terminal = generator.getTerminal();
                    if (!(connected ? terminal.connect() : terminal.disconnect())) {
                        throw new PowsyblException(Messages.get("desktop.common.editError.connectionUnchanged"));
                    }
                })
                .text("desktop.common.column.connectedComponent", () -> component(true))
                .text("desktop.common.column.synchronousComponent", () -> component(false));

        form.section("detail.section.activePower")
                .doubleField("desktop.common.column.minP", DetailForm.MW, GuiParameters.Quantity.POWER, generator::getMinP, generator::setMinP)
                .doubleField("desktop.common.column.maxP", DetailForm.MW, GuiParameters.Quantity.POWER, generator::getMaxP, generator::setMaxP)
                .doubleField("generators.column.targetP", DetailForm.MW, GuiParameters.Quantity.POWER, generator::getTargetP, generator::setTargetP);

        form.section("detail.section.localTargets")
                .doubleField("detail.localTargetV", DetailForm.KV, GuiParameters.Quantity.VOLTAGE, generator::getLocalTargetV, generator::setLocalTargetV)
                .doubleField("detail.localTargetQ", DetailForm.MVAR, GuiParameters.Quantity.POWER, generator::getLocalTargetQ, generator::setLocalTargetQ);

        form.section("desktop.common.column.voltageRegulation");
        addVoltageRegulationDefinedRow();
        form
                .booleanField("desktop.common.column.regulating", generator::isRegulating,
                        regulating -> VoltageRegulations.setRegulating(generator, RegulationMode.VOLTAGE, regulating))
                // IIDM's settable modes for a generator (VoltageRegulationUtils), reactive power only with a remote terminal
                .choiceField("desktop.common.column.regulationMode", List.of(RegulationMode.VOLTAGE, RegulationMode.REACTIVE_POWER), VoltageRegulations.MODE_FORMAT,
                        () -> regulation() == null ? null : regulation().getMode(), mode -> VoltageRegulations.setMode(generator, mode))
                .doubleField("detail.voltageRegulation.targetValue", null, GuiParameters.Quantity.VOLTAGE,
                        () -> regulation() == null ? Double.NaN : regulation().getTargetValue(),
                        value -> VoltageRegulations.setTargetValue(generator, RegulationMode.VOLTAGE, value))
                .doubleField("desktop.common.column.targetDeadband", null, GuiParameters.Quantity.VOLTAGE,
                        () -> regulation() == null ? Double.NaN : regulation().getTargetDeadband(),
                        value -> VoltageRegulations.setTargetDeadband(generator, RegulationMode.VOLTAGE, value))
                .doubleField("detail.voltageRegulation.slope", null, GuiParameters.Quantity.POWER_FACTOR,
                        () -> regulation() == null ? Double.NaN : regulation().getSlope(), this::setSlope);
        addRegulatingTerminalRow();

        form.section("desktop.common.columnGroup.solvedValues")
                .value("desktop.common.column.p", DetailForm.MW, GuiParameters.Quantity.POWER, () -> generator.getTerminal().getP())
                .value("desktop.common.column.q", DetailForm.MVAR, GuiParameters.Quantity.POWER, () -> generator.getTerminal().getQ())
                .value("desktop.common.column.i", DetailForm.A, GuiParameters.Quantity.CURRENT, () -> generator.getTerminal().getI())
                .value("detail.busV", DetailForm.KV, GuiParameters.Quantity.VOLTAGE, () -> busValue(generator.getTerminal(), true))
                .value("detail.busAngle", DetailForm.DEGREE, GuiParameters.Quantity.ANGLE, () -> busValue(generator.getTerminal(), false))
                .value("generators.column.regulatedBusVoltage", DetailForm.KV, GuiParameters.Quantity.VOLTAGE,
                        () -> busValue(generator.getRegulatingTerminal(), true));
    }

    private VoltageRegulation regulation() {
        return generator.getVoltageRegulation();
    }

    private void setSlope(double slope) {
        if (regulation() != null) {
            regulation().setSlope(slope);
        } else {
            generator.newVoltageRegulation().withMode(RegulationMode.VOLTAGE).withRegulating(false).withSlope(slope).build();
        }
    }

    private void addVoltageRegulationDefinedRow() {
        Label label = new Label();
        Button removeButton = iconButton("mdi2d-delete-outline", "detail.voltageRegulation.remove");
        removeButton.disableProperty().bind(form.editableProperty().not());
        removeButton.setOnAction(event -> form.apply(removeButton, generator::removeVoltageRegulation));
        HBox box = new HBox(4, label, removeButton);
        box.setAlignment(Pos.CENTER_LEFT);
        form.node("detail.voltageRegulation.defined", box, null, () -> {
            label.setText(yesNo(regulation() != null));
            removeButton.setVisible(regulation() != null);
        });
    }

    private void addRegulatingTerminalRow() {
        Label localLabel = new Label(Messages.get("detail.voltageRegulation.local"));
        Hyperlink link = new Hyperlink();
        link.getStyleClass().add("container-link");
        link.setOnAction(event -> {
            Terminal terminal = regulation() == null ? null : regulation().getTerminal();
            if (terminal != null) {
                sceneModel.navigate(NavigationEvent.create(NavigationType.SUBSTATIONS, ContainerNavigationState.create(terminal.getVoltageLevel())));
            }
        });
        Button pickButton = iconButton("mdi2p-pencil", "detail.voltageRegulation.pickTerminal");
        pickButton.disableProperty().bind(form.editableProperty().not());
        pickButton.setOnAction(event -> TerminalPickerDialog.show(pickButton.getScene().getWindow(), mainModel)
                .ifPresent(terminal -> form.apply(pickButton, () -> setRegulatingTerminal(terminal))));
        Button localButton = iconButton("mdi2m-map-marker-off-outline", "detail.voltageRegulation.setLocal");
        localButton.disableProperty().bind(form.editableProperty().not());
        localButton.setOnAction(event -> form.apply(localButton, () -> setRegulatingTerminal(null)));
        HBox box = new HBox(4, localLabel, link, pickButton, localButton);
        box.setAlignment(Pos.CENTER_LEFT);
        form.node("detail.voltageRegulation.terminal", box, null, () -> {
            Terminal terminal = regulation() == null ? null : regulation().getTerminal();
            boolean remote = terminal != null;
            localLabel.setVisible(!remote);
            localLabel.setManaged(!remote);
            link.setVisible(remote);
            link.setManaged(remote);
            link.setText(remote ? TerminalPickerDialog.terminalLabel(terminal) : "");
            localButton.setVisible(remote);
        });
    }

    /**
     * A null terminal regulates locally: in voltage mode, at the local target V, as IIDM requires. A remote one
     * regulates at the current target value, the local target V if regulating locally until now.
     */
    void setRegulatingTerminal(Terminal terminal) {
        VoltageRegulation regulation = regulation();
        if (regulation != null && terminal == null) {
            regulation.setMode(RegulationMode.VOLTAGE);
            regulation.setTerminal(null, Double.NaN);
        } else if (regulation != null) {
            double targetValue = regulation.getTargetValue();
            regulation.setTerminal(terminal, Double.isNaN(targetValue) ? generator.getLocalTargetV() : targetValue);
        } else {
            generator.newVoltageRegulation().withMode(RegulationMode.VOLTAGE).withRegulating(false).withTerminal(terminal)
                    .withTargetValue(generator.getLocalTargetV()).build();
        }
    }

    private static Button iconButton(String icon, String tooltipKey) {
        Button button = new Button();
        button.setGraphic(new FontIcon(icon));
        button.getStyleClass().add("icon-button");
        button.setTooltip(new Tooltip(Messages.get(tooltipKey)));
        return button;
    }

    ReactiveLimitsPane.OperatingPoints operatingPoints() {
        // generator convention, the terminal's being load convention
        return new ReactiveLimitsPane.OperatingPoints(generator.getTargetP(), generator.getLocalTargetQ(),
                -generator.getTerminal().getP(), -generator.getTerminal().getQ());
    }

    private void refresh() {
        titleLabel.setText(Messages.get("detail.title", Messages.get("desktop.common.equipment.generator"), generator.getNameOrId()));
        VoltageLevel voltageLevel = generator.getTerminal().getVoltageLevel();
        voltageLevelLink.setText(voltageLevel.getNameOrId());
        form.refresh();
        mainChart.setLimits(CapabilityChart.points(generator.getReactiveLimits(), generator.getMinP(), generator.getMaxP()));
        ReactiveLimitsPane.OperatingPoints points = operatingPoints();
        mainChart.setOperatingPoints(points.targetP(), points.targetQ(), points.solvedP(), points.solvedQ());
        reactiveLimitsPane.refresh();
        extensionsPane.refresh();
        propertiesPane.refresh();
        aliasesPane.refresh();
    }

    private String component(boolean connected) {
        Bus bus = generator.getTerminal().getBusView().getBus();
        if (bus == null) {
            return null;
        }
        var component = connected ? bus.getConnectedComponent() : bus.getSynchronousComponent();
        return component == null ? null : String.valueOf(component.getNum());
    }

    private static double busValue(Terminal terminal, boolean voltage) {
        Bus bus = terminal == null ? null : terminal.getBusView().getBus();
        if (bus == null) {
            return Double.NaN;
        }
        return voltage ? bus.getV() : bus.getAngle();
    }

    private static String yesNo(boolean value) {
        return Messages.get(value ? "detail.yes" : "detail.no");
    }

    public Generator getGenerator() {
        return generator;
    }
}
