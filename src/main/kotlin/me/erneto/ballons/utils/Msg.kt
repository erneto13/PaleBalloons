package me.erneto.ballons.utils

import me.clip.placeholderapi.PlaceholderAPI
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.title.TitlePart
import org.bukkit.Sound
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

object Msg {
    private val miniMsg = MiniMessage.miniMessage()

    fun parse(msg: String, player: Player): Component {
        return miniMsg.deserialize(placeholder(msg, player))
    }

    fun parse(msg: String): Component {
        return miniMsg.deserialize(msg)
    }

    fun parseItem(msg: String): Component {
        return miniMsg.deserialize(msg).decoration(TextDecoration.ITALIC, false)
    }

    fun parseItem(msg: String, player: Player): Component {
        return miniMsg.deserialize(placeholder(msg, player)).decoration(TextDecoration.ITALIC, false)
    }

    fun send(player: Player, path: String) {
        player.sendMessage(parse(getMsg(path), player))
    }

    fun send(player: Player, path: String, vararg replacements: Pair<String, String>) {
        val msg = replacePlaceholders(getMsg(path), *replacements)
        player.sendMessage(parse(msg, player))
    }

    fun send(sender: CommandSender, path: String) {
        sender.sendMessage(parse(getMsg(path)))
    }

    fun send(sender: CommandSender, path: String, vararg replacements: Pair<String, String>) {
        val msg = replacePlaceholders(getMsg(path), *replacements)
        if (sender is Player) {
            sender.sendMessage(parse(msg, sender))
        } else {
            sender.sendMessage(parse(msg))
        }
    }

    fun sendList(sender: CommandSender, path: String) {
        val messages = getMsgList(path)
        if (sender is Player) {
            messages.forEach { msg ->
                sender.sendMessage(parse(msg, sender))
            }
        } else {
            messages.forEach { msg ->
                sender.sendMessage(parse(msg))
            }
        }
    }

    fun sendParsed(player: Player, msg: String) {
        player.sendMessage(parse(msg, player))
    }

    fun sendTitle(player: Player, title: String) {
        player.sendTitlePart(TitlePart.TITLE, parse(title, player))
    }

    fun sendSubtitle(player: Player, subtitle: String) {
        player.sendTitlePart(TitlePart.SUBTITLE, parse(subtitle, player))
    }

    fun placeholder(msg: String, player: Player): String {
        return PlaceholderAPI.setPlaceholders(player, msg)
    }

    fun sendActionbar(player: Player, bar: String) {
        player.sendActionBar(parse(bar, player))
    }

    fun playSound(player: Player, sound: String) {
        try {
            player.playSound(player.location, Sound.valueOf(sound.uppercase()), 1f, 1f)
        } catch (e: IllegalArgumentException) {
            player.playSound(player.location, sound, 1f, 1f)
        }
    }

    fun getMsg(path: String): String {
        return FileManager.get("lang")?.getString(path) ?: "Message not found: $path"
    }

    fun getMsgList(path: String): List<String> {
        return FileManager.get("lang")?.getStringList(path)
            ?: listOf("Message list not found: $path")
    }

    private fun replacePlaceholders(msg: String, vararg replacements: Pair<String, String>): String {
        var result = msg
        replacements.forEach { (key, value) ->
            result = result.replace("%$key%", value)
        }
        return result
    }
}