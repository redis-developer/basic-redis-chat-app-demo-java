package com.redisdeveloper.basicchat.controller;

import com.google.gson.Gson;
import com.redisdeveloper.basicchat.model.Message;
import com.redisdeveloper.basicchat.model.Room;
import com.redisdeveloper.basicchat.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.*;


@RestController
@RequestMapping("/rooms")
public class RoomsController {

    @Autowired
    private StringRedisTemplate redisTemplate;

    /**
     * Get rooms for specific user id.
     */
    @RequestMapping(value = "user/{userId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Room[]> getRooms(@PathVariable int userId) {
        var roomIds = redisTemplate.opsForSet().members(String.format("user:%d:rooms", userId));
        if (roomIds == null) {
            return (ResponseEntity<Room[]>) ResponseEntity.status(400);
        }
        var rooms = new LinkedList<Room>();

        for (String roomId : roomIds) {
            String name = redisTemplate.opsForValue().get(String.format("room:%s:name", roomId));
            if (name == null) {
                // It's a room without a name, likey the one with private messages
                var roomExists = redisTemplate.hasKey(String.format("room:%s", roomId));
                if (!roomExists) {
                    continue;
                }

                var userIds = roomId.split(":");
                if (userIds.length != 2) {
                    return ResponseEntity.status(400).build();
                }
                rooms.add(new Room(roomId,
                        (String) redisTemplate.opsForHash().get(String.format("user:%s", userIds[0]), "username"),
                        (String) redisTemplate.opsForHash().get(String.format("user:%s", userIds[1]), "username")
                ));
            } else {
                rooms.add(new Room(roomId, name));
            }
        }

        return new ResponseEntity(rooms.toArray(), HttpStatus.OK);
    }

    /**
     * Get Messages.
     */
    @RequestMapping(value = "messages/{roomId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Message[]> getMessages(@PathVariable String roomId, @RequestParam int offset, @RequestParam int size) {
        var roomKey = String.format("room:%s", roomId);
        var roomExists = redisTemplate.hasKey(roomKey);
        var messages = new LinkedList<Message>();

        if (roomExists) {
            var gson = new Gson();

            var values = redisTemplate.opsForZSet().reverseRange(roomKey, offset, offset + size);

            for (var value : values) {
                try {
                    messages.add(gson.fromJson(value, Message.class));
                } catch (Exception e) {
                    System.out.println(String.format("Couldn't deserialize json: %s", value));
                }
            }
        }
        return new ResponseEntity(messages.toArray(), HttpStatus.OK);
    }
}
