package me.herex.karmsmp.scoreboards;

import java.util.ArrayList;
import java.util.List;

public final class ScoreboardLine {

    private final List<String> frames;
    private final int frameSpeedTicks;

    public ScoreboardLine(List<String> frames, int frameSpeedTicks) {
        this.frames = frames == null || frames.isEmpty() ? List.of("") : new ArrayList<>(frames);
        this.frameSpeedTicks = Math.max(1, frameSpeedTicks);
    }

    public static ScoreboardLine staticLine(String line) {
        return new ScoreboardLine(List.of(line == null ? "" : line), 20);
    }

    public String getFrame(int tick) {
        if (frames.size() == 1) {
            return frames.get(0);
        }

        int index = Math.floorMod(tick / frameSpeedTicks, frames.size());
        return frames.get(index);
    }

    public List<String> getFrames() {
        return List.copyOf(frames);
    }

    public int getFrameSpeedTicks() {
        return frameSpeedTicks;
    }
}
