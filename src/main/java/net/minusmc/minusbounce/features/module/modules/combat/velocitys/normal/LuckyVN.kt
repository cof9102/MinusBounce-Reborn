package net.minusmc.minusbounce.features.module.modules.combat.velocitys.normal

import net.minusmc.minusbounce.features.module.modules.combat.velocitys.VelocityMode

class LuckyVNJump : VelocityMode("LuckyVNJump") {
    private var shouldJump = false
    private var jumpCooldown = 0

    override fun onUpdate() {
        val player = mc.thePlayer ?: return
        val hurtTime = player.hurtTime

        when {
            hurtTime >= 8 -> {
                if (jumpCooldown <= 0) {
                    shouldJump = true
                    jumpCooldown = 2
                }
            }
            
            hurtTime <= 1 -> {
                shouldJump = false
                jumpCooldown = 0
            }
        }

        if (shouldJump && player.onGround && jumpCooldown <= 0) {
            player.jump()
            shouldJump = false
        }

        if (jumpCooldown > 0) {
            jumpCooldown--
        }
    }

    override fun onDisable() {
        shouldJump = false
        jumpCooldown = 0
    }
}