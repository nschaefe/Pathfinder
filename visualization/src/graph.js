
export function render(data) {

  // set the dimensions and margins of the graph
  var margin = { top: 10, right: 30, bottom: 30, left: 40 },
    width = screen.width - margin.left - margin.right,
    height = screen.height - margin.top - margin.bottom;
  var node_radius = 20

  // append the svg object to the body of the page
  var svg = d3.select("#viz")
    .append("svg")
    .attr("width", width + margin.left + margin.right)
    .attr("height", height + margin.top + margin.bottom)
    .attr("transform",
      "translate(" + margin.left + "," + margin.top + ")");

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
    .data(data.links)
    .enter()
    .append("line")
    .style("stroke", "#aaa")
    .attr("marker-end", "url(#triangle)");

  // Initialize the nodes
  var node = svg
    .selectAll("g")
    .data(data.nodes)
    .enter()
    .append("g").call(d3.drag()
      .on("start", dragstarted)
      .on("drag", dragged)
      .on("end", dragended));

  node.append('circle')
    .attr("r", node_radius)
    .style("fill", function (d) {
      if (d.root) return 'red'
      if (d.sink) return '#4265ff'
      else return "#69b3a2"
    });

  // Add text to nodes
  node.append('text')
    .text(d => d.name)
    .attr('font-weight', 'bold')
    .attr('font-family', 'sans-serif')
    .attr('text-anchor', 'middle')
    .attr('alignment-baseline', 'middle')
    .attr('fill', 'black');


  // Let's list the force we wanna apply on the network
  var simulation = d3.forceSimulation(data.nodes)                 // Force algorithm is applied to data.nodes
    .force("link", d3.forceLink()                               // This force provides links between nodes
      .id(function (d) { return d.id; })                     // This provide  the id of a node
      .links(data.links)
      .distance(node_radius * 3)
      .iterations(1)
    )
    .force("charge", d3.forceManyBody().strength(-300))
    // This adds repulsion between nodes. Play with the -400 for the repulsion strength
    .force("center", d3.forceCenter(width / 2, height / 2))     // This force attracts nodes to the center of the svg area
    .force("collide", d3.forceCollide().radius(node_radius * 1.25))
    .on("tick", ticked);

  simulation.alpha(1).restart();

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

  function boundX(x) {
    return Math.max(Math.min(x, width), 0 + node_radius * 2);
  }

  function boundY(y) {
    return Math.max(Math.min(y, height), 0 + node_radius * 2);
  }

  function dragstarted(d) {
    if (!d3.event.active) simulation.alphaTarget(0.3).restart();
    d.fx = d.x;
    d.fy = d.y;
  }

  function dragged(d) {
    d.fx = d3.event.x;
    d.fy = d3.event.y;
  }

  function dragended(d) {
    if (!d3.event.active) simulation.alphaTarget(0);
    d.fx = null;
    d.fy = null;
  }

}