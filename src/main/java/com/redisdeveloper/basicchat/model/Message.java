package com.redisdeveloper.basicchat.model;

public class Message {
    public String from;
    public int date;
    public String message;
    public String roomId;

    public Message(String from, int date, String message, String roomId) {
        this.from = from;
        this.date = date;
        this.message = message;
        this.roomId = roomId;
    }
}
