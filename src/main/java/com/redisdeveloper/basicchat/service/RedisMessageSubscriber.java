package com.redisdeveloper.basicchat.service;

import com.redisdeveloper.basicchat.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

@Service
public class RedisMessageSubscriber implements MessageListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(RedisMessageSubscriber.class);

    @Autowired
    private StringRedisTemplate redisTemplate;

    CopyOnWriteArrayList<Function<String, Integer>> handlers = new CopyOnWriteArrayList<>();

    @PostConstruct
    void init() {
        try {
            Utils.createDemoData(redisTemplate);
        } catch (Exception e) {
            LOGGER.error("Error occurred when creating demo data", e);
        }
    }

    @Override
    public void onMessage(final Message message, final byte[] pattern) {
        String messageBody = new String(message.getBody());

        for (Function<String, Integer> handler : handlers) {
            handler.apply(messageBody);
        }
    }

    public void attach(Function<String, Integer> handler) {
        handlers.add(handler);
    }
    public void detach(Function<String, Integer> handler) {
        handlers.removeIf(e -> e.equals(handler));
    }
}