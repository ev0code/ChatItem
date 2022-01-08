package me.dadus33.chatitem.utils;

import java.net.InetSocketAddress;

import org.bukkit.Bukkit;

public enum ProtocolVersion {
	
	// http://wiki.vg/Protocol_version_numbers
	V1_7(0, 5, 0),
	V1_8(6, 47, 1),
	V1_9(49, 110, 2), // 1.9.X - Starts with 49 as 48 was an april fools update
	V1_10(201, 210, 3), // 1.10.X - Starts with 201 because why not.
	V1_11(301, 316, 4),
	V1_12(317, 340, 5),
	V1_13(341, 440, 6),
	V1_14(100, 441, 500),
	V1_15(100, 550, 578),
	V1_16(100, 700, 754),
	V1_17(100, 755, 756),
	V1_18(100, 757, 800),
	V1_19(100, 801, 1000),
	HIGHER(Integer.MAX_VALUE, -1, Integer.MAX_VALUE);

	// Latest version should always have the upper limit set to Integer.MAX_VALUE so
	// I don't have to update the plugin for every minor protocol change

	public final int MIN_VER;
	public final int MAX_VER;
	public final int index; // Represents how new the version is (0 - extremely old)

	private static final ProtocolVersion SERVER_VERSION;
	public static final String BUKKIT_VERSION;

	static {
		BUKKIT_VERSION = Bukkit.getServer().getClass().getPackage().getName().replace(".", ",").split(",")[3];
		SERVER_VERSION = getVersionByName(BUKKIT_VERSION);
	}

	ProtocolVersion(int min, int max, int index) {
		this.MIN_VER = min;
		this.MAX_VER = max;
		this.index = index;
	}

	public boolean isNewerThan(ProtocolVersion other) {
		return index > other.index;
	}
	
	public boolean isNewerOrEquals(ProtocolVersion other) {
		return index >= other.index;
	}

	public static ProtocolVersion getVersionByName(String name) {
		for (ProtocolVersion v : ProtocolVersion.values())
			if (name.toLowerCase().startsWith(v.name().toLowerCase()))
				return v;
		return HIGHER;
	}

	public static ProtocolVersion getVersion(int protocolVersion) {
		for (ProtocolVersion ver : ProtocolVersion.values()) {
			if (protocolVersion >= ver.MIN_VER && protocolVersion <= ver.MAX_VER) {
				return ver;
			}
		}
		return HIGHER;
	}

	public static ProtocolVersion getServerVersion() {
		return SERVER_VERSION;
	}

	public static String stringifyAdress(InetSocketAddress address) {
		String port = String.valueOf(address.getPort());
		String ip = address.getAddress().getHostAddress();
		return ip + ":" + port;
	}

}