package autohost.handler;

import autohost.AutoHost;
import autohost.IRCBot;
import autohost.Lobby;
import autohost.irc.IRCClient;
import autohost.lobby.BeatmapParameter;
import autohost.lobby.BeatmapSetting;
import autohost.util.*;
import lt.ekgame.beatmap_analyzer.difficulty.Difficulty;
import lt.ekgame.beatmap_analyzer.parser.BeatmapException;
import lt.ekgame.beatmap_analyzer.parser.BeatmapParser;
import lt.ekgame.beatmap_analyzer.performance.Performance;
import lt.ekgame.beatmap_analyzer.performance.scores.Score;
import lt.ekgame.beatmap_analyzer.utils.Mod;
import lt.ekgame.beatmap_analyzer.utils.Mods;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.HttpClients;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.DecimalFormat;
import java.util.Map;
import java.util.regex.Matcher;

import static autohost.util.MathUtils.round;
import static autohost.util.TimeUtils.MINUTE;

public class ChannelMessageHandler {
	private final IRCBot    m_bot;
	private final IRCClient m_client;

	public ChannelMessageHandler(IRCBot bot) {
		m_bot = bot;
		m_client = m_bot.getClient();
	}

	public void handle(String channel, String sender, String message) {
		if (sender.equalsIgnoreCase(m_client.getUser())) {
			return;
		}

		// Is this a multi lobby channel?
		if (!RegexUtils.matches("#mp_(\\d+)", channel)) {
			// If not a lobby channel, then why the fuck we care?
			return;
		}

		Map<String, Lobby> lobbies = m_bot.getLobbies();
		if (lobbies.isEmpty()) {
			return;
		}

		Lobby loadedLobby = null;
		if (lobbies.containsKey(channel)) {
			Lobby lobby = lobbies.get(channel);
			if (lobby.channel.equalsIgnoreCase(channel)) {
				// Is it an autohosted (by us) channel?
				loadedLobby = lobby;
			}
		}

		if (loadedLobby != null) {
			if (sender.equalsIgnoreCase(m_client.getUser())) {
				return;
			}

			if (sender.equalsIgnoreCase("BanchoBot")) {
				handleBancho(loadedLobby, message);
			} else {
				handleCommands(loadedLobby, sender, message);
			}
		} else {
			System.out.println("Warning: Channel not loaded? C: " + channel);
		}
	}

	private void handleBancho(Lobby lobby, String message) {
		// Room name and ID, important (?)
		// Room name: test, History: https://osu.ppy.sh/mp/31026456
		// Room name: AutoHost 5-6* || !info || By HyPeX,
		//      History: https://osu.ppy.sh/mp/32487590
		Matcher rNM = RegexUtils.matcher(
				"Room name: (.+), History: https://osu.ppy.sh/mp/(.+)",
				message);

		if (rNM.matches()) {
			String name = rNM.group(1);
			System.out.println("New room name! " + name);
			lobby.name = name;
			lobby.mpID = Integer.valueOf(rNM.group(2));
		}

		// Win condition... meh
		Matcher rTM = RegexUtils.matcher(
				"Team Mode: (.+), Win condition: (.+)",
				message);
		if (rTM.matches()) {
			System.out.println("Team & Condition set");
			lobby.teamgamemode = rTM.group(1);
			lobby.winCondition = rTM.group(2);
		}

		// Beatmap change... i guess thats k?
		Matcher bM = RegexUtils.matcher(
				"Beatmap: https://osu.ppy.sh/b/(\\d+) (.+)- (.+)",
				message);
		if (bM.matches()) {
			lobby.currentBeatmap = Integer.valueOf(bM.group(1));
			lobby.currentBeatmapAuthor = bM.group(2);
			lobby.currentBeatmapName = bM.group(3);
		}

		Matcher iMM = RegexUtils.matcher(
				"Invalid map ID provided",
				message);
		if (iMM.matches()) {
			m_bot.nextbeatmap(lobby);
		}

		// Is this one even worth adding?
		Matcher pM = RegexUtils.matcher(
				"Players: (\\d+)",
				message);
		if (pM.matches()) {
			if (lobby.slots.size() != Integer.valueOf(pM.group(1))) {
				// m_client.sendMessage(lobby.channel, "Warning: Player count
				// mismatch! Did bot reconnect?");
			}
		}

		Matcher passmatch = RegexUtils.matcher(
				"(.+) the match password",
				message);
		if (passmatch.matches()) {
			if (passmatch.group(1).equals("Enabled")) {
				if (lobby.Password.equalsIgnoreCase("")) {
					m_client.sendMessage(lobby.channel, "!mp password");
				}
			} else {
				if (!lobby.Password.equalsIgnoreCase("")) {
					m_client.sendMessage(lobby.channel, "!mp password");
				}
			}
		}

		// Slot info on players... generally should be empty on start.. but
		// who knows.
		// :Slot 1 Ready https://osu.ppy.sh/u/711080 HyPeX
		// :Slot 2 Not Ready https://osu.ppy.sh/u/6435456 Saerph
		// Slot 1 Not Ready https://osu.ppy.sh/u/711080 HyPeX [Hidden]
		// Slot 1 Not Ready https://osu.ppy.sh/u/711080 HyPeX [HardRock]
		// :Slot 1 Not Ready https://osu.ppy.sh/u/711080 HyPeX
		// TODO: Document these groups.
		//       Have to manually parse the regex to figure out wtf group 5 is.
		Matcher sM = RegexUtils.matcher(
				"^Slot (\\d+)(\\s+){1,2}(.+) https://osu.ppy.sh/u/(\\d+) (.+?)(?=( *$| +(\\[)([^\\[\\]]+)?(\\])?)$)",
				message);
		if (sM.matches()) {
			int slotN = Integer.valueOf(sM.group(1));
			if (lobby.slots.containsKey(slotN)) {
				Slot slotM = lobby.slots.get(slotN);
				slotM.status = sM.group(3);
				slotM.id = slotN;
				slotM.playerid = Integer.valueOf(sM.group(4));
				slotM.name = sM.group(5);
				lobby.slots.replace(slotN, slotM);
			} else {
				Slot slotM = new Slot();
				slotM.status = sM.group(3);
				slotM.id = slotN;
				slotM.playerid = Integer.valueOf(sM.group(4));
				slotM.name = sM.group(5);
				lobby.slots.put(slotN, slotM);
			}
			return;
		}

		// :BanchoBot!cho@ppy.sh PRIVMSG #mp_29691447 :HyPeX joined in slot
		// 1.
		Matcher joinMatch = RegexUtils.matcher(
				"(.+) joined in slot (\\d+).",
				message);
		if (joinMatch.matches()) {
			String playerName = joinMatch.group(1);
			int jslot = Integer.valueOf(joinMatch.group(2));
			// int playerId = getId(playerName);
			int playerId = m_bot.getId(playerName);
			String status = "Not Ready";
			Slot newSlot = new Slot(jslot, playerName, playerId, status);
			if (lobby.slots.containsKey(jslot)) {
				lobby.slots.replace(jslot, newSlot);
			} else {
				lobby.slots.put(jslot, newSlot);
			}
			lobby.afk.put(playerName, 0);

			int id = m_bot.getId(playerName);
			if (lobby.isOP(id)) {
				m_client.sendMessage(lobby.channel, "Operator " + playerName + " has joined. Welcome!");
				m_client.sendMessage(lobby.channel, "!mp addref #" + playerName);
			}
		}

		Matcher moveMatcher = RegexUtils.matcher(
				"(.+) moved to slot (\\d+)",
				message);
		if (moveMatcher.matches()) {
			int playerId = 0;
			playerId = m_bot.getId(moveMatcher.group(1));
			Slot player = new Slot(Integer.valueOf(moveMatcher.group(2)), moveMatcher.group(1), playerId,
					"Not Ready");
			for (int i = 1; i < 17; i++) {
				if (lobby.slots.containsKey(i)) {
					if (lobby.slots.get(i).name.equalsIgnoreCase(moveMatcher.group(1))) {
						player = lobby.slots.get(i);
						lobby.slots.remove(i);
					}
				}
			}
			lobby.slots.put(Integer.valueOf(moveMatcher.group(2)), player);
		}

		// :BanchoBot!cho@ppy.sh PRIVMSG #mp_32757177 :TrackpadEasy left the
		// game.
		Matcher leftMatcher = RegexUtils.matcher(
				"(.+) left the game.",
				message);
		if (leftMatcher.matches()) {
			for (int i = 1; i < 17; i++) {
				if (lobby.slots.containsKey(i)) {
					if (lobby.slots.get(i).name.equalsIgnoreCase(leftMatcher.group(1))) {
						lobby.slots.remove(i);
					}
				}
			}
			if (lobby.slots.size() == 0) {
				if (!lobby.OPLobby) {
					m_client.sendMessage(lobby.channel, "!mp close");
					m_bot.removeLobby(lobby);
				}
			}
		}

		if (message.equalsIgnoreCase("All players are ready")) {
			m_client.sendMessage(lobby.channel, "All players are ready! starting...");
			m_client.sendMessage(lobby.channel, "!mp start 5");
			lobby.timer.stopTimer();
		}

		if (message.equalsIgnoreCase("The match has started!")) {
			lobby.scores.clear();
			lobby.Playing = true;
		}

		if (message.equalsIgnoreCase("The match has finished!")) {
			// Chech for player scores -- TODO
			// TODO: At the end of the match, the score isn't always reported immediately.
			//       This causes people to be kicked super often. Find a way to fix this.
			for (Slot player : lobby.slots.values()) {
				if (!lobby.scores.containsKey(player.name)) {
					m_bot.addAFK(lobby, player.name);
				}
			}
			m_bot.nextbeatmap(lobby);
			lobby.timer.continueTimer();
			/*
			 * Integer orderedScores[] = new Integer[(lobby.LobbySize - 1)];
			 * orderedScores = orderScores(lobby); for (int i = 0; i < 3;
			 * i++) { String player = lobby.scores.get(orderedScores[i]);
			 * m_client.sendMessage(lobby.channel, player + " finished " + (i + 1) +
			 * "!"); }
			 */
		}

		Matcher scoreMatcher = RegexUtils.matcher(
				"(.+) has finished playing \\(Score: (\\d+), (\\D+)\\)",
				message);
		if (scoreMatcher.matches()) {
			if (Integer.valueOf(scoreMatcher.group(2)) == 0) {
				m_bot.addAFK(lobby, scoreMatcher.group(1));
			} else {
				m_bot.removeAFK(lobby, scoreMatcher.group(1));
				lobby.scores.put(scoreMatcher.group(1), Integer.valueOf(scoreMatcher.group(2)));
			}
		}

		// Beatmap changed to: Rameses B - Neon Rainbow (ft. Anna Yvette)
		// [Easy] (https://osu.ppy.sh/b/961779)
		Matcher beatmapMatcher = RegexUtils.matcher(
				"Beatmap changed to: (.+) [(.+)] (https://osu.ppy.sh/b/(.+))",
				message);
		if (beatmapMatcher.matches()) {

		}
		return;
	}

	private void handleCommands(Lobby lobby, String sender, String message) {
		message = message.trim().toLowerCase();
		// --TODO
		// Player is playing, not AFK.
		m_bot.removeAFK(lobby, sender);

		if (!message.startsWith("!")) {
			return;
		}

		message = message.substring(1);
		String[] args = message.split(" ");
		switch (args[0]) {
		case "add":
			handleAdd(lobby, sender, message);
			break;
		case "adddt":
			handleAddDT(lobby, sender, message);
			break;
		case "ready":
		case "r":
			handleReady(lobby, sender);
			break;
		case "skip":
		case "s":
			handleSkip(lobby, sender);
			break;
		case "info":
			handleInfo(lobby);
			break;
		case "commands":
			handleCommands(lobby);
			break;
		case "playlist":
		case "queue":
			handlePlaylist(lobby);
			break;
		case "select":
			handleSelect(lobby, sender, message);
			break;
		case "maxdiff":
			handleMaxDifficulty(lobby, sender, message);
			break;
		case "freemods":
			handleFreeMods(lobby, sender);
			break;
		case "mode":
			handleMode(lobby, sender, message);
			break;
		case "graveyard":
			handleGraveyard(lobby, sender);
			break;
		case "lock":
			handleLock(lobby, sender);
			break;
		case "ver":
			handleVersion(lobby);
			break;
		case "wait":
			handleWait(lobby);
			break;
		case "lobby":
			handleLobby(lobby, sender, message);
			break;
		case "start":
			handleStart(lobby, sender);
			break;
		case "last":
		case "l":
			handleLast(lobby, sender);
			break;
		case "kick":
			handleKick(lobby, sender, message);
			break;
		case "addop":
			handleAddOP(lobby, sender, message);
			break;
		case "forceskip":
			handleForceSkip(lobby, sender);
			break;
		case "forcestart":
			handleForceStart(lobby, sender);
			break;
		case "password":
			handlePassword(lobby, sender, message);
			break;
		case "hostme":
			handleHostMe(lobby, sender);
			break;
		case "clearhost":
			handleClearHost(lobby, sender);
			break;
		case "random":
			handleRandom(lobby, sender);
			break;
		case "rename":
			handleRename(lobby, sender, message);
			break;
		case "say":
			handleSay(lobby, sender, message);
			break;
		case "closeroom":
			handleCloseRoom(lobby, sender);
			break;
		case "songsearch":
			handleSongSearch(lobby, sender, message);
			break;
		case "set":
			handleSet(lobby, sender, message);
			break;
		default:
			// Unknown command.
		}
	}

	private void handleAdd(Lobby lobby, String sender, String message) {
		if (lobby.lockAdding) {
			m_client.sendMessage(lobby.channel,
					sender + " sorry, beatmap requesting is currently disabled.");
			return;
		}
		for (Beatmap beatmap : lobby.beatmapQueue) {
			if (m_bot.hasAlreadyRequested(lobby, sender)) {
				m_client.sendMessage(lobby.channel,
						sender + " you have already requested a beatmap!");
				return;
			}
		}
		int id = 0;
		Matcher mapR = RegexUtils.matcher(
				"add (\\d+)",
				message);
		Matcher mapU = RegexUtils.matcher(
				"add (.+)osu.ppy.sh/b/(\\d+)(.*)",
				message);
		Matcher mapUS = RegexUtils.matcher(
				"add (.+)osu.ppy.sh/s/(\\d+)(.*)",
				message);
		if (mapR.matches()) {
			id = Integer.valueOf(mapR.group(1));
		} else if (mapU.matches()) {
			id = Integer.valueOf(mapU.group(2));
		} else if (mapUS.matches()) {
			m_client.sendMessage(lobby.channel, sender
					+ " You introduced a beatmap set link, processing beatmaps... (for a direct difficulty add use the /b/ link)");
			int bid = Integer.valueOf(mapUS.group(2));
			m_bot.askForConfirmation(sender, bid, lobby);
			return;
		}
		if (id == 0) {
			m_client.sendMessage(lobby.channel,
					sender + " Incorrect Arguments for !add. Please use the beatmap URL. !add [url]");
			return;
		}
		try {
			m_bot.getBeatmap(id, lobby, (obj) -> {
				if (obj == null) {
					m_client.sendMessage(lobby.channel, sender + ": Beatmap not found.");
					return;
				}

				String mode = JSONUtils.silentGetString(obj, "mode");
				if (!mode.equals(lobby.type)) {
					m_client.sendMessage(lobby.channel,
							sender + " That beatmap does not fit the lobby's current gamemode!");
					return;
				}
				Beatmap beatmap = JSONUtils.silentGetBeatmapFromApi(obj);
				beatmap.RequestedBy = m_bot.getId(sender);
				if (!lobby.isBeatmapValid(beatmap)) {
					// TODO: Output a useful error?
					return;
				}
				if (!lobby.statusTypes.get(beatmap.graveyard)) {
					m_client.sendMessage(lobby.channel, sender
							+ " That beatmap is not within ranking criteria for this lobby! (Ranked/loved/etc)");
					return;
				}

				if (lobby.onlyGenre) {
					if (!beatmap.genre.equalsIgnoreCase(lobby.genre)) {
						m_client.sendMessage(lobby.channel, sender + " This lobby is set to only play "
								+ lobby.genres[Integer.valueOf(lobby.genre)] + " genre!");
						return;
					}
				}
				m_bot.addBeatmap(lobby, beatmap);
			});
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void handleAddDT(Lobby lobby, String sender, String message) {
		if (lobby.lockAdding) {
			m_client.sendMessage(lobby.channel,
					sender + " sorry, beatmap requesting is currently disabled.");
			return;
		}
		if (!lobby.isOP(m_bot.getId(sender))) {
			if (m_bot.hasAlreadyRequested(lobby, sender)) {
				m_client.sendMessage(lobby.channel, sender + " you have already requested a beatmap!");
				return;
			}
		}
		Matcher mapU = RegexUtils.matcher(
				"adddt (.+)osu.ppy.sh/b/(\\d+)(.*)",
				message);
		int id = 0;
		if (mapU.matches()) {
			id = Integer.valueOf(mapU.group(2));
		}
		if (id == 0) {
			m_client.sendMessage(lobby.channel,
					sender + " Incorrect Arguments for !adddt. Please use the beatmap URL. !adddt [url]");
			return;
		}
		try {
			m_bot.getBeatmap(id, lobby, (obj) -> {
				if (obj == null) {
					m_client.sendMessage(lobby.channel, sender + ": Beatmap not found.");
					return;
				}

				String mode = JSONUtils.silentGetString(obj, "mode");
				if (!mode.equals(lobby.type)) {
					m_client.sendMessage(lobby.channel,
							sender + " That beatmap does not fit the lobby's current gamemode!");
					return;
				}
				Beatmap beatmap = JSONUtils.silentGetBeatmapFromApi(obj);
				beatmap.RequestedBy = m_bot.getId(sender);
				beatmap.DT = true;
				try {
					RequestConfig defaultRequestConfig = RequestConfig.custom().setSocketTimeout(10000)
							.setConnectTimeout(10000).setConnectionRequestTimeout(10000).build();

					HttpClient httpClient = HttpClients.custom().setDefaultRequestConfig(defaultRequestConfig)
							.build();
					URI uri = new URIBuilder().setScheme("http").setHost("osu.ppy.sh")
							.setPath("/osu/" + beatmap.beatmap_id).build();
					HttpGet request = new HttpGet(uri);
					HttpResponse response = httpClient.execute(request);
					InputStream content = response.getEntity().getContent();
					BeatmapParser parser = new BeatmapParser();
					lt.ekgame.beatmap_analyzer.beatmap.Beatmap cbp = parser.parse(content);

					Difficulty diff = cbp.getDifficulty(new Mods(Mod.DOUBLE_TIME));
					Score ss = Score.of(cbp).build();
					Performance perf = diff.getPerformance(ss);
					beatmap.difficulty = diff.getStars();
					beatmap.difficulty_ar = 4.66666 + 0.6666 * beatmap.difficulty_ar;
					beatmap.difficulty_od = cbp.getDifficultySettings().getOD();
					beatmap.difficulty_hp = cbp.getDifficultySettings().getHP();

				} catch (IOException | URISyntaxException | BeatmapException e) {
					e.printStackTrace();
					m_client.sendMessage(lobby.channel, "Error Parsing beatmap. Please try again.");
				}

				if (!lobby.isBeatmapValid(beatmap)) {
					// TODO: The total_length check here was checking beatmap.total_length / 1.5) >= lobby.maxLength
					// I don't know what that / 1.5 was there tho...
					return;
				}

				if (!lobby.statusTypes.get(beatmap.graveyard)) {
					m_client.sendMessage(lobby.channel, sender
							+ " That beatmap is not within ranking criteria for this lobby! (Ranked/loved/etc)");
					return;
				}

				if (lobby.onlyGenre) {
					if (!beatmap.genre.equalsIgnoreCase(lobby.genre)) {
						m_client.sendMessage(lobby.channel, sender + " This lobby is set to only play "
								+ lobby.genres[Integer.valueOf(lobby.genre)] + " genre!");
						return;
					}
				}
				m_bot.addBeatmap(lobby, beatmap);

			});
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void handleReady(Lobby lobby, String sender) {
		if (lobby.Playing) {
			m_client.sendMessage(sender, "The lobby is currently playing, you cant vote for starting right now.");
			return;
		}
		if (lobby.currentBeatmap == 0) {
			m_client.sendMessage(sender, "Please add a map before starting playing!");
			return;
		}
		if (lobby.votestarted(sender)) {
			m_client.sendMessage(sender, "You already voted for starting!");
		} else {
			lobby.voteStart.add(sender);
			m_client.sendMessage(lobby.channel, sender + " voted for starting! (" + lobby.voteStart.size() + "/"
					+ (int) round(lobby.slots.size() * 0.75, 0) + ")");
			if (lobby.voteStart.size() >= round(lobby.slots.size() * 0.75, 0)) {
				m_bot.start(lobby);
			}
		}
	}

	private void handleSkip(Lobby lobby, String sender) {
		if (lobby.Playing) {
			m_client.sendMessage(sender, "The lobby is currently playing, you cant vote for skipping right now.");
			return;
		}
		if (lobby.votedskip(sender)) {
			m_client.sendMessage(sender, "You already voted for skipping!");
		} else {
			lobby.voteskip.add(sender);
			m_client.sendMessage(lobby.channel, sender + " voted for skipping! (" + lobby.voteskip.size() + "/"
					+ (int) round(lobby.slots.size() * 0.6, 0) + ")");
			if (lobby.voteskip.size() >= (int) round(lobby.slots.size() * 0.6, 0)) {
				m_client.sendMessage(lobby.channel, "Map has been skipped by vote.");
				m_bot.nextbeatmap(lobby);
			}
		}
	}

	private void handleInfo(Lobby lobby) {
		// TODO: Maybe whisper this to the player, so we don't spam the multi chat.
		m_client.sendMessage(lobby.channel,
				"This is an in-development IRC version of autohost developed by HyPeX. "
						+ "Do !commands to know them ;) "
						+ "[https://discord.gg/UDabf2y Discord] "
						+ "[https://www.reddit.com/r/osugame/comments/67u0k9/autohost_bot_is_finally_ready_for_public_usage/ Reddit Thread]");
	}

	private void handleCommands(Lobby lobby) {
		m_client.sendMessage(lobby.channel,
				"C.List: !add [beatmap] "
						+ "| !ready/!r "
						+ "| !skip/!s "
						+ "| !queue/!playlist "
						+ "| !ver "
						+ "| !last "
						+ "| !maxdiff "
						+ "| !mindiff "
						+ "| !graveyard "
						+ "| !clearhost "
						+ "| !hostme "
						+ "| !favorites/!fav");
	}

	private void handlePlaylist(Lobby lobby) {
		StringBuilder sb = new StringBuilder();
		sb.append("Queue: ")
				.append(lobby.beatmapQueue.size());
		for (Beatmap bm : lobby.beatmapQueue) {
			sb.append(" || ")
					.append("[https://osu.ppy.sh/b/")
					.append(bm.beatmap_id)
					.append(" ")
					.append(bm.artist)
					.append(" - ")
					.append(bm.title)
					.append("] [")
					.append(round(bm.difficulty, 2))
					.append("*]");
		}
		m_client.sendMessage(lobby.channel, sb.toString());
	}

	private void handleSelect(Lobby lobby, String sender, String message) {
		Matcher sm = RegexUtils.matcher(
				"select (.+)",
				message);
		if (!sm.matches()) {
			m_client.sendMessage(lobby.channel,
					"Incorrect usage, please do !select [number]. "
							+ "Please consider using the number in []");
			return;
		}
		if (lobby.requests.containsKey(sender)) {
			int map = Integer.valueOf(sm.group(1));
			// TODO: Fix this ~~amazing~~ line.
			m_bot.addBeatmap(lobby,
					lobby.requests.get(sender).beatmaps.get(lobby.requests.get(sender).bids.get(map)));
			lobby.requests.remove(sender);
		} else {
			m_client.sendMessage(lobby.channel, "You dont have any pending map requests.");
		}
	}

	private void handleMaxDifficulty(Lobby lobby, String sender, String message) {
		if (!lobby.isOP(m_bot.getId(sender))) return;

		Matcher diffM = RegexUtils.matcher(
				"maxdiff (\\d+(?:\\.\\d+)?)",
				message);
		if (diffM.matches()) {
			lobby.setParameterMax(BeatmapParameter.STAR_DIFFICULTY, Double.valueOf(diffM.group(1)));
			m_client.sendMessage(lobby.channel, "Max difficulty now is " + diffM.group(1));
		}
	}

	private void handleFreeMods(Lobby lobby, String sender) {
		if (!lobby.isOP(m_bot.getId(sender))) return;
		m_client.sendMessage(lobby.channel, "!mp mods Freemod");
	}

	private void handleMode(Lobby lobby, String sender, String message) {
		if (!lobby.isOP(m_bot.getId(sender))) return;

		Matcher modeMatch = RegexUtils.matcher(
				"mode (.+)",
				message);
		if (modeMatch.matches()) {
			if (modeMatch.group(1).equalsIgnoreCase("mania")) {
				lobby.type = "3";
				m_client.sendMessage(lobby.channel,
						"This lobby is now a mania lobby");
			} else if (modeMatch.group(1).equalsIgnoreCase("std")
					|| modeMatch.group(1).equalsIgnoreCase("standard"))
			{
				lobby.type = "0";
				m_client.sendMessage(lobby.channel,
						"This lobby is now a Standard lobby");
			} else if (modeMatch.group(1).equalsIgnoreCase("ctb")) {
				lobby.type = "2";
				m_client.sendMessage(lobby.channel,
						"This lobby is now a Catch The Beat lobby");
			} else if (modeMatch.group(1).equalsIgnoreCase("taiko")) {
				lobby.type = "1";
				m_client.sendMessage(lobby.channel,
						"This lobby is now a Taiko lobby");
			}
		}
	}

	private void handleGraveyard(Lobby lobby, String sender) {
		if (!lobby.isOP(m_bot.getId(sender))) {
			m_client.sendMessage(lobby.channel, sender + " You're not an Operator!");
			return;
		}

		if (lobby.graveyard) {
			lobby.graveyard = false;
			lobby.WIP = false;
			lobby.pending = false;
			lobby.statusTypes.put(0, false);
			lobby.statusTypes.put(-1, false);
			lobby.statusTypes.put(-2, false);
			m_client.sendMessage(lobby.channel,
					"Graveyard maps are now unallowed.");
		} else {
			lobby.graveyard = true;
			lobby.WIP = true;
			lobby.pending = true;
			lobby.statusTypes.put(0, true);
			lobby.statusTypes.put(-1, true);
			lobby.statusTypes.put(-2, true);
			m_client.sendMessage(lobby.channel,
					"Graveyard maps are now allowed.");
		}
	}

	private void handleLock(Lobby lobby, String sender) {
		if (!lobby.isOP(m_bot.getId(sender))) {
			m_client.sendMessage(lobby.channel, sender + ", you're not an Operator!");
			return;
		}

		if (lobby.lockAdding) {
			m_client.sendMessage(lobby.channel, "Map requests are now enabled.");
			lobby.lockAdding = false;
		} else {
			m_client.sendMessage(lobby.channel, "Map requests are now disabled.");
			lobby.lockAdding=true;
		}
	}

	private void handleVersion(Lobby lobby) {
		m_client.sendMessage(lobby.channel, "Bot version is " + AutoHost.VERSION);
	}

	private void handleWait(Lobby lobby) {
		// TODO: This logic is slightly confusing, specifically the naming of "extendTimer"
		m_client.sendMessage(lobby.channel,
				lobby.timer.extendTimer()
						? "Timer extended by 1 minute."
						: "Timer was already extended.");
	}

	private void handleLobby(Lobby lobby, String sender, String message) {
		if (!lobby.isOP(m_bot.getId(sender))) return;
		Matcher settingMatcher = RegexUtils.matcher(
				"lobby (.+) (.*)",
				message);
		if (settingMatcher.matches()) {

		}
	}

	private void handleStart(Lobby lobby, String sender) {
		if (!lobby.isOP(m_bot.getId(sender))) return;
		m_bot.tryStart(lobby);
	}

	private void handleLast(Lobby lobby, String sender) {
		m_bot.getLastPlay(lobby, sender);
	}

	private void handleKick(Lobby lobby, String sender, String message) {
		if (!lobby.isOP(m_bot.getId(sender))) return;

		Matcher idmatch = RegexUtils.matcher(
				"kick (\\d+)",
				message);
		Matcher namematch = RegexUtils.matcher(
				"kick (.+)",
				message);
		if (idmatch.matches()) {
			m_client.sendMessage(lobby.channel, "!mp kick #" + idmatch.group(1));
			return;
		} else if (namematch.matches()) {
			for (int i = 0; i < 16; i++) {
				Slot slot = lobby.slots.get(i);
				if (slot != null)
					if (slot.name.toLowerCase().contains(namematch.group(1).toLowerCase())) {
						m_client.sendMessage(lobby.channel, "!mp kick #" + slot.playerid);
						return;
					}
			}
		}
		m_client.sendMessage(lobby.channel, sender + " user not found.");
	}

	private void handleAddOP(Lobby lobby, String sender, String message) {
		if (!lobby.isOP(m_bot.getId(sender))) return;

		Matcher diffM = RegexUtils.matcher(
				"addop (\\d+)",
				message);
		if (diffM.matches()) {
			lobby.OPs.add(Integer.valueOf(diffM.group(1)));
		}
	}

	private void handleForceSkip(Lobby lobby, String sender) {
		if (!lobby.isOP(m_bot.getId(sender))) return;
		m_bot.nextbeatmap(lobby);
	}

	private void handleForceStart(Lobby lobby, String sender) {
		if (!lobby.isOP(m_bot.getId(sender))) return;
		m_client.sendMessage(lobby.channel, "!mp start");
	}

	private void handlePassword(Lobby lobby, String sender, String message) {
		if (!lobby.isOP(m_bot.getId(sender))) return;

		Matcher pwmatch = RegexUtils.matcher(
				"password (.+)?",
				message);
		if (pwmatch.matches()) {
			if (pwmatch.groupCount() == 1) {
				if (pwmatch.group(1).equalsIgnoreCase("reset")) {
					lobby.Password = "";
				} else {
					lobby.Password = pwmatch.group(1);
				}
				m_client.sendMessage(lobby.channel, "!mp password");
			} else {
				m_client.sendMessage(lobby.channel,
						"Current password is " + lobby.Password);
			}
		}
	}

	private void handleHostMe(Lobby lobby, String sender) {
		int id = m_bot.getId(sender);
		if (!lobby.isOP(id)) return;
		m_client.sendMessage(lobby.channel, "!mp host #" + id);
	}

	private void handleClearHost(Lobby lobby, String sender) {
		if (!lobby.isOP(m_bot.getId(sender))) return;
		m_client.sendMessage(lobby.channel, "!mp clearhost");
	}

	private void handleRandom(Lobby lobby, String sender) {
		if (!lobby.isOP(m_bot.getId(sender))) return;
		lobby.TrueRandom = !lobby.TrueRandom;
		m_client.sendMessage(lobby.channel,
				"Toggled Random Maps. State: " + lobby.TrueRandom);
	}

	private void handleRename(Lobby lobby, String sender, String message) {
		if (!lobby.isOP(m_bot.getId(sender))) return;

		Matcher renameM = RegexUtils.matcher(
				"rename (.+)",
				message);
		if (renameM.matches()) {
			lobby.name = renameM.group(1);
		}
	}

	private void handleSay(Lobby lobby, String sender, String message) {
		if (!sender.equalsIgnoreCase("HyPeX")) {
			m_client.sendMessage(lobby.channel,
					"I'm afraid " + sender + " I can't let you do that.");
			return;
		}
		Matcher sayM = RegexUtils.matcher(
				"say (.+)",
				message);
		if (sayM.matches()) {
			m_client.sendMessage(lobby.channel, sayM.group(1));
		} else {
			m_client.sendMessage(lobby.channel,
					"Wrong command syntax. Really dude? "
							+ "You made me... and you cant get a fucking command right");
		}
	}

	private void handleCloseRoom(Lobby lobby, String sender) {
		if (!lobby.isOP(m_bot.getId(sender))) {
			m_client.sendMessage(lobby.channel, sender + " You're not an Operator!");
			return;
		}

		m_client.sendMessage(lobby.channel, "!mp close");
		m_bot.removeLobby(lobby);
	}

	private void handleSongSearch(Lobby lobby, String sender, String message) {
		if (lobby.lockAdding) {
			m_client.sendMessage(lobby.channel, sender + " sorry, beatmap requesting is currently disabled.");
			return;
		}
		if (!lobby.isOP(m_bot.getId(sender))) {
			if (m_bot.hasAlreadyRequested(lobby, sender)) {
				m_client.sendMessage(lobby.channel, sender + " you have already requested a beatmap!");
				return;
			}
		}
		String mapname = null;
		String author = null;
		Matcher mapsearchM = RegexUtils.matcher(
				"searchsong ([^|]+)(\\ \\|\\ )?([^|]+)",
				message);
		if (mapsearchM.matches()) {
				mapname = mapsearchM.group(1);
			}
		if (mapsearchM.groupCount() == 3 && mapsearchM.group(2) != null & mapsearchM.group(3) != null) {
				if (!mapsearchM.group(2).equalsIgnoreCase("") & !mapsearchM.group(3).equalsIgnoreCase("")) {
						author = mapsearchM.group(3);
					}
			}
		if (mapname == null) {
				m_client.sendMessage(lobby.channel, sender
						+ " Incorrect Arguments for !searchsong. Please use the beatmap title. !searchsong [title]");
			} else {
				m_bot.searchBeatmap(mapname, lobby, sender, author);
			}
	}

	private void handleSet(Lobby lobby, String sender, String message) {
		if (!lobby.isOP(m_bot.getId(sender))) {
			m_client.sendMessage(lobby.channel, sender + ", you're not an Operator!");
			return;
		}

		Matcher command = RegexUtils.matcher(
				"set (?<mode>.*) (?<param>.*) (?<value>.*)",
				message);
		if (!command.matches()) {
			// TODO: Print usage?
			return;
		}

		String mode = command.group("mode");
		String param = command.group("param");
		String valueString = command.group("value");
		boolean clearValue = valueString.equalsIgnoreCase("clear");

		BeatmapSetting setting = lobby.getBeatmapSetting(param);
		if (setting == null) {
			// TODO: Print usage?
			return;
		}

		Object value = null;
		if (!clearValue) {
			try {
				if (setting.getType().equals(Double.class)) {
					value = Double.parseDouble(valueString);
				} else if (setting.getType().equals(Integer.class)) {
					value = Integer.parseInt(valueString);
				} else {
					// TODO: Print sever error. This should never happen.
					return;
				}
			} catch (NumberFormatException e) {
				// TODO: Print usage?
				return;
			}
		}

		boolean setMin = false;
		boolean setMax = false;
		if (mode.equalsIgnoreCase("min")) {
			setMin = true;
		} else if (mode.equalsIgnoreCase("max")) {
			setMax = true;
		} else if (mode.equalsIgnoreCase("both")) {
			setMin = true;
			setMax = true;
		} else {
			// TODO: Print usage?
			return;
		}

		StringBuilder sb = new StringBuilder();
		if (setMin && setMax) {
			sb.append(setting.getLongName());
		} else if (setMin) {
			sb.append("Max ").append(setting.getLongName().toLowerCase());
		} else {
			sb.append("Min ").append(setting.getLongName().toLowerCase());
		}
		sb.append(" ");

		if (clearValue) {
			if (setMin) {
				setting.clearMin();
			}
			if (setMax) {
				setting.clearMax();
			}
			sb.append("is no longer restricted.");
		} else {
			if (setMin) {
				setting.setMin(value);
			}
			if (setMax) {
				setting.setMax(value);
			}
			sb.append("is now restricted to ");
			if (param.equalsIgnoreCase(BeatmapParameter.LENGTH.getName())) {
				int maxLength = (Integer) value;
				int minutes = maxLength / (int)MINUTE;
				int seconds = maxLength % (int)MINUTE;
				sb.append(minutes)
						.append(":")
						.append(seconds);
			} else if (param.equalsIgnoreCase(BeatmapParameter.LAST_UPDATED.getName())) {
				sb.append(value);
			} else {
				DecimalFormat df = new DecimalFormat("#0.00");
				sb.append(df.format(value));
			}
			sb.append(".");
		}
	}
}
