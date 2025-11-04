package me.erneto.ballons.models

import org.bukkit.Location
import org.bukkit.entity.Chicken
import org.bukkit.entity.Display
import java.util.UUID

data class ActiveBalloon(
    val player: UUID,
    val balloon: BalloonData,
    val displayEntity: Display,
    val leadAnchor: Chicken,
    var lastLocation: Location,
    var idleTime: Double,
    var bobPhase: Double,
    var swayPhase: Double
)