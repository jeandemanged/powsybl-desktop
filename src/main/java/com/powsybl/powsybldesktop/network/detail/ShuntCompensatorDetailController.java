/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.detail;

import com.powsybl.iidm.network.ShuntCompensator;
import com.powsybl.iidm.network.ShuntCompensatorLinearModel;
import com.powsybl.iidm.network.ShuntCompensatorModelType;
import com.powsybl.iidm.network.ShuntCompensatorNonLinearModel;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.iidm.network.regulation.RegulationMode;
import com.powsybl.powsybldesktop.navigation.ContainerNavigationState;
import com.powsybl.powsybldesktop.network.tables.ShuntCompensatorsController;
import com.powsybl.powsybldesktop.parameters.GuiParameters;
import javafx.geometry.Insets;
import javafx.scene.control.Tab;
import javafx.scene.control.TableView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.util.List;
import java.util.stream.IntStream;

/**
 * Shunt compensator detail: sections (count, model, a linear model's per-section susceptance and conductance, every
 * section count's in their own tab), local target V and voltage regulation. Replaces the former sections dialog of the
 * shunt compensators table.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class ShuntCompensatorDetailController extends AbstractEquipmentDetailController<ShuntCompensator> {

    // IIDM's settable modes for a shunt compensator (VoltageRegulationUtils)
    private static final List<RegulationMode> REGULATION_MODES = List.of(RegulationMode.VOLTAGE);

    @Override
    protected List<VoltageLevel> voltageLevels() {
        return List.of(equipment.getTerminal().getVoltageLevel());
    }

    @Override
    protected ContainerNavigationState.ContainerTab containerTab() {
        return ContainerNavigationState.ContainerTab.SHUNT_COMPENSATORS;
    }

    @Override
    protected void buildMain(DetailForm form) {
        DetailSections.connection(form, "detail.section.connection", equipment::getTerminal);
        form.section("detail.section.sections")
                .text("desktop.common.column.type", () -> ShuntCompensatorsController.typeLabel(equipment))
                .text("shuntCompensators.column.modelType", () -> ShuntCompensatorsController.modelTypeLabel(equipment.getModelType()))
                .intField("detail.shunt.sectionCount", () -> equipment.findSectionCount().isPresent() ? equipment.getSectionCount() : null,
                        count -> equipment.setSectionCount(DetailForm.mandatory(count)));
        if (equipment.getModelType() == ShuntCompensatorModelType.LINEAR) {
            ShuntCompensatorLinearModel model = equipment.getModel(ShuntCompensatorLinearModel.class);
            form.intField("detail.shunt.maximumSectionCount", equipment::getMaximumSectionCount,
                            count -> model.setMaximumSectionCount(DetailForm.mandatory(count)))
                    .doubleField("detail.shunt.bPerSection", DetailForm.SIEMENS, GuiParameters.Quantity.ADMITTANCE,
                            model::getBPerSection, model::setBPerSection)
                    .doubleField("detail.shunt.gPerSection", DetailForm.SIEMENS, GuiParameters.Quantity.ADMITTANCE,
                            model::getGPerSection, model::setGPerSection);
        } else {
            form.text("detail.shunt.maximumSectionCount", () -> String.valueOf(equipment.getMaximumSectionCount()));
        }
        form.value("detail.shunt.b", DetailForm.SIEMENS, GuiParameters.Quantity.ADMITTANCE, equipment::getB)
                .value("detail.shunt.g", DetailForm.SIEMENS, GuiParameters.Quantity.ADMITTANCE, equipment::getG);
        DetailSections.localTargets(form, equipment, false);
        DetailSections.voltageRegulation(form, "desktop.common.column.voltageRegulation", equipment, REGULATION_MODES, sceneModel);
        DetailSections.solvedValues(form, "desktop.common.columnGroup.solvedValues", equipment::getTerminal);
        form.text("detail.shunt.solvedSectionCount", () -> equipment.findSolvedSectionCount().isPresent()
                ? String.valueOf(equipment.getSolvedSectionCount()) : null);
    }

    /**
     * Every section count's susceptance and conductance, with the reactive and active power they draw at nominal
     * voltage: editable per section for a non-linear model, a linear model's being its per-section values (main tab).
     */
    @Override
    protected List<Tab> extraTabs() {
        boolean linear = equipment.getModelType() == ShuntCompensatorModelType.LINEAR;
        StringConverter<Double> admittance = DetailTables.doubleFormat(guiParameters(), GuiParameters.Quantity.ADMITTANCE);
        StringConverter<Double> power = DetailTables.doubleFormat(guiParameters(), GuiParameters.Quantity.POWER);
        TableView<Integer> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getColumns().setAll(List.of(
                DetailTables.readOnlyColumn("shuntCompensators.column.section", DetailTables.INTEGER_FORMAT, section -> section),
                linear ? DetailTables.readOnlyColumn("desktop.common.column.b", admittance, equipment::getB)
                        : DetailTables.editableColumn("desktop.common.column.b", admittance, equipment::getB,
                                (section, b) -> nonLinearSection(section).setB(b), onEdit()),
                linear ? DetailTables.readOnlyColumn("desktop.common.column.g", admittance, equipment::getG)
                        : DetailTables.editableColumn("desktop.common.column.g", admittance, equipment::getG,
                                (section, g) -> nonLinearSection(section).setG(g), onEdit()),
                DetailTables.readOnlyColumn("shuntCompensators.sections.column.q", power, section -> -equipment.getB(section) * nominalV2()),
                DetailTables.readOnlyColumn("shuntCompensators.sections.column.p", power, section -> equipment.getG(section) * nominalV2())));
        table.editableProperty().bind(register(DetailPart.of(() -> {
            table.getItems().setAll(IntStream.rangeClosed(1, equipment.getMaximumSectionCount()).boxed().toList());
            table.refresh();
        })).editableProperty());
        VBox content = new VBox(table);
        VBox.setVgrow(table, Priority.ALWAYS);
        content.setPadding(new Insets(8));
        return List.of(tab("detail.section.sections", content));
    }

    private ShuntCompensatorNonLinearModel.Section nonLinearSection(int section) {
        return equipment.getModel(ShuntCompensatorNonLinearModel.class).getAllSections().get(section - 1);
    }

    private double nominalV2() {
        return Math.pow(equipment.getTerminal().getVoltageLevel().getNominalV(), 2);
    }

    @Override
    protected List<ExtensionsPane.Editor<ShuntCompensator>> extensionEditors() {
        return ExtensionEditors.forInjection();
    }
}
