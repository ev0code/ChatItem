package me.dadus33.chatitem.packets.custom.channel;

import static me.dadus33.chatitem.utils.PacketUtils.getPlayerConnection;

import java.util.List;
import java.util.NoSuchElementException;

import org.bukkit.entity.Player;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import me.dadus33.chatitem.packets.AbstractPacket;
import me.dadus33.chatitem.packets.PacketType;
import me.dadus33.chatitem.packets.custom.CustomPacketManager;
import me.dadus33.chatitem.playerversion.hooks.DefaultVersionHook;
import me.dadus33.chatitem.utils.PacketUtils;
import me.dadus33.chatitem.utils.ProtocolVersion;
import me.dadus33.chatitem.utils.ReflectionUtils;

public class INC2Channel extends ChannelAbstract {
	
	@SuppressWarnings("unchecked")
	public INC2Channel(CustomPacketManager customPacketManager) {
		super(customPacketManager);
		try {
			Object mcServer = ReflectionUtils.callMethod(PacketUtils.getCraftServer(), "getServer");
			Object co = ReflectionUtils.getFirstWith(mcServer, PacketUtils.getNmsClass("MinecraftServer", "server."), PacketUtils.getNmsClass("ServerConnection", "server.network."));
			((List<ChannelFuture>) ReflectionUtils.getField(co, "f")).forEach((channelFuture) -> {
				channelFuture.channel().pipeline().addFirst(new ChannelInboundHandler(customPacketManager));
			});
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void addChannel(final Player player, String endChannelName) {
		getOrCreateAddChannelExecutor().execute(() -> {
			if(!player.isOnline())
				return;
			try {
				Channel channel = getChannel(player);
				// Managing outgoing packet (to the player)
				channel.pipeline().addAfter(KEY_HANDLER_SERVER, KEY_SERVER + endChannelName, new ChannelHandlerSent(player));

				AbstractPacket pa = ChannelInboundHandler.TMP.remove(channel);
				if(pa != null)
					DefaultVersionHook.PROTOCOL_PER_UUID.put(player.getUniqueId(), pa.getContent().getIntegers().readSafely(0, ProtocolVersion.getServerVersion().MAX_VER));
			} catch (NoSuchElementException e) {
				// appear when the player's channel isn't accessible because of reload.
				getPacketManager().getPlugin().getLogger().warning("Please, don't use reload, this can produce some problem. Currently, " + player.getName() + " isn't fully checked because of that. More details: " + e.getMessage() + " (NoSuchElementException)");
			} catch (IllegalArgumentException e) {
				if(e.getMessage().contains("Duplicate handler")) {
					removeChannel(player, endChannelName);
					addChannel(player, endChannelName);
				} else
					getPacketManager().getPlugin().getLogger().severe("Error while loading Packet channel. " + e.getMessage() + ". Please, prefer restart than reload.");
			} catch (Exception e) {
				e.printStackTrace();
			}
		});
	}

	@Override
	public void removeChannel(Player player, String endChannelName) {
		getOrCreateRemoveChannelExecutor().execute(() -> {
			try {
				final Channel channel = getChannel(player);
				if(channel.pipeline().get(KEY_SERVER + endChannelName) != null)
					channel.pipeline().remove(KEY_SERVER + endChannelName);
			} catch (Exception e) {}
		});
	}

	@Override
	public Channel getChannel(Player p) throws Exception {
		Object playerConnection = getPlayerConnection(p);
		Object networkManager = playerConnection.getClass().getField("a").get(playerConnection);
		return (Channel) networkManager.getClass().getDeclaredField("k").get(networkManager);//(Channel) networkManager.getClass().getDeclaredField("channel").get(networkManager);
	}

	private class ChannelHandlerSent extends ChannelOutboundHandlerAdapter {

		private final Player owner;

		public ChannelHandlerSent(Player player) {
			this.owner = player;
		}

		@Override
		public void write(ChannelHandlerContext ctx, Object packet, ChannelPromise promise) throws Exception {
			AbstractPacket nextPacket = getPacketManager().onPacketSent(PacketType.getType(packet.getClass().getSimpleName()), owner, packet);
			if(nextPacket != null && nextPacket.isCancelled())
				return;
			super.write(ctx, packet, promise);
		}
	}
}