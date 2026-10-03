package dev.tardyc.hayday.util;

import org.bukkit.ChatColor;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tekst-hjælpere: farvekoder (inkl. hex), tid, tal og progress bars.
 */
public final class Text {

    private static final Pattern HEX = Pattern.compile("&#([0-9a-fA-F]{6})");
    private static final DecimalFormat NUMBER;

    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
        symbols.setGroupingSeparator('.');
        symbols.setDecimalSeparator(',');
        NUMBER = new DecimalFormat("#,##0.##", symbols);
    }

    private Text() {
    }

    /** Oversætter &-farvekoder og &#RRGGBB hex-farver. */
    public static String color(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        Matcher matcher = HEX.matcher(input);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            StringBuilder hex = new StringBuilder("§x");
            for (char c : matcher.group(1).toCharArray()) {
                hex.append('§').append(c);
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(hex.toString()));
        }
        matcher.appendTail(out);
        return ChatColor.translateAlternateColorCodes('&', out.toString());
    }

    public static List<String> color(List<String> input) {
        List<String> out = new ArrayList<>(input.size());
        for (String line : input) {
            out.add(color(line));
        }
        return out;
    }

    public static String strip(String input) {
        return ChatColor.stripColor(color(input));
    }

    /**
     * Erstatter {nøgle} med værdi. Argumenterne gives parvis: "nøgle", værdi, "nøgle2", værdi2 ...
     */
    public static String replace(String input, Object... placeholders) {
        if (input == null) {
            return "";
        }
        String result = input;
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            result = result.replace("{" + placeholders[i] + "}", String.valueOf(placeholders[i + 1]));
        }
        return result;
    }

    /** Formaterer sekunder som fx "1t 4m", "3m 20s" eller "45s". */
    public static String time(long seconds) {
        if (seconds < 0) {
            seconds = 0;
        }
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        if (days > 0) {
            return days + "d " + hours + "t";
        }
        if (hours > 0) {
            return hours + "t " + minutes + "m";
        }
        if (minutes > 0) {
            return secs > 0 ? minutes + "m " + secs + "s" : minutes + "m";
        }
        return secs + "s";
    }

    public static String timeMillis(long millis) {
        return time((long) Math.ceil(Math.max(0, millis) / 1000.0));
    }

    public static String number(double value) {
        synchronized (NUMBER) {
            return NUMBER.format(value);
        }
    }

    public static String progressBar(double progress, int length, String symbol, String doneColor, String leftColor) {
        progress = Math.max(0, Math.min(1, progress));
        int done = (int) Math.round(progress * length);
        StringBuilder bar = new StringBuilder(color(doneColor));
        for (int i = 0; i < done; i++) {
            bar.append(symbol);
        }
        bar.append(color(leftColor));
        for (int i = done; i < length; i++) {
            bar.append(symbol);
        }
        return bar.toString();
    }

    /** Gør et id som "graeskar_taerte" pænt: "Graeskar taerte". */
    public static String prettify(String id) {
        String spaced = id.replace('_', ' ').toLowerCase(Locale.ROOT);
        return spaced.isEmpty() ? spaced : Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }
}
