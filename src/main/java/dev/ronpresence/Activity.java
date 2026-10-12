package dev.ronpresence;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
public record Activity(String details, String state, String largeImage, String hover) {
    public JsonObject json() {
        var activity = new JsonObject();
        activity.addProperty("details", truncate(details, 128));
        if (!state.isBlank()) activity.addProperty("state", truncate(state, 128));
        if (!largeImage.isBlank()) {
            var assets = new JsonObject();
            assets.addProperty("large_image", truncate(largeImage, 256));
            assets.addProperty("large_text", truncate(hover.isBlank() ? "Reign of Nether" : hover, 128));
            activity.add("assets", assets);
        }
        return activity;
    }
    static String truncate(String value, int maxBytes) {
        StringBuilder result = new StringBuilder();
        int bytes = 0;
        for (int offset = 0; offset < value.length();) {
            int codepoint = value.codePointAt(offset);
            String next = new String(Character.toChars(codepoint));
            int size = next.getBytes(StandardCharsets.UTF_8).length;
            if (bytes + size > maxBytes) break;
            result.append(next); bytes += size; offset += Character.charCount(codepoint);
        }
        return result.toString();
    }
}
