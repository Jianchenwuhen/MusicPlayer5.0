package com.example.musicplayer50;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public final class LyricsFetcher {
    private static final int CONNECT_TIMEOUT_MS = 8000;
    private static final int READ_TIMEOUT_MS = 8000;
    private static final String USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36";

    private LyricsFetcher() {
    }

    public static String fetchLrcRaw(String artist, String title) {
        try {
            long songId = searchSongId(artist, title);
            if (songId <= 0) {
                return "";
            }

            String lyricUrl = "https://music.163.com/api/song/lyric?id="
                    + songId + "&lv=1&kv=1&tv=-1";
            JSONObject root = new JSONObject(readUrl(lyricUrl));
            JSONObject lrc = root.optJSONObject("lrc");
            if (lrc == null) {
                return "";
            }
            return lrc.optString("lyric", "");
        } catch (Exception e) {
            return "";
        }
    }

    public static String fetchLyrics(String artist, String title) {
        String rawLrc = fetchLrcRaw(artist, title);
        if (rawLrc == null || rawLrc.trim().isEmpty()) {
            return "";
        }
        return rawLrc
                .replaceAll("\\[[^\\]]*\\]", "")
                .replaceAll("(?m)^\\s*$[\r\n]*", "")
                .trim();
    }

    private static long searchSongId(String artist, String title) throws Exception {
        String cleanTitle = title == null ? "" : title.trim();
        if (cleanTitle.isEmpty()) {
            return -1;
        }

        // 关键修复：本地文件常把歌手标为"未知歌手/<unknown>"，直接拼进搜索词会让网易云
        // 返回 0 条结果 → 拿不到歌词。这里先过滤占位歌手，且"歌手+歌名"搜不到时退回只用歌名。
        String cleanArtist = normalizeArtist(artist);
        if (!cleanArtist.isEmpty()) {
            long id = querySongId(cleanArtist + " " + cleanTitle);
            if (id > 0) {
                return id;
            }
        }
        return querySongId(cleanTitle);
    }

    /**
     * 归一化歌手名：空 / 占位（未知歌手、unknown 等）一律视为"无歌手"，避免污染搜索词。
     * 包级可见，便于单元测试。
     */
    static String normalizeArtist(String artist) {
        if (artist == null) {
            return "";
        }
        String trimmed = artist.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        String lower = trimmed.toLowerCase();
        if (lower.equals("unknown")
                || lower.equals("<unknown>")
                || lower.equals("unknown artist")
                || trimmed.equals("未知歌手")
                || trimmed.equals("未知艺术家")
                || trimmed.equals("未知")) {
            return "";
        }
        return trimmed;
    }

    private static long querySongId(String query) throws Exception {
        String encodedQuery = URLEncoder.encode(query.trim(), "UTF-8");
        if (encodedQuery.isEmpty()) {
            return -1;
        }

        String searchUrl = "https://music.163.com/api/search/get/web?csrf_token="
                + "&type=1&offset=0&total=true&limit=1&s=" + encodedQuery;
        JSONObject root = new JSONObject(readUrl(searchUrl));
        JSONObject result = root.optJSONObject("result");
        if (result == null) {
            return -1;
        }

        JSONArray songs = result.optJSONArray("songs");
        if (songs == null || songs.length() == 0) {
            return -1;
        }

        return songs.getJSONObject(0).optLong("id", -1);
    }

    private static String readUrl(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Referer", "https://music.163.com/");
        connection.setRequestMethod("GET");

        InputStream inputStream = connection.getInputStream();
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
            StringBuilder result = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line).append('\n');
            }
            return result.toString();
        } finally {
            inputStream.close();
            connection.disconnect();
        }
    }
}
