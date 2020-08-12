export var Utils = new Util()
function Util() { }

Utils.getClassName = getClassName
function getClassName(traceEntry) {
    // this is a simple heurisitc for now, since names or what is logged will probably change
    var end = traceEntry.indexOf("(")
    if (end != -1) traceEntry = traceEntry.substring(0, end) // -1 means is a field, does not have brackets
    var names = traceEntry.split('.')

    var first = names[names.length - 1].charAt(0);
    if (first === first.toLowerCase() || end == -1) { // we rely on the convention that classes have big case latter at the start
        names = names.splice(0, names.length - 1)
    }
    return names.join('.')
}

Utils.shortenClassName = shortenClassName
function shortenClassName(traceEntry) {
    var clName = getClassName(traceEntry)
    var rest = traceEntry.substring(clName.length, traceEntry.length)
    var names = clName.split('.')
    return names[names.length - 1] + rest
}

Utils.shortTraceEntry = shortTraceEntry
function shortTraceEntry(traceEntry) {
    var withoutCallPart = traceEntry.substring(0, traceEntry.indexOf("("))
    return shortenClassName(withoutCallPart) + "()"
}

Utils.shortenTrace = shortenTrace
function shortenTrace(trace) {
    return trace.map(d => Utils.shortTraceEntry(d))
}

Utils.cutAfterLast = cutAfterLast
function cutAfterLast(trace, end) {
    var i = trace.lastIndexOf(end)
    if (i < 0) return trace
    return trace.slice(0, i + 1)
}

Utils.download = download
function download(data, filename, type) {
    var file = new Blob([data], { type: type });
    if (window.navigator.msSaveOrOpenBlob) // IE10+
        window.navigator.msSaveOrOpenBlob(file, filename);
    else { // Others
        var a = document.createElement("a"),
            url = URL.createObjectURL(file);
        a.href = url;
        a.download = filename;
        document.body.appendChild(a);
        a.click();
        setTimeout(function () {
            document.body.removeChild(a);
            window.URL.revokeObjectURL(url);
        }, 0);
    }
}

Utils.levDist = levDist
function levDist(s, t) {
    var d = []; //2d matrix

    // Step 1
    var n = s.length;
    var m = t.length;

    if (n == 0) return m;
    if (m == 0) return n;

    //Create an array of arrays in javascript (a descending loop is quicker)
    for (var i = n; i >= 0; i--) d[i] = [];

    // Step 2
    for (var i = n; i >= 0; i--) d[i][0] = i;
    for (var j = m; j >= 0; j--) d[0][j] = j;

    // Step 3
    for (var i = 1; i <= n; i++) {
        var s_i = s.charAt(i - 1);

        // Step 4
        for (var j = 1; j <= m; j++) {

            //Check the jagged ld total so far
            if (i == j && d[i][j] > 4) return n;

            var t_j = t.charAt(j - 1);
            var cost = (s_i == t_j) ? 0 : 1; // Step 5

            //Calculate the minimum
            var mi = d[i - 1][j] + 1;
            var b = d[i][j - 1] + 1;
            var c = d[i - 1][j - 1] + cost;

            if (b < mi) mi = b;
            if (c < mi) mi = c;

            d[i][j] = mi; // Step 6

            //Damerau transposition
            if (i > 1 && j > 1 && s_i == t.charAt(j - 2) && s.charAt(i - 2) == t_j) {
                d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + cost);
            }
        }
    }

    // Step 7
    return d[n][m];

}

Utils.levDistObj = levDistObj
function levDistObj(s, t) {
    var d = []; //2d matrix

    // Step 1
    var n = s.length;
    var m = t.length;

    if (n == 0) return m;
    if (m == 0) return n;

    //Create an array of arrays in javascript (a descending loop is quicker)
    for (var i = n; i >= 0; i--) d[i] = [];

    // Step 2
    for (var i = n; i >= 0; i--) d[i][0] = i;
    for (var j = m; j >= 0; j--) d[0][j] = j;

    // Step 3
    for (var i = 1; i <= n; i++) {
        var s_i = s[i - 1];

        // Step 4
        for (var j = 1; j <= m; j++) {

            //Check the jagged ld total so far
            if (i == j && d[i][j] > 4) return n;

            var t_j = t[j - 1];
            var cost = (s_i == t_j) ? 0 : 1; // Step 5

            //Calculate the minimum
            var mi = d[i - 1][j] + 1;
            var b = d[i][j - 1] + 1;
            var c = d[i - 1][j - 1] + cost;

            if (b < mi) mi = b;
            if (c < mi) mi = c;

            d[i][j] = mi; // Step 6

            //Damerau transposition
            if (i > 1 && j > 1 && s_i == t[j - 2] && s[i - 2] == t_j) {
                d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + cost);
            }
        }
    }

    // Step 7
    return d[n][m];

}