
export function render(data) {

  // set the dimensions and margins of the graph
  var margin = { top: 10, right: 30, bottom: 30, left: 40 },
    width = screen.width - margin.left - margin.right,
    height = screen.height - margin.top - margin.bottom;
  var node_radius = 5

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


  //TODO
  svg.append("svg:defs").append("svg:marker")
    .attr("id", "triangle")
    .attr("refX", 15)
    .attr("refY", -1.5)
    .attr("markerWidth", 6)
    .attr("markerHeight", 6)
    .attr("orient", "auto")
    .append("path")
    .attr("d", "M 0 -5 10 10")
    .style("stroke", "black");

  // Initialize the links
  var link = svg
    .selectAll("line")

  // Initialize the nodes
  var node = svg
    .selectAll("g")

  // Let's list the force we wanna apply on the network
  var simulation = d3.forceSimulation(data.nodes)                 // Force algorithm is applied to data.nodes
    .force("link", d3.forceLink()                               // This force provides links between nodes
      .id(function (d) { return d.id; })                     // This provide  the id of a node
      .links(data.links)
      .distance(node_radius * 3).strength(2)
      .iterations(1)
    )
    .force("charge", d3.forceManyBody().strength(-50))
    // This adds repulsion between nodes. Play with the -400 for the repulsion strength
    .force("center", d3.forceCenter(width / 2, height / 2))     // This force attracts nodes to the center of the svg area
    .force("collide", d3.forceCollide().radius(node_radius * 1.5))
    .on("tick", ticked);

  update()

  function update() {

    link = link.data(data.links)
    link.exit().remove();
    link = link
      .enter().append("line")
      .attr("class", "link")
      .style("stroke", "#aaa").merge(link);

    node = node.data(data.nodes);
    node.exit().remove();
    node = node.enter()
      .append("g")
      .attr("visibility", (d) => {
        if (d.enabled) return "visible"
        else return "collapse"
      })
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
      .call(d3.drag()
        .on("start", dragStart)
        .on("drag", draging)
        .on("end", dragEnd))
      .append('circle')
      .attr("r", node_radius)
      .style("fill", function (d) {
        if (d.root) return 'red'
        if (d.sink) return '#4265ff'
        else return "#69b3a2"
      })
      .merge(node)

    simulation.restart();

  };


  // This function is run at each iteration of the force algorithm, updating the nodes position.
  function ticked() {

    // prevent exceeding borders
    data.nodes.forEach(el => {
      el.x = boundX(el.x)
      el.y = boundY(el.y)
    });

    node
      // .attr("cx", function (d) { return d.x + 6; })
      // .attr("cy", function (d) { return d.y - 6; });
      .attr('transform', (d) => `translate(${d.x}, ${d.y})`);

    link
      .attr("x1", function (d) { return d.source.x; })
      .attr("y1", function (d) { return d.source.y; })
      .attr("x2", function (d) { return d.target.x; })
      .attr("y2", function (d) { return d.target.y; });

  }

  // d3.interval(function () {
  //   data.nodes = data.nodes.slice(1); // Remove c.
  //   update();
  // }, 1000, d3.now());

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

}