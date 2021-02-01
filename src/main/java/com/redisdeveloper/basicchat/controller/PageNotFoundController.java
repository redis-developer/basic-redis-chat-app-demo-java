package com.redisdeveloper.basicchat.controller;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.NoHandlerFoundException;
import javax.servlet.http.HttpServletRequest;

@ControllerAdvice
public class PageNotFoundController {
    @ExceptionHandler(NoHandlerFoundException.class)
    public String handleError404() {
        return "redirect:/index.html";
    }
}