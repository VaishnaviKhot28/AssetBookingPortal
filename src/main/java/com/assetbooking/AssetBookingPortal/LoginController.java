package com.assetbooking.AssetBookingPortal;

import jakarta.servlet.http.HttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
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

    @PostMapping("/login")
    public String login(
            @RequestParam("email") String email,
            @RequestParam("password") String password,
            HttpSession session,
            Model model) {

        if (email == null || email.isBlank()
                || password == null || password.isBlank()) {
            return "redirect:/";
        }

        return userRepository.findByEmail(email.trim())
                .filter(user ->
                        user.getPassword() != null
                        && passwordEncoder.matches(
                                password, user.getPassword()))
                .map(user -> {
                    String[] parts = user.getName().trim().split("\\s+");

                    String initials = parts[0].substring(0, 1)
                            .toUpperCase();

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

    @PostMapping("/register")
    public String register(
            @RequestParam("fullName") String fullName,
            @RequestParam("email") String email,
            @RequestParam("password") String password) {

        if (fullName == null || fullName.isBlank()
                || email == null || email.isBlank()
                || password == null || password.isBlank()) {
            return "redirect:/?error=empty";
        }

        String cleanEmail = email.trim().toLowerCase();

        if (userRepository.existsByEmail(cleanEmail)) {
            return "redirect:/?error=emailExists";
        }

        User user = new User();
        user.setName(fullName.trim());
        user.setEmail(cleanEmail);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole("USER");

        userRepository.save(user);

        return "redirect:/?registered=true";
    }
}