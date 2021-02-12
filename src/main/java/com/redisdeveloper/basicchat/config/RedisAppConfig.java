package com.redisdeveloper.basicchat.config;

import com.redisdeveloper.basicchat.service.RedisMessageSubscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;


@Configuration
public class RedisAppConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisAppConfig.class);

    @Value("#{'${redis.endpointUrl}'.split(':')[0] ?: \"127.0.0.1\"}")
    private String REDIS_HOST;

    @Value("#{'${redis.endpointUrl}'.split(':')[1] ?: \"6379\"}")
    private String REDIS_PORT;

    @Value("${redis.password:null}")
    private String REDIS_PASSWORD;

    @Bean
    @ConditionalOnMissingBean(name = "redisTemplate")
    @Primary
    public StringRedisTemplate redisTemplate(RedisConnectionFactory redisConnectionFactory) {
        return new StringRedisTemplate(redisConnectionFactory);
    }

    @Bean
    MessageListenerAdapter messageListener() {
        return new MessageListenerAdapter(new RedisMessageSubscriber());
    }

    @Bean
    RedisMessageListenerContainer redisContainer(RedisConnectionFactory redisConnectionFactory,
                                                 MessageListenerAdapter messageListener) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(redisConnectionFactory);
        container.addMessageListener(messageListener, topic());
        return container;
    }

    @Bean
    ChannelTopic topic() {
        return new ChannelTopic("MESSAGES");
    }

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration config =
                new RedisStandaloneConfiguration(REDIS_HOST, Integer.parseInt(REDIS_PORT));
        config.setPassword(REDIS_PASSWORD);
        LOGGER.info("REDIS: Connecting to "+REDIS_HOST+":"+REDIS_PORT+" with password: "+REDIS_PASSWORD);

        return new LettuceConnectionFactory(config);
    }
}
