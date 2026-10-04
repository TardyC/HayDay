package dev.tardyc.hayday.island;

import dev.tardyc.hayday.hologram.Hologram;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * En spillers ø i HayDay-verdenen: placering, navn, hjem, hvem der må komme på besøg, venner og likes.
 */
public final class Island {

    /** Hvem der må besøge øen. */
    public enum Access {
        ALLE,
        VENNER,
        INGEN;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public Access next() {
            return values()[(ordinal() + 1) % values().length];
        }

        public static Access parse(String input, Access fallback) {
            if (input == null) {
                return fallback;
            }
            switch (input.toLowerCase(Locale.ROOT)) {
                case "alle":
                case "open":
                case "åben":
                case "aaben":
                    return ALLE;
                case "venner":
                case "friends":
                    return VENNER;
                case "ingen":
                case "lukket":
                case "closed":
                    return INGEN;
                default:
                    return fallback;
            }
        }
    }

    private final UUID owner;
    private String ownerName;
    private final int gridX;
    private final int gridZ;
    private String farmName;
    /** x, y, z, yaw, pitch - eller null for standard-hjemmet ved stranden. */
    private double[] home;
    private Access access = Access.ALLE;
    private final Map<UUID, String> friends = new LinkedHashMap<>();
    private final Map<UUID, String> banned = new LinkedHashMap<>();
    private final Set<UUID> likes = new HashSet<>();
    private int visits;
    private long created;
    /** 1 = den første simple gård, 2 = den realistiske gård med lade, silo osv. */
    private int style = 1;

    private transient Hologram hologram;
    private final transient Map<Landmark, Hologram> landmarkHolograms = new EnumMap<>(Landmark.class);

    public Island(UUID owner, String ownerName, int gridX, int gridZ) {
        this.owner = owner;
        this.ownerName = ownerName;
        this.gridX = gridX;
        this.gridZ = gridZ;
    }

    public UUID getOwner() {
        return owner;
    }

    public boolean isOwner(UUID uuid) {
        return owner.equals(uuid);
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public int getGridX() {
        return gridX;
    }

    public int getGridZ() {
        return gridZ;
    }

    public long key() {
        return IslandLayout.key(gridX, gridZ);
    }

    /** Eget navn på gården, eller null. */
    public String getFarmName() {
        return farmName;
    }

    public void setFarmName(String farmName) {
        this.farmName = farmName == null || farmName.isEmpty() ? null : farmName;
    }

    public double[] getHome() {
        return home == null ? null : home.clone();
    }

    public void setHome(double[] home) {
        this.home = home == null ? null : home.clone();
    }

    public Access getAccess() {
        return access;
    }

    public void setAccess(Access access) {
        this.access = access == null ? Access.ALLE : access;
    }

    public Map<UUID, String> getFriends() {
        return friends;
    }

    public boolean isFriend(UUID uuid) {
        return friends.containsKey(uuid);
    }

    public Map<UUID, String> getBanned() {
        return banned;
    }

    public boolean isBanned(UUID uuid) {
        return banned.containsKey(uuid);
    }

    public Set<UUID> getLikes() {
        return Collections.unmodifiableSet(likes);
    }

    public boolean addLike(UUID uuid) {
        return likes.add(uuid);
    }

    public void setLikes(Set<UUID> uuids) {
        likes.clear();
        likes.addAll(uuids);
    }

    public int getVisits() {
        return visits;
    }

    public void setVisits(int visits) {
        this.visits = Math.max(0, visits);
    }

    public long getCreated() {
        return created;
    }

    public void setCreated(long created) {
        this.created = created;
    }

    public int getStyle() {
        return style;
    }

    public void setStyle(int style) {
        this.style = style;
    }

    public Map<Landmark, Hologram> getLandmarkHolograms() {
        return landmarkHolograms;
    }

    public Hologram getHologram() {
        return hologram;
    }

    public void setHologram(Hologram hologram) {
        this.hologram = hologram;
    }
}
