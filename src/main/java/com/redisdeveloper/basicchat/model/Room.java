package com.redisdeveloper.basicchat.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Room {
    private String id;
    private String[] names;

    public Room(String id, String name) {
        this.id = id;
        this.names = new String[1];
        this.names[0] = name;
    }

    public Room(String id, String name1, String name2) {
        this.id = id;
        this.names = new String[2];
        this.names[0] = name1;
        this.names[1] = name2;
    }
}
