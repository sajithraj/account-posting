package com.accountposting.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
public class PostingEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topic.posting-success}")
    private String postingSuccessTopic;

    public void publishSuccess(PostingSuccessEvent event) {
        log.info("[KAFKA] Publishing SUCCESS event postingId={} e2eRef={} topic={}",
                event.postingId(), event.endToEndReferenceId(), postingSuccessTopic);
        kafkaTemplate.send(postingSuccessTopic, String.valueOf(event.postingId()), event);
    }
}
