package me.qiooip.lazarus.userdata;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import me.qiooip.lazarus.config.Config;
import me.qiooip.lazarus.userdata.event.UserdataValueChangeEvent;
import me.qiooip.lazarus.userdata.event.UserdataValueType;
import me.qiooip.lazarus.userdata.settings.Settings;
import org.apache.commons.lang.time.FastDateFormat;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class Userdata {

    private UUID uuid;
    private String name;

    private int kills;
    private int deaths;
    private int killstreak;
    private int highestKillstreak;

    private int balance;
    private int lives;

    private boolean reclaimUsed;
    private Settings settings;

    private List<UUID> ignoring;
    private List<String> notes;
    private List<String> lastKills;
    private List<String> lastDeaths;
    private Map<String, Long> kitDelays;

    private transient Location lastLocation;

    public Userdata(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;

        this.balance = Config.DEFAULT_BALANCE_PLAYER;
        this.lives = Config.DEFAULT_LIVES;

        this.settings = new Settings();

        this.ignoring = new ArrayList<>();
        this.notes = new ArrayList<>();
        this.lastKills = new ArrayList<>();
        this.lastDeaths = new ArrayList<>();
        this.kitDelays = new HashMap<>();
    }

    public Player getPlayer() {
        return Bukkit.getPlayer(this.uuid);
    }

    public void changeBalance(int newBalance) {
        this.balance = newBalance;
        new UserdataValueChangeEvent(this, UserdataValueType.BALANCE);
    }

    public void updateKillStats(String killMessage) {
        this.addKill();
        this.addLastKill(killMessage);
    }

    public void addKill() {
        this.kills++;
        new UserdataValueChangeEvent(this, UserdataValueType.KILLS);
    }

    public void updateDeathStats(String killMessage) {
        this.addDeath();
        this.addLastDeath(killMessage);
    }

    public void addDeath() {
        this.deaths++;
        new UserdataValueChangeEvent(this, UserdataValueType.DEATHS);
    }

    public void addKillstreak() {
        this.killstreak++;

        if(this.killstreak > this.highestKillstreak) {
            this.highestKillstreak = this.killstreak;
            new UserdataValueChangeEvent(this, UserdataValueType.HIGHEST_KILLSTREAK);
        }
    }

    public int resetKillstreak() {
        int temp = this.killstreak;
        this.killstreak = 0;

        return temp;
    }

    public void addLives(int amount) {
        this.lives += amount;
    }

    public void subtractLives(int amount) {
        this.lives -= amount;
    }

    public boolean isIgnoring(Player player) {
        return this.ignoring.contains(player.getUniqueId());
    }

    public void addIgnoring(Player player) {
        this.ignoring.add(player.getUniqueId());
    }

    public void removeIgnoring(Player player) {
        this.ignoring.remove(player.getUniqueId());
    }

    public void addLastKill(String killMessage) {
        this.addLastKillOrDeathEntry(this.lastKills, killMessage);
    }

    public void addLastDeath(String deathMessage) {
        this.addLastKillOrDeathEntry(this.lastDeaths, deathMessage);
    }

    private void addLastKillOrDeathEntry(List<String> list, String message) {
        FastDateFormat fastDateFormat = FastDateFormat.getInstance(Config
            .DATE_FORMAT, Config.TIMEZONE, Locale.ENGLISH);

        message = message.replace('§', '&');

        if(list.size() < 5) {
            list.add(0, fastDateFormat.format(System.currentTimeMillis()) + " - " + message);
            return;
        }

        list.remove(4);
        list.add(0, fastDateFormat.format(System.currentTimeMillis()) + " - " + message);
    }
}
