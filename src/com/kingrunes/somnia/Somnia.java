package com.kingrunes.somnia;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.Mod.Instance;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.network.FMLEventChannel;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.lwjgl.opengl.GL11;

import com.kingrunes.somnia.common.CommonProxy;
import com.kingrunes.somnia.common.PacketHandler;
import com.kingrunes.somnia.common.util.SomniaState;
import com.kingrunes.somnia.server.ServerTickHandler;
import com.kingrunes.somnia.server.SomniaCommand;

@Mod(modid = Somnia.MOD_ID, name = Somnia.NAME, version="-au")
public class Somnia
{
	public static final String MOD_ID = "Somnia";
	public static final String NAME = "Somnia";
	public static final String VERSION = SomniaVersion.getVersionString();
	
	public List<ServerTickHandler> tickHandlers;
	public List<WeakReference<EntityPlayerMP>> ignoreList;
	
	@Instance(Somnia.MOD_ID)
	public static Somnia instance;
	
	@SidedProxy(serverSide="com.kingrunes.somnia.common.CommonProxy", clientSide="com.kingrunes.somnia.client.ClientProxy")
	public static CommonProxy proxy;
	
	public static FMLEventChannel channel;
	
	public static long clientAutoWakeTime = -1;
	
	public Somnia()
	{
		this.tickHandlers = new ArrayList<ServerTickHandler>();
		this.ignoreList = new ArrayList<WeakReference<EntityPlayerMP>>();
	}
	
	@EventHandler
    public void preInit(FMLPreInitializationEvent event)
	{
		event.getModMetadata().version = VERSION;
        proxy.configure(event.getSuggestedConfigurationFile());
    }
	
	@EventHandler
	public void init(FMLInitializationEvent event) 
	{
		channel = NetworkRegistry.INSTANCE.newEventDrivenChannel(MOD_ID);
		channel.register(new PacketHandler());
		
		proxy.register();
	}
	
	@EventHandler
	public void onServerStarting(FMLServerStartingEvent event)
	{
		event.registerServerCommand(new SomniaCommand());
	}
	
	public static void tick()
	{
		synchronized (Somnia.instance.tickHandlers)
		{
			for (ServerTickHandler serverTickHandler : Somnia.instance.tickHandlers)
				serverTickHandler.tickStart();
		}
	}
	
	public static String timeStringForWorldTime(long time)
	{
		time += 6000; // Tick -> Time offset
		
		time = time % 24000;
		int hours = (int) Math.floor(time / (double)1000);
		int minutes = (int) ((time % 1000) / 1000.0d * 60);
		
		String lsHours = String.valueOf(hours);
		String lsMinutes = String.valueOf(minutes);
		
		if (lsHours.length() == 1)
			lsHours = "0"+lsHours;
		if (lsMinutes.length() == 1)
			lsMinutes = "0"+lsMinutes;
		
		return lsHours + ":" + lsMinutes;
	}

	public static boolean doesPlayHaveAnyArmor(EntityPlayer e)
	{
		ItemStack[] armor = e.inventory.armorInventory;
		for (int a=0; a<armor.length; a++)
		{
			if (armor[a] != null)
				return true;
		}
		return false;
	}

	public static long calculateWakeTime(long totalWorldTime, int i)
	{
		long l;
		long timeInDay = totalWorldTime % 24000l;
		l = totalWorldTime - timeInDay + i;
		if (timeInDay > i)
			l += 24000l;
		return l;
	}

	/*
	 * These methods are referenced by ASM generated bytecode
	 * 
	*/

	@SideOnly(Side.CLIENT)
	public static void renderWorld(float par1, long par2)
	{
		if (Minecraft.getMinecraft().thePlayer.isPlayerSleeping() && proxy.disableRendering)
		{
			GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
			return;
		}
			
		Minecraft.getMinecraft().entityRenderer.renderWorld(par1, par2);
	}
	
	public static boolean doMobSpawning(WorldServer par1WorldServer)
	{
		boolean defValue = par1WorldServer.getGameRules().getGameRuleBooleanValue("doMobSpawning");
		if (!proxy.disableCreatureSpawning || !defValue)
			return defValue;
		
		for (ServerTickHandler serverTickHandler : instance.tickHandlers)
		{
			if (serverTickHandler.worldServer == par1WorldServer)
				return serverTickHandler.currentState != SomniaState.ACTIVE;
		}
		
		throw new IllegalStateException("tickHandlers doesn't contain match for given world server");
	}
	
	public static void chunkLightCheck(Chunk chunk)
	{
		if (!proxy.disableMoodSoundAndLightCheck)
			chunk.func_150809_p();
		
		for (ServerTickHandler serverTickHandler : instance.tickHandlers)
		{
			if (serverTickHandler.worldServer == chunk.getWorld())
			{
				if (serverTickHandler.currentState != SomniaState.ACTIVE)
					chunk.func_150809_p();
				return;
			}
		}
		
		chunk.func_150809_p();
	}
}