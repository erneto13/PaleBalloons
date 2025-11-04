package me.erneto.ballons.storage

import org.jetbrains.exposed.sql.Table

object PlayerBalloons : Table("player_balloons") {
    val id = uuid("id")
    val ownedBalloons = text("owned_balloons").default("")
    val equippedBalloon = varchar("equipped_balloon", 64).nullable()

    override val primaryKey = PrimaryKey(id)
}