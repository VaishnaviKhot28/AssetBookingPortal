package com.assetbooking.AssetBookingPortal;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

    @GetMapping("/ai-booking")
    public String aiBookingPage() {
        return "ai-booking";
    }

    @GetMapping("/booking/history")
    public String bookingHistoryPage() {
        return "booking-history";
    }
}	