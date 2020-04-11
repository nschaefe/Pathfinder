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
        w_trace = cutAfterLast(w_trace, "") //SET THIS TO STARTING POINT E.G 
        w_trace = w_trace.reverse()
        w_trace.push(detect.location)
        var sink = parseTrace(w_trace, node_map, id, true)
        sink.sink = true

        var r_trace = JSON.parse(detect.reader_stacktrace)
        r_trace = r_trace.reverse()
        r_trace.push(detect.location)
        sink = parseTrace(r_trace, node_map, id, false)

    }
    var nodes = Array.from(node_map.values());

    //console.log(JSON.stringify(nodes))
    //console.log(JSON.stringify(links))

    render(nodes)
}

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