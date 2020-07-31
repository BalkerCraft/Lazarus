package me.qiooip.lazarus.commands.staff;

import me.qiooip.lazarus.commands.manager.BaseCommand;
import me.qiooip.lazarus.config.Config;
import me.qiooip.lazarus.config.Language;
import org.apache.commons.lang.time.DurationFormatUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.util.Arrays;
import java.util.Locale;
import java.util.StringJoiner;

public class LagCommand extends BaseCommand {

    public LagCommand() {
        super("lag", Arrays.asList("memory", "mem"), "lazarus.lag");
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        double tps = Bukkit.spigot().getTPS()[0];
        this.sendTPSInfo(sender, tps);
    }

    private void sendTPSInfo(CommandSender sender, double tps) {
        tps = Math.min(tps, 20.0);

        StringJoiner joiner = new StringJoiner("\n");

        Bukkit.getWorlds().forEach(world -> joiner.add(Language.LAG_COMMAND_WORLD_FORMAT
            .replace("<world>", world.getName())
            .replace("<loaded_chunks>", String.valueOf(world.getLoadedChunks().length))
            .replace("<entities>", String.valueOf(world.getEntities().size()))));

        double finalTps = tps;

        String uptime = DurationFormatUtils.formatDurationWords(System.currentTimeMillis() - Config.START_TIME, true, true);

        Language.LAG_COMMAND_MESSAGE.forEach(line -> sender.sendMessage(line
            .replace("<tps>", String.format(Locale.ROOT, "%.2f", finalTps))
            .replace("<uptime>", uptime)
            .replace("<max_memory>", String.valueOf(Runtime.getRuntime().maxMemory() / 1024 / 1024))
            .replace("<allocated_memory>", String.valueOf(Runtime.getRuntime().totalMemory() / 1024 / 1024))
            .replace("<free_memory>", String.valueOf(Runtime.getRuntime().freeMemory() / 1024 / 1024))
            .replace("<worlds>", joiner.toString())));
    }
}

