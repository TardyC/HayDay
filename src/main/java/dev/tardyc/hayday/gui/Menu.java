package dev.tardyc.hayday.gui;

import dev.tardyc.hayday.HayDayPlugin;
import dev.tardyc.hayday.pack.ResourcePackManager;
import dev.tardyc.hayday.util.ItemBuilder;
import dev.tardyc.hayday.util.Sounds;
import dev.tardyc.hayday.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Basis for alle HayDay-menuer. Items kan ikke tages ud (se {@link MenuListener}), og kun ændrede
 * slots sendes til klienten når menuen opdateres.
 */
public abstract class Menu implements InventoryHolder {

    /** Hvad der sker når der klikkes på et slot. */
    @FunctionalInterface
    public interface ClickHandler {
        void onClick(ClickType click);
    }

    /** Usynligt item-model fra resourcepacken - lægges over knapper der er tegnet i baggrunden. */
    private static final NamespacedKey INVISIBLE = NamespacedKey.fromString("hayday:invisible");

    protected final HayDayPlugin plugin;
    protected final Player player;
    private final Inventory inventory;
    /** ItemsAdder-menuen hvis menuen har Hay Day-tekstur via ItemsAdder, ellers null. */
    private final Object textured;
    /** Har menuen baggrund fra HayDays egen resourcepack (i titlen)? */
    private final boolean ownTexture;
    private final Map<Integer, ClickHandler> handlers = new HashMap<>();
    private ItemStack[] buffer;

    protected Menu(HayDayPlugin plugin, Player player, int rows, String title, String texture) {
        this.plugin = plugin;
        this.player = player;
        int size = Math.max(1, Math.min(6, rows)) * 9;
        Object texturedMenu = null;
        Inventory created = null;
        boolean own = false;
        String gui = texture == null ? null : texture + "_" + (size / 9);
        ResourcePackManager pack = plugin.getPack();
        // Mørk tekst på pergamentet i stedet for menuens normale farver
        String plainTitle = Text.color(plugin.getSettings().itemsAdderTitleColor) + Text.strip(title);
        if (gui != null && plugin.getSettings().texturedMenus && pack.hasPack(player)) {
            if (pack.getMode() == ResourcePackManager.Mode.ITEMSADDER && plugin.getSettings().itemsAdderMenus) {
                texturedMenu = plugin.getItemsAdder().createTexturedInventory(this, size, plainTitle, "hayday:gui_" + gui);
                if (texturedMenu != null) {
                    created = plugin.getItemsAdder().getInventory(texturedMenu);
                }
            } else if (pack.getMode() == ResourcePackManager.Mode.OWN && pack.getGlyphs().hasGui(gui)) {
                created = Bukkit.createInventory(this, size, pack.getGlyphs().guiTitle(gui, plainTitle));
                own = true;
            }
        }
        if (created == null) {
            texturedMenu = null;
            own = false;
            created = Bukkit.createInventory(this, size, Text.color(title));
        }
        this.textured = texturedMenu;
        this.ownTexture = own;
        this.inventory = created;
    }

    /** Har menuen en ItemsAdder-baggrund? Så vises der ingen glasruder. */
    protected boolean isTextured() {
        return textured != null || ownTexture;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /** Fyld menuen med {@link #set(int, ItemStack, ClickHandler)}. */
    protected abstract void render();

    /** Menuer med nedtællinger opdateres automatisk hvert sekund. */
    public boolean isAutoRefresh() {
        return false;
    }

    protected int size() {
        return inventory.getSize();
    }

    protected void set(int slot, ItemStack item) {
        set(slot, item, null);
    }

    protected void set(int slot, ItemStack item, ClickHandler handler) {
        if (slot < 0 || slot >= buffer.length) {
            return;
        }
        buffer[slot] = item;
        if (handler != null) {
            handlers.put(slot, handler);
        } else {
            handlers.remove(slot);
        }
    }

    /** Tegner menuen på ny og sender kun ændringerne. */
    public final void update() {
        handlers.clear();
        buffer = new ItemStack[inventory.getSize()];
        render();
        for (int i = 0; i < buffer.length; i++) {
            if (!Objects.equals(inventory.getItem(i), buffer[i])) {
                inventory.setItem(i, buffer[i]);
            }
        }
    }

    public void open() {
        update();
        if (textured == null || !plugin.getItemsAdder().show(textured, player)) {
            player.openInventory(inventory);
        }
    }

    void handleClick(int slot, ClickType click) {
        ClickHandler handler = handlers.get(slot);
        if (handler != null) {
            handler.onClick(click);
        }
    }

    /** Åbner en anden menu på næste tick (sikrere end at åbne midt i et klik-event). */
    protected void openLater(Menu menu) {
        Sounds.play(player, Sounds.CLICK);
        Bukkit.getScheduler().runTask(plugin, menu::open);
    }

    protected void closeLater() {
        Bukkit.getScheduler().runTask(plugin, player::closeInventory);
    }

    /** Fylder alle tomme slots med glas (ikke i teksturerede menuer, hvor baggrunden skal ses). */
    protected void fillEmpty() {
        if (isTextured()) {
            return;
        }
        ItemStack filler = filler();
        for (int i = 0; i < buffer.length; i++) {
            if (buffer[i] == null) {
                buffer[i] = filler;
            }
        }
    }

    protected void fillRow(int row) {
        if (isTextured()) {
            return;
        }
        ItemStack filler = filler();
        for (int i = row * 9; i < row * 9 + 9 && i < buffer.length; i++) {
            buffer[i] = filler;
        }
    }

    /** De "indre" slots (kolonne 1-7) i rækkerne fra {@code firstRow} til {@code lastRow}. */
    protected static List<Integer> innerSlots(int firstRow, int lastRow) {
        List<Integer> slots = new ArrayList<>();
        for (int row = firstRow; row <= lastRow; row++) {
            for (int column = 1; column <= 7; column++) {
                slots.add(row * 9 + column);
            }
        }
        return slots;
    }

    protected static ItemStack filler() {
        return new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
    }

    protected ItemStack backButton() {
        return button(Material.ARROW, "&e« Tilbage");
    }

    protected ItemStack closeButton() {
        return button(Material.BARRIER, "&cLuk");
    }

    /**
     * En knap der er tegnet i menuens tekstur. Med ItemsAdder bruges et usynligt item, så tegningen
     * kan ses; ellers et almindeligt item.
     */
    protected ItemStack button(Material fallback, String name, String... lore) {
        if (isTextured()) {
            return new ItemBuilder(Material.PAPER).model(INVISIBLE).name(name).lore(lore).build();
        }
        return new ItemBuilder(fallback).name(name).lore(lore).build();
    }

    protected String money(double amount) {
        return plugin.getEconomy().format(amount);
    }
}
