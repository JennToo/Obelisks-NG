package nl.nitori.ObelisksNG;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import net.milkbowl.vault.economy.Economy;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class Obelisks extends JavaPlugin implements Listener {
    /** Permission constants */
    private static final String CREATE_OB_PERM = "obelisk.create";
    private static final String USE_OB_PERM = "obelisk.use";
    private static final String DESTROY_OB_OVERRIDE_PERM = "obelisk.destroy-override";

    /** Separates the sections in the config file */
    private static final String DELIMITOR = "&---------------&";

    /** Maps Obelisks from their (unique) names */
    private HashMap<String, Obelisk> obeliskByName;

    /** Maps Obelisks from their locations */
    private HashMap<Location, Obelisk> obeliskByLocation;

    /** Stores which players have discovered which Obelisks */
    private HashMap<String, List<String>> discovered;

    private int createCost, useCost, learnCost;

    private Economy econ;

    public static Obelisks inst = null;

    @Override
    public void onEnable() {
        inst = this;

        obeliskByName = new HashMap<String, Obelisk>();
        obeliskByLocation = new HashMap<Location, Obelisk>();
        discovered = new HashMap<String, List<String>>();
        getServer().getPluginManager().registerEvents(this, this);

        // Init economy-link
        if (getServer().getPluginManager().getPlugin("Vault") != null) {
            RegisteredServiceProvider<Economy> rsp = getServer()
                    .getServicesManager().getRegistration(Economy.class);
            if (rsp != null) {
                econ = rsp.getProvider();
            }
        }
        if (econ == null) {
            getLogger().severe("Could not load Vault. Disabling Obelisks");
            getServer().getPluginManager().disablePlugin(this);
        }

        loadFromFile();
    }

    /*
     * Checks for when an Obelisk is being created
     */
    @EventHandler
    public void onSignChange(SignChangeEvent e) {
        Block b = e.getBlock();
        if (b.getType() == Material.WALL_SIGN) {
            Player p = e.getPlayer();
            String[] lines = e.getLines();

            if (!lines[0].trim().equals("Obelisk")) {
                return;
            }

            if (!p.hasPermission(CREATE_OB_PERM)) {
                p.sendMessage("You don't have permission to create Obelisks");
                return;
            }

            if (!Obelisk.validateLocation(b.getLocation(), lines)) {
                p.sendMessage("Your Obelisk is incorrect. Check the Game Guide");
                return;
            }

            if (obeliskByName.containsKey(lines[1].trim())) {
                p.sendMessage("An obelisk by that name already exists");
                return;
            }

            if (econ.getBalance(p.getName()) < createCost) {
                p.sendMessage("You don't have enough money to create an Obelisk");
                return;
            }

            // Create and register the Obelisk
            Obelisk created = new Obelisk(p.getName(), lines[1].trim(),
                    b.getLocation());
            obeliskByName.put(created.getName(), created);
            obeliskByLocation.put(created.getLocation(), created);
            discover(p, created, false);

            econ.withdrawPlayer(p.getName(), createCost);

            p.sendMessage("Created a new Obelisk named " + created.getName());
            getLogger().info(
                    "Created Obelisk " + created.getName() + " by player "
                            + p.getName() + " at " + created.getLocation());

            created.validate();

            e.setLine(
                    0,
                    ChatColor.DARK_GREEN.toString()
                            + ChatColor.UNDERLINE.toString()
                            + ChatColor.BOLD.toString() + "Obelisk");

            saveToFile();
        }
    }

    //
    // @EventHandler
    // void onHangBreak(HangingBreakEvent e) {
    // getLogger().info("Loc: " + e.getEntity().getLocation());
    // if (obeliskByLocation.containsKey(e.getEntity().getLocation())) {
    // e.setCancelled(true);
    // }
    // }

    /*
     * Checks for left and right clicks
     */
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent e) {
        Block b = e.getClickedBlock();
        if (b == null)
            return;

        Obelisk ob = obeliskByLocation.get(b.getLocation());
        if (ob != null) {
            Player p = e.getPlayer();

            // Validate the source Obelisk first
            ob.validate();
            if (!ob.isValid()) {
                p.sendMessage("You must repair this Obelisk before using it");
                return;
            }

            if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
                // Scrolling through destinations

                if (!p.hasPermission(USE_OB_PERM)) {
                    p.sendMessage("You don't have permission to use Obelisks");
                    return;
                }

                // The Obelisk needs to be discovered before it can be used
                if (discovered.get(p.getName()) == null) {
                    discover(p, ob, true);
                    return;
                }
                int myindex = discovered.get(p.getName()).indexOf(ob.getName());
                if (myindex == -1) {
                    discover(p, ob, true);
                    return;
                }

                // Attach current index of destination to player
                if (!p.hasMetadata("OB-DEST")) {
                    p.setMetadata("OB-DEST", new FixedMetadataValue(this,
                            findAfter(myindex, myindex, p.getName())));
                } else {
                    int oldIndex = p.getMetadata("OB-DEST").get(0).asInt();
                    p.setMetadata("OB-DEST", new FixedMetadataValue(this,
                            findAfter(oldIndex, myindex, p.getName())));
                }

                // Validate the destination as well
                String destName = discovered.get(p.getName()).get(
                        p.getMetadata("OB-DEST").get(0).asInt());
                Obelisk dest = obeliskByName.get(destName);
                dest.validate();
                if (!dest.isValid()) {
                    destName = ChatColor.DARK_RED + destName;
                } else {
                    destName = ChatColor.DARK_GREEN + destName;
                }
                p.sendMessage("Destination: " + destName);

            } else if (e.getAction() == Action.LEFT_CLICK_BLOCK) {
                // Go to selected destination

                if (!p.hasPermission(USE_OB_PERM)) {
                    p.sendMessage("You don't have permission to use Obelisks");
                    return;
                }

                // This Obelisk needs to be discovered before using
                if (hasDiscovered(p.getName(), ob.getName())) {
                    // Check that a destination has been selected
                    if (!p.hasMetadata("OB-DEST")) {
                        p.sendMessage("Right click to select destination, then left click to be transported");
                    } else {
                        Obelisk dest = obeliskByName.get(discovered.get(
                                p.getName()).get(
                                p.getMetadata("OB-DEST").get(0).asInt()));
                        p.removeMetadata("OB-DEST", this);

                        // Validate the source and destination
                        ob.validate();
                        dest.validate();
                        if (!ob.isValid()) {
                            p.sendMessage("This Obelisk needs to be repaired first");
                            return;
                        } else if (!dest.isValid()) {
                            p.sendMessage("The destination Obelisk needs to be repaired first");
                            return;
                        }

                        if (econ.getBalance(p.getName()) < useCost) {
                            p.sendMessage("You don't have enough money to use an Obelisk");
                            return;
                        }
                        econ.withdrawPlayer(p.getName(), useCost);
                        econ.depositPlayer(dest.getOwner(), useCost);

                        ob.doStrike();
                        dest.doStrike();

                        p.teleport(dest.getLocation());
                        getLogger().info(
                                "Teleporting player " + p.getName() + " from "
                                        + ob.getName() + "@" + ob.getLocation()
                                        + " to " + dest.getName() + "@"
                                        + dest.getLocation());
                    }
                } else {
                    discover(p, ob, true);
                }
            }
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        if (!e.isCancelled()) {
            Block b = e.getBlock();
            Player p = e.getPlayer();
            if (p == null) {
                return;
            }

            if (b.getType() == Material.OBSIDIAN) {
                // Check if any Obsidian being broken is holding up a wall sign
                Location t1 = b.getLocation().add(1, 0, 0);
                Location t2 = b.getLocation().add(-1, 0, 0);
                Location t3 = b.getLocation().add(0, 0, 1);
                Location t4 = b.getLocation().add(0, 0, -1);
                if (t1.getBlock().getType() == Material.WALL_SIGN
                        || t2.getBlock().getType() == Material.WALL_SIGN
                        || t3.getBlock().getType() == Material.WALL_SIGN
                        || t4.getBlock().getType() == Material.WALL_SIGN) {
                    p.sendMessage("Remove sign first");
                    e.setCancelled(true);
                }
            } else if (b.getType() == Material.WALL_SIGN) {
                // Check if the sign being broken belongs to us
                Obelisk ob = obeliskByLocation.get(b.getLocation());
                if (ob != null) {
                    // Check if the player owns the Obelisk, or if they have
                    // override permissions to break
                    if (p.hasPermission(DESTROY_OB_OVERRIDE_PERM)
                            || p.getName().equals(ob.getName())) {
                        getLogger().info(
                                "Deactivating Obelisk " + ob.getName()
                                        + " at Location " + ob.getLocation()
                                        + " by player " + p.getName());

                        p.sendMessage("Deactivated Obelisk");

                        // Unregister
                        obeliskByLocation.remove(b.getLocation());
                        obeliskByName.remove(ob.getName());
                        for (String player : discovered.keySet()) {
                            discovered.get(player).remove(ob.getName());
                        }
                        saveToFile();
                    } else {
                        p.sendMessage("You cannot destroy an Obelisk you didn't create");
                        e.setCancelled(true);
                    }
                }
            }
        }
    }

    private boolean hasDiscovered(String player, String ob) {
        if (discovered.containsKey(player)) {
            return discovered.get(player).indexOf(ob) != -1;
        }
        return false;
    }

    private void discover(Player player, Obelisk ob, boolean cost) {
        if (cost) {
            double bal = econ.getBalance(player.getName());
            if (bal < learnCost) {
                player.sendMessage("You don't have enough money to learn this Obelisk");
                return;
            }
            econ.withdrawPlayer(player.getName(), learnCost);
            econ.depositPlayer(ob.getOwner(), learnCost);
        }
        String name = player.getName();
        if (discovered.containsKey(name)) {
            discovered.get(name).add(ob.getName());
        } else {
            discovered.put(name, new ArrayList<String>());
            discovered.get(name).add(ob.getName());
        }

        player.sendMessage("You have discovered the Obelisk " + ob.getName());
        saveToFile();
    }

    /*
     * Iterates through the list of known Obelisks, skipping the current if
     * possible
     */
    private int findAfter(int curIndex, int notEqual, String playerName) {
        int size = discovered.get(playerName).size();
        if (size == 1)
            return 0;

        curIndex++;
        if (curIndex == notEqual)
            return findAfter(curIndex, notEqual, playerName);
        else if (curIndex == size)
            return (notEqual == 0) ? 1 : 0;
        else
            return curIndex;
    }

    /*
     * Serialize all data to the config file
     */
    private void saveToFile() {
        List<String> obs = new ArrayList<String>();
        List<String> disc = new ArrayList<String>();

        for (String name : obeliskByName.keySet()) {
            obeliskByName.get(name).serialize(obs);
        }

        for (String player : discovered.keySet()) {
            StringBuilder b = new StringBuilder();
            b.append(player + "$");
            for (String ob : discovered.get(player)) {
                b.append(ob + ",");
            }
            disc.add(b.toString());
        }

        try {
            FileWriter fw = new FileWriter("plugins/obelisks.cfg");
            PrintWriter print = new PrintWriter(fw);
            print.println("Create: " + createCost);
            print.println("Use: " + useCost);
            print.println("Learn: " + learnCost);
            print.println(DELIMITOR);
            for (String s : obs) {
                print.println(s);
            }
            print.println(DELIMITOR);
            for (String s : disc) {
                print.println(s);
            }
            fw.close();
        } catch (IOException e) {
            getLogger().severe(
                    "Unhandlded IOException while loading config file");
        }

    }

    private void loadFromFile() {
        try {
            FileReader read = new FileReader("plugins/obelisks.cfg");
            BufferedReader in = new BufferedReader(read);

            String line = in.readLine();
            ArrayList<String> lines = new ArrayList<String>();
            while (line != null) {
                lines.add(line);
                line = in.readLine();
            }

            if (lines.size() < 5) {
                getLogger().warning("Malformed save-file found");
                in.close();
                return;
            }

            createCost = Integer.parseInt(lines.get(0).split(" ")[1]);
            useCost = Integer.parseInt(lines.get(1).split(" ")[1]);
            learnCost = Integer.parseInt(lines.get(2).split(" ")[1]);
            if (!lines.get(3).equals(DELIMITOR)) {
                in.close();
                throw new RuntimeException();
            }

            int index = 4;
            ArrayList<String> ob = new ArrayList<String>();
            while (!lines.get(index).equals(DELIMITOR)) {
                ob.add(lines.get(index));
                ob.add(lines.get(index + 1));
                ob.add(lines.get(index + 2));
                Obelisk created = new Obelisk(ob);
                obeliskByName.put(created.getName(), created);
                obeliskByLocation.put(created.getLocation(), created);
                created.validate();

                ob.clear();
                index = index + 3;
            }

            index++;
            while (index < lines.size()) {
                getLogger().info("Prcoessing: " + lines.get(index));
                String[] s1 = lines.get(index).split("\\$");

                for (String s : s1) {
                    getLogger().info("Piece: " + s);
                }

                String player = s1[0];
                if (!s1[1].equals("")) {
                    String[] s2 = s1[1].split(",");
                    ArrayList<String> list = new ArrayList<String>();
                    for (String obName : s2) {
                        if (!obName.equals("")) {
                            list.add(obName);
                        }
                    }
                    discovered.put(player, list);
                }

                index++;
            }

            in.close();
        } catch (FileNotFoundException e) {
            getLogger().warning("No obelisk save-file found");
        } catch (RuntimeException e) {
            getLogger().severe("Malformed obelisk save-file. Shutting down");
            getServer().getPluginManager().disablePlugin(this);
        } catch (IOException e) {
            getLogger().severe(
                    "Unhandlded IOException while loading config file");
        }
    }
}
