package com.redisdeveloper.basicchat.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Message {
    private String from;
    private int date;
    private String message;
    private String roomId;

    public Message(String from, int date, String message, String roomId) {
        this.from = from;
        this.date = date;
        this.message = message;
        this.roomId = roomId;
    }
}
