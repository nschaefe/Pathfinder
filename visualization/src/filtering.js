import { Utils } from "./util"

export var Filters = new Filter()
function Filter() { }


Filters.filterCovered = filterCovered
function filterCovered(dets, ignoreIfOneChannelCovered) {
    var coveredReader = new Set()
    dets = dets.filter(d => {
        const a = d.reader_joined_trace_ids.includes(d.writer_sub_traceID)
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
    return dets.filter(d => !matchRegex(d.writer_stacktrace) && !matchRegex(d.reader_stacktrace))

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

Filters.filterByReaderMethodGrouping = filterByReaderMethodGrouping
function filterByReaderMethodGrouping(dets, prefxixLengths, index) {
    // Groups detections by prefixes of reader stacktraces and selects one group fitting a prefix based on the index
    // The idea is to discover all channels per thread pair and iterate over all channels with the index argument
    var dx = dets.map(d => {
        var ss = d.reader_stacktrace.slice(d.reader_stacktrace.length - prefxixLengths, d.reader_stacktrace.length)
        return JSON.stringify(ss)
    })
    var prefixes = [...new Set(dx)]
    var selectedPrefix = prefixes[index]
    return dets.filter(d => JSON.stringify(d.reader_stacktrace.slice(d.reader_stacktrace.length - prefxixLengths, d.reader_stacktrace.length)).startsWith(selectedPrefix))
}

//TODO array ref?
Filters.filterSiblings = filterSiblings
function filterSiblings(dets) {
    var rev_dets = dets.slice()
    rev_dets.reverse() //reverse is used to eliminate from end to front in the orginal order

    rev_dets = rev_dets.filter((det, i1) => {
        const sibling = rev_dets.find((d, i2) => {
            var isSibling = i2 > i1 && det.parent == d.parent && JSON.stringify(deleteLastLineNumber(d.writer_stacktrace)) == JSON.stringify(deleteLastLineNumber(det.writer_stacktrace)) &&
                JSON.stringify(deleteLastLineNumber(d.reader_stacktrace)) == JSON.stringify(deleteLastLineNumber(det.reader_stacktrace))
            return isSibling
        })
        const hasSibling = sibling != null
        return !hasSibling
    }
    )
    rev_dets.reverse()
    return rev_dets
}

Filters.filterCoveredGrpInteractive = filterCoveredGrpInteractive
function filterCoveredGrpInteractive(grp, covered) {
    //returns true if a grp was already covered based on the first detection fitting the representative given by the covered argument
    //TODO make adjustable
    var det = grp[0]
    for (var el of covered) {
        if (filterCoveredGrpInteractiveSingle(det, el)) return true
    }
    return false
}

function filterCoveredGrpInteractiveSingle(det, c) {
    var a = JSON.stringify({ loc: det.location, wt: det.writer_stacktrace, rt: det.reader_stacktrace })
    var b = JSON.stringify(c)
    return a == b
}

Filters.filterEditDistance = filterEditDistance
function filterEditDistance(dets, similarityThresh, ignoreLineNumbers) {
    if (similarityThresh == 1) return dets;

    var mod;
    if (ignoreLineNumbers) mod = (d) => deleteLineNumbers(d)
    else mod = (d) => d

    return dets.filter((det, i1) => {
        const sibling = dets.find((d, i2) => {
            var a = i2 > i1 && Utils.levDistObj(mod(d.writer_stacktrace), mod(det.writer_stacktrace)) / d.writer_stacktrace.length < (1 - similarityThresh) &&
                Utils.levDistObj(mod(d.reader_stacktrace), mod(det.reader_stacktrace)) / d.reader_stacktrace.length < (1 - similarityThresh)
            return a

        })
        const hasSibling = sibling != null
        return !hasSibling
    }
    )
}

function deleteLastLineNumber(stacktrace) {
    const trace = stacktrace.slice()
    const colonIndex = trace[0].indexOf(":")
    if (colonIndex == -1) return trace
    trace[0] = trace[0].substring(0, colonIndex)
    return trace
}


function deleteLineNumbers(stacktrace) {
    const trace = stacktrace.slice()
    for (var i = 0; i < trace.length; i++) {
        const colonIndex = trace[i].indexOf(":")
        if (colonIndex == -1) continue;
        trace[i] = trace[i].substring(0, colonIndex)
    }
    return trace
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
        d.writer_stacktrace[0] + " --- " + d.reader_stacktrace[0]))]
}


Filters.filterDistinctMemoryAddressCodePlace = filterDistinctMemoryAddressCodePlace
function filterDistinctMemoryAddressCodePlace(dets) {
    var aliases = [...new Set(dets.map(d => d.location))] //TODO if array, how to count?
    aliases.sort()
    return aliases
}

Filters.filterDistinctResources = filterDistinctMemoryResources
function filterDistinctMemoryResources(dets) {
    var aliases = [...new Set(dets.map(d => Utils.getClassName(d.location)))] //TODO if array, how to count?
    aliases.sort((a, b) => {
        if (a.includes('$') && b.includes('$')) return 0
        if (a.includes('$')) return -1
        else if (b.includes('$')) return 1
        else return 0
    })
    return aliases
}



Filters.filterBlacklist = filterBlacklist
function filterBlacklist(dets, blacklist) {
    var flatBL = blacklist.map(d => JSON.stringify({ loc: d.location, wt: d.writer_stacktrace, rt: d.reader_stacktrace }))
    return dets.filter(d => {
        var flatd = JSON.stringify({ loc: d.location, wt: d.writer_stacktrace, rt: d.reader_stacktrace })
        return !flatBL.includes(flatd)
    })
}


Filters.intersectionClasses = intersectionClasses
function intersectionClasses(channel) {
    var intersects = new Set()
    for (var det of channel) {
        var w = det.writer_stacktrace.slice().reverse().map((d, i) => [Utils.getClassName(d), i])
        var r = det.reader_stacktrace.slice().reverse().map((d, i) => [Utils.getClassName(d), i])

        w = Array.from(w)
        Array.from(r).filter(s => w.find(x => x[0] == s[0])).forEach(intersects.add, intersects)
    }
    //sort according to appearence order in stacktrace, make distinct
    var x = Array.from(intersects).sort((a, b) => a[1] - b[1]).map(d => d[0])
    return x.filter((v, i, self) => self.indexOf(v) === i) //filter preserves order
}


Filters.filterDuplicateCommunication = filterDuplicateCommunication
function filterDuplicateCommunication(dets) {
    //eliminates redundant communication paths.
    //When code is executed several times but by different threads (threadpool) one path is enough to see.
    //first groups detections by reader_thread_id
    //then compares these groups. Identical are removed.
    //just making the set of all detections distinct is not enough because we could remove detections across ITC channels
    //such that none of them is complete but all still exist

    //assumes only one writer
    //assumes detections are distinct

    // group detections by reader_thread_id
    var readerDetSets = new Map()
    for (var d of dets) {
        var threadsDets = readerDetSets.get(d.reader_thread_id)
        if (threadsDets == null) threadsDets = []
        threadsDets.push(d)
        readerDetSets.set(d.reader_thread_id, threadsDets)
    }

    var detGrps = Array.from(readerDetSets.entries())
    // for each group of detections check if there is another equal grp, if yes delete it.
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
        // a, b are tuples of the form (thread_id, detection array)
        var aS = JSON.stringify(a[1].map(d => "" + d.writer_stacktrace + d.reader_stacktrace + d.location).sort())
        var bS = JSON.stringify(b[1].map(d => "" + d.writer_stacktrace + d.reader_stacktrace + d.location).sort())
        return aS == bS
    }

    // concat all groups to have one set again
    var detss = []
    for (var o of detGrps.filter(n => n != null)) {
        detss = detss.concat(o[1])
    }
    return detss
}


Filters.filterSimilarCommunication = filterSimilarCommunication
function filterSimilarCommunication(dets_t1, dets_t2, similarityThresh) {
    if (dets_t1 === dets_t2) all_dets = dets_t1 // otherwise we change the threadID for both arguments
    else {
        var rand = Math.random()
        //TODO deept copy
        dets_t2.forEach(d => d.reader_thread_id = d.reader_thread_id + "_" + rand)
        var all_dets = dets_t1.concat(dets_t2)
    }

    var channels = groupByReaderThreaderId(all_dets)
    var grpsOfChannels = []
    for (var i = 0; i < channels.length; i++) {
        var channel = channels[i]

        //assign channels to channel groups
        var max_similar = 0;
        var max_sim_index;
        for (var q = 0; q < grpsOfChannels.length; q++) {
            var grp = grpsOfChannels[q]
            var sim = similarGrp(channel, grp)
            if (sim > max_similar) {
                if (max_similar >= similarityThresh) console.log("two similar")
                max_similar = sim
                max_sim_index = q
            }
        }
        if (max_similar >= similarityThresh) grpsOfChannels[max_sim_index].push(channel)
        else grpsOfChannels.push([channel])


    }
    console.log("built channel grps")
    //build intersections withing groups
    for (var q = 0; q < grpsOfChannels.length; q++) {
        var grp = grpsOfChannels[q]
        if (grp.length == 1) {
            grpsOfChannels[q] = grp[0][1]
            continue;
        }
        var resultSet = []
        grp = grp.sort((a, b) => a.length - b.length);
        var channel = grp[0]

        for (var det of channel[1]) {
            var foundAll = true
            for (var i = 1; i < grp.length; i++) {
                var found = grp[i][1].find(d => d.location == det.location) != null
                foundAll = foundAll && found
            }
            if (foundAll) resultSet.push(det)
        }
        grpsOfChannels[q] = resultSet
    }
    console.log("intersected grps")

    function similarGrp(channel, grp) {
        // avg. similarity
        var sim_acc = 0
        for (var chan of grp) {
            sim_acc = similar(channel[1], chan[1])
        }
        return sim_acc / grp.length
    }

    function similar(ch1, ch2) {
        return Math.max(similarH(ch1, ch2), similarH(ch2, ch1))
    }

    function similarH(ch1, ch2) {
        // if the sizes are to far away from each other we say not equal
        if (ch1.length / ch2.length < 0.1) return 0
        if (ch2.length / ch1.length < 0.1) return 0
        var hits = 0;
        for (var det of ch1) {
            if (ch2.find(d => d.location == det.location)) hits++
        }
        return hits / ch1.length
    }
    // concat all groups to have one set again
    var detss = []
    for (var o of grpsOfChannels) {
        detss = detss.concat(o)
    }
    console.log("concated channel grps")
    return detss
}


Filters.groupByReaderThreaderId = groupByReaderThreaderId
function groupByReaderThreaderId(dets) {
    var readerDetSets = new Map()
    for (var d of dets) {
        var threadsDets = readerDetSets.get(d.reader_thread_id)
        if (threadsDets == null) threadsDets = []
        threadsDets.push(d)
        readerDetSets.set(d.reader_thread_id, threadsDets)
    }
    return Array.from(readerDetSets.entries())
}

