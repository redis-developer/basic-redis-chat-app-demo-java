package com.redisdeveloper.basicchat.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChatControllerMessage {
    private MessageType type;
    private User user;
    private String data;

}
