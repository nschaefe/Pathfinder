export var Graphs = new Graph()

function Graph() {
}

Graphs.parseDAG = function (dets, events, startEntry = "") {
    var node_map = new Map();
    var id = new Object()
    var depthLimit = 25
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
    return Array.from(node_map.values());


    function cutAfterLast(trace, end) {
        var i = trace.lastIndexOf(end)
        if (i < 0) return trace
        return trace.slice(0, i)
    }

    function parseTrace(trace, node_map, id, isWriter) {
        var source = null
        var postfix = isWriter ? "" : "R"
        //is used to avoid recursion loops, count for every element, 
        //elements are merged if they appear in the same order in different stacktraces
        var unif_id = 0;
        for (var i = 0; i < trace.length; i++) {

            var entry
            //last is detection, not part of stacktrace
            if (i != trace.length - 1) entry = trace[i] + '_' + (unif_id++) + postfix
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
    function parseEvents(root, data, node_map, id, depthLimit) {

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

        function parseFromSrc(src_node, src_event, data, unifyID = 0, depthCounter = 0) {
            // idea: We follow paths as they are identical from the start to remove redundancy
            // if we process a new event we indroduce a new node
            // if we lookup a node that that does not belong to the same path, so does not have the last node as parent,
            // we branch and do not follow this node to avoid crosstalking of paths what results in too high node degrees what looks cluttered and "breaks" the layouting.
            // (layouting would be zick-zack like). For more information on merge degrees (tried 4 different variants) see documents/records.

            if (depthCounter > depthLimit) return
            var children = getChildren(src_event, data)

            for (var child of children) {
                var entry = child.text + '_' + 'RE' + '_' + (unifyID)

                var target = node_map.get(entry) // complete fresh path
                if (target == null) {
                    target = getNode(entry, id.val++, false, src_node)
                    node_map.set(entry, target)
                }
                else if (!src_node.children.has(target)) { // cross talking
                    unifyID = unifyID + Math.random() // opens subspace for the new branch by changing the numbers behind the comma
                    var entry = child.text + '_' + 'RE' + '_' + (unifyID)
                    target = getNode(entry, id.val++, false, src_node)
                    node_map.set(entry, target)
                }

                parseFromSrc(target, child, data, unifyID + 1, depthCounter + 1)
            }
        }

        if (event_map == null) {
            event_map = new Map();
            hashEvents(data)
            createChildLinks(data)
        }
        // by shifting the digits of the id behind the comma: 112 -> 0.112 we create a unique namespace for nodes under this root
        parseFromSrc(root, root, data, root.id / (numDigits(root.id) ** 10))

    }

}

Graphs.enableParentsOfSinks = function (graph) {
    graph.forEach(n => {
        if (n.sink) {
            n.parents.forEach(p => p.enabled = true);
        }
    });
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

Graphs.getRoots = function (nodes) {
    //collect root nodes, make sets to arrays
    var roots = []
    nodes.forEach((el) => {
        if (el.enabled && el.parents.size == 0) roots.push(el)
    })
    return roots
}

//TODO node accessors/ work on view
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

function getLink(source, target) {
    var link = new Object();
    link.source = source.id;
    link.target = target.id;
    return link
}
function getClass(st_el) {
    var end = st_el.indexOf("(")
    st_el = st_el.substring(0, end)
    var names = st_el.split('.')
    return names[names.length - 2]
}

function getName(name) {
    var a = name.indexOf(":")
    var b = name.indexOf(")")
    var lineNumber = name.substring(a, b)

    var end = name.indexOf("(")
    name = name.substring(0, end)

    var names = name.split('.')
    names = names.slice(names.length - 2, names.length);
    name = names.join('.')

    name += lineNumber
    return name

}

function numDigits(x) {
    return Math.max(Math.floor(Math.log10(Math.abs(x))), 0) + 1;
}
