package com.assetbooking.AssetBookingPortal;

public class GeminiConfigTest {

    public static void main(String[] args) {

        String apiKey = System.getenv("GEMINI_API_KEY");

        if (apiKey != null && !apiKey.isBlank()) {
            System.out.println("Gemini API Key is configured!");
        } else {
            System.out.println("Gemini API Key is missing!");
        }
    }
}