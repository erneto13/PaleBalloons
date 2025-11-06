package me.erneto.ballons.models

import org.bukkit.Location
import org.bukkit.entity.Chicken
import org.bukkit.entity.Entity
import java.util.UUID

data class ActiveBalloon(
    val player: UUID,
    val balloon: BalloonData,
    val displayEntity: Entity,
    val knotEntity: Entity?,
    val leadAnchor: Chicken,
    var lastLocation: Location,
    var idleTime: Double,
    var bobPhase: Double,
    var swayPhase: Double,
    var knotBobPhase: Double,
    var knotSwayPhase: Double
)