package net.minusmc.minusbounce.features.module.modules.world.scaffold.tower

import net.minusmc.minusbounce.features.module.modules.world.scaffold.TowerScaffold

class JumpTower: TowerScaffold("Jump") {
	override fun onPostMotion() {
		if (mc.thePlayer.onGround) {
			fakeJump()
			mc.thePlayer.motionY = 0.42
		}
	}
}
