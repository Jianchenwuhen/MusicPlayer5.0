package com.example.musicplayer50;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * 校验歌手名归一化：占位/未知歌手必须被过滤，否则拼进网易云搜索词会导致
 * 返回 0 条结果、拿不到歌词（本地文件常见 "未知歌手"/"<unknown>"）。
 */
public class LyricsFetcherTest {

    @Test
    public void placeholderArtistsBecomeEmpty() {
        assertEquals("", LyricsFetcher.normalizeArtist(null));
        assertEquals("", LyricsFetcher.normalizeArtist(""));
        assertEquals("", LyricsFetcher.normalizeArtist("   "));
        assertEquals("", LyricsFetcher.normalizeArtist("未知歌手"));
        assertEquals("", LyricsFetcher.normalizeArtist("未知艺术家"));
        assertEquals("", LyricsFetcher.normalizeArtist("未知"));
        assertEquals("", LyricsFetcher.normalizeArtist("unknown"));
        assertEquals("", LyricsFetcher.normalizeArtist("Unknown"));
        assertEquals("", LyricsFetcher.normalizeArtist("<unknown>"));
        assertEquals("", LyricsFetcher.normalizeArtist("unknown artist"));
    }

    @Test
    public void realArtistsArePreservedAndTrimmed() {
        assertEquals("阎维文", LyricsFetcher.normalizeArtist("阎维文"));
        assertEquals("Beyond", LyricsFetcher.normalizeArtist("  Beyond  "));
        assertEquals("周杰伦", LyricsFetcher.normalizeArtist("周杰伦"));
    }
}
