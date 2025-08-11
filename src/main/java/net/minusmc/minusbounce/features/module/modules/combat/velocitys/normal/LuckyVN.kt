package net.minusmc.minusbounce.features.module.modules.combat.velocitys.normal

import net.minecraft.client.settings.GameSettings
import net.minusmc.minusbounce.features.module.modules.combat.velocitys.VelocityMode

class LuckyVN: VelocityMode("LuckyVNJump") {
    private var velocityInput = false
    private var wasForwardPressed = false

    override fun onUpdate() {
        val player = mc.thePlayer ?: return
        val hurtTime = player.hurtTime

        when {
            hurtTime >= 8 -> {
                mc.gameSettings.keyBindJump.pressed = true
            }

            hurtTime == 7 -> {
                mc.gameSettings.keyBindJump.pressed = true

                if (!mc.gameSettings.keyBindForward.pressed) {
                    wasForwardPressed = GameSettings.isKeyDown(mc.gameSettings.keyBindForward)
                    mc.gameSettings.keyBindForward.pressed = true
                    velocityInput = true
                }
            }

            hurtTime in 2..6 -> {
                mc.gameSettings.keyBindJump.pressed = false

                if (velocityInput && hurtTime <= 4) {
                    mc.gameSettings.keyBindForward.pressed = false
                    velocityInput = false
                }
            }

            hurtTime == 1 -> {
                mc.gameSettings.keyBindJump.pressed = false

                if (velocityInput) {
                    mc.gameSettings.keyBindForward.pressed = wasForwardPressed
                    velocityInput = false
                } else {
                    mc.gameSettings.keyBindForward.pressed = GameSettings.isKeyDown(mc.gameSettings.keyBindForward)
                }
            }

            hurtTime == 0 -> {
                if (velocityInput) {
                    mc.gameSettings.keyBindForward.pressed = wasForwardPressed
                    velocityInput = false
                }
            }
        }
    }

    override fun onDisable() {
        super.onDisable()
        if (velocityInput) {
            mc.gameSettings.keyBindForward.pressed = wasForwardPressed
            velocityInput = false
        }
        mc.gameSettings.keyBindJump.pressed = GameSettings.isKeyDown(mc.gameSettings.keyBindJump)
    }
}