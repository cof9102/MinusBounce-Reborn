/*
 * MinusBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/MinusMC/MinusBounce
 */
package net.minusmc.minusbounce.features.module.modules.combat

import net.minecraft.entity.EntityLivingBase
import net.minusmc.minusbounce.event.EventTarget
import net.minusmc.minusbounce.event.MoveEvent
import net.minusmc.minusbounce.event.UpdateEvent
import net.minusmc.minusbounce.features.module.Module
import net.minusmc.minusbounce.features.module.ModuleCategory
import net.minusmc.minusbounce.features.module.ModuleInfo
import net.minusmc.minusbounce.utils.EntityUtils
import net.minusmc.minusbounce.utils.extensions.getDistanceToEntityBox
import net.minusmc.minusbounce.utils.timer.MSTimer
import net.minusmc.minusbounce.value.BoolValue
import net.minusmc.minusbounce.value.FloatValue
import net.minusmc.minusbounce.value.IntegerValue
import net.minusmc.minusbounce.value.ListValue
import kotlin.math.abs

/**
 * Skid RinBounce Recode by DeltedUser
 * Automatically performs hit selection to initiate combos
 */
@ModuleInfo(name = "AutoHitSelect", spacedName = "Auto Hit Select", description = "Automatically performs hit selection to initiate combos.", category = ModuleCategory.COMBAT)
class AutoHitSelect : Module() {

    private val modeValue = ListValue("Mode", arrayOf("Pause", "Active"), "Pause")
    private val preferenceValue = ListValue("Preference", arrayOf("Move Speed", "KB Reduction", "Critical Hits"), "Move Speed")
    private val delayValue = IntegerValue("Delay", 420, 300, 500, "ms")
    private val chanceValue = IntegerValue("Chance", 80, 0, 100, "%")
    private val rangeValue = FloatValue("Range", 8F, 1F, 20F, "m")
    private val debugValue = BoolValue("Debug", false)

    private var attackTime = 0L
    private var currentShouldAttack = false
    private val attackTimer = MSTimer()

    @EventTarget
    fun onUpdate(event: UpdateEvent) {
        val thePlayer = mc.thePlayer ?: return
        val target = getNearestEntityInRange() ?: run {
            resetState()
            return
        }

        currentShouldAttack = false

        if (Math.random() * 100 <= chanceValue.get()) {
            when (preferenceValue.get()) {
                "KB Reduction" -> {
                    currentShouldAttack = !thePlayer.onGround && thePlayer.motionY < 0
                }
                "Critical Hits" -> {
                    currentShouldAttack = thePlayer.hurtTime > 0 && !thePlayer.onGround && isPlayerMoving()
                }
                "Move Speed" -> {
                    currentShouldAttack = attackTimer.hasTimePassed(delayValue.get().toLong())
                }
            }
        } else {
            currentShouldAttack = true
        }

        if (currentShouldAttack && attackTimer.hasTimePassed(delayValue.get().toLong())) {
            attackTime = System.currentTimeMillis()
            attackTimer.reset()

            if (debugValue.get()) {
                mc.thePlayer.addChatMessage(net.minecraft.util.ChatComponentText("§7[§9AutoHitSelect§7] §rHit selection performed"))
            }
        }
    }

    @EventTarget
    fun onMove(event: MoveEvent) {
        if (modeValue.get().equals("Pause", true) && !canAttack()) {
            // Reduce movement speed when pausing
            event.x *= 0.2
            event.z *= 0.2
        }
    }

    private fun resetState() {
        currentShouldAttack = false
    }

    private fun getNearestEntityInRange(): EntityLivingBase? {
        val thePlayer = mc.thePlayer ?: return null
        val entities = mc.theWorld?.loadedEntityList ?: return null

        return entities.asSequence()
            .filterIsInstance<EntityLivingBase>()
            .filter {
                EntityUtils.isSelected(it, true) &&
                        thePlayer.getDistanceToEntityBox(it) <= rangeValue.get() &&
                        it != thePlayer
            }
            .minByOrNull { thePlayer.getDistanceToEntityBox(it) }
    }

    private fun isPlayerMoving(): Boolean {
        val thePlayer = mc.thePlayer ?: return false
        return abs(thePlayer.motionX) > 0.01 || abs(thePlayer.motionZ) > 0.01
    }

    /**
     * Check if we can attack based on current conditions
     */
    fun canAttack(): Boolean = canSwing()

    /**
     * Check if we can swing weapon
     */
    fun canSwing(): Boolean {
        if (!state || modeValue.get().equals("Active", true)) return true
        return currentShouldAttack
    }

    override val tag: String
        get() = modeValue.get()
}