import { Utils } from "./util.js";

export var Graphs = new Graph()
function Graph() { }

Graphs.parseDAG = function (dets, events, startEntry = "") {
    var node_map = new Map();
    var id = new Object()
    var depthLimit = 75
    id.val = 0
    for (var i = 0; i < dets.length; i++) {
        var detect = dets[i]
        var w_trace = JSON.parse(detect.writer_stacktrace)
        w_trace = cutAfterLast(w_trace, startEntry) //SET THIS TO STARTING POINT E.G org.apache.hadoop.hbase.client.HBaseAdmin.createTable(HBaseAdmin.java:601)
        w_trace = w_trace.reverse()
        var s = detect.location + (detect.parent != null ? "" : "_" + detect.reference)
        w_trace.push(s)
        var sink = parseTrace(w_trace, node_map, id, true)
        sink.sink = true

        // can have multiple event ids because of node merging, several instances of the location over several java objects (e.g. hbase.Call will appear severla times in a run)
        if (sink.eventIDs == null) sink.eventIDs = new Set()
        sink.eventIDs.add(detect.eventID)

        // we want to maintain a logical order when the detection field was first hit by a reader
        // therefore we maintain the min serial number per detection over node merging
        if (sink.minSerial == null) sink.minSerial = Number.MAX_SAFE_INTEGER
        sink.minSerial = Math.min(sink.minSerial, detect.serial)

        var r_trace = JSON.parse(detect.reader_stacktrace)
        r_trace = r_trace.reverse()
        r_trace.push(s)
        sink = parseTrace(r_trace, node_map, id, false)
    }

    var sinks = Array.from(node_map.values()).filter(e => e.sink)

    // init an order starting at 0
    sinks.sort((a, b) => a.minSerial - b.minSerial)
    var c = 1
    for (var sink of sinks) sink.firstHitClock = c++

    parseEventsFromStart(sinks, events, node_map, id, depthLimit)
    var nodes = Array.from(node_map.values());

    // Depending on the node merging strategy when parsing, cycles can occur
    // to get a DAG we cut the cycles, if no cycles this is a no-op 
    // We want a dag, beacuse dag layout libraries produce a better result than the graph force layout
    Graphs.cutCycle(nodes)

    // to remove redundancy (e.g. different writes can trigger the same execution on the reader side,
    // what results in the same event stream several times
    Graphs.getRoots(nodes).forEach(n => Graphs.mergeEqualPathsRecursive(n))
    return nodes

    function cutAfterLast(trace, end) {
        var i = trace.lastIndexOf(end)
        if (i < 0) return trace
        return trace.slice(0, i + 1)
    }

    function parseTrace(trace, node_map, id, isWriter) {
        var source = null
        var postfix = isWriter ? "W" : "R"
        //is used to avoid recursion loops, count for every element, 
        //elements are merged if they appear in the same order in different stacktraces
        var unif_id = 0;
        for (var i = 0; i < trace.length; i++) {

            var entry
            //last is detection, not part of stacktrace
            if (i != trace.length - 1) entry = trace[i] + '_' + postfix //+ (unif_id++) + '_'
            else entry = trace[i]

            // get node if existant
            var target = node_map.get(entry)
            if (target == null) {
                target = getNode(entry, id.val++, isWriter)//this.id++ TODO
                if (source == null) target.root = true
                node_map.set(entry, target)
            }

            //link source to target
            if (source != null) {
                source.children.add(target)
                target.parents.add(source)
            }
            source = target
            target = null
        }
        return source

    }

    function parseEventsFromStart(startSinks, eventData, node_map, id, depthLimit) {
        var event_map = new Map();

        // hashing
        startSinks.forEach(el => {
            el.eventIDs.forEach(id => event_map.set(id, el))
            // we init the children set here, because it is in priciple possible that a sink has no child, if it was generated right before shutdown and 
            // no event was emmited
            el.ev_children = new Set()
        })
        for (var event of eventData) {
            event_map.set(event.eventID, event)
        }
        createChildLinksForParents(eventData)

        startSinks.forEach(sink => {
            // parsing TODO
            parseFromSrc(sink, sink, eventData)
        })

        function createChildLinksForParents(events) {
            for (var event of events) {
                var parents = JSON.parse(event.parentEventID)
                for (var parentID of parents) {
                    var ev_node = event_map.get(parentID)
                    if (ev_node == null) {
                        console.log("WANING: parent with id " + parentID +
                            " NOT IN MAP, maybe detection was filtered out but not the correpsonding events, parent is skipped")
                        continue;
                    }
                    if (ev_node.ev_children == null) ev_node.ev_children = new Set()
                    ev_node.ev_children.add(event)
                }
                // end nodes are not parent of another node, we init the childset here to hit all nodes
                if (event.ev_children == null) event.ev_children = new Set()
            }
        }


        function getChildren(ev_node, events) {
            console.assert(ev_node.ev_children != null, "NO CHILDREN")
            return ev_node.ev_children
        }

        function parseFromSrc(src_node, src_event, data, depthCounter = 0) {
            if (depthCounter > depthLimit) return
            var children = getChildren(src_event, data)

            for (var child of children) {
                var entry = child.text + '_' + 'RE' + '_' + id.val

                var target = node_map.get(entry)
                if (target == null) {
                    target = getNode(child.text, id.val++, false)
                    node_map.set(entry, target)
                }

                src_node.children.add(target)
                target.parents.add(src_node)

                parseFromSrc(target, child, data, depthCounter + 1)
            }
        }


    }


}

Graphs.enableParentsOfSinks = function (graph) {
    graph.forEach(n => {
        if (n.sink) {
            n.parents.forEach(p => p.enabled = true);
        }
    });
}

Graphs.mergeEqualPathsRecursive = function (node, nodeCompare = (a, b) => a.name.localeCompare(b.name)) {
    if (node.children.size == 0) return;
    var children = Array.from(node.children)
    for (var i = 0; i < children.length; i++) {
        Graphs.mergeEqualPathsRecursive(children[i])
    }

    for (var i = 0; i < children.length - 1; i++) { // last one has not partner
        var child = children[i]
        if (child == null) continue;
        for (var d = i + 1; d < children.length; d++) {
            var child_p = children[d]
            if (child_p == null) continue;
            if (equals(child, child_p)) children[d] = null
        }
    }
    node.children = new Set(children.filter(e => e != null))

    function equals(n1, n2) {
        if (nodeCompare(n1, n2) != 0) return false
        if (n1.children.size != n2.children.size) return false
        if (n1.children.size == 0) return true

        var n1_children = Array.from(n1.children)
        n1_children.sort(nodeCompare)

        var n2_children = Array.from(n2.children)
        n2_children.sort(nodeCompare)

        for (var i = 0; i < n1_children.length; i++) {
            if (!equals(n1_children[i], n2_children[i])) return false
        }
        return true
    }
}

Graphs.disableReaderTraces = function (graph) {
    graph.forEach(n => {
        if (!n.isWriter) n.enabled = false
    });
}

Graphs.canExpand = function (node) {
    for (var p of node.parents.values()) {
        if (p.enabled == false) return true;
    }
    return false;
}

//TODO make clear working on view or on all
Graphs.getEnabledRoots = function (nodes) {
    return Graphs.getRoots(nodes).filter(d => d.enabled)
}

Graphs.getRoots = function (nodes) {
    //collect root nodes, make sets to arrays
    var roots = []
    nodes.forEach((el) => {
        if (el.parents.size == 0) roots.push(el)
    })
    return roots
}

Graphs.hasCycle = function (nodes) {
    var roots = Graphs.getRoots(nodes)
    for (var i = 0; i < roots.length; i++) {
        var n = roots[i]
        if (hasCycleInner(n)) return true
    }
    return false

    function hasCycleInner(n) {
        if (n.visited != null) return true
        n.visited = true;

        for (var child of n.children) {
            if (hasCycleInner(child)) return true
        }
        n.visited = null
        return false
    }
}

Graphs.cutCycle = function (nodes) {
    var roots = Graphs.getRoots(nodes)
    for (var i = 0; i < roots.length; i++) {
        var n = roots[i]
        cutCycleInner(n)
    }

    function cutCycleInner(n) {
        if (n.visited != null) return true
        n.visited = true;

        for (var child of n.children) {
            var cycleEdge = cutCycleInner(child)
            if (cycleEdge) cutChild(n, child)
        }
        n.visited = null
        return false
    }

    function cutChild(n, c) {
        n.cuttedChildren.add(c)
        n.children.delete(c)
        c.parents.delete(n)
    }
}


Graphs.shrinkStraightPaths = function (nodes) {
    for (var i = 0; i < nodes.length; i++) {
        var n = nodes[i]
        if (!n.enabled) continue;

        var next = getNextEnabledOnLine(n)
        if (next != null) {
            var next_next = getNextEnabledOnLine(next)
            if (next_next != null) {
                next.enabled = false
                i--;
            }
        }
    }
}

Graphs.expand = function (node, graph) {
    var changed = false

    if (node.sink) {
        expandForSink(node, graph);
        changed = true
    }
    else {
        node.parents.forEach((d) => {
            if (!d.enabled) {
                changed = true;
                d.enabled = true
            }
        })
    }
    return changed
}


Graphs.updateLinks = function (graph) {
    graph.forEach(n => {
        if (n.enabled) {
            n.viewChildren = new Set()
            n.children.forEach(child => {
                var enabled_tgt = getEnabledOnLine(child)
                if (enabled_tgt != null) n.viewChildren.add(enabled_tgt)
            });
        }
    });
}

function disableAll(graph) {
    graph.forEach((el) => {
        el.enabled = false
    })
}

function expandForSink(sink, graph) {
    // TODO this method is awful, it does many arbitrary actions, partialy visually motivated. Split this in generic graph methods and
    // ui related stuff that goes in view.js
    var enabledNodes = []
    disableAll(graph)
    sink.enabled = true
    expandParentsRecursive(sink, enabledNodes)

    // TODO no java filter here
    var en = []
    expandChildrenRecursive(sink, en)
    en.forEach(n => { if (n.name.startsWith("java")) n.enabled = false })
    enabledNodes = enabledNodes.concat(en)

    //Graphs.shrinkStraightPaths(enabledNodes)
    sink.parents.forEach(p => p.enabled = true);
}

function expandParentsRecursive(node, enabledNodes) {
    node.parents.forEach((d) => {
        d.enabled = true
        enabledNodes.push(d)
        expandParentsRecursive(d, enabledNodes)
    })
}

function expandChildrenRecursive(node, enabledNodes) {
    node.children.forEach((d) => {
        d.enabled = true
        enabledNodes.push(d)
        expandChildrenRecursive(d, enabledNodes)
    })
}

function hasSingleChild(n) {
    return n.children.size == 1
}
function hasAtMostOneParent(n) {
    return n.parents.size <= 1
}

function getSingleEntry(set) {
    return set.values().next().value;
}

function getEnabledOnLine(n) {
    // Does not support branches
    while (hasSingleChild(n) && hasAtMostOneParent(n) && !n.enabled) {
        n = getSingleEntry(n.children);
    }
    if (n.enabled) return n;
    return null;
}

function getNextEnabledOnLine(n) {
    if (hasSingleChild(n) && hasAtMostOneParent(n)) n = getSingleEntry(n.children);
    else return null;
    return getEnabledOnLine(n)
}

function getNode(name, id, isWriter, parent = null) {
    var node = new Object();
    node.id = id
    node.class = Utils.getClassName(name)
    node.name = name;
    node.enabled = true;
    node.children = new Set()
    node.parents = new Set()
    node.viewChildren = new Set()
    node.cuttedChildren = new Set()
    node.isWriter = isWriter
    if (parent != null) {
        node.parents.add(parent)
        parent.children.add(node)
    }
    return node
}
