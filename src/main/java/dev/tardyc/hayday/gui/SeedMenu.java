package dev.tardyc.hayday.gui;

import dev.tardyc.hayday.HayDayPlugin;
import dev.tardyc.hayday.model.CropType;
import dev.tardyc.hayday.model.FarmItem;
import dev.tardyc.hayday.model.Field;
import dev.tardyc.hayday.model.PlayerData;
import dev.tardyc.hayday.util.ItemBuilder;
import dev.tardyc.hayday.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.ArrayList;
import java.util.List;

/**
 * Vælg hvilken afgrøde der skal plantes på en mark.
 */
public final class SeedMenu extends Menu {

    private final Field field;
    private final List<FarmItem> crops;

    public SeedMenu(HayDayPlugin plugin, Player player, Field field) {
        super(plugin, player, rowsFor(plugin.getItems().crops().size()), "&a&lPlant &8» &7Vælg afgrøde", "seed");
        this.field = field;
        this.crops = plugin.getItems().crops();
    }

    private static int rowsFor(int crops) {
        return Math.min(6, 2 + Math.max(1, (crops + 6) / 7));
    }

    @Override
    protected void render() {
        PlayerData data = plugin.getPlayers().get(player);
        int rows = size() / 9;
        List<Integer> slots = innerSlots(1, rows - 2);
        int radius = plugin.getSettings().massRadius;
        for (int i = 0; i < crops.size() && i < slots.size(); i++) {
            FarmItem item = crops.get(i);
            CropType crop = item.getCrop();
            int slot = slots.get(i);
            if (data.getLevel() < crop.getLevel()) {
                set(slot, new ItemBuilder(Material.GRAY_DYE)
                        .name("&7" + item.getPlainName() + " &8(låst)")
                        .lore("&cLåses op ved level " + crop.getLevel())
                        .build());
                continue;
            }
            int inSilo = data.getAmount(item.getId());
            List<String> lore = new ArrayList<>();
            lore.add("&7Vækstid: &f" + Text.time(crop.getGrowSeconds()));
            lore.add("&7Høst: &f" + crop.getHarvestAmount() + "x &8| &7XP: &b+" + crop.getHarvestXp());
            lore.add("&7I siloen: &f" + inSilo);
            if (plugin.getSettings().useSiloCrop && inSilo > 0) {
                lore.add("&7Pris: &f1x " + item.getName() + " &7fra siloen");
            } else {
                lore.add("&7Pris: &6" + (crop.getSeedPrice() > 0 ? money(crop.getSeedPrice()) : "Gratis"));
            }
            lore.add("");
            lore.add("&e» Venstreklik &7for at plante her");
            lore.add("&e» Shift-klik &7for at plante på alle");
            lore.add("&7  tomme marker inden for " + radius + " blokke");
            set(slot, item.icon().amount(Math.max(1, inSilo)).lore(lore).build(), click -> plant(item, click));
        }
        set(size() - 5, closeButton(), click -> closeLater());
        fillEmpty();
    }

    private void plant(FarmItem item, ClickType click) {
        closeLater();
        if (click.isShiftClick()) {
            plugin.getService().plantNearby(player, item, field.getSoil().toCenter());
        } else {
            plugin.getService().plant(player, field, item);
        }
    }
}
