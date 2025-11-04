package me.erneto.ballons.models

enum class BalloonRarity(val displayName: String, val color: String) {
    COMMON("Común", "<gray>"),
    UNCOMMON("Poco Común", "<green>"),
    RARE("Raro", "<blue>"),
    EPIC("Épico", "<light_purple>"),
    MYTHIC("Mítico", "<red>"),
    LEGENDARY("Legendario", "<gold>")
}