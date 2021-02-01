package com.redisdeveloper.basicchat.model;

public class User {
    public int id;
    public String username;
    public boolean online;


    public User(int id, String username, boolean online) {
        this.id = id;
        this.username = username;
        this.online = online;
    }

}
