The tool hooks into compiled java classes by inserting method calls to a central tracking framework on field/array accesses.  
Classes of the java lib (e.g. java.util) are statically instrumented by rewriting the rt.jar.  
Application specific and thirdparty classes are instrumented at runtime when these are loaded (using javaagent support).  
At runtime, when the instrumented code is executed, inserted methods are invoked when a field/array is accessed.
Field/array location information is passed along with these calls. In addtion, the tracking framework captures thread related information on each call.  
Incoming data is correlated with tracked data to detect inter thread communication.  
If thread A writes to a field or array and thread B reads from the same location and A!=B and A did the last write, an inter thread communication is reported.  

Example (java.util.LinkedList.set):

```
public E set(int index, E element) {
        checkElementIndex(index);
        Node<E> x = node(index);
        E oldVal = x.item;
        x.item = element;
        return oldVal;
}
```


is rewritten to:

```
public E set(int index, E element) {
        checkElementIndex(index);
        Node<E> x = node(index);
        AccessTracker.readObject(x, "java.util.LinkedList$Node.item");
        E oldVal = x.item;
        AccessTracker.writeObject(x, element, "java.util.LinkedList$Node.item");
        x.item = element;
        return oldVal;
}
```

If thread A executes set(1,"value1") and thread B executes set(1,"value2") afterwards,
thread B will read "value1" before writing "value2" what leads to a inter thread communication report.
This is a toy example and demonstrates a rather uninteresting case. This inter thread communication is rather a side effect.

## Project Structure

### agent
Contains bytecode instrumentation logic to rewrite classes of the system that should be instrumented. Hooks are establsihed to monitor global field and array accesses.
Can be compiled to a java agent.

### javaRT
Utilizes code from **agent** to rewrite the rt.jar.

### tracker
Inserted monitor methods inserted by logic in **agent** call tracker methods.
Contains logic to track global field and array accesses. These are used to detect actual inter-thread communication places.

### testClient
A test module. 
Provides two ways of testing:
* JUnit testing: The testing framework and test code is automatically instrumented by **agent** as javaagent.
* Using a test client as external application that is instrumented by **agent** as javaagent. The maven pom contains several maven:exec calls for this.

### drill (deprecated)
Provides 
* A SQL query generator framework, to generate SQL queries for filtering and analysing the outputs of **tracker**. These SQL queries are inteded to be used for Apache-Drill. The supported syntax is used.
* A storage plugin for Apache drill for input and output.

## Build
* install the custom javassist version under ./lib 
* mvn clean install

This builds everything, instruments the rt.jar and runs the tests.

## Installation
To install the tool in an application, the instrumented rt.jar and the javaagent must be provided.
See mvn exec@dynamic call in the pom.xml of testClient\
Example:\
java -Xbootclasspath/p:/some/path/InstrumentationHelper/javaRT/rt_inst.jar:/some/path/InstrumentationHelper/tracker/target/tracker-0.1-SNAPSHOT-jar-with-dependencies.jar:/some/path/InstrumentationHelper/agent/target/agent-0.1-SNAPSHOT-jar-with-dependencies.jar -javaagent:/some/path/InstrumentationHelper/agent/target/agent-0.1-SNAPSHOT-jar-with-dependencies.jar


## Getting Started
The framework captures any write by threads that have a taskID. TaskIDs are not inherited to forked threads. Reads are captured independed of the taskID (so just all are captured).
If there is a write and read to/from the same location by different threads, this is reported. 

**AccessTracker.startTask** starts a tracking scope for the current thread.\
**AccessTracker.stopTask**  stops the tracking scope if there is any for the current thread.\
**AccessTracker.hasTask**   returns true if there is an active tracking scope.\
**AccessTracker.getTask**   returns the current tracking scope object if there is any, null otherwise.
   
**AccessTracker.pauseTask** copies the thread local to somewhere else and removes the taskID in the thread local\
**AccessTracker.resumeTask** brings the copied version back.
There is a limited support for subsequent calls like pause() pause() resume() resume(). The taskID is only copied on the first pause() and brought back on the last resume().

**AccessTracker.fork**      copies and returns the current tracking scope object to pass it over to another thread.\
**AccessTracker.join**      continues the given task. If there is already a task present, joins the given task into the existing one.\
**AccessTracker.discard**   equivalent to stopTask.

The instrumentation process with the InstrumentationHelper requires to successively track inter thread communications, traversing a thread dependency graph.
We start with the thread A1 calling the API method "myAPIRequest" in the target system. If we want to know with which other threads this thread communicates, we enclose the method body with a tracking scope.
We can compile the code and run the system such that the API method is executed. We shut the system down and look at the the outputs the tool produced (a file starting with tracker_report in the folder the system was started). We can do this by using the visualization (see Visualization chapter below) the tool provides.
By looking at the outputs we figured out that the thread A1 communicated with another thread B1. From the shown stacktraces and corresponding code we derived that A1 iniated a Runnable in method "DoSomeConcurrentWork" that is executed by B1 that is part of a threadpool.
This runnable contains work that belongs to the same request. We decide to propagate context here. We do not need to put actual calls to our tracing framework in the code, yet.
We just mark this code place by extending the tracking scope. We fork and join the tracking scope object as shown in the method "doSomeConcurrentWork".
We recompile the system and run it again such that the API request is executed again. Now we consider the outputs under tag "ConcurrentWorkRunnableExec".
We continue doing this for all appearing thread relations until no relevant relations with other threads appear or when a all appearing relations were already covered.
We strongly recommend to track in a request-bounded manner starting at the API end-point following the request along the thread dependecy graph through the system. This strongly improves performance.
We do not support the instrumentation of inter-process execution boundaries. So if the tracking hits the network you wont see any inter thread communication recorded.
Usually inter-process communication is exclusivly handled via a small set of RPC libraries (mostly just one). Currently only tracking per process is supported.

```
public void myAPIRequest(...){
    try {
      AccessTracker.startTask("ClientStartMyAPIRequest");

      doSomeConcurrentWork();

    } finally {
      AccessTracker.stopTask();
    }

}
  
  
public doSomeConcurrentWork(){
 Task trackerTask = AccessTracker.fork();
   customThreadPool.execute(new Runnable() {
     @Override
     public void run() {
        try {
          AccessTracker.join(trackerTask, "ConcurrentWorkRunnableExec");
        }
          doWork();
        } finally {
          AccessTracker.discard();
        }
     }
   });
}


```

### Filtering and Visualization

To filter and visualize the tracked data we provide an additional tool under ./visualization.
To use it follow these steps:
* copy the tracked data under ./visualization/data/tracker_report.txt. The repo contains a sample dataset.
* Go to the config section in ./visualization/src/index.js and adjust the config as needed.
* Build the tool with ./visualization/build.sh. The first time you build it you have to first setup the npm environment with npm install.
Everytime the tracker data changes build.sh also needs to be executed.
* Now just open index.html under ./visualization/dist/index.html (tested with chrome)
* If your config is correct you should see a graph, if not look at the browser console output for help.

#### Visualization Features
* mouse over a node shows the corresponding stack trace entry, the postifx _R or _W means reader or writer thread
* press n on your keyboard to make all node labels visible
* click on a node to mark it. press n to show the labels of all nodes under the marked node
* press r to unselect all 
* one color corresponds to one class
* the rectangles at the bottom are the memory addresses of the inter thread communications where one thread wrote to and the other read from
* if a node has a label like "unknown.." this belongs to an array index access where the field location of the corresponding array could not be automatically inferred
* double click on a rectangle to see only the part of the graph the rectangle is reachable from. Refresh the page to come back to the full view.
* There is a suggested order in which the

#### Investigation Strategies
There are different use cases for which the tool can be used:

* Identifying Execution Boundaries:\
The probably easiest and most efficient way for investigating the shown inter thread communications (ITCs) is to look at them one-by-one in the the suggested order (number below the rectangle). Use the one-by-one config preset in index.js.
Usually the investigation of the first few ITCs is enough to describe what the execution context of the reader and writer is and in which way they communicate on a high level. For example: a Runnable is submitted to an executor that executes it.
If the communication is not some kind of message passing, it is likely that your are looking at a "submitter relation". For example: Some meta data was made available by the writer at a global point and is now accessed by several other threads. The writer is in that case not responsible for the task the consumers are executing.
(See Principled workfow-centric tracing of distributed systems by  Sambasivan et al. for more on submitter-preserving vs trigger-preserving)
Often such relations are not relevant for tracing.

* identifying resources to store the trace context:
TODO

## Troubleshooting
* Performance: The execution is significantly slowed down by the tracking framework. Especially when the program is started and all loaded classes are instrumented. The request bounded tracking strategy effectivly reduces the performance impact of the tool, so do not try to track ITCs starting from a main method as the first approach.
As long as the slowdown does not lead to exceeding of timeouts and so changes the behavior of the program this is not a problem. If timeouts appear, try to extend the program timeouts.\
The tracking framework batches entries for write out. This can consume quite some memory. Increasing the JVM memory (e.g., -Xmx2g) of the target program can significantly improve performance by reducing GC calls.\
To speed up the instrumentation time the class filtering can be extended.
Adjust the EXCLUDED list in ./tracker/src/main/java/boundarydetection/tracker/AccessTracker.java\
To speed up tracking you can try out the minimal tracking mode. Set minimalTracking to true in ./tracker/src/main/java/boundarydetection/tracker/AccessTracker.java. Be aware that some ITCs might be missed, though.



