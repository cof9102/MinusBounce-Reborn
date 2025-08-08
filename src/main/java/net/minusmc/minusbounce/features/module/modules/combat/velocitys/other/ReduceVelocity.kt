package net.minusmc.minusbounce.features.module.modules.combat.velocitys.other

import net.minusmc.minusbounce.event.AttackEvent
import net.minusmc.minusbounce.value.FloatValue
import net.minusmc.minusbounce.features.module.modules.combat.velocitys.VelocityMode

class ReduceVelocity : VelocityMode("Reduce") {
    val reduceAmount = FloatValue("ReduceAmount", 0.8f, 0.3f, 1f)

    override fun onAttack(event: AttackEvent) {
        if (mc.thePlayer.hurtTime < 3)
            return
        mc.thePlayer.motionX *= reduceAmount.get().toDouble()
        mc.thePlayer.motionZ *= reduceAmount.get().toDouble()
    }
}