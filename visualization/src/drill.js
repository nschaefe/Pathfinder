export function DrillDriver(url = "http://localhost:8047/query.json") {
    this.url = url;
}

DrillDriver.prototype.fetchEvents = function (callback, file = "./tracker_report.json") {
    var query = "WITH " +
        "t1 AS (SELECT * FROM rep.root.`" + file + "`), " +
        "t11 AS (SELECT * FROM t1 WHERE tag = 'EVENT' ) SELECT * FROM t11"

    return query_drill(query, this.url)
};

DrillDriver.prototype.fetchDetections = function (callback, file = "./tracker_report.json", epoch = 2) {
    var query = "WITH " +
        "t1 AS (SELECT * FROM rep.root.`" + file + "`), " +
        "t11 AS (SELECT * FROM t1 WHERE NOT( regexp_matches(regexp_replace(writer_stacktrace,'(\\n)',''), '(.*edu\.brown\.cs\.systems.*|.*org\.apache\.hadoop\.hbase\.zookeeper.*)') or regexp_matches( regexp_replace(reader_stacktrace,'(\\n)',''), '(.*edu\.brown\.cs\.systems.*|.*org\.apache\.hadoop\.hbase\.zookeeper.*)') )), " +
        "t111 AS (SELECT * FROM t1 WHERE epoch=" + epoch + ") SELECT * FROM t111"

    //var query = "select * from dfs.`/home/nico/Dokumente/Entwicklung/Uni/Tracing/instrumentationhelper/testClient/track.json` where type = \u0027CONCURRENT WRITE/READ DETECTION\u0027"
    return query_drill(query, this.url)
};

//TODO url, make this private memeber meth
function query_drill(query, url) {
    var message = JSON.stringify({ "queryType": "SQL", "query": query })

    const http = new XMLHttpRequest()
    http.open("POST", url, false);
    console.log(message)
    // http.timeout = 5000;
    http.setRequestHeader('Content-type', 'application/json')
    http.send(message)
    // http.onload = function () {
    //     if (http.status != 200) {
    //         console.log("Error", http.statusText);
    //     }
    //     const result = JSON.parse(http.response).rows
    //     callback(result)
    // }
    if (http.status != 200) {
        console.log("Error", http.statusText);
    }
    const result = JSON.parse(http.response).rows
    return result
};