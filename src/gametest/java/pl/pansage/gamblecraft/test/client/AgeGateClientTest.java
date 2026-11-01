package pl.pansage.gamblecraft.test.client;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.TitleScreen;
import pl.pansage.gamblecraft.client.config.ClientConfig;
import pl.pansage.gamblecraft.client.screen.AgeGateScreen;

/** The 18+ notice replaces the title screen on a fresh install and remembers acceptance. */
public class AgeGateClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		context.waitForScreen(AgeGateScreen.class);
		context.takeScreenshot("age_gate");

		context.clickScreenButton("gamblecraft.age_gate.accept");
		context.waitForScreen(TitleScreen.class);
		context.waitTicks(5);

		if (!(context.computeOnClient(client -> client.gui.screen()) instanceof TitleScreen)) {
			throw new AssertionError("Age gate came back after accepting");
		}
		if (!ClientConfig.get().ageAccepted) {
			throw new AssertionError("Acceptance was not saved");
		}
		context.takeScreenshot("title_after_accept");
	}
}
