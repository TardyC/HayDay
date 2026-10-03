package dev.tardyc.hayday.util;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Forhindrer at et klik behandles flere gange (fx når både blok- og entity-events fyres).
 */
public final class ClickGuard {

    private static final long COOLDOWN_MS = 200;
    private final Map<UUID, Long> lastClick = new HashMap<>();

    public boolean tryClick(Player player) {
        long now = System.currentTimeMillis();
        Long last = lastClick.get(player.getUniqueId());
        if (last != null && now - last < COOLDOWN_MS) {
            return false;
        }
        lastClick.put(player.getUniqueId(), now);
        return true;
    }

    public void forget(Player player) {
        lastClick.remove(player.getUniqueId());
    }
}
