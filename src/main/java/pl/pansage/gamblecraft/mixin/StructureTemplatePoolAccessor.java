package pl.pansage.gamblecraft.mixin;

import java.util.List;

import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets {@link pl.pansage.gamblecraft.worldgen.VillageInjector} add pieces to vanilla village pools. */
@Mixin(StructureTemplatePool.class)
public interface StructureTemplatePoolAccessor {
	@Accessor("rawTemplates")
	List<Pair<StructurePoolElement, Integer>> gamblecraft$getRawTemplates();

	@Mutable
	@Accessor("rawTemplates")
	void gamblecraft$setRawTemplates(List<Pair<StructurePoolElement, Integer>> templates);

	@Accessor("templates")
	ObjectArrayList<StructurePoolElement> gamblecraft$getTemplates();
}
