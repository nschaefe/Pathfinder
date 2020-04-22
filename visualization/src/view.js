import { getColor } from "./colors.js"
import { Graphs } from "./graph.js";

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


  Graphs.shrinkStraightPaths(full_graph)
  Graphs.enableParentsOfSinks(full_graph)
  Graphs.disableReaderTraces(full_graph)

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
    .coord(d3.coordVert())//.coordCenter()

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
      Graphs.updateLinks(full_graph)
      dag = builder(...Graphs.getRoots(full_graph))
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
        //.attr('marker-mid', "url(#triangle)")
        .attr('stroke-width', 1)
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
        .on("dblclick", function (d) {
          var changed = Graphs.expand(getNode(d), full_graph)
          if (changed) update()
        })
        .on("click", function (d) {
          toggleHighlighting(d)
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
        .style("stroke-width", (d) => Graphs.canExpand(getNode(d)) ? 2 : 0)
        .attr('transform', ({ x, y }) => `translate(${x}, ${y})`);
    }
  };

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

  function getNode(nodeWrapper) {
    return nodeWrapper.data
  }

  function boundX(x) {
    return Math.max(Math.min(x, width), 0 + node_radius * 2);
  }

  function boundY(y) {
    return Math.max(Math.min(y, height), 0 + node_radius * 2);
  }
}