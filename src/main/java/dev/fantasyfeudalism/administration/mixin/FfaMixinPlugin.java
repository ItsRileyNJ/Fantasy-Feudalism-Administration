package dev.fantasyfeudalism.administration.mixin;

import java.util.List;
import java.util.Set;
import net.neoforged.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.*;

/** Optional private layouts are patched only for inspected mod versions. */
public final class FfaMixinPlugin implements IMixinConfigPlugin {
  @Override
  public void onLoad(String mixinPackage) {}

  @Override
  public String getRefMapperConfig() {
    return null;
  }

  @Override
  public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
    var mods = FMLLoader.getLoadingModList();
    if (mixinClassName.endsWith("HatStandEquipmentMixin"))
      return mods != null
          && mods.getMods().stream()
              .anyMatch(
                  mod ->
                      mod.getModId().equals("supplementaries")
                          && mod.getVersion().toString().equals("1.21.1-4.1.4"));
    if (!mixinClassName.endsWith("ItemsLanternFuelMixin")
        && !mixinClassName.endsWith("ItemsLanternWeatherMixin")) return true;
    String id =
        targetClassName.startsWith("com.fantasyfeudalism.additions.")
            ? "fantasy_feudalism_additions"
            : "fantasy_feudalism_items";
    String version = id.equals("fantasy_feudalism_additions") ? "0.1.25" : "0.1.7";
    return mods != null
        && mods.getMods().stream()
            .anyMatch(
                mod -> mod.getModId().equals(id) && mod.getVersion().toString().equals(version));
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
