package dev.tardyc.hayday.events;

import org.bukkit.boss.BarColor;

import java.util.Locale;

/**
 * De events en admin kan starte (eller som starter automatisk).
 */
public enum EventType {
    /** Mere for alt man sælger, ordrer og skibet. */
    PENGE("penge", BarColor.YELLOW),
    /** Mere XP for alt. */
    XP("xp", BarColor.BLUE),
    /** Afgrøder og produktion bliver hurtigere færdige. */
    VAEKST("vaekst", BarColor.GREEN),
    /** Flere afgrøder pr. høst. */
    HOEST("hoest", BarColor.WHITE),
    /** Flere varer fra bygningerne. */
    PRODUKTION("produktion", BarColor.PURPLE);

    private final String id;
    private final BarColor color;

    EventType(String id, BarColor color) {
        this.id = id;
        this.color = color;
    }

    public String id() {
        return id;
    }

    public BarColor color() {
        return color;
    }

    public static EventType parse(String input) {
        if (input == null) {
            return null;
        }
        String key = input.toLowerCase(Locale.ROOT).replace("æ", "ae").replace("ø", "oe").replace("å", "aa");
        switch (key) {
            case "penge":
            case "money":
            case "coins":
                return PENGE;
            case "xp":
            case "exp":
                return XP;
            case "vaekst":
            case "hastighed":
            case "speed":
            case "growth":
                return VAEKST;
            case "hoest":
            case "harvest":
                return HOEST;
            case "produktion":
            case "production":
                return PRODUKTION;
            default:
                return null;
        }
    }
}
