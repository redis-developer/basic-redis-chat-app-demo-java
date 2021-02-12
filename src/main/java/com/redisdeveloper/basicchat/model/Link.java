package com.redisdeveloper.basicchat.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Link {

    private String github;

    public Link() {
        this.github = "http://google.com";
    }
}