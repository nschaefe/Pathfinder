/******/ (function(modules) { // webpackBootstrap
/******/ 	// The module cache
/******/ 	var installedModules = {};
/******/
/******/ 	// The require function
/******/ 	function __webpack_require__(moduleId) {
/******/
/******/ 		// Check if module is in cache
/******/ 		if(installedModules[moduleId]) {
/******/ 			return installedModules[moduleId].exports;
/******/ 		}
/******/ 		// Create a new module (and put it into the cache)
/******/ 		var module = installedModules[moduleId] = {
/******/ 			i: moduleId,
/******/ 			l: false,
/******/ 			exports: {}
/******/ 		};
/******/
/******/ 		// Execute the module function
/******/ 		modules[moduleId].call(module.exports, module, module.exports, __webpack_require__);
/******/
/******/ 		// Flag the module as loaded
/******/ 		module.l = true;
/******/
/******/ 		// Return the exports of the module
/******/ 		return module.exports;
/******/ 	}
/******/
/******/
/******/ 	// expose the modules object (__webpack_modules__)
/******/ 	__webpack_require__.m = modules;
/******/
/******/ 	// expose the module cache
/******/ 	__webpack_require__.c = installedModules;
/******/
/******/ 	// define getter function for harmony exports
/******/ 	__webpack_require__.d = function(exports, name, getter) {
/******/ 		if(!__webpack_require__.o(exports, name)) {
/******/ 			Object.defineProperty(exports, name, { enumerable: true, get: getter });
/******/ 		}
/******/ 	};
/******/
/******/ 	// define __esModule on exports
/******/ 	__webpack_require__.r = function(exports) {
/******/ 		if(typeof Symbol !== 'undefined' && Symbol.toStringTag) {
/******/ 			Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
/******/ 		}
/******/ 		Object.defineProperty(exports, '__esModule', { value: true });
/******/ 	};
/******/
/******/ 	// create a fake namespace object
/******/ 	// mode & 1: value is a module id, require it
/******/ 	// mode & 2: merge all properties of value into the ns
/******/ 	// mode & 4: return value when already ns object
/******/ 	// mode & 8|1: behave like require
/******/ 	__webpack_require__.t = function(value, mode) {
/******/ 		if(mode & 1) value = __webpack_require__(value);
/******/ 		if(mode & 8) return value;
/******/ 		if((mode & 4) && typeof value === 'object' && value && value.__esModule) return value;
/******/ 		var ns = Object.create(null);
/******/ 		__webpack_require__.r(ns);
/******/ 		Object.defineProperty(ns, 'default', { enumerable: true, value: value });
/******/ 		if(mode & 2 && typeof value != 'string') for(var key in value) __webpack_require__.d(ns, key, function(key) { return value[key]; }.bind(null, key));
/******/ 		return ns;
/******/ 	};
/******/
/******/ 	// getDefaultExport function for compatibility with non-harmony modules
/******/ 	__webpack_require__.n = function(module) {
/******/ 		var getter = module && module.__esModule ?
/******/ 			function getDefault() { return module['default']; } :
/******/ 			function getModuleExports() { return module; };
/******/ 		__webpack_require__.d(getter, 'a', getter);
/******/ 		return getter;
/******/ 	};
/******/
/******/ 	// Object.prototype.hasOwnProperty.call
/******/ 	__webpack_require__.o = function(object, property) { return Object.prototype.hasOwnProperty.call(object, property); };
/******/
/******/ 	// __webpack_public_path__
/******/ 	__webpack_require__.p = "";
/******/
/******/
/******/ 	// Load entry module and return exports
/******/ 	return __webpack_require__(__webpack_require__.s = "./src/index.js");
/******/ })
/************************************************************************/
/******/ ({

/***/ "./src/drill.js":
/*!**********************!*\
  !*** ./src/drill.js ***!
  \**********************/
/*! exports provided: getData */
/***/ (function(module, __webpack_exports__, __webpack_require__) {

"use strict";
eval("__webpack_require__.r(__webpack_exports__);\n/* harmony export (binding) */ __webpack_require__.d(__webpack_exports__, \"getData\", function() { return getData; });\n\nfunction getData(callback) {\n    var url = \"http://localhost:8047/query.json\";\n\n    var query = \"select * from dfs.`/home/nico/Dokumente/Entwicklung/Uni/Tracing/instrumentationhelper/testClient/track.json` where type = \\u0027CONCURRENT WRITE/READ DETECTION\\u0027\"\n    var message = JSON.stringify({ \"queryType\": \"SQL\", \"query\": query })\n\n    const http = new XMLHttpRequest()\n    http.open(\"POST\", url, true);\n    console.log(message)\n    http.setRequestHeader('Content-type', 'application/json')\n    http.send(message)\n    http.onload = function () {\n        // Do whatever with response\n        const result = JSON.parse(http.response).rows\n        callback(result)\n\n    }\n}\n\n//# sourceURL=webpack:///./src/drill.js?");

/***/ }),

/***/ "./src/graph.js":
/*!**********************!*\
  !*** ./src/graph.js ***!
  \**********************/
/*! exports provided: render */
/***/ (function(module, __webpack_exports__, __webpack_require__) {

"use strict";
eval("__webpack_require__.r(__webpack_exports__);\n/* harmony export (binding) */ __webpack_require__.d(__webpack_exports__, \"render\", function() { return render; });\n\nfunction render(data) {\n\n  // set the dimensions and margins of the graph\n  var margin = { top: 10, right: 30, bottom: 30, left: 40 },\n    width = 1000 - margin.left - margin.right,\n    height = 1000 - margin.top - margin.bottom;\n  var node_radius = 20\n\n  // append the svg object to the body of the page\n  var svg = d3.select(\"#my_dataviz\")\n    .append(\"svg\")\n    .attr(\"width\", width + margin.left + margin.right)\n    .attr(\"height\", height + margin.top + margin.bottom)\n    .attr(\"transform\",\n      \"translate(\" + margin.left + \",\" + margin.top + \")\");\n\n  //TODO\n  svg.append(\"svg:defs\").append(\"svg:marker\")\n  .attr(\"id\", \"triangle\")\n  .attr(\"refX\", 15)\n  .attr(\"refY\", -1.5)\n  .attr(\"markerWidth\", 6)\n  .attr(\"markerHeight\", 6)\n  .attr(\"orient\", \"auto\")\n  .append(\"path\")\n  .attr(\"d\", \"M 0 -5 10 10\")\n  .style(\"stroke\", \"black\");\n\n  // Initialize the links\n  var link = svg\n    .selectAll(\"line\")\n    .data(data.links)\n    .enter()\n    .append(\"line\")\n    .style(\"stroke\", \"#aaa\")\n    .attr(\"marker-end\", \"url(#triangle)\");\n\n  // Initialize the nodes\n  var node = svg\n    .selectAll(\"g\")\n    .data(data.nodes)\n    .enter()\n    .append(\"g\")\n\n  node.append('circle')\n    .attr(\"r\", node_radius)\n    .style(\"fill\", function (d) {\n      if (d.root) return 'red'\n      if (d.sink) return '#4265ff'\n      else return \"#69b3a2\"\n    })\n\n  // Add text to nodes\n  node.append('text')\n    .text(d => d.name)\n    .attr('font-weight', 'bold')\n    .attr('font-family', 'sans-serif')\n    .attr('text-anchor', 'middle')\n    .attr('alignment-baseline', 'middle')\n    .attr('fill', 'black');\n\n  // Let's list the force we wanna apply on the network\n  var simulation = d3.forceSimulation(data.nodes)                 // Force algorithm is applied to data.nodes\n    .force(\"link\", d3.forceLink()                               // This force provides links between nodes\n      .id(function (d) { return d.id; })                     // This provide  the id of a node\n      .links(data.links)                                    // and this the list of links\n    )\n    .force(\"charge\", d3.forceManyBody().strength(-400).distanceMin(node_radius * 2))\n    //.distanceMin(5)\n    //.distanceMax(forceProperties.charge.distanceMax)   \n    // This adds repulsion between nodes. Play with the -400 for the repulsion strength\n    .force(\"center\", d3.forceCenter(width / 2, height / 2))     // This force attracts nodes to the center of the svg area\n    .force(\"collide\", d3.forceCollide().radius(node_radius * 2))\n    .on(\"end\", ticked);\n\n  // This function is run at each iteration of the force algorithm, updating the nodes position.\n  function ticked() {\n    link\n      .attr(\"x1\", function (d) { return d.source.x; })\n      .attr(\"y1\", function (d) { return d.source.y; })\n      .attr(\"x2\", function (d) { return d.target.x; })\n      .attr(\"y2\", function (d) { return d.target.y; });\n\n    node\n      // .attr(\"cx\", function (d) { return d.x + 6; })\n      // .attr(\"cy\", function (d) { return d.y - 6; });\n      .attr('transform', (d) => `translate(${d.x}, ${d.y})`);\n\n  }\n\n\n}\n\n//# sourceURL=webpack:///./src/graph.js?");

/***/ }),

/***/ "./src/index.js":
/*!**********************!*\
  !*** ./src/index.js ***!
  \**********************/
/*! no exports provided */
/***/ (function(module, __webpack_exports__, __webpack_require__) {

"use strict";
eval("__webpack_require__.r(__webpack_exports__);\n/* harmony import */ var _drill_js__WEBPACK_IMPORTED_MODULE_0__ = __webpack_require__(/*! ./drill.js */ \"./src/drill.js\");\n/* harmony import */ var _graph_js__WEBPACK_IMPORTED_MODULE_1__ = __webpack_require__(/*! ./graph.js */ \"./src/graph.js\");\n\n\n\nObject(_drill_js__WEBPACK_IMPORTED_MODULE_0__[\"getData\"])(f)\nfunction f(data) {\n    var node_map = new Map();\n    var link_set = new Set();\n\n    var id = new Object()\n    id.val = 0\n    for (var i = 0; i < data.length; i++) {\n        var detect = data[i]\n        var w_trace = JSON.parse(detect.writer_stacktrace)\n        w_trace = w_trace.reverse()\n        w_trace.push(detect.location)\n        var sink = parseTrace(w_trace, node_map, link_set, id)\n        sink.sink = true\n\n        var r_trace = JSON.parse(detect.reader_stacktrace)\n        r_trace = r_trace.reverse()\n        r_trace.push(detect.location)\n        sink = parseTrace(r_trace, node_map, link_set, id)\n\n    }\n    var nodes = Array.from(node_map.values());\n    var links = Array.from(link_set);\n\n    // console.log(JSON.stringify(nodes))\n    // console.log(JSON.stringify(links))\n\n    var data = new Object();\n    data.nodes = nodes\n    data.links = links\n    Object(_graph_js__WEBPACK_IMPORTED_MODULE_1__[\"render\"])(data)\n}\n\nfunction getName(name) {\n    var a = name.indexOf(\":\")\n    var b = name.indexOf(\")\")\n    var lineNumber = name.substring(a, b)\n\n    var end = name.indexOf(\"(\")\n    name = name.substring(0, end)\n\n    var names = name.split('.')\n    names = names.slice(names.length - 2, names.length);\n    name = names.join('.')\n\n    name += lineNumber\n    return name\n\n}\n\nfunction parseTrace(trace, node_map, link_set, id) {\n    var source = null\n    for (var i = 0; i < trace.length; i++) {\n\n        var entry\n        if (i != trace.length - 1) // last is detection, not part of stacktrace\n            entry = getName(trace[i])\n        else\n            entry = trace[i]\n\n        // get node if existant\n        var target = node_map.get(entry)\n        if (target == null) {\n            target = getNode(entry, id.val++)//this.id++ TODO\n            if (source == null)// root\n                target.root = true\n            node_map.set(entry, target)\n        }\n\n        //link source to target\n        if (source != null) {\n            link_set.add(getLink(source, target))\n        }\n        source = target\n        target = null\n    }\n    return source\n\n}\n\nfunction getNode(name, id) {\n    var node = new Object();\n    node.id = id\n    node.name = name;\n    return node\n}\n\nfunction getLink(source, target) {\n    var link = new Object();\n    link.source = source.id;\n    link.target = target.id;\n    return link\n}\n\n//# sourceURL=webpack:///./src/index.js?");

/***/ })

/******/ });