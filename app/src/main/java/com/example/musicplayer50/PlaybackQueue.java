package com.example.musicplayer50;

import java.util.List;

public final class PlaybackQueue {
    private PlaybackQueue() {
    }

    public static Music next(List<Music> songs, String currentTitle) {
        return select(songs, currentTitle, 1);
    }

    public static Music previous(List<Music> songs, String currentTitle) {
        return select(songs, currentTitle, -1);
    }

    private static Music select(List<Music> songs, String currentTitle, int step) {
        if (songs == null || songs.isEmpty()) {
            return null;
        }

        int currentIndex = findByTitle(songs, currentTitle);
        if (currentIndex < 0) {
            return songs.get(0);
        }

        int size = songs.size();
        int nextIndex = (currentIndex + step + size) % size;
        return songs.get(nextIndex);
    }

    private static int findByTitle(List<Music> songs, String title) {
        if (title == null) {
            return -1;
        }

        for (int i = 0; i < songs.size(); i++) {
            Music music = songs.get(i);
            if (music != null && title.equals(music.getTitle())) {
                return i;
            }
        }
        return -1;
    }
}
