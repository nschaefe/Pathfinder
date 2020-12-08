Pathfinder aids a developer with instrumenting a software system for request context propagation such as needed for distributed tracing.

To realize tracing request context must be propagated alongside the request everywhere the request goes. Manually implementing context propagation involves identifying and implementing all thread exectution boundaries (e.g. propagating runnables to execution services). This can be challenging.
Pathfinder helps to identify such thread execution boundaries by tracking inter-thread communication in program runs.

Pathfinder rewrites Java classes to track memory accesses and reports inter-thread communication events. 

An inter-thread communication event occurs at time t2 if following conditions hold:  
Thread A writes to a memory address X at time t1.  
Thread B reads from memory address X at time t2, with t1 < t2 and A != B.  
There does not exist a point in time t3 where t1 < t3 <t2 and a write happens to memory address X.

If a inter-thread communcation event is detected, the stacktrace of the wirter and read thread are logged.
Tracked data can be inspected with an additional investigation tool.

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

### visualization
A tool to select, filter and visualize the tracked data.

### drill (deprecated)
Provides 
* A SQL query generator framework, to generate SQL queries for filtering and analysing the outputs of **tracker**. These SQL queries are inteded to be used for Apache-Drill. The supported syntax is used.
* A storage plugin for Apache drill for input and output.

## Build
* install the custom javassist version under ./lib 
* mvn clean install

This builds everything and instruments the shipped rt.jar.

## Installation
To install the tool in an application, the instrumented rt.jar and the javaagent must be provided.
See mvn exec@dynamic call in the pom.xml of testClient\
Example:\
java -Xbootclasspath/p:/some/path/InstrumentationHelper/javaRT/rt_inst.jar:/some/path/InstrumentationHelper/tracker/target/tracker-0.1-SNAPSHOT-jar-with-dependencies.jar:/some/path/InstrumentationHelper/agent/target/agent-0.1-SNAPSHOT-jar-with-dependencies.jar -javaagent:/some/path/InstrumentationHelper/agent/target/agent-0.1-SNAPSHOT-jar-with-dependencies.jar


## Getting Started
Pathfinder provides an API to control what is tracked.
The framework captures any write by threads that have a taskID. TaskIDs are not inherited to forked threads. Reads are captured independed of the taskID (so just all are captured).
If there is a write and read to/from the same location by different threads this is reported. 

**AccessTracker.startTask** starts a tracking task for the current thread. Thereby gets a taskID\
**AccessTracker.stopTask**  stops the task if there is any for the current thread. This removes the taskID.\
**AccessTracker.hasTask**   returns true if there is an active tracking task.\
**AccessTracker.getTask**   returns the current tracking task object if there is any, null otherwise.
   
**AccessTracker.pauseTask**  pauses the tracking task and so temporarily removes the taskID from the thread\
**AccessTracker.resumeTask** brings the paused tracking task back.
There is a limited support for subsequent calls like pause() pause() resume() resume(). The task is only brought back on the last resume(). Intermediate resumes have no observable effects and caused writes are still not tracked.

**AccessTracker.fork**      copies and returns the current tracking task object to pass it over to another thread.\
**AccessTracker.join**      continues the given task. If there is already a task present, joins the given task into the existing one.\
**AccessTracker.discard**   equivalent to stopTask.

In the following we present an example of the application of this API.

```java
public void myAPIRequest(...){
    try {
      AccessTracker.startTask("ClientStartMyAPIRequest");

      doSomeConcurrentWork();

    } finally {
      AccessTracker.stopTask();
    }

}
  
  
public doSomeConcurrentWork(){
  WorkItem item = new WorkItem(...);
  Task trackerTask = AccessTracker.fork();
  customThreadPool.execute(new Runnable() {
     @Override
     public void run() {
        try {
          AccessTracker.join(trackerTask, "ConcurrentWorkRunnableExec");
          doWork(item);
        } finally {
          AccessTracker.discard();
        }
     }
  });
}

```
The code might be part of a server that processes requests.
As part of processing a request that starts at "myAPIRequest" a thread pool is used in "doSomeConcurrentWork" to perform some work. 
We can use Pathfinder here to detect the place where the Runnable is submitted to the thread pool and where we usually need to propagate request context.

We place AccessTracker.startTask() at the beginning of the request and end the task at the end of the method. When we run the system under Pathfinder (run the server with the JVM options for installation), Pathfinder will detect several inter-thread communications between the thread that started the tracking task and the thread pool thread that performs the work.
Those events will be logged to disk in a tracker report file that will be automatically generated in the folder where the server was started.
We can investigate the tracked data with the visualization tool. 
Now we know where this boundary is and so as a next step we can extend the tracking along the request path across this thread execution boundary by propagating the task over to the thread pool thread with AccessTracker.fork and AccessTracker.join.


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



