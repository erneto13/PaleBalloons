package me.erneto.ballons

import com.github.shynixn.mccoroutine.bukkit.registerSuspendingEvents
import me.erneto.ballons.commands.BalloonsCommand
import me.erneto.ballons.hook.PlaceholderAPIHook
import me.erneto.ballons.listener.BalloonGUIListener
import me.erneto.ballons.listener.BalloonProtectionListener
import me.erneto.ballons.listener.PlayerJoinListener
import me.erneto.ballons.listener.PlayerQuitListener
import me.erneto.ballons.storage.Data
import me.erneto.ballons.utils.FileManager
import me.erneto.ballons.utils.Filer
import org.bukkit.Bukkit
import revxrsal.commands.Lamp
import revxrsal.commands.bukkit.BukkitLamp
import revxrsal.commands.bukkit.actor.BukkitCommandActor
import revxrsal.zapper.ZapperJavaPlugin

class PaleBalloons : ZapperJavaPlugin() {

    companion object {
        private lateinit var instance: PaleBalloons
        fun getInstance(): PaleBalloons = instance
    }

    private lateinit var balloonManager: BalloonManager

    override fun onEnable() {
        instance = this

        initStorage()
        initManagers()
        initHooks()
        registerCommands()
        registerListeners()

        // Cleanup orphaned entities on startup
        Bukkit.getScheduler().runTaskLater(this, Runnable {
            balloonManager.cleanupOrphanedEntities()
        }, 40L)

        logger.info("PaleBalloons plugin enabled successfully!")
    }

    override fun onDisable() {
        if (::balloonManager.isInitialized) {
            balloonManager.shutdown()
            logger.info("All balloons removed")
        }

        logger.info("PaleBalloons plugin disabled successfully!")
    }

    private fun initStorage() {
        Filer.createFolders()
        FileManager.initialize()
        Data.load()
    }

    private fun initManagers() {
        balloonManager = BalloonManager(this)
    }

    private fun initHooks() {
        if (server.pluginManager.getPlugin("PlaceholderAPI") != null) {
            PlaceholderAPIHook().register()
            logger.info("PlaceholderAPI hook registered")
        }
    }

    private fun registerCommands() {
        val lamp: Lamp<BukkitCommandActor> = BukkitLamp.builder(this).build()
        lamp.register(BalloonsCommand(balloonManager))
        logger.info("Commands registered")
    }

    private fun registerListeners() {
        server.pluginManager.registerSuspendingEvents(PlayerJoinListener(balloonManager), this)
        server.pluginManager.registerSuspendingEvents(PlayerQuitListener(balloonManager), this)
        server.pluginManager.registerSuspendingEvents(BalloonGUIListener(), this)
        server.pluginManager.registerEvents(BalloonProtectionListener(this), this)
        logger.info("Listeners registered")
    }

    fun getBalloonManager(): BalloonManager = balloonManager
}
