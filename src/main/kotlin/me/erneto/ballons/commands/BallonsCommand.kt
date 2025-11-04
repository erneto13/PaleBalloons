package me.erneto.ballons.commands

import me.erneto.ballons.BalloonManager
import me.erneto.ballons.gui.BalloonGUI
import me.erneto.ballons.storage.Data
import me.erneto.ballons.utils.Msg
import org.bukkit.entity.Player
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Subcommand
import revxrsal.commands.bukkit.actor.BukkitCommandActor
import revxrsal.commands.bukkit.annotation.CommandPermission

@Command("balloons", "balloon", "globos", "globo")
class BalloonsCommand(private val balloonManager: BalloonManager) {

    @Command("balloons")
    suspend fun menu(actor: BukkitCommandActor) {
        if (!actor.isPlayer) {
            Msg.send(actor.sender(), "messages.only-players")
            return
        }

        val player = actor.asPlayer()!!
        BalloonGUI(balloonManager).open(player)
    }

    @Subcommand("equip")
    suspend fun equip(actor: BukkitCommandActor, balloonId: String) {
        if (!actor.isPlayer) {
            Msg.send(actor.sender(), "messages.only-players")
            return
        }

        val player = actor.asPlayer()!!

        val success = balloonManager.equipBalloon(player, balloonId)
        if (success) {
            Msg.send(player, "messages.balloon-equipped", "balloon" to balloonId)
            Msg.playSound(player, "entity.player.levelup")
        } else {
            Msg.send(player, "messages.balloon-not-found")
        }
    }

    @Subcommand("unequip")
    suspend fun unequip(actor: BukkitCommandActor) {
        if (!actor.isPlayer) {
            Msg.send(actor.sender(), "messages.only-players")
            return
        }

        val player = actor.asPlayer()!!
        balloonManager.unequipBalloon(player)
        Msg.send(player, "messages.balloon-unequipped")
    }

    @Subcommand("give")
    @CommandPermission("balloons.admin")
    suspend fun give(actor: BukkitCommandActor, playerName: String, balloonId: String) {
        val target = actor.sender().server.getPlayer(playerName)

        if (target == null) {
            Msg.send(actor.sender(), "messages.player-not-found")
            return
        }

        val balloon = balloonManager.getBalloon(balloonId)
        if (balloon == null) {
            Msg.send(actor.sender(), "messages.balloon-not-found")
            return
        }

        Data.addOwnedBalloon(target.uniqueId, balloonId)
        Msg.send(
            actor.sender(), "messages.balloon-given",
            "player" to target.name, "balloon" to balloonId
        )
        Msg.send(target, "messages.balloon-received", "balloon" to balloon.name)
    }

    @Subcommand("remove")
    @CommandPermission("balloons.admin")
    suspend fun remove(actor: BukkitCommandActor, playerName: String, balloonId: String) {
        val target = actor.sender().server.getPlayer(playerName)

        if (target == null) {
            Msg.send(actor.sender(), "messages.player-not-found")
            return
        }

        Data.removeOwnedBalloon(target.uniqueId, balloonId)
        Msg.send(
            actor.sender(), "messages.balloon-removed",
            "player" to target.name, "balloon" to balloonId
        )
    }

    @Subcommand("list")
    fun list(actor: BukkitCommandActor) {
        val balloons = balloonManager.getAllBalloons()

        Msg.send(actor.sender(), "messages.balloons-list-header")

        balloons.values.sortedBy { it.rarity.ordinal }.forEach { balloon ->
            Msg.sendParsed(
                actor.sender() as? Player ?: return,
                "  <gray>- <yellow>${balloon.id} <gray>(${balloon.name}<gray>)"
            )
        }
    }

    @Subcommand("info")
    fun info(actor: BukkitCommandActor) {
        val count = balloonManager.getActiveBalloonCount()
        Msg.sendParsed(
            actor.sender() as? Player ?: return,
            "<gold>Active Balloons: <yellow>$count"
        )
    }

    @Subcommand("reload")
    @CommandPermission("balloons.admin")
    fun reload(actor: BukkitCommandActor) {
        balloonManager.reload()
        Msg.send(actor.sender(), "messages.reload-success")
    }
}