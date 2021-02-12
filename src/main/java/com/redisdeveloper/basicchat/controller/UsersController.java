package com.redisdeveloper.basicchat.controller;

import com.google.gson.Gson;
import com.redisdeveloper.basicchat.config.SessionAttrs;
import com.redisdeveloper.basicchat.model.User;
import com.redisdeveloper.basicchat.repository.UsersRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpSession;
import java.util.*;
import java.util.stream.Collectors;


@RestController
@RequestMapping("/users")
public class UsersController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChatController.class);

    @Autowired
    private UsersRepository usersRepository;

    /**
     * The request the client sends to check if it has the user is cached.
     */
    @RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, User>> get(@RequestParam(value = "ids") String idsString) {
        Set<Integer> ids = parseIds(idsString);

        Map<String, User> usersMap = new HashMap<>();

        for (Integer id : ids) {
            User user = usersRepository.getUserById(id);
            if (user == null){
                LOGGER.debug("User not found by id: "+id);
                return new ResponseEntity<>(new HashMap<>(), HttpStatus.BAD_REQUEST);
            }
            usersMap.put(String.valueOf(user.getId()), user);
        }

        return new ResponseEntity<>(usersMap, HttpStatus.OK);
    }

    private Set<Integer> parseIds(String idsString){
        return Arrays.stream(idsString.split(","))
                .map(Integer::parseInt)
                .collect(Collectors.toSet());
    }

    /**
     * The request the client sends to check if it has the user is cached.
     */
    @RequestMapping(value = "/me", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<User> getMe(Model model, HttpSession session) {
        String user = (String) session.getAttribute(SessionAttrs.USER_ATTR_NAME);
        if (user == null){
            LOGGER.debug("User not found in session by attribute: "+SessionAttrs.USER_ATTR_NAME);
            return new ResponseEntity<>(null, HttpStatus.OK);
        }
        Gson gson = new Gson();
        return new ResponseEntity<>(gson.fromJson(user, User.class), HttpStatus.OK);
    }

    /**
     * user
     * Check which users are online.
     */
    @RequestMapping(value = "/online", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, User>> getOnline() {
        Map<String, User> usersMap = new HashMap<>();
        Set<Integer> onlineIds = usersRepository.getOnlineUsersIds();
        if (onlineIds == null){
            LOGGER.debug("No online users found!");
            return new ResponseEntity<>(new HashMap<>(), HttpStatus.OK);
        }

        for (Integer onlineId : onlineIds) {
            User user = usersRepository.getUserById(onlineId);
            if (user == null){
                LOGGER.debug("User not found by id: "+onlineId);
                return new ResponseEntity<>(new HashMap<>(), HttpStatus.INTERNAL_SERVER_ERROR);
            }
            usersMap.put(String.valueOf(user.getId()), user);
        }

        return new ResponseEntity<>(usersMap, HttpStatus.OK);
    }
}
