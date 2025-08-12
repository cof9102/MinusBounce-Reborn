package net.minusmc.minusbounce.features.module.modules.combat

import net.minusmc.minusbounce.MinusBounce
import net.minusmc.minusbounce.event.*
import net.minusmc.minusbounce.features.module.*
import net.minusmc.minusbounce.utils.*
import net.minusmc.minusbounce.utils.timer.MSTimer
import net.minusmc.minusbounce.value.*
import net.minusmc.minusbounce.utils.player.BlinkUtils
import net.minecraft.client.entity.EntityOtherPlayerMP
import net.minecraft.entity.EntityLivingBase
import net.minecraft.network.Packet
import net.minecraft.network.play.client.*
import net.minecraft.network.play.server.*
import net.minecraft.network.play.INetHandlerPlayClient
import net.minecraft.world.WorldSettings
import java.util.concurrent.LinkedBlockingQueue

@ModuleInfo(name = "LagReach", description = "Very Lag reach", category = ModuleCategory.COMBAT)
object LagReach: Module() {
    private val modeValue = ListValue("Mode", arrayOf("FakePlayer", "IntaveTest", "AllIncomingPackets", "TargetPackets"), "FakePlayer")
    private val pulseDelayValue = IntegerValue("PulseDelay", 200, 50, 500)
    private val onlyAuraValue = BoolValue("OnlyAura", false)
    private val intavetesthurttime = IntegerValue("Packets", 5, 0, 30) { modeValue.get().equals("IntaveTest", true) }

    private var fakePlayer: EntityOtherPlayerMP? = null
    private val pulseTimer = MSTimer()
    private var currentTarget: EntityLivingBase? = null
    private var shown = false

    private val packets = LinkedBlockingQueue<Packet<*>>()


    override fun onEnable() {
        if (modeValue.get().equals("AllIncomingPackets", true))
            BlinkUtils.setBlinkState(all = true)
    }

    override fun onDisable() {
        removeFakePlayer()
        clearPackets()
        if (modeValue.get().equals("AllIncomingPackets", true))
            BlinkUtils.setBlinkState(off = true, release = true)
    }

    private fun removeFakePlayer() {
        fakePlayer ?: return
        currentTarget = null
        mc.theWorld.removeEntity(fakePlayer)
        fakePlayer = null
    }

    private fun clearPackets() {
        while (!packets.isEmpty())
            PacketUtils.handlePacket(packets.take() as Packet<INetHandlerPlayClient>)
        BlinkUtils.releasePacket()
    }


    private fun attackEntity(entity: EntityLivingBase) {
        mc.thePlayer ?: return
        mc.thePlayer.swingItem()
        mc.netHandler.addToSendQueue(C02PacketUseEntity(entity, C02PacketUseEntity.Action.ATTACK))
        if (mc.playerController.currentGameType != WorldSettings.GameType.SPECTATOR)
            mc.thePlayer.attackTargetEntityWithCurrentItem(entity)
    }

    @EventTarget
    fun onAttack(event: AttackEvent) {
        mc.theWorld ?: return
        if (modeValue.get().equals("FakePlayer", true) || modeValue.get().equals("IntaveTest", true)) {
            clearPackets()
            if (fakePlayer == null) {
                currentTarget = event.targetEntity as? EntityLivingBase ?: return
                val target = currentTarget ?: return

                val uniqueID = target.uniqueID ?: return
                val playerInfo = mc.netHandler.getPlayerInfo(uniqueID) ?: return
                val gameProfile = playerInfo.gameProfile ?: return

                val faker = EntityOtherPlayerMP(mc.theWorld, gameProfile)

                faker.rotationYawHead = target.rotationYawHead
                faker.renderYawOffset = target.renderYawOffset
                faker.copyLocationAndAnglesFrom(target)
                faker.health = target.health
                for (index in 0..4) {
                    target.getEquipmentInSlot(index)?.let {
                        faker.setCurrentItemOrArmor(index, it)
                    }
                }
                mc.theWorld.addEntityToWorld(-1337, faker)

                fakePlayer = faker
                shown = true
            } else {
                if (event.targetEntity == fakePlayer) {
                    currentTarget?.let { attackEntity(it) }
                    event.cancelEvent()
                } else {
                    removeFakePlayer()
                    currentTarget = event.targetEntity as? EntityLivingBase
                    shown = false
                }
            }
        } else {
            if (event.targetEntity != currentTarget) {
                clearPackets()
                currentTarget = event.targetEntity as? EntityLivingBase
            }
        }
    }

    @EventTarget
    fun onUpdate(event: UpdateEvent) {
        if (mc.thePlayer == null || mc.theWorld == null) return

        if (!MinusBounce.combatManager.inCombat) {
            removeFakePlayer()
        }

        if (modeValue.get().equals("FakePlayer", true) || modeValue.get().equals("IntaveTest", true)) {
            if (onlyAuraValue.get() && !MinusBounce.moduleManager[KillAura::class.java]!!.state) {
                removeFakePlayer()
                return
            }

            fakePlayer ?: return
            val target = currentTarget ?: return

            if (fakePlayer != null && EntityUtils.isRendered(fakePlayer!!) &&
                (target.isDead || !EntityUtils.isRendered(target))) {
                removeFakePlayer()
                return
            }

            fakePlayer?.health = target.health
            for (index in 0..4) {
                target.getEquipmentInSlot(index)?.let {
                    fakePlayer?.setCurrentItemOrArmor(index, it)
                }
            }

            when {
                modeValue.get().equals("IntaveTest", true) && mc.thePlayer.ticksExisted % intavetesthurttime.get() == 0 -> {
                    fakePlayer?.apply {
                        rotationYawHead = target.rotationYawHead
                        renderYawOffset = target.renderYawOffset
                        copyLocationAndAnglesFrom(target)
                    }
                    pulseTimer.reset()
                }
                modeValue.get().equals("FakePlayer", true) && pulseTimer.hasTimePassed(pulseDelayValue.get().toLong()) -> {
                    fakePlayer?.apply {
                        rotationYawHead = target.rotationYawHead
                        renderYawOffset = target.renderYawOffset
                        copyLocationAndAnglesFrom(target)
                    }
                    pulseTimer.reset()
                }
            }

            if (!shown) {
                val uniqueID = target.uniqueID ?: return
                val playerInfo = mc.netHandler.getPlayerInfo(uniqueID) ?: return
                val gameProfile = playerInfo.gameProfile ?: return

                val faker = EntityOtherPlayerMP(mc.theWorld, gameProfile).apply {
                    rotationYawHead = target.rotationYawHead
                    renderYawOffset = target.renderYawOffset
                    copyLocationAndAnglesFrom(target)
                    health = target.health
                    for (i in 0..4) {
                        target.getEquipmentInSlot(i)?.let { setCurrentItemOrArmor(i, it) }
                    }
                }
                mc.theWorld.addEntityToWorld(-1337, faker)
                fakePlayer = faker
                shown = true
            }
        } else {
            if (pulseTimer.hasTimePassed(pulseDelayValue.get().toLong())) {
                pulseTimer.reset()
                clearPackets()
            }
        }
    }

    @EventTarget
    fun onPacket(event: PacketEvent) {
        val packet = event.packet
        if (onlyAuraValue.get() && !MinusBounce.moduleManager[KillAura::class.java]!!.state) return

        when {
            modeValue.get().equals("TargetPackets", ignoreCase = true) -> {
                if (packet is S14PacketEntity && MinusBounce.combatManager.inCombat) {
                    if (packet.getEntity(mc.theWorld) == currentTarget) {
                        event.cancelEvent()
                        packets.add(packet)
                    }
                }
            }
            modeValue.get().equals("AllIncomingPackets", ignoreCase = true) -> {
                if (packet.javaClass.simpleName.startsWith("S", true) &&
                    MinusBounce.combatManager.inCombat &&
                    mc.thePlayer.ticksExisted >= 20) {
                    event.cancelEvent()
                    packets.add(packet)
                }
            }
        }
    }
}