package me.dadus33.chatitem.chatmanager.v2;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.ItemStack;

import me.dadus33.chatitem.ChatItem;
import me.dadus33.chatitem.ItemPlayer;
import me.dadus33.chatitem.chatmanager.ChatManager;
import me.dadus33.chatitem.hook.DiscordSrvSupport;
import me.dadus33.chatitem.playernamer.PlayerNamerManager;
import me.dadus33.chatitem.utils.ColorManager;
import me.dadus33.chatitem.utils.ItemUtils;
import me.dadus33.chatitem.utils.PacketUtils;
import me.dadus33.chatitem.utils.ReflectionUtils;
import me.dadus33.chatitem.utils.Storage;
import me.dadus33.chatitem.utils.Utils;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.HoverEvent.Action;
import net.md_5.bungee.api.chat.TextComponent;

@SuppressWarnings("deprecation")
public class ChatListener implements Listener {
	
	private static Method saveMethod;
	private static boolean shouldUseAppendMethod = false;
	
	static {
		try {
			Class<?> nbtTag = PacketUtils.getNmsClass("NBTTagCompound", "nbt.");
			Class<?> itemClass = PacketUtils.getNmsClass("ItemStack", "world.item.");
			for (Method m : itemClass.getDeclaredMethods()) {
				if (m.getParameterTypes().length == 1) {
					if (m.getParameterTypes()[0].equals(nbtTag) && m.getReturnType().equals(nbtTag)) {
						saveMethod = m;
					}
				}
			}
			try {
				ComponentBuilder.class.getDeclaredMethod("append", BaseComponent[].class);
				shouldUseAppendMethod = true;
			} catch (Exception e) {
				shouldUseAppendMethod = false;
			}
		} catch (Exception e) {

		}
		Logger log = ChatItem.getInstance().getLogger();
		String appendMethodMsg = shouldUseAppendMethod ? "Use ComponentBuilder's method."
				: "Use own ComponentBuilder append method.";
		if (saveMethod == null)
			log.info("Failed to find save method. Using default system. " + appendMethodMsg);
		else
			log.info("Save method founded: " + saveMethod.getName() + ". " + appendMethodMsg);
	}
	
	private ChatListenerChatManager manage;

	public ChatListener(ChatListenerChatManager manage) {
		this.manage = manage;
	}

	public Storage getStorage() {
		return manage.getStorage();
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onChat(AsyncPlayerChatEvent e) {
		Storage c = getStorage();
		if (e.isCancelled()) {
			if (ChatItem.getInstance().getChatManager().size() == 1) { // only chat
				String msg = e.getMessage().toLowerCase();
				for (String rep : c.PLACEHOLDERS) {
					if (msg.contains(rep)) {
						ChatItem.debug(
								"You choose 'chat' manager but it seems to don't be the good choice. More informations here: https://github.com/dadus33-plugins/ChatItem/wiki");
						return;
					}
				}
			}
			ChatItem.debug("Chat cancelled for " + e.getPlayer().getName());
			return;
		}
		Player p = e.getPlayer();
		boolean hasv1 = false;
		String placeholder = null;
		for (String rep : c.PLACEHOLDERS) {
			// v1 try to manage it, but the message have not been changed by another plugin
			if (e.getMessage().contains(rep + ChatManager.SEPARATOR + p.getName())) {
				hasv1 = true;
				ChatItem.debug("Found v1 placeholders in v2");
				placeholder = rep;
				break;
			}
			if (e.getMessage().contains(rep)) {
				placeholder = rep;
				break;
			}
		}

		if (placeholder == null) { // if not found
			ChatItem.debug("(v2) Can't found placeholder in: " + e.getMessage() + " > " + PlayerNamerManager.getPlayerNamer().getName(p));
			return;
		}
		ItemStack item = ChatManager.getUsableItem(p);
		if(!ChatManager.canShowItem(p, item, e))
			return;
		e.setCancelled(true);
		String format = e.getFormat();
		String msg, defMsg = e.getMessage();
		for (String rep : c.PLACEHOLDERS) {
			if (hasv1) // remove v1 char
				defMsg = defMsg.replace(rep + ChatManager.SEPARATOR + p.getName(), String.valueOf(ChatManager.SEPARATOR));
			else
				defMsg = defMsg.replace(rep, String.valueOf(ChatManager.SEPARATOR));
		}
		if (Utils.countMatches(defMsg, String.valueOf(ChatManager.SEPARATOR)) > getStorage().LIMIT) {
			e.setCancelled(true);
			if (getStorage().LIMIT_MESSAGE.isEmpty()) {
				return;
			}
			p.sendMessage(getStorage().LIMIT_MESSAGE);
			return;
		}
		if (format.contains("%1$s") || format.contains("%2$s")) {
			msg = (format.contains("%2$s") ? String.format(format, p.getDisplayName(), defMsg)
					: String.format(format, p.getDisplayName()));
		} else {
			msg = format.replace(e.getMessage(), defMsg);
		}
		String itemName = ChatManager.getNameOfItem(p, item, c);
		String loggedMessage = msg.replace(ChatManager.SEPARATOR + "", itemName).replace("{name}", p.getName())
				.replace("{display-name}", p.getDisplayName());
		Bukkit.getConsoleSender().sendMessage(loggedMessage); // show in log
		if (ChatItem.discordSrvSupport)
			DiscordSrvSupport.sendChatMessage(p, defMsg.replace(ChatManager.SEPARATOR + "", itemName).replace("{name}", p.getName())
					.replace("{display-name}", p.getDisplayName()));
		ChatItem.debug("Msg: " + msg.replace(ChatColor.COLOR_CHAR, '&') + ", format: " + format);
		e.getRecipients().forEach((pl) -> showItem(pl, p, item, msg));
		if (c.COOLDOWN > 0 && !p.hasPermission("chatitem.ignore-cooldown"))
			ChatManager.applyCooldown(p);
	}

	public static void showItem(Player to, Player origin, ItemStack item, String msg) {
		ComponentBuilder builder = new ComponentBuilder("");
		ChatColor color = ChatColor.WHITE;
		String colorCode = "", text = "";
		boolean waiting = false;
		for (char args : msg.toCharArray()) {
			if (args == '§') { // begin of color
				if (colorCode.isEmpty() && !text.isEmpty()) { // text before this char
					ChatItem.debug("Append " + text);
					appendToComponentBuilder(builder, new ComponentBuilder(text).color(color).create());
					text = "";
				}
				waiting = true; // waiting for color code
			} else if (waiting) { // if waiting for code and valid str
				// if it's hexademical value and with enough space for full color
				waiting = false;
				if(args == 'r' && colorCode.isEmpty()) {
					color = ChatColor.RESET;
					continue;
				}
				if(args == 'x' && !colorCode.isEmpty()) {
					text += ColorManager.getColorString(colorCode);
					colorCode = "x";
				} else
					colorCode += args; // a color by itself
			} else {
				waiting = false;
				if(!colorCode.isEmpty()) {
					if(colorCode.startsWith("x") && colorCode.length() >= 7) {
						if(colorCode.length() == 7)
							color = ColorManager.getColor(colorCode);
						else {
							color = ColorManager.getColor(colorCode.substring(0, 7)); // only the hex code
							ChatItem.debug("Adding color for " + colorCode.substring(7, colorCode.length()) + " (in " + colorCode + ")");
							text += ColorManager.getColorString(colorCode.substring(7, colorCode.length()));
						}
					} else if(colorCode.length() == 1) // if only one color code
						color = ColorManager.getColor(colorCode);
					else
						text += ColorManager.getColorString(colorCode);
					colorCode = "";
				}
				if (args == ChatManager.SEPARATOR) {
					// here put the item
					appendToComponentBuilder(builder, fixColorComponent(text, color));
					addItem(builder, to, origin, item);
					text = "";
				} else {
					// basic text, not waiting for code after '§'
					text += args;
				}
			}
		}
		if(!text.isEmpty())
			appendToComponentBuilder(builder, new ComponentBuilder(text).color(color).create());
		to.spigot().sendMessage(builder.create());
	}

	/**
	 * Converts an {@link org.bukkit.inventory.ItemStack} to a Json string for
	 * sending with {@link net.md_5.bungee.api.chat.BaseComponent}'s.
	 *
	 * @param itemStack the item to convert
	 * @return the Json string representation of the item
	 */
	public static String convertItemStackToJson(ItemStack itemStack) {
		try {
			Class<?> nbtTag = PacketUtils.getNmsClass("NBTTagCompound", "nbt.");
			Class<?> craftItemClass = PacketUtils.getObcClass("inventory.CraftItemStack");
			Object nmsNbtTagCompoundObj = nbtTag.getConstructor().newInstance();
			if (saveMethod == null) {
				Object nmsItemStackObj = craftItemClass.getMethod("asNMSCopy", ItemStack.class).invoke(null, itemStack);
				return nmsItemStackObj.getClass().getMethod("save", nbtTag)
						.invoke(nmsItemStackObj, nmsNbtTagCompoundObj).toString();
			} else {
				Object nmsItemStackObj = craftItemClass.getMethod("asNMSCopy", ItemStack.class).invoke(null, itemStack);
				return saveMethod.invoke(nmsItemStackObj, nmsNbtTagCompoundObj).toString();
			}
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	public static void addItem(ComponentBuilder builder, Player to, Player origin, ItemStack item) {
		Storage c = ChatItem.getInstance().getStorage();
		if (!ItemUtils.isEmpty(item)) {
			ComponentBuilder itemComponent = new ComponentBuilder(ChatManager.styleItem(to, item, c));
			String itemJson = convertItemStackToJson(item);
			ChatItem.debug("Item for " + to.getName() + " (ver: " + ItemPlayer.getPlayer(to).getVersion().name() + ") : " + itemJson);
			itemComponent.event(new HoverEvent(Action.SHOW_ITEM, new ComponentBuilder(itemJson).create()));
			appendToComponentBuilder(builder, itemComponent.create());
		} else {
			String handName = c.HAND_NAME;
			ComponentBuilder handComp = new ComponentBuilder("");
			ComponentBuilder handTooltip = new ComponentBuilder("");
			int stay = c.HAND_TOOLTIP.size();
			for (String line : c.HAND_TOOLTIP) {
				stay--;
				handTooltip.append(ColorManager.fixColor(line.replace("{name}", origin.getName()).replace("{display-name}", origin.getDisplayName())));
				if (stay > 0)
					handTooltip.append("\n");
			}
			handComp.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, handTooltip.create()));
			if (handName.contains("{display-name}")) {
				String[] splitted = handName.split("\\{display-name\\}");
				for (int i = 0; i < (splitted.length - 1); i++) {
					handComp.append(splitted[i]);
					appendToComponentBuilder(handComp, PlayerNamerManager.getPlayerNamer().getName(origin));
				}
				handComp.append(splitted[splitted.length - 1]);
			} else
				handComp.append(handName.replace("{name}", origin.getName()));
			appendToComponentBuilder(builder, handComp.create());
		}
	}

	@SuppressWarnings("unchecked")
	public static void appendToComponentBuilder(ComponentBuilder builder, BaseComponent[] comps) {
		if (shouldUseAppendMethod) {
			try {
				builder.append(comps);
			} catch (Exception e) {
				ChatItem.getInstance().getLogger().severe(
						"This should NEVER append. The ComponentBuilder#append(BaseComponent[]) was found but it's NOT. Using own next time.");
				shouldUseAppendMethod = false;
			}
		} else {
			try {
				Field currentField = ReflectionUtils.getField(builder, "current");
				List<BaseComponent> parts = (List<BaseComponent>) ReflectionUtils.getObject(builder, "parts");
				parts.add(new TextComponent((TextComponent) currentField.get(builder)));
				parts.addAll(Arrays.asList(comps));
				currentField.set(builder, new TextComponent(""));
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public static BaseComponent[] fixColorComponent(String message, ChatColor color) {
		ComponentBuilder builder = new ComponentBuilder("");
		String colorCode = "", text = "";
		boolean waiting = false;
		for (char args : message.toCharArray()) {
			if (args == '§') { // begin of color
				if (colorCode.isEmpty() && !text.isEmpty()) { // text before this char
					ChatItem.debug("Append while fixing name " + text);
					appendToComponentBuilder(builder, new ComponentBuilder(text).color(color).create());
					text = "";
				}
				waiting = true; // waiting for color code
			} else if (waiting) { // if waiting for code and valid str
				// if it's hexademical value and with enough space for full color
				waiting = false;
				if(args == 'r' && colorCode.isEmpty()) {
					color = ChatColor.RESET;
					continue;
				}
				if(args == 'x' && !colorCode.isEmpty()) {
					text += ColorManager.getColorString(colorCode);
					colorCode = "x";
				} else
					colorCode += args; // a color by itself
			} else {
				waiting = false;
				if(!colorCode.isEmpty()) {
					if(colorCode.startsWith("x") && colorCode.length() >= 7) {
						if(colorCode.length() == 7)
							color = ColorManager.getColor(colorCode);
						else {
							color = ColorManager.getColor(colorCode.substring(0, 7)); // only the hex code
							ChatItem.debug("Adding color for " + colorCode.substring(7, colorCode.length()) + " (in " + colorCode + ")");
							text += ColorManager.getColorString(colorCode.substring(7, colorCode.length()));
						}
					} else if(colorCode.length() == 1) // if only one color code
						color = ColorManager.getColor(colorCode);
					else
						text += ColorManager.getColorString(colorCode);
					colorCode = "";
				}
				// basic text, not waiting for code after '§'
				text += args;
			}
		}
		if(!text.isEmpty())
			appendToComponentBuilder(builder, new ComponentBuilder(text).color(color).create());
		return builder.create();
	}
}
