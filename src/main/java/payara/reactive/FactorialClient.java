package payara.reactive;

import java.util.concurrent.CompletionStage;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

interface FactorialClient {

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    @Path("{arg}")
    public long computeFactorial(@PathParam("arg") long arg);

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    @Path("async/{arg}")
    public CompletionStage<Long> computeFactorialAsync(@PathParam("arg") long arg);

}

