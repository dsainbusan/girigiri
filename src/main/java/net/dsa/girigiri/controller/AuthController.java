package net.dsa.girigiri.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@GetMapping("/auth")
public class AuthController {

	@GetMapping("/loginForm")
	public String loginForm() {
		return "authView/loginForm";
	}
}
