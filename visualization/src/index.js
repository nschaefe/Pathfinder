import { DrillDriver } from "./drill.js";
import { render } from "./view.js";
import { Graphs } from "./graph.js";
import { Filters } from "./filtering.js";
import stPluginTemplate from './storage_plugin_template.json';
import blacklist from './blacklist.json';

try {
    var drill = new DrillDriver()

    var plugin = stPluginTemplate
    plugin.workspaces.root.location = "/home/nico/Dokumente/Entwicklung/Uni/Tracing/instrumentationhelper/reports/"
    plugin.workspaces.out.location = "/home/nico/Dokumente/Entwicklung/Uni/Tracing/instrumentationhelper/reports_filtered/"
    drill.loadStoragePlugin("rep", plugin)
    console.log("installed storage plugin")

    console.log(drill.fetchTaskTags());

    var dets = drill.fetchDetections(1, "CallRunner_CreateTable")
    var events = null//drill.fetchEvents(dets[0].writer_taskID)
    console.log("fetched data")

    { // intersect over several traces
        var serials = drill.fetchTraceSerials().map(d => d.writer_trace_serial)
        var traces = []
        //for (var serial of serials) traces.push(drill.fetchDetections(serial))
        //dets = Filters.intersectWithTraces(dets, traces)
    }

    //dets = Filters.filterBlacklist(dets, blacklist)
    dets = Filters.filterDistinct(dets)
    dets = Filters.filterByLocation(dets, ".*\.bag")
    //"org\.apache\.hadoop\.hbase\.shaded\.protobuf\.generated.*"
    dets = Filters.filterByTraces(dets, "(.*edu\.brown\.cs\.systems.*|java\.lang\.ref\.Finalizer.*|boundarydetection.*|java\.util\.concurrent\.locks.*|.*ConditionObject\.signalAll.*)")
    dets = Filters.filterSiblings(dets)
    //dets = Filters.filterCovered(dets,false)
    dets = Filters.filterDuplicateCommunication(dets)
    console.log("filtered data")

    var readerIDs = Array.from(new Set(dets.map(a => a.reader_thread_id)))
    readerIDs = readerIDs.sort() // to make things reproducible for a report
    console.log("Reader-IDs: " + readerIDs)
    var selection = readerIDs[0]

    dets = dets.filter(a => a.reader_thread_id == selection)
    const ITCP = Filters.filterDistinctPath(dets)
    console.log(ITCP)
    const ITCCP = Filters.filterDistinctCodePlace(dets)
    console.log(ITCCP)
    const ITCMACP = Filters.filterDistinctMemoryAddressCodePlace(dets)
    console.log(ITCMACP)
    //"org.apache.hadoop.hbase.client.HTable.put(HTable.java:566)"
    //"org.apache.hadoop.hbase.ipc.RpcExecutor$Handler.run(RpcExecutor.java:324)"
    //"org.apache.hadoop.hbase.client.HBaseAdmin.createTable(HBaseAdmin.java:631)"
    //"org.apache.hadoop.hbase.procedure2.ProcedureExecutor$WorkerThread.run(ProcedureExecutor.java:2058)"
    var nodes = Graphs.parseDAG(dets, events)
    console.log("parsed data")

    render(nodes)
    console.log("rendered data")
    console.log("done")
}
catch (e) {
    alert("Errors, see browser console.\n" + e)
}
