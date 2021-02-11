package com.redisdeveloper.basicchat.controller;

import com.google.gson.Gson;
import com.redisdeveloper.basicchat.config.RedisAppConfig;
import com.redisdeveloper.basicchat.config.RedisTemplateKeys;
import com.redisdeveloper.basicchat.config.SessionAttrs;
import com.redisdeveloper.basicchat.model.LoginData;
import com.redisdeveloper.basicchat.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.ui.Model;

import javax.servlet.http.HttpSession;
import java.util.Objects;


@RestController
@RequestMapping("/auth")
public class AuthController {
    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthController.class);

    /**
     * Create user session by username and password.
     */
    @PostMapping(value = "/login")
    public ResponseEntity<User> login(@RequestBody LoginData loginData, HttpSession session) {
        String username = loginData.getUsername();

        String usernameKey = String.format(RedisTemplateKeys.USERNAME_KEY, username);
        boolean userExists = redisTemplate.hasKey(usernameKey);
        if (!userExists) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        String userKey = redisTemplate.opsForValue().get(usernameKey);
        int userId = parseUserId(Objects.requireNonNull(userKey));

        // We have all the info needed to store the valid user object into session.
        User newUser = new User(userId, username, true);

        Gson gson = new Gson();
        session.setAttribute(SessionAttrs.USER_ATTR_NAME, gson.toJson(newUser));
        LOGGER.info("Sign in user: "+newUser.getUsername());

        return new ResponseEntity<>(newUser, HttpStatus.OK);
    }

    private int parseUserId(String userKey){
        String[] userIds = userKey.split(":");
        return Integer.parseInt(userIds[userIds.length - 1]);
    }

    /**
     * Dispose the user session.
     */
    @PostMapping(value = "/logout")
    public ResponseEntity<Object> logout(Model model, HttpSession session) {
        Object user = session.getAttribute(SessionAttrs.USER_ATTR_NAME);
        LOGGER.info("Sign out user: "+user.toString());

        session.removeAttribute(SessionAttrs.USER_ATTR_NAME);
        return ResponseEntity.status(HttpStatus.OK).build();
    }
}
