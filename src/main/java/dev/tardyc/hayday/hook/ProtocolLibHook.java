package dev.tardyc.hayday.hook;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.Collection;

/**
 * Bruger ProtocolLib til at afspille Minecrafts rigtige "saml op"-animation, hvor en vare flyver ind i spilleren.
 * Klassen indlæses kun hvis ProtocolLib er installeret.
 */
public final class ProtocolLibHook {

    private final ProtocolManager manager;

    public ProtocolLibHook() {
        this.manager = ProtocolLibrary.getProtocolManager();
    }

    /** Får klienterne til at vise at {@code collector} samler {@code item} op. */
    public void sendPickup(Entity item, Player collector, int amount, Collection<? extends Player> viewers) {
        PacketContainer packet = manager.createPacket(PacketType.Play.Server.COLLECT);
        packet.getIntegers()
                .write(0, item.getEntityId())
                .write(1, collector.getEntityId())
                .write(2, Math.max(1, amount));
        for (Player viewer : viewers) {
            manager.sendServerPacket(viewer, packet);
        }
    }
}
