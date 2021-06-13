## java-grpc-tests

Test examples written in Java 11 and akka-grpc library.

Tests should be executed against [server-grpc-sample](https://github.com/alexromanov/server-grpc-sample).

Generate gRPC clients:
```console
mvn akka-grpc:generate
```

Execute tests (in case if server app has already started):
```console
mvn clean install
```