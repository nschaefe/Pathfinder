import { DrillDriver } from "./drill.js";
import { render } from "./view.js";
import { Graphs } from "./graph.js";

var drill = new DrillDriver()

var dets = drill.fetchDetections()
var events = drill.fetchEvents(dets[0].writer_taskID)

//"org.apache.hadoop.hbase.client.HTable.put(HTable.java:566)"
//"org.apache.hadoop.hbase.ipc.RpcExecutor$Handler.run(RpcExecutor.java:324)"
//"org.apache.hadoop.hbase.client.HBaseAdmin.createTable(HBaseAdmin.java:630)"
//"org.apache.hadoop.hbase.procedure2.ProcedureExecutor$WorkerThread.run(ProcedureExecutor.java:2058)"
var nodes = Graphs.parseDAG(dets, events,"org.apache.hadoop.hbase.client.HTable.put(HTable.java:566)")
render(nodes)


