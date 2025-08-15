package net.minusmc.minusbounce.ui.client.hud.element.elements.targets.impl

import net.minusmc.minusbounce.ui.client.hud.element.Border
import net.minusmc.minusbounce.ui.client.hud.element.elements.Target
import net.minusmc.minusbounce.ui.client.hud.element.elements.targets.TargetStyle
import net.minusmc.minusbounce.ui.font.Fonts
import net.minusmc.minusbounce.utils.render.RenderUtils
import net.minecraft.client.network.NetworkPlayerInfo
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.player.EntityPlayer
import java.awt.Color

class Myau(inst: Target) : TargetStyle("Myau", inst, true) {

    override fun drawTarget(entity: EntityPlayer) {
        try {
            val font = Fonts.minecraftFont
            val hp = decimalFormat.format(entity.health)
            val hplength = font.getStringWidth(hp)
            val length = font.getStringWidth(entity.displayName.formattedText)
            val totalWidth = length + hplength + 45f
            val height = 35f

            GlStateManager.pushMatrix()

            RenderUtils.drawRect(0f, 0f, totalWidth, height, targetInstance.bgColor.rgb)

            val playerInfo: NetworkPlayerInfo = mc.netHandler.getPlayerInfo(entity.uniqueID)
                ?: return
            val locationSkin = playerInfo.locationSkin

            drawHead(locationSkin, 5, 5, 20, 20, 255f)
            RenderUtils.drawBorder(0.5f, 0.5f, 51f, 51f, 2f, Color.BLACK.rgb)
            font.drawStringWithShadow(entity.displayName.formattedText, 30f, 6f, Color.GRAY.rgb)
            font.drawStringWithShadow(hp, 30f, 18f, Color(100, 255, 100).rgb)
            val maxBarWidth = totalWidth - 10f
            val easingHealth = entity.health
            val currentBar = (entity.health / entity.maxHealth).coerceIn(0f, 1f) * maxBarWidth
            RenderUtils.drawRect(5f, 30f, totalWidth - 5f, 33f, Color(50, 50, 50, 160).rgb)
            RenderUtils.drawRect(5f, 30f, 5f + currentBar, 33f, targetInstance.barColor.rgb)

            GlStateManager.popMatrix()
        } catch (e: Exception) {
            drawFallback(entity, entity.health, entity.maxHealth, entity.hurtTime.toFloat())
        }
    }

    private fun drawFallback(entity: EntityLivingBase, easingHealth: Float, maxHealth: Float, easingHurtTime: Float) {
        val font = mc.fontRendererObj
        val healthString = decimalFormat.format(entity.health)
        val name = entity.displayName.formattedText
        val width = 110f

        RenderUtils.drawRect(0f, 0f, width, 30f, targetInstance.bgColor.rgb)
        font.drawStringWithShadow("Target: $name", 4f, 5f, Color.GRAY.rgb)
        font.drawStringWithShadow("Health: $healthString", 4f, 17f, Color(100, 255, 100).rgb)

        val healthPercent = (entity.health / entity.maxHealth).coerceIn(0f, 1f)
        RenderUtils.drawRect(0f, 28f, width * healthPercent, 30f, targetInstance.barColor.rgb)
    }

    override fun getBorder(entity: EntityPlayer?): Border? {
        entity ?: return Border(0F, 0F, 118F, 36F)
        return Border(0F, 0F, 38F, 36F)
    }
}
