
package com.redisdeveloper.basicchat.controller;

import com.redisdeveloper.basicchat.model.Links;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;


@RestController
public class LinksController {
    @RequestMapping(value = "/links", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Links> getMe() {
        return new ResponseEntity<>(new Links(), HttpStatus.OK);
    }
}