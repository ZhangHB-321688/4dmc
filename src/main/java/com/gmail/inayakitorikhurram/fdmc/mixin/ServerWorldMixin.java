package com.gmail.inayakitorikhurram.fdmc.mixin;

import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(value = ServerWorld.class, priority = 900)
public class ServerWorldMixin {

    @ModifyConstant(method = "setSpawnPos", constant = @Constant(intValue = 11), require = 0)
    private int injectedStartRegionRange(int value) {
        return 5;
    }

}
