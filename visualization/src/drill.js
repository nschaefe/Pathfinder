
export function getData(callback) {
    var url = "http://localhost:8047/query.json";

    var query = "WITH " +
        "t1 AS (SELECT * FROM rep.root.`./tracker_report.json`), " +
      //  "t11 AS (SELECT * FROM t1 WHERE NOT( regexp_matches(regexp_replace(writer_stacktrace,'(\\n)',''), '(.*edu\.brown\.cs\.systems.*|.*org\.apache\.hadoop\.hbase\.zookeeper.*)') or regexp_matches( regexp_replace(reader_stacktrace,'(\\n)',''), '(.*edu\.brown\.cs\.systems.*|.*org\.apache\.hadoop\.hbase\.zookeeper.*)') )), " +
        "t111 AS (SELECT * FROM t1 WHERE epoch=2) SELECT * FROM t111"


    //var query = "select * from dfs.`/home/nico/Dokumente/Entwicklung/Uni/Tracing/instrumentationhelper/testClient/track.json` where type = \u0027CONCURRENT WRITE/READ DETECTION\u0027"
    var message = JSON.stringify({ "queryType": "SQL", "query": query })

    const http = new XMLHttpRequest()
    http.open("POST", url, true);
    console.log(message)
    http.setRequestHeader('Content-type', 'application/json')
    http.send(message)
    http.onload = function () {
        if (http.status != 200) {
            console.log("Error", http.statusText);
        }
        //if (error) throw error;
        // Do whatever with response
        const result = JSON.parse(http.response).rows
        callback(result)

    }
}