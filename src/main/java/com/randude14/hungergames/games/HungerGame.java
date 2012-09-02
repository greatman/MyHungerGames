package com.randude14.hungergames.games;

import com.randude14.hungergames.GameManager;
import com.randude14.hungergames.*;
import com.randude14.hungergames.api.Game;
import static com.randude14.hungergames.api.Game.GameState.*;
import static com.randude14.hungergames.stats.PlayerStat.PlayerState;
import com.randude14.hungergames.reset.ResetHandler;
import com.randude14.hungergames.stats.PlayerStat;
import com.randude14.hungergames.api.event.*;
import com.randude14.hungergames.stats.StatHandler;
import com.randude14.hungergames.utils.ChatUtils;
import com.randude14.hungergames.utils.Cuboid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.HashSet;
import java.util.Set;

import org.bukkit.*;
import org.bukkit.block.Chest;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;

	
public class HungerGame implements Comparable<HungerGame>, Runnable, Game {
	// Per game
	private final Map<String, PlayerStat> stats;
	private final Map<String, Location> spawnsTaken;
	private final List<Location> randomLocs;
	private final Map<String, List<String>> sponsors; // Just a list for info, <sponsor, sponsee>
	private final SpectatorSponsoringRunnable spectatorSponsoringRunnable;
	private final List<Long> startTimes;
	private final List<Long> endTimes;
	private long initialStartTime;

	// Persistent
	private final Map<Location, Float> chests;
	private final Map<Location, String> fixedChests;
	private final List<Location> blacklistedChests;
	private final List<Location> spawnPoints;
	private final String name;
	private String setup;
	private final List<String> itemsets;
	private final Set<String> worlds;
	private final Set<Cuboid> cuboids;
	private Location spawn;
	private GameState state;

	
	// Temporary
	private final Map<String, Location> playerLocs;// For pausing
	private final Map<String, Location> spectators;
	private final Map<String, Boolean> spectatorFlying; // If a spectator was flying
	private final Map<String, Boolean> spectatorFlightAllowed; // If a spectator's flight was allowed
	private final Map<String, GameMode> playerGameModes; // Whether a player was in survival when game started
	private final List<String> readyToPlay;
	private GameCountdown countdown;
	private int locTaskId = 0;

	public HungerGame(String name) {
		this(name, null);
	}

	public HungerGame(final String name, final String setup) {
		stats = new TreeMap<String, PlayerStat>();
		spawnsTaken = new HashMap<String, Location>();
		sponsors = new HashMap<String, List<String>>();
		spectatorSponsoringRunnable = new SpectatorSponsoringRunnable(this);
		randomLocs = new ArrayList<Location>();
		startTimes = new ArrayList<Long>();
		endTimes = new ArrayList<Long>();
		initialStartTime = 0;
		
		chests = new HashMap<Location, Float>();
		fixedChests = new HashMap<Location, String>();
		blacklistedChests = new ArrayList<Location>();
		spawnPoints = new ArrayList<Location>();
		this.name = name;
		this.setup = null;
		itemsets = new ArrayList<String>();
		worlds = new HashSet<String>();
		cuboids = new HashSet<Cuboid>();
		spawn = null;
		state = GameState.STOPPED;

		readyToPlay = new ArrayList<String>();
		playerLocs = new HashMap<String, Location>();
		spectators = new HashMap<String, Location>();
		spectatorFlying = new HashMap<String, Boolean>();
		spectatorFlightAllowed = new HashMap<String, Boolean>();
		playerGameModes = new HashMap<String, GameMode>();
		countdown = null;
	}

	public void loadFrom(ConfigurationSection section) {
		spawnPoints.clear();
		chests.clear();
		fixedChests.clear();
		itemsets.clear();
		worlds.clear();
		cuboids.clear();
		if (section.contains("spawn-points")) {
			ConfigurationSection spawnPointsSection = section.getConfigurationSection("spawn-points");
			for (String key : spawnPointsSection.getKeys(false)) {
				String str = spawnPointsSection.getString(key);
				Location loc = null;
				try {
					loc = HungerGames.parseToLoc(str);
				}
				catch (NumberFormatException e) {}
				if (loc == null) {
					Logging.warning("failed to load location '%s'", str);
					continue;
				}
				spawnPoints.add(loc);
			}

		}

		if (section.contains("chests")) {
			ConfigurationSection chestsSection = section.getConfigurationSection("chests");
			for (String key : chestsSection.getKeys(false)) {
				String[] parts = chestsSection.getString(key).split(",");
				Location loc = null;
				float weight = 1f;
				try {
					loc = HungerGames.parseToLoc(parts[0]);
					weight = Float.parseFloat(parts[1]);
				}
				catch (NumberFormatException e) {}
				catch (IndexOutOfBoundsException e) {}
				if (loc == null || loc.getWorld() == null) {
					Logging.warning("failed to load location '%s'", parts[0]);
					continue;
				}
				if (!(loc.getBlock().getState() instanceof Chest)) {
					Logging.warning("'%s' is no longer a chest.", parts[0]);
					continue;
				}
				chests.put(loc, weight);
			}

		}

		if (section.contains("fixedchests")) {
			ConfigurationSection fixedChestsSection = section.getConfigurationSection("fixedchests");
			for (String key : fixedChestsSection.getKeys(false)) {
				String str = fixedChestsSection.getString(key);
				String[] split = str.split(",");
				if (split.length != 2) continue;
				Location loc = null;
				try {
					loc = HungerGames.parseToLoc(split[0]);
				}
				catch (NumberFormatException e) {
				}
				if (loc == null) {
					Logging.warning("failed to load location '%s'", str);
					continue;
				}
				if (!(loc.getBlock().getState() instanceof Chest)) {
					Logging.warning("'%s' is no longer a chest.", str);
					continue;
				}
				fixedChests.put(loc, split[1]);
			}

		}
                
                if(section.isList("itemsets")) {
			itemsets.addAll(section.getStringList("itemsets"));
                }
		
                if(section.isList("worlds")) {
			worlds.addAll(section.getStringList("worlds"));
                }
		if (section.isList("cuboids")) {
			List<Cuboid> cuboidList = new ArrayList<Cuboid>();
			for (String s : section.getStringList("cuboids")) {
				cuboidList.add(Cuboid.parseFromString(s));
			}
			cuboids.addAll(cuboidList);
		}
		setEnabled(section.getBoolean("enabled", true));
		if (section.contains("setup")) setup = section.getString("setup");
		try {
			if (section.contains("spawn")) spawn = HungerGames.parseToLoc(section.getString("spawn"));
		} 
		catch (NumberFormatException numberFormatException) {}
		HungerGames.callEvent(new GameLoadEvent(this));
	}

	public void saveTo(ConfigurationSection section) {
		ConfigurationSection spawnPointsSection = section.createSection("spawn-points");
		ConfigurationSection chestsSection = section.createSection("chests");
		ConfigurationSection fixedChestsSection = section.createSection("fixedchests");
		int cntr;
		
		for (cntr = 0; cntr < spawnPoints.size(); cntr++) {
			Location loc = spawnPoints.get(cntr);
			if (loc == null) continue;
			//Logging.debug("Saving a spawnpoint. It's location is: " + loc);
			spawnPointsSection.set("spawnpoint" + (cntr + 1), HungerGames.parseToString(loc));
		}
		cntr = 1;
		for (Location loc : chests.keySet()) {
			cntr++;
			chestsSection.set("chest" + cntr, HungerGames.parseToString(loc) + "," + chests.get(loc));
		}
		
		cntr = 1;
		for (Location loc : fixedChests.keySet()) {
			fixedChestsSection.set("fixedchest" + cntr, HungerGames.parseToString(loc) + "," + fixedChests.get(loc));
			cntr++;
		}
		section.set("itemsets", itemsets);
		if (!worlds.isEmpty()) {
			section.set("worlds", new ArrayList<String>(worlds));
		}
		List<String> cuboidStringList = new ArrayList<String>();
		for (Cuboid c : cuboids) {
			cuboidStringList.add(c.parseToString());
		}
		if (!cuboidStringList.isEmpty()) {
			section.set("cuboids", cuboidStringList);
		}
		section.set("enabled", state != DISABLED);
		section.set("setup", setup);
		section.set("spawn", HungerGames.parseToString(spawn));
		
		HungerGames.callEvent(new GameSaveEvent(this));
	}

	public void run() {
		if (state != RUNNING) return;
		Random rand = HungerGames.getRandom();
		Location loc = getRemainingPlayers().get(rand.nextInt(getRemainingPlayers().size())).getLocation();
		if (randomLocs.size() >= 15) randomLocs.remove(rand.nextInt(15));
		randomLocs.add(loc);
	}
	
	public int compareTo(HungerGame game) {
		return game.name.compareToIgnoreCase(name);
	}

	public boolean addReadyPlayer(Player player) {
		if (state == DELETED) {
			ChatUtils.error(player, "That game does not exist anymore.");
			return false;
		}
		if (readyToPlay.contains(player.getName())) {
			ChatUtils.error(player, "You have already cast your vote that you are ready to play.");
			return false;
		}
		if (state == COUNTING_FOR_RESUME || state == COUNTING_FOR_START) {
			ChatUtils.error(player, Lang.getAlreadyCountingDown(setup).replace("<game>", name));
			return false;
		}
		if (state == RUNNING) {
			ChatUtils.error(player, Lang.getRunning(setup).replace("<game>", name));
			return false;
		}
		if(state == PAUSED) {
			ChatUtils.error(player, "%s has been paused.", name);
			return false;
		}
		readyToPlay.add(player.getName());
		String mess = Lang.getVoteMessage(setup).replace("<player>", player.getName()).replace("<game>", this.name);
		ChatUtils.broadcast(mess, true);
		int minVote = Config.getMinVote(setup);
		int minPlayers = Config.getMinPlayers(setup);
		int startTimer = Config.getStartTimer(setup);
		int ready = readyToPlay.size();
		int joined = stats.size();
		boolean allVote = Config.getAllVote(setup);
		boolean autoVote = Config.getAutoVote(setup);
		if (joined >= minPlayers) {
			if ((ready >= minVote && !allVote) || (ready >= joined && allVote && !autoVote)) {
				ChatUtils.broadcast(true, "Enough players have voted that they are ready. Starting game...", this.name);
				startGame(false);
			}
			else if (startTimer > 0) {
				ChatUtils.broadcast(true, "The minimum amount of players for this game has been reached. Countdown has begun...", this.name);
				startGame(startTimer);
			}
		}
		return true;
	}
	
	public void clearWaitingPlayers() {
		for (Iterator<String> it = stats.keySet().iterator(); it.hasNext();) {
			String stat = it.next();
			if (!stats.get(stat).getState().equals(PlayerState.WAITING)) continue;
			stats.get(stat).setState(PlayerState.NOT_IN_GAME);
			Player player = Bukkit.getPlayer(stat);
			ItemStack[] contents = player.getInventory().getContents();
			List<ItemStack> list = new ArrayList<ItemStack>();
			for (ItemStack i : contents) {
				if (i != null) list.add(i);
			}
			contents = list.toArray(new ItemStack[list.size()]);
			playerLeaving(player, false);
			for (ItemStack i : contents) player.getLocation().getWorld().dropItem(player.getLocation(), i);
			teleportPlayerToSpawn(player);
			GameManager.INSTANCE.clearGamesForPlayer(stat, this);
			stats.remove(stat);
		}
	}

	public boolean addSpectator(Player player, Player spectated) {
		if (state != RUNNING) {
			ChatUtils.error(player, Lang.getNotRunning(setup).replace("<game>", name));
			return false;
		}
		spectators.put(player.getName(), player.getLocation());
		if (Config.getSpectatorSponsorPeriod(setup) != 0) {
			 spectatorSponsoringRunnable.addSpectator(player);
		}
		Random rand = HungerGames.getRandom();
		Location loc = randomLocs.get(rand.nextInt(randomLocs.size()));
		if (spectated != null) loc = spectated.getLocation();
		player.teleport(loc);
		spectatorFlying.put(player.getName(), player.isFlying());
		spectatorFlightAllowed.put(player.getName(), player.getAllowFlight());
		player.setAllowFlight(true);
		player.setFlying(true);
		for (Player p : getRemainingPlayers()) {
			p.hidePlayer(player);
		}
		ChatUtils.send(player, "You are now spectating %s", name);
		return true;
	}

	@Override
	public boolean isSpectating(Player player) {
		return spectators.containsKey(player.getName());
	}

	public void removeSpectator(Player player) {
		if (!spectators.containsKey(player.getName())) {
			ChatUtils.error(player, "You are not spectating that game.");
			return;
		}
		spectatorSponsoringRunnable.removeSpectator(player);
		player.setFlying(spectatorFlying.get(player.getName()));
		player.setAllowFlight(spectatorFlightAllowed.get(player.getName()));
		player.teleport(spectators.remove(player.getName()));
		for (Player p : getRemainingPlayers()) {
			p.showPlayer(player);
		}
	}
	
	@Override
	public boolean stopGame(Player player, boolean isFinished) {
		String result = stopGame(isFinished);
		if (result != null && player != null) {
			ChatUtils.error(player, result);
			return false;
		}
		return true;
	}
	
	@Override
	public String stopGame(boolean isFinished) {
		if (state == DELETED) return "That game does not exist anymore.";
		clearWaitingPlayers();
		if (state != RUNNING && state != PAUSED && state != COUNTING_FOR_RESUME && state != COUNTING_FOR_START) return "Game is not started";
		
		endTimes.add(System.currentTimeMillis());
		if (countdown != null) countdown.cancel();
		if (state == PAUSED) { // Needed for inventory stuff
			for(String playerName : playerLocs.keySet()) {
				Player p = Bukkit.getPlayer(playerName);
				if (p == null) continue;
				playerEntering(p, true);
				InventorySave.loadGameInventory(p);
			}
		}
		StatHandler.updateGame(this);
		for (Player player : getRemainingPlayers()) {
			stats.get(player.getName()).setState(PlayerState.NOT_IN_GAME);
			ItemStack[] contents = player.getInventory().getContents();
			List<ItemStack> list = new ArrayList<ItemStack>();
			for (ItemStack i : contents) {
				if (i != null) list.add(i);
			}
			contents = list.toArray(new ItemStack[list.size()]);
			playerLeaving(player, false);
			if (isFinished && Config.getWinnerKeepsItems(setup)) {
				for (ItemStack i : player.getInventory().addItem(contents).values()) {
					player.getLocation().getWorld().dropItem(player.getLocation(), i);
				}
			}
			else {
				for (ItemStack i : contents) player.getLocation().getWorld().dropItem(player.getLocation(), i);
			}
			teleportPlayerToSpawn(player);
			if (isFinished) HungerGames.rewardPlayer(player);
		}
		for (String stat : stats.keySet()) {
			StatHandler.updateStat(stats.get(stat));// TODO: this might be a little slow to do it this way. Thread?
			GameManager.INSTANCE.clearGamesForPlayer(stat, this);
		}
		stats.clear();
		for (String spectatorName : spectators.keySet()) {
			Player spectator = Bukkit.getPlayer(spectatorName);
			if (spectator == null) continue;
			removeSpectator(spectator);
		}
		spectatorSponsoringRunnable.cancel();
		HungerGames.cancelTask(locTaskId);
		if (Config.getRemoveItems(setup)) removeItemsOnGround();
		state = STOPPED;
		if (!isFinished) {
			GameEndEvent event = new GameEndEvent(this);
			HungerGames.callEvent(event);
		}
		clear();
		ResetHandler.resetChanges(this);
		return null;
	}

	@Override
	public boolean startGame(Player player, int ticks) {
		String result = startGame(0);
		if (result != null) {
			ChatUtils.error(player, result);
			return false;
		}
		return true;
	}

	@Override
	public boolean startGame(Player player, boolean immediate) {
		if(!immediate) return startGame(player, Config.getDefaultTime(setup));
		return startGame(player, 0);
	}

	@Override
	public boolean startGame(boolean immediate) {
		if(!immediate) return startGame(Config.getDefaultTime(setup)) == null;
		return startGame(0) == null;
	}

	@Override
	public String startGame(int ticks) {
		if (state == DELETED) return "Game no longer exists.";
		if (state == DISABLED) return Lang.getNotEnabled(setup).replace("<game>", name);
		if (state == RUNNING) return Lang.getRunning(setup).replace("<game>", name);
		if (countdown != null) {
			if (ticks < countdown.getTimeLeft()) {
				countdown.cancel();
				countdown = null;
			}
			else {
				return Lang.getAlreadyCountingDown(setup).replace("<game>", name);
			}
		}
		if (ticks > 0) {
			countdown = new GameCountdown(this, ticks);
			state = COUNTING_FOR_START;
			return null;
		}
		if (stats.size() < Config.getMinPlayers(setup)) return String.format("There are not enough players in %s", name);
		if (stats.size() < 2) ChatUtils.broadcast(true, "%s is being started with only one player. This has a high potential to lead to errors.", name);
		initialStartTime = System.currentTimeMillis();
		startTimes.add(System.currentTimeMillis());
		GameStartEvent event = new GameStartEvent(this);
		HungerGames.callEvent(event);
		if (event.isCancelled()) {
			return "Start was cancelled.";
		}
		locTaskId = HungerGames.scheduleTask(this, 20 * 120, 20 * 10); // Wait two minutes, then poll every 10 seconds
		spectatorSponsoringRunnable.setTaskId(HungerGames.scheduleTask(spectatorSponsoringRunnable, 0, SpectatorSponsoringRunnable.pollEveryInTicks));
		ResetHandler.gameStarting(this);
		releasePlayers();
		fillInventories();
		for (String playerName : stats.keySet()) {
			Player p = Bukkit.getPlayer(playerName);
			if (p == null) continue;
			World world = p.getWorld();
			world.setFullTime(0L);
			p.setHealth(20);
			p.setFoodLevel(20);
			stats.get(playerName).setState(PlayerStat.PlayerState.PLAYING);
		}
		state = RUNNING;
		run(); // Add at least one randomLoc
		readyToPlay.clear();
		ChatUtils.broadcast(true, "Starting %s. Go!!", name);
		return null;
	}

	@Override
 	public boolean resumeGame(Player player, int ticks) {		
		if (ticks <= 0) {
			String result = resumeGame(0);
			if (result != null) {
				ChatUtils.error(player, result);
				return false;
			}
		} else {
			countdown = new GameCountdown(this, ticks, true);
			state = COUNTING_FOR_RESUME;
		}
		return true;
	}
	
	@Override
	public boolean resumeGame(Player player, boolean immediate) {
		if (!immediate) return resumeGame(player, Config.getDefaultTime(setup));
		return resumeGame(player, 0);
	}
	
	@Override
	public boolean resumeGame(boolean immediate) {
		if (!immediate) return resumeGame(Config.getDefaultTime(setup)) == null;
		return resumeGame(0) == null;
	}

	@Override
	public String resumeGame(int ticks) {
		if (state == DELETED) return "That game does not exist anymore.";
		if(state != PAUSED && state != ABOUT_TO_START) return "Cannot resume a game that has not been paused.";
		if (ticks > 0) {
			countdown = new GameCountdown(this, ticks, true);
			state = COUNTING_FOR_RESUME;
			return null;
		}
		startTimes.add(System.currentTimeMillis());
		GameStartEvent event = new GameStartEvent(this, true);
		HungerGames.callEvent(event);
		if (event.isCancelled()) {
			return "Start was cancelled.";
		}
		for(String playerName : playerLocs.keySet()) {
			Player p = Bukkit.getPlayer(playerName);
			if (p == null) continue;
			stats.get(p.getName()).setState(PlayerState.PLAYING);
			playerEntering(p, true);
			InventorySave.loadGameInventory(p);
			World world = p.getWorld();
			world.setFullTime(0L);
			p.setHealth(20);
			p.setFoodLevel(20);
		}
		state = RUNNING;
		countdown = null;
		ChatUtils.broadcast(true, "Resuming %s. Go!!", name);
		return null;
	}
	
	@Override
	public boolean pauseGame(Player player) {
		String result = pauseGame();
		if (result != null) {
			ChatUtils.error(player, "Cannot pause a game that has been paused.");
			return false;
		}
		return true;
	}
	
	/**
	 * 
	 * @return null if successful, message if not
	 */
	@Override
	public String pauseGame() {
		if (state == DELETED) return "That game does not exist anymore.";
		if(state == PAUSED) return "Cannot pause a game that has been paused.";
		
		state = PAUSED;
		endTimes.add(System.currentTimeMillis());
		if(countdown != null) {
			countdown.cancel();
			countdown = null;
		}
		for(Player p : getRemainingPlayers()) {
			if (p == null) continue;
			stats.get(p.getName()).setState(PlayerState.GAME_PAUSED);
			playerLocs.put(p.getName(), p.getLocation());
			InventorySave.saveAndClearGameInventory(p);
			playerLeaving(p, true);
			teleportPlayerToSpawn(p);
		}
		for (String spectatorName : spectators.keySet()) {
			Player spectator = Bukkit.getPlayer(spectatorName);
			removeSpectator(spectator);
		}
		HungerGames.callEvent(new GamePauseEvent(this));
		return null;
	}
	
	private void releasePlayers() {
		for (String playerName : stats.keySet()) {
			Player p = Bukkit.getPlayer(playerName);
			if (p == null) continue;
			GameManager.INSTANCE.unfreezePlayer(p);
		}

	}

	@Override
	public void addAndFillChest(Chest chest) {
		if (fixedChests.containsKey(chest.getLocation())) return;
		if(!chests.keySet().contains(chest.getLocation()) && !blacklistedChests.contains(chest.getLocation())) {
			//Logging.debug("Inventory Location was not in randomInvs.");
			HungerGames.fillChest(chest, 0, itemsets);
			addChest(chest.getLocation(), 1f);
		}
	}
        
	@Override
	public void fillInventories() {
	    Location prev = null;
	    // Logging.debug("Filling inventories. Chests size: %s fixedChests size: %s", chests.size(), fixedChests.size());
	    for (Location loc : chests.keySet()) {
		    if (prev != null && prev.getBlock().getFace(loc.getBlock()) != null) {
			    //Logging.debug("Cancelling a fill because previous was a chest");
			    continue;
		    }
		    if (!(loc.getBlock().getState() instanceof Chest)) {
			    //Logging.debug("Cancelling a fill because not a chest");
			    continue;
		    }
		    prev = loc;
		    Chest chest = (Chest) loc.getBlock().getState();
		    HungerGames.fillChest(chest, chests.get(loc), itemsets);
	    }
	    for (Location loc : fixedChests.keySet()) {
		    if (prev != null && prev.getBlock().getFace(loc.getBlock()) != null) {
			    //Logging.debug("Cancelling a fill because previous was a chest");
			    continue;
		    }
		    if (!(loc.getBlock().getState() instanceof Chest)) {
			    //Logging.debug("Cancelling a fill because not a chest");
			    continue;
		    }
		    prev = loc;
		    Chest chest = (Chest) loc.getBlock().getState();
		    HungerGames.fillFixedChest(chest, fixedChests.get(loc));   
	    }

	}

	@Override
	public synchronized boolean rejoin(Player player) {
		if (state != RUNNING) {
			ChatUtils.error(player, Lang.getNotRunning(setup).replace("<game>", name));
			return false;
		}
		if(!playerEnteringPreCheck(player)) return false;
		if (!Config.getAllowRejoin(setup)) {
			ChatUtils.error(player, "You are not allowed to rejoin a game.");
			return false;
		}
		if (stats.get(player.getName()).getState() == PlayerState.PLAYING){
			ChatUtils.error(player, "You can't rejoin a game while you are in it.");
			return false;
		}
		if (!stats.containsKey(player.getName()) || stats.get(player.getName()).getState() != PlayerState.NOT_PLAYING) {
			ChatUtils.error(player, Lang.getNotInGame(setup).replace("<game>", name));
			return false;
		}
		PlayerJoinGameEvent event = new PlayerJoinGameEvent(this, player, true);
		HungerGames.callEvent(event);
		if (event.isCancelled()) return false;
		if (!playerEntering(player, false)) return false;
		stats.get(player.getName()).setState(PlayerState.PLAYING);
		
		String mess = Lang.getRejoinMessage(setup);
		mess = mess.replace("<player>", player.getName()).replace("<game>", name);
		ChatUtils.broadcast(mess, true);
		return true;
	}

	@Override
	public synchronized boolean join(Player player) {
	    if (GameManager.INSTANCE.getSession(player) != null) {
		    ChatUtils.error(player, "You are already in a game. Leave that game before joining another.");
		    return false;
	    }
	    if (stats.containsKey(player.getName())) {
		    ChatUtils.error(player, Lang.getInGame(setup).replace("<game>", name));
		    return false;
	    }
	    if (!playerEnteringPreCheck(player)) return false;
	    if (state == RUNNING && !Config.getAllowJoinWhileRunning(setup)) {
		    ChatUtils.error(player, Lang.getRunning(setup).replace("<game>", name));
		    return false;
	    }
	    if(state == PAUSED) {
		    ChatUtils.error(player, "%s has been paused.", name);
		    return false;
	    }
	    PlayerJoinGameEvent event = new PlayerJoinGameEvent(this, player);
	    HungerGames.callEvent(event);
	    if (event.isCancelled()) return false;
	    if(!playerEntering(player, false)) return false;
	    stats.put(player.getName(), GameManager.INSTANCE.createStat(this, player));
	    String mess = Lang.getJoinMessage(setup);
	    mess = mess.replace("<player>", player.getName()).replace("<game>", name);
	    ChatUtils.broadcast(mess, true);
	    if (state == RUNNING) {
		    stats.get(player.getName()).setState(PlayerState.PLAYING);
	    }
	    else {
		    stats.get(player.getName()).setState(PlayerState.WAITING);
		    if (Config.getAutoVote(setup)) addReadyPlayer(player);
	    }
	    return true;
	}

	private synchronized boolean playerEnteringPreCheck(Player player) {
	    if (state == DELETED) {
		    ChatUtils.error(player, "That game does not exist anymore.");
		    return false;
	    }
	    if (state == DISABLED) {
		    ChatUtils.error(player, Lang.getNotEnabled(setup).replace("<game>", name));
		    return false;
	    }

	    if (spawnsTaken.size() >= spawnPoints.size()) {
		    ChatUtils.error(player, "%s is already full.", name);
		    return false;
	    }

	    if (Config.getRequireInvClear(setup)) {
		    if(!HungerGames.hasInventoryBeenCleared(player)) {
			    ChatUtils.error(player, "You must clear your inventory first (Be sure you're not wearing armor either).");
			    return false;
		    }
	    }
	    return true;
	}

	/**
	 * When a player enters the game. Does not handle stats.
	 * This handles the teleporting.
	 * @param player
	 * @param fromTemporary if the player leaving was temporary. Leave is not temporary.
	 * @return
	 */
	private synchronized boolean playerEntering(Player player, boolean fromTemporary) {
	    Location loc;
	    if (!fromTemporary) {
		    loc = getNextOpenSpawnPoint();
		    spawnsTaken.put(player.getName(), loc);
	    }
	    else {
		    loc = spawnsTaken.get(player.getName());
	    }
	    GameManager.INSTANCE.addSubscribedPlayer(player);
	    GameManager.INSTANCE.addBackLocation(player);
	    player.teleport(loc, TeleportCause.PLUGIN);
	    if (state != RUNNING && Config.getFreezePlayers(setup)) GameManager.INSTANCE.freezePlayer(player);
	    if (Config.getForceSurvival(setup)) {
		    playerGameModes.put(player.getName(), player.getGameMode());
		    player.setGameMode(GameMode.SURVIVAL);
	    }
	    if(Config.getClearInv(setup)) InventorySave.saveAndClearInventory(player);
	    for (String string : spectators.keySet()) {
		    Player spectator = Bukkit.getPlayer(string);
		    if (spectator == null) continue;
		    player.hidePlayer(spectator);
	    }
	    return true;
	}
	
	public Location getNextOpenSpawnPoint() {
		Random rand = HungerGames.getRandom();
		Location loc;
		do {
			loc = spawnPoints.get(rand.nextInt(spawnPoints.size()));
			if (loc == null) spawnPoints.remove(loc);
			
		} while (loc == null || spawnsTaken.containsValue(loc));
		return loc;
	}
	
	@Override
	public synchronized boolean leave(Player player, boolean callEvent) {
		if (state != RUNNING && state != PAUSED) return quit(player, true);
		
		if (!isPlaying(player)) {
			ChatUtils.error(player, "You are not playing the game %s.", name);
			return false;
		}

		if (callEvent) HungerGames.callEvent(new PlayerLeaveGameEvent(this, player, PlayerLeaveGameEvent.Type.LEAVE));
		if (!Config.getAllowRejoin(setup)) {
			stats.get(player.getName()).die();
		}
		else {
			stats.get(player.getName()).setState(PlayerState.NOT_PLAYING);
			stats.get(player.getName()).death(PlayerStat.NODODY);
		}
		playerEntering(player, true);
		InventorySave.loadGameInventory(player);
		dropInventory(player);
		playerLeaving(player, false);
		teleportPlayerToSpawn(player);
		String mess = Lang.getLeaveMessage(setup);
		mess = mess.replace("<player>", player.getName()).replace("<game>", name);
		ChatUtils.broadcast(mess, true);
		checkForGameOver(false);

		return true;
	}
	
	@Override
	public synchronized boolean quit(Player player, boolean callEvent) {
	    if (!contains(player)) {
		    ChatUtils.error(player, Lang.getNotInGame(setup).replace("<game>", name));
		    return false;
	    }
	    if (callEvent) HungerGames.callEvent(new PlayerLeaveGameEvent(this, player, PlayerLeaveGameEvent.Type.QUIT));
	    boolean wasPlaying = stats.get(player.getName()).getState() == PlayerState.PLAYING || stats.get(player.getName()).getState() == PlayerState.WAITING;
	    if (wasPlaying) {
		    dropInventory(player);
	    }
	    if(state == RUNNING) {
		    stats.get(player.getName()).die();
	    }
	    else {
		    stats.remove(player.getName());
		    GameManager.INSTANCE.clearGamesForPlayer(player.getName(), this);
	    }
	    playerLeaving(player, false);
	    if (wasPlaying || state != RUNNING) {
		    teleportPlayerToSpawn(player);
	    }
	    
	    String mess = Lang.getQuitMessage(setup);
	    mess = mess.replace("<player>", player.getName()).replace("<game>", name);
	    ChatUtils.broadcast(mess, true);
	    checkForGameOver(false);
	    return true;
	}
	
	/**
	 * Used when a player is exiting.
	 * This does not handle teleporting and should be used before the teleport.
	 * @param player
	 */
	private synchronized void playerLeaving(Player player, boolean temporary) {
		for (String string : spectators.keySet()) {
		    Player spectator = Bukkit.getPlayer(string);
		    if (spectator == null) continue;
		    player.showPlayer(spectator);
		}
		GameManager.INSTANCE.unfreezePlayer(player);
		InventorySave.loadInventory(player);
		if (playerGameModes.containsKey(player.getName())) {
			player.setGameMode(playerGameModes.remove(player.getName()));
		}
		readyToPlay.remove(player.getName());
		if (!temporary) {
			spawnsTaken.remove(player.getName());
			PlayerQueueHandler.addPlayer(player);
		}
	}

	// Complete clear just to be sure
	public void clear() {
		releasePlayers();
		stats.clear();
		spawnsTaken.clear();
		spectators.clear();
		sponsors.clear();
		randomLocs.clear();
		
		readyToPlay.clear();
		playerLocs.clear();
		spectatorFlying.clear();
		spectatorFlightAllowed.clear();
		playerGameModes.clear();
		if (countdown != null) countdown.cancel(); 
		countdown = null;
	}

	@Override
	public void teleportPlayerToSpawn(Player player) {
		if (player == null) {
			return;
		}
		if (Config.getUseSpawn(setup)) {
			if (spawn != null) {
				player.teleport(spawn);
				return;
			}
			else {
				ChatUtils.error(player, "There was no spawn set for %s. Teleporting to back location.", name);
			}
		}
		Location loc = GameManager.INSTANCE.getAndRemoveBackLocation(player);
		if (loc != null) {
			player.teleport(loc);
		}
		else {
			ChatUtils.error(player, "For some reason, there was no back location. Please contact an admin for help.", name);
			player.teleport(player.getWorld().getSpawnLocation());
		}
	}

	@Override
	public boolean checkForGameOver(boolean notifyOfRemaining) {// TODO config option
		if (state != RUNNING) return false;
		List<Player> remaining = getRemainingPlayers();
		if (remaining.size() < 2) {
			Player winner = null;
			if (!remaining.isEmpty()) {
				winner = remaining.get(0);
			}
			GameEndEvent event;
			if (winner == null) {
				ChatUtils.broadcast(Lang.getNoWinner(setup), true);
				event = new GameEndEvent(this, null);
			} else {
				ChatUtils.broadcast(true, Lang.getWin(setup).replace("<player>", winner.getName()).replace("<game>", name));
				ChatUtils.send(winner, "Congratulations! You won!");// TODO message
				event = new GameEndEvent(this, winner);
			}
			HungerGames.callEvent(event);
			stopGame(true);
			return true;
		}

	    if (!notifyOfRemaining) return false;
	    String mess = "Remaining players: ";
	    for (int cntr = 0; cntr < remaining.size(); cntr++) {
		    mess += remaining.get(cntr).getName();
		    if (cntr < remaining.size() - 1) {
			    mess += ", ";
		    }

	    }
	    ChatUtils.broadcastRaw(mess, ChatColor.WHITE, true);
	    return false;
	}

	@Override
	public String getInfo() {
		return String.format("%s[%d/%d] Enabled: %b", name, spawnsTaken.size(), spawnPoints.size(), state != DISABLED);
	}

	@Override
	public boolean contains(Player... players) {
	    if (state == DELETED) return false;
	    for (Player player : players) {
		if (!stats.containsKey(player.getName())) return false;
		PlayerState pState = stats.get(player.getName()).getState();
		if (pState == PlayerState.NOT_IN_GAME || pState == PlayerState.DEAD) return false;
	    }
	    return true;
	}
	
	@Override
	public boolean isPlaying(Player... players) {
	    for (Player player : players) {
		if (state != RUNNING || !stats.containsKey(player.getName()) 
			|| stats.get(player.getName()).getState() != PlayerState.PLAYING ){
		    return false;
		}
	    }
	    return true;
	}

	
	public void killed(final Player killer, final Player killed, PlayerDeathEvent deathEvent) {
		if (state == DELETED || state != RUNNING || stats.get(killed.getName()).getState() != PlayerState.PLAYING) return;

		deathEvent.setDeathMessage(null);
		killed.setHealth(20);
		killed.setFoodLevel(20);
		PlayerStat killedStat = stats.get(killed.getName());
		PlayerKillEvent event;
		if (killer != null) {
			PlayerStat killerStat = stats.get(killer.getName());
			killerStat.kill(killed.getName());
			String message = Lang.getKillMessage(setup).replace("<killer>", killer.getName()).replace("<killed>", killed.getName()).replace("<game>", name);
			event = new PlayerKillEvent(this, killer, killed, message);
			ChatUtils.broadcast(message, true);
			killedStat.death(killer.getName());
		}
		else {
			event = new PlayerKillEvent(this, killed);
			killedStat.death(PlayerStat.NODODY);
		}
		HungerGames.callEvent(event);
		if (killedStat.getState() == PlayerState.DEAD) {
			playerLeaving(killed, false);
			final ItemStack[] armor = killed.getInventory().getArmorContents();
			final ItemStack[] inventory = killed.getInventory().getContents();
			Bukkit.getScheduler().scheduleSyncDelayedTask(HungerGames.getInstance(), new Runnable() {
				@Override
				public void run() {
					killed.getInventory().setArmorContents(armor);
					killed.getInventory().setContents(inventory);
				}

			});
			for (ItemStack i : deathEvent.getDrops()) {
				killed.getWorld().dropItemNaturally(killed.getLocation(), i);
			}
			deathEvent.getDrops().clear();
			teleportPlayerToSpawn(killed);
			checkForGameOver(false);
			if (Config.getDeathCannon(setup) == 1 || Config.getDeathCannon(setup) == 2) playCannonBoom();
		}
		else {
			if (Config.shouldRespawnAtSpawnPoint(setup)) {
				Location respawn = spawnsTaken.get(killed.getName());
				killed.teleport(respawn, TeleportCause.PLUGIN);
			}
			else {
				Location respawn = randomLocs.get(HungerGames.getRandom().nextInt(randomLocs.size()));
				killed.teleport(respawn, TeleportCause.PLUGIN);
			}
			ChatUtils.send(killed, "You have " + killedStat.getLivesLeft() + " lives left.");
			if (Config.getDeathCannon(setup) == 1) playCannonBoom();
		}
	}

	@Override
	public List<Player> getRemainingPlayers() {
	    List<Player> remaining = new ArrayList<Player>();
	    for (String playerName : stats.keySet()) {
		Player player = Bukkit.getPlayer(playerName);
		if (player == null) continue;
		PlayerStat stat = stats.get(playerName);
		if (stat.getState() == PlayerState.PLAYING || stat.getState() == PlayerState.GAME_PAUSED || stat.getState() == PlayerState.WAITING) {
		    remaining.add(player);
		}
	    }
	    return remaining;
	}

	@Override
	public PlayerStat getPlayerStat(OfflinePlayer player) {
		return stats.get(player.getName());
	}

	@Override
	public void listStats(Player player) {
		int living = 0, dead = 0;
		List<String> players = new ArrayList<String>(stats.keySet());
		String mess = "";
		for (int cntr = 0; cntr < players.size(); cntr++) {
			PlayerStat stat = stats.get(players.get(cntr));
			Player p = stat.getPlayer();
			if (p == null) continue;
			String statName;
			if (stat.getState() == PlayerState.DEAD) {
				statName = ChatColor.RED.toString() + p.getName() + ChatColor.GRAY.toString();
				dead++;
			}
			else if (stat.getState() == PlayerState.NOT_PLAYING) {
				statName = ChatColor.YELLOW.toString() + p.getName() + ChatColor.GRAY.toString();
				dead++;
			}
			else {
				statName = ChatColor.GREEN.toString() + p.getName() + ChatColor.GRAY.toString();
				living++;
			}
			mess += String.format("%s [%d/%d]", statName, stat.getLivesLeft(), stat.getKills().size());
			if (players.size() >= cntr + 1) {
				mess += ", ";
			}
		}
		ChatUtils.send(player, "<name>[lives/kills]");
		ChatUtils.send(player, "Total Players: %s Total Living: %s Total Dead or Not Playing: %s", stats.size(), living, dead);
		ChatUtils.send(player, "");
		ChatUtils.send(player, mess);
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public boolean addChest(Location loc, float weight) {
		if (chests.keySet().contains(loc) || fixedChests.containsKey(loc)) return false;
		blacklistedChests.remove(loc);
		chests.put(loc, weight);
		Block b = loc.getBlock();
		if (b.getRelative(BlockFace.NORTH).getState() instanceof Chest) chests.put(b.getRelative(BlockFace.NORTH).getLocation(), weight);
		else if (b.getRelative(BlockFace.SOUTH).getState() instanceof Chest) chests.put(b.getRelative(BlockFace.SOUTH).getLocation(), weight);
		else if (b.getRelative(BlockFace.EAST).getState() instanceof Chest) chests.put(b.getRelative(BlockFace.EAST).getLocation(), weight);
		else if (b.getRelative(BlockFace.WEST).getState() instanceof Chest) chests.put(b.getRelative(BlockFace.WEST).getLocation(), weight);
		return true;
	}

	@Override
	public boolean addFixedChest(Location loc, String fixedChest) {
		if (loc == null || fixedChest == null || fixedChest.equalsIgnoreCase("")) return false;
		if (fixedChests.keySet().contains(loc)) return false;
		blacklistedChests.remove(loc);
		if (!(loc.getBlock().getState() instanceof Chest)) return false;
		removeChest(loc);
		fixedChests.put(loc, fixedChest);
		Block b = loc.getBlock();
		if (b.getRelative(BlockFace.NORTH).getState() instanceof Chest) fixedChests.put(b.getRelative(BlockFace.NORTH).getLocation(), fixedChest);
		else if (b.getRelative(BlockFace.SOUTH).getState() instanceof Chest) fixedChests.put(b.getRelative(BlockFace.SOUTH).getLocation(), fixedChest);
		else if (b.getRelative(BlockFace.EAST).getState() instanceof Chest) fixedChests.put(b.getRelative(BlockFace.EAST).getLocation(), fixedChest);
		else if (b.getRelative(BlockFace.WEST).getState() instanceof Chest) fixedChests.put(b.getRelative(BlockFace.WEST).getLocation(), fixedChest);
		return true;
	}

	@Override
	public boolean addSpawnPoint(Location loc) {
		if (loc == null) return false;
		if (spawnPoints.contains(loc)) return false;
		spawnPoints.add(loc);
		return true;
	}

	/**
	 * Removes chest from fixedChests and adds it to chests
	 * @param loc
	 * @return
	 */
	@Override
	public boolean removeFixedChest(Location loc) {
		if (loc == null) return false;
		if (!(loc.getBlock().getState() instanceof Chest)) return false;
		fixedChests.remove(loc);
		Block b = loc.getBlock();
		if (b.getRelative(BlockFace.NORTH).getState() instanceof Chest) fixedChests.remove(b.getRelative(BlockFace.NORTH).getLocation());
		else if (b.getRelative(BlockFace.SOUTH).getState() instanceof Chest) fixedChests.remove(b.getRelative(BlockFace.SOUTH).getLocation());
		else if (b.getRelative(BlockFace.EAST).getState() instanceof Chest) fixedChests.remove(b.getRelative(BlockFace.EAST).getLocation());
		else if (b.getRelative(BlockFace.WEST).getState() instanceof Chest) fixedChests.remove(b.getRelative(BlockFace.WEST).getLocation());
		return addChest(loc, 1f);
	}

	@Override
	public boolean removeChest(Location loc) {
		Block b = loc.getBlock();
		Location ad = null;
		if (b.getRelative(BlockFace.NORTH).getState() instanceof Chest) loc = b.getRelative(BlockFace.NORTH).getLocation();
		else if (b.getRelative(BlockFace.SOUTH).getState() instanceof Chest) loc = b.getRelative(BlockFace.SOUTH).getLocation();
		else if (b.getRelative(BlockFace.EAST).getState() instanceof Chest) loc = b.getRelative(BlockFace.EAST).getLocation();
		else if (b.getRelative(BlockFace.WEST).getState() instanceof Chest) loc = b.getRelative(BlockFace.WEST).getLocation();
		if (ad != null) {
			if (chests.remove(ad) == null & fixedChests.remove(ad) == null) {
				blacklistedChests.add(ad);
			}
		}
		if (chests.remove(loc) == null & fixedChests.remove(loc) == null) {
			blacklistedChests.add(loc);
			return false;
		}
		return true;
	}

	@Override
	public boolean removeSpawnPoint(Location loc) {
		if (loc == null) return false;
		Iterator<Location> iterator = spawnPoints.iterator();
		Location l;
		while (iterator.hasNext()) {
			if (HungerGames.equals(loc, l = iterator.next())) {
				iterator.remove();
				for (String playerName : spawnsTaken.keySet()) {
					Location comp = spawnsTaken.get(playerName);
					if (HungerGames.equals(l, comp)) {
						spawnsTaken.remove(playerName);
						if (Bukkit.getPlayer(playerName) == null) continue;
						ChatUtils.error(Bukkit.getPlayer(playerName),
							"Your spawn point has been recently removed. Try rejoining by typing '/hg rejoin %s'", name);
						leave(Bukkit.getPlayer(playerName), true);
					}
				}
				return true;
			}
		}
		return false;
	}

	private static void dropInventory(Player player) {
		for (ItemStack i : player.getInventory().getContents()) {
			if (i == null || i.getType().equals(Material.AIR)) continue;
			player.getWorld().dropItemNaturally(player.getLocation(), i);
		}
		player.getInventory().clear();
	}

	@Override
	public void setEnabled(boolean flag) {
		if (state == DELETED) return;
		if (!flag) {
			if (!flag) stopGame(false);
			state = DISABLED; // TODO do this better
			for (String s : stats.keySet()) {
				Player p = Bukkit.getPlayer(s);
				if (p == null) continue;
				playerLeaving(p, false);
				teleportPlayerToSpawn(p);
			}
			clear();
			state = DISABLED;
		}
		if (flag && state == DISABLED) state = STOPPED;
	}

	@Override
	public void setSpawn(Location newSpawn) {
		spawn = newSpawn;
	}

	@Override
	public List<String> getAllPlayers() {
		return new ArrayList<String>(stats.keySet());
	}

	@Override
	public List<PlayerStat> getStats() {
		return new ArrayList<PlayerStat>(stats.values());
	}
	
	@Override
	public Location getSpawn() {
		return spawn;
	}

	@Override
	public String getSetup() {
		return (setup == null || "".equals(setup)) ? null : setup;
	}

	@Override
	public List<String> getItemSets() {
		return itemsets;
	}

	@Override
	public void addItemSet(String name) {
		itemsets.add(name);
	}

	@Override
	public void removeItemSet(String name) {
		itemsets.remove(name);
	}
	
	public void setDoneCounting() {
		state = ABOUT_TO_START;
	}
	
	@Override
	public void addWorld(World world) {
		worlds.add(world.getName());
	}

	@Override
	public void addCuboid(Location one, Location two) {
		cuboids.add(new Cuboid(one, two));
	}

	@Override
	public Map<String, List<String>> getSponsors() {
		return Collections.unmodifiableMap(sponsors);
	}

	public void addSponsor(String player, String playerToBeSponsored) {
		if (sponsors.get(player) == null) sponsors.put(player, new ArrayList<String>());
		sponsors.get(player).add(playerToBeSponsored);
	}
	
	@Override
	public Set<World> getWorlds() {
		if (worlds.size() <= 0) return Collections.emptySet();
		Set<World> list = new HashSet<World>();
		for (String s : worlds) {
			if (Bukkit.getWorld(s) == null) continue;
			list.add(Bukkit.getWorld(s));
		}
	return list;
	}
	
	@Override
	public Set<Cuboid> getCuboids() {
		return Collections.unmodifiableSet(cuboids);
	}
	
	@Override
	public void removeItemsOnGround() {
		Logging.debug("Aboout the check items on the ground for %s worlds.", worlds.size());
		for (String s : worlds) {
			World w = Bukkit.getWorld(s);
			if (w == null) continue;
			Logging.debug("Checking world for items.");
			int count = 0;
			for (Entity e : w.getEntities()) {
				count++;
				if (!(e instanceof Item)) continue;
				e.remove();
			}
			Logging.debug("Checked: ", count);
		}
		for (Cuboid c : cuboids) {
			if (worlds.contains(c.getLower().getWorld().getName())) continue;
			for (Entity e : c.getLower().getWorld().getEntities()) {
				if (!(e instanceof Item)) continue;
				if (!c.isLocationWithin(e.getLocation())) continue;
				e.remove();
			}
		}
	}
	
	@Override
	public int getSize() {
		return spawnPoints.size();
	}

	@Override
	public void playCannonBoom() {
		for (Player p : getRemainingPlayers()) {
			p.getWorld().createExplosion(p.getLocation(), 0f, false);
		}
	}

	@Override
	public List<Long> getEndTimes() {
		return endTimes;
	}

	@Override
	public long getInitialStartTime() {
		return initialStartTime;
	}

	@Override
	public List<Long> getStartTimes() {
		return startTimes;
	}
	
	@Override
	public GameState getState() {
		return state;
	}

	public void delete() {
		clear();
		state = DELETED;
		chests.clear();
		fixedChests.clear();
		setup = null;
		itemsets.clear();
		worlds.clear();
		cuboids.clear();
		spawn = null;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		final HungerGame other = (HungerGame) obj;
		return this.compareTo(other) == 0;
	}

	@Override
	public int hashCode() {
		int hash = 5;
		hash = 59 * hash + (this.name != null ? this.name.toLowerCase().hashCode() : 0);
		return hash;
	}
	
	

	// sorts players by name ignoring case
	private class PlayerComparator implements Comparator<Player> {

		public PlayerComparator() {
		}

		public int compare(Player p1, Player p2) {
			String name1 = p1.getName();
			String name2 = p2.getName();
			return name1.compareToIgnoreCase(name2);
		}

	}
}