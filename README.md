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

### drill
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
We can compile the code and run the system such that the API method is executed. We shut the system down and look at the the outputs the tool produced. A file starting with tracker_report in the folder the system was started. We can do this by using the visualization (see Visualization chapter below) the tool provides.
By looking at the outputs we figured out that the thread A1 communicated with another thread B1. From the shown stacktraces and corresponding code we derived that A1 iniated a Runnable in method "DoSomeConcurrentWork" that is executed by B1 that is part of a threadpool.
This runnable contains work that belongs to the same request. We decide to propagate context here. We do not need to put actual calls to our tracing framework in the code, yet.
We just mark this code place by extending the tracking scope. We fork and join the tracking scope object as shown in the method "DoSomeConcurrentWork".
We recompile the system and run it again such that the API request is executed again. Now we consider the outputs under tag "ConcurrentWorkRunnableExec".
We continue doing this for all appearing thread relations until no relevant relations with other threads appear or when a all appearing relations were already covered.

```
public void myAPIRequest(...){
    try {
      AccessTracker.startTask("ClientStartMyAPIRequest");

      doTheWork();

    } finally {
      AccessTracker.stopTask();
    }

}
  
  
public DoSomeConcurrentWork(){
 Task trackerTask = AccessTracker.fork();
   customThreadPool.execute(new Runnable() {
     @Override
     public void run() {
       try {
         if (trackerTask != null) {
           AccessTracker.join(trackerTask);
           AccessTracker.getTask().setTag("ConcurrentWorkRunnableExec");
           AccessTracker.getTask().setWriteCapability(true);
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
* copy the tracked data under ./visualization/src/tracker_report.txt.
* Go to the config section in ./visualization/src/index.js and adjust the config as needed.
* Build the tool with ./visualization/build.sh. The first time you build it you have to first setup the npm environment with npm install.
Everytime the tracker data changes build.sh also needs to be executed.
* Now just open index.html under ./visualization/dist/index.html (tested with chrome)
* If your config is correct you should see a graph, if not look at the browser console output for help.

#### Visualization Features
* mouse over a node shows the corresponding stack trace entry, the postifx _R or _W means reader or writer thread
* press n on your keyboard to make all node labels visible
*click on a node to mark it. press n to show the labels of all nodes under the marked node
* press r to unselect all 
* one color corresponds to one class
* the rectangles at the bottom are the memory addresses of the inter thread communications where one thread wrote to and the other read from
* if a node has a label like "unknown.." this belongs to an array index access where the field location of the corresponding array could not be automatically inferred
* double click on a rectangle to see only the part of the graph the rectangle is reachable from.






