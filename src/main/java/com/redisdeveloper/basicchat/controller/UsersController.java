package com.redisdeveloper.basicchat.controller;

import com.google.gson.Gson;
import com.redisdeveloper.basicchat.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpSession;
import java.util.Arrays;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.stream.Collectors;


@RestController
@RequestMapping("/users")
public class UsersController {
    @Autowired
    private StringRedisTemplate redisTemplate;

    /**
     * The request the client sends to check if it has the user is cached.
     */
    @RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Dictionary<String, User>> get(@RequestParam(value = "ids") String idsString) {
        Dictionary<String, User> users = Arrays.stream(idsString.split(","))
                .map(Integer::parseInt)
                .distinct()
                .map(id ->
                        new User(
                                id,
                                (String) redisTemplate.opsForHash().get(
                                        String.format("user:%s", id.toString()),
                                        "username"),
                                redisTemplate.opsForSet().isMember("online_users", id.toString())
                        )
                )
                .collect(Collectors.toMap(u -> String.format("%s", u.getId()), u -> u,
                        (u, v) -> {
                            throw new IllegalStateException(
                                    String.format("Cannot have 2 values (%s, %s) for the same key", u, v)
                            );
                        }, Hashtable::new
                ));

        return new ResponseEntity<>(users, HttpStatus.OK);
    }

    /**
     * The request the client sends to check if it has the user is cached.
     */
    @RequestMapping(value = "/me", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<User> getMe(Model model, HttpSession session) {
        String user = (String) session.getAttribute("user");
        if (user != null) {
            Gson gson = new Gson();
            return new ResponseEntity<>(gson.fromJson(user, User.class), HttpStatus.OK);
        }
        return new ResponseEntity<>(null, HttpStatus.OK);
    }

    /**
     * user
     * Check which users are online.
     */
    @RequestMapping(value = "/online", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Dictionary<String, User>> getOnline() {
        var onlineIds = redisTemplate.opsForSet().members("online_users");
        var users = new Hashtable<String, User>();

        for (var onlineId : onlineIds) {
            var username = (String) redisTemplate.opsForHash().get(
                    String.format("user:%s", onlineId), "username"
            );

            users.put(onlineId, new User(Integer.parseInt(onlineId), username, true));
        }

        return new ResponseEntity<>(users, HttpStatus.OK);
    }
}
