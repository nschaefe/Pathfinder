import { getColor } from "./colors.js"

export function render(full_graph) {
  shrinkStraightPaths(full_graph)

  var graphView = getGraphView(full_graph)
  // set the dimensions and margins of the graph
  var margin = { top: 10, right: 30, bottom: 30, left: 40 },
    width = screen.width - margin.left - margin.right,
    height = screen.height - margin.top - margin.bottom;
  var node_radius = 5
  var node_diameter = 2 * node_radius

  // append the svg object to the body of the page
  var div = d3.select("#viz")
  var svg = div
    .append("svg")
    .attr("width", width + margin.left + margin.right)
    .attr("height", height + margin.top + margin.bottom)
    .attr("transform", "translate(" + margin.left + "," + margin.top + ")");

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

  // Initialize the links
  var link = svg
    .selectAll("line")

  // Initialize the nodes
  var node = svg
    .selectAll("g")

  // Let's list the force we wanna apply on the network
  var simulation = d3.forceSimulation(graphView.nodes)                 // Force algorithm is applied to graphView.nodes
    .force("link", d3.forceLink()                               // This force provides links between nodes
      .id(function (d) { return d.id; })                     // This provide  the id of a node
      .links(graphView.links)
      .distance(node_radius * 3).strength(2)
      .iterations(1)
    )
    .force("charge", d3.forceManyBody().strength(-50))
    // This adds repulsion between nodes. Play with the -400 for the repulsion strength
    .force("center", d3.forceCenter(width / 2, height / 2))     // This force attracts nodes to the center of the svg area
    .force("collide", d3.forceCollide().radius(node_radius * 1.5))
    .on("tick", ticked);

  update()


  function update(alpha = 1) {

    link = link.data(graphView.links)
    link.exit().remove();
    link = link
      .enter().append("line")
      .attr("class", "link")
      .style("stroke", "#4f4f4f")
      .attr("marker-end", "url(#triangle)")
      .merge(link);

    node = node.data(graphView.nodes);
    node.exit().remove();
    node = node.enter()
      .append("g")
      .attr("class", "node")
      // draging
      .call(d3.drag()
        .on("start", dragStart)
        .on("drag", draging)
        .on("end", dragEnd))
      // tooltip
      .on("mouseover", function (d) {
        tooltip
          .style("visibility", "visible")
          .html(d.name) //.html(d.name + "<br>" +"aa")
          .style("left", (d3.event.pageX) + "px")
          .style("top", (d3.event.pageY - 28) + "px");
      })
      .on("mouseout", function (d) {
        tooltip.style("visibility", "hidden");
      })
      // node expanding
      .on("dblclick", function (d) { expand(d) })

      .append('circle')
      .attr("r", node_radius)
      .style("fill", function (d) {
        if (d.root) return 'red'
        if (d.sink) return 'black'
        else return Colors.getColor(d.name)
      })
      .merge(node)

      simulation.nodes(graphView.nodes);
      simulation.force("link").links(graphView.links)
      simulation.alpha(alpha).restart(); 
  };

  // This function is run at each iteration of the force algorithm, updating the nodes position.
  function ticked() {

    // prevent exceeding borders
    graphView.nodes.forEach(el => {
      el.x = boundX(el.x)
      el.y = boundY(el.y)
    });

    node.attr('transform', (d) => `translate(${d.x}, ${d.y})`);

    link
      .attr("x1", function (d) { return d.source.x; })
      .attr("y1", function (d) { return d.source.y; })
      .attr("x2", function (d) {
        return truncateLineToNodeEdge(d.source.x, d.source.y, d.target.x, d.target.y).x
      })
      .attr("y2", function (d) {
        return truncateLineToNodeEdge(d.source.x, d.source.y, d.target.x, d.target.y).y
      });
  }

  function truncateLineToNodeEdge(x1, y1, x2, y2) {
    var xVec = x2 - x1
    var yVec = y2 - y1

    var norm = Math.sqrt(xVec * xVec + yVec * yVec)

    xVec *= 1 - ((node_diameter) / norm)
    yVec *= 1 - ((node_diameter) / norm)

    return { x: x1 + xVec, y: y1 + yVec }
  }

  function expand(node) {
    node.parents.forEach((d) => d.enabled = true)
    graphView = getGraphView(full_graph)
    update(0.1)
  }

  function boundX(x) {
    return Math.max(Math.min(x, width), 0 + node_radius * 2);
  }

  function boundY(y) {
    return Math.max(Math.min(y, height), 0 + node_radius * 2);
  }

  function dragStart(d) {
    if (!d3.event.active) simulation.alphaTarget(0.3).restart();
    d.fx = d.x;
    d.fy = d.y;
  }

  function draging(d) {
    d.fx = d3.event.x;
    d.fy = d3.event.y;
  }

  function dragEnd(d) {
    if (!d3.event.active) simulation.alphaTarget(0);
    d.fx = null;
    d.fy = null;
  }

  ///----------GRAPHVIEW (ENABLE/DISABLE)

  function getGraphView(nodes) {
    var enabled_nodes = nodes.filter(d => { return d.enabled })
    return {
      nodes: enabled_nodes,
      links: getLinksView(enabled_nodes)
    }
  }

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

  function getLinksView(n) {
    var linkSet = new Set();
    n.forEach(n => {
      if (n.enabled) {
        n.children.forEach(child => {
          var enabled_tgt = getEnabledOnLine(child)
          if (enabled_tgt != null) linkSet.add(getLink(n, enabled_tgt))
        });
      }
    });
    return Array.from(linkSet)
  }

  function getLink(source, target) {
    var link = new Object();
    link.source = source.id;
    link.target = target.id;
    return link
  }


}