package me.qiooip.lazarus.factions.commands.player;

import me.qiooip.lazarus.commands.manager.SubCommand;
import me.qiooip.lazarus.config.Config;
import me.qiooip.lazarus.config.Language;
import me.qiooip.lazarus.factions.FactionsManager;
import me.qiooip.lazarus.factions.type.PlayerFaction;
import me.qiooip.lazarus.utils.StringUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;

public class RallyCommand extends SubCommand {

    public RallyCommand() {
        super("rally", Collections.singletonList("setrally"), true);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        Player player = (Player) sender;

        PlayerFaction faction = FactionsManager.getInstance().getPlayerFaction(player);

        if(faction == null) {
            player.sendMessage(Language.FACTION_PREFIX + Language.FACTIONS_NOT_IN_FACTION_SELF);
            return;
        }

        faction.setRallyLocation(player.getLocation());

        String location = Config.FACTION_RALLY_INCLUDE_Y_COORDINATE
            ? StringUtils.getLocationNameWithWorld(player.getLocation())
            : StringUtils.getLocationNameWithWorldWithoutY(player.getLocation());

        faction.sendMessage(Language.FACTION_PREFIX + Language.FACTIONS_RALLY_SET
            .replace("<player>", player.getName())
            .replace("<location>", location));
    }
}
