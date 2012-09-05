package com.randude14.hungergames.core.bukkit;

import com.randude14.hungergames.core.*;
import com.randude14.hungergames.core.blocks.BaseBlock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Effect;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Painting;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class BukkitWorld extends LocalWorld {
    
    private static final Logger logger = Logger.getLogger(BukkitWorld.class.getCanonicalName());
    private World world;
    private boolean skipNmsAccess = false;
    private boolean skipNmsSafeSet = false;

    /**
     * Construct the object.
     * @param world
     */
    public BukkitWorld(World world) {
        this.world = world;
    }

    /**
     * Get the world handle.
     *
     * @return
     */
    public World getWorld() {
        return world;
    }

    /**
     * Get the name of the world
     *
     * @return
     */
    @Override
    public String getName() {
        return world.getName();
    }

    /**
     * Set block type.
     *
     * @param pt
     * @param type
     * @return
     */
    @Override
    public boolean setBlockType(Vector pt, int type) {
        return world.getBlockAt(pt.getBlockX(), pt.getBlockY(), pt.getBlockZ()).setTypeId(type);
    }

    /**
     * Set block type.
     *
     * @param pt
     * @param type
     * @return
     */
    @Override
    public boolean setBlockTypeFast(Vector pt, int type) {
        return world.getBlockAt(pt.getBlockX(), pt.getBlockY(), pt.getBlockZ()).setTypeId(type, false);
    }

    /**
     * set block type & data
     * @param pt
     * @param type
     * @param data
     * @return
     */
    @Override
    public boolean setTypeIdAndData(Vector pt, int type, int data) {
        return world.getBlockAt(pt.getBlockX(), pt.getBlockY(), pt.getBlockZ()).setTypeIdAndData(type, (byte) data, true);
    }

    /**
     * set block type & data
     * @param pt
     * @param type
     * @param data
     * @return
     */
    @Override
    public boolean setTypeIdAndDataFast(Vector pt, int type, int data) {
        return world.getBlockAt(pt.getBlockX(), pt.getBlockY(), pt.getBlockZ()).setTypeIdAndData(type, (byte) data, false);
    }

    /**
     * Get block type.
     *
     * @param pt
     * @return
     */
    @Override
    public int getBlockType(Vector pt) {
        return world.getBlockTypeIdAt(pt.getBlockX(), pt.getBlockY(), pt.getBlockZ());
    }

    /**
     * Set block data.
     *
     * @param pt
     * @param data
     */
    @Override
    public void setBlockData(Vector pt, int data) {
        world.getBlockAt(pt.getBlockX(), pt.getBlockY(), pt.getBlockZ()).setData((byte) data);
    }

    /**
     * Set block data.
     *
     * @param pt
     * @param data
     */
    @Override
    public void setBlockDataFast(Vector pt, int data) {
        world.getBlockAt(pt.getBlockX(), pt.getBlockY(), pt.getBlockZ()).setData((byte) data, false);
    }

    /**
     * Get block data.
     *
     * @param pt
     * @return
     */
    @Override
    public int getBlockData(Vector pt) {
        return world.getBlockAt(pt.getBlockX(), pt.getBlockY(), pt.getBlockZ()).getData();
    }

    /**
     * Get block light level.
     *
     * @param pt
     * @return
     */
    @Override
    public int getBlockLightLevel(Vector pt) {
        return world.getBlockAt(pt.getBlockX(), pt.getBlockY(), pt.getBlockZ()).getLightLevel();
    }

    /**
     * Drop an item.
     *
     * @param pt
     * @param item
     */
    @Override
    public void dropItem(Vector pt, com.randude14.hungergames.core.ItemStack item) {
        ItemStack bukkitItem = new ItemStack(item.getType(), item.getAmount(), item.getData());
        world.dropItemNaturally(BukkitUtil.toLocation(world, pt), bukkitItem);
    }

    /**
     * Remove entities in an area.
     *
     * @param origin
     * @param radius
     * @return
     */
    @Override
    public int removeEntities(EntityType type, Vector origin, int radius) {
        int num = 0;
        double radiusSq = Math.pow(radius, 2);

        for (Entity ent : world.getEntities()) {
            if (radius != -1
                    && origin.distanceSq(BukkitUtil.toVector(ent.getLocation())) > radiusSq) {
                continue;
            }

            if (type == EntityType.ARROWS) {
                if (ent instanceof Arrow) {
                    ent.remove();
                    ++num;
                }
            } else if (type == EntityType.BOATS) {
                if (ent instanceof Boat) {
                    ent.remove();
                    ++num;
                }
            } else if (type == EntityType.ITEMS) {
                if (ent instanceof Item) {
                    ent.remove();
                    ++num;
                }
            } else if (type == EntityType.MINECARTS) {
                if (ent instanceof Minecart) {
                    ent.remove();
                    ++num;
                }
            } else if (type == EntityType.PAINTINGS) {
                if (ent instanceof Painting) {
                    ent.remove();
                    ++num;
                }
            } else if (type == EntityType.TNT) {
                if (ent instanceof TNTPrimed) {
                    ent.remove();
                    ++num;
                }
            } else if (type == EntityType.XP_ORBS) {
                if (ent instanceof ExperienceOrb) {
                    ent.remove();
                    ++num;
                }
            }
        }

        return num;
    }

    /**
     * Set a sign's text.
     *
     * @param pt
     * @param text
     * @return
     */
    private boolean setSignText(Vector pt, String[] text) {
        Block block = world.getBlockAt(pt.getBlockX(), pt.getBlockY(), pt.getBlockZ());
        if (block == null) return false;
        BlockState state = block.getState();
        if (state == null || !(state instanceof Sign)) return false;
        Sign sign = (Sign) state;
        sign.setLine(0, text[0]);
        sign.setLine(1, text[1]);
        sign.setLine(2, text[2]);
        sign.setLine(3, text[3]);
        sign.update();
        return true;
    }

    /**
     * Get a sign's text.
     *
     * @param pt
     * @return
     */
    private String[] getSignText(Vector pt) {
        Block block = world.getBlockAt(pt.getBlockX(), pt.getBlockY(), pt.getBlockZ());
        if (block == null) return new String[] { "", "", "", "" };
        BlockState state = block.getState();
        if (state == null || !(state instanceof Sign)) return new String[] { "", "", "", "" };
        Sign sign = (Sign) state;
        String line0 = sign.getLine(0);
        String line1 = sign.getLine(1);
        String line2 = sign.getLine(2);
        String line3 = sign.getLine(3);
        return new String[] {
                line0 != null ? line0 : "",
                line1 != null ? line1 : "",
                line2 != null ? line2 : "",
                line3 != null ? line3 : "",
            };
    }


    @Override
    public void checkLoadedChunk(Vector pt) {
        if (!world.isChunkLoaded(pt.getBlockX() >> 4, pt.getBlockZ() >> 4)) {
            world.loadChunk(pt.getBlockX() >> 4, pt.getBlockZ() >> 4);
        }
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof BukkitWorld)) {
            return false;
        }

        return ((BukkitWorld) other).world.equals(world);
    }

    @Override
    public int hashCode() {
        return world.hashCode();
    }

    @Override
    public int getMaxY() {
        return world.getMaxHeight() - 1;
    }

    private static final Map<Integer, Effect> effects = new HashMap<Integer, Effect>();
    static {
        for (Effect effect : Effect.values()) {
            effects.put(effect.getId(), effect);
        }
    }


    @Override
    public LocalEntity[] getEntities(Region region) {
        List<BukkitEntity> entities = new ArrayList<BukkitEntity>();
        for (Vector2D pt : region.getChunks()) {
            if (world.isChunkLoaded(pt.getBlockX(), pt.getBlockZ())) {
                Entity[] ents = world.getChunkAt(pt.getBlockX(), pt.getBlockZ()).getEntities();
                for (Entity ent : ents) {
                    if (region.contains(BukkitUtil.toVector(ent.getLocation()))) {
                        entities.add(BukkitUtil.toLocalEntity(ent));
                    }
                }
            }
        }
        return entities.toArray(new BukkitEntity[entities.size()]);
    }

    @Override
    public int killEntities(LocalEntity... entities) {
        int amount = 0;
        Set<UUID> toKill = new HashSet<UUID>();
        for (LocalEntity entity : entities) {
            toKill.add(((BukkitEntity) entity).getEntityId());
        }
        for (Entity entity : world.getEntities()) {
            if (toKill.contains(entity.getUniqueId())) {
                entity.remove();
                ++amount;
            }
        }
        return amount;
    }

	@Override
	public com.randude14.hungergames.core.blocks.Block getBlock(Vector pt) {
		com.randude14.hungergames.core.blocks.Block block = super.getBlock(pt);
		if (Material.CHEST.getId() == block.getId()) return new BukkitChest(this, pt);
		return block;
	}
    
    
}