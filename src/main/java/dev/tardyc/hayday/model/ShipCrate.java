package dev.tardyc.hayday.model;

/**
 * En kasse på skibet: skal fyldes med et antal af en vare og giver penge og XP.
 */
public final class ShipCrate {

    private final String itemId;
    private final int amount;
    private final double coins;
    private final int xp;
    private boolean filled;

    public ShipCrate(String itemId, int amount, double coins, int xp, boolean filled) {
        this.itemId = itemId;
        this.amount = amount;
        this.coins = coins;
        this.xp = xp;
        this.filled = filled;
    }

    public String getItemId() {
        return itemId;
    }

    public int getAmount() {
        return amount;
    }

    public double getCoins() {
        return coins;
    }

    public int getXp() {
        return xp;
    }

    public boolean isFilled() {
        return filled;
    }

    public void setFilled(boolean filled) {
        this.filled = filled;
    }
}
