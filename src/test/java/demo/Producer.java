package demo;

import demo.streams.DemoStreamsApplication;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.support.MessageBuilder;

@SpringBootTest(classes = DemoStreamsApplication.class)
public class Producer {
  @Autowired
  private StreamBridge streamBridge;

  @Test
  void sendMessages() {
    for (int i = 0; i < 100; i++) {
      var msg = MessageBuilder
          .withPayload("A message " + i)
          .setHeaderIfAbsent(KafkaHeaders.KEY, UUID.randomUUID().toString())
          .build();
      streamBridge.send("test-out-0", msg);
    }
  }
}