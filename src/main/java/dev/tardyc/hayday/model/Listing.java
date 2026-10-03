package dev.tardyc.hayday.model;

import java.util.UUID;

/**
 * En vare til salg i en spillers vejbod. Varerne er trukket fra sælgerens lager, og pengene
 * hentes i vejboden når varen er solgt (præcis som i Hay Day).
 */
public final class Listing {

    /** Status for en vare i vejboden. */
    public enum State {
        ACTIVE,
        SOLD,
        EXPIRED
    }

    private final UUID id;
    private final UUID seller;
    private String sellerName;
    private final int slot;
    private final String itemId;
    private final int amount;
    private final double price;
    private final long createdAt;
    private State state;
    private String buyerName;

    public Listing(UUID id, UUID seller, String sellerName, int slot, String itemId, int amount, double price,
                   long createdAt, State state, String buyerName) {
        this.id = id;
        this.seller = seller;
        this.sellerName = sellerName;
        this.slot = slot;
        this.itemId = itemId;
        this.amount = amount;
        this.price = price;
        this.createdAt = createdAt;
        this.state = state;
        this.buyerName = buyerName;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSeller() {
        return seller;
    }

    public String getSellerName() {
        return sellerName;
    }

    public void setSellerName(String sellerName) {
        this.sellerName = sellerName;
    }

    public int getSlot() {
        return slot;
    }

    public String getItemId() {
        return itemId;
    }

    public int getAmount() {
        return amount;
    }

    public double getPrice() {
        return price;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public String getBuyerName() {
        return buyerName;
    }

    public void setBuyerName(String buyerName) {
        this.buyerName = buyerName;
    }

    public boolean isActive() {
        return state == State.ACTIVE;
    }
}
