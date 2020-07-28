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

    var dets = drill.fetchDetections(3, "CreateTable_ClientStart")
    dets.forEach(el => {
        el.reader_joined_trace_ids = JSON.parse(el.reader_joined_trace_ids);
        el.writer_stacktrace = JSON.parse(el.writer_stacktrace);
        el.reader_stacktrace = JSON.parse(el.reader_stacktrace);
    });


    var events = null//drill.fetchEvents(dets[0].writer_taskID)
    console.log("fetched data")

    { // intersect over several traces
        var serials = drill.fetchTraceSerials().map(d => d.writer_trace_serial)
        var traces = []
        //for (var serial of serials) traces.push(drill.fetchDetections(serial))
        //dets = Filters.intersectWithTraces(dets, traces)
    }

    function filtering(dets) {
        dets = Filters.filterDistinct(dets)
        //dets = Filters.filterBlacklist(dets, blacklist)
        //dets = Filters.filterCovered(dets,false)
        dets = Filters.filterByLocation(dets, ".*\.bag")
        dets = Filters.filterByTraces(dets, "(.*edu\.brown\.cs\.systems.*|java\.lang\.ref\.Finalizer.*|boundarydetection.*|java\.util\.concurrent\.locks.*|.*ConditionObject\.signalAll.*)")
        // dets = Filters.filterSiblings(dets)
        // edit distance and sibling filter onyl per thread pair, otherwise we compare things that belong to different channels.
        // What results in showing some detections only for one thread pair even if the communication appears in both (inconsistent, tradeoff)
        var grps = Filters.groupByReaderThreaderId(dets)
        var dd = grps.map((grp) => {
            var d = grp[1]
            d = Filters.filterSiblings(d)
            //d = Filters.filterEditDistance(d, 0.95, true)
            return d
        })
        var dets = [].concat.apply([], dd);

        dets = Filters.filterDuplicateCommunication(dets)
        // dets = Filters.filterSimilarCommunication(dets, [], 0.95)
        return dets
    }

    dets = filtering(dets)
    console.log("filtered data")

    var readerIDs = Array.from(new Set(dets.map(a => a.reader_thread_id)))
    readerIDs = readerIDs.sort() // to make things reproducible for a report
    console.log("Reader-IDs: " + readerIDs)

    var cursor = 0
    var threadIDselection = readerIDs[cursor]
    dets = dets.filter(a => a.reader_thread_id == threadIDselection)
    const ITCP = Filters.filterDistinctPath(dets)
    console.log(ITCP)
    const ITCCP = Filters.filterDistinctCodePlace(dets)
    console.log(ITCCP)
    const ITCMACP = Filters.filterDistinctMemoryAddressCodePlace(dets)
    console.log(ITCMACP)
    //"org.apache.hadoop.hbase.client.HTable.put(HTable.java:566)"
    //"org.apache.hadoop.hbase.ipc.RpcExecutor$Handler.run(RpcExecutor.java:324)"
    //"org.apache.hadoop.hbase.client.HBaseAdmin.createTable(HBaseAdmin.java:631)"
    //"org.apache.hadoop.hbase.client.HBaseAdmin.createTable(HBaseAdmin.java:629)")
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
