
package com.redisdeveloper.basicchat.controller;

import com.redisdeveloper.basicchat.model.Link;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;


@RestController
public class LinksController {
    @GetMapping(value = "/links", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Link> getMe() {
        return new ResponseEntity<>(new Link(), HttpStatus.OK);
    }
}
