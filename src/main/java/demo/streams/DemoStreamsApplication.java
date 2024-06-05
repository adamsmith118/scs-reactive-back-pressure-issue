package demo.streams;

import java.util.function.Function;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.kafka.receiver.ReceiverOffset;

@SpringBootApplication
@Slf4j
public class DemoStreamsApplication {

  public static void main(String[] args) {
    SpringApplication.run(DemoStreamsApplication.class, args);
  }

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
}
