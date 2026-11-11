/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */

// Edges are thin lines, hard to point at: a wider transparent copy of each, in its group, catches the pointer for it.
function widenEdges() {
    var paths = document.querySelectorAll('.nad-edge-path');
    for (var i = 0; i < paths.length; i++) {
        var hitArea = paths[i].cloneNode(false);
        hitArea.removeAttribute('class');
        hitArea.setAttribute('style', 'fill: none; stroke: transparent; stroke-width: 20px; pointer-events: stroke');
        paths[i].parentNode.insertBefore(hitArea, paths[i].nextSibling);
    }
    // transformer windings and injection circles are drawn unfilled: their inside catches the pointer too
    var circles = document.querySelectorAll('.nad-winding, .nad-injections circle');
    for (var j = 0; j < circles.length; j++) {
        circles[j].style.pointerEvents = 'visible';
    }
}

// called by ContainerDiagram right after it injects a diagram, rather than from a MutationObserver on svgContainer: in
// the WebView, its callback could run after the next script, or not at all
function diagramInjected() {
    widenEdges();
}

// the first element from target up to the root that ContainerDiagram knows something to do with on a click, or null.
// A voltage level's label is HTML in a foreignObject, whose id is the label's.
function clickableId(target) {
    for (var element = target; element; element = element.parentElement) {
        if (element.id && controller.isClickableEquipment(element.id)) {
            return element.id;
        }
    }
    return null;
}

document.addEventListener('click', function (event) {
    var id = clickableId(event.target);
    if (id) {
        controller.onEquipmentClick(id);
    }
}, false);

// the same lookup as the click, so that the pointer cursor shows exactly over what can be clicked
document.addEventListener('mouseover', function (event) {
    document.body.style.cursor = clickableId(event.target) ? 'pointer' : '';
}, false);

// called by ContainerDiagram on a right-click, the WebView not dispatching contextmenu events to the page (its own
// context menu is disabled): x, y in CSS pixels of the viewport
function equipmentContextMenuAt(x, y) {
    for (var element = document.elementFromPoint(x, y); element; element = element.parentElement) {
        if (element.id && controller.onEquipmentContextMenu(element.id)) {
            return;
        }
    }
    controller.onBackgroundContextMenu();
}

// Once the diagram is centered on another voltage level: a ring travels from the previous center to the new one, the
// view scrolling along when zoomed in, then outlines the new center for a while. The rings are HTML circles laid over
// the diagram in page coordinates, which the WebView's zoom scales like the diagram; injecting another diagram removes
// them. Driven frame by frame, as the WebView doesn't reliably finish CSS transitions.
var CENTER_TRAVEL_MS = 800;
var HIGHLIGHT_MS = 4000;
var HIGHLIGHT_FADE_MS = 1000;
var CENTER_RING_PADDING = 8;

// called by ContainerDiagram with the SVG id of the previous center's voltage level, null if not drawn, then those of
// the new center's
function animateCenter(fromId) {
    var targets = [];
    for (var i = 1; i < arguments.length; i++) {
        var node = document.getElementById(arguments[i]);
        if (node) {
            targets.push(circleOf(node));
        }
    }
    if (targets.length === 0) {
        return;
    }
    var fromNode = fromId ? document.getElementById(fromId) : null;
    var from = fromNode ? circleOf(fromNode) : targets[0];
    var rings = targets.map(function (target, index) {
        return ring(index === 0 ? from : target);
    });
    var start = Date.now();
    var travel = function () {
        var t = fromNode ? Math.min(1, (Date.now() - start) / CENTER_TRAVEL_MS) : 1;
        // ease in and out
        var eased = t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2;
        var current = {
            x: from.x + (targets[0].x - from.x) * eased,
            y: from.y + (targets[0].y - from.y) * eased,
            radius: from.radius + (targets[0].radius - from.radius) * eased
        };
        place(rings[0], current);
        if (fromNode) {
            // a no-op while the whole diagram fits in the view
            window.scrollTo(current.x - window.innerWidth / 2, current.y - window.innerHeight / 2);
        }
        if (t < 1) {
            requestAnimationFrame(travel);
        } else {
            fadeOut(rings);
        }
    };
    travel();
}

// a voltage level node's circle, in page coordinates
function circleOf(node) {
    var box = node.getBoundingClientRect();
    return {
        x: box.left + box.width / 2 + window.scrollX,
        y: box.top + box.height / 2 + window.scrollY,
        radius: Math.max(box.width, box.height) / 2 + CENTER_RING_PADDING
    };
}

function ring(circle) {
    var element = document.createElement('div');
    element.className = 'nad-center-ring';
    // clicks and hover go through to what it outlines
    element.setAttribute('style', 'position: absolute; box-sizing: border-box; border-radius: 50%; pointer-events: none;'
        + ' border: 3px solid #2e7d32; background: rgba(46, 125, 50, 0.1)');
    place(element, circle);
    document.getElementById('svgContainer').appendChild(element);
    return element;
}

function place(element, circle) {
    element.style.left = (circle.x - circle.radius) + 'px';
    element.style.top = (circle.y - circle.radius) + 'px';
    element.style.width = (2 * circle.radius) + 'px';
    element.style.height = (2 * circle.radius) + 'px';
}

function fadeOut(rings) {
    setTimeout(function () {
        var fadeStart = Date.now();
        var fade = function () {
            var opacity = 1 - (Date.now() - fadeStart) / HIGHLIGHT_FADE_MS;
            rings.forEach(function (element) {
                if (opacity > 0) {
                    element.style.opacity = opacity;
                } else {
                    element.remove();
                }
            });
            if (opacity > 0) {
                requestAnimationFrame(fade);
            }
        };
        requestAnimationFrame(fade);
    }, HIGHLIGHT_MS - HIGHLIGHT_FADE_MS);
}
