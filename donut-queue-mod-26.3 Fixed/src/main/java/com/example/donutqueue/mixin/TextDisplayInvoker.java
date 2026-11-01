package com.example.donutqueue.mixin;

import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Display.TextDisplay.class)
public interface TextDisplayInvoker {
    @Invoker("setBackgroundColor")
    void donutqueue$setBackgroundColor(int color);

    @Invoker("setFlags")
    void donutqueue$setFlags(byte flags);
}
