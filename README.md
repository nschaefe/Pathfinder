The tool detects concurrent write-read relations by different threads.
Example: Thread A writes a Runnable to an array, Thread B reads this runnable. The tool logs a report to a file located where the application is started.
The report contains the array location and the stack traces of the writer and the reader.

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

AccessTracker.startTask sets a taskID in the thread's local.\
AccessTracker.stopTask  removes the taskID in the thread's local.\
AccessTracker.hasTask   returns if there is a taskID set in the thread local.

pauseTask copies the thread local to somewhere else and removes the taskID in the thread local
resumeTask brings the copied version back.
There is a limited support for subsequent calls like pause() pause() resume() resume(). The taskID is only copied on the first pause() and brought back on the last resume().


