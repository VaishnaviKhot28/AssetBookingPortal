package com.assetbooking.AssetBookingPortal;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.time.DayOfWeek;
import java.time.format.DateTimeParseException;
import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class HomeController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private AssetRepository assetRepository;    
    
 // LOGIN PAGE
    @GetMapping("/")
    public String loginPage() {
        return "index";
    }
    // =========================
    // DASHBOARD
    // =========================
    @GetMapping("/dashboard")
    public String dashboardPage(HttpSession session, Model model) {
        String displayName = (String) session.getAttribute("displayName");

        if (displayName == null) {
            return "redirect:/";
        }
        return "dashboard";
    }

    // =========================
    // ALL ASSETS PAGE
    // =========================
    @GetMapping("/all-assets")
    public String allAssetsPage(HttpSession session, Model model) {

        String displayName =
                (String) session.getAttribute("displayName");

        if (displayName == null) {
            return "redirect:/";
        }

        model.addAttribute("displayName", displayName);

        return "all-assets";
    }

    // =========================
    // BOOKING FORM
    // =========================
    @GetMapping("/booking")
    public String bookingPage(
            @RequestParam(required = false) String asset,
            HttpSession session,
            Model model) {

        String displayName =
                (String) session.getAttribute("displayName");

        if (displayName == null) {
            return "redirect:/";
        }

        model.addAttribute("asset", asset);
        model.addAttribute("displayName", displayName);

        return "booking";
    }

    // =========================
    // REVIEW BOOKING DETAILS
    // =========================
    @GetMapping("/booking/confirm")
    public String confirmBooking(
            @RequestParam String assetType,
            @RequestParam String assetName,
            @RequestParam String bookingDate,
            @RequestParam String startTime,
            @RequestParam String endTime,
            @RequestParam(required = false) String description,
            HttpSession session,
            Model model) {

        String displayName =
                (String) session.getAttribute("displayName");

        if (displayName == null) {
            return "redirect:/";
        }

        model.addAttribute("assetType", assetType);
        model.addAttribute("assetName", assetName);
        model.addAttribute("bookingDate", bookingDate);
        model.addAttribute("startTime", startTime);
        model.addAttribute("endTime", endTime);
        model.addAttribute("description", description);
        model.addAttribute("displayName", displayName);

        try {
            LocalDate date = LocalDate.parse(bookingDate);
            LocalTime start = LocalTime.parse(startTime);
            LocalTime end = LocalTime.parse(endTime);

            if (date.isBefore(LocalDate.now())) {
                model.addAttribute(
                        "errorMessage",
                        "Booking date cannot be in the past."
                );
                model.addAttribute("asset", assetType);
                return "booking";
            }

            if (!start.isBefore(end)) {
                model.addAttribute(
                        "errorMessage",
                        "End time must be later than start time."
                );
                model.addAttribute("asset", assetType);
                return "booking";
            }

        } catch (Exception e) {
            model.addAttribute(
                    "errorMessage",
                    "Please enter a valid booking date and time."
            );
            model.addAttribute("asset", assetType);
            return "booking";
        }

        return "booking-confirm";
    }

    // =========================
    // SAVE BOOKING TO DATABASE
    // =========================
    @PostMapping("/booking/save")
    public String saveBooking(
            @RequestParam String assetType,
            @RequestParam String assetName,
            @RequestParam String bookingDate,
            @RequestParam String startTime,
            @RequestParam String endTime,
            @RequestParam(required = false) String description,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        String email =
                (String) session.getAttribute("userEmail");

        if (email == null) {
            return "redirect:/";
        }

        try {
            // Convert form values
            LocalDate date = LocalDate.parse(bookingDate);
            LocalTime start = LocalTime.parse(startTime);
            LocalTime end = LocalTime.parse(endTime);

            // Validate booking date
            if (date.isBefore(LocalDate.now())) {
                redirectAttributes.addFlashAttribute(
                        "errorMessage",
                        "Booking date cannot be in the past."
                );

                return "redirect:/booking?asset=" + assetType;
            }

            // Validate booking time
            if (!start.isBefore(end)) {
                redirectAttributes.addFlashAttribute(
                        "errorMessage",
                        "End time must be later than start time."
                );

                return "redirect:/booking?asset=" + assetType;
            }

            // Find logged-in user
            User user = userRepository
                    .findByEmail(email)
                    .orElse(null);

            if (user == null) {
                session.invalidate();
                return "redirect:/";
            }

         // Find selected asset by asset name
         Asset asset = assetRepository
                 .findByAssetName(assetName)
                 .orElse(null);

         if (asset == null || !asset.getAssetType().equals(assetType)) {
             redirectAttributes.addFlashAttribute(
                     "errorMessage",
                     "Selected asset was not found."
             );

             return "redirect:/booking?asset=" + assetType;
         }

            // Check booking time conflict
            boolean conflict = bookingRepository.hasConflict(
                    asset.getAssetId(),
                    date,
                    start,
                    end
            );

            if (conflict) {
                redirectAttributes.addFlashAttribute(
                        "errorMessage",
                        "This asset is already booked for the selected time."
                );

                return "redirect:/booking?asset=" + assetType;
            }

            // Create booking
            Booking booking = new Booking();

            booking.setUser(user);
            booking.setAsset(asset);
            booking.setBookingDate(date);
            booking.setStartTime(start);
            booking.setEndTime(end);
            booking.setDescription(description);
            booking.setStatus("BOOKED");

            // Save booking to MySQL
            Booking savedBooking = bookingRepository.save(booking);

            System.out.println("========== BOOKING SAVED ==========");
            System.out.println("User Email: " + email);
            System.out.println("Asset Type: " + assetType);
            System.out.println("Booking Date: " + date);
            System.out.println(
                    "Saved Booking ID: " + savedBooking.getBookingId()
            );
            System.out.println("===================================");

            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Booking confirmed successfully!"
            );

            return "redirect:/booking/success";

        } catch (Exception e) {
            e.printStackTrace();

            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "Booking could not be saved: " + e.getMessage()
            );

            return "redirect:/booking?asset=" + assetType;
        }
    }
    
    //MEETING ROOMS 
    @GetMapping("/meeting-rooms")
    public String meetingRoomsPage(HttpSession session, Model model) {

        System.out.println("========== MEETING ROOMS DEBUG ==========");
        System.out.println("Requested URL: /meeting-rooms");
        System.out.println("Session ID: " + session.getId());
        System.out.println("Display Name: " + session.getAttribute("displayName"));
        System.out.println("User Email: " + session.getAttribute("userEmail"));

        String displayName =
                (String) session.getAttribute("displayName");

        if (displayName == null) {
            System.out.println("No session. Redirecting to login.");
            return "redirect:/";
        }

        model.addAttribute("displayName", displayName);

        System.out.println("Returning meeting-rooms template");
        System.out.println("=========================================");

        return "meeting-rooms";
    }
    
    //LAPTOPS
    
    @GetMapping("/laptops")
    public String laptopsPage(HttpSession session, Model model) {

        String displayName = (String) session.getAttribute("displayName");

        if (displayName == null) {
            return "redirect:/";
        }

        model.addAttribute("displayName", displayName);

        return "laptops";
    }
    
    //Projectors
    
    @GetMapping("/projectors")
    public String projectorsPage(HttpSession session, Model model) {
        String displayName = (String) session.getAttribute("displayName");

        if (displayName == null) {
            return "redirect:/";
        }

        model.addAttribute("displayName", displayName);
        return "projector";
    }
    
    // =========================
    // BOOKING SUCCESS PAGE
    // =========================
    @GetMapping("/booking/success")
    public String bookingSuccessPage(
            HttpSession session,
            Model model) {

        String displayName =
                (String) session.getAttribute("displayName");

        if (displayName == null) {
            return "redirect:/";
        }

        model.addAttribute("displayName", displayName);

        return "booking-success";
    }
    
 // =========================
 // MY BOOKINGS
 // =========================

 @GetMapping("/my-bookings")
 public String myBookings(HttpSession session, Model model) {

     String email = (String) session.getAttribute("userEmail");

     if (email == null) {
         return "redirect:/";
     }

     User user = userRepository.findByEmail(email).orElse(null);

     if (user == null) {
         session.invalidate();
         return "redirect:/";
     }

     model.addAttribute(
             "bookings",
             bookingRepository.findByUser_UserIdAndStatus(
                     user.getUserId(), "BOOKED"
             )
     );

     model.addAttribute("displayName", user.getName());

     return "my-bookings";
 }
 
//=========================
//CANCEL BOOKING
//=========================
@PostMapping("/booking/cancel")
public String cancelBooking(
      @RequestParam Long bookingId,
      HttpSession session,
      RedirectAttributes redirectAttributes) {

  String email = (String) session.getAttribute("userEmail");

  if (email == null) {
      return "redirect:/";
  }

  User user = userRepository.findByEmail(email).orElse(null);

  if (user == null) {
      session.invalidate();
      return "redirect:/";
  }

  Booking booking = bookingRepository
          .findByBookingIdAndUser_UserId(
                  bookingId, user.getUserId())
          .orElse(null);

  if (booking == null) {
      redirectAttributes.addFlashAttribute(
              "errorMessage", "Booking not found."
      );
      return "redirect:/my-bookings";
  }

  if (!"BOOKED".equals(booking.getStatus())) {
      redirectAttributes.addFlashAttribute(
              "errorMessage", "This booking cannot be cancelled."
      );
      return "redirect:/my-bookings";
  }

  booking.setStatus("CANCELLED");
  bookingRepository.save(booking);

  redirectAttributes.addFlashAttribute(
          "successMessage", "Booking cancelled successfully."
  );

  return "redirect:/my-bookings";
}
//=========================
//BOOKING HISTORY
//=========================
@GetMapping("/booking-history")
public String bookingHistory(
     HttpSession session,
     Model model) {

 String email = (String) session.getAttribute("userEmail");

 if (email == null) {
     return "redirect:/";
 }

 User user = userRepository.findByEmail(email).orElse(null);

 if (user == null) {
     session.invalidate();
     return "redirect:/";
 }

 List<Booking> bookings =
         bookingRepository.findByUser_UserId(user.getUserId());

 model.addAttribute("bookings", bookings);
 model.addAttribute("displayName", user.getName());

 return "booking-history";
}
// WEEKLY CALENDAR

    @GetMapping("/weekly-calendar")
    public String weeklyCalendar(
            @RequestParam(required = false) String week,
            Model model) {

        LocalDate selectedDate = LocalDate.now();

        // Use the date provided in the URL, if available.
        if (week != null && !week.isBlank()) {
            try {
                selectedDate = LocalDate.parse(week);
            } catch (DateTimeParseException e) {
                selectedDate = LocalDate.now();
            }
        }

        // Calculate the Monday and Sunday of the selected week.
        LocalDate startDate = selectedDate.with(DayOfWeek.MONDAY);
        LocalDate endDate = startDate.plusDays(6);

        // Calculate the previous and next weeks.
        LocalDate previousWeek = startDate.minusWeeks(1);
        LocalDate nextWeek = startDate.plusWeeks(1);

        // Send date information to the HTML page.
        model.addAttribute("displayName", "User");
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);
        model.addAttribute("previousWeek", previousWeek);
        model.addAttribute("nextWeek", nextWeek);

        // Retrieve bookings for the selected week from the database.
        List<Booking> bookings =
                bookingRepository
                        .findByBookingDateBetweenOrderByBookingDateAscStartTimeAsc(
                                startDate,
                                endDate
                        );

        model.addAttribute("bookings", bookings);

        return "weekly-calendar";
    }


 // =========================
 // ADMIN PANEL
 // =========================

 @GetMapping("/admin")
 public String adminPage(HttpSession session, Model model) {

     String displayName = (String) session.getAttribute("displayName");

     if (displayName == null) {
         return "redirect:/";
     }

     model.addAttribute("displayName", displayName);
     model.addAttribute("assets", assetRepository.findAll());
     model.addAttribute("totalAssets", assetRepository.count());
     model.addAttribute(
         "availableAssets",
         assetRepository.countByStatus("AVAILABLE")
     );

     return "admin-assets";
 }
 
 @GetMapping("/admin/assets")
 public String adminAssets(HttpSession session, Model model) {

     String displayName = (String) session.getAttribute("displayName");

     if (displayName == null) {
         return "redirect:/";
     }

     model.addAttribute("displayName", displayName);
     model.addAttribute("assets", assetRepository.findAll());
     model.addAttribute("totalAssets", assetRepository.count());
     model.addAttribute(
         "availableAssets",
         assetRepository.countByStatus("AVAILABLE")
     );

     return "admin-assets";
 }

@PostMapping("/admin/assets/add")
public String addAsset(
        @RequestParam String assetName,
        @RequestParam String assetType,
        RedirectAttributes redirectAttributes) {

    try {
        Asset asset = new Asset();
        asset.setAssetName(assetName);
        asset.setAssetType(assetType);
        asset.setStatus("AVAILABLE");

        assetRepository.save(asset);

        redirectAttributes.addFlashAttribute(
                "successMessage", "Asset added successfully!");

    } catch (Exception e) {
        redirectAttributes.addFlashAttribute(
                "errorMessage", "Asset could not be added.");
    }

    return "redirect:/admin/assets";
}

// Admin delete

@PostMapping("/admin/assets/delete")
public String deleteAsset(
        @RequestParam Long assetId,
        RedirectAttributes redirectAttributes) {

    try {
        Asset asset = assetRepository.findById(assetId).orElse(null);

        if (asset == null) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage", "Asset not found!");
            return "redirect:/admin/assets";
        }

        assetRepository.delete(asset);

        redirectAttributes.addFlashAttribute(
                "successMessage", "Asset deleted successfully!");

    } catch (Exception e) {
        redirectAttributes.addFlashAttribute(
                "errorMessage",
                "Asset could not be deleted. It may have existing bookings.");
    }

    return "redirect:/admin/assets";
}

//=========================
//REPORTS
//=========================
@GetMapping("/reports")
public String reports(HttpSession session, Model model) {

 String email = (String) session.getAttribute("userEmail");

 if (email == null) {
     return "redirect:/";
 }

 User user = userRepository.findByEmail(email).orElse(null);

 if (user == null) {
     session.invalidate();
     return "redirect:/";
 }

 model.addAttribute("displayName", user.getName());

 model.addAttribute("totalAssets", assetRepository.count());

 model.addAttribute(
     "availableAssets",
     assetRepository.countByStatus("AVAILABLE")
 );

 model.addAttribute("totalBookings", bookingRepository.count());

 model.addAttribute(
     "activeBookings",
     bookingRepository.countByStatus("BOOKED")
 );

 model.addAttribute("assets", assetRepository.findAll());

 // Fetch recent bookings together with their assets.
 List<Booking> recentBookings =
         bookingRepository.findAllByOrderByBookingIdDesc();

 model.addAttribute("recentBookings", recentBookings);

 return "reports";
}

//=========================
//MY ACCOUNT
//=========================

@GetMapping({"/account", "/my-account"})
public String accountPage(HttpSession session, Model model) {

 String email = (String) session.getAttribute("userEmail");

 if (email == null) {
     return "redirect:/";
 }

 User user = userRepository
         .findByEmail(email)
         .orElse(null);

 if (user == null) {
     session.invalidate();
     return "redirect:/";
 }

 model.addAttribute("user", user);
 model.addAttribute("displayName", user.getName());

 return "account";
}

    // =========================
    // LOGOUT
    // =========================
    @GetMapping("/logout")
    public String logout(HttpSession session) {

        session.invalidate();

        return "redirect:/";
    }
}