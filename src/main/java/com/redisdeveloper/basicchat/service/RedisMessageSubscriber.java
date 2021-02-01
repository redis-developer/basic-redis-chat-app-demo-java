package com.redisdeveloper.basicchat.service;

import com.redisdeveloper.basicchat.util.Utils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

@Service
public class RedisMessageSubscriber implements MessageListener {
    @Autowired
    private StringRedisTemplate redisTemplate;

    @PostConstruct
    void init() {
        try {
            Utils.createDemoData(redisTemplate);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void onMessage(final Message message, final byte[] pattern) {
        var messageBody = new String(message.getBody());

        for (var handler : handlers) {
            handler.apply(messageBody);
        }
    }

    Set<Function<String, Integer>> handlers = new HashSet<>();

    public void attach(Function<String, Integer> handler) {
        handlers.add(handler);
    }

    public void detach(Function<String, Integer> handler) {
        handlers.remove(handler);
    }
}