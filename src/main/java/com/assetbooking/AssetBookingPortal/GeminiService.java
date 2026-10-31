package com.assetbooking.AssetBookingPortal;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class GeminiService {
	
	private static final String API_URL =
	        "https://generativelanguage.googleapis.com/v1beta/models/"
	        + "gemini-3.5-flash-lite:generateContent";

private final HttpClient httpClient = HttpClient.newHttpClient();

private final ObjectMapper objectMapper = new ObjectMapper();

/**
 * Sends the user's booking request to Gemini and returns
 * the complete API response as a JSON string.
 */
public String understandBooking(String userPrompt) throws Exception {

    String apiKey = System.getenv("GEMINI_API_KEY");

    if (apiKey == null || apiKey.isBlank()) {
        throw new IllegalStateException(
                "GEMINI_API_KEY environment variable is missing."
        );
    }

    if (userPrompt == null || userPrompt.isBlank()) {
        throw new IllegalArgumentException(
                "Booking request cannot be empty."
        );
    }

    String today = LocalDate.now(
            ZoneId.of("Asia/Kolkata")
    ).toString();

    String prompt = """
            You are an assistant for an Asset Booking Portal.

            Today's date is %s.

            Extract booking information from the user's request.

            Return ONLY a valid JSON object with these fields:
            {
              "assetType": "projector",
              "bookingDate": "YYYY-MM-DD",
              "startTime": "HH:mm",
              "endTime": "HH:mm"
            }

            Rules:
            1. assetType must be one of:
               "Projector", "Laptop", "Meeting Room".
            2. Use YYYY-MM-DD for bookingDate.
            3. Use 24-hour HH:mm format for times.
            4. Resolve relative dates using today's date.
            5. Do not invent missing dates or times.
            6. If information is missing or unclear, return:
               {"error":"Please provide the missing booking details."}
            7. Return JSON only, without Markdown fences.

            User request:
            %s
            """.formatted(today, userPrompt);

    String requestBody = """
            {
              "contents": [
                {
                  "parts": [
                    {
                      "text": "%s"
                    }
                  ]
                }
              ],
              "generationConfig": {
                "responseMimeType": "application/json"
              }
            }
            """.formatted(escapeJson(prompt));

    HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(API_URL))
            .header("Content-Type", "application/json")
            .header("x-goog-api-key", apiKey)
            .POST(HttpRequest.BodyPublishers.ofString(
                    requestBody,
                    StandardCharsets.UTF_8
            ))
            .build();

    HttpResponse<String> response = httpClient.send(
            request,
            HttpResponse.BodyHandlers.ofString()
    );

    if (response.statusCode() < 200
            || response.statusCode() >= 300) {

        System.err.println("Gemini API HTTP Status: "
                + response.statusCode());

        System.err.println("Gemini API Error Response: "
                + response.body());

        throw new IllegalStateException(
                "Gemini API request failed. HTTP status: "
                + response.statusCode()
        );
    }

    return response.body();
}
/**
 * Extracts booking fields from Gemini's API response.
 */
public JsonNode extractBookingDetails(
        String userPrompt) throws Exception {

    String response = understandBooking(userPrompt);

    JsonNode root = objectMapper.readTree(response);

    JsonNode candidates = root.path("candidates");

    if (!candidates.isArray() || candidates.isEmpty()) {
        throw new IllegalStateException(
                "Gemini returned no candidates."
        );
    }

    JsonNode parts = candidates.path(0)
            .path("content")
            .path("parts");

    String responseText = null;

    if (parts.isArray()) {
        for (JsonNode part : parts) {
            JsonNode textNode = part.get("text");

            if (textNode != null && !textNode.asText().isBlank()) {
                responseText = textNode.asText();
                break;
            }
        }
    }

    if (responseText == null) {
        throw new IllegalStateException(
                "Gemini returned an empty response."
        );
    }

    String jsonText = responseText
            .replaceAll("^```json\\s*", "")
            .replaceAll("\\s*```$", "")
            .trim();

    JsonNode booking = objectMapper.readTree(jsonText);

    if (booking.has("error")) {
        throw new IllegalArgumentException(
                booking.path("error").asText()
        );
    }

    String assetType = requiredField(booking, "assetType");
    String bookingDate = requiredField(booking, "bookingDate");
    String startTime = requiredField(booking, "startTime");
    String endTime = requiredField(booking, "endTime");

    if (!assetType.equalsIgnoreCase("Projector")
            && !assetType.equalsIgnoreCase("Laptop")
            && !assetType.equalsIgnoreCase("Meeting Room")) {

        throw new IllegalArgumentException(
                "Unsupported asset type."
        );
    }

    LocalDate date = LocalDate.parse(bookingDate);

    java.time.LocalTime start =
            java.time.LocalTime.parse(startTime);

    java.time.LocalTime end =
            java.time.LocalTime.parse(endTime);

    if (!date.isAfter(LocalDate.now(
            ZoneId.of("Asia/Kolkata")))) {

        throw new IllegalArgumentException(
                "Booking date must be in the future."
        );
    }

    if (!end.isAfter(start)) {
        throw new IllegalArgumentException(
                "End time must be after start time."
        );
    }

    return booking;
}

private String requiredField(
        JsonNode booking,
        String fieldName) {

    JsonNode value = booking.get(fieldName);

    if (value == null || value.isNull()
            || value.asText().isBlank()) {

        throw new IllegalArgumentException(
                "Missing booking field: " + fieldName
        );
    }

    return value.asText().trim();
}

/**
 * Escapes text so it can safely be placed inside a JSON string.
 */
private String escapeJson(String value) {

    StringBuilder escaped = new StringBuilder();

    for (char c : value.toCharArray()) {
        switch (c) {
            case '"' -> escaped.append("\\\"");
            case '\\' -> escaped.append("\\\\");
            case '\n' -> escaped.append("\\n");
            case '\r' -> escaped.append("\\r");
            case '\t' -> escaped.append("\\t");
            default -> {
                if (c < 0x20) {
                    escaped.append(
                            String.format("\\u%04x", (int) c)
                    );
                } else {
                    escaped.append(c);
                }
            }
        }
    }

    return escaped.toString();
}

}