package com.redisdeveloper.basicchat.model;

public class PubSubMessage<T> {
    public String type;
    public T data;

    public PubSubMessage(String type, T data) {
        this.type = type;
        this.data = data;
    }
}
