package net.minusmc.minusbounce.features.module.modules.combat.velocitys.other

import net.minusmc.minusbounce.features.module.modules.combat.velocitys.VelocityMode
import net.minusmc.minusbounce.event.UpdateEvent
import net.minusmc.minusbounce.event.PacketEvent
import net.minusmc.minusbounce.features.module.modules.combat.Velocity
import net.minusmc.minusbounce.utils.extensions.tryJump
import net.minusmc.minusbounce.value.IntegerValue
import net.minecraft.network.play.server.S12PacketEntityVelocity

class LegitSmartVelocity : VelocityMode("LegitSmart") {
    private val legitSmartJumpLimit = IntegerValue("JumpLimit", 2, 1, 5)
    private var legitSmartJumpCount = 0
    private var hasReceivedVelocity = false

    override fun onPacket(event: PacketEvent) {
        val packet = event.packet
        val thePlayer = mc.thePlayer ?: return

        if (packet is S12PacketEntityVelocity && thePlayer.entityId == packet.entityID) {
            hasReceivedVelocity = true
        }
    }

    override fun onUpdate() {
        val thePlayer = mc.thePlayer ?: return

        if (hasReceivedVelocity) {
            if (thePlayer.onGround && 
                thePlayer.hurtTime == 9 && 
                thePlayer.isSprinting && 
                mc.currentScreen == null) {
    
                if (legitSmartJumpCount > legitSmartJumpLimit.get()) {
                    legitSmartJumpCount = 0
                } else {
                    legitSmartJumpCount++
                    if (thePlayer.ticksExisted % 5 != 0) {
                        thePlayer.jump() // Sửa từ tryJump() thành jump()
                    }
                }
            } else if (thePlayer.hurtTime == 8) {
                hasReceivedVelocity = false
                legitSmartJumpCount = 0
            }
        }
    }
}