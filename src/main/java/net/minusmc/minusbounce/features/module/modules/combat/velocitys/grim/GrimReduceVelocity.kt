package net.minusmc.minusbounce.features.module.modules.combat.velocitys.grim

import net.minusmc.minusbounce.features.module.modules.combat.velocitys.VelocityMode
import net.minusmc.minusbounce.event.UpdateEvent
import net.minusmc.minusbounce.event.PacketEvent
import net.minusmc.minusbounce.features.module.modules.combat.Velocity
import net.minusmc.minusbounce.value.BoolValue
import net.minusmc.minusbounce.value.FloatValue
import net.minusmc.minusbounce.value.IntegerValue
import net.minecraft.network.play.server.S12PacketEntityVelocity
import net.minecraft.network.play.server.S27PacketExplosion

class GrimReduceVelocity : VelocityMode("GrimReduce") {
    private val reduceFactor = FloatValue("ReduceFactor", 0.6f, 0f..1f)
    private val reduceMinHurtTime = IntegerValue("MinHurtTime", 5, 0..10)
    private val reduceMaxHurtTime = IntegerValue("MaxHurtTime", 10, 0..20)
    private val reduceOnlyGround = BoolValue("OnlyGround", false)
    private var hasReceivedVelocity = false

    override fun onPacket(event: PacketEvent) {
        val packet = event.packet
        val thePlayer = mc.thePlayer ?: return

        if (packet is S12PacketEntityVelocity && thePlayer.entityId == packet.entityID ||
            packet is S27PacketExplosion) {
            hasReceivedVelocity = true
        }
    }

    override fun onUpdate() {
        val thePlayer = mc.thePlayer ?: return

        if (hasReceivedVelocity && thePlayer.hurtTime in reduceMinHurtTime.get()..reduceMaxHurtTime.get()) {
            thePlayer.motionX *= reduceFactor.get().toDouble()
            thePlayer.motionY *= reduceFactor.get().toDouble()
            thePlayer.motionZ *= reduceFactor.get().toDouble()
            
            if (reduceOnlyGround.get() && !thePlayer.onGround) {
                hasReceivedVelocity = false
            }
        }
    }
}