package com.example.donutqueue.mixin;

import com.mojang.math.Transformation;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Lets OwnTag call Display's private setters directly (no name-based reflection, which breaks on obfuscated runtimes). */
@Mixin(Display.class)
public interface DisplayInvoker {
    @Invoker("setTransformation")
    void donutqueue$setTransformation(Transformation transformation);

    @Invoker("setBillboardConstraints")
    void donutqueue$setBillboardConstraints(Display.BillboardConstraints constraints);
}
