package dev.zpp.mixin;

import dev.zpp.ZppMod;
import dev.zpp.ZombieVariants;
import net.minecraft.world.entity.monster.Zombie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Zombie.class)
public abstract class ZombieMixin {
    @Inject(method = "isSunSensitive", at = @At("HEAD"), cancellable = true)
    private void zpp$sunlight(CallbackInfoReturnable<Boolean> cir) {
        Zombie self = (Zombie) (Object) this;
        if (!self.level().isClientSide && ZppMod.config().enabled && !ZppMod.config().dayburn
                && ZombieVariants.supported(self)) cir.setReturnValue(false);
    }
    @ModifyVariable(method = "setBaby", at = @At("HEAD"), argsOnly = true)
    private boolean zpp$allowBabies(boolean baby) {
        Zombie self = (Zombie) (Object) this;
        return baby && (self.level().isClientSide || !ZppMod.config().enabled
                || ZppMod.config().babies || !ZombieVariants.supported(self));
    }
}
