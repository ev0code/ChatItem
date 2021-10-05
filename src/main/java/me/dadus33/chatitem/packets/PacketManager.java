package me.dadus33.chatitem.packets;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.entity.Player;

public abstract class PacketManager {

	public abstract void addPlayer(Player p);
	public abstract void removePlayer(Player p);
	public abstract void clear();

	private final List<PacketHandler> handlers = new ArrayList<>();
	public boolean addHandler(PacketHandler handler) {
		return !handlers.add(handler);
	}

	public boolean removeHandler(PacketHandler handler) {
		return handlers.remove(handler);
	}

	public void notifyHandlersSent(AbstractPacket packet) {
		handlers.forEach((handler) -> handler.onSend(packet));
	}
}