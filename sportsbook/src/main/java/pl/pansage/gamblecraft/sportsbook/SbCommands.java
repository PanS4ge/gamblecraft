package pl.pansage.gamblecraft.sportsbook;

import java.util.Map;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import pl.pansage.gamblecraft.sportsbook.feed.League;
import pl.pansage.gamblecraft.sportsbook.network.SbNetworking;

/**
 * /sportsbook status (where each league is read from), and for operators /sportsbook refresh and
 * /sportsbook switch &lt;source:espn|league:nba|odds:draftkings&gt; &lt;true|false&gt; — the same switches as the window.
 */
public final class SbCommands {
	private SbCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> dispatcher.register(Commands.literal("sportsbook")
				.then(Commands.literal("status").executes(context -> {
					FeedService feed = FeedService.get();
					if (feed == null) {
						return 0;
					}
					for (Map.Entry<League, FeedService.Status> entry : feed.status().entrySet()) {
						FeedService.Status s = entry.getValue();
						String from = s.source() == null ? "-" : s.source().displayName;
						context.getSource().sendSuccess(() -> Component.translatable(entry.getKey().translationKey()).append(": " + from + ", " + s.count()
								+ (s.ok() ? "" : " (" + s.message() + ")")).withStyle(s.ok() ? ChatFormatting.GREEN : ChatFormatting.RED), false);
					}
					return 1;
				}))
				.then(Commands.literal("refresh").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(context -> {
					FeedService feed = FeedService.get();
					if (feed != null) {
						feed.refresh();
					}
					context.getSource().sendSuccess(() -> Component.translatable("gamblecraft_sportsbook.command.refreshing"), true);
					return 1;
				}))
				.then(Commands.literal("switch").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.argument("key", StringArgumentType.string()).then(Commands.argument("on", BoolArgumentType.bool()).executes(context -> {
							String key = StringArgumentType.getString(context, "key");
							boolean on = BoolArgumentType.getBool(context, "on");
							if (!SbNetworking.set(key, on)) {
								context.getSource().sendFailure(Component.translatable("gamblecraft_sportsbook.command.unknown_switch", key));
								return 0;
							}
							FeedService feed = FeedService.get();
							if (feed != null) {
								feed.refresh();
							}
							context.getSource().sendSuccess(() -> Component.literal(key + " = " + on), true);
							return 1;
						}))))));
	}
}
