package me.erneto.ballons.gui

import me.erneto.ballons.BalloonManager
import me.erneto.ballons.models.BalloonData
import me.erneto.ballons.storage.Data
import me.erneto.ballons.utils.Msg
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack

class BalloonGUI(private val balloonManager: BalloonManager) {

    private lateinit var inventory: Inventory
    private lateinit var player: Player

    companion object {
        private val activeGUIs = mutableMapOf<Inventory, BalloonGUI>()

        fun getGUI(inventory: Inventory): BalloonGUI? = activeGUIs[inventory]
        fun removeGUI(inventory: Inventory) = activeGUIs.remove(inventory)
    }

    suspend fun open(player: Player) {
        this.player = player
        val title = Msg.parse(Msg.getMsg("gui.title"))
        inventory = Bukkit.createInventory(null, 54, title)

        setupItems()
        activeGUIs[inventory] = this
        player.openInventory(inventory)
    }

    private suspend fun setupItems() {
        val equipped = Data.getEquippedBalloon(player.uniqueId)

        // Show all balloons
        val owned = Data.getOwnedBalloons(player.uniqueId)
        val allBalloons = balloonManager.getAllBalloons()
            .values
            .sortedBy { it.rarity.ordinal }

        var slot = 10
        for (balloon in allBalloons) {
            if (slot > 23) break

            val isOwned = owned.contains(balloon.id)
            val isEquipped = balloon.id == equipped

            inventory.setItem(slot, createBalloonItem(balloon, isOwned, isEquipped))
            slot++
        }

        // Close button
        inventory.setItem(48, createCloseItem())
    }

    private fun createBalloonItem(
        balloon: BalloonData,
        isOwned: Boolean,
        isEquipped: Boolean
    ): ItemStack {
        val material = if (isOwned) Material.STRING else Material.BARRIER
        val item = ItemStack(material)
        val meta = item.itemMeta!!

        meta.displayName(Msg.parse("<${balloon.rarity.color}>${balloon.name}"))

        val lore = mutableListOf<String>()
        lore.addAll(balloon.description)
        lore.add("")
        lore.add(
            if (isOwned) {
                if (isEquipped) "<green>✓ Equipado" else "<yellow>Click para equipar"
            } else {
                "<red>✗ No desbloqueado"
            }
        )

        meta.lore(lore.map { Msg.parse(it) })

        if (isEquipped) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true)
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS)
        }

        item.itemMeta = meta
        return item
    }

    private fun createEquippedItem(balloon: BalloonData): ItemStack {
        val item = ItemStack(Material.STRING)
        val meta = item.itemMeta!!

        meta.displayName(Msg.parse("<gold>Equipped: ${balloon.name}"))
        meta.lore(
            listOf(
                Msg.parse("<gray>Right-click to unequip")
            )
        )

        meta.addEnchant(Enchantment.UNBREAKING, 1, true)
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS)

        item.itemMeta = meta
        return item
    }

    private fun createCloseItem(): ItemStack {
        val item = ItemStack(Material.IRON_DOOR)
        val meta = item.itemMeta!!
        meta.displayName(Msg.parse("<red>Cerrar"))
        item.itemMeta = meta
        return item
    }

    suspend fun handleClick(player: Player, slot: Int, isRightClick: Boolean) {
        when (slot) {
            4 -> {
                // Unequip if right-click on equipped item
                if (isRightClick) {
                    balloonManager.unequipBalloon(player)
                    Msg.send(player, "messages.balloon-unequipped")
                    open(player)
                }
            }

            in 18..44 -> {
                val allBalloons = balloonManager.getAllBalloons()
                    .values
                    .sortedBy { it.rarity.ordinal }

                val index = slot - 18
                if (index < allBalloons.size) {
                    val balloon = allBalloons.elementAt(index)
                    val owned = Data.getOwnedBalloons(player.uniqueId)

                    if (owned.contains(balloon.id)) {
                        val success = balloonManager.equipBalloon(player, balloon.id)
                        if (success) {
                            Msg.send(
                                player, "messages.balloon-equipped",
                                "balloon" to balloon.name
                            )
                            Msg.playSound(player, "entity.player.levelup")
                            open(player)
                        }
                    } else {
                        Msg.send(player, "messages.balloon-not-owned")
                    }
                }
            }

            49 -> player.closeInventory()
        }
    }

    fun handleClose() {
        activeGUIs.remove(inventory)
    }
}