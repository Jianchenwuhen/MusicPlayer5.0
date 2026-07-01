package com.example.musicplayer50;

/**
 * 单行歌词数据：时间戳(毫秒) + 歌词文本
 */
public class LrcLine {
    private long time;   // 毫秒
    private String text;

    public LrcLine(long time, String text) {
        this.time = time;
        this.text = text;
    }

    public long getTime() { return time; }
    public String getText() { return text; }
    public void setTime(long time) { this.time = time; }
    public void setText(String text) { this.text = text; }
}
