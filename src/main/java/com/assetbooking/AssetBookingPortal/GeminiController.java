package com.assetbooking.AssetBookingPortal;

import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.fasterxml.jackson.databind.JsonNode;

@RestController
public class GeminiController {

    private final GeminiService geminiService;
    private final AiBookingService aiBookingService;

    public GeminiController(
            GeminiService geminiService,
            AiBookingService aiBookingService) {
        this.geminiService = geminiService;
        this.aiBookingService = aiBookingService;
    }

    // Test Gemini API
    @PostMapping("/ai-booking/test")
    public ResponseEntity<String> testGemini(
            @RequestParam String prompt) {
        try {
            String response = geminiService.understandBooking(prompt);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                    .body("Gemini request failed: " + e.getMessage());
        }
    }

    // Parse booking details without saving
    @PostMapping(
        value = "/ai-booking/parse",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<String> parseBooking(
            @RequestBody Map<String, String> request) {
        try {
            String prompt = request.get("prompt");

            if (prompt == null || prompt.isBlank()) {
                return ResponseEntity.badRequest()
                        .body("{\"error\":\"Please enter a booking request.\"}");
            }

            JsonNode booking = geminiService.extractBookingDetails(prompt);

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(booking.toString());

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"error\":\"Invalid booking request.\"}");

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"error\":\"Could not parse booking request.\"}");
        }
    }

    // Create a booking and save it to MySQL
    @PostMapping(
        value = "/ai-booking/create",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<Map<String, Object>> createBooking(
            @RequestBody Map<String, String> request) {
        try {
            String prompt = request.get("prompt");
            String userIdText = request.get("userId");

            if (prompt == null || prompt.isBlank()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Please enter a booking request."));
            }

            if (userIdText == null || userIdText.isBlank()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Please provide a userId."));
            }

            Long userId = Long.parseLong(userIdText);

            Booking booking = aiBookingService.createBooking(prompt, userId);

            return ResponseEntity.ok(Map.of(
                    "message", "Booking created successfully!",
                    "bookingId", booking.getBookingId(),
                    "assetName", booking.getAsset().getAssetName(),
                    "assetType", booking.getAsset().getAssetType(),
                    "bookingDate", booking.getBookingDate().toString(),
                    "startTime", booking.getStartTime().toString(),
                    "endTime", booking.getEndTime().toString(),
                    "totalRent", booking.getTotalRent(),
                    "status", booking.getStatus()
            ));

        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "userId must be a valid number."));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Booking could not be created."));
        }
    }
}