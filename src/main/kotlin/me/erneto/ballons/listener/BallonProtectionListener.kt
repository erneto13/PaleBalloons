package me.erneto.ballons.listener

import me.erneto.ballons.PaleBalloons
import org.bukkit.entity.*
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.hanging.HangingBreakByEntityEvent
import org.bukkit.event.hanging.HangingBreakEvent
import org.bukkit.event.player.PlayerInteractAtEntityEvent
import org.bukkit.event.player.PlayerUnleashEntityEvent

class BalloonProtectionListener(private val plugin: PaleBalloons) : Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerInteractAtEntity(event: PlayerInteractAtEntityEvent) {
        val entity = event.rightClicked

        // Protect balloon display entities
        if (entity is Display && entity.customName?.startsWith("Balloon:") == true) {
            event.isCancelled = true
            return
        }

        // Protect lead anchors
        if (entity is Chicken && entity.customName?.startsWith("BalloonAnchor:") == true) {
            event.isCancelled = true
            return
        }

        // Protect leash hitches connected to balloons
        if (entity is LeashHitch) {
            entity.getNearbyEntities(5.0, 5.0, 5.0).forEach { nearby ->
                if ((nearby is Chicken && nearby.customName?.startsWith("BalloonAnchor:") == true) ||
                    (nearby is Display && nearby.customName?.startsWith("Balloon:") == true)) {
                    event.isCancelled = true
                    return
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerUnleashEntity(event: PlayerUnleashEntityEvent) {
        val entity = event.entity

        if (entity is Chicken && entity.customName?.startsWith("BalloonAnchor:") == true) {
            event.isCancelled = true
            event.isDropLeash = false
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onHangingBreak(event: HangingBreakEvent) {
        if (event.entity is LeashHitch) {
            val hitch = event.entity as LeashHitch
            hitch.getNearbyEntities(5.0, 5.0, 5.0).forEach { nearby ->
                if ((nearby is Chicken && nearby.customName?.startsWith("BalloonAnchor:") == true) ||
                    (nearby is Display && nearby.customName?.startsWith("Balloon:") == true)) {
                    event.isCancelled = true
                    return
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onHangingBreakByEntity(event: HangingBreakByEntityEvent) {
        if (event.entity is LeashHitch) {
            val hitch = event.entity as LeashHitch
            hitch.getNearbyEntities(5.0, 5.0, 5.0).forEach { nearby ->
                if ((nearby is Chicken && nearby.customName?.startsWith("BalloonAnchor:") == true) ||
                    (nearby is Display && nearby.customName?.startsWith("Balloon:") == true)) {
                    event.isCancelled = true
                    return
                }
            }
        }
    }
}