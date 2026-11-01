# UI conventions (FXML, i18n, dialogs, styling)

Paths `…/X.java` are relative to `src/main/java/com/powsybl/powsybldesktop/`; FXML files sit in the mirrored
resources package. Tables, editors and detail views: [editing.md](editing.md); diagrams and the map:
[diagrams-and-map.md](diagrams-and-map.md); separate windows: [windows.md](windows.md).

## Screens: FXML + controller

- One FXML file per view, named `kebab-case-view.fxml`, in the resources package matching the controller's Java
  package (`ui/network/tables/LoadsController.java` ↔ `ui/network/tables/loads-view.fxml`). Included parts and tabs
  are named for what they are (`search-box.fxml`, `diagram-pane.fxml`, `ui/parameters/lf-parameters.fxml`,
  `ui/contingency/*-form.fxml`).
- The root element declares `fx:controller="fully.qualified.Controller"` - except `equipment-detail-view.fxml`, shared
  by every detail view, whose controller `EquipmentDetails.load` sets. Every `fx:id` has a matching `@FXML` field of
  the same name and compatible type; every `onAction="#name"` has a matching method. Renaming one side without the
  other breaks loading at runtime (and the controller's tests).
- Controller fields that tests read are package-private or public (e.g. `LoadsController.loadsTableView`,
  `nameColumn`); keep that visibility when tests depend on it.
- Reusable sub-views are embedded with `<fx:include fx:id="searchBox" source="..."/>`; the nested controller is
  injected as a field named `<fx:id>Controller` (e.g. `searchBoxController`, `columnVisibilityToolbarController`,
  `sldPaneController`). Existing reusable pieces:
  - `ui/network/search/search-box.fxml` → `SearchBoxController` (bind with
    `searchBox().bind(sceneModel, kinds, onMatch)`).
  - `ui/network/tables/column-visibility-toolbar.fxml` → `ColumnVisibilityToolbarController.configure(List<ColumnGroup>)`.
  - `ui/diagram/diagram-pane.fxml` → `DiagramPaneController` (WebView diagram viewport).
  - Equipment table views themselves, included in `ui/network/substations-view.fxml` as `*Embedded` tabs.
- Load views with `new FXMLLoader(url, Messages.bundle())`. For center views, `MainController.ensureController`
  already does it; for separate windows, `SeparateWindows.load`.
- Canonical simple screen: `…/ui/network/tables/LoadsController.java` + `loads-view.fxml`. Canonical complex screens:
  `…/ui/network/SubstationsController.java`, `…/ui/network/NetworksController.java`.

## Internationalization

- All user-visible text is in `i18n/messages.properties` (English) **and** `i18n/messages_fr.properties` (French);
  both have the same key set. Add every new key to both, at its sorted position (checkstyle, see
  [build-and-testing.md](build-and-testing.md#checkstyle-fails-the-build)).
- English text uses **American English** spelling (`canceled`, `color`, `toward`, `-ize`, `analyze`, `center`),
  not British (`cancelled`, `colour`, `towards`, `-ise`, `analyse`, `centre`). Key names may keep PowSyBl/JavaFX
  spellings (e.g. `*.cancelled` mirrors `Worker.State.CANCELLED`).
- Power-system vocabulary must match the PowSyBl dependencies' own translations, so that the UI and the PowSyBl
  reports shown in it use the same words. Before adding or changing a key whose text is not purely about
  powsybl-desktop itself (equipment, electrical quantities, computations, parameters), look up the term in the
  report bundles shipped by the dependencies: `com/powsybl/commons/reports_fr.properties` (powsybl-commons, covers
  `core.*` for all powsybl-core modules) and `com/powsybl/openloadflow/reports_fr.properties` (powsybl-open-loadflow),
  e.g. `unzip -p ~/.m2/repository/com/powsybl/powsybl-commons/<version>/powsybl-commons-<version>.jar com/powsybl/commons/reports_fr.properties`
  (resolve `<version>` with `mvn -o dependency:tree`). Re-check when bumping dependency versions or adding a PowSyBl
  dependency that ships `reports*.properties`.
  Established French terms: substation **site**, voltage level **poste**, bus **nœud électrique**, busbar section
  **section de jeu de barres**, generator **groupe**, shunt compensator **moyen de compensation statique**, static VAR
  compensator **compensateur statique de puissance réactive**, switch **organe de coupure**, tie line
  **interconnexion**, boundary line **ligne frontière**, HVDC line **ligne HVDC**, converter station **station de
  conversion**, branch **quadripôle**, phase/ratio tap changer **déphaseur**/**régleur**, pairing key **clé de
  correspondance**, target **consigne**, target deadband **bande morte cible**, load flow **calcul de répartition**,
  slack bus **nœud bilan**, slack distribution **compensation**, contingency **aléa**, operator strategy **parade**,
  synchronous component **SC** (as in OLF reports). We keep correct French typography (`nœud`, not `noeud`).
- In FXML: `text="%key"`. In Java: `Messages.get("key")` / `Messages.get("key", args...)` (`MessageFormat`
  syntax — escape single quotes as `''`). For a `Label: value` line use `Messages.labelValue(labelKey, value)`
  rather than a dedicated `"Xxx: {0}"` key.
- One concept, one key: a term means the same thing in every view, so it has a single key in `desktop.common.*`
  instead of one copy per view. Before adding a key, search the bundle for the same English text and reuse it.
  - `desktop.common.equipment.<kind>` / `<kind>.plural` — equipment type names (Title Case in English), used for
    toolbar buttons, info panels, search, column headers and dialog headers alike.
  - `desktop.common.column.*` — column headers shared by several tables.
  - `desktop.common.action.*` — generic buttons/menu items (add, remove, import, export, clear all, cancel...).
  - `desktop.common.computationStatus.*`, `regulationMode.*`, `columnGroup.*`, `editError.*`,
    `limitViolationType.*`, `search.*`, and flat `desktop.common.<term>` for other shared terms (`loadFlow`,
    `parameters`, `singleLineDiagram`...).
  - Shared NAD/SLD parameter labels live in `parameters.diagram.*` (`sharedLabel`/`sharedTooltip` in
    `AbstractDiagramParametersController`).
- Other keys: `<area>.<sub>.<name>`, area = the view or feature (`main.*`, `networks.*`, `transformers.*`,
  `detail.*`, `edit.*`, `parameters.*`, `loadflow.param.*`, `securityAnalysis.param.*`, ...), only for text specific
  to that area.
- FXML `%key` text is resolved once at load; language switching works by reloading the shell (see
  [architecture.md](architecture.md#entry-point-and-lifecycle)). Strings built in Java at runtime should be
  re-resolved when rendered (see how `Notification` stores a key, not a string).
- Enum display text is provided by overriding `toString()` with `Messages.get(...)` (e.g. `GroupingMode` in
  `SubstationsController`).

## Dialogs and secondary windows

- **Code-built modal dialogs**: a `final` class with a private constructor and a static `show(Window owner, ...)`
  method building a `javafx.scene.control.Dialog` in code: `initOwner(owner)`, `initModality(Modality.WINDOW_MODAL)`,
  title from `Messages`, content set on the `DialogPane`, `ButtonType.CLOSE` (or a result converter),
  `showAndWait()`. Package-private when used from its own package (`…/ui/network/tables/GeographicalTagsDialog.java`,
  the simplest; `…/ui/network/DropImportDialog.java`, returning a result via `setResultConverter`), public when shared
  (`CreateDialog`, `RemovalDialog`, `TerminalPickerDialog`, which embeds the search box FXML). Edits inside these
  dialogs write to the IIDM object directly and use the same error/flash helpers as tables.
- **FXML secondary windows** opened by `MainController`: memory (`onMemory`): load with `Messages.bundle()`, add
  `styles.css` to the root, `initOwner(main window)`, set the app icon (`logo.png`), dispose the controller in
  `setOnHidden`, and close it in `MainController.dispose()`. About (`onAbout`) is an FXML content inside a `Dialog`.
- **Views in separate windows**: see [windows.md](windows.md). Parameters are a center view that can also be opened
  there.
- Confirmations and error details use `javafx.scene.control.Alert` directly (e.g. `NetworksController`,
  `ParametersController.onReset`).
- Inline forms inside a view (contingency list forms) are FXML sub-controllers swapped in by the parent controller
  (`ui/contingency/CriterionListFormController`, `DefaultContingencyListFormController`).

## Styling and icons

- One global stylesheet, `src/main/resources/com/powsybl/powsybldesktop/styles.css`, added to the main scene, to
  each separate window and secondary window, to the notification overlay popup, to the `SearchableCheckComboBox`
  popup and to the splash screen. Dialogs owned by a window (`dialog.initOwner(...)`) and popups (tooltips, context
  menus) inherit it from their owner's scene; a window or popup with no owner scene must add it itself.
- No inline styles (`setStyle(...)` / FXML `style="..."`): add a style class to `styles.css`, in the matching section
  (generic, tables, status markers, then one section per view), with a short comment saying where it is used and why,
  and apply it with `getStyleClass().add(...)` / `styleClass="..."`. Padding that varies per instance of a shared class
  stays a layout property (`<padding><Insets/></padding>`, as for the `map-overlay` panels).
- Shared classes: `section-title`, `detail-title`, `right-aligned-column`, `centered-column`, `no-header-table`,
  `edit-error`/`edit-success`, `icon-button`, `stepper-button`, `container-link`, `alert-label`/`alert-icon`,
  `severity-marker`, `map-overlay`, `notification-*`; the search field's `:no-match` pseudo-class. Table cell buttons
  usable while the network is busy carry `CopyButtons.NON_EDITING_BUTTON` (see
  [editing.md](editing.md#editing-cells)).
- Icons are Ikonli Material Design 2 (`ikonli-materialdesign2-pack`): `<FontIcon iconLiteral="mdi2a-arrow-left" iconSize="16"/>`
  in FXML or `new FontIcon("mdi2p-pencil")` in Java. Do not add another icon library.
- ControlsFX is available and used (`SearchableComboBox`, `CheckComboBox`, `CheckListView`, `Notifications` popups
  for edit errors).
