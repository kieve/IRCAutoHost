package autohost.util;

import autohost.lobby.BeatmapParameter;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.EnumMap;
import java.util.regex.Matcher;

public class Beatmap {
	private EnumMap<BeatmapParameter, Object> m_beatmapParameters;

	public int RequestedBy;
	public String artist;
	public int play_length;
	public String gamemode;
	public int pass_count;
	public int total_length;
	public int beatmapset_id;
	public String genre; // 0 = any, 1 = unspecified, 2 = video game, 3 = anime,
							// 4 = rock, 5 = pop, 6 = other, 7 = novelty, 9 =
							// hip hop, 10 = electronic (note that there's no 8)
	public String mapper;
	public String date;
	public String source;
	public int beatmap_id;
	public int beatmap_status;
	public int map_count;
	public int favorites;
	public String language; // 0 = any, 1 = other, 2 = english, 3 = japanese, 4
							// = chinese, 5 = instrumental, 6 = korean, 7 =
							// french, 8 = german, 9 = swedish, 10 = spanish, 11
							// = italian
	public String title;
	public String difficulty_name;
	public double difficulty;
	public double difficulty_cs;
	public double difficulty_od;
	public double difficulty_ar;
	public double difficulty_hp;
	public double bpm;
	public int play_count;
	public int graveyard;
	public Boolean ignored;
	public String mods;
	public String[] tags;
	public int maxcombo;
	public Boolean DT;
	public Boolean NC;
	public Boolean HT;

	public Beatmap() {
		this.DT = false;
		this.NC = false;
		this.HT = false;
	};

	private void initParameters() {
		m_beatmapParameters = new EnumMap<>(BeatmapParameter.class);

		m_beatmapParameters.put(BeatmapParameter.CIRCLE_SIZE, difficulty_cs);
		m_beatmapParameters.put(BeatmapParameter.HP_DRAIN, difficulty_hp);
		m_beatmapParameters.put(BeatmapParameter.OVERALL_DIFFICULTY, difficulty_od);
		m_beatmapParameters.put(BeatmapParameter.APPROACH_RATE, difficulty_ar);
		m_beatmapParameters.put(BeatmapParameter.STAR_DIFFICULTY, difficulty);
		m_beatmapParameters.put(BeatmapParameter.LENGTH, total_length);
		m_beatmapParameters.put(BeatmapParameter.BPM, bpm);

		Matcher dateM = RegexUtils.matcher(
				"(\\d+)\\-(\\d+)\\-(\\d+)(.+)",
				date);
		if (dateM.matches()) {
			int year = Integer.valueOf(dateM.group(1));
			m_beatmapParameters.put(BeatmapParameter.LAST_UPDATED, year);
		}
	}

	public EnumMap<BeatmapParameter, Object> getBeatmapParameters() {
		return m_beatmapParameters;
	}

	public static Beatmap createFromApi(JSONObject obj) throws JSONException {
		Beatmap result = new Beatmap();
		result.gamemode = obj.getString("mode");
		result.graveyard = obj.getInt("approved");
		result.artist = obj.getString("artist");
		result.title = obj.getString("title");
		result.mapper = obj.getString("creator");
		result.beatmap_id = obj.getInt("beatmap_id");
		result.beatmapset_id = obj.getInt("beatmapset_id");
		result.bpm = obj.getDouble("bpm");
		result.difficulty_name = obj.getString("version");
		result.difficulty = obj.getDouble("difficultyrating");
		result.difficulty_ar = obj.getDouble("diff_approach");
		result.difficulty_cs = obj.getDouble("diff_size");
		result.difficulty_od = obj.getDouble("diff_overall");
		result.difficulty_hp = obj.getDouble("diff_drain");
		result.play_length = obj.getInt("hit_length");
		result.date = obj.getString("last_update");
		result.source = obj.getString("source");
		result.genre = obj.getString("genre_id");
		result.total_length = obj.getInt("total_length");
		result.tags = obj.getString("tags").split(" ");
		result.play_count = obj.getInt("playcount");
		result.pass_count = obj.getInt("passcount");
		try {
			result.maxcombo = obj.getInt("max_combo");
		} catch (JSONException e) {
			if (!result.gamemode.equalsIgnoreCase("3")
					|| !result.gamemode.equalsIgnoreCase("1"))
			{
				e.printStackTrace();
			}
			result.maxcombo = 0;
		}

		result.initParameters();
		return result;
	}

// Parsing from osusearch is different. Y u do dis osusearch
	//{"date": "2011-11-09T08:37:50", "favorites": 1623, "beatmap_status": 1, "language": "Japanese", "pass_count": 140819,
	//"difficulty_cs": 4.0, "play_length": 147, "ignored": null, "map_count": 4, "genre": "Anime",
	//"difficulty_ar": 9.0, "beatmapset": 29860, "source": "Mirai Nikki", "total_length": 155, "difficulty_hp": 8.0,
	//"beatmap_id": 124701, "title": "Kuusou Mesorogiwi", "mapper": "osuplayer111", "beatmapset_id": 39031,
	//"difficulty": 4.14548778533936, "bpm": 230, "play_count": 1383011, "artist": "Yousei Teikoku", "difficulty_od": 7.0,
	//"difficulty_name": "Insane", "gamemode": 0}
	public static Beatmap createFromSearch(JSONObject obj) throws JSONException {
		Beatmap result = new Beatmap();
		result.gamemode = ""+obj.getInt("gamemode");
		result.graveyard = obj.getInt("beatmap_status");
		result.artist = obj.getString("artist");
		result.title = obj.getString("title");
		result.mapper = obj.getString("mapper");
		result.beatmap_id = obj.getInt("beatmap_id");
		result.beatmapset_id = obj.getInt("beatmapset");
		result.bpm = obj.getDouble("bpm");
		result.difficulty_name = obj.getString("difficulty_name");
		result.difficulty = obj.getDouble("difficulty");
		result.difficulty_ar = obj.getDouble("difficulty_ar");
		result.difficulty_cs = obj.getDouble("difficulty_cs");
		result.difficulty_od = obj.getDouble("difficulty_od");
		result.difficulty_hp = obj.getDouble("difficulty_hp");
		result.play_length = obj.getInt("play_length");
		result.date = obj.getString("date");
		result.source = obj.getString("source");
		result.genre = obj.getString("genre");
		result.total_length = obj.getInt("total_length");
		//result.tags = obj.getString("tags").split(" "); No tags. Yeah who uses this anyway
		result.play_count = obj.getInt("play_count");
		result.pass_count = obj.getInt("pass_count");
		//no max combo

		result.initParameters();
		return result;
	};
}
