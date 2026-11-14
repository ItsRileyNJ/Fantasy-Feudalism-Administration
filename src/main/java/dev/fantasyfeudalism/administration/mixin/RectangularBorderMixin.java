package dev.fantasyfeudalism.administration.mixin;

import dev.fantasyfeudalism.administration.*;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.shapes.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Leave native settings intact, including saves, while managed dimensions use exact rectangles. */
@Mixin(WorldBorder.class)
public abstract class RectangularBorderMixin implements RectangularBorderAccess {
  @Shadow private double centerX;
  @Shadow private double centerZ;
  @Unique private Model.Border ffa$bounds;
  @Unique private VoxelShape ffa$shape;

  @Override
  public void ffa$bounds(Model.Border bounds) {
    boolean removing = ffa$bounds != null && bounds == null;
    ffa$bounds = bounds;
    ffa$shape =
        bounds == null
            ? null
            : Shapes.joinUnoptimized(
                Shapes.INFINITY,
                Shapes.box(
                    bounds.minX,
                    Double.NEGATIVE_INFINITY,
                    bounds.minZ,
                    (double) bounds.maxX + 1,
                    Double.POSITIVE_INFINITY,
                    (double) bounds.maxZ + 1),
                BooleanOp.ONLY_FIRST);
    if (removing) ((WorldBorder) (Object) this).setCenter(centerX, centerZ);
  }

  @Inject(method = "getCenterX", at = @At("HEAD"), cancellable = true)
  private void ffa$centerX(CallbackInfoReturnable<Double> ci) {
    if (ffa$bounds != null) ci.setReturnValue(((double) ffa$bounds.minX + ffa$bounds.maxX + 1) / 2);
  }

  @Inject(method = "getCenterZ", at = @At("HEAD"), cancellable = true)
  private void ffa$centerZ(CallbackInfoReturnable<Double> ci) {
    if (ffa$bounds != null) ci.setReturnValue(((double) ffa$bounds.minZ + ffa$bounds.maxZ + 1) / 2);
  }

  @Inject(method = "createSettings", at = @At("HEAD"), cancellable = true)
  private void ffa$nativeSettings(CallbackInfoReturnable<WorldBorder.Settings> ci) {
    if (ffa$bounds == null) return;
    var self = (WorldBorder) (Object) this;
    var original = new WorldBorder();
    original.setCenter(centerX, centerZ);
    original.setDamagePerBlock(self.getDamagePerBlock());
    original.setDamageSafeZone(self.getDamageSafeZone());
    original.setWarningBlocks(self.getWarningBlocks());
    original.setWarningTime(self.getWarningTime());
    original.lerpSizeBetween(self.getSize(), self.getLerpTarget(), self.getLerpRemainingTime());
    ci.setReturnValue(original.createSettings());
  }

  @Inject(method = "getMinX", at = @At("HEAD"), cancellable = true)
  private void ffa$minX(CallbackInfoReturnable<Double> ci) {
    if (ffa$bounds != null) ci.setReturnValue((double) ffa$bounds.minX);
  }

  @Inject(method = "getMinZ", at = @At("HEAD"), cancellable = true)
  private void ffa$minZ(CallbackInfoReturnable<Double> ci) {
    if (ffa$bounds != null) ci.setReturnValue((double) ffa$bounds.minZ);
  }

  @Inject(method = "getMaxX", at = @At("HEAD"), cancellable = true)
  private void ffa$maxX(CallbackInfoReturnable<Double> ci) {
    if (ffa$bounds != null) ci.setReturnValue((double) ffa$bounds.maxX + 1);
  }

  @Inject(method = "getMaxZ", at = @At("HEAD"), cancellable = true)
  private void ffa$maxZ(CallbackInfoReturnable<Double> ci) {
    if (ffa$bounds != null) ci.setReturnValue((double) ffa$bounds.maxZ + 1);
  }

  @Inject(method = "getCollisionShape", at = @At("HEAD"), cancellable = true)
  private void ffa$shape(CallbackInfoReturnable<VoxelShape> ci) {
    if (ffa$shape != null) ci.setReturnValue(ffa$shape);
  }
}
