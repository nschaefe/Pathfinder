import { getData } from "./drill.js";
import { render } from "./graph.js";

getData(f)
function f(data) {
    var node_map = new Map();
    var link_set = new Set();

    var id = new Object()
    id.val = 0
    for (var i = 0; i < data.length; i++) {
        var a = data[i]
        var w_trace = JSON.parse(a.writer_stacktrace)
        parseTrace(w_trace, node_map, link_set, id)
        // var r_trace = JSON.parse(a.reader_stacktrace)
    }
    var nodes = Array.from(node_map.values());
    var links = Array.from(link_set);


    console.log(JSON.stringify(nodes))
    console.log(JSON.stringify(links))

    var data = new Object();
    data.nodes = nodes
    data.links = links
    render(data)
}

function parseTrace(trace, node_map, link_set, id) {
    var source = null
    for (var i = 0; i < trace.length; i++) {
        var entry = trace[i]

        // get node if existant
        var target = node_map.get(entry)
        if (target == null) {
            target = getNode(entry, id.val)//this.id++ TODO
            id.val++
            node_map.set(entry, target)
        }

        //link source to target
        if (source != null) {
            link_set.add(getLink(source, target))
        }
        source = target
        target = null
    }

}

function getNode(trace_el, id) {
    var node = new Object();
    node.id = id
    node.name = trace_el;
    return node
}

function getLink(source, target) {
    var link = new Object();
    link.source = source.id;
    link.target = target.id;
    return link
}