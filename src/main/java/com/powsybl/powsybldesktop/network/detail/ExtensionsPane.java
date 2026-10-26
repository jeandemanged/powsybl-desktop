/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.detail;

import com.powsybl.commons.extensions.Extendable;
import com.powsybl.commons.extensions.Extension;
import com.powsybl.powsybldesktop.parameters.GuiParameters;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * An extendable's extensions, one section each: an editable form for the extensions this pane has an {@link Editor}
 * for, removable, and the name and class of the others, read-only. Extensions with an editor that can create them
 * are added from the "Add extension" menu. Shared by every equipment detail view, each supplying its editors.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class ExtensionsPane<O extends Extendable<O>> extends VBox implements DetailPart {

    /**
     * How to show and edit one extension class.
     *
     * @param creator adds a default instance of the extension, null if it can't be added from this pane
     * @param form    fills the extension's form
     */
    public record Editor<O>(Class<?> type, String labelKey, Consumer<O> creator, BiConsumer<Extension<O>, DetailForm> form) {
    }

    @SuppressWarnings("unchecked")
    public static <O, E extends Extension<O>> Editor<O> editor(Class<?> type, String labelKey, Consumer<O> creator, BiConsumer<E, DetailForm> form) {
        return new Editor<>(type, labelKey, creator, (extension, detailForm) -> form.accept((E) extension, detailForm));
    }

    private final O extendable;
    private final List<Editor<O>> editors;
    private final Supplier<GuiParameters> guiParameters;
    private final Runnable onEdit;
    private final BooleanProperty editable = new SimpleBooleanProperty(true);
    private final MenuButton addButton = new MenuButton(Messages.get("detail.extensions.add"));
    private final VBox sections = new VBox(4);
    private final List<DetailForm> forms = new ArrayList<>();
    private List<Extension<O>> shown = List.of();

    public ExtensionsPane(O extendable, List<Editor<O>> editors, Supplier<GuiParameters> guiParameters, Runnable onEdit) {
        super(8);
        this.extendable = Objects.requireNonNull(extendable);
        this.editors = List.copyOf(editors);
        this.guiParameters = Objects.requireNonNull(guiParameters);
        this.onEdit = Objects.requireNonNull(onEdit);
        addButton.disableProperty().bind(editable.not());
        getChildren().addAll(addButton, sections);
        rebuild();
    }

    @Override
    public BooleanProperty editableProperty() {
        return editable;
    }

    /**
     * Rebuilds the sections if extensions were added or removed meanwhile, or a form's {@link DetailForm#structure}
     * changed (e.g. a measurement added), re-reads their forms otherwise.
     */
    @Override
    public void refresh() {
        if (!List.copyOf(extendable.getExtensions()).equals(shown) || forms.stream().anyMatch(DetailForm::isStale)) {
            rebuild();
        } else {
            forms.forEach(DetailForm::refresh);
        }
    }

    private void rebuild() {
        shown = List.copyOf(extendable.getExtensions());
        forms.clear();
        List<TitledPane> panes = new ArrayList<>();
        shown.stream()
                .sorted((e1, e2) -> e1.getName().compareToIgnoreCase(e2.getName()))
                .forEach(extension -> panes.add(editorOf(extension)
                        .map(editor -> editableSection(extension, editor))
                        .orElseGet(() -> readOnlySection(extension))));
        sections.getChildren().setAll(panes);
        if (panes.isEmpty()) {
            sections.getChildren().add(new Label(Messages.get("detail.extensions.none")));
        }
        addButton.getItems().setAll(editors.stream()
                .filter(editor -> editor.creator() != null && extendable.getExtension(editor.type()) == null)
                .map(editor -> {
                    MenuItem item = new MenuItem(Messages.get(editor.labelKey()));
                    item.setOnAction(event -> {
                        DetailForm.apply(addButton, () -> editor.creator().accept(extendable), onEdit);
                        refresh();
                    });
                    return item;
                })
                .toList());
        addButton.setVisible(!addButton.getItems().isEmpty());
    }

    private Optional<Editor<O>> editorOf(Extension<O> extension) {
        return editors.stream().filter(editor -> editor.type().isInstance(extension)).findFirst();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private TitledPane editableSection(Extension<O> extension, Editor<O> editor) {
        DetailForm form = new DetailForm(guiParameters, onEdit);
        form.editableProperty().bind(editable);
        editor.form().accept(extension, form);
        forms.add(form);
        Button removeButton = new Button(Messages.get("desktop.common.action.remove"));
        removeButton.disableProperty().bind(editable.not());
        removeButton.setOnAction(event -> {
            DetailForm.apply(removeButton, () -> extendable.removeExtension((Class) editor.type()), onEdit);
            refresh();
        });
        VBox content = new VBox(8, form.getNode(), removeButton);
        return new TitledPane(Messages.get(editor.labelKey()) + " (" + extension.getName() + ")", content);
    }

    private static TitledPane readOnlySection(Extension<?> extension) {
        VBox content = new VBox(4, new Label(Messages.get("detail.extensions.notEditable")),
                new Label(Messages.labelValue("detail.extensions.class", extension.getClass().getName())));
        TitledPane pane = new TitledPane(extension.getName(), content);
        pane.setExpanded(false);
        return pane;
    }
}
