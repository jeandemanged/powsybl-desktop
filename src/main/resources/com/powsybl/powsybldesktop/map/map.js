
// JavaFX's WebKit mis-composites the translate3d-positioned panes/tiles Leaflet uses by default, which
// renders the tile grid and the vector pane as disjoint blocks stuck at stale offsets. Forcing any3d off
// (read as a property at every positioning call, so this takes effect) falls back to plain left/top.
L.Browser.any3d = false;

// OpenStreetMap's, and also the network layer's: Leaflet stacks a tile layer's zoom levels by z-index computed from
// its maxZoom, and without one, a level zoomed back to could end up below the level it should replace.
var MAX_ZOOM = 19;
// Tiles replaced after a zoom are removed this long after all their replacements loaded, so that the WebView has
// painted those first: removing a tile as soon as its replacement loaded flickered.
var PRUNE_DELAY_MS = 100;

// animations off: the WebView doesn't reliably finish Leaflet's CSS transitions, and an interrupted one
// leaves stale frames on screen.
var map = L.map('map', {
    maxZoom: MAX_ZOOM,
    zoomAnimation: false,
    fadeAnimation: false,
    markerZoomAnimation: false
}).setView([48.8566, 2.3522], 5);

// With zoom animation off, every zoom resets the map view, and grid layers drop all their tiles on the
// viewprereset event that starts it: the map went blank until the new zoom level's tiles arrived. Without that
// handler, the viewreset that follows updates the layer as an animated zoom would, keeping the previous level's
// tiles, scaled, until the new ones replace them.
var keepTilesOnViewReset = {
    getEvents: function () {
        var events = L.GridLayer.prototype.getEvents.call(this);
        delete events.viewprereset;
        return events;
    }
};

var osmLayer = new (L.TileLayer.extend(keepTilesOnViewReset))('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: MAX_ZOOM,
    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
});
osmLayer.on('tileload', function () {
    window.controller.onTileLoad(true);
});
osmLayer.on('tileerror', function () {
    window.controller.onTileLoad(false);
});
// sea color behind the offline basemap's countries, drawn in the network tiles; OSM tiles cover it
var SEA_COLOR = '#aad3df';
var HOVER_THROTTLE_MS = 32;

// The network, and the offline basemap, are drawn by MapController into tiles, on background threads: drawn
// here, on the FX thread that runs this script, a large network froze the UI on every load, pan and zoom. A
// requested tile arrives later through onTileRendered, as a PNG data URL, or an empty string when there is nothing
// to draw in it.
var NetworkTileLayer = L.GridLayer.extend(keepTilesOnViewReset).extend({
    options: {
        maxZoom: MAX_ZOOM
    },

    createTile: function (coords, done) {
        var tile = document.createElement('img');
        tile.alt = '';
        requestTile(tile, coords, done);
        return tile;
    },

    // Draws all tiles again, e.g. with other base voltages hidden, in place: unlike redraw(), which drops them all
    // first, each tile keeps its current image until the new one is ready.
    refresh: function () {
        for (var key in this._tiles) {
            var tile = this._tiles[key];
            var id = tile.el.networkTileId;
            var pending = pendingTiles[id];
            if (pending) {
                delete pendingTiles[id];
                window.controller.cancelTile(id);
            }
            // a tile still waiting for its first image keeps Leaflet waiting for the new one instead
            requestTile(tile.el, this._wrapCoords(tile.coords), pending ? pending.done : null);
        }
    },

    // Leaflet prunes as each tile loads, dropping the tiles of the previous zoom level it covers; deferred here
    // until all tiles are loaded, so the previous level's tiles go all at once, see PRUNE_DELAY_MS.
    _pruneTiles: function () {
        if (!this._noTilesToLoad()) {
            return;
        }
        clearTimeout(this._pruneTimer);
        var layer = this;
        this._pruneTimer = setTimeout(function () {
            L.GridLayer.prototype._pruneTiles.call(layer);
        }, PRUNE_DELAY_MS);
    }
});
var lastTileId = 0;
var pendingTiles = {};

function requestTile(tile, coords, done) {
    var id = ++lastTileId;
    tile.networkTileId = id;
    pendingTiles[id] = {tile: tile, done: done};
    window.controller.requestTile(id, coords.z, coords.x, coords.y, window.devicePixelRatio || 1);
}

function onTileRendered(id, url) {
    var pending = pendingTiles[id];
    if (!pending) {
        return;
    }
    delete pendingTiles[id];
    var tile = pending.tile;
    function finish() {
        if (pending.done) {
            pending.done(null, tile);
        }
    }
    if (!url) {
        tile.removeAttribute('src');
        finish();
        return;
    }
    // decoded off-screen first, so that a refreshed tile goes straight from its previous image to this one
    var image = new Image();
    image.onload = function () {
        // unless refreshed again meanwhile, the newer image then being the one to show
        if (tile.networkTileId === id) {
            tile.src = url;
        }
        finish();
    };
    image.src = url;
}

// above the OSM tiles, whose z-index is 1
var networkLayer = new NetworkTileLayer({zIndex: 10});
// Leaflet drops tiles e.g. when zooming or panning past them before they arrive: no need to draw them anymore
networkLayer.on('tileunload', function (e) {
    var id = e.tile.networkTileId;
    if (pendingTiles[id]) {
        delete pendingTiles[id];
        window.controller.cancelTile(id);
    }
});

// Called by MapController once window.controller is set: adding the layer requests its first tiles right away,
// which failed when done as this script loaded, before the page load completed and MapController set it.
function addNetworkLayer() {
    networkLayer.addTo(map);
}

function redrawNetwork() {
    setHovered(null);
    networkLayer.refresh();
}

function setBasemap(name) {
    if (name === 'offline') {
        map.removeLayer(osmLayer);
    } else {
        osmLayer.addTo(map);
    }
    map.getContainer().style.background = name === 'offline' ? SEA_COLOR : '';
    redrawNetwork();
}

// A single shared tooltip, re-anchored whenever the hovered element changes. Hover and clicks are hit-tested by
// MapController, which returns what is under the mouse as {key, text, lat, lng}.
var tooltip = L.tooltip();
var hovered = null;
var pendingHoverEvent = null;
var hoverTimer = null;

function setHovered(hit) {
    if ((hit && hit.key) === (hovered && hovered.key)) {
        return;
    }
    hovered = hit;
    map.getContainer().style.cursor = hit ? 'pointer' : '';
    if (hit) {
        tooltip.setLatLng([hit.lat, hit.lng]).setContent(hit.text);
        if (!map.hasLayer(tooltip)) {
            tooltip.addTo(map);
        }
    } else if (map.hasLayer(tooltip)) {
        map.removeLayer(tooltip);
    }
}

map.on('mousemove', function (e) {
    pendingHoverEvent = e;
    if (hoverTimer === null) {
        hoverTimer = setTimeout(function () {
            hoverTimer = null;
            if (!map.dragging.moving()) {
                var latlng = pendingHoverEvent.latlng;
                var hit = window.controller.hitTest(latlng.lat, latlng.lng, map.getZoom());
                setHovered(hit ? JSON.parse(hit) : null);
            }
        }, HOVER_THROTTLE_MS);
    }
});

map.on('mouseout', function () {
    setHovered(null);
});

map.on('click', function (e) {
    window.controller.onMapClick(e.latlng.lat, e.latlng.lng, map.getZoom());
});

// Called by MapController once a network is ready to be drawn, with the south, west, north and east of what it
// draws, null for nothing.
function renderNetwork(boundsJson) {
    // before fitting the view: tiles it adds are drawn from the new network already
    redrawNetwork();
    var b = JSON.parse(boundsJson);
    if (b && b[0] === b[2] && b[1] === b[3]) {
        map.setView([b[0], b[1]], 12, {animate: false});
    } else if (b) {
        map.fitBounds([[b[0], b[1]], [b[2], b[3]]], {padding: [20, 20], animate: false});
    }
}
