package com.assetbooking.AssetBookingPortal;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.servlet.http.HttpSession;

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
            String response =
                    geminiService.understandBooking(prompt);

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

            JsonNode booking =
                    geminiService.extractBookingDetails(prompt);

            return ResponseEntity.ok(booking.toString());

        } catch (Exception e) {
            e.printStackTrace();

            return ResponseEntity.badRequest()
                    .body("{\"error\":\"Could not parse booking request.\"}");
        }
    }

    // STEP 1: Check availability. No booking is saved.
    @PostMapping(
            value = "/ai-booking/check",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<Map<String, Object>> checkAvailability(
            @RequestBody Map<String, String> request,
            HttpSession session) {

        try {
            String prompt = request.get("prompt");

            String userEmail =
                    (String) session.getAttribute("userEmail");

            if (userEmail == null || userEmail.isBlank()) {
                return ResponseEntity.status(401)
                        .body(Map.of(
                                "error",
                                "Your session has expired. Please login again."
                        ));
            }

            if (prompt == null || prompt.isBlank()) {
                return ResponseEntity.badRequest()
                        .body(Map.of(
                                "error",
                                "Please enter a booking request."
                        ));
            }

            Map<String, Object> result =
                    aiBookingService.checkAvailability(prompt);

            return ResponseEntity.ok(result);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            e.printStackTrace();

            return ResponseEntity.internalServerError()
                    .body(Map.of(
                            "error",
                            "Could not check asset availability."
                    ));
        }
    }

    // STEP 2: Confirm booking after Book Now click
    @PostMapping(
            value = "/ai-booking/confirm",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<Map<String, Object>> confirmBooking(
            @RequestBody Map<String, Object> request,
            HttpSession session) {

        try {
            String userEmail =
                    (String) session.getAttribute("userEmail");

            if (userEmail == null || userEmail.isBlank()) {
                return ResponseEntity.status(401)
                        .body(Map.of(
                                "error",
                                "Your session has expired. Please login again."
                        ));
            }

            Long assetId =
                    Long.valueOf(request.get("assetId").toString());

            String bookingDate =
                    request.get("bookingDate").toString();

            String startTime =
                    request.get("startTime").toString();

            String endTime =
                    request.get("endTime").toString();

            Booking booking = aiBookingService.confirmBooking(
                    assetId,
                    bookingDate,
                    startTime,
                    endTime,
                    userEmail
            );

            Map<String, Object> result = new HashMap<>();

            result.put("message", "Booking confirmed successfully!");
            result.put("bookingId", booking.getBookingId());
            result.put("assetName", booking.getAsset().getAssetName());
            result.put("assetType", booking.getAsset().getAssetType());
            result.put("bookingDate", booking.getBookingDate().toString());
            result.put("startTime", booking.getStartTime().toString());
            result.put("endTime", booking.getEndTime().toString());
            result.put("totalRent", booking.getTotalRent());
            result.put("status", booking.getStatus());

            return ResponseEntity.ok(result);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            e.printStackTrace();

            return ResponseEntity.internalServerError()
                    .body(Map.of(
                            "error",
                            "Booking could not be confirmed."
                    ));
        }
    }
}