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
import com.powsybl.iidm.network.Component;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Terminal;
import com.powsybl.iidm.network.regulation.RegulationMode;
import com.powsybl.iidm.network.regulation.VoltageRegulation;
import com.powsybl.iidm.network.regulation.VoltageRegulationHolder;
import com.powsybl.powsybldesktop.SceneModel;
import com.powsybl.powsybldesktop.navigation.ContainerNavigationState;
import com.powsybl.powsybldesktop.navigation.NavigationEvent;
import com.powsybl.powsybldesktop.navigation.NavigationType;
import com.powsybl.powsybldesktop.network.tables.VoltageRegulations;
import com.powsybl.powsybldesktop.parameters.GuiParameters;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.util.StringConverter;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * {@link DetailForm} sections shared by several equipment types: a terminal's connection and solved values, a
 * {@link VoltageRegulationHolder}'s voltage regulation as IIDM holds it.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class DetailSections {

    private DetailSections() {
    }

    /**
     * Voltage level, bus, connection (editable) and components of {@code terminal}, in a new section unless
     * {@code titleKey} is null.
     */
    public static void connection(DetailForm form, String titleKey, Supplier<Terminal> terminal) {
        if (titleKey != null) {
            form.section(titleKey);
        }
        form.text("desktop.common.equipment.voltageLevel", () -> terminal.get().getVoltageLevel().getNameOrId())
                .text("desktop.common.equipment.bus", () -> {
                    Bus bus = terminal.get().getBusView().getBus();
                    return bus == null ? null : bus.getId();
                })
                .booleanField("desktop.common.column.connected", () -> terminal.get().isConnected(), connected -> {
                    Terminal t = terminal.get();
                    if (!(connected ? t.connect() : t.disconnect())) {
                        throw new PowsyblException(Messages.get("desktop.common.editError.connectionUnchanged"));
                    }
                })
                .text("desktop.common.column.connectedComponent", () -> component(terminal.get(), Bus::getConnectedComponent))
                .text("desktop.common.column.synchronousComponent", () -> component(terminal.get(), Bus::getSynchronousComponent));
    }

    /**
     * Read-only P, Q, I and the bus voltage and angle of {@code terminal}, in a new section unless {@code titleKey}
     * is null.
     */
    public static void solvedValues(DetailForm form, String titleKey, Supplier<Terminal> terminal) {
        if (titleKey != null) {
            form.section(titleKey);
        }
        form.value("desktop.common.column.p", DetailForm.MW, GuiParameters.Quantity.POWER, () -> terminal.get().getP())
                .value("desktop.common.column.q", DetailForm.MVAR, GuiParameters.Quantity.POWER, () -> terminal.get().getQ())
                .value("desktop.common.column.i", DetailForm.A, GuiParameters.Quantity.CURRENT, () -> terminal.get().getI());
        busValues(form, terminal);
    }

    public static void busValues(DetailForm form, Supplier<Terminal> terminal) {
        form.value("detail.busV", DetailForm.KV, GuiParameters.Quantity.VOLTAGE, () -> busValue(terminal.get(), true))
                .value("detail.busAngle", DetailForm.DEGREE, GuiParameters.Quantity.ANGLE, () -> busValue(terminal.get(), false));
    }

    public static double busValue(Terminal terminal, boolean voltage) {
        Bus bus = terminal == null ? null : terminal.getBusView().getBus();
        if (bus == null) {
            return Double.NaN;
        }
        return voltage ? bus.getV() : bus.getAngle();
    }

    private static String component(Terminal terminal, Function<Bus, Component> componentGetter) {
        Bus bus = terminal.getBusView().getBus();
        Component component = bus == null ? null : componentGetter.apply(bus);
        return component == null ? null : String.valueOf(component.getNum());
    }

    /**
     * The regulation's attributes as IIDM holds them, without the tables' simplifications, editing it is creating
     * it if missing (not regulating, in {@code modes}' first).
     *
     * @param modes the modes IIDM lets this holder type take (see {@code VoltageRegulationUtils}), reactive power ones
     *              only with a remote terminal
     */
    public static void voltageRegulation(DetailForm form, String titleKey, VoltageRegulationHolder<?> holder, List<RegulationMode> modes,
                                         SceneModel sceneModel) {
        RegulationMode defaultMode = modes.getFirst();
        Supplier<VoltageRegulation> regulation = holder::getVoltageRegulation;
        form.section(titleKey);
        regulationDefinedRow(form, holder);
        form.booleanField("desktop.common.column.regulating", holder::isRegulating,
                        regulating -> VoltageRegulations.setRegulating(holder, defaultMode, regulating))
                .choiceField("desktop.common.column.regulationMode", modes, VoltageRegulations.MODE_FORMAT,
                        () -> regulation.get() == null ? null : regulation.get().getMode(), mode -> VoltageRegulations.setMode(holder, mode))
                .doubleField("detail.voltageRegulation.targetValue", null, GuiParameters.Quantity.VOLTAGE,
                        () -> regulation.get() == null ? Double.NaN : regulation.get().getTargetValue(),
                        value -> VoltageRegulations.setTargetValue(holder, defaultMode, value))
                .doubleField("desktop.common.column.targetDeadband", null, GuiParameters.Quantity.VOLTAGE,
                        () -> regulation.get() == null ? Double.NaN : regulation.get().getTargetDeadband(),
                        value -> VoltageRegulations.setTargetDeadband(holder, defaultMode, value))
                .doubleField("detail.voltageRegulation.slope", null, GuiParameters.Quantity.POWER_FACTOR,
                        () -> regulation.get() == null ? Double.NaN : regulation.get().getSlope(), slope -> {
                            if (regulation.get() != null) {
                                regulation.get().setSlope(slope);
                            } else {
                                holder.newVoltageRegulation().withMode(defaultMode).withRegulating(false).withSlope(slope).build();
                            }
                        });
        terminalRow(form, "detail.voltageRegulation.terminal", sceneModel,
                () -> regulation.get() == null ? null : regulation.get().getTerminal(),
                terminal -> setRegulatingTerminal(holder, defaultMode, terminal));
        form.value("generators.column.regulatedBusVoltage", DetailForm.KV, GuiParameters.Quantity.VOLTAGE,
                () -> busValue(holder.getRegulatingTerminal(), true));
    }

    /**
     * Local target V and, unless {@code withQ} is false (e.g. a shunt compensator), Q of a holder regulating locally.
     */
    public static void localTargets(DetailForm form, VoltageRegulationHolder<?> holder, boolean withQ) {
        form.section("detail.section.localTargets")
                .doubleField("detail.localTargetV", DetailForm.KV, GuiParameters.Quantity.VOLTAGE, holder::getLocalTargetV, holder::setLocalTargetV);
        if (withQ) {
            form.doubleField("detail.localTargetQ", DetailForm.MVAR, GuiParameters.Quantity.POWER, holder::getLocalTargetQ, holder::setLocalTargetQ);
        }
    }

    /**
     * Each terminal's connection and solved values, in a section per side.
     */
    public static void sides(DetailForm form, List<Supplier<Terminal>> terminals) {
        for (int i = 0; i < terminals.size(); i++) {
            form.section("limits.side", i + 1);
            connection(form, null, terminals.get(i));
            solvedValues(form, null, terminals.get(i));
        }
    }

    /**
     * A link opening another equipment's detail view, e.g. a converter station's HVDC line.
     */
    public static void equipmentLink(DetailForm form, String labelKey, SceneModel sceneModel, Supplier<? extends Identifiable<?>> target) {
        Hyperlink link = new Hyperlink();
        link.getStyleClass().add("container-link");
        link.setOnAction(event -> EquipmentDetails.open(sceneModel, target.get()));
        form.node(labelKey, link, null, () -> {
            Identifiable<?> identifiable = target.get();
            link.setText(identifiable == null ? "-" : identifiable.getNameOrId());
            link.setDisable(identifiable == null);
        });
    }

    private static void regulationDefinedRow(DetailForm form, VoltageRegulationHolder<?> holder) {
        Label label = new Label();
        Button removeButton = iconButton("mdi2d-delete-outline", "detail.voltageRegulation.remove");
        removeButton.disableProperty().bind(form.editableProperty().not());
        removeButton.setOnAction(event -> form.apply(removeButton, holder::removeVoltageRegulation));
        HBox box = new HBox(4, label, removeButton);
        box.setAlignment(Pos.CENTER_LEFT);
        form.node("detail.voltageRegulation.defined", box, null, () -> {
            label.setText(yesNo(holder.getVoltageRegulation() != null));
            removeButton.setVisible(holder.getVoltageRegulation() != null);
        });
    }

    /**
     * A null terminal regulates locally: in the default (voltage) mode, at the local target V, as IIDM requires. A
     * remote one regulates at the current target value, the local target V if regulating locally until now.
     */
    static void setRegulatingTerminal(VoltageRegulationHolder<?> holder, RegulationMode defaultMode, Terminal terminal) {
        VoltageRegulation regulation = holder.getVoltageRegulation();
        if (regulation != null && terminal == null) {
            regulation.setMode(defaultMode);
            regulation.setTerminal(null, Double.NaN);
        } else if (regulation != null) {
            double targetValue = regulation.getTargetValue();
            regulation.setTerminal(terminal, Double.isNaN(targetValue) ? holder.getLocalTargetV() : targetValue);
        } else {
            holder.newVoltageRegulation().withMode(defaultMode).withRegulating(false).withTerminal(terminal)
                    .withTargetValue(holder.getLocalTargetV()).build();
        }
    }

    /**
     * A regulated terminal: "local" or a link to its voltage level, a button choosing another one (see
     * {@link TerminalPickerDialog}), and one setting it back to none, i.e. local.
     */
    public static void terminalRow(DetailForm form, String labelKey, SceneModel sceneModel, Supplier<Terminal> getter,
                                   Consumer<Terminal> setter) {
        Label localLabel = new Label(Messages.get("detail.voltageRegulation.local"));
        Hyperlink link = new Hyperlink();
        link.getStyleClass().add("container-link");
        link.setOnAction(event -> {
            Terminal terminal = getter.get();
            if (terminal != null) {
                sceneModel.navigate(NavigationEvent.create(NavigationType.SUBSTATIONS, ContainerNavigationState.create(terminal.getVoltageLevel())));
            }
        });
        Button pickButton = iconButton("mdi2p-pencil", "detail.voltageRegulation.pickTerminal");
        pickButton.disableProperty().bind(form.editableProperty().not());
        pickButton.setOnAction(event -> TerminalPickerDialog.show(pickButton.getScene().getWindow(), sceneModel)
                .ifPresent(terminal -> form.apply(pickButton, () -> setter.accept(terminal))));
        Button localButton = iconButton("mdi2m-map-marker-off-outline", "detail.voltageRegulation.setLocal");
        localButton.disableProperty().bind(form.editableProperty().not());
        localButton.setOnAction(event -> form.apply(localButton, () -> setter.accept(null)));
        HBox box = new HBox(4, localLabel, link, pickButton, localButton);
        box.setAlignment(Pos.CENTER_LEFT);
        form.node(labelKey, box, null, () -> {
            Terminal terminal = getter.get();
            boolean remote = terminal != null;
            localLabel.setVisible(!remote);
            localLabel.setManaged(!remote);
            link.setVisible(remote);
            link.setManaged(remote);
            link.setText(remote ? TerminalPickerDialog.terminalLabel(terminal) : "");
            localButton.setVisible(remote);
        });
    }

    static Button iconButton(String icon, String tooltipKey) {
        Button button = new Button();
        button.setGraphic(new FontIcon(icon));
        button.getStyleClass().add("icon-button");
        button.setTooltip(new Tooltip(Messages.get(tooltipKey)));
        return button;
    }

    public static String yesNo(boolean value) {
        return Messages.get(value ? "detail.yes" : "detail.no");
    }

    /**
     * Shows an enum's constant names, for IIDM enums without a translated label.
     */
    public static <E extends Enum<E>> StringConverter<E> enumFormat() {
        return new StringConverter<>() {
            @Override
            public String toString(E value) {
                return value == null ? "" : value.name();
            }

            @Override
            public E fromString(String string) {
                throw new UnsupportedOperationException();
            }
        };
    }

    public static <E extends Enum<E>> List<E> values(Class<E> type) {
        return Arrays.asList(type.getEnumConstants());
    }
}
