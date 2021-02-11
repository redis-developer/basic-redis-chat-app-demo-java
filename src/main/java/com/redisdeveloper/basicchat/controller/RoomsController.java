package com.redisdeveloper.basicchat.controller;

import com.google.gson.Gson;
import com.redisdeveloper.basicchat.model.Message;
import com.redisdeveloper.basicchat.model.Room;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

import static com.redisdeveloper.basicchat.config.RedisTemplateKeys.*;


@RestController
@RequestMapping("/rooms")
public class RoomsController {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    private StringRedisTemplate redisTemplate;

    /**
     * Get rooms for specific user id.
     */
    @GetMapping(value = "user/{userId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Room>> getRooms(@PathVariable int userId) {
        String userRoomsKey = String.format(USER_ROOMS_KEY, userId);
        Set<String> roomIds = redisTemplate.opsForSet().members(userRoomsKey);
        if (roomIds == null) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        List<Room> rooms = new ArrayList<>();

        for (String roomId : roomIds) {
            boolean roomExists = redisTemplate.hasKey(String.format("room:%s", roomId));
            if (roomExists){
                String roomNameKey = String.format(ROOM_NAME_KEY, roomId);
                String name = redisTemplate.opsForValue().get(roomNameKey);
                if (name == null) {
                    // private chat case
                    String[] userIds = parseUserIds(roomId);
                    if (userIds == null){
                        return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
                    }
                    Room privateRoom = createPrivateRoom(roomId, userIds);
                    rooms.add(privateRoom);
                } else {
                    rooms.add(new Room(roomId, name));
                }
            }
        }
        return new ResponseEntity<>(rooms, HttpStatus.OK);
    }

    private String[] parseUserIds(String roomId){
        String[] userIds = roomId.split(":");
        if (userIds.length != 2){
            return null;
        }
        return userIds;
    }

    private Room createPrivateRoom(String roomId, String[] userIds){
        String firstUserIdKey = String.format(USER_ID_KEY, userIds[0]);
        String secondUserIdKey = String.format(USER_ID_KEY, userIds[1]);
        String firstUsername = (String) redisTemplate.opsForHash().get(firstUserIdKey, USERNAME_HASH_KEY);
        String secondUsername = (String) redisTemplate.opsForHash().get(secondUserIdKey, USERNAME_HASH_KEY);
        return new Room(roomId, firstUsername, secondUsername);
    }

    /**
     * Get Messages.
     */
    @GetMapping(value = "messages/{roomId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Message>> getMessages(@PathVariable String roomId, @RequestParam int offset, @RequestParam int size) {
        String roomKey = String.format(ROOM_KEY, roomId);
        boolean roomExists = redisTemplate.hasKey(roomKey);
        List<Message> messages = new ArrayList<>();
        if (roomExists) {
            Set<String> values = redisTemplate.opsForZSet().reverseRange(roomKey, offset, offset + size);
            for (String value : values) {
                messages.add(deserialize(value));
            }
        }
        return new ResponseEntity<>(messages, HttpStatus.OK);
    }

    private Message deserialize(String value){
        Gson gson = new Gson();
        try {
            return gson.fromJson(value, Message.class);
        } catch (Exception e) {
            LOGGER.error(String.format("Couldn't deserialize json: %s", value), e);
        }
        return null;
    }
}
