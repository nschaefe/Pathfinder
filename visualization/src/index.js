import { DrillDriver } from "./drill.js";
import { render } from "./view.js";
import { Graphs } from "./graph.js";
import { Filters } from "./filtering.js";
import stPluginTemplate from './storage_plugin_template.json';

try {
    var drill = new DrillDriver()

    var plugin = stPluginTemplate
    plugin.workspaces.root.location = "/home/nico/Dokumente/Entwicklung/Uni/Tracing/instrumentationhelper/reports/"
    plugin.workspaces.out.location = "/home/nico/Dokumente/Entwicklung/Uni/Tracing/instrumentationhelper/reports_filtered/"
    drill.loadStoragePlugin("rep", plugin)
    console.log("installed storage plugin")

    var dets = drill.fetchDetections(1)
    console.log("fetched data")

    dets = Filters.filterDistinct(dets)
    dets = Filters.filterCovered(dets, true)
    dets = Filters.filterByTraces(dets, "(.*edu\.brown\.cs\.systems.*|.*org\.apache\.hadoop\.hbase\.zookeeper.*)")
    dets = Filters.filterDuplicateCommunication(dets)
    console.log("filtered data")

    var readerIDs = Array.from(new Set(dets.map(a => a.reader_thread_id)))
    readerIDs = readerIDs.sort() // to make things reproducible for a report
    console.log("Reader-IDs: " + readerIDs)
    var selection = readerIDs[0]

    dets = dets.filter(a => a.reader_thread_id == selection)

    //"org.apache.hadoop.hbase.client.HTable.put(HTable.java:566)"
    //"org.apache.hadoop.hbase.ipc.RpcExecutor$Handler.run(RpcExecutor.java:324)"
    //"org.apache.hadoop.hbase.client.HBaseAdmin.createTable(HBaseAdmin.java:630)"
    //"org.apache.hadoop.hbase.procedure2.ProcedureExecutor$WorkerThread.run(ProcedureExecutor.java:2058)"
    var nodes = Graphs.parseDAG(dets)
    console.log("parsed data")

    render(nodes)
    console.log("rendered data")
    console.log("done")
}
catch (e) {
    alert("Errors, see browser console.\n" + e)
}

