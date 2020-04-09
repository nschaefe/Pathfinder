import { getColor } from "./colors.js"

export function render(full_graph) {

  // set the dimensions and margins of the graph
  var margin = { top: 10, right: 30, bottom: 30, left: 40 },
    width = screen.width - margin.left - margin.right,
    height = screen.height * 2 - margin.top - margin.bottom;
  var node_radius = 5
  var node_diameter = 2 * node_radius

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

  update()

  function update(alpha = 1) {

    updateLinks(full_graph)
    var dag = builder(...getRoots(full_graph))
    layout(dag);

    // Plot edges
    link = link.data(dag.links())
    link.exit().remove();
    link = link
      .enter()
      .append('path')
      .attr('fill', 'none')
      .attr('stroke-width', 1)
      .attr('stroke', 'black')
      .merge(link)
      .attr('d', (d) => line(d.data.points));

    node = node.data(dag.descendants());
    node.exit().remove();
    node = node.enter()
      .append("g")
      .attr("class", "node")
      .append('circle')
      .attr("r", node_radius)

      .merge(node)
      .on("mouseout", function (d) {
        tooltip.style("visibility", "hidden");
      })
      .on("dblclick", function (d) { expand(d) })
      .on("mouseover", function (d) {
        tooltip
          .style("visibility", "visible")
          .text(d.data.name) //.html(d.name + "<br>" +"aa")
          .style("left", (d3.event.pageX) + "px")
          .style("top", (d3.event.pageY - 28) + "px");
      })
      .style("fill", function (d) {
        if (d.data.root) return 'red'
        if (d.data.sink) return 'black'
        else return Colors.getColor(d.data.class)
      })
      .style("stroke", "black")
      .style("stroke-width", (d) => canExpand(d) ? 2 : 0)
      .attr('transform', ({ x, y }) => `translate(${x}, ${y})`);

  };

  function enableParentsOfSinks(graph) {
    graph.forEach(n => {
      if (n.sink) {
        n.parents.forEach(p => p.enabled = true);
      }
    });
  }

  function canExpand(node) {
    for (var p of node.data.parents.values()) {
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

  function expand(node) {
    var changed = false
    node.data.parents.forEach((d) => {
      if (!d.enabled) {
        changed = true;
        d.enabled = true
      }
    })
    if (changed) update()
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