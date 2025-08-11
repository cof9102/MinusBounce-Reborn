/*
 * MinusBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/MinusMC/MinusBounce
 */
package net.minusmc.minusbounce.features.module.modules.render

import net.minusmc.minusbounce.event.EventTarget
import net.minusmc.minusbounce.event.Render3DEvent
import net.minusmc.minusbounce.features.module.Module
import net.minusmc.minusbounce.features.module.ModuleCategory
import net.minusmc.minusbounce.features.module.ModuleInfo
import net.minusmc.minusbounce.ui.gui.colortheme.ClientTheme
import net.minusmc.minusbounce.utils.EntityUtils
import net.minusmc.minusbounce.value.BoolValue
import net.minusmc.minusbounce.value.FloatValue
import net.minusmc.minusbounce.value.IntegerValue
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.entity.EntityLivingBase
import org.lwjgl.opengl.GL11
import kotlin.math.cos
import kotlin.math.sin

@ModuleInfo(name = "VietnameseHat", spacedName = "Vietnamese Hat", description = "tu noi dong xanh thom huong lua.", category = ModuleCategory.RENDER)
object ChinaHat : Module() {

    private val heightValue = FloatValue("Height", 0.3f, 0.1f, 0.7f)
    private val radiusValue = FloatValue("Radius", 0.7f, 0.3f, 1.5f)
    private val yPosValue = FloatValue("YPos", 0f, -1f, 1f)
    private val rotateSpeedValue = FloatValue("RotateSpeed", 2f, 0f, 5f)
    private val drawThePlayerValue = BoolValue("DrawThePlayer", true)
    private val onlyThirdPersonValue = BoolValue("OnlyThirdPerson", true).displayable { drawThePlayerValue.get() }
    private val drawTargetsValue = BoolValue("DrawTargets", true)
    private val colorDisplay = BoolValue("Color", false)
    private val colorRedValue = IntegerValue("Red", 255, 0, 255).displayable { colorDisplay.get() && !colorThemeClient.get() }
    private val colorGreenValue = IntegerValue("Green", 179, 0, 255).displayable { colorDisplay.get() && !colorThemeClient.get() }
    private val colorBlueValue = IntegerValue("Blue", 72, 0, 255).displayable { colorDisplay.get() && !colorThemeClient.get() }
    private val colorAlphaValue = IntegerValue("Alpha", 255, 0, 255).displayable { colorDisplay.get() && !colorThemeClient.get() }
    private val colorThemeClient = BoolValue("Client Color", true)

    @EventTarget
    fun onRender3d(event: Render3DEvent) {
        if(drawThePlayerValue.get() && !(onlyThirdPersonValue.get() && mc.gameSettings.thirdPersonView == 0)) {
            drawChinaHatFor(mc.thePlayer)
        }
        if(drawTargetsValue.get()) {
            mc.theWorld.loadedEntityList.forEach {
                if(EntityUtils.isSelected(it, true)) {
                    drawChinaHatFor(it as EntityLivingBase)
                }
            }
        }
    }

    private fun drawChinaHatFor(entity: EntityLivingBase) {
        GL11.glPushMatrix()
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
        GL11.glEnable(GL11.GL_BLEND)
        GL11.glDisable(GL11.GL_TEXTURE_2D)
        GL11.glDisable(GL11.GL_DEPTH_TEST)
        GL11.glDepthMask(false)
        GL11.glDisable(GL11.GL_CULL_FACE)
        if(!colorThemeClient.get()) {
            GL11.glColor4f(colorRedValue.get() / 255f, colorGreenValue.get() / 255f, colorBlueValue.get() / 255f, colorAlphaValue.get() / 255f)
        }
        GL11.glTranslated(entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * mc.timer.renderPartialTicks - mc.renderManager.renderPosX,
            entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * mc.timer.renderPartialTicks - mc.renderManager.renderPosY + entity.height + yPosValue.get(),
            entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * mc.timer.renderPartialTicks - mc.renderManager.renderPosZ)
        GL11.glRotatef((entity.ticksExisted + mc.timer.renderPartialTicks) * rotateSpeedValue.get(), 0f, 1f, 0f)

        GL11.glBegin(GL11.GL_TRIANGLE_FAN)
        GL11.glVertex3d(0.0, heightValue.get().toDouble(), 0.0)
        val radius = radiusValue.get().toDouble()
        for(i in 0..360 step 5) {
            if(colorThemeClient.get()) {
                ClientTheme.getColorWithAlpha(1, colorAlphaValue.get())
            }
            GL11.glVertex3d(cos(i.toDouble() * Math.PI / 180.0) * radius, 0.0, sin(i.toDouble() * Math.PI / 180.0) * radius)
        }
        GL11.glVertex3d(0.0, heightValue.get().toDouble(), 0.0)
        GL11.glEnd()

        GL11.glEnable(GL11.GL_CULL_FACE)
        GlStateManager.resetColor()
        GL11.glEnable(GL11.GL_TEXTURE_2D)
        GL11.glEnable(GL11.GL_DEPTH_TEST)
        GL11.glDepthMask(true)
        GL11.glDisable(GL11.GL_BLEND)
        GL11.glPopMatrix()
    }
}