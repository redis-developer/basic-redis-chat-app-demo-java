package com.redisdeveloper.basicchat.controller;

import com.google.gson.Gson;
import com.redisdeveloper.basicchat.model.LoginData;
import com.redisdeveloper.basicchat.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpSession;


@RestController
@RequestMapping("/auth")
public class AuthController {
    @Autowired
    private StringRedisTemplate redisTemplate;

    /**
     * Create user session by username and password.
     */
    @RequestMapping(value = "/login", method = RequestMethod.POST)
    public ResponseEntity<User> login(@RequestBody LoginData loginData, HttpSession session) {
        String username = loginData.getUsername();

        String usernameKey = String.format("username:%s", username);
        boolean userExists = redisTemplate.hasKey(usernameKey);
        if (!userExists) {
            return ResponseEntity.status(404).build();
        }

        String userKey = redisTemplate.opsForValue().get(usernameKey);
        assert userKey != null;
        String[] userIds = userKey.split(":");
        String userId = userIds[userIds.length - 1];

        // We have all the info needed to store the valid user object into session.
        User newUser = new User(Integer.parseInt(userId), username, true);

        Gson gson = new Gson();
        session.setAttribute("user", gson.toJson(newUser));

        return new ResponseEntity<>(newUser, HttpStatus.OK);
    }

    /**
     * Dispose the user session.
     */
    @RequestMapping(value = "/logout", method = RequestMethod.POST)
    public ResponseEntity<Object> logout(Model model, HttpSession session) {
        session.removeAttribute("user");
        return ResponseEntity.status(200).build();
    }
}
