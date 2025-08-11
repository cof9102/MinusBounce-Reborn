/*
 * MinusBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/MinusMC/MinusBounce
 */
package net.minusmc.minusbounce.injection.forge.mixins.client;

import com.google.common.collect.Lists;
import net.minecraft.client.resources.ResourcePackRepository;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.comparator.LastModifiedFileComparator;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;
import java.util.List;

@Mixin(ResourcePackRepository.class)
public class MixinResourcePackRepository {

    @Shadow
    @Final
    private File dirServerResourcepacks;

    @Shadow
    @Final
    private static Logger logger;

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Inject(method = "deleteOldServerResourcesPacks", at = @At("HEAD"))
    private void createDirectory(CallbackInfo ci) {
        if (!this.dirServerResourcepacks.exists())
            this.dirServerResourcepacks.mkdirs();
    }

    /**
     * @author Mojang
     * @reason Fix a bug
     */
    @Overwrite
    private void deleteOldServerResourcesPacks() {
        try {
            List<File> lvt_1_1_ = Lists.newArrayList(FileUtils.listFiles(this.dirServerResourcepacks, TrueFileFilter.TRUE, null));
            lvt_1_1_.sort(LastModifiedFileComparator.LASTMODIFIED_REVERSE);
            int lvt_2_1_ = 0;

            for (File lvt_4_1_ : lvt_1_1_) {
                if (lvt_2_1_++ >= 10) {
                    logger.info("Deleting old server resource pack {}", lvt_4_1_.getName());
                    FileUtils.deleteQuietly(lvt_4_1_);
                }
            }
        }catch(final Throwable e) {
            e.printStackTrace();
        }
    }
}