package net.minusmc.minusbounce.plugin

import net.minecraft.client.gui.GuiScreen

object PluginGuiManager {
	val guiButton = hashMapOf<String, Class<out GuiScreen>>()

    fun addMenuButton(name: String, gui: Class<out GuiScreen>) {
        guiButton[name] = gui
    }

}