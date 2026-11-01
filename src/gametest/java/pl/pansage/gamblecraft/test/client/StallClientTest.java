package pl.pansage.gamblecraft.test.client;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import pl.pansage.gamblecraft.GambleCraft;
import pl.pansage.gamblecraft.client.screen.AgeGateScreen;

/** Places every village's dealer stall in front of the player for a look. */
public class StallClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		if (context.computeOnClient(client -> client.gui.screen() instanceof AgeGateScreen)) {
			context.clickScreenButton("gamblecraft.age_gate.accept");
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				var level = player.level();
				BlockPos base = player.blockPosition().relative(player.getDirection(), 7).below();
				String[] villages = {"plains", "desert", "taiga"};
				for (int i = 0; i < villages.length; i++) {
					BlockPos at = base.relative(player.getDirection().getClockWise(), (i - 1) * 7);
					server.getStructureTemplateManager().get(GambleCraft.id("village/dealer_stall_" + villages[i])).orElseThrow()
							.placeInWorld(level, at, at, new StructurePlaceSettings().setRotation(Rotation.CLOCKWISE_90), level.getRandom(), 2);
				}
				player.setXRot(15);
			});
			context.waitTicks(40);
			context.takeScreenshot("dealer_stalls");

			// the tailor's workshops, one row further
			world.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				var level = player.level();
				BlockPos base = player.blockPosition().relative(player.getDirection(), 16).below();
				String[] villages = {"plains", "desert", "taiga"};
				for (int i = 0; i < villages.length; i++) {
					BlockPos at = base.relative(player.getDirection().getClockWise(), (i - 1) * 9);
					server.getStructureTemplateManager().get(GambleCraft.id("village/tailor_" + villages[i])).orElseThrow()
							.placeInWorld(level, at, at, new StructurePlaceSettings().setRotation(Rotation.CLOCKWISE_90), level.getRandom(), 2);
				}
				player.connection.teleport(player.getX(), player.getY() + 6, player.getZ(), player.getYRot(), 25);
			});
			context.waitTicks(40);
			context.takeScreenshot("tailor_shops");
		}
	}
}
