package com.example.musicplayer50;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class PlaybackQueueTest {
    @Test
    public void nextSongAfterMiddleSong() {
        List<Music> songs = Arrays.asList(song("A"), song("B"), song("C"));

        assertEquals("C", PlaybackQueue.next(songs, "B").getTitle());
    }

    @Test
    public void nextSongWrapsFromLastToFirst() {
        List<Music> songs = Arrays.asList(song("A"), song("B"), song("C"));

        assertEquals("A", PlaybackQueue.next(songs, "C").getTitle());
    }

    @Test
    public void previousSongWrapsFromFirstToLast() {
        List<Music> songs = Arrays.asList(song("A"), song("B"), song("C"));

        assertEquals("C", PlaybackQueue.previous(songs, "A").getTitle());
    }

    @Test
    public void unknownCurrentSongFallsBackToFirstSong() {
        List<Music> songs = Arrays.asList(song("A"), song("B"), song("C"));

        assertEquals("A", PlaybackQueue.next(songs, "Missing").getTitle());
        assertEquals("A", PlaybackQueue.previous(songs, "Missing").getTitle());
    }

    @Test
    public void emptyQueueReturnsNull() {
        assertNull(PlaybackQueue.next(Collections.<Music>emptyList(), "A"));
        assertNull(PlaybackQueue.previous(Collections.<Music>emptyList(), "A"));
    }

    private Music song(String title) {
        Music music = new Music();
        music.setTitle(title);
        music.setArtist("Artist " + title);
        music.setUrl("/music/" + title + ".mp3");
        return music;
    }
}
