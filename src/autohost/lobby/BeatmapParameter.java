package autohost.lobby;

public enum BeatmapParameter {
	CIRCLE_SIZE("cs", "Circle size", Double.class),
	HP_DRAIN("hp", "HP drain", Double.class),
	OVERALL_DIFFICULTY("od", "Overall difficulty", Double.class),
	APPROACH_RATE("ar", "Approach rate", Double.class),
	STAR_DIFFICULTY("diff", "Star difficulty", Double.class, 4.0d, 5.0d),
	LENGTH("length", "Song length", Integer.class, 0, 360),
	BPM("bpm", "BPM", Double.class),
	LAST_UPDATED("year", "Song year", Integer.class);

	private final String m_name;
	private final String m_longName;
	private final Class  m_type;
	private final Object m_defaultMin;
	private final Object m_defaultMax;

	BeatmapParameter(String name, String longName, Class type) {
		this(name, longName, type, null, null);
	}

	BeatmapParameter(String name, String longName, Class type, Object defaultMin, Object defaultMax) {
		m_name = name;
		m_longName = longName;
		m_type = type;
		m_defaultMin = defaultMin;
		m_defaultMax = defaultMax;
	}

	public String getName() {
		return m_name;
	}

	public String getLongName() {
		return m_longName;
	}

	public Class getType() {
		return m_type;
	}

	public Object getDefaultMin() {
		return m_defaultMin;
	}

	public Object getDefaultMax() {
		return m_defaultMax;
	}
}
