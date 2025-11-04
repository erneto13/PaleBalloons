package me.erneto.ballons.listener

import me.erneto.ballons.gui.BalloonGUI
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent

class BalloonGUIListener : Listener {

    @EventHandler
    suspend fun onInventoryClick(event: InventoryClickEvent) {
        val gui = BalloonGUI.getGUI(event.inventory) ?: return
        event.isCancelled = true

        val player = event.whoClicked as? Player ?: return
        val slot = event.rawSlot

        if (slot < 0 || slot >= event.inventory.size) return

        gui.handleClick(player, slot, event.isRightClick)
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        val gui = BalloonGUI.getGUI(event.inventory) ?: return
        gui.handleClose()
    }
}