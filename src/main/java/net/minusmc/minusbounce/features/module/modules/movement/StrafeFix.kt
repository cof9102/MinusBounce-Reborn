package net.minusmc.minusbounce.features.module.modules.movement

import net.minusmc.minusbounce.MinusBounce
import net.minusmc.minusbounce.event.StrafeEvent
import net.minusmc.minusbounce.event.UpdateEvent
import net.minusmc.minusbounce.event.EventTarget
import net.minusmc.minusbounce.features.module.Module
import net.minusmc.minusbounce.features.module.ModuleCategory
import net.minusmc.minusbounce.features.module.ModuleInfo
import net.minusmc.minusbounce.value.BoolValue
import net.minusmc.minusbounce.utils.RotationUtils
import net.minecraft.util.MathHelper
import kotlin.math.abs

@ModuleInfo(name = "StrafeFix", spacedName = "Strafe Fix", description = "Fixes strafing movement", category = ModuleCategory.MOVEMENT)
class StrafeFix : Module() {

    private val silentFixValue = BoolValue("Silent", true)

    var silentFix = false
    var doFix = false
    var isOverwrited = false

    @EventTarget
    fun onUpdate(event: UpdateEvent) {
        if (!isOverwrited) {
            silentFix = silentFixValue.get()
            doFix = true
        }
    }

    override fun onDisable() {
        doFix = false
    }

    fun applyForceStrafe(isSilent: Boolean, runStrafeFix: Boolean) {
        silentFix = isSilent
        doFix = runStrafeFix
        isOverwrited = true
    }

    fun updateOverwrite() {
        isOverwrited = false
        doFix = state
        silentFix = silentFixValue.get()
    }

    fun runStrafeFixLoop(isSilent: Boolean, event: StrafeEvent) {
        if (event.isCancelled) {
            return
        }
        
        val rotation = RotationUtils.targetRotation ?: return
        val yaw = rotation.yaw
        
        var strafe = event.strafe
        var forward = event.forward
        var friction = event.friction
        var factor = strafe * strafe + forward * forward

        val angleDiff = ((MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw - yaw - 22.5f - 135.0f) + 180.0) / 45.0
        val calcYaw = if (isSilent) yaw + 45.0f * angleDiff.toInt() else yaw

        val calcMoveDir = maxOf(abs(strafe), abs(forward))
        val calcMultiplier = MathHelper.sqrt_float((calcMoveDir * calcMoveDir) / minOf(1.0f, calcMoveDir * 2.0f))

        if (isSilent) {
            when (angleDiff.toInt()) {
                1, 3, 5, 7 -> {
                    if ((abs(forward) > 0.005f || abs(strafe) > 0.005f) && 
                        !(abs(forward) > 0.005f && abs(strafe) > 0.005f)) {
                        friction /= calcMultiplier
                    } else if (abs(forward) > 0.005f && abs(strafe) > 0.005f) {
                        friction *= calcMultiplier
                    }
                }
            }
        }

        if (factor >= 1.0E-4F) {
            factor = MathHelper.sqrt_float(factor)
            if (factor < 1.0F) factor = 1.0F

            factor = friction / factor
            strafe *= factor
            forward *= factor

            val radYaw = (calcYaw * Math.PI / 180F).toFloat()
            val sin = MathHelper.sin(radYaw)
            val cos = MathHelper.cos(radYaw)

            mc.thePlayer.motionX += (strafe * cos - forward * sin).toDouble()
            mc.thePlayer.motionZ += (forward * cos + strafe * sin).toDouble()
        }
        event.cancelEvent()
    }
}