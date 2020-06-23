The tool hooks into compiled java classes by inserting method calls to a central tracking framework on field/array accesses.  
Classes of the java lib (e.g. java.util) are statically instrumented by rewriting classes contained in the rt.jar.  
Application specific or thirdparty classes are instrumented at runtime when these are loaded (using javaagent support).  
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
See mvn exec@dynmaic call in the pom.xml of testClient

## Usage
The framework captures any write by threads that have a taskID. TaskIDs are not inherited to forked threads. Reads are captured independed of the taskID (so just all are captured).
If there is a write and read to/from the same location by different threads, this is reported. 

**AccessTracker.startTask** sets a taskID in the thread's local.\
**AccessTracker.stopTask**  removes the taskID in the thread's local.\
**AccessTracker.hasTask**   returns if there is a taskID set in the thread local.

**AccessTracker.pauseTask** copies the thread local to somewhere else and removes the taskID in the thread local\
**AccessTracker.resumeTask** brings the copied version back.
There is a limited support for subsequent calls like pause() pause() resume() resume(). The taskID is only copied on the first pause() and brought back on the last resume().


