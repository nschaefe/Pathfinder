export var Filters = new Filter()
function Filter() { }


Filters.filterCovered = filterCovered
function filterCovered(dets, ignoreIfOneChannelCovered) {
    var coveredReader = new Set()
    dets = dets.filter(d => {
        const a = JSON.parse(d.reader_joined_trace_ids).includes(d.writer_sub_traceID)
        if (a) coveredReader.add(d.reader_thread_id);
        return !a
    })

    if (ignoreIfOneChannelCovered) {
        dets = dets.filter(d => !coveredReader.has(d.reader_thread_id))
    }
    return dets
}

Filters.filterByTraces = filterByTraces
function filterByTraces(dets, regexString) {
    var regex = new RegExp(regexString)
    return dets.filter(d => !matchRegex(JSON.parse(d.writer_stacktrace)) && !matchRegex(JSON.parse(d.reader_stacktrace)))

    function matchRegex(trace) {
        for (var entry of trace) {
            if (entry.match(regex)) return true
        }
        return false
    }
}

Filters.filterByLocation = filterByLocation
function filterByLocation(dets, regexString) {
    var regex = new RegExp(regexString)
    return dets.filter(d => !d.location.match(regex))
}

Filters.filterSiblings = filterSiblings
function filterSiblings(dets) {
    return dets.filter((det, i1) => {
        const sibling = dets.find((d, i2) => {
            var a = i2 > i1 && det.parent == d.parent && JSON.stringify(deleteLineNumber(d.writer_stacktrace)) == JSON.stringify(deleteLineNumber(det.writer_stacktrace)) &&
                JSON.stringify(deleteLineNumber(d.reader_stacktrace)) == JSON.stringify(deleteLineNumber(det.reader_stacktrace))
            return a
        })
        const hasSibling = sibling != null
        return !hasSibling
    }
    )

}

function deleteLineNumber(stacktrace) {
    const copy = JSON.parse(stacktrace).slice()
    const a = copy[0].indexOf(":")
    if (a == -1) return copy
    copy[0] = copy[0].substring(0, a)
    return copy
}

Filters.intersectWithTraces = intersectWithTraces
function intersectWithTraces(dets, traces) {
    var intersec = [...new Set(dets.map(d => d.location))]
    for (var trace of traces) {
        var loc = [...new Set(trace.map(d => d.location))]
        intersec = intersec.filter(s => loc.indexOf(s) != -1)
    }
    return dets.filter(d => intersec.includes(d.location))
}


Filters.filterDistinct = filterDistinct
function filterDistinct(dets) {
    return [...new Set(dets.map(d => JSON.stringify(d)))].map(d => JSON.parse(d))
}


Filters.filterDistinctPath = filterDistinctPath
function filterDistinctPath(dets) {
    return [...new Set(dets.map(d => d.writer_stacktrace + " --- " + d.reader_stacktrace))]
}


Filters.filterDistinctCodePlace = filterDistinctCodePlace
function filterDistinctCodePlace(dets) {
    return [...new Set(dets.map(d => //d.location + "----" +
        JSON.parse(d.writer_stacktrace)[0] + " --- " + JSON.parse(d.reader_stacktrace)[0]))]
}


Filters.filterDistinctMemoryAddressCodePlace = filterDistinctMemoryAddressCodePlace
function filterDistinctMemoryAddressCodePlace(dets) {
    return [...new Set(dets.map(d => d.location))] //TODO if array, how to count?
}



Filters.filterBlacklist = filterBlacklist
function filterBlacklist(dets, blacklist) {
    var flatBL = blacklist.map(d => JSON.stringify({ loc: d.location, wt: d.writer_stacktrace, rt: d.reader_stacktrace }))
    return dets.filter(d => {
        var flatd = JSON.stringify({ loc: d.location, wt: d.writer_stacktrace, rt: d.reader_stacktrace })
        return !flatBL.includes(flatd)
    })
}

Filters.filterDuplicateCommunication = filterDuplicateCommunication
function filterDuplicateCommunication(dets) {
    //eliminates redundant communication paths.
    //When code is executed several times but by different threads (threadpool) one path is enough to see.
    //first groups detections by reader_thread_id
    //then compares these groups. Identical are removed.
    //We need to group first to not destroy consistency between groups.
    //We want to keep the communication between 2 threads complete and not delete some detections in different groups
    //where we would have a disitinct set of detections but all detection sets per thread pair might be incomplete

    //assumes only one writer
    //assumes detections are distinct

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



