#itarate over json
#make each json object a node as detection, point out in the graph (special node)
#extract all writer stacktraces
#extract all reader stacktraces

#make each element a node, join them

#each normal node can have:
#visitThreads(ids)
#stacktrace element

#special nodes for actual field of the detection 

#on the roots add additional nodes representing the threads. on mouse over we display only all path of this thread

#bottom can have sliders for:
#-time
#-logical time
#-epoch
#--> needs querying use apache drill rest api + javascript (maybe adopt node.js apche drill drivers)
