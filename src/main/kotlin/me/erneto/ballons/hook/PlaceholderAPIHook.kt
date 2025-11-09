package me.erneto.ballons.hook

import me.clip.placeholderapi.expansion.PlaceholderExpansion
import me.erneto.ballons.PaleBalloons
import me.erneto.ballons.storage.Data
import org.bukkit.entity.Player

class PlaceholderAPIHook : PlaceholderExpansion() {

    private val plugin = PaleBalloons.getInstance()

    override fun getIdentifier(): String = "balloons"

    override fun getAuthor(): String = "erneto13"

    override fun getVersion(): String = "1.0.0"

    override fun persist(): Boolean = true

    override fun onPlaceholderRequest(player: Player?, params: String): String? {
        if (player == null) return null

        val cached = Data.getFromCache(player.uniqueId) ?: return null

        return when (params) {
            //equipped balloon NAME
            "equipped" -> {
                val balloonId = cached.equippedBalloon ?: return "None"
                val balloon = plugin.getBalloonManager().getBalloon(balloonId)
                balloon?.name ?: "None"
            }

            //equipped balloon ID
            "equipped_id" -> cached.equippedBalloon ?: "None"

            //owned count
            "owned_count" -> cached.ownedBalloons.size.toString()

            //total available
            "total_balloons" -> plugin.getBalloonManager().getAllBalloons().size.toString()

            //collection progress percentage
            "collection_progress" -> {
                val total = plugin.getBalloonManager().getAllBalloons().size
                val owned = cached.ownedBalloons.size
                if (total == 0) "0"
                else String.format("%.1f", (owned.toDouble() / total) * 100)
            }

            //has specific balloon
            else -> {
                if (params.startsWith("has_")) {
                    val balloonId = params.removePrefix("has_")
                    cached.ownedBalloons.contains(balloonId).toString()
                } else if (params.startsWith("equipped_")) {
                    val balloonId = params.removePrefix("equipped_")
                    (cached.equippedBalloon == balloonId).toString()
                } else {
                    null
                }
            }
        }
    }
}