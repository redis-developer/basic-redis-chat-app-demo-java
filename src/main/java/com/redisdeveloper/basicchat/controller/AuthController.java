package com.redisdeveloper.basicchat.controller;

import com.google.gson.Gson;
import com.redisdeveloper.basicchat.config.SessionAttrs;
import com.redisdeveloper.basicchat.model.LoginData;
import com.redisdeveloper.basicchat.model.User;
import com.redisdeveloper.basicchat.repository.UsersRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.ui.Model;

import javax.servlet.http.HttpSession;


@RestController
@RequestMapping("/auth")
public class AuthController {
    @Autowired
    private UsersRepository usersRepository;

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthController.class);

    /**
     * Create user session by username and password.
     */
    @PostMapping(value = "/login")
    public ResponseEntity<User> login(@RequestBody LoginData loginData, HttpSession session) {
        String username = loginData.getUsername();

        boolean userExists = usersRepository.isUserExists(username);
        if (!userExists) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        User user = usersRepository.getUserByName(username);
        if (user == null){
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        user.setOnline(true);

        Gson gson = new Gson();
        session.setAttribute(SessionAttrs.USER_ATTR_NAME, gson.toJson(user));
        LOGGER.info("Sign in user: "+user.getUsername());

        return new ResponseEntity<>(user, HttpStatus.OK);
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
