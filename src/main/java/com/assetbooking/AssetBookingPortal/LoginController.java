package com.assetbooking.AssetBookingPortal;

import jakarta.servlet.http.HttpSession;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class LoginController {

    private final UserRepository userRepository;

    private final BCryptPasswordEncoder passwordEncoder =
            new BCryptPasswordEncoder();

    public LoginController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // Login
    @PostMapping("/login")
    public String login(
            @RequestParam String email,
            @RequestParam String password,
            HttpSession session) {

        String cleanEmail = email.trim().toLowerCase();

        // Find user using email
        User user = userRepository.findByEmail(cleanEmail).orElse(null);

        // Check user and password
        if (user == null ||
                !passwordEncoder.matches(password, user.getPassword())) {

            return "redirect:/?error=invalidCredentials";
        }

        // Login successful: save user details in session
        session.setAttribute("displayName", user.getName());
        session.setAttribute("userEmail", user.getEmail());

        System.out.println("========== LOGIN DEBUG ==========");
        System.out.println("Session ID: " + session.getId());
        System.out.println("Display Name: " + session.getAttribute("displayName"));
        System.out.println("User Email: " + session.getAttribute("userEmail"));
        System.out.println("=================================");

        return "redirect:/dashboard";
    }
}