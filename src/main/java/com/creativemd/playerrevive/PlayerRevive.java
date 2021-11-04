package com.creativemd.playerrevive;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.creativemd.creativecore.common.config.holder.CreativeConfigRegistry;
import com.creativemd.creativecore.common.gui.container.SubContainer;
import com.creativemd.creativecore.common.gui.container.SubGui;
import com.creativemd.creativecore.common.gui.opener.CustomGuiHandler;
import com.creativemd.creativecore.common.gui.opener.GuiHandler;
import com.creativemd.creativecore.common.packet.CreativeCorePacket;
import com.creativemd.playerrevive.api.IRevival;
import com.creativemd.playerrevive.api.capability.CapaRevive;
import com.creativemd.playerrevive.gui.SubContainerRevive;
import com.creativemd.playerrevive.gui.SubGuiRevive;
import com.creativemd.playerrevive.packet.ReviveUpdatePacket;
import com.creativemd.playerrevive.server.PlayerReviveServer;
import com.creativemd.playerrevive.server.ReviveEventServer;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@Mod(modid = PlayerRevive.modid, version = PlayerRevive.version, name = "Player Revive", acceptedMinecraftVersions = "", dependencies = "required-after:creativecore", guiFactory = "com.creativemd.playerrevive.PlayerReviveSettings")
@EventBusSubscriber
public class PlayerRevive {
	
	@SidedProxy(clientSide = "com.creativemd.playerrevive.client.PlayerReviveClient", serverSide = "com.creativemd.playerrevive.server.PlayerReviveServer")
	public static PlayerReviveServer proxy;
	
	public static SoundEvent deathSound = new SoundEvent(new ResourceLocation(PlayerRevive.modid, "death")).setRegistryName(new ResourceLocation(PlayerRevive.modid, "death"));
	public static SoundEvent revivedSound = new SoundEvent(new ResourceLocation(PlayerRevive.modid, "revived")).setRegistryName(new ResourceLocation(PlayerRevive.modid, "revived"));
	
	public static final String modid = "playerreviveatl";
	public static final String version = "1.0";
	
	public static PlayerReviveConfig CONFIG;
	
	@SubscribeEvent
	public static void registerSounds(RegistryEvent.Register<SoundEvent> event) {
		event.getRegistry().registerAll(deathSound, revivedSound);
	}
	
	@EventHandler
	public void serverStart(FMLServerStartingEvent event) {
		event.registerServerCommand(new CommandBase() {
			
			@Override
			public String getUsage(ICommandSender sender) {
				return "revive a bleeding player";
			}
			
			@Override
			public String getName() {
				return "revive";
			}
			
			@Override
			public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
				EntityPlayer player = null;
				if (args.length == 1) {
					player = server.getPlayerList().getPlayerByUsername(args[0]);
				} else if (sender instanceof EntityPlayer)
					player = (EntityPlayer) sender;
				
				if (player != null)
					PlayerReviveServer.revive(player);
			}
			
			@Override
			public boolean isUsernameIndex(String[] args, int index) {
				return index == 1;
			}
			
			@Override
			public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos) {
				if (args.length == 1) {
					return getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames());
				}
				return Collections.emptyList();
			}
		});
	}
	
	@EventHandler
	public void init(FMLInitializationEvent event) {
		CreativeCorePacket.registerPacket(ReviveUpdatePacket.class);
		
		GuiHandler.registerGuiHandler("plrevive", new CustomGuiHandler() {
			
			@Override
			@SideOnly(Side.CLIENT)
			public SubGui getGui(EntityPlayer player, NBTTagCompound nbt) {
				return new SubGuiRevive();
			}
			
			@Override
			public SubContainer getContainer(EntityPlayer player, NBTTagCompound nbt) {
				return new SubContainerRevive(player, PlayerReviveServer.getRevival(player), false);
			}
		});
		
		GuiHandler.registerGuiHandler("plreviver", new CustomGuiHandler() {
			
			@Override
			@SideOnly(Side.CLIENT)
			public SubGui getGui(EntityPlayer player, NBTTagCompound nbt) {
				return new SubGuiRevive();
			}
			
			@Override
			public SubContainer getContainer(EntityPlayer player, NBTTagCompound nbt) {
				IRevival revive = null;
				if (player.world.isRemote)
					revive = PlayerReviveServer.getRevival(player.world.getPlayerEntityByUUID(UUID.fromString(nbt.getString("uuid"))));
				else
					revive = PlayerReviveServer.getRevival(player.getServer().getPlayerList().getPlayerByUUID(UUID.fromString(nbt.getString("uuid"))));
				return new SubContainerRevive(player, revive, true);
			}
		});
		CapaRevive.register();
		CreativeConfigRegistry.ROOT.registerValue(modid, CONFIG = new PlayerReviveConfig());
		
		MinecraftForge.EVENT_BUS.register(new ReviveEventServer());
		
		proxy.loadSide();
	}
	
}
