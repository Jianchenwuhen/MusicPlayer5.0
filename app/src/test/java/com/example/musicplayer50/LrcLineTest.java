package com.example.musicplayer50;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class LrcLineTest {
    @Test
    public void storesTimestampAndLyricText() {
        LrcLine line = new LrcLine(1234L, "hello");

        assertEquals(1234L, line.getTime());
        assertEquals("hello", line.getText());
    }
}
