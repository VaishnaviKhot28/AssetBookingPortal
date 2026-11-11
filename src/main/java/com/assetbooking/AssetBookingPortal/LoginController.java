
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

    // USER LOGIN
    @PostMapping("/login")
    public String login(
            @RequestParam("email") String email,
            @RequestParam("password") String password,
            HttpSession session) {

        if (email == null || email.isBlank()
                || password == null || password.isBlank()) {
            return "redirect:/?error=empty";
        }

        String cleanEmail = email.trim().toLowerCase();

        User user = userRepository
                .findByEmail(cleanEmail)
                .orElse(null);

        if (user == null || user.getPassword() == null
                || !passwordEncoder.matches(
                        password, user.getPassword())) {

            System.out.println("LOGIN FAILED: Invalid credentials");
            return "redirect:/?error=invalid";
        }

        // Only USER can login through this method
        if (!"USER".equalsIgnoreCase(user.getRole())) {
            System.out.println("LOGIN FAILED: Invalid role");
            return "redirect:/?error=invalid";
        }

        String[] parts = user.getName().trim().split("\\s+");

        String initials = parts[0].substring(0, 1).toUpperCase();

        if (parts.length > 1) {
            initials += parts[parts.length - 1]
                    .substring(0, 1).toUpperCase();
        }

        // CREATE USER SESSION
        session.setAttribute("displayName", initials);
        session.setAttribute("userEmail", user.getEmail());
        session.setAttribute("userRole", "USER");

        // DEBUG LOGS
        System.out.println("LOGIN SUCCESS");
        System.out.println("Login displayName: "
                + session.getAttribute("displayName"));
        System.out.println("Login role: "
                + session.getAttribute("userRole"));
        System.out.println("Session ID: " + session.getId());

        return "redirect:/dashboard";
    }

    // USER REGISTRATION
    @PostMapping("/register")
    public String register(
            @RequestParam("fullName") String fullName,
            @RequestParam("email") String email,
            @RequestParam("password") String password) {

        if (fullName == null || fullName.isBlank()
                || email == null || email.isBlank()
                || password == null || password.isBlank()) {

            return "redirect:/register?error=empty";
        }

        String cleanName = fullName.trim();
        String cleanEmail = email.trim().toLowerCase();

        if (password.length() < 6) {
            return "redirect:/register?error=weakPassword";
        }

        if (userRepository.existsByEmail(cleanEmail)) {
            return "redirect:/register?error=emailExists";
        }

        User user = new User();

        user.setName(cleanName);
        user.setEmail(cleanEmail);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole("USER");

        userRepository.save(user);

        System.out.println("USER REGISTERED: " + cleanEmail);

        return "redirect:/?registered=true";
    }
}
