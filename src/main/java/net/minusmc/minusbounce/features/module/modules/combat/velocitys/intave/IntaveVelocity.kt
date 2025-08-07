package net.minusmc.minusbounce.features.module.modules.combat.velocitys

import net.minusmc.minusbounce.event.PacketEvent
import net.minusmc.minusbounce.event.StrafeEvent
import net.minusmc.minusbounce.features.module.modules.combat.Velocity
import net.minusmc.minusbounce.utils.extensions.tryJump
import net.minusmc.minusbounce.value.IntegerValue
import net.minecraft.network.play.server.S12PacketEntityVelocity
import net.minecraft.network.play.server.S27PacketExplosion

class IntaveVelocity : VelocityMode("Intave") {
    private val intaveJumpResetCount = IntegerValue("JumpResetCount", 2, 1..10)
    private var intaveJumpCount = 0
    private var hasReceivedVelocity = false
    private var intaveLastAttackTime = 0L
    private val intaveLastAttackTimeToReduce = 4000L
    private val intaveReduceFactor = 0.6
    private val intaveHurtTime = 1..3

    override fun onPacket(event: PacketEvent) {
        val packet = event.packet
        val thePlayer = mc.thePlayer ?: return

        if (packet is S12PacketEntityVelocity && packet.entityID == thePlayer.entityId) {
            val velocityX = packet.motionX / 8000.0
            val velocityY = packet.motionY / 8000.0
            val velocityZ = packet.motionZ / 8000.0
            
            if (thePlayer.hurtTime in intaveHurtTime && 
                System.currentTimeMillis() - intaveLastAttackTime <= intaveLastAttackTimeToReduce) {
                packet.motionX = (packet.motionX * intaveReduceFactor).toInt()
                packet.motionZ = (packet.motionZ * intaveReduceFactor).toInt()
                intaveLastAttackTime = System.currentTimeMillis()
            }
            hasReceivedVelocity = true
        } else if (packet is S27PacketExplosion) {
            event.cancelEvent()
        }
    }

    override fun onStrafe(event: StrafeEvent) {
        val player = mc.thePlayer ?: return
        
        if (hasReceivedVelocity) {
            if (player.hurtTime == 9) {
                intaveJumpCount++
                if (intaveJumpCount % intaveJumpResetCount.get() == 0 && 
                    player.onGround && 
                    player.isSprinting && 
                    mc.currentScreen == null) {
                    player.tryJump()
                    intaveJumpCount = 0
                }
            } else {
                hasReceivedVelocity = false
                intaveJumpCount = 0
            }
        }
    }
}