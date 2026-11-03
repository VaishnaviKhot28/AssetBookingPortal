package com.assetbooking.AssetBookingPortal;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;

@Service
public class AiBookingService {

    private final GeminiService geminiService;
    private final AssetRepository assetRepository;
    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;

    public AiBookingService(
            GeminiService geminiService,
            AssetRepository assetRepository,
            UserRepository userRepository,
            BookingRepository bookingRepository) {

        this.geminiService = geminiService;
        this.assetRepository = assetRepository;
        this.userRepository = userRepository;
        this.bookingRepository = bookingRepository;
    }

    // STEP 1: Check availability only. Do NOT save a booking here.
    public Map<String, Object> checkAvailability(String prompt)
            throws Exception {

        if (prompt == null || prompt.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Please enter your booking request.");
        }

        // Extract booking details using Gemini
        JsonNode details = geminiService.extractBookingDetails(prompt);

        String assetType = details.path("assetType").asText();

        LocalDate bookingDate = LocalDate.parse(
                details.path("bookingDate").asText());

        LocalTime startTime = LocalTime.parse(
                details.path("startTime").asText());

        LocalTime endTime = LocalTime.parse(
                details.path("endTime").asText());

        if (!endTime.isAfter(startTime)) {
            throw new IllegalArgumentException(
                    "End time must be after start time.");
        }

        long durationMinutes =
                Duration.between(startTime, endTime).toMinutes();

        if (durationMinutes <= 0) {
            throw new IllegalArgumentException(
                    "Invalid booking duration.");
        }

        // Get all assets of requested type
        List<Asset> assets =
                assetRepository.findByAssetType(assetType);

        if (assets.isEmpty()) {
            throw new IllegalArgumentException(
                    "No " + assetType + " assets found.");
        }

        // Find all assets available for requested date and time
        List<Map<String, Object>> availableAssets =
                new ArrayList<>();

        for (Asset asset : assets) {

            if (!"AVAILABLE".equalsIgnoreCase(asset.getStatus())) {
                continue;
            }

            boolean conflict = bookingRepository.hasConflict(
                    asset.getAssetId(),
                    bookingDate,
                    startTime,
                    endTime);

            if (!conflict) {

                Map<String, Object> assetDetails =
                        new HashMap<>();

                assetDetails.put("assetId", asset.getAssetId());
                assetDetails.put("assetName", asset.getAssetName());
                assetDetails.put("assetType", asset.getAssetType());
                assetDetails.put("hourlyRent", asset.getHourlyRent());

                availableAssets.add(assetDetails);
            }
        }

        Map<String, Object> response = new HashMap<>();

        response.put("assetType", assetType);
        response.put("bookingDate", bookingDate.toString());
        response.put("startTime", startTime.toString());
        response.put("endTime", endTime.toString());

        // If assets are available, show choices to the user
        if (!availableAssets.isEmpty()) {

            response.put("available", true);
            response.put("message",
                    "Available assets found. Select an asset and click Book Now.");

            response.put("assets", availableAssets);

            return response;
        }

        // If all assets are booked, search for another time
        LocalTime[] suggestedSlot = findNearestFreeSlot(
                assets,
                bookingDate,
                endTime,
                durationMinutes);

        response.put("available", false);
        response.put("assets", new ArrayList<>());

        if (suggestedSlot != null) {

            response.put("message",
                    "All " + assetType
                    + " assets are booked for the selected time.");

            response.put("suggestedStartTime",
                    suggestedSlot[0].toString());

            response.put("suggestedEndTime",
                    suggestedSlot[1].toString());

        } else {

            response.put("message",
                    "No available slot found for the rest of this day.");
        }

        return response;
    }

    // STEP 2: Save booking only after user clicks Book Now
    @Transactional
    public Booking confirmBooking(
            Long assetId,
            String bookingDateText,
            String startTimeText,
            String endTimeText,
            String userEmail) {

        // Validate login
        if (userEmail == null || userEmail.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Please login before booking.");
        }

        // Find logged-in user
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Logged-in user not found. Please login again."));

        // Validate requested values
        if (assetId == null
                || bookingDateText == null
                || startTimeText == null
                || endTimeText == null) {

            throw new IllegalArgumentException(
                    "Booking details are missing.");
        }

        LocalDate bookingDate = LocalDate.parse(bookingDateText);
        LocalTime startTime = LocalTime.parse(startTimeText);
        LocalTime endTime = LocalTime.parse(endTimeText);

        if (!endTime.isAfter(startTime)) {
            throw new IllegalArgumentException(
                    "End time must be after start time.");
        }

        long durationMinutes =
                Duration.between(startTime, endTime).toMinutes();

        // Find selected asset
        Asset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Selected asset was not found."));

        // Check asset status again
        if (!"AVAILABLE".equalsIgnoreCase(asset.getStatus())) {
            throw new IllegalArgumentException(
                    "This asset is currently unavailable. Please select another asset.");
        }

        // IMPORTANT: Check conflict again before saving
        boolean conflict = bookingRepository.hasConflict(
                asset.getAssetId(),
                bookingDate,
                startTime,
                endTime);

        if (conflict) {
            throw new IllegalArgumentException(
                    "Sorry! This asset has just been booked by someone else. "
                    + "Please check availability again.");
        }

        // Calculate rent
        double totalRent =
                (durationMinutes / 60.0) * asset.getHourlyRent();

        // Create booking
        Booking booking = new Booking();

        booking.setAsset(asset);
        booking.setUser(user);
        booking.setBookingDate(bookingDate);
        booking.setStartTime(startTime);
        booking.setEndTime(endTime);
        booking.setTotalRent(totalRent);
        booking.setDescription("Created through Gemini AI booking");
        booking.setStatus("BOOKED");

        // Save only after Book Now click
        return bookingRepository.save(booking);
    }

    // Search next available time across ALL assets of requested type
    private LocalTime[] findNearestFreeSlot(
            List<Asset> assets,
            LocalDate bookingDate,
            LocalTime searchStart,
            long durationMinutes) {

        if (durationMinutes <= 0 || durationMinutes >= 1440) {
            return null;
        }

        LocalTime candidateStart = searchStart;
        LocalTime lastStart =
                LocalTime.MAX.minusMinutes(durationMinutes);

        while (!candidateStart.isAfter(lastStart)) {

            LocalTime candidateEnd =
                    candidateStart.plusMinutes(durationMinutes);

            for (Asset asset : assets) {

                if (!"AVAILABLE".equalsIgnoreCase(asset.getStatus())) {
                    continue;
                }

                boolean conflict = bookingRepository.hasConflict(
                        asset.getAssetId(),
                        bookingDate,
                        candidateStart,
                        candidateEnd);

                if (!conflict) {
                    return new LocalTime[]{
                            candidateStart,
                            candidateEnd
                    };
                }
            }

            candidateStart = candidateStart.plusMinutes(30);
        }

        return null;
    }
}