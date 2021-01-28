package com.example.springboot;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import javax.servlet.http.HttpSession;

@RestController
public class DiscoController {

	@RequestMapping("/disco")
	public String index(Model model, HttpSession session) {
		int users = 0;
		Object sessionValue = session.getAttribute("User");
		if (sessionValue != null) {
			users = (int) sessionValue;
		}

		session.setAttribute("User", ++users);

		String usersFormatRequest = String.format("Meowowow %d", users);

		return usersFormatRequest;
	}
}
