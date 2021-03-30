package me.qiooip.lazarus.factions.commands.player;

import me.qiooip.lazarus.commands.manager.SubCommand;
import me.qiooip.lazarus.config.Language;
import me.qiooip.lazarus.factions.Faction;
import me.qiooip.lazarus.factions.FactionsManager;
import me.qiooip.lazarus.factions.enums.Role;
import me.qiooip.lazarus.factions.type.PlayerFaction;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class FocusCommand extends SubCommand {

    public FocusCommand() {
        super("focus", true);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if(args.length == 0) {
            sender.sendMessage(Language.FACTION_PREFIX + Language.FACTIONS_FOCUS_USAGE);
            return;
        }

        Player player = (Player) sender;
        PlayerFaction faction = FactionsManager.getInstance().getPlayerFaction(player);

        if(faction == null) {
            player.sendMessage(Language.FACTION_PREFIX + Language.FACTIONS_NOT_IN_FACTION_SELF);
            return;
        }

        if(!faction.getMember(player).getRole().isAtLeast(Role.CAPTAIN)) {
            player.sendMessage(Language.FACTION_PREFIX + Language.FACTIONS_NO_PERMISSION
                .replace("<role>", Role.getName(Role.CAPTAIN)));
            return;
        }

        Faction target = FactionsManager.getInstance().getAnyFaction(args[0]);

        if(!(target instanceof PlayerFaction)) {
            player.sendMessage(Language.FACTION_PREFIX + Language.FACTIONS_FACTION_DOESNT_EXIST.replace("<argument>", args[0]));
            return;
        }

        if(faction.getId().equals(target.getId())) {
            player.sendMessage(Language.FACTION_PREFIX + Language.FACTIONS_FOCUS_CANNOT_FOCUS);
            return;
        }

        PlayerFaction targetFaction = (PlayerFaction) target;

        if(faction.isFocusing(targetFaction)) {
            player.sendMessage(Language.FACTION_PREFIX + Language.FACTIONS_FOCUS_ALREADY_FOCUSING
                .replace("<faction>", target.getName()));
            return;
        }

        faction.focusFaction(targetFaction);
        Language.FACTIONS_FOCUS_FOCUSED.forEach(line -> faction.sendMessage(line.replace("<faction>", target.getName())));
    }
}
