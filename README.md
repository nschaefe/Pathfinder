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

