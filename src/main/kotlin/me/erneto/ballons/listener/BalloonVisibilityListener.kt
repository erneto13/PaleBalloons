package me.erneto.ballons.listener

import me.erneto.ballons.BalloonManager
import me.erneto.ballons.PaleBalloons
import me.erneto.ballons.storage.Data
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityPotionEffectEvent
import org.bukkit.event.player.PlayerGameModeChangeEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.potion.PotionEffectType

class BalloonVisibilityListener(
    private val balloonManager: BalloonManager,
    private val plugin: PaleBalloons
) : Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    fun onGameModeChange(event: PlayerGameModeChangeEvent) {
        val player = event.player

        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            if (event.newGameMode != GameMode.SPECTATOR && !balloonManager.hasBalloon(player.uniqueId)) {
                val equipped = Data.getFromCache(player.uniqueId)?.equippedBalloon
                if (equipped != null) {
                    Bukkit.getScheduler().runTask(plugin, Runnable {
                        kotlinx.coroutines.runBlocking {
                            balloonManager.equipBalloon(player, equipped)
                        }
                    })
                }
            }
        }, 5L)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerRespawn(event: PlayerRespawnEvent) {
        val player = event.player

        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            if (!balloonManager.hasBalloon(player.uniqueId)) {
                val equipped = Data.getFromCache(player.uniqueId)?.equippedBalloon
                if (equipped != null) {
                    Bukkit.getScheduler().runTask(plugin, Runnable {
                        kotlinx.coroutines.runBlocking {
                            balloonManager.equipBalloon(player, equipped)
                        }
                    })
                }
            }
        }, 10L)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPotionEffect(event: EntityPotionEffectEvent) {
        if (event.entity !is Player) return

        val player = event.entity as Player

        //check invisibility effect
        if (event.modifiedType == PotionEffectType.INVISIBILITY) {
            when (event.action) {
                EntityPotionEffectEvent.Action.REMOVED,
                EntityPotionEffectEvent.Action.CLEARED -> {
                    //invisibility removed, re-equip balloon
                    Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                        if (!balloonManager.hasBalloon(player.uniqueId)) {
                            val equipped = Data.getFromCache(player.uniqueId)?.equippedBalloon
                            if (equipped != null) {
                                Bukkit.getScheduler().runTask(plugin, Runnable {
                                    kotlinx.coroutines.runBlocking {
                                        balloonManager.equipBalloon(player, equipped)
                                    }
                                })
                            }
                        }
                    }, 5L)
                }
                else -> {}
            }
        }
    }
}