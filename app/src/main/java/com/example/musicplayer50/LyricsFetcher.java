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
        StringBuilder query = new StringBuilder();
        if (artist != null && !artist.trim().isEmpty()) {
            query.append(artist.trim()).append(' ');
        }
        if (title != null) {
            query.append(title.trim());
        }

        String encodedQuery = URLEncoder.encode(query.toString().trim(), "UTF-8");
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
