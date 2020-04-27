import { getColor } from "./colors.js"
import { Graphs } from "./graph.js";
import { Utils } from "./util.js";

export function render(full_graph) {

  var width = screen.width * 1.1
  var height = screen.height * 1.1
  var nodeRadius = 5 // TODO polygons are not dynamic based on radius
  var tooltipTextSize = 0.02 * height
  var nodeTextSize = 10 * 1.5

  // append the svg object to the body of the page
  var div = d3.select("#viz")
  var svg = div
    .append("svg")
    .attr("width", width)
    .attr("height", height)
    .attr("viewBox", `${-nodeRadius} ${-nodeRadius} ${width + 2 * nodeRadius} ${height + 2 * nodeRadius}`);

  //tooltip
  var tooltip = d3.select("#tooltip")
    .attr("class", "tooltip")
    .style("font-size", tooltipTextSize + "px")
    .style("background-color", "#e6e6e6")
    .style("visibility", "hidden")
    .style("position", "absolute")
    .style("z-index", "10")


  svg.append("svg:defs").append("svg:marker")
    .attr("id", "triangle")
    .attr("refX", 0)
    .attr("refY", 2.5)
    .attr("markerWidth", 5)
    .attr("markerHeight", 5)
    .attr("orient", "auto")
    .append("path")
    .attr("d", "M 0 0 L 5 2.5 L 0 5 z")
    .style("fill", "#4f4f4f");


  //Graphs.shrinkStraightPaths(full_graph)
  Graphs.enableParentsOfSinks(full_graph)
  Graphs.disableReaderTraces(full_graph)

  var builder = d3.dagHierarchy()
  builder = builder.children((d) => {
    return Array.from(d.viewChildren)
  })

  // for starting tree
  var layout = d3.sugiyama()
    .size([width, height])
    .layering(d3.layeringLongestPath())
    .decross(d3.decrossTwoLayer())
    .coord(d3.coordMinCurve())

  // for reader view
  // var layout = d3.sugiyama()
  //   .size([width, height])
  //   .layering(d3.layeringSimplex())
  //   .decross(d3.decrossTwoLayer())//d3.decrossTwoLayer().order(d3.twolayerOpt()
  //   .coord(d3.coordCenter())

  // var layout = d3.zherebko()
  //   .size([width, height])

  var link = svg.append('g')
    .attr('class', 'links')
    .selectAll('path')

  var node = svg.append('g')
    .attr('class', 'nodes')
    .selectAll("g")

  // How to draw edges
  const line = d3.line()
    .curve(d3.curveMonotoneX)
    .x(d => d.x)
    .y(d => d.y);

  var dag;
  var linkData = []

  update()

  function update(alpha = 1) {

    updateData()
    updateView()
  }

  function updateData() {
    Graphs.updateLinks(full_graph)
    dag = builder(...Graphs.getEnabledRoots(full_graph))
    console.log("layouting started... Can take some time")
    layout(dag);
    console.log("layouting finished")

    linkData = dag.links().concat(getWeakLinks(dag))
    linkData.forEach((e) => shortenLink(e))
  }

  function updateView() {
    link = link.data(linkData).join(
      enter => onLinkUpdate(onLinkEnter(enter)),
      update => onLinkUpdate(update),
      exit => exit.remove())

    node = node.data(dag.descendants()).join(
      enter => onNodeUpdate(onNodeEnter(enter)),
      update => onNodeUpdate(update),
      exit => exit.remove())
  }

  function onLinkEnter(root) {
    return root.append('path')
      .attr("class", "link")
      .attr('marker-end', "url(#triangle)")
      .attr('fill', 'none')
      .attr('stroke-width', 1);
  }

  function onLinkUpdate(linkViewEl) {
    linkViewEl.attr('stroke', (link) => {
      if (link.target.highlight) return "red";
      else return 'black';
    })
      .attr('d', (link) => line(getNode(link).points));
    return linkViewEl
  }

  function onNodeEnter(root) {
    var groupEl = root.append("g")
      .attr("class", "node");

    groupEl.append('polygon')
      .style("stroke", "black")
    groupEl.append('text')
    return groupEl
  }

  function onNodeUpdate(nodeEl) {
    nodeEl.attr('transform', ({ x, y }) => `translate(${x}, ${y})`)
      .on("mouseover", function (d) {
        tooltip
          .style("visibility", "visible")
          .text(Utils.shortenClassName(getNode(d).name)) //.html(d.name + "<br>" +"aa")
          .style("left", (d3.event.pageX) + "px")
          .style("top", (d3.event.pageY - tooltipTextSize - nodeRadius * 3) + "px");

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
      .on("dblclick", function (d) {
        var changed = Graphs.expand(getNode(d), full_graph)
        if (changed) update()
      })
      .on("click", function (d) {
        toggleHighlighting(d)
        updateView();
      });

    nodeEl.select('polygon')
      .attr('points', d => getNode(d).sink ? "-5,-5 5,-5 5,5 -5,5" : "0,-5 3,-4 4,-3 5,0 4,3 3,4 0,5 -3,4 -4,3 -5,0 -4,-3 -3,-4")
      .style("fill", d => Colors.getColor(getNode(d).class))
      .style("stroke-width", (d) => Graphs.canExpand(getNode(d)) ? 1.5 : 0);

    nodeEl.select('text')
      .attr("dx", nodeRadius * 2 + 5 + "px")
      .attr("dy", nodeRadius + "px")
      .attr("font-size", nodeTextSize + "px")
      .attr("visibility", (d) => d.textEnabled ? "visible" : "hidden")
      .text((d) => Utils.shortenClassName(getNode(d).name));

    return nodeEl
  }

  d3.select("body").on("keydown", function showEmbeddedNodeNames() {
    const event = d3.event
    if (event.key == 'n') {
      var dagNodes = dag.descendants()

      // if there are toggled nodes, only show the names of their children,
      // otherwise show all names
      var nodes = []
      dagNodes.filter(n => n.toggled).forEach(n => {
        var ll = n.descendants()
        nodes = nodes.concat(ll)
      })
      if (nodes.length == 0) nodes = dagNodes

      nodes.forEach(n => {
        if (!n.textEnabled) n.textEnabled = true
        else n.textEnabled = false
      })
      updateView()
    }
  });

  function toggleHighlighting(node) {
    if (node.toggled) {
      node.highlight = false
      node.toggled = false
    }
    else {
      node.highlight = true;
      node.toggled = true
    }
  }

  function getWeakLinks(dag) {
    var links = new Array()
    dag.each(wrappedNode => {
      var n = wrappedNode
      n.data.cuttedChildren.forEach(child => {
        if (child.enabled) links.push(getLink(n, getOutteNode(child, dag)))
      });
    });
    return links
  }

  function getOutteNode(innerNode, outterGraph) {
    var outterNode;
    outterGraph.each((n) => {
      if (n.data.id == innerNode.id) {
        outterNode = n
        return
      }
    })
    return outterNode
  }

  function shortenLink(link) {
    var points = link.data.points
    var p1 = points[points.length - 2]
    // we use the actual link target as reference, to make the operation indempotent 
    var p2 = { x: link.target.x, y: link.target.y }

    var vecX = p2.x - p1.x
    var vecY = p2.y - p1.y
    var norm = Math.sqrt(vecX ** 2 + vecY ** 2)
    var scale = 1 - (nodeRadius * 2 / norm)

    var linkP2 = points[points.length - 1]
    linkP2.x = p1.x + vecX * scale
    linkP2.y = p1.y + vecY * scale
  }

  function getLink(source, target) {
    var link = new Object();
    link.source = source.id;
    link.target = target.id;
    link.data = { points: [{ x: source.x, y: source.y }, { x: target.x, y: target.y }] }
    return link
  }

  function getNode(nodeWrapper) {
    return nodeWrapper.data
  }

  function boundX(x) {
    return Math.max(Math.min(x, width), 0 + nodeRadius * 2);
  }

  function boundY(y) {
    return Math.max(Math.min(y, height), 0 + nodeRadius * 2);
  }
}