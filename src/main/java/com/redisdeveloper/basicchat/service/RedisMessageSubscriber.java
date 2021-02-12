package com.redisdeveloper.basicchat.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Service;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

@Service
public class RedisMessageSubscriber implements MessageListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(RedisMessageSubscriber.class);

    CopyOnWriteArrayList<Function<String, Integer>> handlers = new CopyOnWriteArrayList<>();

    @Override
    public void onMessage(final Message message, final byte[] pattern) {
        String messageBody = new String(message.getBody());

        LOGGER.debug("Received message in global subscriber: "+ message.toString());

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