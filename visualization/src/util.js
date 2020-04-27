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
    var rest = traceEntry.substring(clName.length - 1, traceEntry.length)
    var names = clName.split('.')
    return names[names.length - 1] + rest
}
