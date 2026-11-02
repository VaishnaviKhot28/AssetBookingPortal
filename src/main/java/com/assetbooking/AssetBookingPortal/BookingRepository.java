package com.assetbooking.AssetBookingPortal;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.EntityGraph;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    // Check whether an asset has a booking conflict.
    @Query("""
        SELECT COUNT(b) > 0
        FROM Booking b
        WHERE b.asset.assetId = :assetId
          AND b.bookingDate = :bookingDate
          AND b.status = 'BOOKED'
          AND b.startTime < :endTime
          AND b.endTime > :startTime
    """)
    boolean hasConflict(
            @Param("assetId") Long assetId,
            @Param("bookingDate") LocalDate bookingDate,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime
    );

    // Count bookings by status.
    long countByStatus(String status);

    // Find all bookings made by a specific user.
 // Fetch bookings together with their associated assets.
    @EntityGraph(attributePaths = {"asset"})
    List<Booking> findByUser_UserId(Long userId);

    // Find a specific booking belonging to a specific user.
    Optional<Booking> findByBookingIdAndUser_UserId(
            Long bookingId,
            Long userId
    );

 // Fetch bookings and their associated assets together.
 @EntityGraph(attributePaths = {"asset"})
 List<Booking> findByUser_UserIdAndStatus(
         Long userId,
         String status
 );
 
//Fetch all bookings together with their associated assets for reports.
@EntityGraph(attributePaths = {"asset"})
List<Booking> findAllByOrderByBookingDateAscStartTimeAsc();

    // Retrieve all bookings ordered by booking ID.
    List<Booking> findAllByOrderByBookingIdAsc();

 // Fetch bookings with their associated assets.
    @EntityGraph(attributePaths = {"asset"})
    List<Booking> findByBookingDateBetweenOrderByBookingDateAscStartTimeAsc(
            LocalDate startDate,
            LocalDate endDate
    );
 // Fetch bookings along with their associated assets.
    @EntityGraph(attributePaths = {"asset"})
    List<Booking> findAllWithAssetByUser_UserId(Long userId);

 // Fetch all bookings with their assets, newest booking first.
 @EntityGraph(attributePaths = {"asset"})
 List<Booking> findAllByOrderByBookingIdDesc();
}
