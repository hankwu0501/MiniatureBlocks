package de.studiocode.miniatureblocks.command.impl

import com.mojang.brigadier.context.CommandContext
import de.studiocode.miniatureblocks.command.PlayerCommand
import de.studiocode.miniatureblocks.menu.MiniaturesMenu

class MiniaturesCommand(name: String, permission: String) : PlayerCommand(name, permission) {
    
    init {
        command = command.executes { handleMiniaturesCommand(it); 0 }
    }
    
    private fun handleMiniaturesCommand(context: CommandContext<Any>) {
        try {
            val player = getPlayer(context.source)
            MiniaturesMenu(player).openWindow()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
}

