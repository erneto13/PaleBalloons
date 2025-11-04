package me.erneto.ballons

import me.erneto.ballons.models.ActiveBalloon
import me.erneto.ballons.models.BalloonData
import me.erneto.ballons.models.BalloonDisplayType
import me.erneto.ballons.models.BalloonRarity
import me.erneto.ballons.storage.Data
import me.erneto.ballons.utils.FileManager
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.*
import org.bukkit.inventory.ItemStack
import org.bukkit.metadata.FixedMetadataValue
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.EulerAngle
import java.util.*
import kotlin.math.*

class BalloonManager(private val plugin: PaleBalloons) {

    private val activeBalloons = mutableMapOf<UUID, ActiveBalloon>()
    private val balloonRegistry = mutableMapOf<String, BalloonData>()
    private lateinit var updateTask: BukkitTask

    // Physics constants
    private val BALLOON_HEIGHT = 2.5
    private val IDLE_THRESHOLD = 0.1
    private val BOB_SPEED = 2.0
    private val SWAY_SPEED = 1.5
    private val BOB_AMPLITUDE = 0.15
    private val SWAY_AMPLITUDE = 0.1
    private val FOLLOW_DISTANCE = 1.0

    init {
        loadBalloons()
        startUpdateTask()
    }

    private fun loadBalloons() {
        val config = FileManager.get("balloons") ?: return

        val section = config.getConfigurationSection("balloons") ?: return

        for (id in section.getKeys(false)) {
            val path = "balloons.$id"

            val displayType = when {
                config.contains("$path.model-data") -> BalloonDisplayType.ITEM
                config.contains("$path.block-data") -> BalloonDisplayType.BLOCK
                config.contains("$path.skull-texture") -> BalloonDisplayType.SKULL
                else -> {
                    plugin.logger.warning("Balloon $id has no valid display type!")
                    continue
                }
            }

            val balloon = BalloonData(
                id = id,
                name = config.getString("$path.name") ?: id,
                description = config.getStringList("$path.description"),
                rarity = try {
                    BalloonRarity.valueOf(config.getString("$path.rarity")?.uppercase() ?: "COMMON")
                } catch (e: Exception) {
                    BalloonRarity.COMMON
                },
                permission = config.getString("$path.permission"),
                displayType = displayType,
                modelData = config.getInt("$path.model-data", -1).takeIf { it != -1 },
                blockData = config.getString("$path.block-data"),
                skullTexture = config.getString("$path.skull-texture"),
                scale = Triple(
                    config.getDouble("$path.scale.x", 1.0).toFloat(),
                    config.getDouble("$path.scale.y", 1.0).toFloat(),
                    config.getDouble("$path.scale.z", 1.0).toFloat()
                ),
                offset = Triple(
                    config.getDouble("$path.offset.x", 0.0),
                    config.getDouble("$path.offset.y", 0.0),
                    config.getDouble("$path.offset.z", 0.0)
                ),
                rotation = config.getDouble("$path.rotation", 0.0).toFloat()
            )

            balloonRegistry[id] = balloon
            plugin.logger.info("Loaded balloon: $id ($displayType)")
        }
    }

    private fun startUpdateTask() {
        updateTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            updateAllBalloons()
        }, 0L, 1L)
    }

    suspend fun equipBalloon(player: Player, balloonId: String): Boolean {
        val balloon = balloonRegistry[balloonId] ?: return false

        if (balloon.permission != null && !player.hasPermission(balloon.permission)) {
            return false
        }

        val owned = Data.getOwnedBalloons(player.uniqueId)
        if (!owned.contains(balloonId)) {
            return false
        }

        removeBalloon(player.uniqueId)

        val display = createBalloonDisplay(player, balloon) ?: return false
        val leadAnchor = createLeadAnchor(player, display)

        val active = ActiveBalloon(
            player = player.uniqueId,
            balloon = balloon,
            displayEntity = display,
            leadAnchor = leadAnchor,
            lastLocation = player.location.clone(),
            idleTime = 0.0,
            bobPhase = 0.0,
            swayPhase = 0.0
        )

        activeBalloons[player.uniqueId] = active
        Data.setEquippedBalloon(player.uniqueId, balloonId)

        return true
    }

    private fun createBalloonDisplay(player: Player, balloon: BalloonData): Display? {
        val location = calculateBalloonLocation(player, balloon)

        return when (balloon.displayType) {
            BalloonDisplayType.BLOCK -> {
                player.world.spawn(location, BlockDisplay::class.java) { entity ->
                    try {
                        entity.block = Bukkit.createBlockData(balloon.blockData!!)
                    } catch (e: Exception) {
                        plugin.logger.warning("Invalid block data: ${balloon.blockData}")
                        entity.block = Material.STONE.createBlockData()
                    }
                    configureBalloonDisplay(entity, balloon, player.uniqueId)
                }
            }

            BalloonDisplayType.ITEM -> {
                player.world.spawn(location, ItemDisplay::class.java) { entity ->
                    val item = ItemStack(Material.PAPER)
                    val meta = item.itemMeta!!
                    meta.setCustomModelData(balloon.modelData!!)
                    item.itemMeta = meta
                    entity.setItemStack(item)
                    configureBalloonDisplay(entity, balloon, player.uniqueId)
                }
            }

            BalloonDisplayType.SKULL -> {
                player.world.spawn(location, ItemDisplay::class.java) { entity ->
                    val skull = ItemStack(Material.PLAYER_HEAD)
                    val meta = skull.itemMeta as org.bukkit.inventory.meta.SkullMeta

                    // Set skull texture using player profile
                    val profile = Bukkit.createPlayerProfile(UUID.randomUUID())
                    val textures = profile.textures
                    try {
                        textures.skin = java.net.URL("http://textures.minecraft.net/texture/${balloon.skullTexture}")
                        profile.setTextures(textures)
                        meta.ownerProfile = profile
                    } catch (e: Exception) {
                        plugin.logger.warning("Invalid skull texture: ${balloon.skullTexture}")
                    }

                    skull.itemMeta = meta
                    entity.setItemStack(skull)
                    configureBalloonDisplay(entity, balloon, player.uniqueId)
                }
            }
        }
    }

    private fun configureBalloonDisplay(display: Display, balloon: BalloonData, playerId: UUID) {
        val transformation = display.transformation

        transformation.scale.set(
            balloon.scale.first,
            balloon.scale.second,
            balloon.scale.third
        )

        display.interpolationDuration = 2
        display.interpolationDelay = 0
        display.isPersistent = false
        display.isSilent = true
        display.customName = "Balloon:$playerId"
        display.isCustomNameVisible = false

        display.setMetadata("balloon_id", FixedMetadataValue(plugin, balloon.id))
        display.setMetadata("pale_balloon", FixedMetadataValue(plugin, playerId.toString()))
    }

    private fun createLeadAnchor(player: Player, balloon: Display): Chicken {
        cleanupOldLeads(player.world, player.location)

        val anchorLoc = balloon.location.clone().add(0.0, 0.5, 0.0)

        val leadAnchor = player.world.spawn(anchorLoc, Chicken::class.java) { chicken ->
            chicken.isInvulnerable = true
            chicken.isInvisible = true
            chicken.isSilent = true
            chicken.setBaby()
            chicken.ageLock = true
            chicken.setAware(false)
            chicken.isCollidable = false
            chicken.setAI(false)
            chicken.canPickupItems = false
            chicken.customName = "BalloonAnchor:${player.uniqueId}"
            chicken.isCustomNameVisible = false
            chicken.isPersistent = true

            chicken.setMetadata("pale_balloon_anchor", FixedMetadataValue(plugin, player.uniqueId.toString()))

            try {
                chicken.setLeashHolder(player)
            } catch (e: Exception) {
                plugin.logger.warning("Failed to set leash: ${e.message}")
            }
        }

        return leadAnchor
    }

    private fun cleanupOldLeads(world: org.bukkit.World, location: Location) {
        world.getNearbyEntities(location, 10.0, 10.0, 10.0).stream()
            .filter { entity ->
                when (entity) {
                    is org.bukkit.entity.Item -> entity.itemStack.type == Material.LEAD
                    is org.bukkit.entity.LeashHitch -> true
                    else -> false
                }
            }
            .forEach { it.remove() }
    }

    private fun calculateBalloonLocation(player: Player, balloon: BalloonData): Location {
        return player.location.clone().add(
            balloon.offset.first,
            BALLOON_HEIGHT + balloon.offset.second,
            balloon.offset.third
        )
    }

    private fun updateAllBalloons() {
        val iterator = activeBalloons.iterator()

        while (iterator.hasNext()) {
            val (uuid, active) = iterator.next()
            val player = Bukkit.getPlayer(uuid)

            if (player == null || !player.isOnline) {
                cleanupBalloon(active)
                iterator.remove()
                continue
            }

            // Check if balloon is still equipped
            val equipped = Data.getFromCache(uuid)?.equippedBalloon
            if (equipped != active.balloon.id) {
                cleanupBalloon(active)
                iterator.remove()
                continue
            }

            // Check for teleportation or large movements
            val currentLoc = player.location
            if (!currentLoc.world!!.equals(active.lastLocation.world) ||
                currentLoc.distanceSquared(active.lastLocation) > 100.0
            ) {

                // Respawn balloon after teleport
                cleanupBalloon(active)
                iterator.remove()

                Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                    if (player.isOnline) {
                        val balloonId = Data.getFromCache(uuid)?.equippedBalloon
                        if (balloonId != null) {
                            Bukkit.getScheduler().runTask(plugin, Runnable {
                                kotlinx.coroutines.runBlocking {
                                    equipBalloon(player, balloonId)
                                }
                            })
                        }
                    }
                }, 3L)
                continue
            }

            // Maintain leash
            if (!active.leadAnchor.isValid || !active.leadAnchor.isLeashed) {
                try {
                    active.leadAnchor.setLeashHolder(player)
                } catch (e: Exception) {
                    // Recreate if leash fails
                    cleanupBalloon(active)
                    iterator.remove()
                    continue
                }
            }

            updateBalloonPhysics(active, player, currentLoc)
        }
    }

    private fun updateBalloonPhysics(active: ActiveBalloon, player: Player, currentLoc: Location) {
        val movement = currentLoc.distance(active.lastLocation)

        // Update idle time
        if (movement < IDLE_THRESHOLD) {
            active.idleTime += 0.05
        } else {
            active.idleTime = 0.0
        }

        // Update animation phases
        active.bobPhase = (active.bobPhase + BOB_SPEED * 0.05) % (2 * PI)
        active.swayPhase = (active.swayPhase + SWAY_SPEED * 0.05) % (2 * PI)

        // Calculate idle animations
        val idleFactor = min(active.idleTime, 2.0) / 2.0
        val bobOffset = idleFactor * BOB_AMPLITUDE * sin(active.bobPhase)
        val swayOffset = idleFactor * SWAY_AMPLITUDE * sin(active.swayPhase)

        // Calculate target location
        val angle = Math.toRadians(currentLoc.yaw.toDouble())
        val targetLoc = currentLoc.clone()

        targetLoc.add(
            -sin(angle) * FOLLOW_DISTANCE + (swayOffset * cos(angle)),
            bobOffset,
            -cos(angle) * FOLLOW_DISTANCE + (swayOffset * sin(angle))
        )

        targetLoc.add(0.0, BALLOON_HEIGHT + active.balloon.offset.second, 0.0)

        // Smooth follow
        val toPlayer = currentLoc.toVector().subtract(active.displayEntity.location.toVector())
        val distance = toPlayer.length()

        if (distance > 0.1) {
            toPlayer.normalize().multiply(min(distance * 0.3, 0.5))
            targetLoc.add(toPlayer)
        }

        // Calculate tilt
        val tiltZ = toPlayer.z * 30.0 * -1.0
        val tiltX = toPlayer.x * 30.0 * -1.0 + idleFactor * 15.0 * sin(active.swayPhase)

        // Update display entity
        active.displayEntity.teleport(targetLoc)
        active.displayEntity.setRotation(currentLoc.yaw, 0f)

        val headPose = EulerAngle(
            Math.toRadians(tiltZ),
            0.0,
            Math.toRadians(tiltX)
        )

        if (active.displayEntity is ItemDisplay || active.displayEntity is BlockDisplay) {
            // Head pose doesn't apply to displays, but we keep rotation smooth
        }

        // Update lead anchor
        active.leadAnchor.teleport(targetLoc.clone().add(0.0, 0.5, 0.0))

        // Teleport check for desync
        if (active.displayEntity.location.distance(currentLoc) > 5.0) {
            active.displayEntity.teleport(currentLoc.clone().add(0.0, BALLOON_HEIGHT, 0.0))
            active.leadAnchor.teleport(active.displayEntity.location.clone().add(0.0, 0.5, 0.0))
        }

        active.lastLocation = currentLoc.clone()
    }

    suspend fun unequipBalloon(player: Player) {
        removeBalloon(player.uniqueId)
        Data.setEquippedBalloon(player.uniqueId, null)
    }

    private fun removeBalloon(uuid: UUID) {
        val active = activeBalloons.remove(uuid) ?: return
        cleanupBalloon(active)
    }

    private fun cleanupBalloon(active: ActiveBalloon) {
        try {
            active.leadAnchor.setLeashHolder(null)
        } catch (e: Exception) {
            // Ignore
        }
        active.leadAnchor.remove()
        active.displayEntity.remove()
    }

    suspend fun loadPlayerBalloon(player: Player) {
        val equipped = Data.getEquippedBalloon(player.uniqueId)
        if (equipped != null) {
            equipBalloon(player, equipped)
        }
    }

    fun removePlayerBalloon(playerId: UUID) {
        removeBalloon(playerId)
    }

    fun cleanupOrphanedEntities() {
        Bukkit.getWorlds().forEach { world ->
            world.entities.forEach { entity ->
                val customName = entity.customName ?: return@forEach

                if ((entity is Display && customName.startsWith("Balloon:")) ||
                    (entity is Chicken && customName.startsWith("BalloonAnchor:"))
                ) {

                    try {
                        val prefix = if (customName.startsWith("Balloon:")) "Balloon:" else "BalloonAnchor:"
                        val playerUUID = UUID.fromString(customName.substring(prefix.length))
                        val player = Bukkit.getPlayer(playerUUID)

                        if (player == null || !player.isOnline) {
                            entity.remove()
                        } else {
                            val equipped = Data.getFromCache(playerUUID)?.equippedBalloon
                            if (equipped == null) {
                                entity.remove()
                            }
                        }
                    } catch (e: Exception) {
                        entity.remove()
                    }
                }
            }
        }
    }

    fun shutdown() {
        updateTask.cancel()
        activeBalloons.values.forEach { cleanupBalloon(it) }
        activeBalloons.clear()
    }

    fun getAllBalloons(): Map<String, BalloonData> = balloonRegistry

    fun getBalloon(id: String): BalloonData? = balloonRegistry[id]

    fun reload() {
        activeBalloons.values.forEach { cleanupBalloon(it) }
        activeBalloons.clear()
        balloonRegistry.clear()
        loadBalloons()
    }

    fun hasBalloon(uuid: UUID): Boolean = activeBalloons.containsKey(uuid)

    fun getActiveBalloonCount(): Int = activeBalloons.size
}