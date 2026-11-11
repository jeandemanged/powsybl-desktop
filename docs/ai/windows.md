# Separate windows

Paths `…/X.java` are relative to `src/main/java/com/powsybl/powsybldesktop/`.

`…/ui/window/SeparateWindows.java` (owned by `MainController`, reached by views through
`SceneModel.getSeparateWindows()`) shows views in a fixed context, with no menu, toolbar nor history, as tabs of named
windows ("Window N", the lowest free N; renamed by double-clicking the name left of the tab strip or from the Windows
menu, blank and taken names rejected).

Every window other than the main window, the splash screen, modal dialogs and the memory window goes through it: never
create a `Stage` for a view elsewhere (`ArchitectureTest` allows only the shell and `SeparateWindows` to).

## Stages

- Independent stages, not owned by the main window, so that the main window can come in front of them. Never raise
  them from a focus listener: it steals the activation from the clicked window.
- A new window opens centered over the window it's opened from (the focused one, else the main window), kept within
  that window's screen (`showOverActiveWindow`): JavaFX centers an unpositioned stage with no owner on the primary screen. Dialogs get
  the window they're opened from as owner, for the same reason.
- A window closes with its last tab, and all close with the main window. A view's controller is disposed when its tab
  closes or its window hides, not when the tab moves to another window.
- A tab can be dragged to another window's tab strip, reordered, or dropped outside any window to get its own window
  (moved with its controller, not disposed).
- The main window's Windows menu has a submenu per window (`getOpenWindows`): show it, rename it, or show one of its
  tabs.

## Opening a view in a window

- One view per key: whole-network views (tables, map, contingencies, limit scalings) are keyed by `NavigationType`, container
  diagrams and equipment tabs by kind and container id, detail views by equipment id, logs/reports/parameters (not
  network-bound) by name. Opening an open key brings its window to front and selects its tab instead.
- An "open in new window" split button (`separateWindows.addOpenButton`, its menu listing the windows to open the view
  as a tab of one instead) is added to the view's own toolbar (or its `openInNewWindowHost` Pane):
  - by `MainController.ensureSceneView` for whole-network views (`detachSceneView`): a `SceneView` gets it for free if
    it has a top `ToolBar` (BorderPane top or VBox first child), an `openInNewWindowHost` Pane, or a VBox root;
  - by `SubstationsController` for its diagram panes (`ui/network/ContainerDiagram`, shared with its own tabs) and
    equipment tabs (fixed to the selected container);
  - for logs, reports and parameters, single-instance and not network-bound (`detachLogs`, `detachReports`,
    `detachParameters`, through `openSingleInstance`).
- The main toolbar's map, inputs, parameters and diagnostics buttons can also be dragged
  (`separateWindows.addDragToOpen(button, key, open)`): released over a separate window, the view opens as a new tab
  of it, elsewhere in a new window at the release point, a hint following the mouse telling which; if the view of `key`
  is already open, the drag moves it as its tab's would. The parameters button is a split button whose menu lists the
  parameters tabs (`ParametersController.tabTitles()`, kept in the FXML's tab order): an entry shows the view at that
  tab, and dragging it (its `CustomMenuItem` label, through `addDragToOpen(node, key, open, dropped)`) opens or moves
  the view as the button's drag does, then selects the tab (`SeparateWindows.controller(key)`). A drag pressed on a
  split button's arrow is ignored, the arrow opening the menu. The inputs (contingencies, limit scalings, sensitivity
  factors) and diagnostics (logs, reports) split buttons (`MainController.initLastUsedViewButton`) list their views the
  same way; the button itself opens, or drags (`addDragToOpen(button, Supplier<String> key, open)`, the key read at
  release), the last one used: the last of them navigated to (read back from the history after a language reload) or
  dragged from the menu. Its icon and tooltip show which. A plain mouse drag (scene filters), not platform drag and
  drop, whose "forbidden" cursor outside accepting targets would be misleading; tests call `dropToOpen` directly,
  since the headless platform doesn't deliver a drag leaving its window.
- Detail views, and diagrams opened from the map, open as a tab of the latest window created (`openInLatestWindow`,
  the last of `getOpenWindows()`, even the one they're opened from), or in a new window if none is open.

## A view in a separate window

- It gets a `SceneModel.separate` scene: it must navigate through `sceneModel.navigate`/`record` and read its network
  from the scene, never `mainModel.getNetwork()`.
- Its navigation drives the main window and brings it to front - except a single line diagram leading to another
  voltage level (feeder click, "go to"), which stays in its own scene: in a separate window, the diagram shows that
  voltage level itself, its tab re-keyed and retitled (`SeparateWindows.retarget`; `ContainerDiagram.open` titles and
  rebuilds it from what it currently shows).

## Network binding

- A separate window's scene is fixed to the network selected when it was opened. Network-bound views (tabs) close
  before the selection changes to another root network (`confirmNetworkChange` asks first), not while it moves
  between a root network and its subnetworks; so the root network's `MainModel` state (busy) applies to them too,
  while their search boxes search their own network's index.
- The map isn't bound: opened with `openFollowingSelection`, its scene (`SceneModel.separateFollowingSelection`)
  follows the selected network, its tab kept open and retitled when the selection changes. In any window, the map
  shows the selected network's root network, with all its subnetworks (`MapController.network()`).
- Structural edits close the tabs of removed objects (`SeparateWindows.closeRemoved`, see
  [editing.md](editing.md#structural-edits)).

## Language reload

`MainController.reloadShell` hands the `SeparateWindows` instance over to the new controller and `relocalize()`s it:
windows and tabs stay open, each tab's view is rebuilt from the factory it was opened with (titles are `Supplier`s for
the same reason), losing only the view's own UI state.
