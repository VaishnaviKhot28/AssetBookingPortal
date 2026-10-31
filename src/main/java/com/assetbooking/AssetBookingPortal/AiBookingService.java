package com.assetbooking.AssetBookingPortal;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;

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

    @Transactional
    public Booking createBooking(String prompt, Long userId)
            throws Exception {

        // Step 1: Extract booking details using Gemini AI
        JsonNode details = geminiService.extractBookingDetails(prompt);

        String assetType = details.path("assetType").asText();

        LocalDate bookingDate = LocalDate.parse(
                details.path("bookingDate").asText());

        LocalTime startTime = LocalTime.parse(
                details.path("startTime").asText());

        LocalTime endTime = LocalTime.parse(
                details.path("endTime").asText());

        // Step 2: Validate booking time
        if (!endTime.isAfter(startTime)) {
            throw new IllegalArgumentException(
                    "End time must be after start time.");
        }

        long durationMinutes = Duration.between(
                startTime, endTime).toMinutes();

        // Step 3: Find an available asset of the requested type
        Asset asset = assetRepository.findByAssetType(assetType)
                .stream()
                .filter(a -> "AVAILABLE".equalsIgnoreCase(a.getStatus()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No available asset found for type: "
                                + assetType));

        // Step 4: Find the user
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "User not found. Please provide a valid userId."));

        // Step 5: Check whether the requested time has a conflict
        boolean conflict = bookingRepository.hasConflict(
                asset.getAssetId(),
                bookingDate,
                startTime,
                endTime);

        if (conflict) {

            // Find the nearest free slot for the same asset
            LocalTime[] suggestedSlot = findNearestFreeSlot(
                    asset,
                    bookingDate,
                    endTime,
                    durationMinutes);

            if (suggestedSlot != null) {
                throw new IllegalArgumentException(
                        "This asset is already booked for the "
                                + "selected time. Suggested available "
                                + "slot: "
                                + suggestedSlot[0]
                                + " - "
                                + suggestedSlot[1]
                                + ". Please submit a new booking "
                                + "request using this time.");
            }

            throw new IllegalArgumentException(
                    "This asset is already booked for the selected "
                            + "time. No available slot was found "
                            + "for the rest of this day.");
        }

        // Step 6: Calculate rent
        double totalRent = (durationMinutes / 60.0)
                * asset.getHourlyRent();

        // Step 7: Create the booking
        Booking booking = new Booking();

        booking.setAsset(asset);
        booking.setUser(user);
        booking.setBookingDate(bookingDate);
        booking.setStartTime(startTime);
        booking.setEndTime(endTime);
        booking.setTotalRent(totalRent);
        booking.setDescription(
                "Created through Gemini AI booking");
        booking.setStatus("BOOKED");

        // Step 8: Save the booking
        return bookingRepository.save(booking);
    }

    /**
     * Finds the next available slot for the same asset.
     *
     * The search starts at the requested booking's end time
     * and checks slots in 30-minute increments.
     *
     * This method only suggests a slot. It does not create
     * or save a booking.
     */
    private LocalTime[] findNearestFreeSlot(
            Asset asset,
            LocalDate bookingDate,
            LocalTime searchStart,
            long durationMinutes) {

        if (durationMinutes <= 0) {
            return null;
        }

        LocalTime candidateStart = searchStart;

        // Keep the suggested booking within the same day.
        while (candidateStart.isBefore(LocalTime.MAX)) {

            LocalTime candidateEnd;

            try {
                candidateEnd = candidateStart.plusMinutes(
                        durationMinutes);
            } catch (Exception e) {
                return null;
            }

            // Do not allow a booking to cross midnight.
            if (!candidateEnd.isAfter(candidateStart)) {
                return null;
            }

            // Check whether this candidate slot is already booked.
            boolean conflict = bookingRepository.hasConflict(
                    asset.getAssetId(),
                    bookingDate,
                    candidateStart,
                    candidateEnd);

            if (!conflict) {
                return new LocalTime[] {
                        candidateStart,
                        candidateEnd
                };
            }

            // Try the next candidate 30 minutes later.
            try {
                candidateStart = candidateStart.plusMinutes(30);
            } catch (Exception e) {
                return null;
            }
        }

        return null;
    }
}