import { getData } from "./drill.js";
import { render } from "./graph.js";

getData(f)
function f(data) {
    var node_map = new Map();


    var id = new Object()
    id.val = 0
    for (var i = 0; i < data.length; i++) {
        var detect = data[i]
        var w_trace = JSON.parse(detect.writer_stacktrace)
        w_trace = w_trace.reverse()
        w_trace.push(detect.location)
        var sink = parseTrace(w_trace, node_map, id)
        sink.sink = true

        var r_trace = JSON.parse(detect.reader_stacktrace)
        r_trace = r_trace.reverse()
        r_trace.push(detect.location)
        sink = parseTrace(r_trace, node_map, id)

    }
    var nodes = Array.from(node_map.values());
    nodes = shrinkStraightPaths(nodes)

    //console.log(JSON.stringify(nodes))
    //console.log(JSON.stringify(links))

    // for (var i = 0; i < nodes.length; i++) {
    //     var n = nodes[i]
    //     var b = Math.random() >= 0.5;
    //     n.enabled = b
    // }

    var data = new Object();
    data.nodes = nodes
    data.links = getLinks(nodes)
    render(data)
}


function shrinkStraightPaths(nodes) {
    var removedNodes = new Set()
    for (var i = 0; i < nodes.length; i++) {
        var n = nodes[i]
        if (n.children != null && n.children.size == 1) {
            var next = getSingleEntry(n.children)
            if (next.children != null && next.children.size == 1) {
                n.children = new Set([getSingleEntry(next.children)])
                removedNodes.add(next)
                i--
            }
        }
    }

    var dd = new Set()
    for (var i = 0; i < nodes.length; i++) {
        var n = nodes[i]
        if (n.root) dd.add(n)
        if (n.children != null)
            n.children.forEach(child => {
                dd.add(child)
            });
    }
    return Array.from(dd)
}

function getSingleEntry(set) {
    return set.values().next().value;
}

function nextEnabledOnLine(n) {
    while (n.enabled == false && n.children != null && n.children.length == 1) {
        n = n.children[0]
    }
    return n

}

function getLinks(n) {
    var linkSet = new Set();
    n.forEach(n => {
        if (n.children != null) {
            n.children.forEach(tgt => {
                linkSet.add(getLink(n, tgt))
            });
        }
    });
    return Array.from(linkSet)
}

// function shrinkStraightPaths(nodes) {
//     for (var i = 0; i < nodes.length; i++) {
//         var n = nextEnabledOnLine(nodes[i])
//         if (n.children != null && n.children.length == 1) {
//             var next = nextEnabledOnLine(n.children[0])
//             if (next.children != null && next.children.length == 1) {
//                 next.enabled = false
//                 i--
//             }
//         }
//     }
// }

// function nextEnabledOnLine(n) {
//     while (n.enabled == false && n.children != null && n.children.length == 1) {
//         n = n.children[0]
//     }
//     return n

// }


function parseTrace(trace, node_map, id) {
    var source = null
    for (var i = 0; i < trace.length; i++) {

        var entry
        if (i != trace.length - 1) // last is detection, not part of stacktrace
            entry = getName(trace[i])
        else
            entry = trace[i]

        // get node if existant
        var target = node_map.get(entry)
        if (target == null) {
            target = getNode(entry, id.val++)//this.id++ TODO
            if (source == null)// root
                target.root = true
            node_map.set(entry, target)
        }

        //link source to target
        if (source != null) {
            if (source.children == null)
                source.children = new Set()
            source.children.add(target)
        }
        source = target
        target = null
    }
    return source

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

function getNode(name, id) {
    var node = new Object();
    node.id = id
    node.name = name;
    node.enabled = true;
    return node
}

function getLink(source, target) {
    var link = new Object();
    link.source = source.id;
    link.target = target.id;
    return link
}