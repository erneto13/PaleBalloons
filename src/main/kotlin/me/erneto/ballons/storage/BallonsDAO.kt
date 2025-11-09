package me.erneto.ballons.storage

import java.util.UUID
import kotlinx.coroutines.Dispatchers
import me.erneto.ballons.PaleBalloons
import me.erneto.ballons.models.PlayerBalloonData
import me.erneto.ballons.utils.FileManager
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

class BalloonsDAO(private val cache: Cache) {

    init {
        val config =
                FileManager.get("config") ?: throw IllegalStateException("Config file not found!")

        val dbType =
                config.getString("database.driver")?.lowercase()
                        ?: throw IllegalArgumentException("Database driver not specified in config")

        when (dbType) {
            "mysql" -> {
                val host = config.getString("database.host") ?: "localhost"
                val port = config.getInt("database.port", 3306)
                val databaseName = config.getString("database.name") ?: "database"
                val user = config.getString("database.user") ?: "root"
                val password = config.getString("database.password") ?: ""

                val url = "jdbc:mysql://$host:$port/$databaseName"
                Database.Companion.connect(
                        url = url,
                        driver = "com.mysql.cj.jdbc.Driver",
                        user = user,
                        password = password
                )
            }
            "h2" -> {
                PaleBalloons.getInstance().logger.info("Loading H2 database...")
                val databaseName = config.getString("database.name") ?: "balloons"
                val url = "jdbc:h2:file:./data/$databaseName;DB_CLOSE_DELAY=-1;"
                Database.Companion.connect(
                        url = url,
                        driver = "org.h2.Driver",
                        user = "root",
                        password = ""
                )
            }
            else ->
                    throw IllegalArgumentException(
                            "Database '$dbType' type is not supported! Available: 'H2', 'MySQL'."
                    )
        }

        transaction {
            SchemaUtils.create(PlayerBalloons)
            PaleBalloons.getInstance().logger.info("Database tables created/verified")
        }
    }

    suspend fun getPlayerData(playerId: UUID): PlayerBalloonData {
        // Check cache first
        cache.getPlayerData(playerId)?.let {
            return it
        }

        // Load from database
        var data =
                newSuspendedTransaction(Dispatchers.IO) {
                    PlayerBalloons.selectAll()
                            .where { PlayerBalloons.id eq playerId }
                            .singleOrNull()
                            ?.let {
                                val ownedStr = it[PlayerBalloons.ownedBalloons]
                                val owned =
                                        if (ownedStr.isNotEmpty()) {
                                            ownedStr.split(",").toMutableSet()
                                        } else {
                                            mutableSetOf()
                                        }

                                PlayerBalloonData(
                                        ownedBalloons = owned,
                                        equippedBalloon = it[PlayerBalloons.equippedBalloon]
                                )
                            }
                }

        if (data == null) {
            data = PlayerBalloonData()
        }

        cache.updatePlayerData(playerId, data)
        return data
    }

    suspend fun updatePlayerData(playerId: UUID, data: PlayerBalloonData) {
        newSuspendedTransaction(Dispatchers.IO) {
            val ownedStr = data.ownedBalloons.joinToString(",")

            PlayerBalloons.update({ PlayerBalloons.id eq playerId }) {
                it[ownedBalloons] = ownedStr
                it[equippedBalloon] = data.equippedBalloon
            }
        }

        cache.updatePlayerData(playerId, data)
    }

    suspend fun createPlayer(playerId: UUID) {
        if (hasData(playerId)) return

        newSuspendedTransaction(Dispatchers.IO) {
            PlayerBalloons.insert {
                it[id] = playerId
                it[ownedBalloons] = ""
                it[equippedBalloon] = null
            }
        }

        cache.updatePlayerData(playerId, PlayerBalloonData())
    }

    suspend fun deletePlayer(playerId: UUID) {
        newSuspendedTransaction(Dispatchers.IO) { PlayerBalloons.deleteWhere { id eq playerId } }
        cache.removePlayerData(playerId)
    }

    private suspend fun hasData(playerId: UUID): Boolean {
        cache.getPlayerData(playerId)?.let {
            return true
        }

        return newSuspendedTransaction(Dispatchers.IO) {
            PlayerBalloons.selectAll().where { PlayerBalloons.id eq playerId }.singleOrNull() !=
                    null
        }
    }
}
