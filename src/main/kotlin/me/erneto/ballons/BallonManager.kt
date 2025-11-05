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

    //constantes de física
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

        val armorStand = createBalloonArmorStand(player, balloon) ?: return false
        val leadAnchor = createLeadAnchor(player, armorStand)

        val active = ActiveBalloon(
            player = player.uniqueId,
            balloon = balloon,
            displayEntity = armorStand,
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

    private fun createBalloonArmorStand(player: Player, balloon: BalloonData): ArmorStand? {
        val location = calculateBalloonLocation(player, balloon)

        return player.world.spawn(location, ArmorStand::class.java) { stand ->
            stand.setBasePlate(false)
            stand.isVisible = false
            stand.isInvulnerable = true
            stand.canPickupItems = false
            stand.setGravity(false)
            stand.isSmall = false
            stand.isMarker = true
            stand.isCollidable = false
            stand.customName = "Balloon:${player.uniqueId}"
            stand.isCustomNameVisible = false

            val helmetItem = when (balloon.displayType) {
                BalloonDisplayType.BLOCK -> {
                    try {
                        val blockData = Bukkit.createBlockData(balloon.blockData!!)
                        ItemStack(blockData.material)
                    } catch (e: Exception) {
                        plugin.logger.warning("Invalid block data: ${balloon.blockData}")
                        ItemStack(Material.STONE)
                    }
                }

                BalloonDisplayType.ITEM -> {
                    val item = ItemStack(Material.PAPER)
                    val meta = item.itemMeta!!
                    meta.setCustomModelData(balloon.modelData!!)
                    item.itemMeta = meta
                    item
                }

                BalloonDisplayType.SKULL -> {
                    val skull = ItemStack(Material.PLAYER_HEAD)
                    val meta = skull.itemMeta as org.bukkit.inventory.meta.SkullMeta

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
                    skull
                }
            }

            stand.equipment!!.helmet = helmetItem

            stand.setMetadata("balloon_id", FixedMetadataValue(plugin, balloon.id))
            stand.setMetadata("pale_balloon", FixedMetadataValue(plugin, player.uniqueId.toString()))
        }
    }

    private fun createLeadAnchor(player: Player, balloon: ArmorStand): Chicken {
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

            val equipped = Data.getFromCache(uuid)?.equippedBalloon
            if (equipped != active.balloon.id) {
                cleanupBalloon(active)
                iterator.remove()
                continue
            }

            val currentLoc = player.location
            if (!currentLoc.world!!.equals(active.lastLocation.world) ||
                currentLoc.distanceSquared(active.lastLocation) > 100.0
            ) {
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

            if (!active.leadAnchor.isValid || !active.leadAnchor.isLeashed) {
                try {
                    active.leadAnchor.setLeashHolder(player)
                } catch (e: Exception) {
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

        if (movement < IDLE_THRESHOLD) {
            active.idleTime += 0.05
        } else {
            active.idleTime = 0.0
        }

        active.bobPhase = (active.bobPhase + BOB_SPEED * 0.05) % (2 * PI)
        active.swayPhase = (active.swayPhase + SWAY_SPEED * 0.05) % (2 * PI)

        val idleFactor = min(active.idleTime, 2.0) / 2.0
        val bobOffset = idleFactor * BOB_AMPLITUDE * sin(active.bobPhase)
        val swayOffset = idleFactor * SWAY_AMPLITUDE * sin(active.swayPhase)

        val angle = Math.toRadians(currentLoc.yaw.toDouble())
        val targetLoc = currentLoc.clone()

        targetLoc.add(
            -sin(angle) * FOLLOW_DISTANCE + (swayOffset * cos(angle)),
            bobOffset,
            -cos(angle) * FOLLOW_DISTANCE + (swayOffset * sin(angle))
        )

        targetLoc.add(0.0, BALLOON_HEIGHT + active.balloon.offset.second, 0.0)

        val toPlayer = currentLoc.toVector().subtract(active.displayEntity.location.toVector())
        val distance = toPlayer.length()

        if (distance > 0.1) {
            toPlayer.normalize().multiply(min(distance * 0.3, 0.5))
            targetLoc.add(toPlayer)
        }

        val tiltZ = toPlayer.z * 30.0 * -1.0
        val tiltX = toPlayer.x * 30.0 * -1.0 + idleFactor * 15.0 * sin(active.swayPhase)

        val armorStand = active.displayEntity as ArmorStand
        armorStand.teleport(targetLoc)
        armorStand.setRotation(currentLoc.yaw, 0f)

        val headPose = EulerAngle(
            Math.toRadians(tiltZ),
            0.0,
            Math.toRadians(tiltX)
        )
        armorStand.headPose = headPose

        active.leadAnchor.teleport(targetLoc.clone().add(0.0, 0.5, 0.0))

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

                if ((entity is ArmorStand && customName.startsWith("Balloon:")) ||
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
        val equippedBalloons = mutableMapOf<UUID, String>()
        activeBalloons.forEach { (uuid, active) ->
            equippedBalloons[uuid] = active.balloon.id
        }

        activeBalloons.values.forEach { cleanupBalloon(it) }
        activeBalloons.clear()
        balloonRegistry.clear()

        loadBalloons()

        equippedBalloons.forEach { (uuid, balloonId) ->
            val player = Bukkit.getPlayer(uuid)
            if (player != null && player.isOnline) {
                val balloon = balloonRegistry[balloonId]
                if (balloon != null) {
                    Bukkit.getScheduler().runTask(plugin, Runnable {
                        kotlinx.coroutines.runBlocking {
                            equipBalloon(player, balloonId)
                        }
                    })
                }
            }
        }
    }

    fun hasBalloon(uuid: UUID): Boolean = activeBalloons.containsKey(uuid)

    fun getActiveBalloonCount(): Int = activeBalloons.size
}