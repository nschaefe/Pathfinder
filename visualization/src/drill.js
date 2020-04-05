
export function getData(callback) {
    var url = "http://localhost:8047/query.json";

    var query = "select * from dfs.`/home/nico/Dokumente/Entwicklung/Uni/Tracing/instrumentationhelper/testClient/track.json` where type = \u0027CONCURRENT WRITE/READ DETECTION\u0027"
    var message = JSON.stringify({ "queryType": "SQL", "query": query })

    const http = new XMLHttpRequest()
    http.open("POST", url, true);
    console.log(message)
    http.setRequestHeader('Content-type', 'application/json')
    http.send(message)
    http.onload = function () {
        // Do whatever with response
        const result = JSON.parse(http.response).rows
        callback(result)

    }
}