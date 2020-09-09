import { render } from "./view.js";
import { Graphs } from "./graph.js";
import { Filters } from "./filtering.js";
import { Utils } from "./util.js";
import blacklist from './blacklist.json';
import txt from '../data/tracker_report.txt';

//------CUSTOM CONFIG------

// The tag you gave when you started the tracking scope: AccessTracker.startTask(TRACKING_SCOPE_TAG)
var TRACKING_SCOPE_TAG = "CreateTable_ClientStart"

// Is increased everytime AccessTracker.startTask is called. Can be used to select a particular run. 
// If you dont know what to set here, run the vis tool here and look for TAGS in the console output. This shows the possibilities.
var TRACKING_SERIAL = 3

// Use this to iterate through the thread pairs. Each thread pair exists of the writer/producer thread and one consumer thread.
// Look for Reader-IDs in the console output to see the available consumer what limits the max value for the cursor.
var THREAD_PAIR_CURSOR = 0

// Truncates the set of ITCs to the first N. Set to 0 to disable the limit
var ITC_LIMIT = 0

//A regex that filters detection based on the stacktrace of the writer and reader, matched will be excluded
var STACKTRACE_FILTER_REGEX = "(.*edu\.brown\.cs\.systems.*|java\.lang\.ref\.Finalizer.*|boundarydetection.*|java\.util\.concurrent\.locks.*)"

//A regex that filters detection based on field name, matched will be excluded
var LOCATION_FILTER_REGEX = "(.*\.bag)|(.*trackerTaskX)"

// Everything before the cutoff entry here will be deleted from the stacktraces. E.g. [Thread.run, ...., cutoffEntry ,...] -> [cutoffEntry,...]
var STACKTRACE_CUTOFF_UNTIL = "org.apache.hadoop.hbase.client.HBaseAdmin.createTable(HBaseAdmin.java:630)"

//---VISUALIZATION 

// several nodes representing the same memory address/fields/array positions can be merged or left unique.
// If merged different accesses to the same field will be represented by one node, otherwise each ITC gets its one location node. 
var LOCATION_MERGING = 'MERGED'         //'UNIQUE'/'MERGED'

// A stacktrace is represented by a node per entry. Different stacktraces can be merged.
// "FULL" means that a stacktrace entry does only appear once in the graph.
// "INCREMENTAL" means that nodes of different stacktraces are only merged if the corresponding stacktrace entries appear at the same position. [A,B,C,G,E] u [A,B,C,O,U] merges [A,B,C] but branches for G and O
// "UNIQUE" means each entry is unique and never merged with others.
var STACK_TRACE_MERGING = 'FULL'        //'FULL'/'INCREMENTAL'/'UNIQUE'

// Graph layouting strategy. Always start with fast and use quality if the layouting is bad. QUALITY can lead to errors (fail-stop) in the layouting lib, change to FAST if an error occurs
var LAYOUTING = 'FAST'                  //'FAST/'QUALITY'


//---PRESETS

// ONE-BY-ONE ITC investigation
// LOCATION_MERGING = 'UNIQUE'
// STACK_TRACE_MERGING = 'INCREMENTAL'
// ITC_LIMIT = 30

//------CUSTOM CONFIG END------


try {
    var dets = Utils.parseTxtToJSON(txt)

    var tags = [...new Set(dets.map(d => "TRACEID: " + d.traceID + "; TAG: " + d.writer_task_tag + '; SERIAL:' + d.global_task_serial))]
    console.log("TAGS:\n" + tags.join("\n"))

    dets = dets.filter(d => d.tag == 'CONCURRENT WRITE/READ DETECTION' && d.writer_task_tag == TRACKING_SCOPE_TAG && d.global_task_serial == TRACKING_SERIAL)

    var events = null//drill.fetchEvents(dets[0].writer_taskID)
    console.log("fetched data")

    function filtering(dets) {
        dets = Filters.filterDistinct(dets)
        //dets = Filters.filterBlacklist(dets, blacklist)
        //dets = Filters.filterCovered(dets,false)
        dets = Filters.filterByLocation(dets, LOCATION_FILTER_REGEX)
        dets = Filters.filterByTraces(dets, STACKTRACE_FILTER_REGEX)

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

    if (readerIDs.length == 0) alert("No detections to show")
    else if (THREAD_PAIR_CURSOR >= readerIDs.length) alert("THREAD_PAIR_CURSOR is out of bounds")
    var threadIDselection = readerIDs[THREAD_PAIR_CURSOR]
    dets = dets.filter(a => a.reader_thread_id == threadIDselection)
    dets = dets.sort((a, b) => a.serial - b.serial)

    if (ITC_LIMIT > 0) dets = dets.slice(0, ITC_LIMIT)

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

    var startEntry = STACKTRACE_CUTOFF_UNTIL
    dets.forEach(d => {
        d.writer_stacktrace = Utils.cutAfterLast(d.writer_stacktrace, startEntry)
        d.reader_stacktrace = Utils.cutAfterLast(d.reader_stacktrace, startEntry)
    })

    //"org.apache.hadoop.hbase.client.HTable.put(HTable.java:566)"
    //"org.apache.hadoop.hbase.ipc.RpcExecutor$Handler.run(RpcExecutor.java:324)"
    //"org.apache.hadoop.hbase.client.HBaseAdmin.createTable(HBaseAdmin.java:631)"
    //"org.apache.hadoop.hbase.client.HBaseAdmin.createTable(HBaseAdmin.java:629)")
    //"org.apache.hadoop.hbase.procedure2.ProcedureExecutor$WorkerThread.run(ProcedureExecutor.java:2058)"
    var nodes = Graphs.parseDAG(dets, events, LOCATION_MERGING, STACK_TRACE_MERGING)
    console.log("parsed data")

    render(nodes, LAYOUTING)
    console.log("rendered data")
    console.log("done")
}
catch (e) {
    alert("Errors, see browser console.\n" + e)
}
