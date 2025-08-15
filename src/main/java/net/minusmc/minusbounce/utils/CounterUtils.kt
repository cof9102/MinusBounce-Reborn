package net.minusmc.minusbounce.utils

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.ScaledResolution
import net.minecraft.item.ItemBlock
import net.minecraft.item.ItemStack
import net.minusmc.minusbounce.MinusBounce
import net.minusmc.minusbounce.features.module.modules.render.ClientTheme
import net.minusmc.minusbounce.utils.render.RenderUtils
import net.minusmc.minusbounce.utils.timer.MSTimer
import net.minecraft.client.renderer.RenderHelper
import net.minecraft.client.renderer.OpenGlHelper
import net.minecraft.client.renderer.GlStateManager
import java.awt.Color

class CounterUtils {
    private val mc: Minecraft = Minecraft.getMinecraft()
    private val timer = MSTimer()
    private var spoofSlot = -1
    private var oldSlot = -1
    private var spoofing = false

    fun getBlockCount(): Int {
        var count = 0
        for (i in 0..8) {
            val stack: ItemStack? = mc.thePlayer.inventory.mainInventory[i]
            if (stack != null && stack.item is ItemBlock) {
                count += stack.stackSize
            }
        }
        return count
    }

    fun counter() {
        val res = ScaledResolution(mc)
        val blockAmount = getBlockCount()
        val text = "Amount: $blockAmount"
        val textWidth = mc.fontRendererObj.getStringWidth(text).toFloat()
        val iconSize = 16F
        val padding = 4F
        val totalWidth = iconSize + padding + textWidth + padding * 2
        val totalHeight = iconSize + padding
        val posX = res.scaledWidth / 2F
        val posY = res.scaledHeight - 115F

        RenderUtils.drawRoundedRect(
            posX - totalWidth / 2F,
            posY,
            posX + totalWidth / 2F,
            posY + totalHeight,
            4F,
            Color(0, 0, 0, 120).rgb
        )
        mc.thePlayer.heldItem?.let { heldItem ->
            if (heldItem.item is ItemBlock) {
                RenderHelper.enableGUIStandardItemLighting()
                OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f)
                mc.renderItem.renderItemAndEffectIntoGUI(
                    heldItem,
                    (posX - totalWidth / 2F + padding).toInt(),
                    (posY + padding / 2F).toInt()
                )
                RenderHelper.disableStandardItemLighting()
                GlStateManager.disableLighting()
                GlStateManager.disableDepth()
            }
        }
        mc.fontRendererObj.drawStringWithShadow(
            text,
            posX - totalWidth / 2F + iconSize + padding * 2,
            posY + (totalHeight - mc.fontRendererObj.FONT_HEIGHT) / 2F,
            Color.WHITE.rgb
        )
    }
}