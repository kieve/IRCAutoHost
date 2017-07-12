package autohost.lobby;

import java.util.EnumMap;

public class BeatmapSetting <T extends Comparable<T>> {
	private BeatmapParameter m_param;
	private Class<T>         m_type;
	private T                m_minValue;
	private T                m_maxValue;

	private BeatmapSetting(BeatmapParameter param, Class<T> type) {
		m_param = param;
		m_type = type;

		Object min = m_param.getDefaultMin();
		if (min != null && type.isInstance(min)) {
			m_minValue = type.cast(min);
		}

		Object max = m_param.getDefaultMax();
		if (max != null && type.isInstance(max)) {
			m_maxValue = type.cast(max);
		}
	}

	public static EnumMap<BeatmapParameter, BeatmapSetting> createSettings() {
		EnumMap<BeatmapParameter, BeatmapSetting> result = new EnumMap<>(BeatmapParameter.class);

		for (BeatmapParameter p : BeatmapParameter.values()) {
			BeatmapSetting settings = null;
			if (p.getType().equals(Double.class)) {
				settings = new BeatmapSetting<>(p, Double.class);
			} else if (p.getType().equals(Integer.class)) {
				settings = new BeatmapSetting<>(p, Integer.class);
			}

			result.put(p, settings);
		}

		return result;
	}

	public String getName() {
		return m_param.getName();
	}

	public String getLongName() {
		return m_param.getLongName();
	}

	public Class<T> getType() {
		return m_type;
	}

	public T getMin() {
		return m_minValue;
	}

	public T getMax() {
		return m_maxValue;
	}

	public boolean hasMin() {
		return m_minValue != null;
	}

	public boolean hasMax() {
		return m_maxValue != null;
	}

	public boolean isEnabled() {
		return hasMin() || hasMax();
	}

	public void setMin(Object value) {
		if (m_type.isInstance(value)) {
			m_minValue = m_type.cast(value);
		}
	}

	public void setMax(Object value) {
		if (m_type.isInstance(value)) {
			m_maxValue = m_type.cast(value);
		}
	}

	public void clearMin() {
		m_minValue = null;
	}

	public void clearMax() {
		m_maxValue = null;
	}

	public int compare(Object valueObj) {
		if (valueObj == null || !m_type.isInstance(valueObj)) return 0;
		T value = m_type.cast(valueObj);

		if (m_minValue != null && value.compareTo(m_minValue) < 0) {
			return -1;
		}

		if (m_maxValue != null && value.compareTo(m_maxValue) > 0) {
			return 1;
		}

		return 0;
	}
}
