package com.creativemd.playerrevive.server;

import com.creativemd.creativecore.common.gui.opener.GuiHandler;
import com.creativemd.playerrevive.CapaReviveProvider;
import com.creativemd.playerrevive.PlayerRevive;
import com.creativemd.playerrevive.api.DamageBledToDeath;
import com.creativemd.playerrevive.api.IRevival;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import net.minecraftforge.fml.common.gameevent.TickEvent.PlayerTickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.fml.server.FMLServerHandler;

public class ReviveEventServer {
	
	private static Boolean isClient = null;
	
	public static boolean isClient() {
		if (isClient == null) {
			try {
				isClient = Class.forName("net.minecraft.client.Minecraft") != null;
			} catch (ClassNotFoundException e) {
				isClient = false;
			}
		}
		return isClient;
	}
	
	public static boolean isReviveActive() {
		if (isClient())
			return !isSinglePlayer();
		return true;
	}
	
	@SideOnly(Side.CLIENT)
	private static boolean isSinglePlayer() {
		return Minecraft.getMinecraft().isSingleplayer() && !Minecraft.getMinecraft().getIntegratedServer().getPublic();
	}
	
	public static MinecraftServer getMinecraftServer() {
		if (isClient())
			return getMinecraftServerClient();
		return FMLServerHandler.instance().getServer();
	}
	
	@SideOnly(Side.CLIENT)
	private static MinecraftServer getMinecraftServerClient() {
		return Minecraft.getMinecraft().getIntegratedServer();
	}
	
	//private static MinecraftServer server = FMLServerHandler.instance().getServer();
	
	@SubscribeEvent
	public void playerTick(PlayerTickEvent event) {
		if (event.phase == Phase.START && event.side == Side.SERVER && isReviveActive()) {
			EntityPlayer player = event.player;
			if (player.isDead)
				return;
			IRevival revive = PlayerReviveServer.getRevival(player);
			
			if (!revive.isHealty()) {
				revive.tick();
				
				if (revive.getTimeLeft() % 20 == 0)
					PlayerReviveServer.sendUpdatePacket(player);
				
				player.getFoodStats().setFoodLevel(PlayerRevive.CONFIG.playerFoodAfter);
				player.setHealth(PlayerRevive.CONFIG.playerHealthAfter);
				player.capabilities.disableDamage = true;
				player.setEntityInvulnerable(true);
				
				if (revive.isRevived())
					PlayerReviveServer.revive(player);
				else if (revive.isDead())
					PlayerReviveServer.kill(player);
			}
		}
	}
	
	@SubscribeEvent
	public void playerLeave(PlayerLoggedOutEvent event) {
		IRevival revive = PlayerReviveServer.getRevival(event.player);
		if (!revive.isHealty())
			PlayerReviveServer.kill(event.player);
		if (!event.player.world.isRemote)
			PlayerReviveServer.removePlayerAsHelper(event.player);
	}
	
	/* @SubscribeEvent
	 * public void playerJoin(PlayerLoggedInEvent event)
	 * {
	 * if(!isReviveActive())
	 * return ;
	 * Revival revive = PlayerReviveServer.playerRevivals.get(EntityPlayer.getUUID(event.player.getGameProfile()));
	 * if(revive != null)
	 * PacketHandler.sendPacketToPlayer(new PlayerRevivalPacket(revive), (EntityPlayerMP) event.player);
	 * } */
	
	@SubscribeEvent(priority = EventPriority.HIGH)
	public void playerInteract(PlayerInteractEvent.EntityInteract event) {
		/*if (!PlayerReviveServer.isPlayerBleeding(event.getEntityPlayer()) && event.getTarget() instanceof EntityPlayer && !event.getEntityLiving().world.isRemote) {
			EntityPlayer player = (EntityPlayer) event.getTarget();
			IRevival revive = PlayerReviveServer.getRevival(player);
			if (!revive.isHealty()) {
				NBTTagCompound nbt = new NBTTagCompound();
				nbt.setString("uuid", EntityPlayer.getUUID(player.getGameProfile()).toString());
				revive.getRevivingPlayers().add(event.getEntityPlayer());
				GuiHandler.openGui("plreviver", nbt, event.getEntityPlayer());
				event.setCanceled(true);
			}
		}*/
	}
	
	@SubscribeEvent
	public void playerDamage(LivingHurtEvent event) {
		if (event.getEntityLiving() instanceof EntityPlayer) {
			EntityPlayer player = (EntityPlayer) event.getEntityLiving();
			IRevival revive = PlayerReviveServer.getRevival(player);
			if (!revive.isHealty() && ((event.getSource() != DamageBledToDeath.bledToDeath && !PlayerRevive.CONFIG.bypassDamageSources.contains(event.getSource().damageType)) || revive.isDead()))
				event.setCanceled(true);
		}
	}
	
	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public void playerDied(LivingDeathEvent event) {
		if (event.getEntityLiving() instanceof EntityPlayer && isReviveActive() && !event.getEntityLiving().world.isRemote && event.getSource() != DamageBledToDeath.bledToDeath && !PlayerRevive.CONFIG.bypassDamageSources.contains(event.getSource().damageType)) {
			EntityPlayer player = (EntityPlayer) event.getEntityLiving();
			IRevival revive = PlayerReviveServer.getRevival(player);
			
			if (revive.isDead()) {
				revive.stopBleeding();
				return;
			}
			
			PlayerReviveServer.startBleeding(player, event.getSource());
			player.capabilities.disableDamage = true;
			player.setEntityInvulnerable(true);
			
			if (player.isRiding())
				player.dismountRidingEntity();
			
			event.setCanceled(true);
			
			player.setHealth(0.5F);
			player.getFoodStats().setFoodLevel(1);
			
			if (!PlayerRevive.CONFIG.disableBleedingMessage)
				player.getServer().getPlayerList().sendMessage(new TextComponentTranslation("playerrevive.chat.bleeding", player.getDisplayNameString()));
		}
	}
	
	@SubscribeEvent
	public void attachCapability(AttachCapabilitiesEvent<Entity> event) {
		if (event.getObject() instanceof EntityPlayer) {
			event.addCapability(new ResourceLocation(PlayerRevive.modid, "revive"), new CapaReviveProvider());
		}
	}
	
}
