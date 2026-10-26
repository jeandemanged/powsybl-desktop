/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.detail;

import com.powsybl.iidm.network.PhaseTapChanger;
import com.powsybl.iidm.network.PhaseTapChangerHolder;
import com.powsybl.iidm.network.PhaseTapChangerStep;
import com.powsybl.iidm.network.PhaseTapChangerStepsReplacer;
import com.powsybl.iidm.network.RatioTapChanger;
import com.powsybl.iidm.network.RatioTapChangerHolder;
import com.powsybl.iidm.network.RatioTapChangerStepsReplacer;
import com.powsybl.iidm.network.TapChanger;
import com.powsybl.iidm.network.TapChangerStep;
import com.powsybl.iidm.network.regulation.RegulationMode;
import com.powsybl.powsybldesktop.SceneModel;
import com.powsybl.powsybldesktop.network.tables.TableColumnSupport;
import com.powsybl.powsybldesktop.parameters.GuiParameters;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.IntStream;

/**
 * One winding's ratio or phase tap changer: added or removed, its positions and regulation (a ratio tap changer's is a
 * {@link com.powsybl.iidm.network.regulation.VoltageRegulation}, shown as {@link DetailSections#voltageRegulation}
 * shows any), and its steps, edited in place, added or removed (IIDM then replaces them all). Shared by two- and
 * three-winding transformers' detail views.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class TapChangerPane extends VBox implements DetailPart {

    // IIDM's settable modes for a ratio tap changer (VoltageRegulationUtils): remote only
    private static final List<RegulationMode> RATIO_REGULATION_MODES = List.of(RegulationMode.VOLTAGE, RegulationMode.REACTIVE_POWER);

    private final boolean phase;
    private final Supplier<? extends TapChanger<?, ?, ?, ?>> tapChanger;
    private final Runnable creator;
    private final String titleKey;
    private final SceneModel sceneModel;
    private final Supplier<GuiParameters> guiParameters;
    private final Runnable onEdit;
    private final BooleanProperty editable = new SimpleBooleanProperty(true);
    private TapChanger<?, ?, ?, ?> shown;
    private DetailForm form;
    private TableView<Integer> stepsTable;

    private TapChangerPane(boolean phase, String titleKey, Supplier<? extends TapChanger<?, ?, ?, ?>> tapChanger, Runnable creator,
                           SceneModel sceneModel, Supplier<GuiParameters> guiParameters, Runnable onEdit) {
        super(6);
        this.phase = phase;
        this.titleKey = titleKey;
        this.tapChanger = tapChanger;
        this.creator = creator;
        this.sceneModel = sceneModel;
        this.guiParameters = Objects.requireNonNull(guiParameters);
        this.onEdit = Objects.requireNonNull(onEdit);
        rebuild();
    }

    public static TapChangerPane ratio(RatioTapChangerHolder holder, String titleKey, SceneModel sceneModel,
                                       Supplier<GuiParameters> guiParameters, Runnable onEdit) {
        return new TapChangerPane(false, titleKey, holder::getRatioTapChanger, () -> holder.newRatioTapChanger()
                .setLowTapPosition(0).setTapPosition(0).setLoadTapChangingCapabilities(false)
                .beginStep().setRho(1).setR(0).setX(0).setG(0).setB(0).endStep()
                .add(), sceneModel, guiParameters, onEdit);
    }

    public static TapChangerPane phase(PhaseTapChangerHolder holder, String titleKey, SceneModel sceneModel,
                                       Supplier<GuiParameters> guiParameters, Runnable onEdit) {
        return new TapChangerPane(true, titleKey, holder::getPhaseTapChanger, () -> holder.newPhaseTapChanger()
                .setLowTapPosition(0).setTapPosition(0).setLoadTapChangingCapabilities(false)
                .beginStep().setAlpha(0).setRho(1).setR(0).setX(0).setG(0).setB(0).endStep()
                .add(), sceneModel, guiParameters, onEdit);
    }

    @Override
    public BooleanProperty editableProperty() {
        return editable;
    }

    @Override
    public void refresh() {
        if (tapChanger.get() != shown) {
            rebuild();
        } else if (shown != null) {
            form.refresh();
            stepsTable.getItems().setAll(positions(shown));
            DetailTables.fitHeight(stepsTable, stepsTable.getItems().size());
        }
    }

    private void rebuild() {
        shown = tapChanger.get();
        Label title = new Label(Messages.get(titleKey));
        title.getStyleClass().add("section-title");
        if (shown == null) {
            Button addButton = new Button(Messages.get("desktop.common.action.add"));
            addButton.disableProperty().bind(editable.not());
            addButton.setOnAction(event -> {
                DetailForm.apply(addButton, creator, onEdit);
                refresh();
            });
            getChildren().setAll(title, new Label(Messages.get("detail.keyValue.empty")), addButton);
            form = null;
            stepsTable = null;
            return;
        }
        form = new DetailForm(guiParameters, onEdit);
        form.editableProperty().bind(editable);
        TapChanger<?, ?, ?, ?> changer = shown;
        form.section(null)
                .booleanField("detail.tapChanger.loadTapChangingCapabilities", changer::hasLoadTapChangingCapabilities,
                        changer::setLoadTapChangingCapabilities)
                .intField("detail.tapChanger.lowTapPosition", changer::getLowTapPosition, low -> changer.setLowTapPosition(DetailForm.mandatory(low)))
                .text("detail.tapChanger.highTapPosition", () -> String.valueOf(changer.getHighTapPosition()))
                .intField("transformers.tapChanger.column.tapPosition", () -> changer.findTapPosition().isPresent() ? changer.getTapPosition() : null,
                        position -> changer.setTapPosition(DetailForm.mandatory(position)))
                .text("desktop.common.column.solvedTap", () -> changer.getSolvedTapPosition() == null ? null : changer.getSolvedTapPosition().toString());
        if (changer instanceof RatioTapChanger ratio) {
            DetailSections.voltageRegulation(form, "detail.tapChanger.regulation", ratio, RATIO_REGULATION_MODES, sceneModel);
        } else if (changer instanceof PhaseTapChanger phaseChanger) {
            phaseRegulation(phaseChanger);
        }

        stepsTable = stepsTable(changer);
        Button addStepButton = new Button(Messages.get("detail.tapChanger.addStep"));
        addStepButton.disableProperty().bind(editable.not());
        addStepButton.setOnAction(event -> DetailForm.apply(addStepButton, () -> addStep(changer), onEdit));
        Button removeStepButton = new Button(Messages.get("detail.tapChanger.removeStep"));
        removeStepButton.disableProperty().bind(editable.not().or(stepsTable.getSelectionModel().selectedItemProperty().isNull()));
        removeStepButton.setOnAction(event -> DetailForm.apply(removeStepButton,
                () -> removeStep(changer, stepsTable.getSelectionModel().getSelectedItem()), onEdit));
        Button removeButton = new Button(Messages.get("desktop.common.action.remove"));
        removeButton.disableProperty().bind(editable.not());
        removeButton.setOnAction(event -> {
            DetailForm.apply(removeButton, changer::remove, onEdit);
            refresh();
        });
        HBox buttons = new HBox(4, addStepButton, removeStepButton, removeButton);
        buttons.setAlignment(Pos.CENTER_LEFT);
        Label stepsTitle = new Label(Messages.get("detail.tapChanger.steps"));
        stepsTitle.getStyleClass().add("section-title");
        getChildren().setAll(title, form.getNode(), stepsTitle, stepsTable, buttons);
    }

    private void phaseRegulation(PhaseTapChanger changer) {
        form.section("detail.tapChanger.regulation")
                .booleanField("desktop.common.column.regulating", changer::isRegulating, changer::setRegulating)
                .choiceField("desktop.common.column.regulationMode", DetailSections.values(PhaseTapChanger.RegulationMode.class),
                        DetailSections.enumFormat(), changer::getRegulationMode, changer::setRegulationMode)
                .doubleField("desktop.common.column.regulationValue", null, GuiParameters.Quantity.POWER,
                        changer::getRegulationValue, changer::setRegulationValue)
                .doubleField("desktop.common.column.targetDeadband", null, GuiParameters.Quantity.POWER,
                        changer::getTargetDeadband, changer::setTargetDeadband);
        DetailSections.terminalRow(form, "detail.voltageRegulation.terminal", sceneModel, changer::getRegulationTerminal,
                changer::setRegulationTerminal);
    }

    private TableView<Integer> stepsTable(TapChanger<?, ?, ?, ?> changer) {
        TableView<Integer> table = new TableView<>();
        table.editableProperty().bind(editable);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        List<TableColumn<Integer, ?>> columns = new ArrayList<>();
        columns.add(DetailTables.readOnlyColumn("transformers.tapChanger.column.tapPosition", DetailTables.INTEGER_FORMAT, position -> position));
        if (phase) {
            columns.add(stepColumn("transformers.tapChanger.column.alpha", GuiParameters.Quantity.ANGLE,
                    position -> ((PhaseTapChangerStep) changer.getStep(position)).getAlpha(),
                    (position, value) -> ((PhaseTapChangerStep) changer.getStep(position)).setAlpha(value)));
        }
        columns.add(stepColumn("transformers.tapChanger.column.rho", GuiParameters.Quantity.RHO,
                position -> changer.getStep(position).getRho(), (position, value) -> changer.getStep(position).setRho(value)));
        columns.add(stepColumn("transformers.tapChanger.column.r", GuiParameters.Quantity.PERCENTAGE,
                position -> changer.getStep(position).getR(), (position, value) -> changer.getStep(position).setR(value)));
        columns.add(stepColumn("transformers.tapChanger.column.x", GuiParameters.Quantity.PERCENTAGE,
                position -> changer.getStep(position).getX(), (position, value) -> changer.getStep(position).setX(value)));
        columns.add(stepColumn("transformers.tapChanger.column.g", GuiParameters.Quantity.PERCENTAGE,
                position -> changer.getStep(position).getG(), (position, value) -> changer.getStep(position).setG(value)));
        columns.add(stepColumn("transformers.tapChanger.column.b", GuiParameters.Quantity.PERCENTAGE,
                position -> changer.getStep(position).getB(), (position, value) -> changer.getStep(position).setB(value)));
        table.getColumns().setAll(columns);
        table.getItems().setAll(positions(changer));
        DetailTables.fitHeight(table, table.getItems().size());
        return table;
    }

    private TableColumn<Integer, Double> stepColumn(String titleKey, GuiParameters.Quantity quantity, Function<Integer, Double> getter,
                                                    BiConsumer<Integer, Double> setter) {
        StringConverter<Double> format = TableColumnSupport.doubleFormat(guiParameters, quantity);
        TableColumn<Integer, Double> column = DetailTables.editableColumn(titleKey, format, getter, setter, onEdit);
        column.getStyleClass().add("right-aligned-column");
        return column;
    }

    private static List<Integer> positions(TapChanger<?, ?, ?, ?> changer) {
        return IntStream.rangeClosed(changer.getLowTapPosition(), changer.getHighTapPosition()).boxed().toList();
    }

    /**
     * Appends a copy of the highest step.
     */
    private static void addStep(TapChanger<?, ?, ?, ?> changer) {
        List<TapChangerStep<?>> steps = new ArrayList<>(steps(changer));
        steps.add(steps.getLast());
        replaceSteps(changer, steps);
    }

    private static void removeStep(TapChanger<?, ?, ?, ?> changer, int position) {
        List<TapChangerStep<?>> steps = new ArrayList<>(steps(changer));
        steps.remove(position - changer.getLowTapPosition());
        replaceSteps(changer, steps);
    }

    private static List<TapChangerStep<?>> steps(TapChanger<?, ?, ?, ?> changer) {
        return positions(changer).stream().<TapChangerStep<?>>map(changer::getStep).toList();
    }

    private static void replaceSteps(TapChanger<?, ?, ?, ?> changer, List<TapChangerStep<?>> steps) {
        if (changer instanceof PhaseTapChanger phaseChanger) {
            PhaseTapChangerStepsReplacer replacer = phaseChanger.stepsReplacer();
            for (TapChangerStep<?> step : steps) {
                replacer.beginStep().setAlpha(((PhaseTapChangerStep) step).getAlpha()).setRho(step.getRho())
                        .setR(step.getR()).setX(step.getX()).setG(step.getG()).setB(step.getB()).endStep();
            }
            replacer.replaceSteps();
        } else {
            RatioTapChangerStepsReplacer replacer = ((RatioTapChanger) changer).stepsReplacer();
            for (TapChangerStep<?> step : steps) {
                replacer.beginStep().setRho(step.getRho()).setR(step.getR()).setX(step.getX()).setG(step.getG()).setB(step.getB()).endStep();
            }
            replacer.replaceSteps();
        }
    }

}
