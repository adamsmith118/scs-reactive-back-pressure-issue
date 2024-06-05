package demo.reactor;

import java.util.Collections;
import java.util.Map;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.Disposable;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverOptions;

@SpringBootApplication
@Slf4j
public class DemoReactorApp {

  public static void main(String[] args) {
    SpringApplication.run(DemoReactorApp.class, args);
  }

  @EventListener(ApplicationStartedEvent.class)
  public Disposable start() {
    log.info("Starting receiver");

    var client = WebClient.create("http://localhost:9063/actuator/health");

    ReceiverOptions<String, String> ro = ReceiverOptions.<String, String>create(
            Map.of(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092",
                ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 1,
                ConsumerConfig.GROUP_ID_CONFIG, "test-group",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
        )
        .withKeyDeserializer(new StringDeserializer())
        .withValueDeserializer(new StringDeserializer())
        .subscription(Collections.singletonList("test"));

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
}
