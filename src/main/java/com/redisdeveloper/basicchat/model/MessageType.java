package com.redisdeveloper.basicchat.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum MessageType {
    MESSAGE("message"),
    USER_CONNECTED("user.connected"),
    USER_DISCONNECTED("user.disconnected");

    private final String value;

    MessageType(String value) {
        this.value = value;
    }

    @JsonValue
    public String value(){
        return this.value;
    }
}
