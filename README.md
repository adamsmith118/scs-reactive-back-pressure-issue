# Spring Cloud Streams Reactor Kafka back pressure issue/query

# Expected behaviour

Parity in how back pressure is handled when using the SCS ReactorKafkaBinder and ReactorKafka directly.

# Actual behaviour

Consider two consumer implementations performing identical tasks; a simulation of some work followed by a WebClient call.

## Using Reactor Directly

max.poll.records=1 for both, to keep things simple.

```java
  @EventListener(ApplicationStartedEvent.class)
  public Disposable start() {
    log.info("Starting receiver");

    var client = WebClient.create("http://localhost:9063/actuator/health");

    ReceiverOptions<String, String> ro = ReceiverOptions.<String, String>create...// Omitted.  See code.

    return KafkaReceiver.create(ro)
        .receive()
        .concatMap(event -> {
          log.info("Got an event: {}", event);
          pretendWork(1000);

          return client.get()
              .retrieve()
              .bodyToMono(String.class)
              .doOnNext(s -> log.info("Result = {}", s))
              .doOnSuccess(s -> event.receiverOffset().acknowledge());
        }, 1)
        .subscribe();
  }

  @SneakyThrows
  private void pretendWork(int ms) {
    if (ms > 0) {
      log.info("Sleeping for {}ms", ms);
      Thread.sleep(ms);
    }
  }
```

## Using SCS Reactor Kafka Binder

```java
  @Bean
  public Function<Flux<Message<String>>, Mono<Void>> test() {
    var client = WebClient.create("http://localhost:9063/actuator/health");

    return events -> events.concatMap(event -> {
      log.info("Got an event: {}", event);
      pretendWork(1000);

      return client.get()
          .retrieve()
          .bodyToMono(String.class)
          .doOnNext(s -> log.info("Result = {}", s))
          .doOnSuccess(s -> event.getHeaders().get(KafkaHeaders.ACKNOWLEDGMENT, ReceiverOffset.class).acknowledge());
    }, 1).then();
  }

  @SneakyThrows
  private void pretendWork(int ms) {
    if (ms > 0) {
      log.info("Sleeping for {}ms", ms);
      Thread.sleep(ms);
    }
  }
```
In reactor kafka (example 1) we can see behavior in-line with back pressure requirements.  One record is emitted at a time.  The consumer pauses as necessary.  

```
r.k.r.internals.ConsumerEventLoop        : Emitting 1 records, requested now 0
o.a.k.clients.consumer.KafkaConsumer     : [Consumer clientId=consumer-test-group-1, groupId=test-group] Committing offsets: {test-1=OffsetAndMetadata{offset=78
r.k.r.internals.ConsumerEventLoop        : onRequest.toAdd 1, paused true
r.k.r.internals.ConsumerEventLoop        : Consumer woken
```

In Spring Cloud Streams, using the ReactorKafkaBinder, this isn't the case.

Here, 100's of records are emitted to the sink:

```
r.k.r.internals.ConsumerEventLoop        : Emitting 1 records, requested now 0
r.k.r.internals.ConsumerEventLoop        : onRequest.toAdd 1, paused false
r.k.r.internals.ConsumerEventLoop        : Emitting 1 records, requested now 0
r.k.r.internals.ConsumerEventLoop        : onRequest.toAdd 1, paused false
r.k.r.internals.ConsumerEventLoop        : Emitting 1 records, requested now 0
r.k.r.internals.ConsumerEventLoop        : onRequest.toAdd 1, paused false
r.k.r.internals.ConsumerEventLoop        : Emitting 1 records, requested now 0
r.k.r.internals.ConsumerEventLoop        : onRequest.toAdd 1, paused false
r.k.r.internals.ConsumerEventLoop        : Emitting 1 records, requested now 0
<snip>
```

This causes problems if a rebalance occurs during a period of heavy load as the pipeline can contain 100's of pending records.  We'd need to set an intolerably high maxDelayRebalance to get through them all.

Logs resembling the below are visible.

```
Rebalancing; waiting for 523 records in pipeline
```

Presumably something in the binder/channel implementation is causing this? 

# Repro

Requires a Kafka on localhost:9092 and a topic called "test".

`Producer` class in test will send 100 messages.

`demo.reactor.DemoReactorApp` - Pure Reactor Kafka example
`demo.streams.DemoStreamsApplication` - Spring Cloud Streams example

DEBUG logging has been enabled for the `ConsumerEventLoop` for emit visibility.

# Environment details

Java 21
Boot 3.2.2
SCS: 4.1.0
Reactor Kafka: 1.3.22

Loosely related issue [here](https://github.com/reactor/reactor-kafka/issues/345).