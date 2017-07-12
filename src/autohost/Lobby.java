package autohost;

import autohost.lobby.BeatmapParameter;
import autohost.lobby.BeatmapSetting;
import autohost.util.Beatmap;
import autohost.util.Request;
import autohost.util.Slot;
import autohost.util.TimerThread;

import java.util.*;

public class Lobby {
	private final EnumMap<BeatmapParameter, BeatmapSetting> m_beatmapSettings;

	public String channel = "";
	public Integer LobbySize = 16;
	public List<Integer> OPs = new LinkedList<>();

	public List<String> voteStart = new LinkedList<>();
	public List<String> voteskip  = new LinkedList<>();

	public TimerThread timer;
	public String name = "";
	public Boolean OPLobby = false;
	public String creatorName;

	public String Password = "";
	public Boolean Playing = false;
	public Integer mpID;

	public Boolean rejoined = false;

	public String currentBeatmapAuthor = "HyPeX";
	public String currentBeatmapName = "Some random shit";
	public Integer currentBeatmap = 0;

	public Boolean TrueRandom = true;

	public Queue<Beatmap> beatmapQueue = new LinkedList<>();
	public Queue<Beatmap> beatmapPlayed = new LinkedList<>();
	public Boolean DoubleTime = false;
	public Boolean NightCore = false;
	public Boolean HalfTime = false;

	public Map<Integer, Slot> slots = new HashMap<>();
	public Map<Integer, lt.ekgame.beatmap_analyzer.beatmap.Beatmap> beatmaps = new HashMap<>();
	public Map<String, Request> requests = new HashMap<>();

	public Map<String, Integer> afk = new HashMap<>();
	public Map<String, Integer> scores = new HashMap<>();

	public String[] genres = new String[]{ "any" , "unspecified", "video game", "anime", "rock", "pop", "other", "novelty", "error", "hip hop", "electronic"};
	public Map<Integer, Boolean> statusTypes =  new HashMap<>();

	/*
	 * Lobby Specific Settings
	 * 	These set up the lobbies to differ from other lobbies
	 */

	public Boolean lockAdding = false;

	public String teamgamemode; // Team Type of the lobby. Solo? TeamVs?

	public String winCondition; // Win type. Score, Scorev2, etc

	public Integer status; // 4 = loved, 3 = qualified, 2 = approved, 1 = ranked, 0 = pending, -1 = WIP, -2 = graveyard

	public Boolean loved = true;
	public Boolean qualified = true;
	public Boolean approved = true;
	public Boolean ranked = true;
	public Boolean pending = false;
	public Boolean WIP = false;
	public Boolean graveyard = false;

	public Boolean onlyType = true; // Lock lobby to type
	public String type; // 0 = osu!, 1 = Taiko, 2 = CtB, 3 = osu!mania


	public Boolean onlyTags = false; // Lock lobby to tags
	public Boolean inclusiveTags = false; // Songs must contain ALL tags instead of one of the listed
	public String[] Tags;


	public Boolean onlyGenre = false; // Unrecommended. This is usually // inaccurate
	public String genre = "0"; // 0 = any, 1 = unspecified, 2 = video game, 3 =
								// anime, 4 = rock, 5 = pop, 6 = other, 7 =
								// novelty, 9 = hip hop, 10 = electronic (note
								// that there's no 8)


	public Integer previousBeatmap;

	public Lobby() {
		m_beatmapSettings = BeatmapSetting.createSettings();

		// 4 = loved, 3 = qualified, 2 = approved, 1 = ranked, 0 = pending, -1 = WIP, -2 = graveyard
		this.statusTypes.put(4, loved);
		this.statusTypes.put(3, qualified);
		this.statusTypes.put(2, approved);
		this.statusTypes.put(1, ranked);
		this.statusTypes.put(0, pending);
		this.statusTypes.put(-1, WIP);
		this.statusTypes.put(-2, graveyard);
	}

	public Lobby(String channel) {
		this();
		this.channel = channel;
	}

	public BeatmapSetting getBeatmapSetting(BeatmapParameter param) {
		return m_beatmapSettings.get(param);
	}

	public BeatmapSetting getBeatmapSetting(String name) {
		for (BeatmapSetting setting : m_beatmapSettings.values()) {
			if (setting.getName().equalsIgnoreCase(name)) {
				return setting;
			}
		}
		return null;
	}

	public void setParameter(BeatmapParameter param, Object minValue, Object maxValue) {
		BeatmapSetting setting = m_beatmapSettings.get(param);
		setting.setMin(minValue);
		setting.setMax(maxValue);
	}

	public void setParameterMin(BeatmapParameter param, Object minValue) {
		BeatmapSetting setting = m_beatmapSettings.get(param);
		setting.setMin(minValue);
	}

	public void setParameterMax(BeatmapParameter param, Object maxValue) {
		BeatmapSetting setting = m_beatmapSettings.get(param);
		setting.setMax(maxValue);
	}

	public boolean isBeatmapValid(Beatmap beatmap) {
		for (Map.Entry<BeatmapParameter, Object> entry : beatmap.getBeatmapParameters().entrySet()) {
			int result = m_beatmapSettings.get(entry.getKey()).compare(entry.getValue());
			if (result != 0) {
				// TODO: Return string error list, indicating which properties are invalid
				return false;
			}
		}
		return true;
	}

	public Boolean votestarted(String user){
		Boolean didvote = false;
		for (String player : voteStart){
			if (user.equalsIgnoreCase(player))
				didvote = true;
		}
		return didvote;
	}
	public Boolean votedskip(String user){
		Boolean didvote = false;
		for (String player : voteskip){
			if (user.equalsIgnoreCase(player))
				didvote = true;
		}
		return didvote;
	}

	public boolean isOP(int userId) {
		for (int id : OPs) {
			if (id == userId) return true;
		}
		return false;
	}
}
