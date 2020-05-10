import { DrillDriver } from "./drill.js";
import { render } from "./view.js";
import { Graphs } from "./graph.js";
import stPluginTemplate from './storage_plugin_template.json';

try {
    var drill = new DrillDriver()

    var plugin = stPluginTemplate
    plugin.workspaces.root.location = "/home/nico/Dokumente/Entwicklung/Uni/Tracing/instrumentationhelper/reports/"
    plugin.workspaces.out.location = "/home/nico/Dokumente/Entwicklung/Uni/Tracing/instrumentationhelper/reports_filtered/"
    drill.loadStoragePlugin("rep", plugin)
    console.log("installed storage plugin")

    var dets = drill.fetchDetections()
    var events = drill.fetchEvents(dets[0].writer_taskID)
    console.log("fetched data")

    //"org.apache.hadoop.hbase.client.HTable.put(HTable.java:566)"
    //"org.apache.hadoop.hbase.ipc.RpcExecutor$Handler.run(RpcExecutor.java:324)"
    //"org.apache.hadoop.hbase.client.HBaseAdmin.createTable(HBaseAdmin.java:630)"
    //"org.apache.hadoop.hbase.procedure2.ProcedureExecutor$WorkerThread.run(ProcedureExecutor.java:2058)"
    var nodes = Graphs.parseDAG(dets, events, "org.apache.hadoop.hbase.procedure2.ProcedureExecutor$WorkerThread.run(ProcedureExecutor.java:2058)")
    console.log("parsed data")

    render(nodes)
    console.log("done")
}
catch (e) {
    alert("Errors, see browser console.\n" + e)
}

