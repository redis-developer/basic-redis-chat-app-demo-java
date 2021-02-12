package com.redisdeveloper.basicchat.controller;

import com.google.gson.Gson;
import com.redisdeveloper.basicchat.model.*;
import com.redisdeveloper.basicchat.repository.RoomsRepository;
import com.redisdeveloper.basicchat.repository.UsersRepository;
import com.redisdeveloper.basicchat.service.RedisMessageSubscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;


@RestController
@RequestMapping("/chat")
public class ChatController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChatController.class);
    @Autowired
    private UsersRepository usersRepository;

    @Autowired
    private RoomsRepository roomsRepository;

    @Autowired
    ChannelTopic topic;

    @Autowired
    MessageListenerAdapter messageListener;

    @RequestMapping("/stream")
    public SseEmitter streamSseMvc(@RequestParam int userId) {
        AtomicBoolean isComplete = new AtomicBoolean(false);
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
        RedisMessageSubscriber redisMessageSubscriber = (RedisMessageSubscriber) messageListener.getDelegate();
        redisMessageSubscriber.attach(handler);

        // Make sure all life-time methods are covered here and remove the handler from the global subscriber.
        Runnable onDetach = () -> {
            redisMessageSubscriber.detach(handler);
            if (!isComplete.get()) {
                isComplete.set(true);
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
        String serializedMessage;

        LOGGER.info("Received message: "+chatMessage.toString());

        if (chatMessage.getType() == MessageType.MESSAGE) {
            serializedMessage = handleRegularMessageCase(chatMessage);
        } else if (chatMessage.getType() == MessageType.USER_CONNECTED
                || chatMessage.getType() == MessageType.USER_DISCONNECTED) {
            // User-related events cover connection cases.
            serializedMessage = handleUserConnectionCase(chatMessage);
        } else {
            // This is an unknown message type. For those we just send the raw string in the data parameter.
            serializedMessage = gson.toJson(new PubSubMessage<>(chatMessage.getType().value(), chatMessage.getData()));
        }

        // Finally, send the serialized json to Redis.
        roomsRepository.sendMessageToRedis(topic.getTopic(), serializedMessage);

        return ResponseEntity.status(HttpStatus.OK).build();
    }

    private String handleRegularMessageCase(ChatControllerMessage chatMessage){
        Gson gson = new Gson();
        // We've received a message from user. It's necessary to deserialize it first.
        Message message = gson.fromJson(chatMessage.getData(), Message.class);
        // Add the user who sent the message to online list.
        usersRepository.addUserToOnlineList(message.getFrom());
        //redisTemplate.opsForSet().add(ONLINE_USERS_KEY, message.getFrom());
        // Write the message to DB.
        roomsRepository.saveMessage(message);
        // Finally create the serialized output which would go to pub/sub
        return gson.toJson(new PubSubMessage<>(chatMessage.getType().value(), message));
    }

    private String handleUserConnectionCase(ChatControllerMessage chatMessage){
        Gson gson = new Gson();
        int userId = chatMessage.getUser().getId();
        String messageType = chatMessage.getType().value();
        User serializedUser = gson.fromJson(chatMessage.getData(), User.class);
        String serializedMessage = gson.toJson(new PubSubMessage<>(messageType, serializedUser));
        if (chatMessage.getType() == MessageType.USER_CONNECTED) {
            usersRepository.addUserToOnlineList(String.valueOf(userId));
        } else {
            usersRepository.removeUserFromOnlineList(String.valueOf(userId));
        }
        return serializedMessage;
    }
}
