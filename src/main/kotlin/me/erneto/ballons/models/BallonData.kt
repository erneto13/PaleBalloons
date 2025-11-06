package me.erneto.ballons.models

data class BalloonData(
    val id: String,
    val name: String,
    val description: List<String>,
    val rarity: BalloonRarity,
    val permission: String?,
    val displayType: BalloonDisplayType,
    val blockData: String?,
    val skullTexture: String?,
    val scale: Triple<Float, Float, Float> = Triple(1f, 1f, 1f),
    val offset: Triple<Double, Double, Double> = Triple(0.0, 0.0, 0.0),
    val rotation: Float = 0f,
    val knotBlockData: String? = null
)