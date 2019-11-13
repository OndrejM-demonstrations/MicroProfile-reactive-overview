/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package payara.reactive;

import io.reactivex.Flowable;
import java.net.URL;
import java.util.concurrent.*;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.Produces;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.streams.operators.*;
import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.reactivestreams.Publisher;

/**
 * REST Web Service
 *
 * @author Ondro Mihalyi
 */
@Path("factorial")
@ApplicationScoped
public class FactorialResource {

    @Inject
    @ConfigProperty(name = "clientUrl", defaultValue = "http://localhost:8080/factorial")
    URL clientUrl;
    
    /**
     * Computes factorial recursively calling the same REST endpoint. Leads to blocking all threads and a deadlock for arguments bigger than 5 because the thread pool is limited to max 5 threads. All previous requests block one thread and wait for new requests to finish so recursively calling more than 5 requests leads to a deadlock and ends with an error on timeout.
     */
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    @Path("{arg}")
    public long computeFactorial(@PathParam("arg") long arg) {
        if (arg > 1) {
            FactorialClient factorialClient = RestClientBuilder.newBuilder()
                    .baseUrl(clientUrl)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .build(FactorialClient.class);
            return factorialClient.computeFactorial(arg - 1) * arg;
        } else {
            return 1;
        }
    }

    
    /**
     * Computes factorial recursively as in computeFactorial but solves the deadlock problem by using asynchronous API. Another nested call to the service won't block the original thread and that thread is released and can be reused for other (nested) requests. The client method returns a CompletionStage and the server method returns this object to the server container. The container then waits until the stage is completed, without blocking any threads.
     */
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    @Path("async/{arg}")
    public CompletionStage<Long> computeFactorialAsync(@PathParam("arg") long arg) {
        if (arg > 1) {
            FactorialClient factorialClient = RestClientBuilder.newBuilder()
                    .baseUrl(clientUrl)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .build(FactorialClient.class);
            return factorialClient.computeFactorialAsync(arg - 1)
                    .thenApply(v -> v * arg);
        } else {
            return CompletableFuture.completedFuture(1L);
        }
    }

    /**
     * Multiple nested calls to a REST endpoint are replaced by a method that requests a stream of numbers. This is a sequence of numbers that should be multiplied together to compute the factorial. Getting the numbers as stream is much more efficient than calling a REST service for each number or intermediary result.
     */
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    @Path("mp/{arg}")
    public CompletionStage<Long> microProfileReactiveFactorial(@PathParam("arg") int arg) {
        return ReactiveStreams.fromPublisher(askForNumbersUpTo(arg))
                .map(Long::parseLong)
                .onErrorResume(e -> {
                    e.printStackTrace();
                    return 0L;
                })
                .filter(v -> v > 0)
                .reduce((a, b) -> a * b)
                .run()
                .thenApply(v -> v.orElse(0L))
                .toCompletableFuture()
                .orTimeout(10, TimeUnit.SECONDS);
    }

    /**
     * Other reactive libraries can be combined via the reactive streams standard. This is an example of how RxJava can be used to provide functionality on top of MicroProfile.
     * 
     * This is equivalent to microProfileReactiveFactorial method but the stream is delayed by 2 seconds using the operator "delay" from rxJava.
     */
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    @Path("mprx/{arg}")
    public CompletionStage<Long> combineRxJavaWithMicroProfile(@PathParam("arg") int arg) {
        Publisher<Long> mpPublisher =
                ReactiveStreams.fromPublisher(askForNumbersUpTo(arg))
                .map(Long::parseLong)
                .onErrorResume(e -> {
                    e.printStackTrace();
                    return 0L;
                })
                .filter(v -> v > 0)
                .buildRs();
        Flowable<Long> rxPublisher = Flowable.fromPublisher(mpPublisher)
                .delay(2, TimeUnit.SECONDS);
        return ReactiveStreams.fromPublisher(rxPublisher)
                .reduce((a, b) -> a * b)
                .run()
                .thenApply(v -> v.orElse(0L))
                .toCompletableFuture()
                .orTimeout(10, TimeUnit.SECONDS);
    }

    private Flowable<String> askForNumbersUpTo(int arg) {
        return Flowable.rangeLong(1, arg).map(v -> v.toString());
    }

}

