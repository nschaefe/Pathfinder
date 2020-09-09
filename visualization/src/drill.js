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

DrillDriver.prototype.fetchDetections = function (serial = 1, taskTag = null, file = "./tracker_report.json") {
    var query = "WITH " +
        "t1 AS (SELECT * FROM rep.root.`" + file + "`), " +
        "t11 AS (SELECT * FROM t1 WHERE tag = 'CONCURRENT WRITE/READ DETECTION' ), " +
        "t111 AS (SELECT * FROM t11 WHERE global_task_serial=" + serial
    if (taskTag != null) {
        query += " and writer_task_tag='" + taskTag + "'"
    }
    query += ") SELECT * FROM t111"

    //var query = "select * from dfs.`/home/nico/Dokumente/Entwicklung/Uni/Tracing/instrumentationhelper/testClient/track.json` where type = \u0027CONCURRENT WRITE/READ DETECTION\u0027"
    return sendQuery(query, this.baseUrl)
};

DrillDriver.prototype.locationHistogram = function (file = "./tracker_report.json") {
    var query = "SELECT location, COUNT(*) as count FROM rep.root.`" + file + "` GROUP BY location ORDER BY count DESC"
    return sendQuery(query, this.baseUrl)
};

DrillDriver.prototype.fetchTaskTags = function (file = "./tracker_report.json") {
    var query = "SELECT DISTINCT writer_task_tag, global_task_serial FROM rep.root.`" + file + "`"
    return sendQuery(query, this.baseUrl)
};

DrillDriver.prototype.fetchTraceSerials = function (file = "./tracker_report.json") {
    var query = "SELECT DISTINCT global_task_serial FROM rep.root.`" + file + "`"
    return sendQuery(query, this.baseUrl)
};

// idea to write tables with drill, not working
// DrillDriver.prototype.storeInTable = function (tableName) {
//     var file = tableName
//     var t = "rep.root.`" + file + "`"
//     var oldT = "(SELECT * FROM " + t + ")"
//     var newT = "(SELECT 'test' as testCol)"
//     var union = "(" + oldT + " union " + newT + ")"
//     var create = "CREATE TABLE " + t + " AS " + union
//     var query = "ALTER SESSION SET `store.format`='json' "

//     console.log(query)
//     sendQuery(query, this.baseUrl)
//     sendQuery(create, this.baseUrl)
// }


DrillDriver.prototype.loadStoragePlugin = function (pluginName, pluginConfig) {
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
