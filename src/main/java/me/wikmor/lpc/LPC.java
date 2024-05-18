package me.wikmor.lpc;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.papermc.paper.event.player.AsyncChatEvent;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.cacheddata.CachedMetaData;
import org.apache.commons.lang3.text.WordUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class LPC extends JavaPlugin implements Listener {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private final Cache<String, CachedInventory> cachedInventories = CacheBuilder.newBuilder().expireAfterWrite(30, TimeUnit.SECONDS).build();
    private LuckPerms luckPerms;

    private boolean chatMuted = false;

    @Override
    public void onEnable() {
        // Load an instance of 'LuckPerms' using the services manager.
        this.luckPerms = getServer().getServicesManager().load(LuckPerms.class);

        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (onlinePlayer.getOpenInventory().getTopInventory().getHolder() instanceof InventorySnapshot) {
                onlinePlayer.closeInventory();
            }
        }
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        if (label.equalsIgnoreCase("inv")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(colorize("&cYou must be a player to execute this command."));
                return true;
            }

            if (args.length != 1) {
                sender.sendMessage(colorize("&cUsage: /inv <player>"));
                return true;
            }

            CachedInventory cachedInventory = cachedInventories.getIfPresent(args[0].toLowerCase());

            if (cachedInventory == null) {
                sender.sendMessage(colorize("&cInvalid inventory. User hasn't advertised their inventory in the last 30 seconds."));
                return true;
            }

            Inventory inventory = new InventorySnapshot().getInventory();

            inventory.setContents(cachedInventory.getContents());

            ((Player) sender).openInventory(inventory);
            return true;
        }

        if (!sender.hasPermission("lpc.admin")) {
            return false;
        }

        if (args.length == 1 && "reload".equals(args[0])) {
            reloadConfig();

            sender.sendMessage(colorize("&aLPC has been reloaded."));
            return true;
        } else if (args.length == 1 && "mute".equals(args[0])) {
            chatMuted = !chatMuted;

            sender.sendMessage(colorize("&aChat has been " + (chatMuted ? "muted." : "unmuted.")));
            return true;
        }

        return false;
    }

	/*@EventHandler(priority = EventPriority.HIGH)
	public void onChat(final AsyncPlayerChatEvent event) {
		final String message = event.getMessage();
		final Player player = event.getPlayer();

		// Get a LuckPerms cached metadata for the player.
		final CachedMetaData metaData = this.luckPerms.getPlayerAdapter(Player.class).getMetaData(player);
		final String group = metaData.getPrimaryGroup();

		String format = getConfig().getString(getConfig().getString("group-formats." + group) != null ? "group-formats." + group : "chat-format")
				.replace("{prefix}", metaData.getPrefix() != null ? metaData.getPrefix() : "")
				.replace("{suffix}", metaData.getSuffix() != null ? metaData.getSuffix() : "")
				.replace("{prefixes}", metaData.getPrefixes().keySet().stream().map(key -> metaData.getPrefixes().get(key)).collect(Collectors.joining()))
				.replace("{suffixes}", metaData.getSuffixes().keySet().stream().map(key -> metaData.getSuffixes().get(key)).collect(Collectors.joining()))
				.replace("{world}", player.getWorld().getName())
				.replace("{name}", player.getName())
				.replace("{displayname}", player.getDisplayName())
				.replace("{username-color}", metaData.getMetaValue("username-color") != null ? metaData.getMetaValue("username-color") : "")
				.replace("{message-color}", metaData.getMetaValue("message-color") != null ? metaData.getMetaValue("message-color") : "");

		format = colorize(translateHexColorCodes(getServer().getPluginManager().isPluginEnabled("PlaceholderAPI") ? PlaceholderAPI.setPlaceholders(player, format) : format));

		event.setFormat(format.replace("%", "%%").replace("{message}", "%2$s"));
		event.setMessage(player.hasPermission("lpc.colorcodes") && player.hasPermission("lpc.rgbcodes")
				? colorize(translateHexColorCodes(message)) : player.hasPermission("lpc.colorcodes") ? colorize(message) : player.hasPermission("lpc.rgbcodes")
				? translateHexColorCodes(message) : message.replace("%", "%%"));
	}*/

    @Override
    public List<String> onTabComplete(final CommandSender sender, final Command command, final String alias, final String[] args) {
        if (alias.equalsIgnoreCase("inv") || !sender.hasPermission("lpc.admin")) {
            return Collections.emptyList();
        }

        if (args.length == 1) return Collections.singletonList("reload");

        return new ArrayList<>();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof InventorySnapshot) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(final AsyncChatEvent event) {
        Component component = event.message();
        event.setCancelled(true);

        if (chatMuted) {
            event.getPlayer().sendMessage(colorize("&cChat is currently muted."));
            return;
        }

        if (event.getPlayer().hasPermission("lpc.chat.item")) {
            component = component.replaceText(TextReplacementConfig.builder().match(Pattern.compile("\\[item]|\\[i]")).replacement(builder -> formatItemInHand(event.getPlayer())).once().build());
        }

        if (event.getPlayer().hasPermission("lpc.chat.inventory")) {
            component = component.replaceText(TextReplacementConfig.builder().match(Pattern.compile("\\[inventory]|\\[inv]")).replacement(builder -> formatInventory(event.getPlayer())).once().build());
        }

        final Player player = event.getPlayer();
        final CachedMetaData metaData = this.luckPerms.getPlayerAdapter(Player.class).getMetaData(player);
        final String group = metaData.getPrimaryGroup();

        String format = getConfig().getString(getConfig().getString("group-formats." + group) != null ? "group-formats." + group : "chat-format").replace("{prefix}", metaData.getPrefix() != null ? metaData.getPrefix() : "").replace("{suffix}", metaData.getSuffix() != null ? metaData.getSuffix() : "").replace("{prefixes}", metaData.getPrefixes().keySet().stream().map(key -> metaData.getPrefixes().get(key)).collect(Collectors.joining())).replace("{suffixes}", metaData.getSuffixes().keySet().stream().map(key -> metaData.getSuffixes().get(key)).collect(Collectors.joining())).replace("{world}", player.getWorld().getName()).replace("{name}", player.getName()).replace("{displayname}", player.getDisplayName()).replace("{username-color}", metaData.getMetaValue("username-color") != null ? metaData.getMetaValue("username-color") : "").replace("{message-color}", metaData.getMetaValue("message-color") != null ? metaData.getMetaValue("message-color") : "");

        format = colorize(translateHexColorCodes(getServer().getPluginManager().isPluginEnabled("PlaceholderAPI") ? PlaceholderAPI.setPlaceholders(player, format) : format));

        final Component formatComponent = LegacyComponentSerializer.legacySection().deserialize(format);
        Component finalComponent = component;
        final Component toSend = formatComponent.replaceText(TextReplacementConfig.builder().match(Pattern.compile("\\{message}|\\{msg}")).replacement(() -> finalComponent).once().build());

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            onlinePlayer.sendMessage(player, toSend);
        }
    }

    private String colorize(final String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    private String translateHexColorCodes(final String message) {
        final char colorChar = ChatColor.COLOR_CHAR;

        final Matcher matcher = HEX_PATTERN.matcher(message);
        final StringBuffer buffer = new StringBuffer(message.length() + 4 * 8);

        while (matcher.find()) {
            final String group = matcher.group(1);

            matcher.appendReplacement(buffer, colorChar + "x" + colorChar + group.charAt(0) + colorChar + group.charAt(1) + colorChar + group.charAt(2) + colorChar + group.charAt(3) + colorChar + group.charAt(4) + colorChar + group.charAt(5));
        }

        return matcher.appendTail(buffer).toString();
    }

    private Component formatItemInHand(Player player) {
        ItemStack itemInMainHand = player.getEquipment().getItemInMainHand();

        if (!itemInMainHand.hasItemMeta() || !itemInMainHand.getItemMeta().hasDisplayName()) {
            return Component.text("[" + WordUtils.capitalizeFully(itemInMainHand.getType().name().toLowerCase().replace("_", " ")) + (itemInMainHand.getAmount() > 1 ? " x" + itemInMainHand.getAmount() : "") + "]").hoverEvent(itemInMainHand.asHoverEvent());
        }

        return Component.text("[").append(itemInMainHand.getItemMeta().displayName()).append(Component.text((itemInMainHand.getAmount() > 1 ? (" x" + itemInMainHand.getAmount()) : "") + "]")).hoverEvent(itemInMainHand.asHoverEvent());
    }

    private Component formatInventory(Player player) {
        final ItemStack[] contents = player.getInventory().getStorageContents();
        final UUID holder = player.getUniqueId();

        cachedInventories.put(player.getName().toLowerCase(), new CachedInventory(holder, contents.clone()));

        return Component.text("[" + player.getName() + "'s Inventory]").hoverEvent(Component.text("Click to view " + player.getName() + "'s inventory.").color(NamedTextColor.GRAY)).clickEvent(ClickEvent.clickEvent(ClickEvent.Action.RUN_COMMAND, "/inv " + player.getName()));
    }

    private final class CachedInventory {

        private final UUID holder;
        private final ItemStack[] contents;

        public CachedInventory(UUID holder, ItemStack[] contents) {
            this.holder = holder;
            this.contents = contents;
        }

        public UUID getHolder() {
            return holder;
        }

        public ItemStack[] getContents() {
            return contents;
        }
    }

    private final class InventorySnapshot implements InventoryHolder {

        private final Inventory inventory = Bukkit.createInventory(this, 36, "Inventory Snapshot");

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }
}