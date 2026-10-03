package dev.tardyc.hayday.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * En ordre på ordretavlen. En "ventende" ordre har ingen varer og bliver til en ny ordre når {@link #getAvailableAt()} er nået.
 */
public final class Order {

    private final Map<String, Integer> items;
    private final double coins;
    private final int xp;
    private final long availableAt;

    public Order(Map<String, Integer> items, double coins, int xp, long availableAt) {
        this.items = Collections.unmodifiableMap(new LinkedHashMap<>(items));
        this.coins = coins;
        this.xp = xp;
        this.availableAt = availableAt;
    }

    public static Order waiting(long availableAt) {
        return new Order(Collections.<String, Integer>emptyMap(), 0, 0, availableAt);
    }

    public Map<String, Integer> getItems() {
        return items;
    }

    public double getCoins() {
        return coins;
    }

    public int getXp() {
        return xp;
    }

    public long getAvailableAt() {
        return availableAt;
    }

    public boolean isWaiting() {
        return items.isEmpty();
    }
}
