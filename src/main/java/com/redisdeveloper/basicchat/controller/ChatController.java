package com.redisdeveloper.basicchat.controller;

import com.google.gson.Gson;
import com.redisdeveloper.basicchat.model.*;
import com.redisdeveloper.basicchat.service.RedisMessageSubscriber;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.function.Function;


@RestController
@RequestMapping("/chat")
public class ChatController {
    @Autowired
    StringRedisTemplate redisTemplate;

    @Autowired
    ChannelTopic topic;

    @Autowired
    MessageListenerAdapter messageListener;

    @RequestMapping("/stream")
    public SseEmitter streamSseMvc(@RequestParam int userId) {
        var ref = new Object() {
            boolean isComplete = false;
        };
        SseEmitter emitter = new SseEmitter();
        Function<String, Integer> handler = (String message) -> {
            SseEmitter.SseEventBuilder event = SseEmitter.event()
                    .data(message);
            try {
                emitter.send(event);
            } catch (IOException e) {
                // This may occur when the client was disconnected.
                return 1;
            }
            return 0;
        };

        // RedisMessageSubscriber is a global class which subscribes to the "MESSAGES" channel
        // However once the /stream endpoint is invoked, it's necessary to notify the global subscriber
        // that such client-server subscription exists.
        //
        // We send the callback to the subscriber with the SSE instance for sending server-side events.
        var redisMessageSubscriber = (RedisMessageSubscriber) messageListener.getDelegate();
        assert redisMessageSubscriber != null;
        redisMessageSubscriber.attach(handler);

        // Make sure all life-time methods are covered here and remove the handler from the global subscriber.
        Runnable onDetach = () -> {
            redisMessageSubscriber.detach(handler);
            if (!ref.isComplete) {
                ref.isComplete = true;
                emitter.complete();
            }
        };

        emitter.onCompletion(onDetach);
        emitter.onError((err) -> onDetach.run());
        emitter.onTimeout(onDetach);

        return emitter;
    }

    /**
     * Receive incoming messages from the client...
     */
    @RequestMapping(value = "/emit", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> get(@RequestBody ChatControllerMessage chatMessage) {
        Gson gson = new Gson();

        String serialized;

        if (chatMessage.getType().equals("message")) {
            // We've received a message from user. It's necessary to deserialize it first.
            Message message = gson.fromJson(chatMessage.getData(), Message.class);
            // Add the user who sent the message to online list.
            redisTemplate.opsForSet().add("online_users", message.getFrom());
            // Write the message to DB.
            var roomKey = String.format("room:%s", message.getRoomId());
            redisTemplate.opsForZSet().add(roomKey, gson.toJson(message), message.getDate());
            // Finally create the serialized output which would go to pub/sub
            serialized = gson.toJson(new PubSubMessage<>(chatMessage.getType(), message));
        } else if (chatMessage.getType().startsWith("user.")) {
            // User-related events cover connection cases.
            serialized = gson.toJson(new PubSubMessage<>(chatMessage.getType(), gson.fromJson(chatMessage.getData(), User.class)));
            // Remove user from "online" set
            if (chatMessage.getType().equals("user.connected")) {
                redisTemplate.opsForSet().add("online_users", String.format("%d", chatMessage.getUser().getId()));
            } else {
                redisTemplate.opsForSet().remove("online_users", String.format("%d", chatMessage.getUser().getId()));
            }
        } else {
            // This is an unknown message type. For those we just send the raw string in the data parameter.
            serialized = gson.toJson(new PubSubMessage<>(chatMessage.getType(), chatMessage.getData()));
        }

        // Finally, send the serialized json to Redis.
        redisTemplate.convertAndSend(topic.getTopic(), serialized);
        return ResponseEntity.status(200).build();
    }
}
