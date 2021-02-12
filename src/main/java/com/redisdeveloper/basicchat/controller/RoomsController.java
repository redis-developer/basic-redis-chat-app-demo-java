package com.redisdeveloper.basicchat.controller;

import com.google.gson.Gson;
import com.redisdeveloper.basicchat.model.Message;
import com.redisdeveloper.basicchat.model.Room;
import com.redisdeveloper.basicchat.model.User;
import com.redisdeveloper.basicchat.repository.RoomsRepository;
import com.redisdeveloper.basicchat.repository.UsersRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;


@RestController
@RequestMapping("/rooms")
public class RoomsController {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    private RoomsRepository roomsRepository;

    @Autowired
    private UsersRepository usersRepository;

    /**
     * Get rooms for specific user id.
     */
    @GetMapping(value = "user/{userId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Room>> getRooms(@PathVariable int userId) {
        Set<String> roomIds = roomsRepository.getUserRoomIds(userId);
        if (roomIds == null) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        List<Room> rooms = new ArrayList<>();

        for (String roomId : roomIds) {
            boolean roomExists = roomsRepository.isRoomExists(roomId);
            if (roomExists){
                String name = roomsRepository.getRoomNameById(roomId);
                if (name == null) {
                    // private chat case
                    Room privateRoom = handlePrivateRoomCase(roomId);
                    if (privateRoom == null){
                        return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
                    }
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
            LOGGER.error("User ids not parsed properly");
            throw new RuntimeException("Unable to parse users ids from roomId: "+roomId);
        }
        return userIds;
    }

    private Room handlePrivateRoomCase(String roomId){
        String[] userIds = parseUserIds(roomId);
        User firstUser = usersRepository.getUserById(Integer.parseInt(userIds[0]));
        User secondUser = usersRepository.getUserById(Integer.parseInt(userIds[1]));
        if (firstUser == null || secondUser == null){
            LOGGER.error("Users were not found by ids: "+ Arrays.toString(userIds));
            return null;
        }
        return new Room(roomId, firstUser.getUsername(), secondUser.getUsername());
    }

    /**
     * Get Messages.
     */
    @GetMapping(value = "messages/{roomId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Message>> getMessages(@PathVariable String roomId, @RequestParam int offset, @RequestParam int size) {
        boolean roomExists = roomsRepository.isRoomExists(roomId);
        List<Message> messages = new ArrayList<>();
        if (roomExists) {
            Set<String> values = roomsRepository.getMessages(roomId, offset, size);
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
