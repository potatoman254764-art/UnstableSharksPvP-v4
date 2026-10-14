package com.unstablesharks.pvp;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.Particle;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.projectiles.ProjectileSource;
import java.io.File;
import java.io.IOException;
import java.util.*;

public final class UnstableSharksPvP extends JavaPlugin implements Listener {
    private final Map<UUID,Integer> streaks = new HashMap<>();
    private File dataFile;
    private YamlConfiguration data;
    private final String deathMenuTitle = ChatColor.DARK_RED + "Death Sounds";
    private static final List<String> DEATH_SOUNDS = List.of("WITHER", "THUNDER", "DRAGON", "EXPLOSION", "LEVELUP", "ANVIL", "OFF");
    private final String settingsTitle = ChatColor.DARK_AQUA + "Sharks PvP Settings";
    private final String menuTitle = ChatColor.DARK_AQUA + "UnstableSharks Kits";
    @Override public void onEnable() {
        saveDefaultConfig();
        dataFile = new File(getDataFolder(), "data.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);
        Bukkit.getPluginManager().registerEvents(this,this);
        getLogger().info("UnstableSharksPvP v4 enabled; kits use PlayerKits2, protection uses WorldGuard.");
    }
    @Override public void onDisable() { saveData(); }
    private void saveData() { try { data.save(dataFile); } catch(IOException e) { getLogger().severe("Failed to save data: "+e.getMessage()); } }
    private String c(String s) { return ChatColor.translateAlternateColorCodes('&',s == null ? "" : s); }
    private boolean inWorld(World w) { List<String> worlds=getConfig().getStringList("pvp-worlds"); return worlds.isEmpty() || worlds.contains(w.getName()); }
    private String path(UUID id,String key) { return "players."+id+"."+key; }
    private int stat(UUID id,String key) { return data.getInt(path(id,key)); }
    private void inc(UUID id,String key,int n) { data.set(path(id,key),stat(id,key)+n); }
    private String name(UUID id) { return data.getString(path(id,"name"),id.toString()); }
    private void remember(Player p) { data.set(path(p.getUniqueId(),"name"),p.getName()); }
    @EventHandler public void mobHit(EntityDamageByEntityEvent e) {
        if (!getConfig().getBoolean("prevent-mob-damage-to-players") || !(e.getEntity() instanceof Player) || !inWorld(e.getEntity().getWorld())) return;
        if (e.getDamager() instanceof Monster) e.setCancelled(true);
        if (e.getDamager() instanceof Projectile projectile) { ProjectileSource shooter=projectile.getShooter(); if(shooter instanceof Monster) e.setCancelled(true); }
    }
    @EventHandler public void spawn(CreatureSpawnEvent e) {
        if(getConfig().getBoolean("prevent-natural-monster-spawning") && e.getEntity() instanceof Monster && inWorld(e.getLocation().getWorld()) && e.getSpawnReason()==CreatureSpawnEvent.SpawnReason.NATURAL) e.setCancelled(true);
    }
    @EventHandler public void death(PlayerDeathEvent e) {
        Player victim=e.getEntity(); if(!inWorld(victim.getWorld())) return;
        remember(victim); UUID vid=victim.getUniqueId(); inc(vid,"deaths",1); streaks.remove(vid);
        Player killer=victim.getKiller();
        if(killer!=null && !killer.getUniqueId().equals(vid)) {
            remember(killer); UUID kid=killer.getUniqueId(); inc(kid,"kills",1);
            playDeathSound(killer, victim);
            if (enabled(killer,"kill-particles",true)) victim.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING,victim.getLocation().add(0,1,0),18,0.45,0.5,0.45,0.08);
            if (enabled(killer,"kill-title",true)) killer.sendTitle(c("&c&lELIMINATION"),c("&f"+victim.getName()+" &8+"+getConfig().getInt("coins-per-kill",10)+" coins"),5,30,10);
            int streak=streaks.merge(kid,1,Integer::sum);
            int reward=getConfig().getInt("coins-per-kill",10);
            inc(kid,"coins",reward);
            int bounty=stat(vid,"bounty");
            if(bounty>0) {
                inc(kid,"coins",bounty); data.set(path(vid,"bounty"),0);
                Bukkit.broadcastMessage(c("&6[Bounty] &e"+killer.getName()+" &fclaimed &6"+bounty+" &fcoins on &c"+victim.getName()));
            }
            if(getConfig().getIntegerList("killstreak-announcements").contains(streak)) {
                Bukkit.broadcastMessage(c("&c[UnstableSharks] &e"+killer.getName()+" &freached &6"+streak+" &fkillstreak!"));
                int bonus=getConfig().getInt("streak-rewards."+streak,0);
                if(bonus>0) { inc(kid,"coins",bonus); killer.sendMessage(c("&aStreak reward: &6"+bonus+" coins")); }
            }
        }
        saveData();
    }

    private boolean enabled(Player p,String key,boolean fallback) { return data.getBoolean(path(p.getUniqueId(),"settings."+key),fallback); }
    private void toggle(Player p,String key) { data.set(path(p.getUniqueId(),"settings."+key),!enabled(p,key,true)); saveData(); }
    private ItemStack menuItem(Material mat,String title,String lore) {
        ItemStack item=new ItemStack(mat); ItemMeta meta=item.getItemMeta();
        meta.setDisplayName(c(title)); meta.setLore(List.of(c(lore))); item.setItemMeta(meta); return item;
    }
    private void settings(Player p) {
        Inventory inv=Bukkit.createInventory(null,27,settingsTitle);
        inv.setItem(10,menuItem(Material.NOTE_BLOCK,"&d&lDeath Sounds",enabled(p,"death-sounds",true)?"&aON &7| Click to toggle":"&cOFF &7| Click to toggle"));
        inv.setItem(11,menuItem(Material.BLAZE_POWDER,"&6&lKill Particles",enabled(p,"kill-particles",true)?"&aON &7| Click to toggle":"&cOFF &7| Click to toggle"));
        inv.setItem(12,menuItem(Material.NAME_TAG,"&b&lKill Titles",enabled(p,"kill-title",true)?"&aON &7| Click to toggle":"&cOFF &7| Click to toggle"));
        inv.setItem(14,menuItem(Material.WITHER_SKELETON_SKULL,"&c&lChoose Death Sound","&7Click to open sound selection"));
        inv.setItem(15,menuItem(Material.DIAMOND_SWORD,"&b&lKits","&7Click to open kit selector"));
        inv.setItem(16,menuItem(Material.PAPER,"&e&lStats","&7Use /pvpstats to view stats"));
        inv.setItem(22,menuItem(Material.BARRIER,"&cClose","&7Settings save automatically"));
        p.openInventory(inv);
    }
    @EventHandler public void settingsClick(InventoryClickEvent e) {
        if(!e.getView().getTitle().equals(settingsTitle)) return;
        e.setCancelled(true);
        if(!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory()==null || !e.getClickedInventory().equals(e.getView().getTopInventory())) return;
        switch(e.getRawSlot()) {
            case 10 -> { toggle(p,"death-sounds"); settings(p); }
            case 11 -> { toggle(p,"kill-particles"); settings(p); }
            case 12 -> { toggle(p,"kill-title"); settings(p); }
            case 14 -> openDeathSounds(p);
            case 15 -> kits(p);
            case 16 -> { p.closeInventory(); stats(p,p.getUniqueId()); }
            case 22 -> p.closeInventory();
            default -> { }
        }
    }
    // Optional hotbar shortcut: right-click a tagged compass, no client mod required.
    @EventHandler public void shortcut(PlayerInteractEvent e) {
        if(e.getAction()!=Action.RIGHT_CLICK_AIR && e.getAction()!=Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item=e.getItem(); if(item==null || item.getType()!=Material.COMPASS || !item.hasItemMeta()) return;
        Byte marker=item.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(this,"settings_shortcut"),PersistentDataType.BYTE);
        if(marker==null || marker!=1) return;
        e.setCancelled(true);
        Bukkit.getScheduler().runTask(this,()->settings(e.getPlayer()));
    }
    private void giveShortcut(Player p) {
        ItemStack item=menuItem(Material.COMPASS,"&b&lSharks Settings","&7Right-click to open PvP Settings");
        ItemMeta meta=item.getItemMeta(); meta.getPersistentDataContainer().set(new NamespacedKey(this,"settings_shortcut"),PersistentDataType.BYTE,(byte)1); item.setItemMeta(meta);
        HashMap<Integer,ItemStack> leftover=p.getInventory().addItem(item);
        if(!leftover.isEmpty()) p.sendMessage(c("&cMake one free inventory slot first."));
        else p.sendMessage(c("&aRight-click your compass to open settings!"));
    }
    private String chosenSound(Player player) {
        String selected=data.getString(path(player.getUniqueId(),"death-sound"), getConfig().getString("death-sounds.default", "WITHER"));
        selected=selected==null ? "WITHER" : selected.toUpperCase(Locale.ROOT);
        return DEATH_SOUNDS.contains(selected) ? selected : "WITHER";
    }
    private Sound resolveSound(String key) {
        String name=switch(key) {
            case "WITHER" -> "ENTITY_WITHER_DEATH";
            case "THUNDER" -> "ENTITY_LIGHTNING_BOLT_THUNDER";
            case "DRAGON" -> "ENTITY_ENDER_DRAGON_DEATH";
            case "EXPLOSION" -> "ENTITY_GENERIC_EXPLODE";
            case "LEVELUP" -> "ENTITY_PLAYER_LEVELUP";
            case "ANVIL" -> "BLOCK_ANVIL_LAND";
            default -> "ENTITY_WITHER_DEATH";
        };
        try { return Sound.valueOf(name); } catch(IllegalArgumentException ex) { return null; }
    }
    private void playDeathSound(Player killer, Player victim) {
        if(!getConfig().getBoolean("death-sounds.enabled",true) || !enabled(killer,"death-sounds",true)) return;
        String selected=chosenSound(killer);
        if(selected.equals("OFF")) return;
        Sound sound=resolveSound(selected);
        if(sound==null) { getLogger().warning("Unknown death sound: "+selected); return; }
        float volume=(float)Math.max(0,Math.min(2,getConfig().getDouble("death-sounds.volume",0.8)));
        float pitch=(float)Math.max(0.5,Math.min(2,getConfig().getDouble("death-sounds.pitch",1.0)));
        if(getConfig().getBoolean("death-sounds.play-to-nearby",false)) {
            victim.getWorld().playSound(victim.getLocation(),sound,volume,pitch);
        } else {
            killer.playSound(killer.getLocation(),sound,volume,pitch);
        }
    }
    private void openDeathSounds(Player player) {
        Inventory inv=Bukkit.createInventory(null,9,deathMenuTitle);
        Material[] icons={Material.WITHER_SKELETON_SKULL,Material.LIGHTNING_ROD,Material.DRAGON_HEAD,Material.TNT,Material.EXPERIENCE_BOTTLE,Material.ANVIL,Material.BARRIER};
        String current=chosenSound(player);
        for(int i=0;i<DEATH_SOUNDS.size();i++) {
            String key=DEATH_SOUNDS.get(i);
            ItemStack item=new ItemStack(icons[i]); ItemMeta meta=item.getItemMeta();
            meta.setDisplayName(c((key.equals(current)?"&a&l✔ ":"&f")+key));
            meta.setLore(List.of(c("&7Click to select"),c(key.equals(current)?"&aCurrently selected":"&8Saved per player")));
            item.setItemMeta(meta); inv.setItem(i,item);
        }
        player.openInventory(inv);
    }
    @EventHandler public void deathSoundMenuClick(InventoryClickEvent e) {
        if(!e.getView().getTitle().equals(deathMenuTitle)) return;
        e.setCancelled(true);
        if(!(e.getWhoClicked() instanceof Player player) || e.getClickedInventory()==null || !e.getClickedInventory().equals(e.getView().getTopInventory())) return;
        int slot=e.getRawSlot(); if(slot<0 || slot>=DEATH_SOUNDS.size()) return;
        String selected=DEATH_SOUNDS.get(slot);
        data.set(path(player.getUniqueId(),"death-sound"),selected);
        saveData();
        player.closeInventory();
        player.sendMessage(c("&8[&cUnstableSharks&8] &aDeath sound: &f"+selected));
        if(!selected.equals("OFF")) {
            Sound sound=resolveSound(selected);
            if(sound!=null) player.playSound(player.getLocation(),sound,0.7f,1f);
        }
    }

    private void stats(CommandSender viewer,UUID id) {
        int kills=stat(id,"kills"), deaths=stat(id,"deaths");
        double kdr=deaths==0?kills:(double)kills/deaths;
        viewer.sendMessage(c("&8&m------------------------"));
        viewer.sendMessage(c("&b&l"+name(id)+" &fPvP Stats"));
        viewer.sendMessage(c("&aKills: &f"+kills+" &cDeaths: &f"+deaths));
        viewer.sendMessage(c("&eKDR: &f"+String.format(Locale.US,"%.2f",kdr)+" &6Coins: &f"+stat(id,"coins")));
        viewer.sendMessage(c("&dBounty: &f"+stat(id,"bounty")+" &bStreak: &f"+streaks.getOrDefault(id,0)));
    }
    private void top(CommandSender sender) {
        List<UUID> ids=new ArrayList<>();
        if(data.isConfigurationSection("players")) for(String raw:data.getConfigurationSection("players").getKeys(false)) {
            try { ids.add(UUID.fromString(raw)); } catch(IllegalArgumentException ignored) {}
        }
        ids.sort((a,b)->Integer.compare(stat(b,"kills"),stat(a,"kills")));
        sender.sendMessage(c("&b&lUnstableSharks &fTop Kills"));
        for(int i=0;i<Math.min(10,ids.size());i++) sender.sendMessage(c("&e#"+(i+1)+" &f"+name(ids.get(i))+" &7- &c"+stat(ids.get(i),"kills")));
        if(ids.isEmpty()) sender.sendMessage(c("&7No kills recorded yet."));
    }
    private void kits(Player player) {
        List<String> kits=getConfig().getStringList("kits");
        int size=Math.min(54,Math.max(9,((kits.size()+8)/9)*9));
        Inventory inv=Bukkit.createInventory(null,size,menuTitle);
        for(int i=0;i<Math.min(size,kits.size());i++) {
            ItemStack item=new ItemStack(Material.IRON_SWORD); ItemMeta meta=item.getItemMeta();
            meta.setDisplayName(c("&b&l"+kits.get(i)));
            meta.setLore(List.of(c("&7Click to select this kit")));
            item.setItemMeta(meta); inv.setItem(i,item);
        }
        player.openInventory(inv);
    }
    @EventHandler public void click(InventoryClickEvent e) {
        if(!e.getView().getTitle().equals(menuTitle)) return;
        e.setCancelled(true);
        if(!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory()==null || !e.getClickedInventory().equals(e.getView().getTopInventory())) return;
        int slot=e.getSlot(); List<String> kits=getConfig().getStringList("kits");
        if(slot<0 || slot>=kits.size()) return;
        String kit=kits.get(slot);
        p.closeInventory();
        // PlayerKits2 retains kit permissions and cooldown enforcement.
        String command=getConfig().getString("kit-command","kit %kit%").replace("%kit%",kit).replace("%player%",p.getName());
        if(command.startsWith("/")) command=command.substring(1);
        p.performCommand(command);
    }
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args) {
        String cmd=command.getName().toLowerCase(Locale.ROOT);
        if(cmd.equals("uspvp")) {
            if(!sender.hasPermission("unstablesharks.admin")) { sender.sendMessage(c("&cNo permission")); return true; }
            if(args.length==1 && args[0].equalsIgnoreCase("reload")) { reloadConfig(); sender.sendMessage(c("&aConfiguration reloaded")); }
            else sender.sendMessage(c("&e/uspvp reload")); return true;
        }
        if(cmd.equals("pvpsettings")) {
            if(sender instanceof Player p) { remember(p); settings(p); }
            else sender.sendMessage("Players only.");
            return true;
        }
        if(cmd.equals("settingsitem")) {
            if(sender instanceof Player p) giveShortcut(p);
            else sender.sendMessage("Players only.");
            return true;
        }
        if(cmd.equals("pvptop")) { top(sender); return true; }
        if(cmd.equals("deathsounds")) {
            if(sender instanceof Player player) { remember(player); openDeathSounds(player); }
            else sender.sendMessage("Players only.");
            return true;
        }
        if(cmd.equals("pvpstats")) {
            if(args.length>0) { Player other=Bukkit.getPlayerExact(args[0]); if(other==null) { sender.sendMessage(c("&cPlayer must be online.")); return true; } remember(other); stats(sender,other.getUniqueId()); }
            else if(sender instanceof Player p) { remember(p); stats(sender,p.getUniqueId()); }
            else sender.sendMessage("Use /pvpstats <online player>");
            return true;
        }
        if(!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
        remember(p);
        if(cmd.equals("pvpkits")) { kits(p); return true; }
        if(cmd.equals("pvpcoins")) { p.sendMessage(c("&6Coins: &f"+stat(p.getUniqueId(),"coins"))); return true; }
        if(cmd.equals("bounty")) {
            if(args.length!=2) { p.sendMessage(c("&e/bounty <online player> <coins>")); return true; }
            Player target=Bukkit.getPlayerExact(args[0]); if(target==null || target.equals(p)) { p.sendMessage(c("&cChoose another online player.")); return true; }
            int amount;
            try { amount=Integer.parseInt(args[1]); } catch(NumberFormatException ex) { p.sendMessage(c("&cInvalid amount.")); return true; }
            if(amount<getConfig().getInt("minimum-bounty",25) || amount>1000000 || stat(p.getUniqueId(),"coins")<amount) { p.sendMessage(c("&cNot enough coins or invalid bounty amount.")); return true; }
            inc(p.getUniqueId(),"coins",-amount); remember(target); inc(target.getUniqueId(),"bounty",amount); saveData();
            Bukkit.broadcastMessage(c("&6[Bounty] &e"+p.getName()+" &fplaced &6"+amount+" &fcoins on &c"+target.getName()));
            return true;
        }
        return false;
    }
}
