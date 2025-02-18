package me.dadus33.chatitem.utils;

import me.dadus33.chatitem.ChatItem;
import net.md_5.bungee.api.ChatColor;

public class ColorManager {

	public static String getColorString(String input) {
		if (input == null || input.isEmpty())
			return "";
		StringBuilder str = new StringBuilder();
		if (Version.getVersion().isNewerOrEquals(Version.V1_16) && input.startsWith("x")) { // x mean it's an hex
			ChatItem.debug("Removing char x at begin: " + input.substring(1));
			input = input.substring(1);
			if (input.length() >= 6) { // at least hex
				str.append(ChatColor.of("#" + input.substring(0, 6))); // get first hex color code
				ChatItem.debug("Str with hex: " + str);
				if (input.length() > 6) // if as another color code
					str.append(getColorString(input.substring(6))); // get color for after
				return str.toString();
			} else
				ChatItem.debug("Low len: " + input.length());
		}
		// not hex
		for (char c : input.toCharArray())
			str.append(ChatColor.getByChar(c));
		return str.toString();
	}

	public static ChatColor getColor(String input) {
		if (input == null || input.isEmpty())
			return ChatColor.RESET;
		if (Version.getVersion().isNewerOrEquals(Version.V1_16) && input.startsWith("x")) { // x mean it's an hex
			ChatItem.debug("Removing char x at begin: " + input.substring(1));
			if (input.length() >= 7) { // at least hex, and 7 because we count the "x"
				return ChatColor.of("#" + input.substring(1, 7)); // get first hex color code
			} else
				ChatItem.debug("Low len: " + input.length());
		}
		// not hex
		return ChatColor.getByChar(input.charAt(0));
	}

	public static String fixColor(String message) {
		String colorCode = "", text = "";
		boolean waiting = false;
		for (char args : message.toCharArray()) {
			if (args == '§') { // begin of color
				waiting = true; // waiting for color code
			} else if (waiting) { // if waiting for code and valid str
				// if it's hexademical value and with enough space for full color
				waiting = false;
				if (args == 'r' && colorCode.isEmpty()) {
					text += ChatColor.RESET;
					continue;
				}
				if (args == 'x' && !colorCode.isEmpty()) {
					text += ColorManager.getColorString(colorCode);
					colorCode = "x";
				} else
					colorCode += args; // a color by itself
			} else {
				waiting = false;
				if (!colorCode.isEmpty()) {
					if (colorCode.startsWith("x") && colorCode.length() >= 7) {
						if (colorCode.length() == 7)
							text += ColorManager.getColor(colorCode);
						else {
							text += ColorManager.getColor(colorCode.substring(0, 7)); // only the hex code
							ChatItem.debug("Adding color for " + colorCode.substring(7, colorCode.length()) + " (in "
									+ colorCode + ")");
							text += ColorManager.getColorString(colorCode.substring(7, colorCode.length()));
						}
					} else if (colorCode.length() == 1) // if only one color code
						text += ColorManager.getColor(colorCode);
					else
						text += ColorManager.getColorString(colorCode);
					colorCode = "";
				}
				// basic text, not waiting for code after '§'
				text += args;
			}
		}
		return text;
	}
}
