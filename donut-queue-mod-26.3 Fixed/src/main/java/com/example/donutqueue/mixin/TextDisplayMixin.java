package com.example.donutqueue.mixin;

import com.example.donutqueue.rank.OwnTag;
import com.example.donutqueue.rank.RankColors;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Servers (DonutSMP) draw the "[logo] Name / $ money" tag as a Text Display entity, not a vanilla nametag. */
@Mixin(Display.TextDisplay.class)
public abstract class TextDisplayMixin {
    @Inject(method = "getText", at = @At("RETURN"), cancellable = true)
    private void donutqueue$rankcolor(CallbackInfoReturnable<Component> cir) {
        Entity self = (Entity) (Object) this;
        if (!(self.level() instanceof ClientLevel)) return;   // never touch the integrated server's copy

        // our own F5 tag: always shown (Rank colours only decides whether it gets recoloured)
        if (OwnTag.isFake(self)) {
            cir.setReturnValue(RankColors.apply(OwnTag.text(), OwnTag.myName(), true));
            return;
        }

        Component orig = cir.getReturnValue();
        if (orig == null) return;
        boolean recolor = RankColors.enabled();
        // even with Rank colours off we still copy the look of another player's tag now and then,
        // so your own F5 tag matches the server's style
        if (!recolor && !OwnTag.wantsTemplate()) return;
        Player owner = RankColors.nearestPlayer(self);
        OwnTag.captureTemplate(self, owner);
        if (!recolor) return;
        cir.setReturnValue(RankColors.apply(orig, owner == null ? null : owner.getName().getString(), true));
    }
}
