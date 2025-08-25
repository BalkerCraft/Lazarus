package me.qiooip.lazarus.handlers.chat;

import lombok.Getter;
import me.qiooip.lazarus.Lazarus;
import me.qiooip.lazarus.config.Config;
import me.qiooip.lazarus.factions.FactionsManager;
import me.qiooip.lazarus.factions.type.PlayerFaction;
import me.qiooip.lazarus.handlers.manager.Handler;
import me.qiooip.lazarus.integration.Chat_LuckPerms;
import me.qiooip.lazarus.userdata.Userdata;
import me.qiooip.lazarus.utils.Color;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.PluginManager;

public class ChatHandler extends Handler implements Listener {

    @Getter public static ChatHandler instance;

    public static void setup() {
        PluginManager pluginManager = Bukkit.getPluginManager();

        if(pluginManager.isPluginEnabled("LuckPerms")) {
            instance = new Chat_LuckPerms();
        } else {
            instance = new ChatHandler();
        }
    }

    public String getRankName(Player player) {
        return "";
    }

    protected String getTag(Player player) {
        return "";
    }

    public String getPrefix(Player player) {
        return "";
    }

    public String getNameColor(Player player) {
        return "";
    }

    protected String getSuffix(Player player) {
        return "";
    }

    protected String getChatColor(Player player) {
        return "";
    }

    private String getPlayerDisplayName(Player player) {
        if(Config.CHAT_FORMAT_USE_PLAYER_DISPLAY_NAME) {
            return player.getDisplayName();
        }

        String tag = instance.getTag(player);
        String prefix = instance.getPrefix(player);
        String nameColor = instance.getNameColor(player);
        String suffix = instance.getSuffix(player);
        
        // Ensure null values don't break formatting
        tag = tag != null ? tag : "";
        prefix = prefix != null ? prefix : "";
        nameColor = nameColor != null ? nameColor : "";
        suffix = suffix != null ? suffix : "";
        
        return Color.translate(tag + prefix + nameColor + player.getName() + suffix);
    }

    private String getChatMessage(PlayerFaction playerFaction, CommandSender recipient,
                                  String displayName, String chatColor, String message) {

        // Ensure displayName and chatColor are not null
        displayName = displayName != null ? displayName : "Unknown";
        chatColor = chatColor != null ? chatColor : "";
        message = message != null ? message : "";
        
        String format;
        if(playerFaction == null) {
            format = Config.CHAT_FORMAT != null ? Config.CHAT_FORMAT : "<displayName>: ";
            return format.replace("<displayName>", displayName) + chatColor + message;
        }

        format = Config.CHAT_FORMAT_WITH_FACTION != null ? Config.CHAT_FORMAT_WITH_FACTION : "[<faction>] <displayName>: ";
        String factionName = playerFaction.getName(recipient);
        factionName = factionName != null ? factionName : "Unknown";
        
        return format
            .replace("<faction>", factionName)
            .replace("<displayName>", displayName) + chatColor + message;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onAsyncPlayerChat(AsyncPlayerChatEvent event) {
        if(!Config.CHAT_FORMAT_ENABLED) return;

        event.setCancelled(true);

        Player player = event.getPlayer();
        PlayerFaction faction = FactionsManager.getInstance().getPlayerFaction(player);

        String displayName = this.getPlayerDisplayName(player);
        String chatColor = Color.translate(instance.getChatColor(player));

        String message = player.hasPermission("lazarus.chat.color")
            ? Color.translate(event.getMessage())
            : event.getMessage();

        String consoleMessage = this.getChatMessage(faction, Bukkit.getConsoleSender(), displayName, chatColor, message);
        Bukkit.getConsoleSender().sendMessage(consoleMessage);

        event.getRecipients().forEach(recipient -> {
            Userdata userdata = Lazarus.getInstance().getUserdataManager().getUserdata(recipient);

            if((player != recipient && !player.hasPermission("lazarus.staff") && !userdata
                .getSettings().isPublicChat()) || userdata.isIgnoring(player)) return;

            String playerMessage = this.getChatMessage(faction, recipient, displayName, chatColor, message);

            // Use spigot method to avoid translation issues
            recipient.spigot().sendMessage(net.md_5.bungee.api.chat.TextComponent.fromLegacyText(playerMessage));
        });
    }
}
