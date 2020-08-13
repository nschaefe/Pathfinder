import { render } from "./view.js";
import { Graphs } from "./graph.js";
import { Filters } from "./filtering.js";
import { Utils } from "./util.js";
import blacklist from './blacklist.json';
import txt from './tracker_report.txt';


try {
    var dets = Utils.parseTxtToJSON(txt)

    var tags = [...new Set(dets.map(d => [d.writer_task_tag, d.writer_trace_serial]))]
    console.log(tags)

    dets = dets.filter(d => d.tag == 'CONCURRENT WRITE/READ DETECTION' && d.writer_task_tag == "CreateTable_ClientStart" && d.writer_trace_serial == 3)

    var events = null//drill.fetchEvents(dets[0].writer_taskID)
    console.log("fetched data")

    function filtering(dets) {
        dets = Filters.filterDistinct(dets)
        //dets = Filters.filterBlacklist(dets, blacklist)
        //dets = Filters.filterCovered(dets,false)
        dets = Filters.filterByLocation(dets, "(.*\.bag)|(.*trackerTaskX)")
        dets = Filters.filterByTraces(dets, "(.*edu\.brown\.cs\.systems.*|java\.lang\.ref\.Finalizer.*|boundarydetection.*|java\.util\.concurrent\.locks.*|.*ConditionObject\.signalAll.*)")
        // dets = Filters.filterSiblings(dets)
        // edit distance and sibling filter onyl per thread pair, otherwise we compare things that belong to different channels.
        // What results in showing some detections only for one thread pair even if the communication appears in both (inconsistent, tradeoff)
        var grps = Filters.groupByReaderThreaderId(dets)
        var dd = grps.map((grpTuple) => {
            var grp = grpTuple[1]
            grp = Filters.filterSiblings(grp)
            //d = Filters.filterEditDistance(d, 0.95, true)

            //grp = grp.sort((a, b) => a.serial - b.serial)
            //if (Filters.filterCoveredGrpInteractive(grp, covered)) return null
            return grp
        })
        dd = dd.filter(e => e != null)
        var dets = [].concat.apply([], dd);

        dets = Filters.filterDuplicateCommunication(dets)
        // dets = Filters.filterSimilarCommunication(dets, [], 0.95)
        return dets
    }

    dets = filtering(dets)
    console.log("filtered data")

    var readerIDs = Array.from(new Set(dets.map(a => a.reader_thread_id)))
    readerIDs = readerIDs.sort() // to make things reproducible
    console.log("Reader-IDs: " + readerIDs)

    var cursor = 0
    var threadIDselection = readerIDs[cursor]
    dets = dets.filter(a => a.reader_thread_id == threadIDselection)

    dets = dets.sort((a, b) => a.serial - b.serial)
    const ITCP = Filters.filterDistinctPath(dets)
    console.log("ITCP:")
    console.log(ITCP)
    const ITCCP = Filters.filterDistinctCodePlace(dets)
    console.log("ITCCP:")
    console.log(ITCCP)
    const ITCMACP = Filters.filterDistinctMemoryAddressCodePlace(dets)
    console.log("ITCMACP:")
    console.log(ITCMACP)
    var resour = Filters.filterDistinctResources(Filters.filterByLocation(dets, "java|log4j"))
    console.log("Resources:")
    console.log(resour)

    var startEntry = "org.apache.hadoop.hbase.client.HBaseAdmin.createTable(HBaseAdmin.java:630)"
    dets.forEach(d => {
        d.writer_stacktrace = Utils.cutAfterLast(d.writer_stacktrace, startEntry)
        d.reader_stacktrace = Utils.cutAfterLast(d.reader_stacktrace, startEntry)
    })

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
