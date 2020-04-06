//TODO require or import etc
const nodeRadius = 20;
const width = screen.width
const height = screen.height
const margin = 5

const svgNode = `<svg width=${width} height=${height} viewbox="${-nodeRadius} ${-nodeRadius} ${width + 2 * nodeRadius} ${height + 2 * nodeRadius}"></svg>`
document.body.innerHTML += svgNode

const svgSelection = d3.select("svg");
//svgSelection.style("background-color", "grey")

// data = [
//     ["1", "2"],
//     ["1", "5"],
//     ["1", "7"],
//     ["2", "3"],
//     ["2", "4"],
//     ["2", "5"],
//     ["2", "7"],
//     ["2", "8"],
//     ["3", "6"],
//     ["3", "8"],
//     ["4", "7"],
//     ["5", "7"],
//     ["5", "8"],
//     ["5", "9"],
//     ["6", "8"],
//     ["7", "8"],
//     ["9", "10"],
//     ["9", "4"],
//     ["9", "3"],
//     ["9", "11"]

// ]

// could be real detection data
data = [
    ["1", "2"],
    ["2", "3"],
    ["3", "3.1"],
    ["3", "3.2"],
    ["3", "3.3"],
    ["3.3", "3.3.1"],
    ["3.2", "3.2.01"],
    ["3.2.01", "3.2.001"],
    ["3.2.001", "3.2.1"],
    ["3.2.1", "3.2.2"],

    ["r1", "r2"],
    ["r2", "r3"],
    ["r3", "3.1"],
    ["r3", "r3.2"],
    ["r3", "r3.3"],
    ["r3.3", "3.3.1"],
    ["r3.2", "r3.2.1"],
    ["r3.2.1", "3.2.2"],

]

//     <defs>
//     <marker id='head' orient='auto' markerWidth='2' markerHeight='4'
//             refX='0.1' refY='2'>
//       <path d='M0,0 V4 L2,2 Z' fill='red' />
//     </marker>
//   </defs> 

dag = d3.dagConnect()(data)
layout = d3.sugiyama()
    .size([height - margin, width - margin]) // TODO seems to have a bug here, height and width must be swaped like this
    .layering(d3.layeringSimplex())
    .decross(d3.decrossTwoLayer())
    .coord(d3.coordMinCurve())

// Use computed layout
layout(dag);
render();

function render() {

    const steps = dag.size();
    const interp = d3.interpolateRainbow;
    const colorMap = {};
    dag.each((node, i) => {
        colorMap[node.id] = interp(i / steps);
    });

    // How to draw edges
    const line = d3.line()
        .curve(d3.curveMonotoneX)
        .x(d => d.y)
        .y(d => d.x);

    // Plot edges
    svgSelection.append('g')
        .selectAll('path')
        .data(dag.links())
        .enter()
        .append('path')
        .attr('d', ({ data }) => line(data.points))
        .attr('fill', 'none')
        .attr('stroke-width', 2)
        .attr('stroke', 'black');


    // use groups for nodes to group circles and text as nodes
    const nodes = svgSelection.append('g')
        .selectAll('g')
        .data(dag.descendants())
        .enter()
        .append('g')
        .attr('transform', ({ x, y }) => `translate(${y}, ${x})`); //TODO x and y switched?

    // Plot node circles
    nodes.append('circle')
        .attr('r', nodeRadius)
        .attr('fill', n => colorMap[n.id]);

    // Add text to nodes
    nodes.append('text')
        .text(d => d.id)
        .attr('font-weight', 'bold')
        .attr('font-family', 'sans-serif')
        .attr('text-anchor', 'middle')
        .attr('alignment-baseline', 'middle')
        .attr('fill', 'black');

}

function scale(dag, factor) {
    dag.each((node, i) => {
        node.x = node.x * factor;
        node.y = node.y * factor;
    });

    dag.links().forEach(link => {
        link.data.points.forEach(d => {
            d.x = d.x * factor;
            d.y = d.y * factor;
        });
    });
}