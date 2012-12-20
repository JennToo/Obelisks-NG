package nl.nitori.ObelisksNG;

import java.util.List;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;

public class Obelisk {
    private String owner, name;
    private Location loc;
    private boolean valid;

    /**
     * Checks if the Location given (which should point to a sign) is a valid
     * obelisk
     * 
     * @param loc
     *            Location to check
     * @param lines
     *            Lines of text of the sign, if it hasn't been added yet (send
     *            null otherwise)
     * @return If the Obelisk is valid
     */
    public static boolean validateLocation(Location loc, String[] lines) {
        // Check the sign
        if (loc.getBlock().getType() != Material.WALL_SIGN) {
            return false;
        }

        Sign s = (Sign) loc.getBlock().getState();
        String line0 = (lines == null) ? s.getLine(0) : lines[0];
        String line1 = (lines == null) ? s.getLine(1) : lines[1];

        if (!ChatColor.stripColor(line0).equals("Obelisk")) {
            return false;
        }
        if (line1.trim().equals("")) {
            return false;
        }

        // Check Structure
        BlockFace f = ((org.bukkit.material.Sign) loc.getBlock().getState()
                .getData()).getAttachedFace();
        switch (f) {
        case EAST:
        case WEST:
        case NORTH:
        case SOUTH:
            break;
        default:
            return false;
        }

        Location basis = loc.clone().add(f.getModX(), f.getModY(), f.getModZ());
        Location[] obs = new Location[5];
        Location[] glass = new Location[8];

        // God this is ugly
        obs[0] = basis;
        obs[1] = basis.clone().add(0, 1, 0);
        obs[2] = basis.clone().add(0, -1, 0);
        obs[3] = basis.clone().add(0, -2, 0);
        obs[4] = basis.clone().add(0, -3, 0);
        for (Location l : obs) {
            if (l.getBlock().getType() != Material.OBSIDIAN) {
                return false;
            }
        }

        basis.add(0, -2, 0);
        glass[0] = basis.clone().add(1, 0, 0);
        glass[1] = basis.clone().add(-1, 0, 0);
        glass[2] = basis.clone().add(0, 0, 1);
        glass[3] = basis.clone().add(1, 0, 1);
        glass[4] = basis.clone().add(-1, 0, 1);
        glass[5] = basis.clone().add(0, 0, -1);
        glass[6] = basis.clone().add(1, 0, -1);
        glass[7] = basis.clone().add(-1, 0, -1);
        for (Location l : glass) {
            if (l.getBlock().getType() != Material.GLASS) {
                return false;
            }
            l.add(0, -1, 0);
            if (l.getBlock().getType() != Material.STATIONARY_LAVA) {
                return false;
            }
        }

        return true;
    }

    public Obelisk(String owner, String name, Location loc) {
        this.owner = owner;
        this.name = name;
        this.loc = loc;
    }

    /**
     * Initialize from serialization data
     * 
     * @param data
     *            Should contain 3 Strings: owner, name, and location, in that
     *            order
     */
    public Obelisk(List<String> data) {
        if (data.size() != 3) {
            Obelisks.inst.getLogger().severe("Malformed Obelisk data");
            return;
        }

        owner = data.get(0);
        name = data.get(1);
        try {
            String[] lDat = data.get(2).split(" ");
            if (lDat.length != 4) {
                Obelisks.inst.getLogger().severe(
                        "Malformed Obelisk location data: " + data.get(4));
                return;
            }
            double x = Double.parseDouble(lDat[1]);
            double y = Double.parseDouble(lDat[2]);
            double z = Double.parseDouble(lDat[3]);
            loc = new Location(Obelisks.inst.getServer().getWorld(lDat[0]), x,
                    y, z);
        } catch (NumberFormatException e) {
            Obelisks.inst.getLogger().severe(
                    "Got a non-number where expecting number: "
                            + e.getMessage());
        }
    }

    /**
     * Add the data from this obelisk to the serialization stream
     * 
     * @param appendTo
     *            Serialization stream to add on to
     */
    public void serialize(List<String> appendTo) {
        appendTo.add(owner);
        appendTo.add(name);
        appendTo.add(loc.getWorld().getName() + " " + loc.getX() + " "
                + loc.getY() + " " + loc.getZ());
    }

    /**
     * Checks if this Obelisk is valid, and updates the sign accordingly
     */
    public void validate() {
        valid = validateLocation(loc, null);
        if (!(loc.getBlock().getState() instanceof Sign)) {
            Obelisks.inst.getLogger().warning(
                    "Missing sign for obelisk at location " + loc);
            return;
        }
        Sign s = (Sign) loc.getBlock().getState();
        if (valid) {
            s.setLine(
                    0,
                    ChatColor.DARK_GREEN.toString()
                            + ChatColor.UNDERLINE.toString()
                            + ChatColor.BOLD.toString() + "Obelisk");
        } else {
            s.setLine(
                    0,
                    ChatColor.DARK_RED.toString()
                            + ChatColor.UNDERLINE.toString()
                            + ChatColor.BOLD.toString() + "Obelisk");
        }
        s.update();
    }

    public boolean isValid() {
        return valid;
    }

    public String getOwner() {
        return owner;
    }

    public String getName() {
        return name;
    }

    public Location getLocation() {
        return loc;
    }

    /**
     * Performs a lightning strike on the top of the Obelisk
     */
    public void doStrike() {
        Location toStrike = loc.clone();
        BlockFace off = ((org.bukkit.material.Sign) toStrike.getBlock()
                .getState().getData()).getAttachedFace();
        toStrike.add(off.getModX(), 2, off.getModZ());
        loc.getWorld().strikeLightningEffect(toStrike);
    }
}
