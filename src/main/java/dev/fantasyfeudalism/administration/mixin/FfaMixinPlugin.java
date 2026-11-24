package dev.fantasyfeudalism.administration.mixin;

import java.util.List;
import java.util.Set;
import net.neoforged.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.*;

/** Items' private fuel layout is inspected for 0.1.7; other versions remain untouched. */
public final class FfaMixinPlugin implements IMixinConfigPlugin {
  @Override
  public void onLoad(String mixinPackage) {}

  @Override
  public String getRefMapperConfig() {
    return null;
  }

  @Override
  public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
    if (!mixinClassName.endsWith("ItemsLanternFuelMixin")
        && !mixinClassName.endsWith("ItemsLanternWeatherMixin")) return true;
    var mods = FMLLoader.getLoadingModList();
    return mods != null
        && mods.getMods().stream()
            .anyMatch(
                mod ->
                    mod.getModId().equals("fantasy_feudalism_items")
                        && mod.getVersion().toString().equals("0.1.7"));
  }

  @Override
  public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

  @Override
  public List<String> getMixins() {
    return null;
  }

  @Override
  public void preApply(String target, ClassNode node, String name, IMixinInfo info) {}

  @Override
  public void postApply(String target, ClassNode node, String name, IMixinInfo info) {}
}
