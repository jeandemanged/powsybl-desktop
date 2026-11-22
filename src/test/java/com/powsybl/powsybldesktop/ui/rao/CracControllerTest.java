/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.ui.rao;

import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.ContingencyElement;
import com.powsybl.contingency.LineContingency;
import com.powsybl.contingency.list.DefaultContingencyList;
import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.TwoSides;
import com.powsybl.iidm.network.test.PhaseShifterTestCaseFactory;
import com.powsybl.openrao.data.crac.api.Crac;
import com.powsybl.openrao.data.crac.api.Instant;
import com.powsybl.openrao.data.crac.api.InstantKind;
import com.powsybl.openrao.data.crac.api.cnec.FlowCnec;
import com.powsybl.openrao.data.crac.api.networkaction.NetworkAction;
import com.powsybl.openrao.data.crac.api.parameters.CracCreationParameters;
import com.powsybl.openrao.data.crac.api.rangeaction.RangeAction;
import com.powsybl.openrao.data.crac.api.rangeaction.VariationDirection;
import com.powsybl.powsybldesktop.core.MainModel;
import com.powsybl.powsybldesktop.core.NetworkStudy;
import com.powsybl.powsybldesktop.notification.Notification;
import com.powsybl.powsybldesktop.notification.NotificationStatus;
import com.powsybl.powsybldesktop.rao.CnecEdits;
import com.powsybl.powsybldesktop.rao.CracEdits;
import com.powsybl.powsybldesktop.rao.CracIO;
import com.powsybl.powsybldesktop.rao.RangeActionEdits;
import com.powsybl.powsybldesktop.rao.RaoTestCases;
import com.powsybl.powsybldesktop.rao.UsageRuleEdits;
import com.powsybl.powsybldesktop.rao.UsageRuleEdits.UsageRuleSpec;
import com.powsybl.powsybldesktop.ui.testutil.AbstractHeadlessApplicationTest;
import com.powsybl.powsybldesktop.ui.window.SceneModel;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.util.WaitForAsyncUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class CracControllerTest extends AbstractHeadlessApplicationTest {

    private final Network network = RaoTestCases.network();
    private MainModel mainModel;
    private NetworkStudy study;
    private CracController controller;
    // the formats the import dialog was asked for
    private final List<String> askedFormats = new ArrayList<>();

    @TempDir
    Path tempDir;

    // the ids the element picker answers, and the ids it was given as already present
    private final List<String> picked = new ArrayList<>();
    private final List<Set<String>> presentWhenPicked = new ArrayList<>();
    // the branches picked, and the states and sides of the flow CNECs asked
    private final List<String> pickedBranches = new ArrayList<>();
    private AddFlowCnecsDialog.Request flowCnecsRequest;
    // the network elements picked to be acted on
    private final List<String> pickedActionElements = new ArrayList<>();
    // the network elements picked for range actions, the types they were picked for, and the country of a counter-trade
    private final List<String> pickedRangeElements = new ArrayList<>();
    private final List<RangeActionEdits.Type> pickedRangeTypes = new ArrayList<>();
    private Optional<Country> country = Optional.empty();
    // the usage rule asked to be added
    private Optional<UsageRuleSpec> usageRule = Optional.empty();

    // the import dialog and the element picker, modal: answered
    private final class AnsweringCracController extends CracController {
        @Override
        Optional<CracCreationParameters> askParameters(String fileName, String format) {
            askedFormats.add(format);
            return Optional.of(new CracCreationParameters());
        }

        @Override
        List<String> pickContingencyElements(Set<String> present) {
            presentWhenPicked.add(present);
            return List.copyOf(picked);
        }

        @Override
        List<String> pickBranches(String title) {
            return List.copyOf(pickedBranches);
        }

        @Override
        List<String> pickActionElements(Set<String> present) {
            presentWhenPicked.add(present);
            return List.copyOf(pickedActionElements);
        }

        @Override
        List<String> pickRangeActionElements(RangeActionEdits.Type type, Set<String> present) {
            pickedRangeTypes.add(type);
            presentWhenPicked.add(present);
            return List.copyOf(pickedRangeElements);
        }

        @Override
        Optional<Country> askCountry() {
            return country;
        }

        @Override
        Optional<AddFlowCnecsDialog.Request> askFlowCnecs(int branchCount) {
            return Optional.of(flowCnecsRequest);
        }

        @Override
        Optional<UsageRuleSpec> askUsageRule() {
            return usageRule;
        }

        Optional<CracCreationParameters> ask(String fileName, String format) {
            return super.askParameters(fileName, format);
        }
    }

    @Override
    public void start(Stage stage) throws IOException {
        mainModel = new MainModel();
        mainModel.addNetwork(network);
        mainModel.setNetwork(network);
        study = mainModel.getStudy(network);

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/powsybl/powsybldesktop/ui/rao/crac-view.fxml"), Messages.bundle());
        loader.setControllerFactory(_ -> new AnsweringCracController());
        Parent root = loader.load();
        controller = loader.getController();
        controller.setSceneModel(SceneModel.main(mainModel));

        stage.setScene(new Scene(root, 1000, 600));
        stage.show();
    }

    @AfterEach
    void tearDown() {
        interact(controller::dispose);
    }

    private Optional<Notification> notification(String keyPrefix) {
        return mainModel.getNotificationsModel().getNotifications().stream()
                .filter(notification -> notification.messageKey().startsWith(keyPrefix) && notification.status() != NotificationStatus.RUNNING)
                .findFirst();
    }

    @Test
    void emptyWithoutCrac() {
        assertEquals(Messages.get("crac.none"), controller.headerLabel.getText());
        assertTrue(controller.exportButton.isDisabled());
        assertTrue(controller.removeButton.isDisabled());
        assertFalse(controller.importButton.isDisabled());
        assertTrue(controller.contingenciesListView.getItems().isEmpty());
    }

    @Test
    void listsTheCracAndDetailsTheSelectedObject() {
        interact(() -> study.setCrac(RaoTestCases.crac(network), List.of("a report line")));

        assertEquals(Messages.get("crac.header", "crac", "preventive, outage, curative"), controller.headerLabel.getText());
        assertEquals(1, controller.contingenciesListView.getItems().size());
        assertEquals(List.of("L1 preventive", "L1 curative"), controller.cnecsListView.getItems().stream().map(cnec -> cnec.getId()).toList());
        assertEquals(List.of(RaoTestCases.LOAD_SHEDDING, RaoTestCases.PST), controller.remedialActionsListView.getItems().stream().map(action -> action.getId()).toList());
        assertEquals(List.of("a report line"), controller.reportListView.getItems());
        assertFalse(controller.exportButton.isDisabled());

        interact(() -> {
            controller.tabPane.getSelectionModel().select(controller.cnecsTab);
            controller.cnecsListView.getSelectionModel().select(1);
        });
        // a flow CNEC: its form
        FlowCnecForm cnec = controller.flowCnecForm;
        assertEquals(Messages.get("raoResults.stateName", "L2 out", "curative"), cnec.stateChoiceBox.getConverter().toString(cnec.stateChoiceBox.getValue()));
        assertEquals(List.of(List.of("1", "A", "-150", "150")), cnec.thresholdsEditor.getRows());

        interact(() -> {
            controller.tabPane.getSelectionModel().select(controller.remedialActionsTab);
            controller.remedialActionsListView.getSelectionModel().select(0);
        });
        // a network action: its form
        NetworkActionForm networkAction = controller.networkActionForm;
        assertEquals(List.of(UsageRuleSpec.onState("L2 out", "curative")), networkAction.usageRulesPane.listView.getItems());
        assertEquals(Messages.get("crac.usageRule.onState", Messages.get("raoResults.stateName", "L2 out", "curative")),
                controller.usageRuleText(UsageRuleSpec.onState("L2 out", "curative")));
        assertEquals(List.of(List.of("load", "LD2", "70")), networkAction.actionsEditor.getRows());

        // a range action: its form
        interact(() -> controller.remedialActionsListView.getSelectionModel().select(1));
        RangeActionForm rangeAction = controller.rangeActionForm;
        assertEquals(List.of(UsageRuleSpec.onInstant("preventive")), rangeAction.usageRulesPane.listView.getItems());
        assertEquals("PS1", rangeAction.elementField.getText());
    }

    // after a network edit removing an element of the CRAC
    @Test
    void marksTheObjectsWhoseElementsAreMissing() {
        interact(() -> study.setCrac(RaoTestCases.crac(network), List.of()));
        interact(() -> {
            network.getLoad("LD2").remove();
            mainModel.setUpdate();
            controller.tabPane.getSelectionModel().select(controller.remedialActionsTab);
        });
        WaitForAsyncUtils.waitForFxEvents();

        ListCell<?> loadShedding = from(controller.remedialActionsListView).lookup(".list-cell").queryAll().stream()
                .map(node -> (ListCell<?>) node).filter(cell -> cell.getIndex() == 0).findFirst().orElseThrow();
        assertNotNull(loadShedding.getGraphic());
        assertEquals(Messages.get("crac.missingElements", "LD2"), loadShedding.getTooltip().getText());
        interact(() -> controller.remedialActionsListView.getSelectionModel().select(0));
        // its form tells it too
        NetworkActionForm form = controller.networkActionForm;
        assertTrue(form.problemLabel.isVisible());
        assertEquals(Messages.get("crac.missingElements", "LD2"), form.problemLabel.getText());
    }

    @Test
    void importsAJsonCrac() throws IOException, TimeoutException {
        Path file = tempDir.resolve("crac.json");
        Files.write(file, CracIO.toJson(RaoTestCases.crac(network)));
        Crac previous = RaoTestCases.crac(network);
        interact(() -> study.setCrac(previous, List.of()));

        interact(() -> controller.importFile(file));

        WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS, () -> study.getCrac() != previous);
        assertEquals(List.of("JSON"), askedFormats);
        assertNotSame(previous, study.getCrac());
        assertEquals(2, study.getCrac().getFlowCnecs().size());
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(2, controller.cnecsListView.getItems().size());
        assertEquals(NotificationStatus.SUCCESS, notification("crac.import.").orElseThrow().status());
        assertFalse(mainModel.isBusy(network));
    }

    @Test
    void refusesAFileOfNoCracFormat() throws IOException, TimeoutException {
        Path file = tempDir.resolve("notes.txt");
        Files.writeString(file, "not a CRAC");

        interact(() -> controller.importFile(file));

        WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS, () -> notification("crac.import.").isPresent());
        assertEquals("crac.import.unknownFormat", notification("crac.import.").orElseThrow().messageKey());
        assertTrue(askedFormats.isEmpty());
    }

    // OpenRAO's JSON importer needs current limits on the CNECs' branches: the CRAC in place is kept
    @Test
    void keepsTheCracWhenTheImportFails() throws IOException, TimeoutException {
        Path file = tempDir.resolve("crac.json");
        Files.write(file, CracIO.toJson(RaoTestCases.crac(network)));
        Network withoutLimits = PhaseShifterTestCaseFactory.create();
        interact(() -> {
            mainModel.addNetwork(withoutLimits);
            mainModel.setNetwork(withoutLimits);
        });
        Crac previous = RaoTestCases.crac(network);
        NetworkStudy otherStudy = mainModel.getStudy(withoutLimits);
        interact(() -> otherStudy.setCrac(previous, List.of()));

        interact(() -> controller.importFile(file));

        WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS, () -> notification("crac.import.").isPresent());
        Notification failure = notification("crac.import.").orElseThrow();
        assertEquals("crac.import.creationFailed", failure.messageKey());
        assertEquals("crac.import.viewReport", failure.actions().getFirst().messageKey());
        assertSame(previous, otherStudy.getCrac());
    }

    // with the CRAC Generator parameters
    @Test
    void generatesFromTheNetwork() throws TimeoutException {
        mainModel.getParametersModel().cracGeneratorSettingsProperty().get().setRedispatching(true);

        interact(controller::generate);

        WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS, () -> study.getCrac() != null);
        assertEquals(3, study.getCrac().getContingencies().size());
        assertTrue(study.getCrac().getRangeActions().stream().anyMatch(action -> action.getNetworkElementsIds().contains("G1")));
        WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS, () -> notification("crac.generate.").isPresent());
        assertEquals("crac.generate.completed", notification("crac.generate.").orElseThrow().messageKey());
        assertEquals("crac.generate.viewReport", notification("crac.generate.").orElseThrow().actions().getFirst().messageKey());
        assertFalse(mainModel.isBusy(network));
    }

    // the CRAC Creation parameters tab's, with nothing to ask for a JSON CRAC
    @Test
    void importsWithTheCracCreationParameters() {
        CracCreationParameters parameters = mainModel.getParametersModel().cracCreationParametersProperty().get();
        parameters.setDefaultMonitoredLineSide(CracCreationParameters.MonitoredLineSide.MONITOR_LINES_ON_SIDE_TWO);

        Optional<CracCreationParameters> asked = ((AnsweringCracController) controller).ask("crac.json", "JSON");

        assertEquals(Set.of(TwoSides.TWO), asked.orElseThrow().getDefaultMonitoredSides());
        assertNotSame(parameters, asked.orElseThrow());
    }

    @Test
    void exportsInOpenRaoJsonAndRemoves() throws IOException {
        interact(() -> study.setCrac(RaoTestCases.crac(network), List.of()));
        Path file = tempDir.resolve("exported.json");

        interact(() -> controller.exportTo(file));
        assertEquals(2, CracIO.readJson(Files.newInputStream(file), network).getCrac().getFlowCnecs().size());

        interact(controller::remove);
        assertNull(study.getCrac());
        assertEquals(Messages.get("crac.none"), controller.headerLabel.getText());
        assertTrue(controller.cnecsListView.getItems().isEmpty());
    }

    // ----- edits -----

    @Test
    void createsANewCracAndAddsInstants() {
        assertTrue(controller.addMenuButton.isDisabled());
        interact(() -> controller.setNewCrac(CracEdits.newCrac("new", null, List.of("curative"))));

        assertFalse(controller.addMenuButton.isDisabled());
        assertEquals(List.of("preventive", "outage", "curative"), controller.instantsListView.getItems().stream().map(Instant::getId).toList());
        assertTrue(controller.contingenciesListView.getItems().isEmpty());

        int revision = study.cracRevisionProperty().get();
        interact(() -> {
            CracEdits.addInstant(study.getCrac(), "curative 2", InstantKind.CURATIVE);
            study.cracEdited();
        });
        assertEquals(revision + 1, study.cracRevisionProperty().get());
        assertEquals(Messages.get("crac.header", "new", "preventive, outage, curative, curative 2"), controller.headerLabel.getText());
        assertEquals(4, controller.instantsListView.getItems().size());
    }

    @Test
    void addsAndEditsAContingency() {
        interact(() -> study.setCrac(RaoTestCases.crac(network), List.of()));
        interact(controller::onAddContingency);
        WaitForAsyncUtils.waitForFxEvents();

        String id = Messages.get("crac.contingency.newId") + " 1";
        assertSame(controller.contingenciesTab, controller.tabPane.getSelectionModel().getSelectedItem());
        assertEquals(id, controller.contingenciesListView.getSelectionModel().getSelectedItem().getId());
        ContingencyForm form = controller.contingencyForm;
        assertNotNull(form);
        assertSame(form.root(), controller.detailsHost.getChildren().getFirst());
        assertTrue(form.problemLabel.isVisible(), "no elements yet");
        assertEquals(Messages.get("crac.contingency.notReferenced"), form.referencesLabel.getText());

        picked.addAll(List.of("L1", "LD2"));
        interact(form.addElementsButton::fire);
        assertEquals(List.of("L1", "LD2"), study.getCrac().getContingency(id).getElements().stream().map(ContingencyElement::getId).toList());
        assertFalse(form.problemLabel.isVisible());
        assertEquals(Set.of(), presentWhenPicked.getFirst());
        assertEquals(Set.of("L1", "LD2"), Set.copyOf(form.elementsListView.getItems().stream().map(ContingencyElement::getId).toList()));

        interact(() -> {
            form.elementsListView.getSelectionModel().select(1);
            form.removeElementsButton.fire();
        });
        assertEquals(List.of("L1"), study.getCrac().getContingency(id).getElements().stream().map(ContingencyElement::getId).toList());

        // a rename replaces the contingency: it stays selected, in the same form
        interact(() -> {
            form.idField.setText("Trip L1");
            form.nameField.setText("Trip of L1");
            form.idField.fireEvent(new ActionEvent());
        });
        assertNull(study.getCrac().getContingency(id));
        assertEquals("Trip L1", controller.contingenciesListView.getSelectionModel().getSelectedItem().getId());
        assertEquals(Optional.of("Trip of L1"), study.getCrac().getContingency("Trip L1").getName());
        assertSame(form, controller.contingencyForm);

        interact(controller::onRemoveSelected);
        assertNull(study.getCrac().getContingency("Trip L1"));
        assertEquals(1, controller.contingenciesListView.getItems().size());
    }

    @Test
    void keepsAReferencedContingency() {
        interact(() -> study.setCrac(RaoTestCases.crac(network), List.of()));
        interact(() -> {
            controller.tabPane.getSelectionModel().select(controller.contingenciesTab);
            controller.contingenciesListView.getSelectionModel().select(0);
        });
        ContingencyForm form = controller.contingencyForm;
        assertFalse(form.idField.isEditable());
        assertEquals(Messages.get("crac.contingency.referencedBy", "L1 curative, " + RaoTestCases.LOAD_SHEDDING), form.referencesLabel.getText());

        interact(controller::onRemoveSelected);
        assertNotNull(study.getCrac().getContingency(RaoTestCases.CONTINGENCY));
        assertEquals(NotificationStatus.ERROR, notification("crac.removeSelected").orElseThrow().status());
    }

    @Test
    void addsTheContingenciesOfTheEnabledLists() {
        interact(() -> study.setCrac(RaoTestCases.crac(network), List.of()));
        interact(controller::onAddContingenciesFromLists);
        assertEquals(NotificationStatus.ERROR, notification("crac.contingency.fromLists").orElseThrow().status());

        DefaultContingencyList disabled = new DefaultContingencyList("disabled", List.of(Contingency.line("L2")));
        interact(() -> {
            study.getContingencyLists().addAll(new DefaultContingencyList("list", List.of(Contingency.line("L1"),
                    new Contingency(RaoTestCases.CONTINGENCY, new LineContingency("L1")))), disabled);
            study.contingencyListEnabledProperty(disabled).set(false);
        });
        interact(controller::onAddContingenciesFromLists);

        assertEquals(List.of("L1", RaoTestCases.CONTINGENCY), controller.contingenciesListView.getItems().stream().map(Contingency::getId).toList());
        Notification notification = mainModel.getNotificationsModel().getNotifications().stream()
                .filter(candidate -> candidate.messageKey().equals("crac.contingency.fromLists.completed")).findFirst().orElseThrow();
        assertEquals(NotificationStatus.SUCCESS, notification.status());
        assertEquals(List.of(1, 1), notification.messageArgs());
    }

    @Test
    void addsFlowCnecsOnBranches() {
        interact(() -> study.setCrac(RaoTestCases.crac(network), List.of()));
        pickedBranches.addAll(List.of("L1", "L2"));
        flowCnecsRequest = new AddFlowCnecsDialog.Request(List.of(new CnecEdits.StateRef(null, "preventive")), Set.of(TwoSides.ONE));
        interact(controller::onAddFlowCnecs);

        assertSame(controller.cnecsTab, controller.tabPane.getSelectionModel().getSelectedItem());
        assertEquals(List.of("L1 preventive", "L1_preventive", "L2_preventive", "L1 curative"),
                controller.cnecsListView.getItems().stream().map(cnec -> cnec.getId()).toList());
        assertEquals("L1_preventive", controller.cnecsListView.getSelectionModel().getSelectedItem().getId());
        Notification notification = notification("crac.cnec.add").orElseThrow();
        assertEquals(NotificationStatus.SUCCESS, notification.status());
        assertEquals(List.of(2, 0), notification.messageArgs());

        // again: all skipped, already there
        interact(controller::onAddFlowCnecs);
        assertEquals(4, controller.cnecsListView.getItems().size());
        assertTrue(mainModel.getNotificationsModel().getNotifications().stream().anyMatch(n -> n.status() == NotificationStatus.PARTIAL_SUCCESS));
    }

    @Test
    void editsAFlowCnec() {
        interact(() -> study.setCrac(RaoTestCases.crac(network), List.of()));
        interact(() -> {
            controller.tabPane.getSelectionModel().select(controller.cnecsTab);
            controller.cnecsListView.getSelectionModel().select(1);
        });
        FlowCnecForm form = controller.flowCnecForm;
        FlowCnec cnec = study.getCrac().getFlowCnec("L1 curative");
        assertSame(cnec, form.cnec());
        assertSame(form.root(), controller.detailsHost.getChildren().getFirst());
        assertEquals(List.of(List.of("1", "A", "-150", "150")), form.thresholdsEditor.getRows());

        // the role in place
        int revision = study.cracRevisionProperty().get();
        interact(() -> form.monitoredCheckBox.setSelected(true));
        assertSame(cnec, study.getCrac().getFlowCnec("L1 curative"));
        assertTrue(cnec.isMonitored());
        assertEquals(revision + 1, study.cracRevisionProperty().get());

        // anything else by replacing it: kept selected, in the same form
        interact(() -> {
            form.reliabilityMarginField.setText("10");
            form.reliabilityMarginField.fireEvent(new ActionEvent());
        });
        FlowCnec replaced = study.getCrac().getFlowCnec("L1 curative");
        assertNotSame(cnec, replaced);
        assertEquals(10., replaced.getReliabilityMargin());
        assertTrue(replaced.isMonitored());
        assertSame(replaced, controller.cnecsListView.getSelectionModel().getSelectedItem());
        assertSame(form, controller.flowCnecForm);

        // a refused change is told and reverted
        interact(() -> {
            form.iMax1Field.setText("x");
            form.iMax1Field.fireEvent(new ActionEvent());
        });
        assertTrue(form.errorLabel.isVisible());
        assertEquals("", form.iMax1Field.getText());

        // no threshold: refused by OpenRAO, the CNEC put back (a new object), the rows kept as typed
        interact(() -> {
            form.thresholdsEditor.getTable().getSelectionModel().select(0);
            ((Button) ((HBox) form.thresholdsEditor.getChildren().get(1)).getChildren().get(1)).fire();
        });
        assertNotNull(form.thresholdsEditor.getError());
        FlowCnec putBack = study.getCrac().getFlowCnec("L1 curative");
        assertEquals(1, putBack.getThresholds().size());
        assertSame(putBack, form.cnec());
        assertSame(putBack, controller.cnecsListView.getSelectionModel().getSelectedItem());
        assertSame(form, controller.flowCnecForm);

        interact(controller::onRemoveSelected);
        assertNull(study.getCrac().getCnec("L1 curative"));
    }

    @Test
    void keepsACnecARemedialActionIsConditionedOn() {
        interact(() -> {
            Crac crac = RaoTestCases.crac(network);
            crac.newNetworkAction().withId("on constraint").newLoadAction().withNetworkElement("LD2").withActivePowerValue(50.).add()
                    .newOnConstraintUsageRule().withCnec("L1 curative").withInstant("curative").add()
                    .add();
            study.setCrac(crac, List.of());
        });
        interact(() -> {
            controller.tabPane.getSelectionModel().select(controller.cnecsTab);
            controller.cnecsListView.getSelectionModel().select(1);
        });
        FlowCnecForm form = controller.flowCnecForm;
        assertFalse(form.idField.isEditable());
        assertTrue(form.thresholdsEditor.isDisabled());
        assertFalse(form.optimizedCheckBox.isDisabled());
        assertEquals(Messages.get("crac.cnec.referencedBy", "on constraint"), form.referencesLabel.getText());

        interact(controller::onRemoveSelected);
        assertNotNull(study.getCrac().getCnec("L1 curative"));
        assertEquals(NotificationStatus.ERROR, notification("crac.removeSelected").orElseThrow().status());
    }

    @Test
    void addsAndEditsANetworkAction() {
        interact(() -> study.setCrac(RaoTestCases.crac(network), List.of()));
        pickedActionElements.addAll(List.of("L1", "VL1", "G1"));
        interact(controller::onAddNetworkAction);

        String id = Messages.get("crac.networkAction.newId") + " 1";
        NetworkAction action = study.getCrac().getNetworkAction(id);
        assertSame(controller.remedialActionsTab, controller.tabPane.getSelectionModel().getSelectedItem());
        assertSame(action, controller.remedialActionsListView.getSelectionModel().getSelectedItem());
        NetworkActionForm form = controller.networkActionForm;
        assertSame(action, form.action());
        // VL1 can't be acted on
        assertEquals(List.of(List.of("terminals", "L1", "open"), List.of("generator", "G1", "100")), form.actionsEditor.getRows());
        assertTrue(form.usageRulesPane.listView.getItems().isEmpty());

        // renamed: replaced, kept selected in the same form
        interact(() -> {
            form.idField.setText("topo");
            form.idField.fireEvent(new ActionEvent());
        });
        NetworkAction renamed = study.getCrac().getNetworkAction("topo");
        assertNull(study.getCrac().getRemedialAction(id));
        assertSame(renamed, controller.remedialActionsListView.getSelectionModel().getSelectedItem());
        assertSame(form, controller.networkActionForm);

        // more actions, on elements picked: those already acted on given
        pickedActionElements.clear();
        pickedActionElements.add("PS1");
        interact(() -> form.addActionsButton.fire());
        assertEquals(Set.of("L1", "G1"), presentWhenPicked.getLast());
        assertEquals(Set.of("L1", "G1", "PS1"), study.getCrac().getNetworkAction("topo").getNetworkElementsIds());

        // a refused change is told and reverted
        interact(() -> {
            form.speedField.setText("fast");
            form.speedField.fireEvent(new ActionEvent());
        });
        assertTrue(form.errorLabel.isVisible());
        assertEquals("", form.speedField.getText());

        interact(controller::onRemoveSelected);
        assertNull(study.getCrac().getRemedialAction("topo"));
        // the range actions too
        interact(() -> controller.remedialActionsListView.getSelectionModel().select(1));
        interact(controller::onRemoveSelected);
        assertNull(study.getCrac().getRemedialAction(RaoTestCases.PST));
    }

    @Test
    void addsAndEditsRangeActions() {
        interact(() -> study.setCrac(RaoTestCases.crac(network), List.of()));
        pickedRangeElements.add("PS1");
        interact(() -> controller.addRangeActions(RangeActionEdits.Type.PST));

        String pstId = Messages.get("crac.rangeAction.newId.pst") + " 1";
        RangeAction<?> pst = study.getCrac().getRangeAction(pstId);
        assertSame(controller.remedialActionsTab, controller.tabPane.getSelectionModel().getSelectedItem());
        assertSame(pst, controller.remedialActionsListView.getSelectionModel().getSelectedItem());
        RangeActionForm form = controller.rangeActionForm;
        assertSame(pst, form.action());
        assertEquals("PS1", form.elementField.getText());
        assertEquals("1", form.initialTapLabel.getText());
        assertEquals(List.of(List.of("absolute", "0", "2")), form.rangesEditor.getRows());
        assertTrue(form.usageRulesPane.listView.getItems().isEmpty());

        // a cost: replaced, kept selected in the same form
        interact(() -> {
            form.upVariationCostField.setText("2.5");
            form.upVariationCostField.fireEvent(new ActionEvent());
        });
        RangeAction<?> costly = study.getCrac().getRangeAction(pstId);
        assertNotSame(pst, costly);
        assertEquals(2.5, costly.getVariationCost(VariationDirection.UP).orElseThrow());
        assertSame(costly, controller.remedialActionsListView.getSelectionModel().getSelectedItem());
        assertSame(form, controller.rangeActionForm);

        // another element, no PST: refused, told and reverted
        pickedRangeElements.set(0, "L1");
        interact(() -> form.pickElementButton.fire());
        assertEquals(RangeActionEdits.Type.PST, pickedRangeTypes.getLast());
        assertEquals(Set.of("PS1"), presentWhenPicked.getLast());
        assertTrue(form.errorLabel.isVisible());
        assertEquals("PS1", form.elementField.getText());
        assertNotNull(study.getCrac().getRangeAction(pstId));

        // injections, more added to them
        pickedRangeElements.set(0, "G1");
        interact(() -> controller.addRangeActions(RangeActionEdits.Type.INJECTION));
        String injectionId = Messages.get("crac.rangeAction.newId.injection") + " 1";
        RangeActionForm injection = controller.rangeActionForm;
        assertEquals(injectionId, injection.action().getId());
        assertEquals("100", injection.initialSetpointField.getText());
        pickedRangeElements.set(0, "LD2");
        interact(() -> injection.addInjectionsButton.fire());
        assertEquals(Set.of("G1"), presentWhenPicked.getLast());
        assertEquals(List.of(List.of("G1", "1"), List.of("LD2", "1")), injection.keysEditor.getRows());
        assertEquals(Set.of("G1", "LD2"), study.getCrac().getRangeAction(injectionId).getNetworkElementsIds());

        // a counter-trade from the country asked: the network's only, no neighbour
        country = Optional.of(Country.FR);
        interact(() -> controller.addRangeActions(RangeActionEdits.Type.COUNTER_TRADE));
        RangeActionForm counterTrade = controller.rangeActionForm;
        assertEquals(Messages.get("crac.rangeAction.newId.counterTrade") + " 1", counterTrade.action().getId());
        assertEquals("FR", counterTrade.areaField.getText());
        assertEquals(List.of(), counterTrade.connectedAreasEditor.getRows());
        assertEquals(List.of(List.of("absolute", "-100", "100")), counterTrade.rangesEditor.getRows());
        // OpenRAO can't remove it: read-only, not removable
        assertFalse(counterTrade.idField.isEditable());
        assertTrue(counterTrade.rangesEditor.isDisabled());
        assertEquals(Messages.get("rangeActionEdits.counterTradeFixed"), counterTrade.problemLabel.getText());
        interact(controller::onRemoveSelected);
        assertNotNull(study.getCrac().getCounterTradeRangeAction(counterTrade.action().getId()));
        assertEquals(NotificationStatus.ERROR, notification("crac.removeSelected").orElseThrow().status());

        // none picked: none added
        pickedRangeElements.clear();
        int count = study.getCrac().getRangeActions().size();
        interact(() -> controller.addRangeActions(RangeActionEdits.Type.HVDC));
        assertEquals(count, study.getCrac().getRangeActions().size());
    }

    @Test
    void editsTheUsageRulesOfRemedialActions() {
        interact(() -> study.setCrac(RaoTestCases.crac(network), List.of()));
        interact(() -> {
            controller.tabPane.getSelectionModel().select(controller.remedialActionsTab);
            controller.remedialActionsListView.getSelectionModel().select(0);
        });
        NetworkActionForm form = controller.networkActionForm;
        UsageRulesPane rules = form.usageRulesPane;

        // added: replaced, kept selected in the same form
        usageRule = Optional.of(UsageRuleSpec.onInstant("preventive"));
        interact(rules.addButton::fire);
        NetworkAction replaced = study.getCrac().getNetworkAction(RaoTestCases.LOAD_SHEDDING);
        assertEquals(List.of(UsageRuleSpec.onInstant("preventive"), UsageRuleSpec.onState(RaoTestCases.CONTINGENCY, "curative")), rules.listView.getItems());
        assertSame(replaced, form.action());
        assertSame(replaced, controller.remedialActionsListView.getSelectionModel().getSelectedItem());
        assertSame(form, controller.networkActionForm);
        assertFalse(rules.errorLabel.isVisible());

        // twice, or one OpenRAO refuses: told, the action unchanged
        interact(rules.addButton::fire);
        assertEquals(Messages.get("usageRuleEdits.duplicate"), rules.errorLabel.getText());
        usageRule = Optional.of(UsageRuleSpec.onConstraint("L1 preventive", "curative"));
        interact(rules.addButton::fire);
        assertEquals(Messages.get("usageRuleEdits.cnecBefore", "L1 preventive", "preventive", "curative"), rules.errorLabel.getText());
        assertSame(replaced, study.getCrac().getNetworkAction(RaoTestCases.LOAD_SHEDDING));

        // the selected removed
        interact(() -> {
            rules.listView.getSelectionModel().clearAndSelect(1);
            rules.removeButton.fire();
        });
        assertEquals(List.of(UsageRuleSpec.onInstant("preventive")), UsageRuleEdits.of(study.getCrac().getNetworkAction(RaoTestCases.LOAD_SHEDDING)));
        assertFalse(rules.errorLabel.isVisible());

        // a range action's
        interact(() -> controller.remedialActionsListView.getSelectionModel().select(1));
        RangeActionForm rangeAction = controller.rangeActionForm;
        usageRule = Optional.of(UsageRuleSpec.onState(RaoTestCases.CONTINGENCY, "curative"));
        interact(rangeAction.usageRulesPane.addButton::fire);
        assertEquals(List.of(UsageRuleSpec.onInstant("preventive"), UsageRuleSpec.onState(RaoTestCases.CONTINGENCY, "curative")),
                UsageRuleEdits.of(study.getCrac().getRangeAction(RaoTestCases.PST)));
        assertSame(rangeAction, controller.rangeActionForm);
    }

    @Test
    void editsTheUsageLimitsOfAnInstant() {
        interact(() -> study.setCrac(RaoTestCases.crac(network), List.of()));
        interact(() -> {
            controller.tabPane.getSelectionModel().select(controller.instantsTab);
            controller.instantsListView.getSelectionModel().select(2);
        });
        InstantForm form = controller.instantForm;
        Instant curative = study.getCrac().getInstant("curative");
        assertSame(curative, form.instant());
        assertEquals("", form.maxRaField.getText());

        int revision = study.cracRevisionProperty().get();
        interact(() -> {
            form.maxRaField.setText("2");
            form.maxRaField.fireEvent(new ActionEvent());
        });
        assertEquals(2, study.getCrac().getRaUsageLimits(curative).getMaxRa());
        assertEquals(revision + 1, study.cracRevisionProperty().get());
        assertSame(form, controller.instantForm);

        // refused: told and reverted
        interact(() -> {
            form.maxRaField.setText("-1");
            form.maxRaField.fireEvent(new ActionEvent());
        });
        assertEquals(Messages.get("usageLimitEdits.negative", -1), form.errorLabel.getText());
        assertEquals("2", form.maxRaField.getText());

        // the TSOs' limits: a row removed applies the others
        interact(() -> {
            form.tsoLimitsEditor.setRows(List.of(List.of("RTE", "1", "", "2", ""), List.of("ELIA", "", "", "", "")));
            form.tsoLimitsEditor.getTable().getSelectionModel().clearAndSelect(1);
            ((Button) ((HBox) form.tsoLimitsEditor.getChildren().get(1)).getChildren().get(1)).fire();
        });
        assertNull(form.tsoLimitsEditor.getError());
        assertEquals(Map.of("RTE", 1), study.getCrac().getRaUsageLimits(curative).getMaxTopoPerTso());
        assertEquals(Map.of("RTE", 2), study.getCrac().getRaUsageLimits(curative).getMaxRaPerTso());
        assertEquals(2, study.getCrac().getRaUsageLimits(curative).getMaxRa());

        // the outage instant has none
        interact(() -> controller.instantsListView.getSelectionModel().select(1));
        assertNull(controller.instantForm.maxRaField.getParent());
    }
}
