package me.erneto.ballons.models

data class PlayerBalloonData(
    val ownedBalloons: MutableSet<String> = mutableSetOf(),
    val equippedBalloon: String? = null
)