import { getColor } from "./colors.js"

export function render(full_graph) {

  // set the dimensions and margins of the graph
  var margin = { top: 10, right: 30, bottom: 30, left: 40 },
    width = screen.width * 2 - margin.left - margin.right,
    height = screen.height * 2 - margin.top - margin.bottom;
  var node_radius = 5
  var textSize = 0.01 * height

  // append the svg object to the body of the page
  var div = d3.select("#viz")
  var svg = div
    .append("svg")
    .attr("width", width + margin.left + margin.right)
    .attr("height", height + margin.top + margin.bottom)
    .attr("transform", "translate(" + margin.left + "," + margin.top + ")");
  // const svgNode = `<svg width=${width} height=${height} viewbox="${-nodeRadius} ${-nodeRadius} ${width + 2 * nodeRadius} ${height + 2 * nodeRadius}"></svg>`


  //tooltip
  var tooltip = d3.select("#tooltip")
    .attr("class", "tooltip")
    .style("font-size", textSize + "px")
    .style("background-color", "#e6e6e6")
    .style("visibility", "hidden")
    .style("position", "absolute")
    .style("z-index", "10")


  svg.append("svg:defs").append("svg:marker")
    .attr("id", "triangle")
    .attr("refX", 0.5)
    .attr("refY", 2.5)
    .attr("markerWidth", 5)
    .attr("markerHeight", 5)
    .attr("orient", "auto")
    .append("path")
    .attr("d", "M 0 0 L 5 2.5 L 0 5 z")
    .style("fill", "#4f4f4f");


  shrinkStraightPaths(full_graph)
  enableParentsOfSinks(full_graph)
  disableReaderTraces(full_graph)

  var builder = d3.dagHierarchy()
  builder = builder.children((d) => {
    return Array.from(d.viewChildren)
  })

  // var layout = d3.sugiyama()
  //   .size([width, height])
  //   .layering(d3.layeringSimplex())
  //   .decross(d3.decrossTwoLayer())
  //   .coord(d3.coordMinCurve())

  // var layout = d3.zherebko()
  // .size([width, height])

  var layout = d3.sugiyama()
    .size([width, height])
    //.nodeSize([node_diameter,node_diameter])
    .layering(d3.layeringLongestPath()) // d3.layeringCoffmanGraham()
    .decross(d3.decrossTwoLayer())
    .coord(d3.coordVert())

  var node = svg
    .selectAll("g")

  var link = svg.append('g')
    .selectAll('path')

  // How to draw edges
  const line = d3.line()
    .curve(d3.curveMonotoneX)
    .x(d => d.x)
    .y(d => d.y);

  var dag;

  update()


  function update(alpha = 1) {

    updateData()
    updateView()

    function updateData() {
      updateLinks(full_graph)
      dag = builder(...getRoots(full_graph))
      layout(dag);
    }

    function updateView() {

      // Plot edges
      link = link.data(dag.links())
        .attr('stroke', (d) => {
          if (d.target.highlight) return "red";
          else return 'black';
        })
      link.exit().remove();
      link = link
        .enter()
        .append('path')
        .attr("class", "link")
        .attr('fill', 'none')
        .attr('stroke-width', 2)
        .attr('stroke', (d) => {
          if (d.target.highlight) return "red";
          else return 'black';
        })
        .merge(link)
        .attr('d', (d) => line(getNode(d).points));

      node = node.data(dag.descendants());
      node.exit().remove();
      node = node.enter()
        .append("g")
        .attr("class", "node")
        .append('circle')
        .attr("r", node_radius)

        .merge(node)
        .on("dblclick", function (d) { expand(getNode(d)) })
        .on("click", function (d) {
          if (d.toggled) {
            d.highlight = false
            d.toggled = false
          }
          else {
            d.highlight = true;
            d.toggled = true
          }
          updateView();
        })
        .on("mouseover", function (d) {
          tooltip
            .style("visibility", "visible")
            .text(getNode(d).name) //.html(d.name + "<br>" +"aa")
            .style("left", (d3.event.pageX) + "px")
            .style("top", (d3.event.pageY - textSize - node_radius * 2) + "px");

          if (!d.toggled) {
            d.highlight = true
            updateView();
          }
        })
        .on("mouseout", function (d) {
          tooltip.style("visibility", "hidden");
          if (!d.toggled) {
            d.highlight = false
            updateView();
          }
        })
        .style("fill", function (d) {
          if (getNode(d).root) return 'red'
          if (getNode(d).sink) return 'black'
          else return Colors.getColor(getNode(d).class)
        })
        .style("stroke", "black")
        .style("stroke-width", (d) => canExpand(getNode(d)) ? 2 : 0)
        .attr('transform', ({ x, y }) => `translate(${x}, ${y})`);
    }
  };

  function getNode(nodeWrapper) {
    return nodeWrapper.data
  }

  function enableParentsOfSinks(graph) {
    graph.forEach(n => {
      if (n.sink) {
        n.parents.forEach(p => p.enabled = true);
      }
    });
  }

  function disableReaderTraces(graph) {
    graph.forEach(n => {
      if (!n.isWriter) n.enabled = false
    });
  }

  function canExpand(node) {
    for (var p of node.parents.values()) {
      if (p.enabled == false) return true;
    }
    return false;
  }

  function getRoots(nodes) {
    //collect root nodes, make sets to arrays
    var roots = []
    nodes.forEach((el) => {
      if (el.root && el.enabled) roots.push(el)
    })
    return roots
  }

  function disableAll(graph) {
    graph.forEach((el) => {
      el.enabled = false
    })
  }

  function expand(node) {
    var changed = false

    if (node.sink) {
      expandForSink(node);
      changed = true
    }
    else {
      node.parents.forEach((d) => {
        if (!d.enabled) {
          changed = true;
          d.enabled = true
        }
      })
    }
    if (changed) update()
  }

  function expandForSink(sink) {
    var enabledNodes = []
    disableAll(full_graph)
    sink.enabled = true
    expandParentsRecursive(sink, enabledNodes)
    shrinkStraightPaths(enabledNodes)
    sink.parents.forEach(p => p.enabled = true);
  }

  function expandParentsRecursive(node, enabledNodes) {
    node.parents.forEach((d) => {
      d.enabled = true
      enabledNodes.push(d)
      expandParentsRecursive(d, enabledNodes)
    })
  }

  function boundX(x) {
    return Math.max(Math.min(x, width), 0 + node_radius * 2);
  }

  function boundY(y) {
    return Math.max(Math.min(y, height), 0 + node_radius * 2);
  }


  ///----------GRAPHVIEW (ENABLE/DISABLE)

  function shrinkStraightPaths(nodes) {
    for (var i = 0; i < nodes.length; i++) {
      var n = nodes[i]
      if (!n.enabled) continue;

      var next = getNextEnabledOnLine(n)
      if (next != null) {
        var next_next = getNextEnabledOnLine(next)
        if (next_next != null) {
          next.enabled = false
          i--;
        }
      }
    }
  }

  function hasSingleChild(n) {
    return n.children.size == 1
  }
  function hasAtMostOneParent(n) {
    return n.parents.size <= 1
  }

  function getSingleEntry(set) {
    return set.values().next().value;
  }

  function getEnabledOnLine(n) {
    while (hasSingleChild(n) && hasAtMostOneParent(n) && n.enabled == false) {
      n = getSingleEntry(n.children);
    }
    if (n.enabled) return n;
    return null;
  }

  function getNextEnabledOnLine(n) {
    if (hasSingleChild(n) && hasAtMostOneParent(n)) n = getSingleEntry(n.children);
    else return null;
    return getEnabledOnLine(n)
  }


  function getEnabledOnLine(n) {
    while (hasSingleChild(n) && hasAtMostOneParent(n) && n.enabled == false) {
      n = getSingleEntry(n.children);
    }
    if (n.enabled) return n;
    return null;
  }

  function updateLinks(graph) {
    graph.forEach(n => {
      if (n.enabled) {
        n.viewChildren = new Set()
        n.children.forEach(child => {
          var enabled_tgt = getEnabledOnLine(child)
          if (enabled_tgt != null) n.viewChildren.add(enabled_tgt)
        });
      }
    });
  }

  function printArray(arr) {
    var ss = "[ "
    for (var i = 0; i < arr.length; i++) {
      var el = arr[i]
      ss += "[\"" + el.source + "\",\"" + el.target + "\"],"
    }
    ss += "]"
    console.log(ss)
  }

}