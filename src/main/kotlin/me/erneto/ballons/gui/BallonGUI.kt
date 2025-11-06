package me.erneto.ballons.gui

import me.erneto.ballons.BalloonManager
import me.erneto.ballons.models.BalloonData
import me.erneto.ballons.models.BalloonDisplayType
import me.erneto.ballons.storage.Data
import me.erneto.ballons.utils.Msg
import org.bukkit.Bukkit
import org.bukkit.Bukkit.createBlockData
import org.bukkit.Bukkit.createPlayerProfile
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta

class BalloonGUI(private val balloonManager: BalloonManager) {

    private lateinit var inventory: Inventory
    private lateinit var player: Player
    private var currentPage = 0
    private val balloonsPerPage = 14 // 7 slots per row, 2 rows

    companion object {
        private val activeGUIs = mutableMapOf<Inventory, BalloonGUI>()

        fun getGUI(inventory: Inventory): BalloonGUI? = activeGUIs[inventory]
        fun removeGUI(inventory: Inventory) = activeGUIs.remove(inventory)
    }

    fun open(player: Player, page: Int = 0) {
        this.player = player
        this.currentPage = page
        val title = Msg.parse(Msg.getMsg("gui.title"))
        inventory = Bukkit.createInventory(null, 54, title)

        setupItems()
        activeGUIs[inventory] = this
        player.openInventory(inventory)
    }

    private fun setupItems() {
        inventory.clear()

        val allBalloons = balloonManager.getAllBalloons()
            .values
            .sortedBy { it.rarity.ordinal }

        //calculate total pages
        val totalPages = (allBalloons.size + balloonsPerPage - 1) / balloonsPerPage
        val startIndex = currentPage * balloonsPerPage
        val endIndex = minOf(startIndex + balloonsPerPage, allBalloons.size)
        val balloonsOnPage = allBalloons.subList(startIndex, endIndex)

        //balloons in slots 10-16 (first row)
        val firstRowSlots = listOf(10, 11, 12, 13, 14, 15, 16)
        //balloons in slots 19-25 (second row)
        val secondRowSlots = listOf(19, 20, 21, 22, 23, 24, 25)
        val allSlots = firstRowSlots + secondRowSlots

        balloonsOnPage.forEachIndexed { index, balloon ->
            if (index < allSlots.size) {
                inventory.setItem(allSlots[index], createBalloonItem(balloon))
            }
        }

        val separatorSlots = listOf(37, 38, 39, 40, 41, 42, 43)
        separatorSlots.forEach { slot ->
            inventory.setItem(slot, createSeparatorItem())
        }

        if (currentPage > 0) {
            inventory.setItem(46, createPreviousPageItem())
        }

        if (player.hasPermission("balloons.admin")) {
            inventory.setItem(48, createNewBalloonItem())
        }

        inventory.setItem(50, createCloseItem())

        if (currentPage < totalPages - 1) {
            inventory.setItem(52, createNextPageItem())
        }
    }

    private fun createBalloonItem(balloon: BalloonData): ItemStack {
        val item = when (balloon.displayType) {
            BalloonDisplayType.BLOCK -> {
                try {
                    val blockData = createBlockData(balloon.blockData!!)
                    ItemStack(blockData.material)
                } catch (e: Exception) {
                    ItemStack(Material.STONE)
                }
            }

            BalloonDisplayType.SKULL -> {
                val skull = ItemStack(Material.PLAYER_HEAD)
                val skullMeta = skull.itemMeta as SkullMeta

                val profile = createPlayerProfile(java.util.UUID.randomUUID())
                val textures = profile.textures
                try {
                    textures.skin = java.net.URL("http://textures.minecraft.net/texture/${balloon.skullTexture}")
                    profile.setTextures(textures)
                    skullMeta.ownerProfile = profile
                } catch (e: Exception) {
                }

                skull.itemMeta = skullMeta
                skull
            }
        }

        val meta = item.itemMeta!!
        meta.displayName(Msg.parseItem("${balloon.name}"))

        val lore = mutableListOf<String>()
        lore.add("")
        lore.add("<#f7db29>Right-click to edit")
        lore.add("<#ea062c>Shift + Right-click to delete")

        meta.lore(lore.map { Msg.parseItem(it) })
        item.itemMeta = meta
        return item
    }

    private fun createSeparatorItem(): ItemStack {
        val item = ItemStack(Material.GLASS_PANE)
        val meta = item.itemMeta!!
        meta.displayName(Msg.parse(" "))
        meta.isHideTooltip = true
        item.itemMeta = meta
        return item
    }

    private fun createPreviousPageItem(): ItemStack {
        val item = ItemStack(Material.ARROW)
        val meta = item.itemMeta!!
        meta.displayName(Msg.parseItem("<#4d94eb>Previous Page"))
        meta.lore(listOf(Msg.parseItem("<#c1c1c1>Page $currentPage")))
        item.itemMeta = meta
        return item
    }

    private fun createNextPageItem(): ItemStack {
        val item = ItemStack(Material.ARROW)
        val meta = item.itemMeta!!
        meta.displayName(Msg.parseItem("<#4d94eb>Next Page"))
        meta.lore(listOf(Msg.parseItem("<#c1c1c1>Page ${currentPage + 2}")))
        item.itemMeta = meta
        return item
    }

    private fun createNewBalloonItem(): ItemStack {
        val item = ItemStack(Material.ANVIL)
        val meta = item.itemMeta!!
        meta.displayName(Msg.parseItem("<#4d94eb><bold>New Balloon"))
        item.itemMeta = meta
        return item
    }

    private fun createCloseItem(): ItemStack {
        val item = ItemStack(Material.IRON_DOOR)
        val meta = item.itemMeta!!
        meta.displayName(Msg.parseItem("<#ea062c><bold>Close"))
        item.itemMeta = meta
        return item
    }

    suspend fun handleClick(
        player: Player,
        slot: Int,
        isRightClick: Boolean,
        isShiftClick: Boolean,
        clickedItem: ItemStack?
    ) {
        when (slot) {
            46 -> {
                if (currentPage > 0) {
                    open(player, currentPage - 1)
                }
            }

            48 -> {
                if (player.hasPermission("balloons.admin")) {
                    player.closeInventory()
                    Msg.send(player, "<#ea062c>TODO: Soon")
                }
            }

            50 -> {
                player.closeInventory()
            }

            52 -> {
                val allBalloons = balloonManager.getAllBalloons().values
                val totalPages = (allBalloons.size + balloonsPerPage - 1) / balloonsPerPage
                if (currentPage < totalPages - 1) {
                    open(player, currentPage + 1)
                }
            }

            in listOf(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25) -> {
                val allSlots = listOf(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25)
                val index = allSlots.indexOf(slot)

                if (index != -1) {
                    val allBalloons = balloonManager.getAllBalloons()
                        .values
                        .sortedBy { it.rarity.ordinal }

                    val startIndex = currentPage * balloonsPerPage
                    val balloonIndex = startIndex + index

                    if (balloonIndex < allBalloons.size) {
                        val balloon = allBalloons.elementAt(balloonIndex)

                        if (isShiftClick && isRightClick) {
                            if (player.hasPermission("balloons.admin")) {
                                Msg.send(player, "<ea062c>Soon :: ${balloon.name}")
                            }
                        } else if (!isRightClick) {
                            if (player.hasPermission("balloons.admin")) {
                                player.closeInventory()
                                Msg.send(player, "<#ea062c>Soon :: ${balloon.name}")
                            }
                        }
                    }
                }
            }
        }
    }

    fun handleClose() {
        activeGUIs.remove(inventory)
    }
}