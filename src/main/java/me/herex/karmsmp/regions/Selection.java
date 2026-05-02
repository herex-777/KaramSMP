package me.herex.karmsmp.regions;

import org.bukkit.Location;

public final class Selection {

    private Location positionOne;
    private Location positionTwo;

    public Location getPositionOne() {
        return positionOne;
    }

    public void setPositionOne(Location positionOne) {
        this.positionOne = positionOne;
    }

    public Location getPositionTwo() {
        return positionTwo;
    }

    public void setPositionTwo(Location positionTwo) {
        this.positionTwo = positionTwo;
    }

    public boolean isComplete() {
        return positionOne != null && positionTwo != null
                && positionOne.getWorld() != null
                && positionTwo.getWorld() != null
                && positionOne.getWorld().equals(positionTwo.getWorld());
    }
}
