package com.redisdeveloper.basicchat.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class User {
    private int id;
    private String username;
    private boolean isOnline;

    public User(int id, String username, boolean isOnline) {
        this.id = id;
        this.username = username;
        this.isOnline = isOnline;
    }

}
