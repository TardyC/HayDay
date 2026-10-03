package dev.tardyc.hayday.model;

/**
 * En vare i en bygnings produktionskø.
 */
public final class QueueEntry {

    private final String recipeId;
    private long endTime;
    private final long duration;

    public QueueEntry(String recipeId, long endTime, long duration) {
        this.recipeId = recipeId;
        this.endTime = endTime;
        this.duration = duration;
    }

    public static QueueEntry parse(String input) {
        String[] parts = input.split(";");
        if (parts.length != 3) {
            return null;
        }
        try {
            return new QueueEntry(parts[0], Long.parseLong(parts[1]), Long.parseLong(parts[2]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public String serialize() {
        return recipeId + ";" + endTime + ";" + duration;
    }

    public String getRecipeId() {
        return recipeId;
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public long getDuration() {
        return duration;
    }

    public long getStartTime() {
        return endTime - duration;
    }

    public boolean isDone(long now) {
        return now >= endTime;
    }

    public boolean isActive(long now) {
        return now >= getStartTime() && now < endTime;
    }

    public double getProgress(long now) {
        return Math.max(0, Math.min(1, (now - getStartTime()) / (double) Math.max(1, duration)));
    }
}
