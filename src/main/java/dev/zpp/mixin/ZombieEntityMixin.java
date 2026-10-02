package dev.zpp.mixin;

import dev.zpp.ZppMod;
import dev.zpp.ZombieVariants;
import net.minecraft.entity.mob.ZombieEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ZombieEntity.class)
public abstract class ZombieEntityMixin {
    @Inject(method = "burnsInDaylight", at = @At("HEAD"), cancellable = true)
    private void zpp$sunlight(CallbackInfoReturnable<Boolean> cir) {
        ZombieEntity self = (ZombieEntity) (Object) this;
        if (!self.getWorld().isClient && ZppMod.config().enabled && !ZppMod.config().dayburn
                && ZombieVariants.supported(self)) cir.setReturnValue(false);
    }
    @ModifyVariable(method = "setBaby", at = @At("HEAD"), argsOnly = true)
    private boolean zpp$allowBabies(boolean baby) {
        ZombieEntity self = (ZombieEntity) (Object) this;
        return baby && (self.getWorld().isClient || !ZppMod.config().enabled
                || ZppMod.config().babies || !ZombieVariants.supported(self));
    }
}
