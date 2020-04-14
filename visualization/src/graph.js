export var Graphs = new Graph()

function Graph() {
}

Graphs.parseDAG = function (data, startEntry = "") {
    var node_map = new Map();
    var id = new Object()
    id.val = 0
    for (var i = 0; i < data.length; i++) {
        var detect = data[i]
        var w_trace = JSON.parse(detect.writer_stacktrace)
        w_trace = cutAfterLast(w_trace, startEntry) //SET THIS TO STARTING POINT E.G org.apache.hadoop.hbase.client.HBaseAdmin.createTable(HBaseAdmin.java:601)
        w_trace = w_trace.reverse()
        w_trace.push(detect.location)
        var sink = parseTrace(w_trace, node_map, id, true)
        sink.sink = true

        var r_trace = JSON.parse(detect.reader_stacktrace)
        r_trace = r_trace.reverse()
        r_trace.push(detect.location)
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
        if (el.root && el.enabled) roots.push(el)
    })
    return roots
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
    Graphs.shrinkStraightPaths(enabledNodes)
    sink.parents.forEach(p => p.enabled = true);
}

function expandParentsRecursive(node, enabledNodes) {
    node.parents.forEach((d) => {
        d.enabled = true
        enabledNodes.push(d)
        expandParentsRecursive(d, enabledNodes)
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


function getNode(name, id, isWriter) {
    var node = new Object();
    node.id = id
    node.class = getClass(name)
    node.name = name;
    node.enabled = true;
    node.children = new Set()
    node.parents = new Set()
    node.viewChildren = new Set()
    node.isWriter = isWriter
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
