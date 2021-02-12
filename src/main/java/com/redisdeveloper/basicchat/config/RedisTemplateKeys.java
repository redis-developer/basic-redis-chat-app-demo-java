package com.redisdeveloper.basicchat.config;

public class RedisTemplateKeys {
    public static final String USERNAME_HASH_KEY = "username";
    public static final String USERNAME_KEY = "username:%s";
    public static final String USER_ID_KEY = "user:%s";
    public static final String USER_ROOMS_KEY = "user:%d:rooms";
    public static final String ROOM_NAME_KEY = "room:%s:name";
    public static final String ROOM_KEY = "room:%s";
    public static final String ONLINE_USERS_KEY = "online_users";
}
