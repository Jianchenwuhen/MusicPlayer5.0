package com.example.musicplayer50;

public class LrcLine {
    private final long time;
    private final String text;

    public LrcLine(long time, String text) {
        this.time = time;
        this.text = text == null ? "" : text;
    }

    public long getTime() {
        return time;
    }

    public String getText() {
        return text;
    }
}
