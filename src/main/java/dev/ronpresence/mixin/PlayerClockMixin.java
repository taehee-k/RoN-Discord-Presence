package dev.ronpresence.mixin;
import dev.ronpresence.MatchClock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo
@Mixin(targets = "com.solegendary.reignofnether.player.PlayerClientEvents", remap = false)
public abstract class PlayerClockMixin {
    @Inject(method = "syncRtsGameTime(Ljava/lang/Long;)V", at = @At("TAIL"), remap = false)
    private static void presenceSync(Long ticks, CallbackInfo ci) { MatchClock.sync(ticks); }
    @Inject(method = "resetRTS(Z)V", at = @At("TAIL"), remap = false)
    private static void presenceReset(boolean hardReset, CallbackInfo ci) { MatchClock.reset(); }
}
