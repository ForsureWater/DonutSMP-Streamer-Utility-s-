package com.example.donutqueue.mixin;

import com.example.donutqueue.rank.RankColors;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Recolors vanilla player nametags above heads. */
@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    private void donutqueue$rankcolor(CallbackInfoReturnable<Component> cir) {
        if ((Object) this instanceof Player p) {
            cir.setReturnValue(RankColors.apply(cir.getReturnValue(), p.getName().getString()));
        }
    }
}
