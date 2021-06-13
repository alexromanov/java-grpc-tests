package example.myapp.helloworld;

import akka.Done;
import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.grpc.GrpcClientSettings;
import akka.japi.Pair;
import akka.stream.Materializer;
import akka.stream.SystemMaterializer;
import akka.stream.javadsl.Source;
import example.myapp.helloworld.grpc.GreeterServiceClient;
import example.myapp.helloworld.grpc.HelloReply;
import example.myapp.helloworld.grpc.HelloRequest;
import org.junit.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class GreeterServiceApiTest {
    private static final String HOST = "127.0.0.1";
    private static final int PORT = 8080;
    private static final int SMALL_TIMEOUT = 3;
    private static final int LONG_TIMEOUT = 60;

    ActorSystem system = ActorSystem.create("HelloWorldClient");
    Materializer materializer = SystemMaterializer.get(system).materializer();

    GrpcClientSettings settings = GrpcClientSettings.connectToServiceAt(HOST, PORT, system)
            .withTls(false);

    GreeterServiceClient client = GreeterServiceClient.create(settings, system);

    @Test
    public void shouldHandleUnaryCalls() throws Exception {
        HelloRequest request = HelloRequest.newBuilder().setName("Alice").build();
        CompletionStage<HelloReply> response = client.sayHello(request);
        String message = response.toCompletableFuture().get(SMALL_TIMEOUT, TimeUnit.SECONDS).getMessage();
        assertThat(message)
                .isEqualTo("Hello, Alice");
    }

    @Test
    public void shouldHandleClientStreaming() throws Exception {
        List<HelloRequest> requests = Stream.of("Alice", "Bob", "Carol")
                .map(name -> HelloRequest.newBuilder().setName(name).build())
                .collect(Collectors.toList());
        CompletionStage<HelloReply> response = client.itKeepsTalking(Source.from(requests));
        String message = response.toCompletableFuture().get(SMALL_TIMEOUT, TimeUnit.SECONDS).getMessage();
        assertThat(message)
                .isEqualTo("Hello, Alice, Bob, Carol");
    }

    @Test
    public void shouldHandleServerStreaming() throws Exception {
        HelloRequest request = HelloRequest.newBuilder().setName("Alice").build();
        Source<HelloReply, NotUsed> responseStream = client.itKeepsReplying(request);
        CompletionStage<Done> done = responseStream.runForeach(response ->
                assertThat(response.getMessage()).isNotEmpty(), materializer);
        done.toCompletableFuture().get(LONG_TIMEOUT, TimeUnit.SECONDS);
    }

    @Test
    public void shouldHandleBiDirectional() throws Exception {
        Duration interval = Duration.ofSeconds(1);
        Source<HelloRequest, NotUsed> requestStream = Source
                .tick(interval, interval, "tick")
                .zipWithIndex()
                .map(Pair::second)
                .map(i -> HelloRequest.newBuilder().setName("Alice-" + i).build())
                .take(10)
                .mapMaterializedValue(m -> NotUsed.getInstance());
        Source<HelloReply, NotUsed> responseStream = client.streamHellos(requestStream);
        CompletionStage<Done> done =
                responseStream.runForeach(response ->
                        assertThat(response.getMessage()).isNotEmpty(), materializer);

        done.toCompletableFuture().get(LONG_TIMEOUT, TimeUnit.SECONDS);
    }
}
