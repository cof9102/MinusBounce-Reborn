package net.minusmc.minusbounce.features.module.modules.combat.velocitys.other

import net.minusmc.minusbounce.features.module.modules.combat.velocitys.VelocityMode
import net.minusmc.minusbounce.event.PacketEvent
import net.minusmc.minusbounce.event.UpdateEvent
import net.minusmc.minusbounce.utils.Rotation
import net.minusmc.minusbounce.utils.RotationUtils
import net.minecraft.network.play.server.S12PacketEntityVelocity
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

class PredictionVelocity : VelocityMode("Prediction") {
    private var predictionPacket: S12PacketEntityVelocity? = null

    override fun onPacket(event: PacketEvent) {
        val packet = event.packet
        val player = mc.thePlayer ?: return
        
        if (packet is S12PacketEntityVelocity && packet.entityID == player.entityId) {
            predictionPacket = packet
        }
    }

    override fun onUpdate() {
        val player = mc.thePlayer ?: return
        val packet = predictionPacket ?: return

        val motionX = packet.motionX / 8000.0
        val motionZ = packet.motionZ / 8000.0

        if (motionX != 0.0 || motionZ != 0.0) {
            val oppositeYaw = Math.toDegrees(atan2(-motionX, -motionZ)).toFloat()
            RotationUtils.setRotation(Rotation(oppositeYaw, player.rotationPitch))

            if (player.onGround) {
                player.jump()
            }
        }
        predictionPacket = null
    }
}