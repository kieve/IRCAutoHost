package autohost.util;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Created by kieve on 2017-05-22.
 */
public abstract class JSONUtils {
	public static String silentGetString(JSONObject object, String key) {
		try {
			return object.getString(key);
		} catch (Exception e) {
			return "";
		}
	}

	public static int silentGetInt(JSONObject object, String key) {
		try {
			return object.getInt(key);
		} catch (Exception e) {
			return 0;
		}
	}

	public static Beatmap silentGetBeatmapFromApi(JSONObject obj) {
		try {
			return Beatmap.createFromApi(obj);
		} catch (Exception e) {
			return new Beatmap();
		}
	}

	public static Beatmap silentGetBeatmapFromSearch(JSONObject obj) {
		try {
			return Beatmap.createFromSearch(obj);
		} catch (Exception e) {
			return new Beatmap();
		}
	}

	public static JSONObject silentGetArray(JSONArray array, int index) {
		try {
			return array.getJSONObject(index);
		} catch (Exception e) {
			return new JSONObject();
		}
	}
}
