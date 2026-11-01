# Diagrams and the map (WebView views)

Paths `…/X.java` are relative to `src/main/java/com/powsybl/powsybldesktop/`; scripts sit in the mirrored resources
package. Both views run JavaScript in a JavaFX `WebView`, whose WebKit renders some things differently from a browser:
when one looks wrong, snapshot it rather than reason from the source (see
[build-and-testing.md](build-and-testing.md#debugging-rendering)).

## JavaScript ↔ Java

- JS → Java callbacks go through `window.controller` (`JSObject.setMember("controller", this)` once the page has
  loaded). Callback methods are public and commented as called from JS (`@SuppressWarnings("unused") // used by JS` in
  `ContainerDiagram`, `// called from map.js` in `MapController`). Keep the scripts and these method names in sync:
  - `ui/network/sld.js` → `…/ui/network/ContainerDiagram.java`: `isClickableEquipment`, `onEquipmentClick`,
    `onEquipmentContextMenu`, `onBackgroundContextMenu`, `onSwitchClick`, `onFeederTopBottomClick`;
  - `ui/map/map.js` → `…/ui/map/MapController.java`: `requestTile`, `cancelTile`, `onTileLoad`, `hitTest`,
    `onMapClick`, `onMapContextMenu`, `onViewChanged`, `onPlace`, `onEditChanged`, `onEditContextMenu`, `openLink`.
- Java → JS is `webEngine.executeScript(...)` calling the scripts' global functions.

## Diagrams (SLD / NAD)

- Diagrams are SVG produced off the FX thread by `SubstationDiagramRenderer` / `NetworkAreaDiagramRenderer` and
  injected by `DiagramPaneController` (zoom, fit, PNG/SVG export) into an HTML shell in a `WebView`.
- `…/ui/network/ContainerDiagram.java` is a container's diagram, used by the substations view's tabs and by separate
  windows: its `renderService` is a latest-wins `Service` (see [threading.md](threading.md#canonical-patterns)), it
  copies the GUI decimals into each render's parameters copy (see
  [domain-and-persistence.md](domain-and-persistence.md#parameters)), and it answers the SLD's clicks and context
  menus (switch clicks are refused while the network is busy).
- In `sld.js`, `clickAction` is the single answer to what a click on an element does: the click runs it, and the
  pointer cursor shows exactly where it isn't null (feeders, switches, then `ContainerDiagram.isClickableEquipment`
  up the element's ancestors). Busbars, a thin line, get a wider transparent line in their group to catch the
  pointer, added again on every injected diagram; SVG exports use the rendered string, not the page.
- What clicks open (details, other voltage levels): see [editing.md](editing.md#equipment-detail-views).

## Map view

- The map does not draw the network in JavaScript: tiles are rendered in Java (`MapTileRenderer`) on a thread pool
  from an immutable `MapNetworkData` snapshot (built off the FX thread by `MapController.networkDataService`); Leaflet
  only displays tiles. Results come back with `Platform.runLater`.
- Hover and clicks are hit-tested in Java (`MapNetworkData.hitTest`): a few pixels of tolerance around substation
  markers and line strokes, substations winning over lines.
- The map shows the selected network's root network with all its subnetworks, in any window (see
  [windows.md](windows.md#network-binding)).

### Leaflet in a WebView

Leaflet's files are unpacked from the `org.webjars:leaflet` dependency (scope `provided`, `leaflet.version`) at build
time - not checked in: the `unpack-leaflet` execution of `maven-dependency-plugin` (`generate-resources`) extracts
`leaflet.js`/`leaflet.css` into `target/classes/…/ui/map/`, where `MapController` reads them as classpath resources.
- Its `<includes>` and `<outputDirectory>` must stay on a single line with no surrounding whitespace: an indented value
  silently matches nothing, the unpack "succeeds" with zero files. `MapControllerEmptyStateTest` asserts both
  resources resolve, since the view still renders without them.
- An IDE building on its own (IntelliJ) never runs that execution: after it rebuilds its output, map tests fail with
  an NPE in `MapController.readResource`. `mvn generate-resources` alone doesn't recover, since the plugin's marker in
  `target/dependency-maven-plugin-markers` says "already unpacked": delete that folder (or `mvn clean`) first.

The WebView mis-renders parts of Leaflet; each workaround below is commented where it is and load-bearing. A map
rendered torn, duplicated or misplaced is a compositing or container size problem, not wrong coordinates (IIDM's
substation/line position extensions are WGS84 latitude/longitude degrees and are read correctly).
- **`L.Browser.any3d = false`** (top of `map.js`): WebKit mis-composites the `translate3d`-positioned panes and tiles
  Leaflet uses by default, drawn as disjoint blocks at stale offsets; Leaflet reads the flag at every positioning
  call, falling back to `left/top`. Consequences handled in `map.js`: `FractionalZoomMap._limitZoom` restores
  fractional zoom (Leaflet otherwise rounds to whole levels with `any3d` off), and `_setZoomTransform` scales other
  levels' tiles with a 2D transform.
- **Leaflet's animations off** (`zoomAnimation`, `fadeAnimation`, `markerZoomAnimation`, `animate: false`): the
  WebView doesn't reliably finish CSS transitions, and an interrupted one leaves stale frames. Smooth zooming is
  driven frame by frame instead (`animateZoom`, `setZoomAround(..., {animate: false})`), as is the search highlight's
  pulse; Leaflet's own wheel, double-click and zoom buttons handling is replaced. `keepTilesOnViewReset` keeps the
  previous level's tiles until replaced (each non-animated zoom resets the view, which blanked the map), pruned
  `PRUNE_DELAY_MS` after their replacements load.
- **`mix-blend-mode: normal` on tiles** (a `<style>` after `leaflet.css`, in `MapController`'s HTML shell): Leaflet
  1.9's `plus-lighter` makes WebKit put every tile in its own transparency layer, and Prism's hardware pipeline runs
  out of offscreen textures (`IllegalArgumentException: Texture must be non-null` on every repaint). Only reproduces on
  the hardware pipeline, not under the tests' `prism.order=sw`.
- **`invalidateSize()` on WebView width/height changes** (`MapController`): Leaflet caches the container size at
  init, while the view still has its FXML placeholder size. Until the user pans or zooms, the restored or fitted view
  is applied again on each resize (a small network fitted to the early size zoomed in far too much).
- **No Leaflet vector layers**: its SVG and canvas renderers are moved by `translate3d` while zooming. Search markers
  are DOM markers; the position edit overlay is a canvas laid over the map container, redrawn on every move.

### Position edits

Position edits (`SubstationPosition`, `LinePosition`, both immutable) go through `network/edit/PositionEdits`, run
by `NetworkChanges.apply`: moving a substation moves the route ends at its old position, and a tie line's route is
its two boundary lines'. A voltage level may have no substation: reach a position through
`PositionEdits.substationCoordinate(VoltageLevel)`, never `getSubstation().get()`. Map edits are disabled while the
network is busy.

### Basemap

- The offline basemap, `map/countries.geojson`, is Natural Earth 1:50m admin-0 countries — public domain, no
  attribution required (https://www.naturalearthdata.com/about/terms-of-use/). Every feature's only property is
  `{"country": <ISO 3166 alpha-2>}` and every code must be a powsybl `Country` constant: loading fails otherwise.
- `MapTileRenderer.countryAt` reads the country of a place from that basemap, whichever basemap is shown; "New
  substation here" proposes it, falling back to the network's only country (at sea, or in a territory without code).
- To regenerate it: take `geojson/ne_50m_admin_0_countries.geojson` of https://github.com/nvkelso/natural-earth-vector
  (5.2.0-pre), keep the features in order, round every coordinate to 2 decimals (about 1 km) and drop consecutive
  duplicate points, reduce the properties to `{"country": ISO_A2_EH}` (empty for its 3 `-99` territories: Somaliland,
  N. Cyprus, Siachen Glacier), and write compact JSON.
