import { DrillDriver } from "./drill.js";
import { render } from "./view.js";
import { Graphs } from "./graph.js";

var drill = new DrillDriver()

var dets = drill.fetchDetections()
var events = drill.fetchEvents()

//"org.apache.hadoop.hbase.ipc.RpcExecutor$Handler.run(RpcExecutor.java:324)"
var nodes = Graphs.parseDAG(dets, events, "org.apache.hadoop.hbase.client.HBaseAdmin.createTable(HBaseAdmin.java:601)")
render(nodes)


