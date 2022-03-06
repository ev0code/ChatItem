package me.dadus33.chatitem.chatmanager.v1.json;

import static me.dadus33.chatitem.utils.PacketUtils.getNmsClass;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.github.steveice10.opennbt.tag.builtin.ByteArrayTag;
import com.github.steveice10.opennbt.tag.builtin.ByteTag;
import com.github.steveice10.opennbt.tag.builtin.CompoundTag;
import com.github.steveice10.opennbt.tag.builtin.DoubleTag;
import com.github.steveice10.opennbt.tag.builtin.FloatTag;
import com.github.steveice10.opennbt.tag.builtin.IntArrayTag;
import com.github.steveice10.opennbt.tag.builtin.IntTag;
import com.github.steveice10.opennbt.tag.builtin.ListTag;
import com.github.steveice10.opennbt.tag.builtin.LongTag;
import com.github.steveice10.opennbt.tag.builtin.ShortTag;
import com.github.steveice10.opennbt.tag.builtin.StringTag;
import com.github.steveice10.opennbt.tag.builtin.Tag;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import me.dadus33.chatitem.ChatItem;
import me.dadus33.chatitem.chatmanager.v1.json.custom.BooleanTag;
import me.dadus33.chatitem.chatmanager.v1.json.custom.ListMultiTypesTag;
import me.dadus33.chatitem.chatmanager.v1.json.quick.JSONArray;
import me.dadus33.chatitem.chatmanager.v1.json.quick.JSONObject;
import me.dadus33.chatitem.chatmanager.v1.json.quick.parser.JSONParser;
import me.dadus33.chatitem.chatmanager.v1.utils.Item;
import me.dadus33.chatitem.chatmanager.v1.utils.ItemRewriter;
import me.dadus33.chatitem.utils.PacketUtils;
import me.dadus33.chatitem.utils.Reflect;
import me.dadus33.chatitem.utils.Version;

@SuppressWarnings({"unchecked", "deprecation"})
public class JSONManipulatorCurrent implements JSONManipulator {

	public static final Class<?> CRAFT_ITEM_STACK_CLASS = PacketUtils.getObcClass("inventory.CraftItemStack");
	public static final Class<?> NBT_STRING = getNmsClass("NBTTagString", "nbt.");
	public static final Class<?> NBT_LIST = getNmsClass("NBTTagList", "nbt.");
	public static final Map<Class<?>, BiFunction<String, Object, Tag>> TYPES_TO_MC_NBT_TAGS = new HashMap<>();
	public static final Map<Class<?>, Function<String, Tag>> TYPES_TO_OPEN_NBT_TAGS = new HashMap<>();
	public static final List<Class<?>> NBT_BASE_CLASSES = new ArrayList<>();
	public static final List<Field> NBT_BASE_DATA_FIELD = new ArrayList<>();
	public static final Class<?> NMS_ITEM_STACK_CLASS = getNmsClass("ItemStack", "world.item.");
	public static final Method AS_NMS_COPY = Reflect.getMethod(CRAFT_ITEM_STACK_CLASS, "asNMSCopy", ItemStack.class);
	public static final Class<?> NBT_TAG_COMPOUND = getNmsClass("NBTTagCompound", "nbt.");
	public static final Method SAVE_NMS_ITEM_STACK_METHOD = Reflect.getMethod(NMS_ITEM_STACK_CLASS, NBT_TAG_COMPOUND,
			NBT_TAG_COMPOUND);
	public static final Field MAP = Reflect.getField(NBT_TAG_COMPOUND, "map", "x");
	public static final Field LIST_FIELD = Reflect.getField(NBT_LIST, "list", "c");

	// Tags to be ignored. Currently it only contains tags from PortableHorses, but
	// feel free to submit a pull request to add tags from your plugins
	public static final List<String> IGNORED = Arrays.asList("horsetag", "phorse", "iscnameviz", "cname");

	public static final ConcurrentHashMap<Map.Entry<Version, ItemStack>, JsonObject> STACKS = new ConcurrentHashMap<>();

	static {
		NBT_BASE_CLASSES.add(getNmsClass("NBTTagByte", "nbt."));
		NBT_BASE_CLASSES.add(getNmsClass("NBTTagByteArray", "nbt."));
		NBT_BASE_CLASSES.add(getNmsClass("NBTTagDouble", "nbt."));
		NBT_BASE_CLASSES.add(getNmsClass("NBTTagFloat", "nbt."));
		NBT_BASE_CLASSES.add(getNmsClass("NBTTagInt", "nbt."));
		NBT_BASE_CLASSES.add(getNmsClass("NBTTagIntArray", "nbt."));
		NBT_BASE_CLASSES.add(getNmsClass("NBTTagLong", "nbt."));
		NBT_BASE_CLASSES.add(getNmsClass("NBTTagShort", "nbt."));

		for (Class<?> NBT_BASE_CLASS : NBT_BASE_CLASSES) {
			NBT_BASE_DATA_FIELD.add(Reflect.getField(NBT_BASE_CLASS, "data", "c", "x"));
		}
		
		// include NBT tags
		TYPES_TO_MC_NBT_TAGS.put(getNmsClass("NBTTagByte", "nbt."), (name, obj) -> new ByteTag(name, get(obj, "h")));
		TYPES_TO_MC_NBT_TAGS.put(getNmsClass("NBTTagByteArray", "nbt."), (name, obj) -> new ByteArrayTag(name, get(obj, "d")));
		TYPES_TO_MC_NBT_TAGS.put(getNmsClass("NBTTagDouble", "nbt."), (name, obj) -> new DoubleTag(name, get(obj, "i")));
		TYPES_TO_MC_NBT_TAGS.put(getNmsClass("NBTTagFloat", "nbt."), (name, obj) -> new FloatTag(name, get(obj, "j")));
		TYPES_TO_MC_NBT_TAGS.put(getNmsClass("NBTTagInt", "nbt."), (name, obj) -> new IntTag(name, get(obj, "f")));
		TYPES_TO_MC_NBT_TAGS.put(getNmsClass("NBTTagIntArray", "nbt."), (name, obj) -> new IntArrayTag(name, get(obj, "c")));
		TYPES_TO_MC_NBT_TAGS.put(getNmsClass("NBTTagLong", "nbt."), (name, obj) -> new LongTag(name, get(obj, "e")));
		TYPES_TO_MC_NBT_TAGS.put(getNmsClass("NBTTagShort", "nbt."), (name, obj) -> new ShortTag(name, get(obj, "g")));
		// now basic types
		
		TYPES_TO_OPEN_NBT_TAGS.put(String.class, StringTag::new);
		TYPES_TO_OPEN_NBT_TAGS.put(Boolean.class, BooleanTag::new);
		TYPES_TO_OPEN_NBT_TAGS.put(Byte.class, ByteTag::new);
		TYPES_TO_OPEN_NBT_TAGS.put(Byte[].class, ByteArrayTag::new);
		TYPES_TO_OPEN_NBT_TAGS.put(Double.class, DoubleTag::new);
		TYPES_TO_OPEN_NBT_TAGS.put(Float.class, FloatTag::new);
		TYPES_TO_OPEN_NBT_TAGS.put(Integer.class, IntTag::new);
		TYPES_TO_OPEN_NBT_TAGS.put(Integer[].class, IntArrayTag::new);
		TYPES_TO_OPEN_NBT_TAGS.put(Long.class, LongTag::new);
		TYPES_TO_OPEN_NBT_TAGS.put(Short.class, ShortTag::new);
		// Add the primitive types too, just in case
		TYPES_TO_OPEN_NBT_TAGS.put(boolean.class, BooleanTag::new);
		TYPES_TO_OPEN_NBT_TAGS.put(byte.class, ByteTag::new);
		TYPES_TO_OPEN_NBT_TAGS.put(byte[].class, ByteArrayTag::new);
		TYPES_TO_OPEN_NBT_TAGS.put(double.class, DoubleTag::new);
		TYPES_TO_OPEN_NBT_TAGS.put(float.class, FloatTag::new);
		TYPES_TO_OPEN_NBT_TAGS.put(int.class, IntTag::new);
		TYPES_TO_OPEN_NBT_TAGS.put(int[].class, IntArrayTag::new);
		TYPES_TO_OPEN_NBT_TAGS.put(long.class, LongTag::new);
		TYPES_TO_OPEN_NBT_TAGS.put(short.class, ShortTag::new);

	}

	public static <T> T get(Object obj, String methodName){
		try {
			return (T) obj.getClass().getDeclaredMethod(methodName).invoke(obj);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
	
	private List<String> replaces;
	private String rgx;
	private Version protocolVersion;
	private JsonObject itemTooltip;
	private JsonArray classicTooltip;

	public String parse(String json, List<String> replacements, ItemStack item, String replacement, int protocol)
			throws Exception {
		JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
		JsonArray array = obj.getAsJsonArray("extra");
		replaces = replacements;
		String regex = "";
		for (int i = 0; i < replacements.size(); ++i) {
			if (replacements.size() == 1) {
				regex = Pattern.quote(replacements.get(0));
				break;
			}
			if (i == 0 || i + 1 == replacements.size()) {
				if (i == 0) {
					regex = "(" + Pattern.quote(replacements.get(i));
				} else {
					regex = regex.concat("|").concat(Pattern.quote(replacements.get(i))).concat(")");
				}
				continue;
			}
			regex = regex.concat("|").concat(Pattern.quote(replacements.get(i)));
		}
		rgx = regex;
		JsonArray rep = new JsonArray();
		final AbstractMap.SimpleEntry<Version, ItemStack> p = new AbstractMap.SimpleEntry<>(
				protocolVersion = Version.getVersion(protocol), item);

		if ((itemTooltip = STACKS.get(p)) == null) {
			JsonArray use = Translator.toJson(replacement); // We get the json representation of the old color
															// formatting method
			// There's no public clone method for JSONObjects so we need to parse them every time
			JsonObject hover = JsonParser.parseString("{\"action\":\"show_item\", \"value\": \"\"}").getAsJsonObject();

			String jsonRep =  JsonTester.stringifyItem2(this, item);// stringifyItem(item); // Get the JSON representation of the item (well, not really JSON, but
													// rather a string representation of NBT data)
			hover.addProperty("value", jsonRep);

			JsonObject wrapper = new JsonObject(); // Create a wrapper object for the whole array
			wrapper.addProperty("text", ""); // The text field is compulsory, even if it's empty
			wrapper.add("extra", use);
			wrapper.add("hoverEvent", hover);

			itemTooltip = wrapper; // Save the tooltip for later use when we encounter a placeholder
			STACKS.put(p, itemTooltip); // Save it in the cache too so when parsing other packets with the same item
										// (and client version) we no longer have to create it again
			// We remove it later when no longer needed to save memory
			Bukkit.getScheduler().runTaskLaterAsynchronously(ChatItem.getInstance(), () -> STACKS.remove(p), 100L);
		}

		for (int i = 0; i < array.size(); ++i) {
			if (array.get(i).isJsonObject()) {
				JsonObject o = array.get(i).getAsJsonObject();
				boolean inside = false;
				for (String replace : replacements)
					if (o.toString().contains(replace)) {
						if (inside) {
							break;
						}
						inside = true;
					}
				JsonElement text = o.get("text");
				if (text == null) {
					JsonElement el = o.get("extra");
					if (el != null) {
						JsonArray jar = el.getAsJsonArray();
						if (jar.size() != 0) {
							jar = parseArray(jar);
							o.add("extra", jar);
						} else {
							o.remove("extra");
						}
					}
					continue;
				} else {
					if (text.getAsString().isEmpty()) {
						JsonElement el = o.get("extra");
						if (el != null) {
							JsonArray jar = el.getAsJsonArray();
							if (jar.size() != 0) {
								jar = parseArray(jar);
								o.add("extra", jar);
							} else {
								o.remove("extra");
							}
						}
					}
				}

				String msg = text.getAsString();
				boolean isLast = false;
				boolean done = false;
				boolean fnd;
				String[] splits;
				for (String repls : replacements) {
					if (done) {
						break;
					}
					isLast = msg.endsWith(repls);
					if (isLast) {
						done = true;
						msg = msg.concat(".");
					}
				}
				splits = msg.split(regex);
				fnd = splits.length != 1;
				if (fnd)
					for (int j = 0; j < splits.length; ++j) {
						boolean endDot = (j == splits.length - 1) && isLast;
						if (!splits[j].isEmpty() && !endDot) {
							String st = o.toString();
							JsonObject fix = JsonParser.parseString(st).getAsJsonObject();
							fix.addProperty("text", splits[j]);
							rep.add(fix);
						}
						if (j != splits.length - 1) {
							rep.add(itemTooltip);
						}
					}
				if (!fnd) {
					rep.add(o);
				}
			} else {
				if (array.get(i).isJsonNull()) {
					continue;
				} else {
					if (array.get(i).isJsonArray()) {
						JsonArray jar = array.get(i).getAsJsonArray();
						if (jar.size() != 0) {
							jar = parseArray(array.get(i).getAsJsonArray());
							rep.set(i, jar);
						}
					} else {

						String msg = array.get(i).getAsString();
						boolean isLast = false;
						boolean done = false;
						boolean fnd;
						String[] splits;
						for (String repls : replacements) {
							if (done) {
								break;
							}
							isLast = msg.endsWith(repls);
							if (isLast) {
								done = true;
								msg = msg.concat(".");
							}
						}
						splits = msg.split(regex);
						fnd = splits.length != 1;
						if (fnd)
							for (int j = 0; j < splits.length; ++j) {
								boolean endDot = (j == splits.length - 1) && isLast;
								if (!splits[j].isEmpty() && !endDot) {
									JsonElement fix = new JsonPrimitive(splits[j]);
									rep.add(fix);
								}
								if (j != splits.length - 1) {
									rep.add(itemTooltip);
								}
							}
						if (!fnd) {
							rep.add(array.get(i));
						}
					}
				}
			}

		}
		obj.add("extra", rep);
		if (!obj.has("text")) {
			obj.addProperty("text", "");
		}
		return obj.toString();
	}

	@Override
	public String parseEmpty(String json, List<String> replacements, String repl, List<String> tooltip, Player sender) {
		JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
		JsonArray array = obj.getAsJsonArray("extra");
		replaces = replacements;
		String regex = "";
		for (int i = 0; i < replacements.size(); ++i) {
			if (replacements.size() == 1) {
				regex = Pattern.quote(replacements.get(0));
				break;
			}
			if (i == 0 || i + 1 == replacements.size()) {
				if (i == 0) {
					regex = "(" + Pattern.quote(replacements.get(i));
				} else {
					regex = regex.concat("|").concat(Pattern.quote(replacements.get(i))).concat(")");
				}
				continue;
			}
			regex = regex.concat("|").concat(Pattern.quote(replacements.get(i)));
		}
		rgx = regex;
		JsonArray rep = new JsonArray();
		JsonArray use = Translator
				.toJson(repl.replace("{name}", sender.getName()).replace("{display-name}", sender.getDisplayName()));
		JsonObject hover = JsonParser.parseString("{\"action\":\"show_text\", \"value\": \"\"}").getAsJsonObject();

		StringBuilder oneLineTooltip = new StringBuilder("");
		int index = 0;
		for (String m : tooltip) {
			oneLineTooltip
					.append(m.replace("{name}", sender.getName()).replace("{display-name}", sender.getDisplayName()));
			++index;
			if (index != tooltip.size()) {
				oneLineTooltip.append('\n');
			}
		}

		hover.add("value", new JsonPrimitive(oneLineTooltip.toString()));
		for (JsonElement ob : use)
			ob.getAsJsonObject().add("hoverEvent", hover);

		classicTooltip = use;

		for (int i = 0; i < array.size(); ++i) {
			if (array.get(i).isJsonObject()) {
				JsonObject o = array.get(i).getAsJsonObject();
				boolean inside = false;
				for (String replace : replacements)
					if (o.toString().contains(replace)) {
						if (inside) {
							break;
						}
						inside = true;
					}
				JsonElement text = o.get("text");
				if (text == null) {
					JsonElement el = o.get("extra");
					if (el != null) {
						JsonArray jar = el.getAsJsonArray();
						if (jar.size() != 0) {
							jar = parseNoItemArray(jar);
							o.add("extra", jar);
						} else {
							o.remove("extra");
						}
					}
					continue;
				} else {
					if (text.getAsString().isEmpty()) {
						JsonElement el = o.get("extra");
						if (el != null) {
							JsonArray jar = el.getAsJsonArray();
							if (jar.size() != 0) {
								jar = parseNoItemArray(jar);
								o.add("extra", jar);
							} else {
								o.remove("extra");
							}
						}
					}
				}

				String msg = text.getAsString();
				boolean isLast = false;
				boolean done = false;
				boolean fnd;
				String[] splits;
				for (String repls : replacements) {
					if (done) {
						break;
					}
					isLast = msg.endsWith(repls);
					if (isLast) {
						done = true;
						msg = msg.concat(".");
					}
				}
				splits = msg.split(regex);
				fnd = splits.length != 1;
				if (fnd)
					for (int j = 0; j < splits.length; ++j) {
						boolean endDot = (j == splits.length - 1) && isLast;
						if (!splits[j].isEmpty() && !endDot) {
							String st = o.toString();
							JsonObject fix = JsonParser.parseString(st).getAsJsonObject();
							fix.addProperty("text", splits[j]);
							rep.add(fix);
						}
						if (j != splits.length - 1) {
							rep.addAll(use);
						}
					}
				if (!fnd) {
					rep.add(o);
				}
			} else {
				if (array.get(i).isJsonNull()) {
					continue;
				} else {
					if (array.get(i).isJsonArray()) {
						JsonArray jar = array.get(i).getAsJsonArray();
						if (jar.size() != 0) {
							jar = parseNoItemArray(array.get(i).getAsJsonArray());
							rep.set(i, jar);
						}
					} else {

						String msg = array.get(i).getAsString();
						boolean isLast = false;
						boolean done = false;
						boolean fnd;
						String[] splits;
						for (String repls : replacements) {
							if (done) {
								break;
							}
							isLast = msg.endsWith(repls);
							if (isLast) {
								done = true;
								msg = msg.concat(".");
							}
						}
						splits = msg.split(regex);
						fnd = splits.length != 1;
						if (fnd)
							for (int j = 0; j < splits.length; ++j) {
								boolean endDot = (j == splits.length - 1) && isLast;
								if (!splits[j].isEmpty() && !endDot) {
									JsonElement fix = new JsonPrimitive(splits[j]);
									rep.add(fix);
								}
								if (j != splits.length - 1) {
									rep.addAll(use);
								}
							}
						if (!fnd) {
							rep.add(array.get(i));
						}

					}
				}
			}

		}
		obj.add("extra", rep);
		return obj.toString();
	}

	private JsonArray parseNoItemArray(JsonArray arr) {
		JsonArray replacer = new JsonArray();
		for (int i = 0; i < arr.size(); ++i) {
			if (arr.get(i).isJsonObject()) {
				JsonObject o = arr.get(i).getAsJsonObject();
				boolean inside = false;
				for (String replacement : replaces)
					if (o.toString().contains(replacement)) {
						if (inside) {
							break;
						}
						inside = true;
					}
				if (!inside) { // the placeholder we're looking for is not inside this element, so we continue
								// searching
					replacer.add(o);
					continue;
				}
				JsonElement text = o.get("text");
				if (text == null) {
					continue;
				}
				if (text.getAsString().isEmpty()) {
					JsonElement el = o.get("extra");
					if (el == null) {
						continue;
					}
					JsonArray jar = el.getAsJsonArray();
					if (jar.size() != 0) {
						jar = parseNoItemArray(jar);
						o.add("extra", jar);
					} else {
						o.remove("extra");
					}
				}

				String msg = text.getAsString();
				boolean isLast = false;
				boolean done = false;
				boolean fnd;
				String[] splits;
				for (String repls : replaces) {
					if (done) {
						break;
					}
					isLast = msg.endsWith(repls);
					if (isLast) {
						done = true;
						msg = msg.concat(".");
					}
				}
				splits = msg.split(rgx);
				fnd = splits.length != 1;
				if (fnd)
					for (int j = 0; j < splits.length; ++j) {
						boolean endDot = (j == splits.length - 1) && isLast;
						if (!splits[j].isEmpty() && !endDot) {
							String st = o.toString();
							JsonObject fix = JsonParser.parseString(st).getAsJsonObject();
							fix.addProperty("text", splits[j]);
							replacer.add(fix);
						}
						if (j != splits.length - 1) {
							replacer.addAll(classicTooltip);
						}
					}
				if (!fnd) {
					replacer.add(o);
				}
			} else {
				if (arr.get(i).isJsonNull()) {
					continue;
				} else {
					if (arr.get(i).isJsonArray()) {
						JsonArray jar = arr.get(i).getAsJsonArray();
						if (jar.size() != 0) {
							jar = parseNoItemArray(arr.get(i).getAsJsonArray());
							replacer.set(i, jar);
						}
					} else {
						String msg = arr.get(i).getAsString();
						boolean isLast = false;
						boolean done = false;
						boolean fnd;
						String[] splits;
						for (String repls : replaces) {
							if (done) {
								break;
							}
							isLast = msg.endsWith(repls);
							if (isLast) {
								done = true;
								msg = msg.concat(".");
							}
						}
						splits = msg.split(rgx);
						fnd = splits.length != 1;
						if (fnd)
							for (int j = 0; j < splits.length; ++j) {
								boolean endDot = (j == splits.length - 1) && isLast;
								if (!splits[j].isEmpty() && !endDot) {
									JsonElement fix = new JsonPrimitive(splits[j]);
									replacer.add(fix);
								}
								if (j != splits.length - 1) {
									replacer.addAll(classicTooltip);
								}
							}
						if (!fnd) {
							replacer.add(arr.get(i));
						}
					}
				}
			}

		}
		return replacer;
	}

	private JsonArray parseArray(JsonArray arr) {
		JsonArray replacer = new JsonArray();
		for (int i = 0; i < arr.size(); ++i) {
			if (arr.get(i).isJsonObject()) {
				JsonObject o = arr.get(i).getAsJsonObject();
				boolean inside = false;
				for (String replacement : replaces)
					if (o.toString().contains(replacement)) {
						if (inside) {
							break;
						}
						inside = true;
					}
				if (!inside) { // the placeholder we're looking for is not inside this element, so we continue
								// searching
					replacer.add(o);
					continue;
				}
				JsonElement text = o.get("text");
				if (text == null) {
					continue;
				}
				if (text.getAsString().isEmpty()) {
					JsonElement el = o.get("extra");
					if (el == null) {
						continue;
					}
					JsonArray jar = el.getAsJsonArray();
					if (jar.size() != 0) {
						jar = parseArray(jar);
						o.add("extra", jar);
					} else {
						o.remove("extra");
					}
				}

				String msg = text.getAsString();
				boolean isLast = false;
				boolean done = false;
				boolean fnd;
				String[] splits;
				for (String repls : replaces) {
					if (done) {
						break;
					}
					isLast = msg.endsWith(repls);
					if (isLast) {
						done = true;
						msg = msg.concat(".");
					}
				}
				splits = msg.split(rgx);
				fnd = splits.length != 1;
				if (fnd)
					for (int j = 0; j < splits.length; ++j) {
						boolean endDot = (j == splits.length - 1) && isLast;
						if (!splits[j].isEmpty() && !endDot) {
							String st = o.toString();
							JsonObject fix = JsonParser.parseString(st).getAsJsonObject();
							fix.addProperty("text", splits[j]);
							replacer.add(fix);
						}
						if (j != splits.length - 1) {
							replacer.add(itemTooltip);
						}
					}
				if (!fnd) {
					replacer.add(o);
				}
			} else {
				if (arr.get(i).isJsonNull()) {
					continue;
				} else {
					if (arr.get(i).isJsonArray()) {
						JsonArray jar = arr.get(i).getAsJsonArray();
						if (jar.size() != 0) {
							jar = parseArray(arr.get(i).getAsJsonArray());
							replacer.set(i, jar);
						}
					} else {
						String msg = arr.get(i).getAsString();
						boolean isLast = false;
						boolean done = false;
						boolean fnd;
						String[] splits;
						for (String repls : replaces) {
							if (done) {
								break;
							}
							isLast = msg.endsWith(repls);
							if (isLast) {
								done = true;
								msg = msg.concat(".");
							}
						}
						splits = msg.split(rgx);
						fnd = splits.length != 1;
						if (fnd)
							for (int j = 0; j < splits.length; ++j) {
								boolean endDot = (j == splits.length - 1) && isLast;
								if (!splits[j].isEmpty() && !endDot) {
									JsonElement fix = new JsonPrimitive(splits[j]);
									replacer.add(fix);
								}
								if (j != splits.length - 1) {
									replacer.add(itemTooltip);
								}
							}
						if (!fnd) {
							replacer.add(arr.get(i));
						}
					}
				}
			}

		}
		return replacer;
	}

	private Item toItem(ItemStack is) throws Exception {
		CompoundTag tag = new CompoundTag("tag");

		Object nmsStack = AS_NMS_COPY.invoke(null, is);
		Object nmsTag = NBT_TAG_COMPOUND.newInstance();
		SAVE_NMS_ITEM_STACK_METHOD.invoke(nmsStack, nmsTag);
		Map<String, Object> nmsMap = (Map<String, Object>) MAP.get(nmsTag);
		String id = nmsMap.get("id").toString().replace("\"", "");
		Object realTag = nmsMap.get("tag");
		if (NBT_TAG_COMPOUND.isInstance(realTag)) { // We need to make sure this is indeed an NBTTagCompound
			Map<String, Object> realMap = (Map<String, Object>) MAP.get(realTag);
			Set<Map.Entry<String, Object>> entrySet = realMap.entrySet();
			Map<String, Tag> map = tag.getValue();
			ChatItem.debug("Tag map: " + entrySet.size() + " > " + entrySet);
			for (Map.Entry<String, Object> entry : entrySet) {
				map.put(entry.getKey(), toOpenTag(entry.getValue(), entry.getKey()));
			}
			tag.setValue(map);
		}

		Item item = new Item();
		item.setAmount((byte) is.getAmount());
		item.setData(is.getDurability());
		item.setId(id);
		item.setTag(tag);
		return item;
	}
	
	private CompoundTag toOpenTag(JSONObject json, String name) throws Exception {
		CompoundTag tag = new CompoundTag(name);
		HashMap<String, Tag> map = new HashMap<>();
		json.forEach((key, value) -> {
			Function<String, Tag> fun = TYPES_TO_OPEN_NBT_TAGS.get(value.getClass());
			if(fun != null) {
				Tag t = fun.apply(key.toString());
				setValueToOpenTag(t, value);
				map.put(key.toString(), t);
			} else
				ChatItem.debug("Failed to find open tab for JSONObject > " + value.getClass().getSimpleName() + " : " + key);
		});
		tag.setValue(map);
		return tag;
	}
	
	private Tag toOpenTag(JSONArray json, String name) throws Exception {
		List<Tag> list = new ArrayList<>();
		for(Object item : json) {
			if(item instanceof JSONObject) {
				list.add(toOpenTag((JSONObject) item, ""));
			} else {
				ChatItem.debug("This type from JSONArray is not supported. " + item.getClass().getSimpleName() + " : " + item);
			}
		}
		ChatItem.debug("List: " + list + ", str: " + stringifyTag(new ListMultiTypesTag(name, list)));
		return new ListMultiTypesTag(name, list);
	}

	private Tag toOpenTag(Object nmsTag, String name) throws Exception {
		if(nmsTag instanceof String) {
			return new StringTag(name, (String) nmsTag);
		}
		if (NBT_TAG_COMPOUND.isInstance(nmsTag)) {
			CompoundTag tag = new CompoundTag(name);
			Map<String, Tag> tagMap = tag.getValue();

			Map<String, Object> nmsMap = (Map<String, Object>) MAP.get(nmsTag);
			Set<Map.Entry<String, Object>> entrySet = nmsMap.entrySet();
			for (Map.Entry<String, Object> entry : entrySet) {
				tagMap.put(entry.getKey(), toOpenTag(entry.getValue(), entry.getKey()));
			}
			tag.setValue(tagMap);
			return tag;
		} else {
			// Strings are a special case as they need proper escaping
			if (NBT_STRING.isInstance(nmsTag)) {
				ChatItem.debug("NbtString for " + name + " > " + nmsTag);
				String toString = nmsTag.toString();
				if (toString.startsWith("\"") && toString.endsWith("\"")) {
					return new StringTag(name, toString.substring(1, toString.length() - 1));
				} else if (toString.startsWith("'") && toString.endsWith("'")) {
					toString = toString.substring(1, toString.length() - 1);
					CompoundTag tag = new CompoundTag(name);
					ChatItem.debug("Checking compound " + name);
					Map<String, Tag> tagMap = tag.getValue();
					Set<Map.Entry<String, Object>> entrySet = ((Map<String, Object>) new JSONParser().parse(toString)).entrySet();
					for (Map.Entry<String, Object> entry : entrySet) {
						Object val = entry.getValue();
						Tag t;
						if(val instanceof JSONObject)
							t = toOpenTag((JSONObject) entry.getValue(), entry.getKey());
						else if(val instanceof JSONArray)
							t = toOpenTag((JSONArray) entry.getValue(), entry.getKey());
						else
							t = toOpenTag(entry.getValue(), entry.getKey());
						ChatItem.debug("Key: " + entry.getKey() + " > " + stringifyTag(t) + ", val: " + val.getClass().getSimpleName());
						tagMap.put(entry.getKey(), t);
					}
					tag.setValue(tagMap);
					ChatItem.debug("Final compound " + stringifyTag(tag));
					return tag;
				} else
					ChatItem.debug("Invalid string about NbtString for " + toString);
			}

			// NBTTag Lists are also special, as they are a sort of compound themselves and
			// need to be parsed recursively
			if (NBT_LIST.isInstance(nmsTag)) {
				List<Object> nmsNBTBaseList = (List<Object>) LIST_FIELD.get(nmsTag);
				List<Tag> list = new ArrayList<>();
				for (Object baseTag : nmsNBTBaseList) {
					list.add(toOpenTag(baseTag, ""));
				}
				ChatItem.debug("NbtList final with " + stringifyTag(new ListTag(name, list)));
				return new ListTag(name, list);
			}

			for (int i = 0; i < NBT_BASE_CLASSES.size(); ++i) {
				Class<?> c = NBT_BASE_CLASSES.get(i);
				if (c.isInstance(nmsTag)) {
					Object value = NBT_BASE_DATA_FIELD.get(i).get(nmsTag);
					if(value.getClass().getSimpleName().startsWith("NBTTag")) {
						if(!TYPES_TO_MC_NBT_TAGS.containsKey(value.getClass())) {
							ChatItem.getInstance().getLogger().warning("Failed to find NBT tag for class: " + value.getClass().getSimpleName());
							continue;
						}
						return TYPES_TO_MC_NBT_TAGS.get(value.getClass()).apply(name, value);
					} else {
						Tag t = TYPES_TO_OPEN_NBT_TAGS.get(value.getClass()).apply(name);
						setValueToOpenTag(t, value);
						return t;
					}
				}
			}
			ChatItem.debug("What that shit for " + name + " > " + nmsTag.getClass().getSimpleName() + " : " + nmsTag.toString());
			return null; // Should never happen
		}
	}
	
	private void setValueToOpenTag(Tag t, Object value) {
		if (t instanceof ByteTag) {
			((ByteTag) t).setValue((byte) value);
		} else if (t instanceof ByteArrayTag)
			((ByteArrayTag) t).setValue((byte[]) value);
		else if (t instanceof DoubleTag)
			((DoubleTag) t).setValue((double) value);
		else if (t instanceof FloatTag)
			((FloatTag) t).setValue((float) value);
		else if (t instanceof IntTag)
			((IntTag) t).setValue((int) value);
		else if (t instanceof IntArrayTag)
			((IntArrayTag) t).setValue((int[]) value);
		else if (t instanceof LongTag)
			((LongTag) t).setValue((long) value);
		else if (t instanceof ShortTag)
			((ShortTag) t).setValue((short) value);
		else if (t instanceof StringTag)
			((StringTag) t).setValue((String) value);
		else if (t instanceof BooleanTag)
			((BooleanTag) t).setValue((boolean) value);
		else
			ChatItem.getInstance().getLogger().warning("Failed to find Open tag for class: " + t.getClass().getSimpleName());
	}

	private String stringifyItem(ItemStack stack) throws Exception {
		Item item = toItem(stack);
		ItemRewriter.remapIds(Version.getVersion().MAX_VER, protocolVersion.MAX_VER, item);
		StringBuilder sb = new StringBuilder("{id:");
		sb.append("\"").append(item.getId()).append("\"").append(","); // Append the id
		sb.append("Count:").append(item.getAmount()).append("b"); // Append the amount

		Map<String, Tag> tagMap = item.getTag().getValue();
		if(!tagMap.containsKey("Damage")) { // for new versions
			sb.append(",Damage:").append(item.getData()).append("s"); // Append the durability data
		}
		if (tagMap.isEmpty()) {
			sb.append("}");
			return sb.toString();
		}
		Set<Map.Entry<String, Tag>> entrySet = tagMap.entrySet();
		boolean first = true;
		sb.append(",tag:{"); // Start of the tag
		for (Map.Entry<String, Tag> entry : entrySet) {
			String key = entry.getKey();
			if (IGNORED.contains(key)) {
				ChatItem.debug("Ignored key: " + key + " (val: " + entry.getValue().toString() + ")");
				continue;
			}
			Pattern pattern = Pattern.compile("[{}\\[\\],\":\\\\/]");
			Matcher matcher = pattern.matcher(key);
			if (matcher.find()) {
				ChatItem.debug("Invalid matcher: " + key + " (val: " + entry.getValue().toString() + ")");
				continue; // Skip invalid keys, as they can cause exceptions client-side
			}
			String value = stringifyTag(entry.getValue());
			if (!first) {
				sb.append(",");
			}
			if(!key.isEmpty())
				sb.append(key).append(":");
			sb.append(value);
			first = false;
		}
		sb.append("}}"); // End of tag and end of item
		return sb.toString();
	}

	private String stringifyTag(Tag normalTag) {
		if (normalTag instanceof CompoundTag) {
			StringBuilder sb = new StringBuilder("{");
			CompoundTag tagCompound = (CompoundTag) normalTag;
			Map<String, Tag> tagMap = tagCompound.getValue();
			Set<Map.Entry<String, Tag>> entrySet = tagMap.entrySet();
			for (Map.Entry<String, Tag> entry : entrySet) {
				String value = stringifyTag(entry.getValue());
				if (value == null) {
					ChatItem.debug("Failed to stringify " + entry.getValue().getClass().getSimpleName());
					continue;
				}
				if (sb.length() > 1) {
					sb.append(",");
				}
				if(!entry.getKey().isEmpty())
					sb.append(entry.getKey()).append(":");
				sb.append(value);
			}

			sb.append("}");
			return sb.toString();
		} else {
			if (normalTag instanceof StringTag) {
				return "\"" + ((StringTag) normalTag).getValue() + "\""; // Should be already escaped
			}

			if (normalTag instanceof ListTag) {
				List<Tag> list = ((ListTag) normalTag).getValue();
				StringBuilder sb = new StringBuilder("[");
				boolean first = true;
				for (Tag tag : list) {
					String index = tag.getName();
					String value = stringifyTag(tag);
					if (value == null) {
						ChatItem.debug("Failed to stringify tag " + tag.getClass().getSimpleName() + ": " + tag);
						continue;
					}
					if (!first) {
						sb.append(",");
					}
					if (protocolVersion.MAX_VER <= Version.V1_11.MAX_VER && !index.isEmpty()) { // it's before 1.12
						sb.append(index).append(":").append(value);
					} else {
						sb.append(value);
					}

					first = false;
				}
				sb.append("]");
				return sb.toString();
			}

			if (normalTag instanceof ByteTag) {
				return normalTag.getValue() + "b";
			}
			if (normalTag instanceof ByteArrayTag) {
				return "[" + ((byte[]) normalTag.getValue()).length + " bytes]";
			}
			if (normalTag instanceof DoubleTag) {
				return (double) normalTag.getValue() + "d";
			}
			if (normalTag instanceof FloatTag) {
				return (float) normalTag.getValue() + "f";
			}
			if (normalTag instanceof IntTag) {
				return String.valueOf((int) normalTag.getValue());
			}
			if (normalTag instanceof IntArrayTag) {
				int[] array = (int[]) normalTag.getValue();
				if (array.length == 0) {
					return null;
				}
				StringBuilder sb = new StringBuilder("[");
				boolean first = true;
				for (int i : array) {
					if (!first) {
						sb.append(",");
					}
					sb.append(i);
					first = false;
				}
				sb.append("]");
				return sb.toString();
			}
			if (normalTag instanceof LongTag) {
				return (long) normalTag.getValue() + "L";
			}
			if (normalTag instanceof ShortTag) {
				return (short) normalTag.getValue() + "s";
			}
			if (normalTag instanceof BooleanTag) {
				return String.valueOf((boolean) normalTag.getValue());
			}
			ChatItem.debug("Unrecognized tag " + normalTag.getClass().getClass() + ", value: " + normalTag);
			return null; // Should never happen
		}
	}

}
