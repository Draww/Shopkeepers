package com.nisovin.shopkeepers.util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.apache.commons.lang.Validate;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.SkullType;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.permissions.Permissible;
import org.bukkit.util.Vector;

import com.nisovin.shopkeepers.Log;
import com.nisovin.shopkeepers.ShopkeepersPlugin;
import com.nisovin.shopkeepers.TradingRecipe;
import com.nisovin.shopkeepers.compat.NMSManager;

public class Utils {

	/**
	 * Creates a clone of the given {@link ItemStack} with amount <code>1</code>.
	 * 
	 * @param item
	 *            the item to get a normalized version of
	 * @return the normalized item
	 */
	public static ItemStack getNormalizedItem(ItemStack item) {
		if (item == null) return null;
		ItemStack normalizedClone = item.clone();
		normalizedClone.setAmount(1);
		return normalizedClone;
	}

	public static boolean isEmpty(ItemStack item) {
		return item == null || item.getType() == Material.AIR || item.getAmount() <= 0;
	}

	public static ItemStack getNullIfEmpty(ItemStack item) {
		return isEmpty(item) ? null : item;
	}

	public static boolean isChest(Material material) {
		return material == Material.CHEST || material == Material.TRAPPED_CHEST;
	}

	public static boolean isSign(Material material) {
		return material == Material.WALL_SIGN || material == Material.SIGN_POST || material == Material.SIGN;
	}

	// TODO temporary, due to a bukkit bug custom head item can currently not be saved
	public static boolean isCustomHeadItem(ItemStack item) {
		if (item == null) return false;
		if (item.getType() != Material.SKULL_ITEM) {
			return false;
		}
		if (item.getDurability() != SkullType.PLAYER.ordinal()) {
			return false;
		}

		ItemMeta meta = item.getItemMeta();
		if (meta instanceof SkullMeta) {
			SkullMeta skullMeta = (SkullMeta) meta;
			if (skullMeta.hasOwner() && skullMeta.getOwner() == null) {
				// custom head items usually don't have a valid owner
				return true;
			}
		}
		return false;
	}

	/**
	 * Checks if the given {@link BlockFace} is valid to be used for a wall sign.
	 * 
	 * @param blockFace
	 * @return
	 */
	public static boolean isWallSignFace(BlockFace blockFace) {
		return blockFace == BlockFace.NORTH || blockFace == BlockFace.SOUTH || blockFace == BlockFace.EAST || blockFace == BlockFace.WEST;
	}

	/**
	 * Determines the axis-aligned {@link BlockFace} for the given direction.
	 * If modY is zero only {@link BlockFace}s facing horizontal will be returned.
	 * This method takes into account that the values for EAST/WEST and NORTH/SOUTH
	 * were switched in some past version of bukkit. So it should also properly work
	 * with older bukkit versions.
	 * 
	 * @param modX
	 * @param modY
	 * @param modZ
	 * @return
	 */
	public static BlockFace getAxisBlockFace(double modX, double modY, double modZ) {
		double xAbs = Math.abs(modX);
		double yAbs = Math.abs(modY);
		double zAbs = Math.abs(modZ);

		if (xAbs >= zAbs) {
			if (xAbs >= yAbs) {
				if (modX >= 0.0D) {
					// EAST/WEST and NORTH/SOUTH values were switched in some past bukkit version:
					// with this additional checks it should work across different versions
					if (BlockFace.EAST.getModX() == 1) {
						return BlockFace.EAST;
					} else {
						return BlockFace.WEST;
					}
				} else {
					if (BlockFace.EAST.getModX() == 1) {
						return BlockFace.WEST;
					} else {
						return BlockFace.EAST;
					}
				}
			} else {
				if (modY >= 0.0D) {
					return BlockFace.UP;
				} else {
					return BlockFace.DOWN;
				}
			}
		} else {
			if (zAbs >= yAbs) {
				if (modZ >= 0.0D) {
					if (BlockFace.SOUTH.getModZ() == 1) {
						return BlockFace.SOUTH;
					} else {
						return BlockFace.NORTH;
					}
				} else {
					if (BlockFace.SOUTH.getModZ() == 1) {
						return BlockFace.NORTH;
					} else {
						return BlockFace.SOUTH;
					}
				}
			} else {
				if (modY >= 0.0D) {
					return BlockFace.UP;
				} else {
					return BlockFace.DOWN;
				}
			}
		}
	}

	/**
	 * Tries to find the nearest wall sign {@link BlockFace} facing towards the given direction.
	 * 
	 * @param direction
	 * @return a valid wall sign face
	 */
	public static BlockFace toWallSignFace(Vector direction) {
		assert direction != null;
		return getAxisBlockFace(direction.getX(), 0.0D, direction.getZ());
	}

	/**
	 * Gets the block face a player is looking at.
	 * 
	 * @param player
	 *            the player
	 * @param targetBlock
	 *            the block the player is looking at
	 * @return the block face, or <code<null</code> if none was found
	 */
	public static BlockFace getTargetBlockFace(Player player, Block targetBlock) {
		Location intersection = getBlockIntersection(player, targetBlock);
		if (intersection == null) return null;
		Location blockCenter = targetBlock.getLocation().add(0.5D, 0.5D, 0.5D);
		Vector centerToIntersection = intersection.subtract(blockCenter).toVector();
		double x = centerToIntersection.getX();
		double y = centerToIntersection.getY();
		double z = centerToIntersection.getZ();
		return getAxisBlockFace(x, y, z);
	}

	/**
	 * Determines the exact intersection point of a players view and a targeted block.
	 * 
	 * @param player
	 *            the player
	 * @param targetBlock
	 *            the block the player is looking at
	 * @return the intersection point of the players view and the target block,
	 *         or null if no intersection was found
	 */
	public static Location getBlockIntersection(Player player, Block targetBlock) {
		if (player == null || targetBlock == null) return null;

		// block bounds:
		double minX = targetBlock.getX();
		double minY = targetBlock.getY();
		double minZ = targetBlock.getZ();

		double maxX = minX + 1.0D;
		double maxY = minY + 1.0D;
		double maxZ = minZ + 1.0D;

		// ray origin:
		Location origin = player.getEyeLocation();
		double originX = origin.getX();
		double originY = origin.getY();
		double originZ = origin.getZ();

		// ray direction
		Vector dir = origin.getDirection();
		double dirX = dir.getX();
		double dirY = dir.getY();
		double dirZ = dir.getZ();

		// tiny improvement to save a few divisions below:
		double divX = 1.0D / dirX;
		double divY = 1.0D / dirY;
		double divZ = 1.0D / dirZ;

		// intersection interval:
		double t0 = 0.0D;
		double t1 = Double.MAX_VALUE;

		double tmin;
		double tmax;

		double tymin;
		double tymax;

		double tzmin;
		double tzmax;

		if (dirX >= 0.0D) {
			tmin = (minX - originX) * divX;
			tmax = (maxX - originX) * divX;
		} else {
			tmin = (maxX - originX) * divX;
			tmax = (minX - originX) * divX;
		}

		if (dirY >= 0.0D) {
			tymin = (minY - originY) * divY;
			tymax = (maxY - originY) * divY;
		} else {
			tymin = (maxY - originY) * divY;
			tymax = (minY - originY) * divY;
		}

		if ((tmin > tymax) || (tymin > tmax)) {
			return null;
		}

		if (tymin > tmin) tmin = tymin;
		if (tymax < tmax) tmax = tymax;

		if (dirZ >= 0.0D) {
			tzmin = (minZ - originZ) * divZ;
			tzmax = (maxZ - originZ) * divZ;
		} else {
			tzmin = (maxZ - originZ) * divZ;
			tzmax = (minZ - originZ) * divZ;
		}

		if ((tmin > tzmax) || (tzmin > tmax)) {
			return null;
		}

		if (tzmin > tmin) tmin = tzmin;
		if (tzmax < tmax) tmax = tzmax;

		if ((tmin >= t1) || (tmax <= t0)) {
			return null;
		}

		// intersection:
		Location intersection = origin.add(dir.multiply(tmin));
		return intersection;
	}

	// messages:

	public static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("0.##", new DecimalFormatSymbols(Locale.US));
	static {
		DECIMAL_FORMAT.setGroupingUsed(false);
	}

	public static String getLocationString(Location location) {
		return getLocationString(location.getWorld().getName(), location.getX(), location.getY(), location.getZ());
	}

	public static String getLocationString(Block block) {
		return getLocationString(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
	}

	public static String getLocationString(String worldName, double x, double y, double z) {
		return worldName + "," + DECIMAL_FORMAT.format(x) + "," + DECIMAL_FORMAT.format(y) + "," + DECIMAL_FORMAT.format(z);
	}

	public static String getPlayerAsString(Player player) {
		return getPlayerAsString(player.getName(), player.getUniqueId());
	}

	public static String getPlayerAsString(String playerName, UUID uniqueId) {
		return playerName + (uniqueId == null ? "" : "(" + uniqueId.toString() + ")");
	}

	public static String translateColorCodesToAlternative(char altColorChar, String textToTranslate) {
		char[] b = textToTranslate.toCharArray();
		for (int i = 0; i < b.length - 1; i++) {
			if (b[i] == ChatColor.COLOR_CHAR && "0123456789AaBbCcDdEeFfKkLlMmNnOoRr".indexOf(b[i + 1]) > -1) {
				b[i] = altColorChar;
				b[i + 1] = Character.toLowerCase(b[i + 1]);
			}
		}
		return new String(b);
	}

	public static String decolorize(String colored) {
		if (colored == null) return null;
		return Utils.translateColorCodesToAlternative('&', colored);
	}

	public static List<String> decolorize(List<String> colored) {
		if (colored == null) return null;
		List<String> decolored = new ArrayList<String>(colored.size());
		for (String string : colored) {
			decolored.add(Utils.translateColorCodesToAlternative('&', string));
		}
		return decolored;
	}

	public static String colorize(String message) {
		if (message == null || message.isEmpty()) return message;
		return ChatColor.translateAlternateColorCodes('&', message);
	}

	public static List<String> colorize(List<String> messages) {
		if (messages == null) return messages;
		List<String> colored = new ArrayList<String>(messages.size());
		for (String message : messages) {
			colored.add(Utils.colorize(message));
		}
		return colored;
	}

	public static void sendMessage(CommandSender sender, String message, String... args) {
		// skip if sender is null or message is "empty":
		if (sender == null || message == null || message.isEmpty()) return;
		if (args != null && args.length >= 2) {
			// replace arguments (key-value replacement):
			String key;
			String value;
			for (int i = 1; i < args.length; i += 2) {
				key = args[i - 1];
				value = args[i];
				if (key == null || value == null) continue; // skip invalid arguments
				message = message.replace(key, value);
			}
		}

		String[] msgs = message.split("\n");
		for (String msg : msgs) {
			sender.sendMessage(msg);
		}
	}

	public static boolean isEmpty(String string) {
		return string == null || string.isEmpty();
	}

	public static String normalize(String identifier) {
		if (identifier == null) return null;
		return identifier.trim().replace('_', '-').replace(' ', '-').toLowerCase(Locale.ROOT);
	}

	public static List<String> normalize(List<String> identifiers) {
		if (identifiers == null) return null;
		List<String> normalized = new ArrayList<String>(identifiers.size());
		for (String identifier : identifiers) {
			normalized.add(normalize(identifier));
		}
		return normalized;
	}

	/**
	 * Performs a permissions check and logs debug information about it.
	 * 
	 * @param permissible
	 * @param permission
	 * @return
	 */
	public static boolean hasPermission(Permissible permissible, String permission) {
		assert permissible != null;
		boolean hasPerm = permissible.hasPermission(permission);
		if (!hasPerm && (permissible instanceof Player)) {
			Log.debug("Player '" + ((Player) permissible).getName() + "' does not have permission '" + permission + "'.");
		}
		return hasPerm;
	}

	// entity utilities:

	public static boolean isNPC(Entity entity) {
		return entity.hasMetadata("NPC");
	}

	public static List<Entity> getNearbyEntities(Location location, double radius, EntityType... types) {
		List<Entity> entities = new ArrayList<Entity>();
		if (location == null) return entities;
		if (radius <= 0.0D) return entities;

		List<EntityType> typesList = (types == null) ? Collections.<EntityType>emptyList() : Arrays.asList(types);
		double radius2 = radius * radius;
		int chunkRadius = ((int) (radius / 16)) + 1;
		Chunk center = location.getChunk();
		int startX = center.getX() - chunkRadius;
		int endX = center.getX() + chunkRadius;
		int startZ = center.getZ() - chunkRadius;
		int endZ = center.getZ() + chunkRadius;
		World world = location.getWorld();
		for (int chunkX = startX; chunkX <= endX; chunkX++) {
			for (int chunkZ = startZ; chunkZ <= endZ; chunkZ++) {
				if (!world.isChunkLoaded(chunkX, chunkZ)) continue;
				Chunk chunk = world.getChunkAt(chunkX, chunkZ);
				for (Entity entity : chunk.getEntities()) {
					Location entityLoc = entity.getLocation();
					// TODO: this is a workaround: for some yet unknown reason entities sometimes report to be in a
					// different world..
					if (!entityLoc.getWorld().equals(world)) {
						Log.debug("Found an entity which reports to be in a different world than the chunk we got it from:");
						Log.debug("Location=" + location + ", Chunk=" + chunk + ", ChunkWorld=" + chunk.getWorld()
								+ ", entityType=" + entity.getType() + ", entityLocation=" + entityLoc);
						continue; // skip this entity
					}

					if (entityLoc.distanceSquared(location) <= radius2) {
						if (typesList.isEmpty() || typesList.contains(entity.getType())) {
							entities.add(entity);
						}
					}
				}
			}
		}
		return entities;
	}

	public static List<Entity> getNearbyChunkEntities(Chunk chunk, int chunkRadius, boolean loadChunks, EntityType... types) {
		List<Entity> entities = new ArrayList<Entity>();
		if (chunk == null) return entities;
		if (chunkRadius < 0) return entities;

		List<EntityType> typesList = (types == null) ? Collections.<EntityType>emptyList() : Arrays.asList(types);
		int startX = chunk.getX() - chunkRadius;
		int endX = chunk.getX() + chunkRadius;
		int startZ = chunk.getZ() - chunkRadius;
		int endZ = chunk.getZ() + chunkRadius;
		World world = chunk.getWorld();
		for (int chunkX = startX; chunkX <= endX; chunkX++) {
			for (int chunkZ = startZ; chunkZ <= endZ; chunkZ++) {
				if (!loadChunks && !world.isChunkLoaded(chunkX, chunkZ)) continue;
				Chunk currentChunk = world.getChunkAt(chunkX, chunkZ);
				for (Entity entity : currentChunk.getEntities()) {
					Location entityLoc = entity.getLocation();
					// TODO: this is a workaround: for some yet unknown reason entities sometimes report to be in a
					// different world..
					if (!entityLoc.getWorld().equals(world)) {
						Log.debug("Found an entity which reports to be in a different world than the chunk we got it from:");
						Log.debug("Chunk=" + currentChunk + ", ChunkWorld=" + currentChunk.getWorld() + ", entityType=" + entity.getType()
								+ ", entityLocation=" + entityLoc);
						continue; // skip this entity
					}

					if (typesList.isEmpty() || typesList.contains(entity.getType())) {
						entities.add(entity);
					}
				}
			}
		}
		return entities;
	}

	// itemstack utilities:

	public static ItemStack createItemStack(Material type, int amount, short data, String displayName, List<String> lore) {
		// TODO return null in case of type AIR?
		ItemStack item = new ItemStack(type, amount, data);
		return setItemStackNameAndLore(item, displayName, lore);
	}

	public static ItemStack setItemStackNameAndLore(ItemStack item, String displayName, List<String> lore) {
		if (item == null) return null;
		ItemMeta meta = item.getItemMeta();
		if (meta != null) {
			meta.setDisplayName(displayName);
			meta.setLore(lore);
			item.setItemMeta(meta);
		}
		return item;
	}

	public static String getSimpleItemInfo(ItemStack item) {
		if (item == null) return "empty";
		StringBuilder sb = new StringBuilder();
		sb.append(item.getAmount()).append('x').append(item.getType());
		if (item.getDurability() != 0) {
			sb.append('~').append(item.getDurability());
		}
		return sb.toString();
	}

	public static String getSimpleRecipeInfo(TradingRecipe recipe) {
		if (recipe == null) return "none";
		StringBuilder sb = new StringBuilder();
		sb.append("[item1=").append(getSimpleItemInfo(recipe.getItem1()))
				.append(", item2=").append(getSimpleItemInfo(recipe.getItem2()))
				.append(", result=").append(getSimpleItemInfo(recipe.getResultItem())).append("]");
		return sb.toString();
	}

	/**
	 * Same as {@link ItemStack#isSimilar(ItemStack)}, but taking into account that both given ItemStacks might be
	 * <code>null</code>.
	 * 
	 * @param item1
	 *            an itemstack
	 * @param item2
	 *            another itemstack
	 * @return <code>true</code> if the given item stacks are both <code>null</code> or similar
	 */
	public static boolean isSimilar(ItemStack item1, ItemStack item2) {
		if (item1 == null) return (item2 == null);
		return item1.isSimilar(item2);
	}

	/**
	 * Checks if the given item matches the specified attributes.
	 * 
	 * @param item
	 *            the item
	 * @param type
	 *            the item type
	 * @param data
	 *            the data value/durability, or <code>-1</code> to ignore it
	 * @param displayName
	 *            the displayName, or <code>null</code> or empty to ignore it
	 * @param lore
	 *            the item lore, or <code>null</code> or empty to ignore it
	 * @return <code>true</code> if the item has similar attributes
	 */
	public static boolean isSimilar(ItemStack item, Material type, short data, String displayName, List<String> lore) {
		if (item == null) return false;
		if (item.getType() != type) return false;
		if (data != -1 && item.getDurability() != data) return false;

		ItemMeta itemMeta = null;
		// compare display name:
		if (displayName != null && !displayName.isEmpty()) {
			if (!item.hasItemMeta()) return false;
			itemMeta = item.getItemMeta();
			if (itemMeta == null) return false;

			if (!itemMeta.hasDisplayName() || !displayName.equals(itemMeta.getDisplayName())) {
				return false;
			}
		}

		// compare lore:
		if (lore != null && !lore.isEmpty()) {
			if (itemMeta == null) {
				if (!item.hasItemMeta()) return false;
				itemMeta = item.getItemMeta();
				if (itemMeta == null) return false;
			}

			if (!itemMeta.hasLore() || !lore.equals(itemMeta.getLore())) {
				return false;
			}
		}

		return true;
	}

	// save and load itemstacks from config, including attributes:

	/**
	 * Saves the given {@link ItemStack} to the given configuration section.
	 * Also saves the item's attributes in the same section at '{node}_attributes'.
	 * 
	 * @param section
	 *            a configuration section
	 * @param node
	 *            where to save the item stack inside the section
	 * @param item
	 *            the item stack to save, can be <code>null</code>
	 */
	public static void saveItem(ConfigurationSection section, String node, ItemStack item) {
		assert section != null && node != null;
		section.set(node, item);
		// saving attributes manually, as they weren't saved by bukkit in the past:
		String attributes = NMSManager.getProvider().saveItemAttributesToString(item);
		if (attributes != null && !attributes.isEmpty()) {
			String attributesNode = node + "_attributes";
			section.set(attributesNode, attributes);
		}
	}

	/**
	 * Loads an {@link ItemStack} from the given configuration section.
	 * Also attempts to load attributes saved at '{node}_attributes'.
	 * 
	 * @param section
	 *            a configuration section
	 * @param node
	 *            where to load the item stack from inside the section
	 * @return the loaded item stack, possibly <code>null</code>
	 */
	public static ItemStack loadItem(ConfigurationSection section, String node) {
		assert section != null && node != null;
		ItemStack item = section.getItemStack(node);
		// loading separately stored attributes:
		String attributesNode = node + "_attributes";
		if (item != null && section.contains(attributesNode)) {
			String attributes = section.getString(attributesNode);
			if (attributes != null && !attributes.isEmpty()) {
				item = NMSManager.getProvider().loadItemAttributesFromString(item, attributes);
			}
		}
		return item;
	}

	// inventory utilities:

	// somewhere in early 1.9 getStorageContents was introduced and the previous behavior of getContents was changed for
	// player inventories to now return the combined inventory contents
	// TODO remove this once we support 1.9+

	private static final int PLAYER_INVENTORY_STORAGE_SIZE = 36;

	/**
	 * Gets the storage contents from the specified inventory.
	 * 
	 * @param inventory
	 *            the inventory
	 * @return the storage contents
	 */
	public static ItemStack[] getStorageContents(Inventory inventory) {
		assert inventory != null;
		ItemStack[] storageContents = inventory.getContents();
		if (inventory instanceof PlayerInventory) {
			storageContents = Arrays.copyOf(storageContents, PLAYER_INVENTORY_STORAGE_SIZE);
		}
		return storageContents;
	}

	/**
	 * Sets the storage contents of the specified inventory.
	 * 
	 * @param inventory
	 *            the inventory
	 * @param storageContents
	 *            the new storage contents
	 */
	public static void setStorageContents(Inventory inventory, ItemStack[] storageContents) {
		assert inventory != null;
		// storage contents are always stored at the beginning of the inventory
		for (int slotId = 0; slotId < storageContents.length; slotId++) {
			inventory.setItem(slotId, storageContents[slotId]);
		}
	}

	public static List<ItemCount> countItems(ItemStack[] contents, Filter<ItemStack> filter) {
		List<ItemCount> itemCounts = new ArrayList<ItemCount>();
		if (contents == null) return itemCounts;
		for (ItemStack item : contents) {
			if (isEmpty(item)) continue;
			if (filter != null && !filter.accept(item)) continue;

			// check if we already have a counter for this type of item:
			ItemCount itemCount = ItemCount.findSimilar(itemCounts, item);
			if (itemCount != null) {
				// increase item count:
				itemCount.addAmount(item.getAmount());
			} else {
				// add new item entry:
				itemCounts.add(new ItemCount(item, item.getAmount()));
			}
		}
		return itemCounts;
	}

	/**
	 * Checks if the given contents contains at least the specified amount of items matching the specified attributes.
	 * 
	 * @param contents
	 *            the contents to search through
	 * @param type
	 *            the item type
	 * @param data
	 *            the data value/durability, or <code>-1</code> to ignore it
	 * @param displayName
	 *            the displayName, or <code>null</code> to ignore it
	 * @param lore
	 *            the item lore, or <code>null</code> or empty to ignore it
	 * @param amount
	 *            the amount of items to look for
	 * @return <code>true</code> if the at least specified amount of matching items was found
	 */
	public static boolean containsAtLeast(ItemStack[] contents, Material type, short data, String displayName, List<String> lore, int amount) {
		if (contents == null) return false;
		int remainingAmount = amount;
		for (ItemStack itemStack : contents) {
			if (!Utils.isSimilar(itemStack, type, data, displayName, lore)) continue;
			int currentAmount = itemStack.getAmount() - remainingAmount;
			if (currentAmount >= 0) {
				return true;
			} else {
				remainingAmount = -currentAmount;
			}
		}
		return false;
	}

	/**
	 * Removes the specified amount of items which match the specified attributes from the given contents.
	 * 
	 * @param contents
	 *            the contents
	 * @param type
	 *            the item type
	 * @param data
	 *            the data value/durability, or <code>-1</code> to ignore it
	 * @param displayName
	 *            the display name, or <code>null</code> to ignore it
	 * @param lore
	 *            the item lore, or <code>null</code> or empty to ignore it
	 * @param amount
	 *            the amount of matching items to remove
	 * @return the amount of items that couldn't be removed (<code>0</code> on full success)
	 */
	public static int removeItems(ItemStack[] contents, Material type, short data, String displayName, List<String> lore, int amount) {
		if (contents == null) return amount;
		int remainingAmount = amount;
		for (int slotId = 0; slotId < contents.length; slotId++) {
			ItemStack itemStack = contents[slotId];
			if (!Utils.isSimilar(itemStack, type, data, displayName, lore)) continue;
			int newAmount = itemStack.getAmount() - remainingAmount;
			if (newAmount > 0) {
				itemStack.setAmount(newAmount);
				break;
			} else {
				contents[slotId] = null;
				remainingAmount = -newAmount;
				if (remainingAmount == 0) break;
			}
		}
		return remainingAmount;
	}

	/**
	 * Adds the given {@link ItemStack} to the given contents.
	 * 
	 * <p>
	 * This will first try to fill similar partial {@link ItemStack}s in the contents up to the item's max stack size.
	 * Afterwards it will insert the remaining amount into empty slots, splitting at the item's max stack size.
	 * <p>
	 * This does not modify the original item stacks in the given array. If it has to modify the amount of an item
	 * stack, it first replaces it with a copy. So in case those item stacks are mirroring changes to their minecraft
	 * counterpart, those don't get affected directly.<br>
	 * The item being added gets copied as well before it gets inserted in an empty slot.
	 * 
	 * @param contents
	 *            the contents to add the given {@link ItemStack} to
	 * @param item
	 *            the {@link ItemStack} to add
	 * @return the amount of items which couldn't be added (<code>0</code> on full success)
	 */
	public static int addItems(ItemStack[] contents, ItemStack item) {
		Validate.notNull(contents);
		Validate.notNull(item);
		int amount = item.getAmount();
		Validate.isTrue(amount >= 0);
		if (amount == 0) return 0;

		// search for partially fitting item stacks:
		int maxStackSize = item.getMaxStackSize();
		int size = contents.length;
		for (int slot = 0; slot < size; slot++) {
			ItemStack slotItem = contents[slot];

			// slot empty? - skip, because we are currently filling existing item stacks up
			if (isEmpty(slotItem)) continue;

			// slot already full?
			int slotAmount = slotItem.getAmount();
			if (slotAmount >= maxStackSize) continue;

			if (slotItem.isSimilar(item)) {
				// copy itemstack, so we don't modify the original itemstack:
				slotItem = slotItem.clone();
				contents[slot] = slotItem;

				int newAmount = slotAmount + amount;
				if (newAmount <= maxStackSize) {
					// remaining amount did fully fit into this stack:
					slotItem.setAmount(newAmount);
					return 0;
				} else {
					// did not fully fit:
					slotItem.setAmount(maxStackSize);
					amount -= (maxStackSize - slotAmount);
					assert amount != 0;
				}
			}
		}

		// we have items remaining:
		assert amount > 0;

		// search for empty slots:
		for (int slot = 0; slot < size; slot++) {
			ItemStack slotItem = contents[slot];
			if (isEmpty(slotItem)) {
				// found empty slot:
				if (amount > maxStackSize) {
					// add full stack:
					ItemStack stack = item.clone();
					stack.setAmount(maxStackSize);
					contents[slot] = stack;
					amount -= maxStackSize;
				} else {
					// completely fits:
					ItemStack stack = item.clone(); // create a copy, just in case
					stack.setAmount(amount); // stack of remaining amount
					contents[slot] = stack;
					return 0;
				}
			}
		}

		// not all items did fit into the inventory:
		return amount;
	}

	/**
	 * Removes the given {@link ItemStack} from the given contents.
	 * 
	 * <p>
	 * If the amount of the given {@link ItemStack} is {@link Integer#MAX_VALUE}, then all similar items are being
	 * removed from the contents.<br>
	 * This does not modify the original item stacks. If it has to modify the amount of an item stack, it first replaces
	 * it with a copy. So in case those item stacks are mirroring changes to their minecraft counterpart, those don't
	 * get affected directly.
	 * </p>
	 * 
	 * @param contents
	 *            the contents to remove the given {@link ItemStack} from
	 * @param item
	 *            the {@link ItemStack} to remove from the given contents
	 * @return the amount of items which couldn't be removed (<code>0</code> on full success)
	 */
	public static int removeItems(ItemStack[] contents, ItemStack item) {
		Validate.notNull(contents);
		Validate.notNull(item);
		int amount = item.getAmount();
		Validate.isTrue(amount >= 0);
		if (amount == 0) return 0;

		boolean removeAll = (amount == Integer.MAX_VALUE);
		for (int slot = 0; slot < contents.length; slot++) {
			ItemStack slotItem = contents[slot];
			if (slotItem == null) continue;
			if (item.isSimilar(slotItem)) {
				if (removeAll) {
					contents[slot] = null;
				} else {
					int newAmount = slotItem.getAmount() - amount;
					if (newAmount > 0) {
						// copy itemstack, so we don't modify the original itemstack:
						slotItem = slotItem.clone();
						contents[slot] = slotItem;
						slotItem.setAmount(newAmount);
						// all items were removed:
						return 0;
					} else {
						contents[slot] = null;
						amount = -newAmount;
						if (amount == 0) {
							// all items were removed:
							return 0;
						}
					}
				}
			}
		}

		if (removeAll) return 0;
		return amount;
	}

	/**
	 * Increases the amount of the given {@link ItemStack}.
	 * <p>
	 * This makes sure that the itemstack's amount ends up to be at most {@link ItemStack#getMaxStackSize()}, and that
	 * empty itemstacks are represented by <code>null</code>.
	 * 
	 * @param itemStack
	 *            the itemstack, can be empty
	 * @param amountToIncrease
	 *            the amount to increase, can be negative to decrease
	 * @return the resulting item, or <code>null</code> if the item ends up being empty
	 */
	public static ItemStack increaseItemAmount(ItemStack itemStack, int amountToIncrease) {
		if (Utils.isEmpty(itemStack)) return null;
		int newAmount = Math.min(itemStack.getAmount() + amountToIncrease, itemStack.getMaxStackSize());
		if (newAmount <= 0) return null;
		itemStack.setAmount(newAmount);
		return itemStack;
	}

	/**
	 * Decreases the amount of the given {@link ItemStack}.
	 * <p>
	 * This makes sure that the itemstack's amount ends up to be at most {@link ItemStack#getMaxStackSize()}, and that
	 * empty itemstacks are represented by <code>null</code>.
	 * 
	 * @param itemStack
	 *            the itemstack, can be empty
	 * @param amountToDescrease
	 *            the amount to decrease, can be negative to increase
	 * @return the resulting item, or <code>null</code> if the item ends up being empty
	 */
	public static ItemStack descreaseItemAmount(ItemStack itemStack, int amountToDescrease) {
		return increaseItemAmount(itemStack, -amountToDescrease);
	}

	/**
	 * Gets an itemstack's amount and returns <code>0</code> for empty itemstacks.
	 * 
	 * @param itemStack
	 *            the itemstack, can be empty
	 * @return the itemstack's amount, or <code>0</code> if the itemstack is empty
	 */
	public static int getItemStackAmount(ItemStack itemStack) {
		return (Utils.isEmpty(itemStack) ? 0 : itemStack.getAmount());
	}

	@SuppressWarnings("deprecation")
	public static void updateInventoryLater(Player player) {
		Bukkit.getScheduler().runTaskLater(ShopkeepersPlugin.getInstance(), new Runnable() {

			@Override
			public void run() {
				player.updateInventory();
			}
		}, 3L); // TODO why exactly 3 ticks?
	}

	// value conversion utilities:

	public static Integer parseInt(String intString) {
		try {
			return Integer.parseInt(intString);
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
