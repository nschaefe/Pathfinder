export var Graphs = new Graph()

function Graph() {
}

Graphs.parseDAG = function (dets, events, startEntry = "") {
    var node_map = new Map();
    var id = new Object()
    var depthLimit = 50
    id.val = 0
    for (var i = 0; i < dets.length; i++) {
        var detect = dets[i]
        var w_trace = JSON.parse(detect.writer_stacktrace)
        w_trace = cutAfterLast(w_trace, startEntry) //SET THIS TO STARTING POINT E.G org.apache.hadoop.hbase.client.HBaseAdmin.createTable(HBaseAdmin.java:601)
        w_trace = w_trace.reverse()
        var s = detect.location + "_" + (detect.parent != null ? "" : detect.reference)
        w_trace.push(s)
        var sink = parseTrace(w_trace, node_map, id, true)
        sink.sink = true

        //treat detection node
        sink.eventID = detect.eventID
        parseEvents(sink, events, node_map, id, depthLimit)

        var r_trace = JSON.parse(detect.reader_stacktrace)
        r_trace = r_trace.reverse()
        r_trace.push(s)
        sink = parseTrace(r_trace, node_map, id, false)
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
        return trace.slice(0, i)
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
            if (i != trace.length - 1) entry = trace[i] + '_' +  postfix
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

    var event_map;
    function parseEvents(root, eventData, node_map, id, depthLimit) {

        function hashEvents(events) {
            for (var event of events) {
                event_map.set(event.eventID, event)
            }
        }

        function createChildLinks(events) {
            for (var event of events) {
                var parents = JSON.parse(event.parentEventID)
                for (var parentID of parents) {
                    var ev_node = event_map.get(parentID)
                    if (ev_node == null) continue;
                    if (ev_node.ev_children == null) ev_node.ev_children = new Set()
                    ev_node.ev_children.add(event)
                }
            }
        }

        function getChildren(ev_node, events) {
            if (ev_node.ev_children != null) return ev_node.ev_children

            var children = []
            for (var event of events) {
                var pp = JSON.parse(event.parentEventID)
                if (pp.includes(ev_node.eventID)) children.push(event)
            }
            return children
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

        if (event_map == null) {
            event_map = new Map();
            hashEvents(eventData)
            createChildLinks(eventData)
        }
        parseFromSrc(root, root, eventData)
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
    var enabledNodes = []
    disableAll(graph)
    sink.enabled = true
    expandParentsRecursive(sink, enabledNodes)
    expandChildrenRecursive(sink, enabledNodes)
    //Graphs.shrinkStraightPaths(enabledNodes)
    //enabledNodes.forEach(n => n.textEnabled = true);
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
    while (hasSingleChild(n) && hasAtMostOneParent(n) && n.enabled == false) {
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
    node.class = getClass(name)
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

function getClass(st_el) {
    // this is a simple heurisitc for now, since names or what is logged will probably change
    var end = st_el.indexOf("(")
    if (end != -1) st_el = st_el.substring(0, end) // -1 means is a field, does not have brackets
    var names = st_el.split('.')

    var first = names[names.length - 1].charAt(0);
    if (first === first.toLowerCase() || end == -1) { // we rely on the convention that classes have big case latter at the start
        names = names.splice(0, names.length - 1)
    }
    return names.join('.')
}

