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

    var dets = drill.fetchDetections(1)
    //SELECT writer_task_id FROM rep.root.`./tracker_report.json`
 
    //assumes only one writer
    //assumes detections are distinct
    function eliminateDuplicateCommunication(dets) {
        var m = new Map()
        for (var d of dets) {
            var set = m.get(d.reader_thread_id)
            if (set == null) set = []
            set.push(d)
            m.set(d.reader_thread_id, set)
        }

        var detGrps = Array.from(m.entries())
        for (var i = 0; i < detGrps.length; i++) {
            var el = detGrps[i]
            if (el == null) continue
            for (var q = i + 1; q < detGrps.length; q++) {
                var el2 = detGrps[q]
                if (el2 == null) continue
                if (equ(el, el2)) detGrps[q] = null
            }
        }

        function equ(a, b) {
            var aS = JSON.stringify(a[1].map(m => "" + m.writer_stacktrace + m.reader_stacktrace + m.location).sort())
            var bS = JSON.stringify(b[1].map(m => "" + m.writer_stacktrace + m.reader_stacktrace + m.location).sort())
            return aS == bS
        }

        var detss = []
        for (var o of detGrps.filter(n => n != null)) {
            detss = detss.concat(o[1])
        }
        return detss
    }
    dets = eliminateDuplicateCommunication(dets)

    // BLACKLISTING
    // dets = dets.filter(d => !d.reader_stacktrace.includes("org.apache.hadoop.hbase.procedure2.ProcedureExecutor$WorkerThread.run(ProcedureExecutor.java:2038)"))

    var readerIDs = Array.from(new Set(dets.map(a => a.reader_thread_id)))
    readerIDs = readerIDs.sort() // to make things reproducible for a report
    console.log(readerIDs)
    var selection = readerIDs[0]

    //it is possible that several distinct execution paths are displayed (several roots), if the same thread is reused
    dets = dets.filter(a => a.reader_thread_id == selection)
    var events = drill.fetchEvents(dets[0].writer_taskID)
    console.log("fetched data")

    //"org.apache.hadoop.hbase.client.HTable.put(HTable.java:566)"
    //"org.apache.hadoop.hbase.ipc.RpcExecutor$Handler.run(RpcExecutor.java:324)"
    //"org.apache.hadoop.hbase.client.HBaseAdmin.createTable(HBaseAdmin.java:630)"
    //"org.apache.hadoop.hbase.procedure2.ProcedureExecutor$WorkerThread.run(ProcedureExecutor.java:2058)"
    var nodes = Graphs.parseDAG(dets, events)
    console.log("parsed data")

    render(nodes)
    console.log("done")
}
catch (e) {
    alert("Errors, see browser console.\n" + e)
}

