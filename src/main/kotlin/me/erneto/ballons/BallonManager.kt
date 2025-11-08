package me.erneto.ballons

import java.util.*
import kotlin.math.*
import me.erneto.ballons.models.ActiveBalloon
import me.erneto.ballons.models.BalloonData
import me.erneto.ballons.models.BalloonDisplayType
import me.erneto.ballons.models.BalloonRarity
import me.erneto.ballons.storage.Data
import me.erneto.ballons.utils.FileManager
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.*
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import org.bukkit.metadata.FixedMetadataValue
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.EulerAngle
import com.destroystokyo.paper.profile.ProfileProperty

class BalloonManager(private val plugin: PaleBalloons) {

    private val activeBalloons = mutableMapOf<UUID, ActiveBalloon>()
    private val balloonRegistry = mutableMapOf<String, BalloonData>()
    private lateinit var updateTask: BukkitTask

    private var BALLOON_HEIGHT = 2.5
    private var IDLE_THRESHOLD = 0.1
    private var BOB_SPEED = 2.0
    private var SWAY_SPEED = 1.5
    private var BOB_AMPLITUDE = 0.15
    private var SWAY_AMPLITUDE = 0.1
    private var FOLLOW_DISTANCE = 1.0
    private var MAX_DISTANCE = 5.0
    private var LEAD_ANCHOR_HEIGHT = 1.5
    private var LEAD_ANCHOR_OFFSET = 0.0
    private var CLEANUP_RADIUS = 10.0
    private var CATCH_UP_SPEED = 0.3
    private var MAX_CATCH_UP_SPEED = 0.5
    private var TILT_MULTIPLIER = 30.0
    private var SWAY_TILT_MULTIPLIER = 15.0
    private var MAX_IDLE_TIME = 2.0
    private var UPDATE_INTERVAL = 1L
    private var CLEANUP_STARTUP_DELAY = 40L
    private var RESPAWN_DELAY = 3L

    private var KNOT_ENABLED = true
    private var KNOT_OFFSET = -0.3
    private var KNOT_SCALE = 0.4f
    private var KNOT_BOB_SPEED = 1.5
    private var KNOT_BOB_AMPLITUDE = 0.1
    private var KNOT_SWAY_SPEED = 1.0
    private var KNOT_SWAY_AMPLITUDE = 0.05
    private var KNOT_TILT_MULTIPLIER = 20.0

    init {
        loadPhysicsConfig()
        loadBalloons()
        startUpdateTask()
    }

    private fun loadPhysicsConfig() {
        val config = FileManager.get("config") ?: return

        BALLOON_HEIGHT = config.getDouble("physics.balloon-height", 2.5)
        IDLE_THRESHOLD = config.getDouble("physics.idle-threshold", 0.1)
        BOB_SPEED = config.getDouble("physics.bob-speed", 2.0)
        SWAY_SPEED = config.getDouble("physics.sway-speed", 1.5)
        BOB_AMPLITUDE = config.getDouble("physics.bob-amplitude", 0.15)
        SWAY_AMPLITUDE = config.getDouble("physics.sway-amplitude", 0.1)
        FOLLOW_DISTANCE = config.getDouble("physics.follow-distance", 1.0)
        MAX_DISTANCE = config.getDouble("physics.max-distance", 5.0)
        LEAD_ANCHOR_HEIGHT = config.getDouble("physics.lead-anchor-height", 1.5)
        LEAD_ANCHOR_OFFSET = config.getDouble("physics.lead-anchor-offset", 0.0)
        CLEANUP_RADIUS = config.getDouble("physics.cleanup-radius", 10.0)
        CATCH_UP_SPEED = config.getDouble("physics.catch-up-speed", 0.3)
        MAX_CATCH_UP_SPEED = config.getDouble("physics.max-catch-up-speed", 0.5)
        TILT_MULTIPLIER = config.getDouble("physics.tilt-multiplier", 30.0)
        SWAY_TILT_MULTIPLIER = config.getDouble("physics.sway-tilt-multiplier", 15.0)
        MAX_IDLE_TIME = config.getDouble("physics.max-idle-time", 2.0)
        UPDATE_INTERVAL = config.getLong("physics.update-interval", 1L)
        CLEANUP_STARTUP_DELAY = config.getLong("entity.cleanup-startup-delay", 40L)
        RESPAWN_DELAY = config.getLong("entity.respawn-delay", 3L)

        KNOT_ENABLED = config.getBoolean("knot.enabled", true)
        KNOT_OFFSET = config.getDouble("knot.offset", -0.3)
        KNOT_SCALE = config.getDouble("knot.scale", 0.4).toFloat()
        KNOT_BOB_SPEED = config.getDouble("knot.bob-speed", 1.5)
        KNOT_BOB_AMPLITUDE = config.getDouble("knot.bob-amplitude", 0.1)
        KNOT_SWAY_SPEED = config.getDouble("knot.sway-speed", 1.0)
        KNOT_SWAY_AMPLITUDE = config.getDouble("knot.sway-amplitude", 0.05)
        KNOT_TILT_MULTIPLIER = config.getDouble("knot.tilt-multiplier", 20.0)

        plugin.logger.info("Physics configuration loaded")
    }

    private fun loadBalloons() {
        val config = FileManager.get("balloons") ?: return
        val section = config.getConfigurationSection("balloons") ?: return

        for (id in section.getKeys(false)) {
            val path = "balloons.$id"

            val displayType =
                when {
                    config.contains("$path.block-data") -> BalloonDisplayType.BLOCK
                    config.contains("$path.skull-texture") -> BalloonDisplayType.SKULL
                    else -> {
                        plugin.logger.warning("Balloon $id has no valid display type!")
                        continue
                    }
                }

            val balloon =
                BalloonData(
                    id = id,
                    name = config.getString("$path.name") ?: id,
                    description = config.getStringList("$path.description"),
                    rarity =
                        try {
                            BalloonRarity.valueOf(
                                config.getString("$path.rarity")?.uppercase() ?: "COMMON"
                            )
                        } catch (e: Exception) {
                            BalloonRarity.COMMON
                        },
                    permission = config.getString("$path.permission"),
                    displayType = displayType,
                    blockData = config.getString("$path.block-data"),
                    skullTexture = config.getString("$path.skull-texture"),
                    scale =
                        Triple(
                            config.getDouble("$path.scale.x", 1.0).toFloat(),
                            config.getDouble("$path.scale.y", 1.0).toFloat(),
                            config.getDouble("$path.scale.z", 1.0).toFloat()
                        ),
                    offset =
                        Triple(
                            config.getDouble("$path.offset.x", 0.0),
                            config.getDouble("$path.offset.y", 0.0),
                            config.getDouble("$path.offset.z", 0.0)
                        ),
                    rotation = config.getDouble("$path.rotation", 0.0).toFloat(),
                    knotBlockData = config.getString("$path.knot-block-data")
                )

            balloonRegistry[id] = balloon
            plugin.logger.info("Loaded balloon: $id ($displayType)")
        }
    }

    private fun startUpdateTask() {
        updateTask =
            Bukkit.getScheduler()
                .runTaskTimer(plugin, Runnable { updateAllBalloons() }, 0L, UPDATE_INTERVAL)
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
        val knotStand = if (KNOT_ENABLED) createKnotArmorStand(player, balloon, armorStand) else null
        val leadAnchor = createLeadAnchor(player, armorStand)

        val active =
            ActiveBalloon(
                player = player.uniqueId,
                balloon = balloon,
                displayEntity = armorStand,
                knotEntity = knotStand,
                leadAnchor = leadAnchor,
                lastLocation = player.location.clone(),
                idleTime = 0.0,
                bobPhase = 0.0,
                swayPhase = 0.0,
                knotBobPhase = 0.0,
                knotSwayPhase = 0.0
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

            val helmetItem =
                when (balloon.displayType) {
                    BalloonDisplayType.BLOCK -> {
                        try {
                            val blockData = Bukkit.createBlockData(balloon.blockData!!)
                            ItemStack(blockData.material)
                        } catch (e: Exception) {
                            plugin.logger.warning("Invalid block data: ${balloon.blockData}")
                            ItemStack(Material.STONE)
                        }
                    }

                    BalloonDisplayType.SKULL -> {
                        val skull = ItemStack(Material.PLAYER_HEAD)
                        val meta = skull.itemMeta as SkullMeta

                        val texture = balloon.skullTexture
                        if (texture != null) {
                            try {
                                val profile = Bukkit.getServer().createProfile(UUID.randomUUID())
                                profile.properties.add(ProfileProperty("textures", texture))
                                meta.playerProfile = profile
                            } catch (e: Exception) {
                                plugin.logger.warning("Invalid skull texture: $texture")
                            }
                        } else {
                            plugin.logger.warning("Skull texture is null for balloon: ${balloon.id}")
                        }

                        skull.itemMeta = meta
                        skull
                    }
                }

            stand.equipment!!.helmet = helmetItem

            stand.setMetadata("balloon_id", FixedMetadataValue(plugin, balloon.id))
            stand.setMetadata(
                "pale_balloon",
                FixedMetadataValue(plugin, player.uniqueId.toString())
            )
        }
    }

    private fun createKnotArmorStand(
        player: Player,
        balloon: BalloonData,
        balloonStand: ArmorStand
    ): ArmorStand? {
        val knotLoc = balloonStand.location.clone().add(0.0, KNOT_OFFSET, 0.0)

        return player.world.spawn(knotLoc, ArmorStand::class.java) { stand ->
            stand.setBasePlate(false)
            stand.isVisible = false
            stand.isInvulnerable = true
            stand.canPickupItems = false
            stand.setGravity(false)
            stand.isSmall = true
            stand.isMarker = true
            stand.isCollidable = false
            stand.customName = "BalloonKnot:${player.uniqueId}"
            stand.isCustomNameVisible = false

            val knotItem = if (balloon.knotBlockData != null) {
                try {
                    val blockData = Bukkit.createBlockData(balloon.knotBlockData)
                    ItemStack(blockData.material)
                } catch (e: Exception) {
                    plugin.logger.warning("Invalid knot block data: ${balloon.knotBlockData}")
                    ItemStack(Material.OAK_FENCE)
                }
            } else {
                ItemStack(Material.OAK_FENCE)
            }

            stand.equipment!!.helmet = knotItem

            stand.setMetadata("balloon_knot", FixedMetadataValue(plugin, balloon.id))
            stand.setMetadata(
                "pale_balloon_knot",
                FixedMetadataValue(plugin, player.uniqueId.toString())
            )
        }
    }

    private fun createLeadAnchor(player: Player, balloon: ArmorStand): Chicken {
        cleanupOldLeads(player.world, player.location)

        val anchorLoc =
            balloon.location.clone().add(0.0, LEAD_ANCHOR_HEIGHT + LEAD_ANCHOR_OFFSET, 0.0)

        val leadAnchor =
            player.world.spawn(anchorLoc, Chicken::class.java) { chicken ->
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

                chicken.setMetadata(
                    "pale_balloon_anchor",
                    FixedMetadataValue(plugin, player.uniqueId.toString())
                )

                try {
                    chicken.setLeashHolder(player)
                } catch (e: Exception) {
                    plugin.logger.warning("Failed to set leash: ${e.message}")
                }
            }

        return leadAnchor
    }

    private fun cleanupOldLeads(world: org.bukkit.World, location: Location) {
        world.getNearbyEntities(location, CLEANUP_RADIUS, CLEANUP_RADIUS, CLEANUP_RADIUS)
            .stream()
            .filter { entity ->
                when (entity) {
                    is Item -> entity.itemStack.type == Material.LEAD
                    is LeashHitch -> true
                    else -> false
                }
            }
            .forEach { it.remove() }
    }

    private fun calculateBalloonLocation(player: Player, balloon: BalloonData): Location {
        return player.location
            .clone()
            .add(
                balloon.offset.first,
                BALLOON_HEIGHT + balloon.offset.second,
                balloon.offset.third
            )
    }

    private fun shouldBalloonBeVisible(player: Player): Boolean {
        if (player.gameMode == GameMode.SPECTATOR) return false
        if (player.hasPotionEffect(PotionEffectType.INVISIBILITY)) return false

        //check vanish (Essentials, CMI, SuperVanish, PremiumVanish)
        if (player.hasMetadata("vanished")) {
            val vanishMeta = player.getMetadata("vanished")
            if (vanishMeta.isNotEmpty() && vanishMeta[0].asBoolean()) {
                return false
            }
        }

        return true
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

            //remove balloon if player is invisible/spectator/vanish
            if (!shouldBalloonBeVisible(player)) {
                cleanupBalloon(active)
                iterator.remove()
                continue
            }

            val currentLoc = player.location
            if (currentLoc.world!! != active.lastLocation.world ||
                currentLoc.distanceSquared(active.lastLocation) > 100.0
            ) {
                cleanupBalloon(active)
                iterator.remove()

                Bukkit.getScheduler()
                    .runTaskLater(
                        plugin,
                        Runnable {
                            if (player.isOnline) {
                                val balloonId = Data.getFromCache(uuid)?.equippedBalloon
                                if (balloonId != null) {
                                    Bukkit.getScheduler()
                                        .runTask(
                                            plugin,
                                            Runnable {
                                                kotlinx.coroutines.runBlocking {
                                                    equipBalloon(player, balloonId)
                                                }
                                            }
                                        )
                                }
                            }
                        },
                        RESPAWN_DELAY
                    )
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

        active.knotBobPhase = (active.knotBobPhase + KNOT_BOB_SPEED * 0.05) % (2 * PI)
        active.knotSwayPhase = (active.knotSwayPhase + KNOT_SWAY_SPEED * 0.05) % (2 * PI)

        val idleFactor = min(active.idleTime, MAX_IDLE_TIME) / MAX_IDLE_TIME
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
            toPlayer.normalize().multiply(min(distance * CATCH_UP_SPEED, MAX_CATCH_UP_SPEED))
            targetLoc.add(toPlayer)
        }

        val tiltZ = toPlayer.z * TILT_MULTIPLIER * -1.0
        val tiltX =
            toPlayer.x * TILT_MULTIPLIER * -1.0 +
                    idleFactor * SWAY_TILT_MULTIPLIER * sin(active.swayPhase)

        val armorStand = active.displayEntity as ArmorStand
        armorStand.teleport(targetLoc)
        armorStand.setRotation(currentLoc.yaw, 0f)

        val headPose = EulerAngle(Math.toRadians(tiltZ), 0.0, Math.toRadians(tiltX))
        armorStand.headPose = headPose

        active.knotEntity?.let { knot ->
            val knotBobOffset = idleFactor * KNOT_BOB_AMPLITUDE * sin(active.knotBobPhase)
            val knotSwayOffset = idleFactor * KNOT_SWAY_AMPLITUDE * sin(active.knotSwayPhase)

            val knotLoc = targetLoc.clone().add(
                knotSwayOffset * cos(angle),
                KNOT_OFFSET + knotBobOffset,
                knotSwayOffset * sin(angle)
            )

            val knotTiltZ = toPlayer.z * KNOT_TILT_MULTIPLIER * -1.0
            val knotTiltX = toPlayer.x * KNOT_TILT_MULTIPLIER * -1.0 +
                    idleFactor * (KNOT_TILT_MULTIPLIER * 0.5) * sin(active.knotSwayPhase)

            knot.teleport(knotLoc)
            knot.setRotation(currentLoc.yaw, 0f)
            (knot as ArmorStand).headPose = EulerAngle(
                Math.toRadians(knotTiltZ),
                0.0,
                Math.toRadians(knotTiltX)
            )
        }

        active.leadAnchor.teleport(
            targetLoc.clone().add(0.0, LEAD_ANCHOR_HEIGHT + LEAD_ANCHOR_OFFSET, 0.0)
        )

        if (active.displayEntity.location.distance(currentLoc) > MAX_DISTANCE) {
            active.displayEntity.teleport(currentLoc.clone().add(0.0, BALLOON_HEIGHT, 0.0))
            active.knotEntity?.teleport(
                active.displayEntity.location.clone().add(0.0, KNOT_OFFSET, 0.0)
            )
            active.leadAnchor.teleport(
                active.displayEntity
                    .location
                    .clone()
                    .add(0.0, LEAD_ANCHOR_HEIGHT + LEAD_ANCHOR_OFFSET, 0.0)
            )
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
        active.knotEntity?.remove()
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

                if ((entity is ArmorStand && (customName.startsWith("Balloon:") || customName.startsWith("BalloonKnot:"))) ||
                    (entity is Chicken && customName.startsWith("BalloonAnchor:"))
                ) {
                    try {
                        val prefix = when {
                            customName.startsWith("Balloon:") -> "Balloon:"
                            customName.startsWith("BalloonKnot:") -> "BalloonKnot:"
                            else -> "BalloonAnchor:"
                        }
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
        activeBalloons.forEach { (uuid, active) -> equippedBalloons[uuid] = active.balloon.id }

        activeBalloons.values.forEach { cleanupBalloon(it) }
        activeBalloons.clear()
        balloonRegistry.clear()

        FileManager.reload("config")
        FileManager.reload("balloons")

        loadPhysicsConfig()

        updateTask.cancel()
        startUpdateTask()

        loadBalloons()

        equippedBalloons.forEach { (uuid, balloonId) ->
            val player = Bukkit.getPlayer(uuid)
            if (player != null && player.isOnline) {
                val balloon = balloonRegistry[balloonId]
                if (balloon != null) {
                    Bukkit.getScheduler()
                        .runTask(
                            plugin,
                            Runnable {
                                kotlinx.coroutines.runBlocking {
                                    equipBalloon(player, balloonId)
                                }
                            }
                        )
                }
            }
        }
    }

    fun hasBalloon(uuid: UUID): Boolean = activeBalloons.containsKey(uuid)

    fun getActiveBalloonCount(): Int = activeBalloons.size

    fun getCleanupStartupDelay(): Long = CLEANUP_STARTUP_DELAY
}