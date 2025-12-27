package com.cryptoplatform.api.config;

import com.cryptoplatform.api.redis.TickerStreamListener;
import com.cryptoplatform.api.redis.TradeEngineListener;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.Subscription;

import java.time.Duration;
import java.util.UUID;

@Configuration
public class RedisConfig implements DisposableBean {

    private StreamMessageListenerContainer<String, MapRecord<String, String, String>> listenerContainer;
    private final StringRedisTemplate redisTemplate;

    public RedisConfig(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Bean
    public Subscription subscription(RedisConnectionFactory connectionFactory, TickerStreamListener streamListener) {
        
        // Ensure consumer group exists
        String streamKey = "stream:market_ticks";
        String group = "api-ws";
        try {
            redisTemplate.opsForStream().createGroup(streamKey, group);
        } catch (Exception e) {
            // Group likely already exists, ignore
        }

        StreamMessageListenerContainer.StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> options =
                StreamMessageListenerContainer.StreamMessageListenerContainerOptions.builder()
                        .pollTimeout(Duration.ofMillis(100))
                        .build();

        listenerContainer = StreamMessageListenerContainer.create(connectionFactory, options);

        // Random consumer name for fan-out (actually we want all APIs to get all messages if they serve different websockets? 
        // No, Redis Streams with Groups load balance.
        // Wait, if we have 2 API instances, and 1 client connected to Instance A and 1 to Instance B..
        // We want ALL instances to receive ALL ticks so they can fan out to their local websockets.
        // So we should NOT use the same Consumer Group for all instances if we want broadcast.
        // Or we use fan-out pattern (PubSub) but requirement said "Redis Streams".
        // With Streams, to broadcast to all nodes, each node needs its own consumer group or simply read from $ without group (but then how to track offset if restart? maybe just latest).
        // Let's us a unique group name per instance for now to ensure broadcast behavior across instances (if we ever scale up).
        
        String instanceGroup = "api-ws-" + UUID.randomUUID().toString();
        try {
            redisTemplate.opsForStream().createGroup(streamKey, instanceGroup);
        } catch (Exception e) {
            // ignore
        }

        Subscription subscription = listenerContainer.receive(
                Consumer.from(instanceGroup, "instance-1"),
                StreamOffset.create(streamKey, ReadOffset.lastConsumed()),
                streamListener);

        listenerContainer.start();
        return subscription;
    }

    @Bean
    public Subscription tradeEngineSubscription(RedisConnectionFactory connectionFactory, 
                                                TradeEngineListener tradeEngineListener,
                                                StringRedisTemplate redisTemplate) { // Inject template to create group
        
        // This listener SHOULD be load balanced across instances if we scale up.
        // So we use a shared group "api-trade-engine".
        
        String streamKey = "stream:market_ticks";
        String group = "api-trade-engine";
        
        try {
            redisTemplate.opsForStream().createGroup(streamKey, group);
        } catch (Exception e) {
            // Group exists
        }

        StreamMessageListenerContainer.StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> options =
                StreamMessageListenerContainer.StreamMessageListenerContainerOptions.builder()
                        .pollTimeout(Duration.ofMillis(100))
                        .build();

        // Note: reusing the same listenerContainer bean if possible, or creating new one?
        // Ideally we should reuse one container for all subscriptions. 
        // But `listenerContainer` field above is created inside `subscription()` method which is a @Bean.
        // Let's refactor to make container a separate Bean or just create a new one here.
        // Creating a second container is fine for now to avoid breaking existing code structure too much.
        
        StreamMessageListenerContainer<String, MapRecord<String, String, String>> container =
                StreamMessageListenerContainer.create(connectionFactory, options);

        Subscription subscription = container.receive(
                Consumer.from(group, "consumer-" + UUID.randomUUID()), // Unique consumer name to avoid clashes within group? No, consumer name must be unique per thread/connection usually.
                StreamOffset.create(streamKey, ReadOffset.lastConsumed()),
                tradeEngineListener);

        container.start();
        return subscription;
    }

    @Override
    public void destroy() {
        if (listenerContainer != null) {
            listenerContainer.stop();
        }
    }
}
