import { Utils } from "./util.js";

export var Graphs = new Graph()
function Graph() { }

Graphs.parseDAG = function (dets, events = null, startEntry = "") {
    var node_map = new Map();
    var id = new Object()
    id.val = 0
    for (var i = 0; i < dets.length; i++) {
        var detect = dets[i]
        var w_trace = detect.writer_stacktrace
        w_trace = cutAfterLast(w_trace, startEntry) //SET THIS TO STARTING POINT E.G org.apache.hadoop.hbase.client.HBaseAdmin.createTable(HBaseAdmin.java:601)
        w_trace = w_trace.reverse()
        var s = detect.location + (detect.parent != null ? "" : "_" + detect.reference)
        w_trace.push(s)
        var sink = parseTrace(w_trace, node_map, id, true, detect.writer_thread_id)
        sink.sink = true
        if (sink.jsons == null) sink.jsons = new Set()
        sink.jsons.add(detect)

        // can have multiple event ids because of node merging, several instances of the location over several java objects (e.g. hbase.Call will appear severla times in a run)
        if (sink.eventIDs == null) sink.eventIDs = new Set()
        sink.eventIDs.add(detect.eventID)

        // we want to maintain a logical order when the detection field was first hit by a reader
        // therefore we maintain the min serial number per detection over node merging
        if (sink.minSerial == null) sink.minSerial = Number.MAX_SAFE_INTEGER
        sink.minSerial = Math.min(sink.minSerial, detect.serial)

        if (sink.minWriterSerial == null) sink.minWriterSerial = Number.MAX_SAFE_INTEGER
        sink.minWriterSerial = Math.min(sink.minWriterSerial, detect.writer_global_clock)

        var r_trace = detect.reader_stacktrace
        r_trace = r_trace.reverse()
        r_trace.push(s)
        sink = parseTrace(r_trace, node_map, id, false, detect.reader_thread_id)
    }

    var sinks = Array.from(node_map.values()).filter(e => e.sink)

    // init an order starting at 0
    sinks.sort((a, b) => a.minSerial - b.minSerial)
    var c = 1
    for (var sink of sinks) sink.firstHitClock = c++

    sinks.sort((a, b) => a.minWriterSerial - b.minWriterSerial)
    c = 1
    for (var sink of sinks) sink.firstWriterHitClock = c++

    if (events != null) {
        var depthLimit = 75
        parseEventsFromStart(sinks, events, node_map, id, depthLimit)
    }
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

    function parseTrace(trace, node_map, id, isWriter, thId) {
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
                target = getNode(entry, id.val++, isWriter, thId)//this.id++ TODO
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

                var entry = child.eventID
                //var entry = child.text + '_' + 'RE' + '_' + id.val

                var target = node_map.get(entry)
                if (target == null) {
                    target = getNode(child.text, id.val++, false, child.thread_id)
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

Graphs.mergeEqualPathsRecursive = function (node, getChildren = (n) => n.children, childrenSet = (n, s) => n.children = s, nodeCompare = (a, b) => a.name.localeCompare(b.name)) {
    if (getChildren(node).size == 0) return;
    var children = Array.from(getChildren(node))
    for (var i = 0; i < children.length; i++) {
        Graphs.mergeEqualPathsRecursive(children[i], getChildren, childrenSet, nodeCompare)
    }

    for (var i = 0; i < children.length - 1; i++) { // last one has not partner
        var child = children[i]
        if (child == null) continue;
        for (var d = i + 1; d < children.length; d++) {
            var child_p = children[d]
            if (child_p == null) continue;
            if (equals(child, child_p, getChildren)) children[d] = null
        }
    }
    childrenSet(node, new Set(children.filter(e => e != null)))

    function equals(n1, n2, getChildren) {
        if (nodeCompare(n1, n2) != 0) return false

        var n1Children = getChildren(n1)
        var n2Children = getChildren(n2)
        if (n1Children.size != n2Children.size) return false
        if (n1Children.size == 0) return true

        n1Children = Array.from(n1Children)
        n1Children.sort(nodeCompare)

        n2Children = Array.from(n2Children)
        n2Children.sort(nodeCompare)

        for (var i = 0; i < n1Children.length; i++) {
            if (!equals(n1Children[i], n2Children[i], getChildren)) return false
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


Graphs.getViewRoots = function (nodes) {
    return getRootsInner(nodes.filter(d => d.enabled), (n) => n.viewParents)
}

Graphs.getRoots = function (nodes) {
    return getRootsInner(nodes, (n) => n.parents)
}

function getRootsInner(nodes, getParents) {
    //collect root nodes, make sets to arrays
    var roots = []
    nodes.forEach((el) => {
        if (getParents(el).size == 0) roots.push(el)
    })
    return roots
}

Graphs.hasCycle = function (nodes) {
    // TODO this could be optimized, since nodes are visited several times
    for (var i = 0; i < nodes.length; i++) {
        var n = nodes[i]
        if (hasCycleInner(n)) return true
    }
    return false

    function hasCycleInner(n) {
        if (n.visited != null) return true
        n.visited = true;

        for (var child of n.children) {
            if (hasCycleInner(child)) return true
        }
        n.visited = undefined
        return false
    }
}

Graphs.cutCycle = function (nodes) {
    // TODO this could be optimized, since nodes are visited several times
    for (var i = 0; i < nodes.length; i++) {
        var n = nodes[i]
        cutCycleInner(n)
    }

    function cutCycleInner(n) {
        if (n.visited != null) return true
        n.visited = true;

        for (var child of n.children) {
            var cycleEdge = cutCycleInner(child)
            if (cycleEdge) cutChild(n, child)
        }
        n.visited = undefined //delete helper variable
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

Graphs.expand = function (node, graph, limit = 250) {
    var changed = false

    if (node.sink) {
        expandForSink(node, graph, limit);
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
        n.viewParents = new Set();
        n.viewChildren = new Set();
    })
    graph.forEach(n => {
        if (n.enabled) {
            n.children.forEach(child => {
                var enabled_tgt = getEnabledOnLine(child)
                if (enabled_tgt != null) {
                    n.viewChildren.add(enabled_tgt)
                    enabled_tgt.viewParents.add(n)
                }
            });
        }
    });
}

function disableAll(graph) {
    graph.forEach((el) => {
        el.enabled = false
    })
}

function expandForSink(sink, graph, limit) {
    // TODO this method is awful, it does many arbitrary actions, partialy visually motivated. Split this in generic graph methods and
    // ui related stuff that goes in view.js
    var enabledNodes = []
    disableAll(graph)
    sink.enabled = true
    expandParentsRecursive(sink, enabledNodes)

    var children = sink.children
    var maxPaths = 8
    if (children.size > maxPaths) {
        //sampling for performance and reduce data that is presented (not optimal) TODO 
        alert("subset of reader path is displayed for performance reasons")
        children = Array.from(children).slice(0, maxPaths)
    }
    for (var child of children) {
        child.enabled = true
        expandChildrenRecursive(child, limit - 1)
    }

    //be carfeul when disabling arbitray nodes. Disablesing a node with several in or outputs results in disabling also all succeding nodes
    //Graphs.shrinkStraightPaths(enabledNodes)
    sink.parents.forEach(p => p.enabled = true);
}

function expandParentsRecursive(node, enabledNodes = null) {
    node.parents.forEach((d) => {
        d.enabled = true
        if (enabledNodes != null) enabledNodes.push(d)
        expandParentsRecursive(d, enabledNodes)
    })
}

function expandChildrenRecursive(node, limit, depth = 0) {
    if (depth >= limit) return
    node.children.forEach((d) => {
        d.enabled = true
        expandChildrenRecursive(d, limit, depth + 1)
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

function getNode(name, id, isWriter, thID) {
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
    node.threadID = thID
    return node
}
