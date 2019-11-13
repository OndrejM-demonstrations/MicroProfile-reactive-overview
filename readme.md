# A demo application showcasing MicroProfile reactive APIs with Payara

## Introduction

This is a simple REST application that demonstrates how to write reactive applications with MicroProfile.

More information about MicroProfile can be found at https://microprofile.io/

More information about Payara Micro (an opensource MicroProfile runtime) can be found at https://payara.fish/

## How to build and run the application

Build using Maven with:

```
mvn clean package
```

Run using Maven with using Payara Micro Maven plugin:

```
mvn payara-micro:start
```

Redeploy after code changes (keep app running):

```
mvn resources:resources compiler:compile war:exploded
touch target/reactive/.reload
```

If you don't have the `touch` command, just create an empty file `target/reactive/.reload` to reload the app.

## Demo scenarios

The relevant code is in [FactorialResource.java](src/main/java/payara/reactive/FactorialResource.java). It contains several methods, each of them demonstrates a different case. Each method is exposed as a REST resource so that it's easy to trigger and observe the behavior.

To demonstrate that reactive programming reduces thread blocking which leads to smaller thread usage, the demo limits the size of the HTTP request thread pool to 5. So only 5 threads are available to process all requests. This is configured using the file [config/small-thread-pool.asadmin](config/small-thread-pool.asadmin) which is added to Payara Micro in [pom.xml](pom.xml) configuration using the --prebootcommandfile startup option.

### Method computeFactorial: Blocking thread pool with non-reactive code

The method `computeFactorial` computes factorial recursively calling the same REST endpoint. Leads to blocking all threads and a deadlock for arguments bigger than 5 because the thread pool is limited to max 5 threads. All previous requests block one thread and wait for new requests to finish so recursively calling more than 5 requests leads to a deadlock and ends with an error on timeout.

1. Access http://tuxedo:8080/factorial/5 in a browser. The response is OK, contains the result of the factorial computation and is delivered fast.
2. Access http://tuxedo:8080/factorial/6. The response takes long to finish and terminates after 10 second timeout with an error. In this scenario, the request produces nested requests and requires 6 concurrent threads to finish the original request. There are not enough threads to compute the last request and it times out.

### Method computeFactorialAsync: Blocking is avoided using asynchronous JAX-RS API

Computes factorial recursively as in `computeFactorial` but solves the deadlock problem by using asynchronous API. Another nested call to the service won't block the original thread and that thread is released and can be reused for other (nested) requests. The client method returns a CompletionStage and the server method returns this object to the server container. The container then waits until the stage is completed, without blocking any threads.

Access http://tuxedo:8080/factorial/async/6 to call this method to compute the factorial of 6. This will provide the desired result, unlike the method `computeFactorial` which ends with a timeout.

### Method microProfileReactiveFactorial: Computation is optimized by streaming data

Multiple nested calls to a REST endpoint are replaced by a method that requests a stream of numbers. This is a sequence of numbers that should be multiplied together to compute the factorial. Getting the numbers as stream is much more efficient than calling a REST service for each number or intermediary result.

Access http://tuxedo:8080/factorial/mp/6 to call this method

This method will behave in the same way as `computeFactorialAsync` but demonstrates reactive MicroProfile operators to process streams of data.

### Method combineRxJavaWithMicroProfile: Computation is optimized by streaming data

This method is an example of how RxJava can be used to provide functionality on top of MicroProfile.
This method is equivalent to `microProfileReactiveFactorial` method but the stream is delayed by 2 seconds using the operator "delay" from rxJava.

Access http://tuxedo:8080/factorial/mprx/6 to call this method
