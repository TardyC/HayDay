package dev.tardyc.hayday.config;

import dev.tardyc.hayday.util.Text;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;

/**
 * Beskeder fra messages.yml. Manglende nøgler falder tilbage til standardfilen i jar'en.
 */
public final class Messages {

    private final JavaPlugin plugin;
    private YamlConfiguration config;
    private String prefix = "";
    private UnaryOperator<String> postProcessor = UnaryOperator.identity();
    private BiFunction<String, CommandSender, String> receiverProcessor = (text, receiver) -> text;

    public Messages(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Bruges til at indsætte ikoner ({icon_coin} osv.) i alle beskeder. */
    public void setPostProcessor(UnaryOperator<String> postProcessor, BiFunction<String, CommandSender, String> receiverProcessor) {
        this.postProcessor = postProcessor;
        this.receiverProcessor = receiverProcessor;
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(file);
        InputStream defaults = plugin.getResource("messages.yml");
        if (defaults != null) {
            config.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(defaults, StandardCharsets.UTF_8)));
        }
        prefix = Text.color(config.getString("prefix", ""));
    }

    public String raw(String key) {
        return config.getString(key, "");
    }

    /** Beskeden med farver og pladsholdere, uden prefix. */
    public String get(String key, Object... placeholders) {
        return postProcessor.apply(Text.color(Text.replace(raw(key), placeholders)));
    }

    public List<String> list(String key, Object... placeholders) {
        List<String> out = new ArrayList<>();
        for (String line : config.getStringList(key)) {
            out.add(postProcessor.apply(Text.color(Text.replace(line, placeholders))));
        }
        return out;
    }

    public void send(CommandSender sender, String key, Object... placeholders) {
        String message = raw(key);
        if (message == null || message.isEmpty()) {
            return;
        }
        sender.sendMessage(prefix + receiverProcessor.apply(Text.color(Text.replace(message, placeholders)), sender));
    }

    public void sendList(CommandSender sender, String key, Object... placeholders) {
        for (String line : config.getStringList(key)) {
            sender.sendMessage(receiverProcessor.apply(Text.color(Text.replace(line, placeholders)), sender));
        }
    }

    public void actionBar(Player player, String key, Object... placeholders) {
        String message = get(key, placeholders);
        if (!message.isEmpty()) {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message));
        }
    }

    public String prefix() {
        return prefix;
    }
}
