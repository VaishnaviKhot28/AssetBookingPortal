package com.assetbooking.AssetBookingPortal;

import jakarta.servlet.http.HttpSession;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
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
            @RequestParam("email") String email,
            @RequestParam("password") String password,
            HttpSession session) {

        if (email == null || email.isBlank()
                || password == null || password.isBlank()) {
            return "redirect:/?error=empty";
        }

        return userRepository.findByEmail(email.trim().toLowerCase())
                .filter(user ->
                        user.getPassword() != null
                        && passwordEncoder.matches(
                                password, user.getPassword()))
                .map(user -> {

                    String name = user.getName();

                    if (name == null || name.isBlank()) {
                        name = "User";
                    }

                    String[] parts = name.trim().split("\\s+");

                    String initials =
                            parts[0].substring(0, 1).toUpperCase();

                    if (parts.length > 1) {
                        initials += parts[parts.length - 1]
                                .substring(0, 1).toUpperCase();
                    }

                    session.setAttribute("displayName", initials);
                    session.setAttribute("userEmail", user.getEmail());

                    return "redirect:/dashboard";
                })
                .orElse("redirect:/?error=invalid");
    }

    // Register New User
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

        if (password.length() < 8) {
            return "redirect:/register?error=weakPassword";
        }

        String cleanName = fullName.trim();
        String cleanEmail = email.trim().toLowerCase();

        if (userRepository.existsByEmail(cleanEmail)) {
            return "redirect:/register?error=emailExists";
        }

        User user = new User();

        user.setName(cleanName);
        user.setEmail(cleanEmail);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole("USER");

        userRepository.save(user);

        return "redirect:/?registered=true";
    }

}
