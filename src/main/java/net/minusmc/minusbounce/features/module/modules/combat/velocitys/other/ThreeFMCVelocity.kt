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

    private val motionXField by lazy {
        S27PacketExplosion::class.java.getDeclaredField("field_149152_f").apply { isAccessible = true }
    }
    private val motionYField by lazy {
        S27PacketExplosion::class.java.getDeclaredField("field_149153_g").apply { isAccessible = true }
    }
    private val motionZField by lazy {
        S27PacketExplosion::class.java.getDeclaredField("field_149159_h").apply { isAccessible = true }
    }

    override fun onReceivedPacket(event: ReceivedPacketEvent) {
        val packet = event.packet
        val player = mc.thePlayer ?: return

        if (disableInAirValue.get() && !player.onGround) return
        if ((0..99).random() >= chanceValue.get()) return

        when (packet) {
            is S12PacketEntityVelocity -> handleS12Packet(event, packet)
            is S27PacketExplosion -> handleS27Packet(event, packet)
        }
    }

    private fun handleS12Packet(event: ReceivedPacketEvent, packet: S12PacketEntityVelocity) {
        if (packet.entityID != mc.thePlayer.entityId) return

        val horizontal = horizontalValue.get()
        val vertical = verticalValue.get()

        when {
            horizontal == 0f && vertical == 0f -> event.isCancelled = true
            horizontal == 0f -> {
                packet.motionY = (packet.motionY * vertical).toInt()
            }
            else -> {
                packet.motionX = (packet.motionX * horizontal).toInt()
                packet.motionY = (packet.motionY * vertical).toInt()
                packet.motionZ = (packet.motionZ * horizontal).toInt()
            }
        }
    }

    private fun handleS27Packet(event: ReceivedPacketEvent, packet: S27PacketExplosion) {
        val horizontal = horizontalValue.get()
        val vertical = verticalValue.get()

        if (horizontal == 0f && vertical == 0f) {
            event.isCancelled = true
            return
        }

        try {
            motionXField.setFloat(packet, motionXField.getFloat(packet) * horizontal)
            motionYField.setFloat(packet, motionYField.getFloat(packet) * vertical)
            motionZField.setFloat(packet, motionZField.getFloat(packet) * horizontal)
        } catch (e: Exception) {
            event.isCancelled = true
            e.printStackTrace()
        }
    }
}