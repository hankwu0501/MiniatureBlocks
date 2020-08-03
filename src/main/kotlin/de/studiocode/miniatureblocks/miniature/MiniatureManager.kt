package de.studiocode.miniatureblocks.miniature

import de.studiocode.miniatureblocks.MiniatureBlocks
import de.studiocode.miniatureblocks.resourcepack.model.MainModelData.CustomModel
import de.studiocode.miniatureblocks.utils.getTargetEntity
import org.bukkit.*
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.inventory.InventoryCreativeEvent
import org.bukkit.event.player.PlayerInteractAtEntityEvent
import org.bukkit.event.world.ChunkLoadEvent
import org.bukkit.event.world.ChunkUnloadEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType.*

class MiniatureManager(private val plugin: MiniatureBlocks) : Listener {

    private val modelNameKey = NamespacedKey(plugin, "modelName")
    private val modelDataKey = NamespacedKey(plugin, "customModelData")
    private val rotationKey = NamespacedKey(plugin, "rotation")

    val playerAutoRotationMap = HashMap<Player, Float>()
    val playerRotationMap = HashMap<Player, Float>()
    private val rotatingArmorStands = HashMap<ArmorStand, Float>()
    
    init {
        Bukkit.getServer().pluginManager.registerEvents(this, plugin)
        Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::handleTick, 0, 1)
        
        Bukkit.getWorlds().forEach { it.loadedChunks.forEach(this::handleChunkLoad) }
    }

    private fun spawnArmorStandMiniature(location: Location, itemStack: ItemStack, customModel: CustomModel) {
        location.add(0.5, 0.0, 0.5)
        val armorStand = location.world!!.spawnEntity(location, EntityType.ARMOR_STAND) as ArmorStand
        armorStand.equipment?.helmet = itemStack
        armorStand.isVisible = false
        armorStand.isCollidable = false
        armorStand.setGravity(false)

        val dataContainer = armorStand.persistentDataContainer
        dataContainer.set(modelNameKey, STRING, customModel.name)
        dataContainer.set(modelDataKey, INTEGER, customModel.customModelData)
    }

    private fun setMiniatureRotation(armorStand: ArmorStand, rotation: Float) {
        val dataContainer = armorStand.persistentDataContainer
        if (rotation != 0f) {
            dataContainer.set(rotationKey, FLOAT, rotation)
            rotatingArmorStands[armorStand] = rotation
        } else {
            dataContainer.remove(rotationKey)
            rotatingArmorStands.remove(armorStand)
            armorStand.teleport(armorStand.location.also { it.yaw = 0f })
        }
    }

    fun removeMiniatureArmorStands(customModel: CustomModel) {
        for (world in Bukkit.getWorlds()) {
            world.entities
                    .filterIsInstance<ArmorStand>()
                    .filter { it.isMiniature(customModel) }
                    .forEach(ArmorStand::remove)
        }
    }

    private fun handleTick() {
        rotatingArmorStands.forEach { (armorStand, rotation) -> 
            val location = armorStand.location
            location.yaw += rotation
            armorStand.teleport(location)
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun handleBlockPlace(event: BlockPlaceEvent) {
        val itemStack = event.itemInHand.clone()
        itemStack.amount = 1
        if (itemStack.isMiniatureLike()) {
            event.isCancelled = true
            val mainModelData = plugin.resourcePack.mainModelData
            val customModel = mainModelData.getCustomModelFromCustomModelData(itemStack.itemMeta!!.customModelData)
            if (customModel != null) {
                spawnArmorStandMiniature(event.blockPlaced.location, itemStack, customModel)

                // decrease item count if player is in survival mode
                val player = event.player
                if (player.gameMode == GameMode.SURVIVAL) {
                    event.itemInHand.amount--
                }
            }
        }
    }

    @EventHandler
    fun handleEntityInteract(event: PlayerInteractAtEntityEvent) {
        val player = event.player
        val entity = event.rightClicked
        
        if (entity is ArmorStand && entity.hasMiniatureData()) {
            event.isCancelled = true
            
            val location = entity.location

            when {
                playerAutoRotationMap.containsKey(player) -> {
                    setMiniatureRotation(entity, playerAutoRotationMap[player]!!)
                    playerAutoRotationMap.remove(player)
                }
                
                playerRotationMap.containsKey(player) -> {
                    location.yaw = playerRotationMap[player]!!
                    playerRotationMap.remove(player)
                }
                
                else -> {
                    location.yaw += 45
                }
            }
            
            entity.teleport(location)
        }
    }

    @EventHandler
    fun handleEntityDamage(event: EntityDamageByEntityEvent) {
        val entity = event.entity
        if (entity is ArmorStand && entity.hasMiniatureData()) {
            event.isCancelled = true
            val location = entity.location
            val damager = event.damager
            if (damager !is Player || (damager.gameMode == GameMode.SURVIVAL || damager.gameMode == GameMode.ADVENTURE)) {
                location.world!!.dropItem(location, entity.equipment!!.helmet!!)
            }

            entity.remove()
        }
    }

    @EventHandler
    fun handleChunkLoad(event: ChunkLoadEvent) = handleChunkLoad(event.chunk)
    
    private fun handleChunkLoad(chunk: Chunk) {
        for (miniature in chunk.entities
                .filterIsInstance<ArmorStand>()
                .filter { it.hasMiniatureData() }) {

            if (!miniature.isValidMiniature()) {
                // remove armor stand if this miniature model does no longer exist
                miniature.remove()
            } else if (miniature.hasRotationData()) {
                // put into auto rotation map
                rotatingArmorStands[miniature] = miniature.getRotationData()
            }
        }
    }
    
    @EventHandler
    fun handleChunkUnload(event: ChunkUnloadEvent) {
        // remove armor stands from rotation map when the chunk gets unloaded
        event.chunk.entities
                .filterIsInstance<ArmorStand>()
                .forEach { rotatingArmorStands.remove(it) }
    }
    
    @EventHandler
    fun handleMiniatureClone(event: InventoryCreativeEvent) {
        val cloned = event.cursor
        if (cloned.type == Material.ARMOR_STAND) {
            
            val player = event.whoClicked as Player
            val entity = player.getTargetEntity(8.0)
            
            if (entity != null && entity is ArmorStand) {
                val customModel = entity.getCustomModel()
                if (customModel != null) {
                    event.cursor = customModel.createItemBuilder().build()
                }
            }
        }
    }
    
    private fun ArmorStand.hasMiniatureData(): Boolean = persistentDataContainer.has(modelNameKey, STRING)

    private fun ArmorStand.isValidMiniature(): Boolean = getCustomModel() != null

    private fun ArmorStand.isMiniature(customModel: CustomModel): Boolean = getCustomModel() == customModel

    private fun ArmorStand.getCustomModel(): CustomModel? {
        val dataContainer = persistentDataContainer
        val mainModelData = plugin.resourcePack.mainModelData

        return if (dataContainer.has(modelDataKey, INTEGER) && dataContainer.has(modelNameKey, STRING)) {
            mainModelData.getExactModel(dataContainer.get(modelDataKey, INTEGER)!!, dataContainer.get(modelNameKey, STRING)!!)
        } else null
    }
    
    private fun ArmorStand.hasRotationData(): Boolean = persistentDataContainer.has(rotationKey, FLOAT)

    private fun ArmorStand.getRotationData(): Float = persistentDataContainer.get(rotationKey, FLOAT) ?: 0.0f
    
    private fun ItemStack.isMiniatureLike(): Boolean {
        return type == Material.BEDROCK && itemMeta?.hasCustomModelData()!! && itemMeta?.customModelData!! > 1000000
    }

}