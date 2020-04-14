import { DrillDriver } from "./drill.js";
import { render } from "./view.js";
import { Graphs } from "./graph.js";

var drill = new DrillDriver()
drill.fetch(process)

function process(data) {
    var nodes = Graphs.parseDAG(data, "org.apache.hadoop.hbase.client.HBaseAdmin.createTable(HBaseAdmin.java:601)")
    render(nodes)
}

