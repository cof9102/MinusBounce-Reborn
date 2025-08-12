/*
 * MinusBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/MinusMC/MinusBounce
 */
package net.minusmc.minusbounce.features.module.modules.combat

import net.minecraft.client.settings.GameSettings
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.item.EntityArmorStand
import net.minecraft.item.ItemSword
import net.minecraft.network.play.client.C07PacketPlayerDigging
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement
import net.minecraft.network.play.client.C09PacketHeldItemChange
import net.minecraft.network.play.client.C0APacketAnimation
import net.minecraft.network.play.client.C02PacketUseEntity
import net.minecraft.util.BlockPos
import net.minecraft.util.EnumFacing
import net.minecraft.util.MovingObjectPosition
import net.minecraft.util.Vec3
import net.minusmc.minusbounce.event.*
import net.minusmc.minusbounce.features.module.Module
import net.minusmc.minusbounce.features.module.ModuleCategory
import net.minusmc.minusbounce.features.module.ModuleInfo
import net.minusmc.minusbounce.utils.ClassUtils
import net.minusmc.minusbounce.utils.EntityUtils.isSelected
import net.minusmc.minusbounce.utils.RaycastUtils
import net.minusmc.minusbounce.utils.RaycastUtils.runWithModifiedRaycastResult
import net.minusmc.minusbounce.utils.Rotation
import net.minusmc.minusbounce.utils.extensions.*
import net.minusmc.minusbounce.utils.ClientUtils
import net.minusmc.minusbounce.utils.misc.RandomUtils
import net.minusmc.minusbounce.utils.player.MovementCorrection
import net.minusmc.minusbounce.utils.player.RotationUtils
import net.minusmc.minusbounce.utils.timer.MSTimer
import net.minusmc.minusbounce.value.*
import org.lwjgl.input.Keyboard
import org.lwjgl.opengl.GL11
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.pow
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.max
import net.minecraft.item.ItemFood
import net.minecraft.item.ItemBucketMilk
import net.minecraft.item.ItemBlock
import net.minecraft.client.gui.inventory.GuiContainer
import net.minecraft.client.gui.inventory.GuiInventory
import net.minusmc.minusbounce.MinusBounce
import net.minusmc.minusbounce.features.module.modules.movement.TargetStrafe
import net.minusmc.minusbounce.features.module.modules.movement.Flight
import net.minusmc.minusbounce.features.module.modules.movement.StrafeFix
import net.minusmc.minusbounce.features.module.modules.player.Blink
import net.minusmc.minusbounce.features.module.modules.world.Scaffold
import net.minusmc.minusbounce.features.module.modules.render.FreeCam
import net.minusmc.minusbounce.utils.render.RenderUtils
import java.awt.Color
import net.minecraft.util.AxisAlignedBB
import net.minusmc.minusbounce.utils.EntityUtils
import net.minusmc.minusbounce.utils.CooldownHelper
import net.minusmc.minusbounce.utils.render.EaseUtils
import net.minusmc.minusbounce.utils.timer.TimeUtils
import org.lwjgl.util.glu.Cylinder
import kotlin.math.PI
import kotlin.math.sqrt


@ModuleInfo(name = "KillAura", spacedName = "Kill Aura", description = "Automatically attacks targets around you.", category = ModuleCategory.COMBAT, keyBind = Keyboard.KEY_R)
class KillAura : Module() {
    // Attack Options
    private val attackDisplay = BoolValue("Attack Options", true)
    private val maxCpsValue: IntegerValue = object : IntegerValue("MaxCPS", 12, 1, 20, displayable = {!simulateCooldown.get() && attackDisplay.get()}) {
        override fun onChanged(oldValue: Int, newValue: Int) {
            val i = minCpsValue.get()
            if (i > newValue) set(i)
            attackDelay = getAttackDelay(minCpsValue.get(), this.get())
        }
    }
    private val minCpsValue: IntegerValue = object : IntegerValue("MinCPS", 8, 1, 20, displayable = {!simulateCooldown.get() && attackDisplay.get()}) {
        override fun onChanged(oldValue: Int, newValue: Int) {
            val i = maxCpsValue.get()
            if (i < newValue) set(i)
            attackDelay = getAttackDelay(this.get(), maxCpsValue.get())
        }
    }
    private val CpsReduceValue = BoolValue("CPSReduceVelocity", false, displayable = {attackDisplay.get()})
    private val simulateCooldown = BoolValue("SimulateCooldown", false, displayable = {attackDisplay.get()})
    private val cooldownNoDupAtk = BoolValue("NoDuplicateAttack", false, displayable = {simulateCooldown.get() && attackDisplay.get()})
    private val hurtTimeValue = IntegerValue("HurtTime", 10, 0, 10, displayable = {attackDisplay.get()})
    private val clickOnly = BoolValue("ClickOnly", false, displayable = {attackDisplay.get()})

    // Range Options
    private val rangeValue = object : FloatValue("Range", 3.7f, 0f, 8f, displayable = {attackDisplay.get()}) {
        override fun onChanged(oldValue: Float, newValue: Float) {
            val i = throughWallsRangeValue.get()
            if (i < newValue) set(i)
        }
    }
    private val throughWallsRangeValue = object : FloatValue("ThroughWallsRange", 1.5f, 0f, 8f, displayable = {attackDisplay.get()}) {
        override fun onChanged(oldValue: Float, newValue: Float) {
            val i = rangeValue.get()
            if (i < newValue) set(i)
        }
    }
    private val rangeSprintReducementValue = FloatValue("RangeSprintReducement", 0f, 0f, 0.4f, displayable = {attackDisplay.get()})
    private val swingRangeValue = object : FloatValue("SwingRange", 5f, 0f, 8f, displayable = {attackDisplay.get()}) {
        override fun onChanged(oldValue: Float, newValue: Float) {
            val i = rotationRangeValue.get()
            if (i < newValue) set(i)
            if (rotationRangeValue.get() > newValue) set(rotationRangeValue.get())
        }
    }
    private val rotationRangeValue: FloatValue = object : FloatValue("RotationRange", 6f, 0f, 8f, displayable = {attackDisplay.get()}) {
        override fun onPostChange(oldValue: Float, newValue: Float) {
            set(newValue.coerceAtLeast(rangeValue.get()))
        }
    }
    private val hitselectValue = BoolValue("Hitselect", false, displayable = {attackDisplay.get()})
    private val hitselectRangeValue = FloatValue("HitselectRange", 2.7f, 2f, 4f, displayable = {attackDisplay.get()})
    private val blinkCheck = BoolValue("BlinkCheck", true, displayable = {attackDisplay.get()})
    private val noScaffValue = BoolValue("NoScaffold", true, displayable = {attackDisplay.get()})
    private val noFlyValue = BoolValue("NoFly", false, displayable = {attackDisplay.get()})
    private val noEat = BoolValue("NoEat", true, displayable = {attackDisplay.get()})
    private val noBlocking = BoolValue("NoBlocking", false, displayable = {attackDisplay.get()})
    private val swingValue = ListValue("Swing", arrayOf("Normal", "Packet", "None"), "Normal", displayable = {attackDisplay.get()})
    private val attackTimingValue = ListValue("AttackTiming", arrayOf("All", "Pre", "Post"), "All", displayable = {attackDisplay.get()})
    private val keepSprintValue = BoolValue("KeepSprint", true, displayable = {attackDisplay.get()})
    private val noBadPacketsValue = BoolValue("NoBadPackets", false, displayable = {attackDisplay.get()})

    // AutoBlock Options
    private val autoBlockModeValue: ListValue = object : ListValue("AutoBlock", arrayOf("None", "AfterTick", "Vanilla", "NewNCP", "RightHold", "Swing", "Legit", "HoldKey", "KeyBlock", "Gay"), "None") {
        override fun onPreChange(oldValue: String, newValue: String) {
            if (state) onDisable()
        }

        override fun onPostChange(oldValue: String, newValue: String) {
            if (state) onEnable()
        }
    }
    private val interactValue = BoolValue("InteractAutoBlock", true)
    private val autoBlockRangeValue = FloatValue("AutoBlock-Range", 5f, 0f, 12f, "m", displayable = {!autoBlockModeValue.get().equals("None", true)})
    private val smartAutoBlockValue = BoolValue("SmartAutoBlock", false, displayable = {!autoBlockModeValue.get().equals("None", true)})
    private val blockRateValue = IntegerValue("BlockRate", 100, 1, 100, displayable = {!autoBlockModeValue.get().equals("None", true)})
    private val alwaysBlockDisplayValue = BoolValue("AlwaysRenderBlocking", true, displayable = {!autoBlockModeValue.get().equals("None", true)})

    // Rotation Options
    private val rotationDisplay = BoolValue("Rotation Options", true)
    private val priorityValue = ListValue("Priority", arrayOf("Health", "Distance", "Direction", "LivingTime", "Armor", "HurtResistance", "HurtTime", "HealthAbsorption", "RegenAmplifier"), "Distance", displayable = {rotationDisplay.get()})
    private val switchDelayValue = IntegerValue("SwitchDelay", 1000, 1, 2000, "ms", displayable = {targetModeValue.get().equals("Switch", true) && rotationDisplay.get()})
    private val limitedMultiTargetsValue = IntegerValue("LimitedMultiTargets", 1, 1, 50, displayable = {targetModeValue.get().equals("Multi", true) && rotationDisplay.get()})
    private val rotationModeValue = ListValue("RotationMode", arrayOf("None", "LiquidBounce", "ForceCenter", "SmoothCenter", "SmoothLiquid", "LockView", "OldMatrix", "Test", "SmoothCustom"), "LiquidBounce", displayable = {rotationDisplay.get()})
    private val customRotationValue = ListValue("CustomRotationMode", arrayOf("LiquidBounce", "Full", "HalfUp", "HalfDown", "CenterSimple", "CenterLine"), "HalfUp", displayable = {rotationDisplay.get() && rotationModeValue.equals("SmoothCustom")})
    private val silentRotationValue = BoolValue("SilentRotation", true, displayable = {!rotationModeValue.equals("None") && rotationDisplay.get()})
    private val hitAbleValue = BoolValue("AlwaysHitAble", true, displayable = {rotationDisplay.get()})
    private val fovValue = FloatValue("FOV", 180f, 0f, 180f, displayable = {rotationDisplay.get()})
    private val maxTurnSpeedValue: FloatValue = object : FloatValue("MaxTurnSpeed", 360f, 1f, 360f, "°", displayable = {rotationDisplay.get() && !rotationModeValue.equals("LockView")}) {
        override fun onChanged(oldValue: Float, newValue: Float) {
            val v = minTurnSpeedValue.get()
            if (v > newValue) set(v)
        }
    }
    private val minTurnSpeedValue: FloatValue = object : FloatValue("MinTurnSpeed", 360f, 1f, 360f, "°", displayable = {rotationDisplay.get() && !rotationModeValue.equals("LockView")}) {
        override fun onChanged(oldValue: Float, newValue: Float) {
            val v = maxTurnSpeedValue.get()
            if (v < newValue) set(v)
        }
    }
    private val rotationSmoothModeValue = ListValue("SmoothMode", arrayOf("Custom", "Line", "Quad", "Sine", "QuadSine"), "Custom", displayable = {rotationDisplay.get() && !rotationModeValue.equals("LiquidBounce") && !rotationModeValue.equals("ForceCenter") && !rotationModeValue.equals("LockView")})
    private val rotationSmoothValue = FloatValue("CustomSmooth", 2f, 1f, 10f, displayable = {rotationSmoothModeValue.equals("Custom") && rotationSmoothModeValue.displayable})
    private val randomCenterModeValue = ListValue("RandomCenter", arrayOf("Off", "Cubic", "Horizontal", "Vertical"), "Off", displayable = {rotationDisplay.get()})
    private val randomCenRangeValue = FloatValue("RandomRange", 0.0f, 0.0f, 1.2f, displayable = {!randomCenterModeValue.equals("Off") && rotationDisplay.get()})
    private val rotationRevValue = BoolValue("RotationReverse", false, displayable = {!rotationModeValue.equals("None") && rotationDisplay.get()})
    private val rotationRevTickValue = IntegerValue("RotationReverseTick", 5, 1, 20, displayable = {rotationRevValue.get() && rotationRevValue.displayable})
    private val keepDirectionValue = BoolValue("KeepDirection", true, displayable = {!rotationModeValue.equals("None") && rotationDisplay.get()})
    private val keepDirectionTickValue = IntegerValue("KeepDirectionTick", 15, 1, 20, displayable = {keepDirectionValue.get() && keepDirectionValue.displayable})
    private val rotationDelayValue = BoolValue("RotationDelay", false, displayable = {!rotationModeValue.equals("None") && rotationDisplay.get()})
    private val rotationDelayMSValue = IntegerValue("RotationDelayMS", 300, 0, 1000, displayable = {rotationDelayValue.get() && rotationDelayValue.displayable})
    private val predictValue = BoolValue("Predict", true, displayable = {!rotationModeValue.equals("None") && rotationDisplay.get()})
    private val maxPredictSizeValue: FloatValue = object : FloatValue("MaxPredictSize", 1f, -2f, 5f, displayable = {predictValue.displayable && predictValue.get()}) {
        override fun onChanged(oldValue: Float, newValue: Float) {
            val v = minPredictSizeValue.get()
            if (v > newValue) set(v)
        }
    }
    private val minPredictSizeValue: FloatValue = object : FloatValue("MinPredictSize", 1f, -2f, 5f, displayable = {predictValue.displayable && predictValue.get()}) {
        override fun onChanged(oldValue: Float, newValue: Float) {
            val v = maxPredictSizeValue.get()
            if (v < newValue) set(v)
        }
    }
    private val predictPlayerValue = BoolValue("PredictPlayer", true, displayable = {!rotationModeValue.equals("None") && rotationDisplay.get()})
    private val maxPredictPlayerSizeValue: FloatValue = object : FloatValue("MaxPredictPlayerSize", 1f, -1f, 4f, displayable = {predictPlayerValue.displayable && predictPlayerValue.get()}) {
        override fun onChanged(oldValue: Float, newValue: Float) {
            val v = minPredictPlayerSizeValue.get()
            if (v > newValue) set(v)
        }
    }
    private val minPredictPlayerSizeValue: FloatValue = object : FloatValue("MinPredictPlayerSize", 1f, -1f, 4f, displayable = {predictPlayerValue.displayable && predictPlayerValue.get()}) {
        override fun onChanged(oldValue: Float, newValue: Float) {
            val v = maxPredictPlayerSizeValue.get()
            if (v < newValue) set(v)
        }
    }

    // Bypass Options
    private val bypassDisplay = BoolValue("Bypass Options", true)
    private val rotationStrafeValue = ListValue("Strafe", arrayOf("Off", "Strict", "Silent"), "Silent", displayable = {silentRotationValue.get() && !rotationModeValue.equals("None") && bypassDisplay.get()})
    private val failRateValue = FloatValue("FailRate", 0f, 0f, 100f, displayable = {bypassDisplay.get()})
    private val fakeSwingValue = BoolValue("FakeSwing", true, displayable = {failRateValue.get() != 0f && failRateValue.displayable})
    private val noInventoryAttackValue = ListValue("NoInvAttack", arrayOf("Spoof", "CancelRun", "Off"), "Off", displayable = {bypassDisplay.get()})
    private val noInventoryDelayValue = IntegerValue("NoInvDelay", 200, 0, 500, displayable = {!noInventoryAttackValue.equals("Off") && noInventoryAttackValue.displayable})

    // Visual Options
    private val visualDisplay = BoolValue("Visual Options", false)
    private val markValue = ListValue("Mark", arrayOf("Liquid", "FDP", "Block", "OtherBlock", "Jello", "Sims", "Lies", "None"), "Jello", displayable = {visualDisplay.get()})
    private val blockMarkExpandValue = FloatValue("BlockExpandValue", 0.2f, -0.5f, 1f, displayable = {markValue.displayable && (markValue.equals("Block") || markValue.equals("OtherBlock")})
    private val circleValue = BoolValue("Circle", true, displayable = {visualDisplay.get()})
    private val circleRedValue = IntegerValue("CircleRed", 255, 0, 255, displayable = {circleValue.get() && circleValue.displayable})
    private val circleGreenValue = IntegerValue("CircleGreen", 255, 0, 255, displayable = {circleValue.get() && circleValue.displayable})
    private val circleBlueValue = IntegerValue("CircleBlue", 255, 0, 255, displayable = {circleValue.get() && circleValue.displayable})
    private val circleAlphaValue = IntegerValue("CircleAlpha", 255, 0, 255, displayable = {circleValue.get() && circleValue.displayable})
    private val circleThicknessValue = FloatValue("CircleThickness", 2F, 1F, 5F, displayable = {circleValue.get() && circleValue.displayable})
    private val displayMode = ListValue("DisplayMode", arrayOf("Simple", "LessSimple", "Complicated"), "Simple", displayable = {visualDisplay.get()})

    // Target
    var target: EntityLivingBase? = null
    private var hitable = false
    private var packetSent = false
    private val prevTargetEntities = mutableListOf<Int>()
    private val discoveredTargets = mutableListOf<EntityLivingBase>()
    private val inRangeDiscoveredTargets = mutableListOf<EntityLivingBase>()
    private val canFakeBlock: Boolean
        get() = inRangeDiscoveredTargets.isNotEmpty()

    // Attack delay
    private val attackTimer = MSTimer()
    private val switchTimer = MSTimer()
    private val rotationTimer = MSTimer()
    private var attackDelay = 0L
    private var clicks = 0

    // Container Delay
    private var containerOpen = -1L

    // Swing
    private var canSwing = false

    // Last Tick Can Be Seen
    private var lastCanBeSeen = false

    // Fake block status
    var blockingStatus = false
    private var espAnimation = 0.0
    private var isUp = true

    val displayBlocking: Boolean
        get() = blockingStatus || (autoBlockModeValue.get().equals("Fake") || (alwaysBlockDisplayValue.get() && !autoBlockModeValue.get().equals("None") && canFakeBlock)

    private var predictAmount = 1.0f
    private var predictPlayerAmount = 1.0f

    // hit select
    private var canHitselect = false
    private val hitselectTimer = MSTimer()

    private val delayBlockTimer = MSTimer()
    private var delayBlock = false
    private var legitBlocking = 0

    private val getAABB: ((Entity) -> AxisAlignedBB) = {
        var aabb = it.hitBox
        aabb = if (predictValue.get()) aabb.offset(
            (it.posX - it.lastTickPosX) * predictAmount,
            (it.posY - it.lastTickPosY) * predictAmount,
            (it.posZ - it.lastTickPosZ) * predictAmount
        ) else aabb
        aabb = if (predictPlayerValue.get()) aabb.offset(
            mc.thePlayer.motionX * predictPlayerAmount * -1f,
            mc.thePlayer.motionY * predictPlayerAmount * -1f,
            mc.thePlayer.motionZ * predictPlayerAmount * -1f
        ) else aabb
        aabb.expand(
            it.collisionBorderSize.toDouble(),
            it.collisionBorderSize.toDouble(),
            it.collisionBorderSize.toDouble()
        )
        aabb
    }

    override fun onEnable() {
        mc.theWorld ?: return
        lastCanBeSeen = false
        delayBlock = false
        legitBlocking = 0
        updateTarget()
    }

    override fun onDisable() {
        MinusBounce.moduleManager[TargetStrafe::class.java]?.doStrafe = false
        target = null
        hitable = false
        packetSent = false
        prevTargetEntities.clear()
        discoveredTargets.clear()
        inRangeDiscoveredTargets.clear()
        attackTimer.reset()
        clicks = 0
        canSwing = false

        stopBlocking {
            mc.netHandler.addToSendQueue(C07PacketPlayerDigging(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN))
        }
        if (autoBlockModeValue.equals("HoldKey") || autoBlockModeValue.equals("KeyBlock")) {
            mc.gameSettings.keyBindUseItem.pressed = false
        }
        
        RotationUtils.setTargetRotationReverse(
            RotationUtils.serverRotation,
            if (keepDirectionValue.get()) keepDirectionTickValue.get() + 1 else 1,
            if (rotationRevValue.get()) rotationRevTickValue.get() + 1 else 0
        )
    }

    @EventTarget
    fun onMotion(event: MotionEvent) {
        if (event.eventState == EventState.POST) {
            packetSent = false
        }

        updateHitable()
        val target = this.target ?: discoveredTargets.getOrNull(0) ?: return
        
        if (autoBlockModeValue.equals("HoldKey") && canBlock) {
            if (inRangeDiscoveredTargets.isEmpty()) {
                mc.gameSettings.keyBindUseItem.pressed = false
            } else if (mc.thePlayer.getDistanceToEntityBox(target) < maxRange) {
                mc.gameSettings.keyBindUseItem.pressed = true
            }
        }

        if ((attackTimingValue.equals("Pre") && event.eventState != EventState.PRE) || 
            (attackTimingValue.equals("Post") && event.eventState != EventState.POST) || 
            attackTimingValue.equals("All")) {
            runAttackLoop()
        }

        if (packetSent && noBadPacketsValue.get()) {
            return
        }
        
        // AutoBlock
        if (!autoBlockModeValue.equals("None") && discoveredTargets.isNotEmpty() && (!autoBlockModeValue.equals("AfterAttack") || 
                discoveredTargets.any { mc.thePlayer.getDistanceToEntityBox(it) > maxRange }) && canBlock) {
            if (mc.thePlayer.getDistanceToEntityBox(target) <= autoBlockRangeValue.get()) {
                startBlocking(target, interactValue.get() && (mc.thePlayer.getDistanceToEntityBox(target) < maxRange))
            } else {
                if (!mc.thePlayer.isBlocking) {
                    stopBlocking { 
                        mc.netHandler.addToSendQueue(C07PacketPlayerDigging(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN))
                    }
                }
            }
        }
    }

    @EventTarget
    fun onUpdate(ignoredEvent: UpdateEvent) {
        if (clickOnly.get() && !mc.gameSettings.keyBindAttack.isKeyDown) return

        if (cancelRun) {
            target = null
            hitable = false
            stopBlocking { 
                mc.netHandler.addToSendQueue(C07PacketPlayerDigging(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN))
            }
            discoveredTargets.clear()
            inRangeDiscoveredTargets.clear()
            return
        }

        if (noInventoryAttackValue.equals("CancelRun") && (mc.currentScreen is GuiContainer ||
                    System.currentTimeMillis() - containerOpen < noInventoryDelayValue.get())) {
            target = null
            hitable = false
            if (mc.currentScreen is GuiContainer) containerOpen = System.currentTimeMillis()
            return
        }

        updateTarget()

        if (discoveredTargets.isEmpty()) {
            stopBlocking { 
                mc.netHandler.addToSendQueue(C07PacketPlayerDigging(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN))
            }
            return
        }
        
        MinusBounce.moduleManager[TargetStrafe::class.java]?.targetEntity = target
        MinusBounce.moduleManager[StrafeFix::class.java]?.applyForceStrafe(rotationStrafeValue.equals("Silent"), !rotationStrafeValue.equals("Off") && !rotationModeValue.equals("None"))

        val target = this.target ?: discoveredTargets.getOrNull(0) ?: return
        
        if (autoBlockModeValue.equals("Delayed2") || autoBlockModeValue.equals("Test")) {
             if (mc.thePlayer.swingProgressInt == 1) {
                 startBlocking(target, interactValue.get() && (mc.thePlayer.getDistanceToEntityBox(target) < maxRange))
            }
        }

        if (autoBlockModeValue.equals("Gay")) {
            if (mc.thePlayer.ticksExisted % 3 == 1) {
                startBlocking(target, interactValue.get() && (mc.thePlayer.getDistanceToEntityBox(target) < maxRange))
            } else if (mc.thePlayer.ticksExisted % 3 == 2) {
                stopBlocking { 
                    mc.netHandler.addToSendQueue(C07PacketPlayerDigging(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN))
                }
            }
        }

        if (attackTimingValue.equals("All")) {
            runAttackLoop()
        }

        if (legitBlocking < 1 && autoBlockModeValue.equals("Legit")) {
            if (blockingStatus) stopBlocking { 
                mc.netHandler.addToSendQueue(C07PacketPlayerDigging(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN))
            }
            blockingStatus = false
        }
    }

    private fun runAttackLoop() {
        if (autoBlockModeValue.equals("Gay")) {
            if (mc.thePlayer.ticksExisted % 3 > 0) {
                return
            }
        }

        if (CpsReduceValue.get() && mc.thePlayer.hurtTime > 8){
            clicks += 4
        }

        // legit auto block
        if (autoBlockModeValue.equals("Legit")) {
            if (mc.thePlayer.hurtTime > 8) {
                legitBlocking = 0
                if (blockingStatus) stopBlocking { 
                    mc.netHandler.addToSendQueue(C07PacketPlayerDigging(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN))
                }
                blockingStatus = false
            } else {
                if (mc.thePlayer.hurtTime == 2) {
                    legitBlocking = 5
                } else if (legitBlocking > 0) {
                    legitBlocking--
                    if (discoveredTargets.isNotEmpty() && !blockingStatus) {
                        val target = this.target ?: discoveredTargets.first()
                        startBlocking(target, interactValue.get() && (mc.thePlayer.getDistanceToEntityBox(target) < maxRange))
                        blockingStatus = true
                    }
                    if (clicks > 0)
                        clicks = 1
                    return
                } else {
                    if (!canHitselect && hitselectValue.get()) {
                        legitBlocking = 3
                    } else {
                        if (blockingStatus) stopBlocking { 
                            mc.netHandler.addToSendQueue(C07PacketPlayerDigging(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN))
                        }
                        blockingStatus = false
                    }
                }
            }
        }

        // hit select
        if (hitselectValue.get()) {
            if (canHitselect) {
                if (inRangeDiscoveredTargets.isEmpty() && hitselectTimer.hasTimePassed(900L)) canHitselect = false
            } else {
                if (mc.thePlayer.hurtTime > 7) {
                    canHitselect = true
                    hitselectTimer.reset()
                }
                inRangeDiscoveredTargets.forEachIndexed { index, entity -> 
                    if (mc.thePlayer.getDistanceToEntityBox(entity) < hitselectRangeValue.get()) {
                        canHitselect = true
                        hitselectTimer.reset()
                    }
                }
            }
            if (!canHitselect) {
                if (clicks > 0)
                    clicks = 1
                return
            }
        }

        if (autoBlockModeValue.equals("Test") && blockingStatus) {
            stopBlocking { 
                mc.netHandler.addToSendQueue(C07PacketPlayerDigging(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN))
            }
            if (clicks > 0) {
                clicks = 1
            }
            return
        }

        if (simulateCooldown.get() && CooldownHelper.getAttackCooldownProgress() < 1.0f) {
            return
        }

        if (simulateCooldown.get() && cooldownNoDupAtk.get() && clicks > 0) {
            clicks = 1
        }

        try {
            while (clicks > 0) {
                runAttack()
                clicks--
            }
        } catch (e: java.lang.IllegalStateException) {
            return
        }
    }

    private fun runAttack() {
        target ?: return

        // Settings
        val failRate = failRateValue.get()
        val openInventory = noInventoryAttackValue.equals("Spoof") && mc.currentScreen is GuiInventory
        val failHit = failRate > 0 && Random().nextInt(100) <= failRate

        // Check is not hitable or check failrate
        if (hitable && !failHit) {
            // Close inventory when open
            if (openInventory) {
                mc.netHandler.addToSendQueue(C0DPacketCloseWindow())
            }

            // Attack
            if (!targetModeValue.get().equals("Multi", true)) {
                attackEntity(if (raycastValue.get()) {
                    (RaycastUtils.raycastEntity(maxRange.toDouble()) {
                        it is EntityLivingBase && it !is EntityArmorStand && (!raycastValue.get() || EntityUtils.canRayCast(it)) && !EntityUtils.isFriend(it)
                    } ?: target!!) as EntityLivingBase } else { target!! })
            } else {
                inRangeDiscoveredTargets.forEachIndexed { index, entity ->
                    if (limitedMultiTargetsValue.get() == 0 || index < limitedMultiTargetsValue.get()) {
                        attackEntity(entity)
                    }
                }
            }

            if (targetModeValue.get().equals("Switch", true)) {
                if (switchTimer.hasTimePassed(switchDelayValue.get().toLong())) {
                    prevTargetEntities.add(target!!.entityId)
                    switchTimer.reset()
                }
            } else {
                prevTargetEntities.add(target!!.entityId)
            }

            // Open inventory
            if (openInventory) {
                mc.netHandler.addToSendQueue(C16PacketClientStatus(C16PacketClientStatus.EnumState.OPEN_INVENTORY_ACHIEVEMENT))
            }
        } else if (fakeSwingValue.get() || canSwing) {
            runSwing()
        }
    }

    private fun updateTarget() {
        // Settings
        val fov = fovValue.get()
        val switchMode = targetModeValue.get().equals("Switch", true)

        // Find possible targets
        discoveredTargets.clear()

        for (entity in mc.theWorld.loadedEntityList) {
            if (entity !is EntityLivingBase || !EntityUtils.isSelected(entity, true) || 
                (switchMode && prevTargetEntities.contains(entity.entityId))) {
                continue
            }

            var distance = mc.thePlayer.getDistanceToEntityBox(entity)
            // Backtrack would be implemented separately

            val entityFov = RotationUtils.getRotationDifference(entity)

            if (distance <= rotationRangeValue.get() && (fov == 180F || entityFov <= fov)) {
                discoveredTargets.add(entity)
            }
        }

        // Sort targets by priority
        when (priorityValue.get().lowercase()) {
            "distance" -> discoveredTargets.sortBy { mc.thePlayer.getDistanceToEntityBox(it) }
            "health" -> discoveredTargets.sortBy { it.health + it.absorptionAmount }
            "direction" -> discoveredTargets.sortBy { RotationUtils.getRotationDifference(it) }
            "livingtime" -> discoveredTargets.sortBy { -it.ticksExisted }
            "armor" -> discoveredTargets.sortBy { it.totalArmorValue }
            "hurtresistance" -> discoveredTargets.sortBy { it.hurtResistantTime }
            "hurttime" -> discoveredTargets.sortBy { it.hurtTime }
            "healthabsorption" -> discoveredTargets.sortBy { it.absorptionAmount }
            "regenamplifier" -> discoveredTargets.sortBy { if (it.isPotionActive(Potion.regeneration)) it.getActivePotionEffect(Potion.regeneration).amplifier else -1 }
        }

        inRangeDiscoveredTargets.clear()
        inRangeDiscoveredTargets.addAll(discoveredTargets.filter { 
            mc.thePlayer.getDistanceToEntityBox(it) < (rangeValue.get() - if (mc.thePlayer.isSprinting) rangeSprintReducementValue.get() else 0F) 
        })

        // Cleanup last targets when no targets found
        if (inRangeDiscoveredTargets.isEmpty() && prevTargetEntities.isNotEmpty()) {
            prevTargetEntities.clear()
            updateTarget()
            return
        }

        // Find best target
        for (entity in discoveredTargets) {
            // Update rotations to current target
            if (!updateRotations(entity)) {
                // Backtrack implementation would go here
                continue
            }

            // Set target to current entity
            if (mc.thePlayer.getDistanceToEntityBox(entity) < rotationRangeValue.get()) {
                target = entity
                MinusBounce.moduleManager[TargetStrafe::class.java]?.targetEntity = target
                MinusBounce.moduleManager[TargetStrafe::class.java]?.doStrafe = MinusBounce.moduleManager[TargetStrafe::class.java]?.toggleStrafe() ?: false
                return
            }
        }

        target = null
        MinusBounce.moduleManager[TargetStrafe::class.java]?.doStrafe = false
    }

    private fun runSwing() {
        val swing = swingValue.get()
        if (swing.equals("packet", true)) {
            mc.netHandler.addToSendQueue(C0APacketAnimation())
        } else if (swing.equals("normal", true)) {
            mc.thePlayer.swingItem()
        }
    }

    private fun attackEntity(entity: EntityLivingBase) {
        if (packetSent && noBadPacketsValue.get()) return

        // Call attack event
        // EventManager.callEvent(AttackEvent(entity)) - Implement your event system

        // Stop blocking
        preAttack()

        // Attack target
        runSwing()
        packetSent = true
        mc.netHandler.addToSendQueue(C02PacketUseEntity(entity, C02PacketUseEntity.Action.ATTACK))

        swingKeepSprint(entity)

        postAttack(entity)

        CooldownHelper.resetLastAttackedTicks()
    }

    private fun preAttack() {
        if (mc.thePlayer.isBlocking || blockingStatus) {
            when (autoBlockModeValue.get().lowercase()) {
                "vanilla" -> null
                "aftertick", "afterattack", "delayed", "delayed2" -> stopBlocking { 
                    mc.netHandler.addToSendQueue(C07PacketPlayerDigging(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN))
                }
                "newncp" -> {
                    mc.netHandler.addToSendQueue(C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem % 8 + 1))
                    mc.netHandler.addToSendQueue(C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem))
                    blockingStatus = false
                }
                "keyblock" -> mc.gameSettings.keyBindUseItem.pressed = false
                "legit", "test", "holdkey", "gay" -> null
                else -> null
            }
        }
    }

    private fun postAttack(entity: EntityLivingBase) {
        if (mc.thePlayer.isBlocking || (!autoBlockModeValue.equals("None") && canBlock)) {
            if (blockRateValue.get() > 0 && Random().nextInt(100) <= blockRateValue.get()) {
                if (smartAutoBlockValue.get() && clicks != 1 && mc.thePlayer.hurtTime < 4 && mc.thePlayer.getDistanceToEntityBox(entity) < 4) {
                    return
                }
                when (autoBlockModeValue.get().lowercase()) {
                    "vanilla", "afterattack", "newncp" -> startBlocking(entity, interactValue.get() && (mc.thePlayer.getDistanceToEntityBox(entity) < maxRange))
                    "delayed", "keyblock" -> delayBlockTimer.reset()
                    "aftertick", "legit", "delayed2", "test", "holdkey", "gay" -> null
                    else -> null
                }
            }
        }
    }

    private fun swingKeepSprint(entity: EntityLivingBase) {
        if (keepSprintValue.get()) {
            // Enchant Effect
            // Implement enchant effect
        } else {
            if (mc.playerController.currentGameType != WorldSettings.GameType.SPECTATOR) {
                mc.thePlayer.attackTargetEntityWithCurrentItem(entity)
            }
        }
    }

    private fun updateRotations(entity: Entity): Boolean {
        if (rotationModeValue.equals("None")) {
            return true
        }

        // View differences
        val entityFov = RotationUtils.getRotationDifference(RotationUtils.toRotation(RotationUtils.getCenter(entity.hitBox), true), RotationUtils.serverRotation)

        if (entityFov <= mc.gameSettings.fovSetting) lastCanBeSeen = true
        else if (lastCanBeSeen) {
            rotationTimer.reset()
            lastCanBeSeen = false
        }

        if (predictValue.get()) {
            predictAmount = RandomUtils.nextFloat(maxPredictSizeValue.get(), minPredictSizeValue.get())
        }
        if (predictPlayerValue.get()) {
            predictPlayerAmount = RandomUtils.nextFloat(maxPredictPlayerSizeValue.get(), minPredictPlayerSizeValue.get())
        }

        val boundingBox = if (rotationModeValue.get() == "Test") entity.hitBox else getAABB(entity)

        val rModes = when (rotationModeValue.get()) {
            "LiquidBounce", "SmoothLiquid" -> "LiquidBounce"
            "ForceCenter", "SmoothCenter", "OldMatrix", -> "CenterLine"
            "LockView" -> "CenterSimple"
            "SmoothCustom" -> customRotationValue.get()
            else -> "LiquidBounce"
        }

        val (_, directRotation) = RotationUtils.calculateCenter(
            rModes,
            randomCenterModeValue.get(),
            randomCenRangeValue.get().toDouble(),
            boundingBox,
            predictValue.get(),
            true
        ) ?: return false

        if (rotationModeValue.get() == "OldMatrix") directRotation.pitch = 89.9f

        var diffAngle = RotationUtils.getRotationDifference(RotationUtils.serverRotation, directRotation)
        if (diffAngle < 0) diffAngle = -diffAngle
        if (diffAngle > 180.0) diffAngle = 180.0

        val calculateSpeed = when (rotationSmoothModeValue.get()) {
            "Custom" -> diffAngle / rotationSmoothValue.get()
            "Line" -> (diffAngle / 360) * maxTurnSpeedValue.get() + (1 - diffAngle / 360) * minTurnSpeedValue.get()
            "Quad" -> (diffAngle / 360.0).pow(2.0) * maxTurnSpeedValue.get() + (1 - (diffAngle / 360.0).pow(2.0)) * minTurnSpeedValue.get()
            "Sine" -> (-cos(diffAngle / 180 * PI) * 0.5 + 0.5) * maxTurnSpeedValue.get() + (cos(diffAngle / 360 * PI) * 0.5 + 0.5) * minTurnSpeedValue.get()
            "QuadSine" -> (-cos(diffAngle / 180 * PI) * 0.5 + 0.5).pow(2.0) * maxTurnSpeedValue.get() + (1 - (-cos(diffAngle / 180 * PI) * 0.5 + 0.5).pow(2.0)) * minTurnSpeedValue.get()
            else -> 360.0
        }

        if (!lastCanBeSeen && rotationDelayValue.get() && !rotationTimer.hasTimePassed(rotationDelayMSValue.get().toLong())) return true

        val rotation = when (rotationModeValue.get()) {
            "LiquidBounce", "ForceCenter" -> RotationUtils.limitAngleChange(
                RotationUtils.serverRotation, directRotation,
                (Math.random() * (maxTurnSpeedValue.get() - minTurnSpeedValue.get()) + minTurnSpeedValue.get()).toFloat()
            )
            "LockView" -> RotationUtils.limitAngleChange(
                RotationUtils.serverRotation,
                directRotation,
                180.0f
            )
            "SmoothCenter", "SmoothLiquid", "SmoothCustom", "OldMatrix" -> RotationUtils.limitAngleChange(
                RotationUtils.serverRotation,
                directRotation,
                calculateSpeed.toFloat()
            )
            else -> return true
        }

        if (silentRotationValue.get()) {
            RotationUtils.setTargetRotationReverse(
                rotation,
                if (keepDirectionValue.get()) keepDirectionTickValue.get() else 1,
                if (rotationRevValue.get()) rotationRevTickValue.get() else 0
            )
        } else {
            rotation.toPlayer(mc.thePlayer)
        }
        return true
    }

    private fun updateHitable() {
        if (target == null) {
            canSwing = false
            hitable = false
            return
        }
        val entityDist = mc.thePlayer.getDistanceToEntityBox(target as Entity)
        canSwing = entityDist < swingRangeValue.get() && (target as EntityLivingBase).hurtTime <= hurtTimeValue.get()
        if (hitAbleValue.get()) {
            hitable = entityDist <= maxRange.toDouble()
            return
        }
        if (maxTurnSpeedValue.get() <= 0F) {
            hitable = true
            return
        }
        val wallTrace = mc.thePlayer.rayTraceWithServerSideRotation(entityDist)
        hitable = RotationUtils.isFaced(target, maxRange.toDouble()) && 
                  (entityDist < throughWallsRangeValue.get() || wallTrace?.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) && 
                  (target as EntityLivingBase).hurtTime <= hurtTimeValue.get()
    }

    private fun startBlocking(interactEntity: Entity, interact: Boolean) {
        if (!autoBlockModeValue.equals("None") && mc.thePlayer.getDistanceToEntityBox(interactEntity) > autoBlockRangeValue.get()) {
            return
        }

        if (blockingStatus) {
            return
        }

        if (packetSent && noBadPacketsValue.get()) {
            return
        }

        if (interact) {
            val positionEye = mc.renderViewEntity?.getPositionEyes(1F)
            val expandSize = interactEntity.collisionBorderSize.toDouble()
            val boundingBox = interactEntity.hitBox
            val rotation = RotationUtils.currentRotation ?: mc.thePlayer.rotation
            val vec = RotationUtils.getVectorForRotation(rotation)
            val lookAt = positionEye!!.addVector(vec.xCoord * maxRange, vec.yCoord * maxRange, vec.zCoord * maxRange)

            val movingObject = boundingBox.calculateIntercept(positionEye, lookAt) ?: return
            val hitVec = movingObject.hitVec

            mc.netHandler.addToSendQueue(C02PacketUseEntity(interactEntity, hitVec - interactEntity.positionVector))
        }

        mc.netHandler.addToSendQueue(C08PacketPlayerBlockPlacement(mc.thePlayer.inventory.getCurrentItem()))
        blockingStatus = true
        packetSent = true
    }

    private fun stopBlocking(sendPacketUnblocking: () -> Unit) {
        if (blockingStatus) {   
            sendPacketUnblocking()
            blockingStatus = false
        }
    }

    @EventTarget
    fun onRender3D(event: Render3DEvent) {
        if (circleValue.get()) {
            GL11.glPushMatrix()
            GL11.glTranslated(
                mc.thePlayer.lastTickPosX + (mc.thePlayer.posX - mc.thePlayer.lastTickPosX) * mc.timer.renderPartialTicks - mc.renderManager.renderPosX,
                mc.thePlayer.lastTickPosY + (mc.thePlayer.posY - mc.thePlayer.lastTickPosY) * mc.timer.renderPartialTicks - mc.renderManager.renderPosY,
                mc.thePlayer.lastTickPosZ + (mc.thePlayer.posZ - mc.thePlayer.lastTickPosZ) * mc.timer.renderPartialTicks - mc.renderManager.renderPosZ
            )
            GL11.glEnable(GL11.GL_BLEND)
            GL11.glEnable(GL11.GL_LINE_SMOOTH)
            GL11.glDisable(GL11.GL_TEXTURE_2D)
            GL11.glDisable(GL11.GL_DEPTH_TEST)
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)

            GL11.glLineWidth(circleThicknessValue.get())
            GL11.glColor4f(
                circleRedValue.get().toFloat() / 255.0F,
                circleGreenValue.get().toFloat() / 255.0F,
                circleBlueValue.get().toFloat() / 255.0F,
                circleAlphaValue.get().toFloat() / 255.0F
            )
            GL11.glRotatef(90F, 1F, 0F, 0F)
            GL11.glBegin(GL11.GL_LINE_STRIP)

            for (i in 0..360 step 5) {
                GL11.glVertex2f(
                    cos(i * PI / 180.0).toFloat() * rangeValue.get(),
                    (sin(i * PI / 180.0).toFloat() * rangeValue.get())
                )
            }

            GL11.glEnd()

            GL11.glDisable(GL11.GL_BLEND)
            GL11.glEnable(GL11.GL_TEXTURE_2D)
            GL11.glEnable(GL11.GL_DEPTH_TEST)
            GL11.glDisable(GL11.GL_LINE_SMOOTH)

            GL11.glPopMatrix()
        }

        if (cancelRun) {
            target = null
            hitable = false
            stopBlocking { 
                mc.netHandler.addToSendQueue(C07PacketPlayerDigging(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN))
            }
            discoveredTargets.clear()
            inRangeDiscoveredTargets.clear()
        }
        if (target != null && attackTimer.hasTimePassed(attackDelay) && target!!.hurtTime <= hurtTimeValue.get()) {
            clicks++
            attackTimer.reset()
            attackDelay = getAttackDelay(minCpsValue.get(), maxCpsValue.get())
        }

        if (target != null && attackTimer.hasTimePassed((attackDelay.toDouble() * 0.9).toLong()) && 
            (!autoBlockModeValue.equals("None") && canBlock) && autoBlockModeValue.equals("KeyBlock")) {
             mc.gameSettings.keyBindUseItem.pressed = false
        }

        if (target != null && delayBlockTimer.hasTimePassed(30) && (!autoBlockModeValue.equals("None") && canBlock)) {
            if (autoBlockModeValue.equals("KeyBlock")) {
                mc.gameSettings.keyBindUseItem.pressed = true
            }
            if (autoBlockModeValue.equals("Delayed")) {
                val target = this.target ?: discoveredTargets.getOrNull(0) ?: return
                startBlocking(target, interactValue.get() && (mc.thePlayer.getDistanceToEntityBox(target) < maxRange))
            }
        }

        // Render marks
        discoveredTargets.forEach {
            when (markValue.get().lowercase()) {
                "liquid" -> {
                    RenderUtils.drawPlatform(
                        it,
                        if (it.hurtTime <= 0) Color(37, 126, 255, 170) else Color(255, 0, 0, 170)
                    )
                }
                "block", "otherblock" -> {
                    val bb = it.entityBoundingBox
                    it.entityBoundingBox = it.entityBoundingBox.expand(blockMarkExpandValue.get().toDouble(),
                                                            blockMarkExpandValue.get().toDouble(),
                                                            blockMarkExpandValue.get().toDouble())
                    RenderUtils.drawEntityBox(
                        it,
                        if (it.hurtTime <= 0) if (it == target) Color(25, 230, 0, 170) else Color(10, 250, 10, 170) else Color(255, 0, 0, 170),
                        markValue.equals("Block"),
                        true,
                        4f
                    )
                    it.entityBoundingBox = bb
                }
                "fdp" -> {
                    val drawTime = (System.currentTimeMillis() % 1500).toInt()
                    val drawMode = drawTime > 750
                    var drawPercent = drawTime / 750.0
                    if (!drawMode) {
                        drawPercent = 1 - drawPercent
                    } else {
                        drawPercent -= 1
                    }
                    drawPercent = EaseUtils.easeInOutQuad(drawPercent)
                    mc.entityRenderer.disableLightmap()
                    GL11.glPushMatrix()
                    GL11.glDisable(GL11.GL_TEXTURE_2D)
                    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
                    GL11.glEnable(GL11.GL_LINE_SMOOTH)
                    GL11.glEnable(GL11.GL_BLEND)
                    GL11.glDisable(GL11.GL_DEPTH_TEST)

                    val bb = it.hitBox
                    val radius = ((bb.maxX - bb.minX) + (bb.maxZ - bb.minZ)) * 0.5f
                    val height = bb.maxY - bb.minY
                    val x = it.lastTickPosX + (it.posX - it.lastTickPosX) * event.partialTicks - mc.renderManager.viewerPosX
                    val y = (it.lastTickPosY + (it.posY - it.lastTickPosY) * event.partialTicks - mc.renderManager.viewerPosY) + height * drawPercent
                    val z = it.lastTickPosZ + (it.posZ - it.lastTickPosZ) * event.partialTicks - mc.renderManager.viewerPosZ
                    mc.entityRenderer.disableLightmap()
                    GL11.glLineWidth((radius * 8f).toFloat())
                    GL11.glBegin(GL11.GL_LINE_STRIP)
                    for (i in 0..360 step 10) {
                        RenderUtils.glColor(Color.getHSBColor(i / 180f, 0.7f, 1.0f))
                        GL11.glVertex3d(x - sin(i * PI / 180F) * radius, y, z + cos(i * PI / 180F) * radius)
                    }
                    GL11.glEnd()

                    GL11.glEnable(GL11.GL_DEPTH_TEST)
                    GL11.glDisable(GL11.GL_LINE_SMOOTH)
                    GL11.glDisable(GL11.GL_BLEND)
                    GL11.glEnable(GL11.GL_TEXTURE_2D)
                    GL11.glPopMatrix()
                }
                "jello" -> {
                    val drawTime = (System.currentTimeMillis() % 2000).toInt()
                    val drawMode = drawTime > 1000
                    var drawPercent = drawTime / 1000.0
                    if (!drawMode) {
                        drawPercent = 1 - drawPercent
                    } else {
                        drawPercent -= 1
                    }
                    drawPercent = EaseUtils.easeInOutQuad(drawPercent)
                    val points = mutableListOf<Vec3>()
                    val bb = it.hitBox
                    val radius = bb.maxX - bb.minX
                    val height = bb.maxY - bb.minY
                    val posX = it.lastTickPosX + (it.posX - it.lastTickPosX) * mc.timer.renderPartialTicks
                    var posY = it.lastTickPosY + (it.posY - it.lastTickPosY) * mc.timer.renderPartialTicks
                    if (drawMode) {
                        posY -= 0.5
                    } else {
                        posY += 0.5
                    }
                    val posZ = it.lastTickPosZ + (it.posZ - it.lastTickPosZ) * mc.timer.renderPartialTicks
                    for (i in 0..360 step 7) {
                        points.add(Vec3(posX - sin(i * PI / 180F) * radius, posY + height * drawPercent, posZ + cos(i * PI / 180F) * radius))
                    }
                    points.add(points[0])
                    mc.entityRenderer.disableLightmap()
                    GL11.glPushMatrix()
                    GL11.glDisable(GL11.GL_TEXTURE_2D)
                    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
                    GL11.glEnable(GL11.GL_LINE_SMOOTH)
                    GL11.glEnable(GL11.GL_BLEND)
                    GL11.glDisable(GL11.GL_DEPTH_TEST)
                    GL11.glBegin(GL11.GL_LINE_STRIP)
                    val baseMove = (if (drawPercent > 0.5) 1 - drawPercent else drawPercent) * 2
                    val min = (height / 60) * 20 * (1 - baseMove) * (if (drawMode) -1 else 1)
                    for (i in 0..20) {
                        var moveFace = (height / 60F) * i * baseMove
                        if (drawMode) {
                            moveFace = -moveFace
                        }
                        val firstPoint = points[0]
                        GL11.glVertex3d(
                            firstPoint.xCoord - mc.renderManager.viewerPosX, firstPoint.yCoord - moveFace - min - mc.renderManager.viewerPosY,
                            firstPoint.zCoord - mc.renderManager.viewerPosZ
                        )
                        GL11.glColor4f(1F, 1F, 1F, 0.7F * (i / 20F))
                        for (vec3 in points) {
                            GL11.glVertex3d(
                                vec3.xCoord - mc.renderManager.viewerPosX, vec3.yCoord - moveFace - min - mc.renderManager.viewerPosY,
                                vec3.zCoord - mc.renderManager.viewerPosZ
                            )
                        }
                        GL11.glColor4f(0F, 0F, 0F, 0F)
                    }
                    GL11.glEnd()
                    GL11.glEnable(GL11.GL_DEPTH_TEST)
                    GL11.glDisable(GL11.GL_LINE_SMOOTH)
                    GL11.glDisable(GL11.GL_BLEND)
                    GL11.glEnable(GL11.GL_TEXTURE_2D)
                    GL11.glPopMatrix()
                }
                "lies" -> {
                    val everyTime = 3000
                    val drawTime = (System.currentTimeMillis() % everyTime).toInt()
                    val drawMode = drawTime > (everyTime / 2)
                    var drawPercent = drawTime / (everyTime / 2.0)
                    if (!drawMode) {
                        drawPercent = 1 - drawPercent
                    } else {
                        drawPercent -= 1
                    }
                    drawPercent = EaseUtils.easeInOutQuad(drawPercent)
                    mc.entityRenderer.disableLightmap()
                    GL11.glPushMatrix()
                    GL11.glDisable(GL11.GL_TEXTURE_2D)
                    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
                    GL11.glEnable(GL11.GL_LINE_SMOOTH)
                    GL11.glEnable(GL11.GL_BLEND)
                    GL11.glDisable(GL11.GL_DEPTH_TEST)
                    GL11.glDisable(GL11.GL_CULL_FACE)
                    GL11.glShadeModel(7425)
                    mc.entityRenderer.disableLightmap()

                    val bb = it.hitBox
                    val radius = ((bb.maxX - bb.minX) + (bb.maxZ - bb.minZ)) * 0.5f
                    val height = bb.maxY - bb.minY
                    val x = it.lastTickPosX + (it.posX - it.lastTickPosX) * event.partialTicks - mc.renderManager.viewerPosX
                    val y = (it.lastTickPosY + (it.posY - it.lastTickPosY) * event.partialTicks - mc.renderManager.viewerPosY) + height * drawPercent
                    val z = it.lastTickPosZ + (it.posZ - it.lastTickPosZ) * event.partialTicks - mc.renderManager.viewerPosZ
                    val eased = (height / 3) * (if (drawPercent > 0.5) 1 - drawPercent else drawPercent) * (if (drawMode) -1 else 1)
                    for (i in 5..360 step 5) {
                        val color = Color.getHSBColor(i / 180f, 0.7f, 1.0f)
                        val x1 = x - sin(i * PI / 180F) * radius
                        val z1 = z + cos(i * PI / 180F) * radius
                        val x2 = x - sin((i - 5) * PI / 180F) * radius
                        val z2 = z + cos((i - 5) * PI / 180F) * radius
                        GL11.glBegin(GL11.GL_QUADS)
                        RenderUtils.glColor(color, 0f)
                        GL11.glVertex3d(x1, y + eased, z1)
                        GL11.glVertex3d(x2, y + eased, z2)
                        RenderUtils.glColor(color, 150f)
                        GL11.glVertex3d(x2, y, z2)
                        GL11.glVertex3d(x1, y, z1)
                        GL11.glEnd()
                    }

                    GL11.glEnable(GL11.GL_CULL_FACE)
                    GL11.glShadeModel(7424)
                    GL11.glColor4f(1f, 1f, 1f, 1f)
                    GL11.glEnable(GL11.GL_DEPTH_TEST)
                    GL11.glDisable(GL11.GL_LINE_SMOOTH)
                    GL11.glDisable(GL11.GL_BLEND)
                    GL11.glEnable(GL11.GL_TEXTURE_2D)
                    GL11.glPopMatrix()
                }
                "sims" -> {
                    val radius = 0.15f
                    val side = 4
                    GL11.glPushMatrix()
                    GL11.glTranslated(
                        it.lastTickPosX + (it.posX - it.lastTickPosX) * event.partialTicks - mc.renderManager.viewerPosX,
                        (it.lastTickPosY + (it.posY - it.lastTickPosY) * event.partialTicks - mc.renderManager.viewerPosY) + it.height * 1.1,
                        it.lastTickPosZ + (it.posZ - it.lastTickPosZ) * event.partialTicks - mc.renderManager.viewerPosZ
                    )
                    GL11.glRotatef(-it.width, 0.0f, 1.0f, 0.0f)
                    GL11.glRotatef((mc.thePlayer.ticksExisted + mc.timer.renderPartialTicks) * 5, 0f, 1f, 0f)
                    RenderUtils.glColor(if (it.hurtTime <= 0) Color(80, 255, 80) else Color(255, 0, 0))
                    RenderUtils.enableSmoothLine(1.5F)
                    val c = Cylinder()
                    GL11.glRotatef(-90.0f, 1.0f, 0.0f, 0.0f)
                    c.draw(0F, radius, 0.3f, side, 1)
                    c.drawStyle = 100012
                    GL11.glTranslated(0.0, 0.0, 0.3)
                    c.draw(radius, 0f, 0.3f, side, 1)
                    GL11.glRotatef(90.0f, 0.0f, 0.0f, 1.0f)
                    GL11.glTranslated(0.0, 0.0, -0.3)
                    c.draw(0F, radius, 0.3f, side, 1)
                    GL11.glTranslated(0.0, 0.0, 0.3)
                    c.draw(radius, 0F, 0.3f, side, 1)
                    RenderUtils.disableSmoothLine()
                    GL11.glPopMatrix()
                }
            }
        }
    }

    private fun getAttackDelay(minCps: Int, maxCps: Int): Long {
        return TimeUtils.randomClickDelay(minCps.coerceAtMost(maxCps), minCps.coerceAtLeast(maxCps))
    }

    private val cancelRun: Boolean
        get() = mc.thePlayer.isSpectator || !isAlive(mc.thePlayer) ||
                (blinkCheck.get() && MinusBounce.moduleManager[Blink::class.java]?.state == true) ||
                MinusBounce.moduleManager[FreeCam::class.java]?.state == true ||
                (noScaffValue.get() && MinusBounce.moduleManager[Scaffold::class.java]?.state == true) ||
                (noFlyValue.get() && MinusBounce.moduleManager[Flight::class.java]?.state == true) ||
                (noEat.get() && mc.thePlayer.isUsingItem && (mc.thePlayer.heldItem?.item is ItemFood || mc.thePlayer.heldItem?.item is ItemBucketMilk)) ||
                (noBlocking.get() && mc.thePlayer.isUsingItem && mc.thePlayer.heldItem?.item is ItemBlock) ||
                (noInventoryAttackValue.equals("CancelRun") && (mc.currentScreen is GuiContainer || System.currentTimeMillis() - containerOpen < noInventoryDelayValue.get()))

    private fun isAlive(entity: EntityLivingBase) = entity.isEntityAlive && entity.health > 0

    private val canBlock: Boolean
        get() = mc.thePlayer.heldItem != null && mc.thePlayer.heldItem.item is ItemSword

    private val maxRange: Float
        get() = max(rangeValue.get(), throughWallsRangeValue.get())

    override val tag: String
        get() = when (displayMode.get().lowercase()) {
            "simple" -> targetModeValue.get() + ""
            "lesssimple" -> rangeValue.get().toString() + " " + targetModeValue.get().toString() + " " + autoBlockModeValue.get().toString()
            "complicated" -> "M:" + targetModeValue.get() + ", AB:" + autoBlockModeValue.get() + ", R:" + rangeValue.get() + ", CPS:" + minCpsValue.get() + " - " + maxCpsValue.get()
            else -> targetModeValue.get() + ""
        }
}