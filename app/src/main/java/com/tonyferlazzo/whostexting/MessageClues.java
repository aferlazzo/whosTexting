package com.tonyferlazzo.whostexting;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Conservative offline rules. Message claims never authenticate the sender. */
final class MessageClues {
    private MessageClues() {}
    static String analyze(String body) {
        if (body == null || body.trim().isEmpty()) return "";
        String text = body.substring(0, Math.min(body.length(), 4096));
        String lower = text.toLowerCase(Locale.ROOT);
        String purpose = "";
        if (lower.matches("(?s).*(verification code|security code|one.time (code|password)|otp|sign.in code).*"))
            purpose = "Verification or sign-in code";
        else if (lower.matches("(?s).*(prescription|pharmacy|rx ready).*")) purpose = "Prescription or pharmacy notice";
        else if (lower.matches("(?s).*(appointment|scheduled visit|reservation).*")) purpose = "Appointment or reservation";
        else if (lower.matches("(?s).*(package|parcel|delivery|shipment|tracking).*")) purpose = "Delivery or shipment";
        else if (lower.matches("(?s).*(payment|invoice|bill due|balance due|transaction).*")) purpose = "Payment or account notice";
        else if (lower.matches("(?s).*(order|pickup|pick up).*")) purpose = "Order or pickup";
        else if (lower.matches("(?s).*(sale|discount|coupon|offer|promo).*")) purpose = "Promotion or offer";
        String claimed = "";
        // Only explicit brand mentions; do not invent an organization from arbitrary prose.
        String[] brands = {"Walgreens", "CVS", "Walmart", "Amazon", "FedEx", "UPS", "USPS",
            "DHL", "Target", "Costco", "Wells Fargo", "Bank of America", "Chase", "PayPal",
            "T-Mobile", "Verizon", "AT&T", "DoorDash", "Uber", "Lyft"};
        for (String brand : brands) {
            if (Pattern.compile("(?i)(?<![a-z0-9])" + Pattern.quote(brand) + "(?![a-z0-9])").matcher(text).find()) {
                if (!claimed.isEmpty()) { claimed = "Multiple organizations mentioned"; break; }
                claimed = brand;
            }
        }
        // An explicit introduction is a claim too. Limit length and never copy URLs or codes.
        if (claimed.isEmpty()) {
            Matcher intro = Pattern.compile("(?i)\\b(?:this is|message from|hi from|hello from) ([a-z][a-z '&-]{1,39})(?=[.!,:;\\n]|$)").matcher(text);
            if (intro.find()) claimed = intro.group(1).trim();
        }
        if (purpose.isEmpty() && claimed.isEmpty()) return "";
        return (claimed.isEmpty() ? "" : "Mentioned sender: " + claimed + ". ")
            + (purpose.isEmpty() ? "" : "Apparent purpose: " + purpose + ". ")
            + "Message clues only — sender not verified.";
    }
}
