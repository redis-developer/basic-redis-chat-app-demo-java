package com.redisdeveloper.basicchat.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

@RestController
public class RootController {
    @RequestMapping("/")
    public RedirectView main() {
        return new RedirectView("/index.html");
    }
}
