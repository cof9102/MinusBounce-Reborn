package net.minusmc.minusbounce.features.module.modules.combat.velocitys.normal

import net.minecraft.network.play.server.S12PacketEntityVelocity
import net.minusmc.minusbounce.event.*
import net.minusmc.minusbounce.features.module.modules.combat.velocitys.VelocityMode
import net.minusmc.minusbounce.utils.timing.MSTimer
import net.minusmc.minusbounce.value.BoolValue
import net.minusmc.minusbounce.value.IntegerValue
import java.util.*

class JumpResetVelocity: VelocityMode("JumpReset") {
    private val jumpChance = IntegerValue("JumpChance", 100, 0, 100)
    private val resetCount = IntegerValue("ResetCount", 2, 1, 5)
    private val reduceMotion = BoolValue("ReduceMotion", true)
    
    private var hasReceivedVelocity = false
    private var jumpCount = 0
    private val velocityTimer = MSTimer()

    override fun onReceivedPacket(event: ReceivedPacketEvent) {
        val packet = event.packet
        if (packet is S12PacketEntityVelocity && packet.entityID == mc.thePlayer?.entityId) {
            hasReceivedVelocity = true
            velocityTimer.reset()
        }
    }

    override fun onStrafe(event: StrafeEvent) {
        val player = mc.thePlayer ?: return
        
        if (hasReceivedVelocity && player.onGround) {

            if (player.hurtTime == 0) {
                jumpCount = 0
            }
        
            if (player.hurtTime == 9) {
                jumpCount++
                
                if (jumpCount % resetCount.get() == 0 && 
                    Random().nextInt(100) < jumpChance.get() &&
                    player.isSprinting && 
                    mc.currentScreen == null) {
                    
                    player.jump()
                    
                    if (reduceMotion.get()) {
                        player.motionX *= 0.6
                        player.motionZ *= 0.6
                    }
                }
            }
            
            if (player.hurtTime <= 8) {
                hasReceivedVelocity = false
            }
        }
        
        if (hasReceivedVelocity && velocityTimer.hasTimePassed(500)) {
            hasReceivedVelocity = false
            jumpCount = 0
        }
    }

    override fun onDisable() {
        hasReceivedVelocity = false
        jumpCount = 0
    }
}