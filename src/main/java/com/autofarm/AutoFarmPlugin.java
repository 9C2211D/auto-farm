package com.autofarm;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public class AutoFarmPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    
    private boolean pluginEnabled = true;
    private boolean requirePermission = true;
    private boolean giveExperience = true;
    private int experienceAmount = 1;
    private boolean playSound = true;
    private String soundType = "ENTITY_ITEM_PICKUP";
    
    // 作物与种子的映射关系
    private final Map<Material, Material> cropToSeedMap = new HashMap<>();
    
    @Override
    public void onEnable() {
        // 保存默认配置
        saveDefaultConfig();
        
        // 加载配置
        loadConfiguration();
        
        // 初始化作物映射
        initializeCropMapping();
        
        // 注册事件监听器
        Bukkit.getPluginManager().registerEvents(this, this);
        
        // 注册命令执行器
        getCommand("autofarm").setExecutor(this);
        getCommand("autofarm").setTabCompleter(this);
        
        getLogger().info("AutoFarm 插件已启用！");
        getLogger().info("支持的作物: " + cropToSeedMap.size() + " 种");
    }
    
    @Override
    public void onDisable() {
        getLogger().info("AutoFarm 插件已禁用！");
    }
    
    /**
     * 加载配置文件
     */
    private void loadConfiguration() {
        reloadConfig();
        
        pluginEnabled = getConfig().getBoolean("enabled", true);
        requirePermission = getConfig().getBoolean("require-permission", true);
        giveExperience = getConfig().getBoolean("give-experience", true);
        experienceAmount = getConfig().getInt("experience-amount", 1);
        playSound = getConfig().getBoolean("play-sound", true);
        soundType = getConfig().getString("sound-type", "ENTITY_ITEM_PICKUP");
        
        getLogger().info("配置已加载 - 插件启用: " + pluginEnabled + ", 需要权限: " + requirePermission);
    }
    
    /**
     * 初始化作物与种子的映射关系
     */
    private void initializeCropMapping() {
        cropToSeedMap.clear();
        
        // 小麦
        cropToSeedMap.put(Material.WHEAT, Material.WHEAT_SEEDS);
        
        // 胡萝卜
        cropToSeedMap.put(Material.CARROTS, Material.CARROT);
        
        // 土豆
        cropToSeedMap.put(Material.POTATOES, Material.POTATO);
        
        // 甜菜根
        cropToSeedMap.put(Material.BEETROOTS, Material.BEETROOT_SEEDS);
        
        // 下界疣
        cropToSeedMap.put(Material.NETHER_WART, Material.NETHER_WART);
        
        getLogger().info("已初始化 " + cropToSeedMap.size() + " 种作物映射");
    }
    
    /**
     * 监听玩家右键事件
     */
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        // 检查插件是否启用
        if (!pluginEnabled) {
            return;
        }
        
        // 检查是否为右键点击方块
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        
        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        
        if (block == null) {
            return;
        }
        
        // 检查权限
        if (requirePermission && !player.hasPermission("autofarm.use")) {
            return;
        }
        
        // 检查是否为支持的作物
        Material blockType = block.getType();
        if (!cropToSeedMap.containsKey(blockType)) {
            return;
        }
        
        // 检查作物是否成熟
        if (!isCropMature(block)) {
            return;
        }
        
        // 取消原始事件，防止其他插件干扰
        event.setCancelled(true);
        
        // 执行自动收获和种植
        harvestAndReplant(player, block, blockType);
    }
    
    /**
     * 检查作物是否成熟
     */
    private boolean isCropMature(Block block) {
        if (block.getBlockData() instanceof Ageable) {
            Ageable ageable = (Ageable) block.getBlockData();
            return ageable.getAge() == ageable.getMaximumAge();
        }
        return false;
    }
    
    /**
     * 收获并重新种植作物
     */
    private void harvestAndReplant(Player player, Block block, Material cropType) {
        try {
            // 获取掉落物
            Collection<ItemStack> drops = block.getDrops(player.getInventory().getItemInMainHand());
            
            // 获取对应的种子类型
            Material seedType = cropToSeedMap.get(cropType);
            
            // 重新种植（设置为刚种植的状态）
            block.setType(cropType);
            if (block.getBlockData() instanceof Ageable) {
                Ageable ageable = (Ageable) block.getBlockData();
                ageable.setAge(0); // 设置为刚种植状态
                block.setBlockData(ageable);
            }
            
            // 给予掉落物
            for (ItemStack drop : drops) {
                // 如果掉落物是种子，减少一个用于重新种植
                if (drop.getType() == seedType && drop.getAmount() > 1) {
                    drop.setAmount(drop.getAmount() - 1);
                }
                
                // 如果还有剩余物品，给予玩家
                if (drop.getAmount() > 0) {
                    // 尝试添加到玩家背包
                    HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(drop);
                    
                    // 如果背包满了，掉落到地上
                    for (ItemStack item : leftover.values()) {
                        player.getWorld().dropItemNaturally(block.getLocation(), item);
                    }
                }
            }
            
            // 给予经验
            if (giveExperience && experienceAmount > 0) {
                player.giveExp(experienceAmount);
            }
            
            // 播放声音
            if (playSound) {
                try {
                    org.bukkit.Sound sound = org.bukkit.Sound.valueOf(soundType);
                    player.playSound(block.getLocation(), sound, 1.0f, 1.0f);
                } catch (IllegalArgumentException e) {
                    getLogger().log(Level.WARNING, "无效的声音类型: " + soundType, e);
                }
            }
            
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "自动种植时发生错误", e);
            player.sendMessage(ChatColor.RED + "自动种植时发生错误，请查看控制台日志。");
        }
    }
    
    /**
     * 重新加载配置
     */
    public void reloadConfiguration() {
        loadConfiguration();
        initializeCropMapping();
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("autofarm")) {
            if (args.length == 0) {
                sender.sendMessage(ChatColor.YELLOW + "=== AutoFarm 插件 ===");
                sender.sendMessage(ChatColor.GREEN + "版本: " + getDescription().getVersion());
                sender.sendMessage(ChatColor.GREEN + "状态: " + (pluginEnabled ? ChatColor.GREEN + "启用" : ChatColor.RED + "禁用"));
                sender.sendMessage(ChatColor.GREEN + "支持作物: " + cropToSeedMap.size() + " 种");
                sender.sendMessage(ChatColor.AQUA + "使用 /autofarm reload 重新加载配置");
                sender.sendMessage(ChatColor.AQUA + "使用 /autofarm info 查看详细信息");
                return true;
            }
            
            if (args[0].equalsIgnoreCase("reload")) {
                if (!sender.hasPermission("autofarm.reload")) {
                    sender.sendMessage(ChatColor.RED + "你没有权限执行此命令！");
                    return true;
                }
                
                reloadConfiguration();
                sender.sendMessage(ChatColor.GREEN + "AutoFarm 配置已重新加载！");
                sender.sendMessage(ChatColor.GREEN + "插件状态: " + (pluginEnabled ? "启用" : "禁用"));
                return true;
            }
            
            if (args[0].equalsIgnoreCase("info")) {
                if (!sender.hasPermission("autofarm.info")) {
                    sender.sendMessage(ChatColor.RED + "你没有权限执行此命令！");
                    return true;
                }
                
                sender.sendMessage(ChatColor.YELLOW + "=== AutoFarm 详细信息 ===");
                sender.sendMessage(ChatColor.GREEN + "插件版本: " + getDescription().getVersion());
                sender.sendMessage(ChatColor.GREEN + "插件状态: " + (pluginEnabled ? ChatColor.GREEN + "启用" : ChatColor.RED + "禁用"));
                sender.sendMessage(ChatColor.GREEN + "需要权限: " + (requirePermission ? "是" : "否"));
                sender.sendMessage(ChatColor.GREEN + "给予经验: " + (giveExperience ? "是 (" + experienceAmount + " 点)" : "否"));
                sender.sendMessage(ChatColor.GREEN + "播放声音: " + (playSound ? "是 (" + soundType + ")" : "否"));
                sender.sendMessage(ChatColor.YELLOW + "支持的作物:");
                for (Map.Entry<Material, Material> entry : cropToSeedMap.entrySet()) {
                    sender.sendMessage(ChatColor.GRAY + "- " + entry.getKey().name() + " -> " + entry.getValue().name());
                }
                return true;
            }
        }
        
        return false;
    }
    
    @Override
    public java.util.List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("autofarm")) {
            if (args.length == 1) {
                java.util.List<String> completions = new java.util.ArrayList<>();
                if (sender.hasPermission("autofarm.reload")) {
                    completions.add("reload");
                }
                if (sender.hasPermission("autofarm.info")) {
                    completions.add("info");
                }
                return completions;
            }
        }
        return null;
    }
}