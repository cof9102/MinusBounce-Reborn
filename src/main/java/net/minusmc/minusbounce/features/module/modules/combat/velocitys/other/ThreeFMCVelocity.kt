package net.minusmc.minusbounce.features.module.modules.combat.velocitys.other

import net.minecraft.network.play.server.S12PacketEntityVelocity
import net.minecraft.network.play.server.S27PacketExplosion
import net.minusmc.minusbounce.event.ReceivedPacketEvent
import net.minusmc.minusbounce.features.module.modules.combat.velocitys.VelocityMode
import net.minusmc.minusbounce.utils.player.MovementUtils
import net.minusmc.minusbounce.value.BoolValue
import net.minusmc.minusbounce.value.FloatValue
import net.minusmc.minusbounce.value.IntegerValue

class ThreeFMCVelocity : VelocityMode("3FMC") {
    private val horizontalValue = FloatValue("Horizontal", 0F, 0F, 1F)
    private val verticalValue = FloatValue("Vertical", 0F, 0F, 1F)
    private val chanceValue = IntegerValue("Chance", 50, 0, 100)
    private val disableInAirValue = BoolValue("DisableInAir", true)

    override fun onEnable() {
    }

    override fun onReceivedPacket(event: ReceivedPacketEvent) {
        val packet = event.packet

        if (packet is S12PacketEntityVelocity && packet.entityID == mc.thePlayer.entityId) {
            if (disableInAirValue.get() && !MovementUtils.isOnGround(0.5)) {
                return
            }

            if ((0..99).random() >= chanceValue.get()) {
                return
            }

            val horizontal = horizontalValue.get()
            val vertical = verticalValue.get()

            if (horizontal == 0f && vertical == 0f) {
                event.isCancelled = true
                return
            }

            if (horizontal == 0f) {
                mc.thePlayer.motionY = packet.motionY / 8000.0 * vertical
                event.isCancelled = true
                return
            }

            packet.motionX = (packet.motionX * horizontal).toInt()
            packet.motionY = (packet.motionY * vertical).toInt()
            packet.motionZ = (packet.motionZ * horizontal).toInt()
        }

        if (packet is S27PacketExplosion) {
            if (disableInAirValue.get() && !MovementUtils.isOnGround(0.5)) {
                return
            }

            if ((0..99).random() >= chanceValue.get()) {
                return
            }

            val horizontal = horizontalValue.get()
            val vertical = verticalValue.get()


            if (horizontal == 0f && vertical == 0f) {
                event.isCancelled = true
                return
            }


            try {
                val motionXField = S27PacketExplosion::class.java.getDeclaredField("field_149152_f")
                val motionYField = S27PacketExplosion::class.java.getDeclaredField("field_149153_g")
                val motionZField = S27PacketExplosion::class.java.getDeclaredField("field_149159_h")

                motionXField.isAccessible = true
                motionYField.isAccessible = true
                motionZField.isAccessible = true

                val currentMotionX = motionXField.getFloat(packet)
                val currentMotionY = motionYField.getFloat(packet)
                val currentMotionZ = motionZField.getFloat(packet)

                motionXField.setFloat(packet, currentMotionX * horizontal)
                motionYField.setFloat(packet, currentMotionY * vertical)
                motionZField.setFloat(packet, currentMotionZ * horizontal)
            } catch (e: Exception) {
                event.isCancelled = true
            }
        }
    }
}