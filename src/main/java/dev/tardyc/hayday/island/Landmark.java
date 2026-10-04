package dev.tardyc.hayday.island;

/**
 * Gårdens faste bygninger på en ø (stil 2). Klik på dem åbner menuerne - ligesom i Hay Day.
 * Koordinaterne er ankerets plads på en 48x48-ø (på større øer flyttes alt ind mod midten).
 */
public enum Landmark {
    /** Laden: produkter. */
    BARN("barn", 31, 17),
    /** Siloen: afgrøder. */
    SILO("silo", 40, 10),
    /** Ordretavlen. */
    ORDERS("orderboard", 30, 21),
    /** Postkassen: avisen. */
    MAILBOX("mailbox", 21, 40);

    private final String structure;
    private final int x;
    private final int z;

    Landmark(String structure, int x, int z) {
        this.structure = structure;
        this.x = x;
        this.z = z;
    }

    public String structure() {
        return structure;
    }

    public int x() {
        return x;
    }

    public int z() {
        return z;
    }

    public String id() {
        return structure;
    }
}
