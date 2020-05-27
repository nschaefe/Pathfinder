export function DrillDriver(baseUrl = "http://localhost:8047") {
    this.baseUrl = baseUrl;
}

DrillDriver.prototype.fetchEvents = function (taskID, file = "./tracker_report.json") {
    var query = "WITH " +
        "t1 AS (SELECT * FROM rep.root.`" + file + "`), " +
        "t11 AS (SELECT * FROM t1 WHERE tag = 'EVENT' ), " +
        "t111 AS (SELECT * FROM t11 WHERE traceID='" + taskID + "') SELECT * FROM t111"

    return sendQuery(query, this.baseUrl)
};

DrillDriver.prototype.fetchDetections = function (serial = 1, file = "./tracker_report.json") {
    var query = "WITH " +
        "t1 AS (SELECT * FROM rep.root.`" + file + "`), " +
        "t11 AS (SELECT * FROM t1 WHERE tag = 'CONCURRENT WRITE/READ DETECTION' ), " +
        "t111 AS (SELECT * FROM t11 WHERE NOT( REPEATED_CONTAINS(writer_stacktrace,'(.*edu\.brown\.cs\.systems.*|.*org\.apache\.hadoop\.hbase\.zookeeper.*)') OR REPEATED_CONTAINS(reader_stacktrace,'(.*edu\.brown\.cs\.systems.*|.*org\.apache\.hadoop\.hbase\.zookeeper.*)')) ), " +
        "t1111 AS (SELECT * FROM t111 WHERE writer_trace_serial=" + serial + ") SELECT * FROM t1111"

    //var query = "select * from dfs.`/home/nico/Dokumente/Entwicklung/Uni/Tracing/instrumentationhelper/testClient/track.json` where type = \u0027CONCURRENT WRITE/READ DETECTION\u0027"
    return sendQuery(query, this.baseUrl)
};

DrillDriver.prototype.loadStoragePlugin = function (pluginName, pluginConfig, baseUrl) {
    var message = JSON.stringify({ "name": pluginName, "config": pluginConfig })
    console.log(message)
    var pluginURL = this.baseUrl + "/storage/myplugin.json"
    const result = sendHTTP(message, pluginURL)
}

//TODO url, make this private memeber meth
function sendQuery(query, baseUrl) {
    var message = JSON.stringify({ "queryType": "SQL", "query": query })

    var queryURL = baseUrl + "/query.json"
    const result = JSON.parse(sendHTTP(message, queryURL)).rows
    return result
};

function sendHTTP(message, url) {
    const http = new XMLHttpRequest()
    http.open("POST", url, false);
    http.setRequestHeader('Content-type', 'application/json')
    http.send(message)

    if (http.status != 200) throw new Error(http.statusText) //TODO errorname
    return http.response
}
