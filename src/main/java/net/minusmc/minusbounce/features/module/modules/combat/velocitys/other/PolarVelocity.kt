package net.minusmc.minusbounce.features.module.modules.combat.velocitys.other

import net.minusmc.minusbounce.features.module.modules.combat.velocitys.VelocityMode
import net.minusmc.minusbounce.event.UpdateEvent
import net.minusmc.minusbounce.utils.misc.RandomUtils

class PolarVelocity : VelocityMode("Polar") {
    private var polarHurtTime = RandomUtils.nextInt(8, 10)
    
    override fun onUpdate() {
        val player = mc.thePlayer ?: return
        if (player.hurtTime == polarHurtTime) {
            player.jump()
            polarHurtTime = RandomUtils.nextInt(8, 10)
        }
    }
}