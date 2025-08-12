package net.minusmc.minusbounce.utils.player

import net.minusmc.minusbounce.event.*
import net.minusmc.minusbounce.utils.MinecraftInstance
import net.minusmc.minusbounce.utils.PacketUtils
import net.minusmc.minusbounce.utils.Rotation
import net.minecraft.client.entity.EntityOtherPlayerMP
import net.minecraft.network.Packet
import net.minecraft.network.handshake.client.C00Handshake
import net.minecraft.network.play.client.C03PacketPlayer
import net.minecraft.network.play.server.S02PacketChat
import net.minecraft.network.play.server.S40PacketDisconnect
import net.minecraft.network.status.client.C00PacketServerQuery
import net.minecraft.network.status.client.C01PacketPing
import net.minecraft.network.play.INetHandlerPlayServer
import java.util.LinkedList
import java.math.BigInteger
import net.minecraft.util.Vec3

/**
 * @author CCBluex
 */

object BlinkUtils: MinecraftInstance() {
    val sentPackets = mutableListOf<Packet<*>>()
    val receivedPackets = mutableListOf<Packet<*>>()
    val positions = mutableListOf<Vec3>()

    private var fakePlayer: EntityOtherPlayerMP? = null

    private val playerBuffer = LinkedList<Packet<INetHandlerPlayServer>>()

    var movingPacketStat = false
    var transactionStat = false
    var keepAliveStat = false
    var actionStat = false
    var abilitiesStat = false
    var invStat = false
    var interactStat = false
    var otherPacket = false

    private var packetToggleStat = BooleanArray(26) { false }

    fun releasePacket(packetType: String? = null, onlySelected: Boolean = false, amount: Int = -1, minBuff: Int = 0) {
        var count = 0
        when (packetType) {
            null -> {
                count = -1
                for (packets in playerBuffer) {
                    val packetID = BigInteger(packets.javaClass.simpleName.substring(1..2), 16).toInt()
                    if (packetToggleStat[packetID] || !onlySelected) {
                        PacketUtils.sendPacketNoEvent(packets)
                    }
                }
            }
            else -> {
                val tempBuffer = LinkedList<Packet<INetHandlerPlayServer>>()
                for (packets in playerBuffer) {
                    val className = packets.javaClass.simpleName
                    if (className.equals(packetType, ignoreCase = true)) {
                        tempBuffer.add(packets)
                    }
                }
                while (tempBuffer.size > minBuff && (count < amount || amount <= 0)) {
                    PacketUtils.sendPacketNoEvent(tempBuffer.pop())
                    count++
                }
            }
        }
        clearPacket(packetType = packetType, onlySelected = onlySelected, amount = count)
    }

    fun setBlinkState(
        off: Boolean = false,
        release: Boolean = false,
        all: Boolean = false,
        packetMoving: Boolean = movingPacketStat,
        packetTransaction: Boolean = transactionStat,
        packetKeepAlive: Boolean = keepAliveStat,
        packetAction: Boolean = actionStat,
        packetAbilities: Boolean = abilitiesStat,
        packetInventory: Boolean = invStat,
        packetInteract: Boolean = interactStat,
        other: Boolean = otherPacket
    ) {
        if (release) {
            releasePacket()
        }
        movingPacketStat = (packetMoving && !off) || all
        transactionStat = (packetTransaction && !off) || all
        keepAliveStat = (packetKeepAlive && !off) || all
        actionStat = (packetAction && !off) || all
        abilitiesStat = (packetAbilities && !off) || all
        invStat = (packetInventory && !off) || all
        interactStat = (packetInteract && !off) || all
        otherPacket = (other && !off) || all

        if (all) {
            for (i in packetToggleStat.indices) {
                packetToggleStat[i] = true
            }
        } else {
            for (i in packetToggleStat.indices) {
                when (i) {
                    0x00 -> packetToggleStat[i] = keepAliveStat
                    0x01, 0x11, 0x12, 0x14, 0x15, 0x17, 0x18, 0x19 ->  packetToggleStat[i] = otherPacket
                    0x03, 0x04, 0x05, 0x06 -> packetToggleStat[i] = movingPacketStat
                    0x0F -> packetToggleStat[i] = transactionStat
                    0x02, 0x09, 0x0A, 0x0B -> packetToggleStat[i] = actionStat
                    0x0C, 0x13 -> packetToggleStat[i] = abilitiesStat
                    0x0D, 0x0E, 0x10, 0x16 -> packetToggleStat[i] = invStat
                    0x07, 0x08 -> packetToggleStat[i] = interactStat
                }
            }
        }
    }


    val packetsSize: Int
        get() = sentPackets.size + receivedPackets.size

    val isBlinking: Boolean
        get() = packetsSize > 0


    fun blink(event: SentPacketEvent, sent: Boolean = true, receive: Boolean = true) {
        mc.thePlayer ?: return

        if (mc.thePlayer.isDead || event.isCancelled)
            return

        val packet = event.packet

        if (packet is C00Handshake || packet is C00PacketServerQuery || packet is C01PacketPing)
            return

        if (sent && !receive) {
            event.isCancelled = true

            synchronized(sentPackets) {
                sentPackets += packet
            }

            if (packet is C03PacketPlayer && packet.isMoving)
                synchronized(positions) {
                    positions += Vec3(packet.x, packet.y, packet.z)
                }
        }

        if (!sent && receive)
            synchronized(sentPackets) {
                while (sentPackets.size > 0) {
                    val packet = sentPackets.removeFirst()
                    PacketUtils.sendPacketNoEvent(packet)
                }
            }

        if (sent && receive) {
            event.isCancelled = true

            synchronized(sentPackets) {
                sentPackets += packet
            }

            if (packet is C03PacketPlayer && packet.isMoving) {
                synchronized(positions) {
                    positions += Vec3(packet.x, packet.y, packet.z)
                }

                if (packet.rotating)
                    RotationUtils.serverRotation = Rotation(packet.yaw, packet.pitch)
            }
        }

        if (!sent && !receive)
            unblink()
    }

    fun clearPacket(packetType: String? = null, onlySelected: Boolean = false, amount: Int = -1) {
        when (packetType) {
            null -> {
                val tempBuffer = LinkedList<Packet<INetHandlerPlayServer>>()
                for (packets in playerBuffer) {
                    val packetID = BigInteger(packets.javaClass.simpleName.substring(1..2), 16).toInt()
                    if (!packetToggleStat[packetID] && onlySelected) {
                        tempBuffer.add(packets)
                    }
                }
                playerBuffer.clear()
                playerBuffer.addAll(tempBuffer)
            }
            else -> {
                var count = 0
                val tempBuffer = LinkedList<Packet<INetHandlerPlayServer>>()
                for (packets in playerBuffer) {
                    val className = packets.javaClass.simpleName
                    if (!className.equals(packetType, ignoreCase = true)) {
                        tempBuffer.add(packets)
                    } else {
                        count++
                        if (count > amount) {
                            tempBuffer.add(packets)
                        }
                    }
                }
                playerBuffer.clear()
                playerBuffer.addAll(tempBuffer)
            }
        }
    }


    fun blink(event: ReceivedPacketEvent, sent: Boolean = true, receive: Boolean = true) {
        mc.thePlayer ?: return

        if (mc.thePlayer.isDead || event.isCancelled)
            return

        val packet = event.packet

        if (packet is S02PacketChat || packet is S40PacketDisconnect)
            return


        if (sent && receive)
            synchronized(receivedPackets) {
                while (receivedPackets.size > 0) {
                    val packet = receivedPackets.removeFirst()
                    PacketUtils.processPacket(packet)
                }
            }

        if (!sent && receive && mc.thePlayer.ticksExisted > 10) {
            event.isCancelled = true
            synchronized(receivedPackets) {
                receivedPackets += packet
            }
        }

        if (sent && receive && mc.thePlayer.ticksExisted > 10) {
            event.isCancelled = true

            synchronized(receivedPackets) {
                receivedPackets += packet
            }
        }

        if (!sent && !receive)
            unblink()
    }

    @EventTarget
    fun onWorld(event: WorldEvent) {
        event.worldClient ?: run {
            sentPackets.clear()
            receivedPackets.clear()
            positions.clear()
        }
    }


    fun unblink() {
        mc.theWorld ?: return

        synchronized(receivedPackets) {
            while (receivedPackets.size > 0) {
                val packet = receivedPackets.removeFirst()
                PacketUtils.processPacket(packet)
            }
        }

        synchronized(sentPackets) {
            while (receivedPackets.size > 0) {
                val packet = receivedPackets.removeFirst()
                PacketUtils.sendPacketNoEvent(packet)
            }
        }

        sentPackets.clear()
        receivedPackets.clear()
        positions.clear()

        // Remove fake player
        fakePlayer?.let {
            mc.theWorld.removeEntityFromWorld(it.entityId)
            fakePlayer = null
        }
    }

    fun addFakePlayer() {
        mc.thePlayer ?: return

        val faker = EntityOtherPlayerMP(mc.theWorld, mc.thePlayer.gameProfile)

        faker.rotationYawHead = mc.thePlayer.rotationYawHead
        faker.renderYawOffset = mc.thePlayer.renderYawOffset
        faker.copyLocationAndAnglesFrom(mc.thePlayer)
        faker.rotationYawHead = mc.thePlayer.rotationYawHead
        faker.inventory = mc.thePlayer.inventory
        mc.theWorld.addEntityToWorld(-1337, faker)

        fakePlayer = faker
    }
}