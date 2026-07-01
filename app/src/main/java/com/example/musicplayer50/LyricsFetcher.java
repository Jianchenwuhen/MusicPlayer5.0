package com.example.musicplayer50;

import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

/**
 * 歌词获取工具类 - 通过网络API获取歌词
 * 多API链式查找：lrclib → QQ音乐 → lyrics.ovh
 * Created on 2026/06/30.
 */
public class LyricsFetcher {

    private static final int CONNECT_TIMEOUT = 10000;
    private static final int READ_TIMEOUT = 10000;

    /**
     * 从网络API获取歌词，已去除LRC时间戳（纯文本展示用）
     */
    public static String fetchLyrics(String artist, String title) {
        artist = normalizeArtist(artist); // 过滤"未知歌手"等占位，避免污染精确搜索
        // 1. lrclib.net - 覆盖广，中日英都有
        String lyrics = fetchFromLrclib(artist, title);
        if (lyrics != null) return lyrics;

        // 2. QQ音乐 - 国内歌曲覆盖最好
        Log.e("huizhong", "lrclib未找到，尝试QQ音乐...");
        lyrics = fetchFromQQMusic(artist, title);
        if (lyrics != null) return lyrics;

        // 3. 网易云 - 中文/本地歌按歌名搜
        Log.e("huizhong", "QQ音乐未找到，尝试网易云...");
        String neteaseLrc = fetchNeteaseLrcRaw(artist, title);
        if (neteaseLrc != null) return cleanLrc(neteaseLrc);

        // 4. lyrics.ovh - 最后兜底（英文）
        Log.e("huizhong", "网易云未找到，尝试lyrics.ovh...");
        return fetchFromLyricsOvh(artist, title);
    }

    /**
     * 获取原始 LRC 歌词（保留时间戳，用于逐行同步高亮）
     * 优先从 QQ音乐/lrclib 获取带时间戳的 LRC
     */
    public static String fetchLrcRaw(String artist, String title) {
        artist = normalizeArtist(artist); // 过滤"未知歌手"等占位，避免污染精确搜索
        // 1. QQ音乐 - LRC 时间戳最全
        String lrc = fetchQQMusicLrcRaw(artist, title);
        if (lrc != null) return lrc;

        // 2. lrclib - syncedLyrics 字段
        Log.e("huizhong", "QQ音乐LRC未找到，尝试lrclib...");
        lrc = fetchLrclibLrcRaw(artist, title);
        if (lrc != null) return lrc;

        // 3. 网易云 - 中文/本地歌按歌名搜，命中率高
        Log.e("huizhong", "lrclib未找到，尝试网易云...");
        lrc = fetchNeteaseLrcRaw(artist, title);
        if (lrc != null) return lrc;

        // 都没有带时间戳的 LRC
        Log.e("huizhong", "无LRC同步歌词");
        return null;
    }

    // ==================== lrclib.net ====================

    private static String fetchFromLrclib(String artist, String title) {
        HttpURLConnection connection = null;
        BufferedReader reader = null;
        try {
            String urlString = "https://lrclib.net/api/get?artist_name="
                    + URLEncoder.encode(artist, "UTF-8")
                    + "&track_name=" + URLEncoder.encode(title, "UTF-8");

            Log.e("huizhong", "lrclib: " + urlString);

            connection = (HttpURLConnection) new URL(urlString).openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "MusicPlayer5.0/1.0");

            if (connection.getResponseCode() != 200) {
                Log.e("huizhong", "lrclib返回: " + connection.getResponseCode());
                return null;
            }

            reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);

            JSONObject json = new JSONObject(sb.toString());
            String lyrics = json.optString("plainLyrics", null);
            if (lyrics == null || lyrics.isEmpty()) {
                lyrics = json.optString("syncedLyrics", null); // LRC格式也能用
            }
            // 优先取纯文本歌词，没有就取同步歌词（需要清理LRC时间戳）
            if (lyrics != null && !lyrics.isEmpty()) {
                lyrics = cleanLrc(lyrics);
                Log.e("huizhong", "lrclib成功! 长度=" + lyrics.length());
                return lyrics;
            }
            Log.e("huizhong", "lrclib未收录此歌");
            return null;
        } catch (Exception e) {
            Log.e("huizhong", "lrclib异常: " + e.getMessage());
            return null;
        } finally {
            close(reader, connection);
        }
    }

    // ==================== QQ音乐 ====================

    private static String fetchFromQQMusic(String artist, String title) {
        // Step 1: 搜索歌曲获取 songmid
        String songmid = searchQQMusic(artist, title);
        if (songmid == null) return null;

        // Step 2: 通过 songmid 获取歌词
        return getQQMusicLyric(songmid);
    }

    private static String searchQQMusic(String artist, String title) {
        HttpURLConnection connection = null;
        BufferedReader reader = null;
        try {
            String keyword = URLEncoder.encode(title + " " + artist, "UTF-8");
            String urlString = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp"
                    + "?p=1&n=3&w=" + keyword + "&format=json";

            Log.e("huizhong", "QQ音乐搜索: " + urlString);

            connection = (HttpURLConnection) new URL(urlString).openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            connection.setRequestProperty("Referer", "https://y.qq.com");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");

            if (connection.getResponseCode() != 200) {
                Log.e("huizhong", "QQ音乐搜索返回: " + connection.getResponseCode());
                return null;
            }

            reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);

            JSONObject json = new JSONObject(sb.toString());
            JSONArray list = json.getJSONObject("data").getJSONObject("song")
                    .getJSONArray("list");
            if (list.length() == 0) {
                Log.e("huizhong", "QQ音乐搜索结果为空");
                return null;
            }

            for (int i = 0; i < list.length(); i++) {
                JSONObject song = list.getJSONObject(i);
                String songName = song.optString("songname", "");
                String singerName = "";
                JSONArray singers = song.optJSONArray("singer");
                if (singers != null && singers.length() > 0) {
                    singerName = singers.getJSONObject(0).optString("name", "");
                }
                // 模糊匹配，防止同名不同歌
                if (songName.contains(title) || title.contains(songName)
                        || singerName.contains(artist) || artist.contains(singerName)) {
                    String mid = song.optString("songmid");
                    if (mid != null && !mid.isEmpty()) {
                        Log.e("huizhong", "QQ音乐匹配到: " + songName + " - " + singerName);
                        return mid;
                    }
                }
            }
            // 如果没有模糊匹配成功，用第一个结果
            String mid = list.getJSONObject(0).optString("songmid");
            Log.e("huizhong", "QQ音乐使用首个结果, songmid=" + mid);
            return (mid != null && !mid.isEmpty()) ? mid : null;

        } catch (Exception e) {
            Log.e("huizhong", "QQ音乐搜索异常: " + e.getMessage());
            return null;
        } finally {
            close(reader, connection);
        }
    }

    private static String getQQMusicLyric(String songmid) {
        HttpURLConnection connection = null;
        BufferedReader reader = null;
        try {
            String urlString = "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg"
                    + "?songmid=" + songmid + "&g_tk=5381&format=json";

            Log.e("huizhong", "QQ音乐歌词请求: " + urlString);

            connection = (HttpURLConnection) new URL(urlString).openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            connection.setRequestProperty("Referer", "https://y.qq.com");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");

            if (connection.getResponseCode() != 200) {
                Log.e("huizhong", "QQ音乐歌词返回: " + connection.getResponseCode());
                return null;
            }

            reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);

            JSONObject json = new JSONObject(sb.toString());
            if (json.optInt("code") != 0) {
                Log.e("huizhong", "QQ音乐歌词code=" + json.optInt("code"));
                return null;
            }

            // 歌词是 base64 编码的
            String lyricBase64 = json.optString("lyric", null);
            if (lyricBase64 == null || lyricBase64.isEmpty()) {
                Log.e("huizhong", "QQ音乐歌词为空");
                return null;
            }

            byte[] decoded = Base64.decode(lyricBase64, Base64.DEFAULT);
            String lyric = new String(decoded, "UTF-8");

            if (lyric.isEmpty()) {
                Log.e("huizhong", "QQ音乐歌词解码为空");
                return null;
            }

            Log.e("huizhong", "QQ音乐歌词成功! 长度=" + lyric.length());
            return cleanLrc(lyric);

        } catch (Exception e) {
            Log.e("huizhong", "QQ音乐歌词异常: " + e.getMessage());
            return null;
        } finally {
            close(reader, connection);
        }
    }

    // ==================== lyrics.ovh (兜底) ====================

    private static String fetchFromLyricsOvh(String artist, String title) {
        HttpURLConnection connection = null;
        BufferedReader reader = null;
        try {
            String urlString = "https://api.lyrics.ovh/v1/"
                    + URLEncoder.encode(artist, "UTF-8") + "/"
                    + URLEncoder.encode(title, "UTF-8");

            Log.e("huizhong", "lyrics.ovh: " + urlString);

            connection = (HttpURLConnection) new URL(urlString).openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            connection.setRequestProperty("Accept", "application/json");

            if (connection.getResponseCode() != 200) {
                Log.e("huizhong", "lyrics.ovh返回: " + connection.getResponseCode());
                return null;
            }

            reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);

            JSONObject json = new JSONObject(sb.toString());
            String lyrics = json.optString("lyrics", null);

            if (lyrics == null || lyrics.isEmpty() || "null".equals(lyrics)) {
                Log.e("huizhong", "lyrics.ovh未收录");
                return null;
            }

            Log.e("huizhong", "lyrics.ovh成功! 长度=" + lyrics.length());
            return lyrics;

        } catch (Exception e) {
            Log.e("huizhong", "lyrics.ovh异常: " + e.getMessage());
            return null;
        } finally {
            close(reader, connection);
        }
    }

    // ==================== 原始 LRC 获取（保留时间戳） ====================

    private static String fetchLrclibLrcRaw(String artist, String title) {
        HttpURLConnection connection = null;
        BufferedReader reader = null;
        try {
            String urlString = "https://lrclib.net/api/get?artist_name="
                    + URLEncoder.encode(artist, "UTF-8")
                    + "&track_name=" + URLEncoder.encode(title, "UTF-8");
            connection = (HttpURLConnection) new URL(urlString).openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "MusicPlayer5.0/1.0");

            if (connection.getResponseCode() != 200) return null;

            reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);

            JSONObject json = new JSONObject(sb.toString());
            String synced = json.optString("syncedLyrics", null);
            if (synced != null && !synced.isEmpty()) {
                return stripLrcMetadata(synced);
            }
            return null;
        } catch (Exception e) {
            return null;
        } finally {
            close(reader, connection);
        }
    }

    private static String fetchQQMusicLrcRaw(String artist, String title) {
        String songmid = searchQQMusic(artist, title);
        if (songmid == null) return null;
        return getQQMusicLyricRaw(songmid);
    }

    private static String getQQMusicLyricRaw(String songmid) {
        HttpURLConnection connection = null;
        BufferedReader reader = null;
        try {
            String urlString = "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg"
                    + "?songmid=" + songmid + "&g_tk=5381&format=json";
            connection = (HttpURLConnection) new URL(urlString).openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            connection.setRequestProperty("Referer", "https://y.qq.com");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");

            if (connection.getResponseCode() != 200) return null;

            reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);

            JSONObject json = new JSONObject(sb.toString());
            if (json.optInt("code") != 0) return null;

            String lyricBase64 = json.optString("lyric", null);
            if (lyricBase64 == null || lyricBase64.isEmpty()) return null;

            byte[] decoded = Base64.decode(lyricBase64, Base64.DEFAULT);
            String lyric = new String(decoded, "UTF-8");
            if (lyric.isEmpty()) return null;

            // 只去掉元数据行，保留 [mm:ss.xx] 时间戳
            return stripLrcMetadata(lyric);
        } catch (Exception e) {
            return null;
        } finally {
            close(reader, connection);
        }
    }

    /**
     * 只去掉 LRC 元数据标签（[ti:] [ar:] 等），保留时间戳行
     */
    private static String stripLrcMetadata(String text) {
        if (text == null) return null;
        StringBuilder result = new StringBuilder();
        String[] lines = text.split("\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            // 跳过纯元数据行
            if (trimmed.matches("\\[ti:.*") || trimmed.matches("\\[ar:.*")
                    || trimmed.matches("\\[al:.*") || trimmed.matches("\\[by:.*")
                    || trimmed.matches("\\[offset:.*") || trimmed.matches("\\[length:.*")
                    || trimmed.matches("\\[la:.*") || trimmed.matches("\\[re:.*")
                    || trimmed.matches("\\[ve:.*")) {
                continue;
            }
            result.append(trimmed).append("\n");
        }
        return result.toString().trim();
    }

    // ==================== LRC清理 ====================

    /**
     * 清理LRC格式歌词：
     * 1. 去掉 [ti:]、[ar:]、[by:] 等元数据标签行
     * 2. 去掉 [00:12.34] 等时间戳，只留歌词文本
     */
    private static String cleanLrc(String text) {
        if (text == null) return null;

        StringBuilder result = new StringBuilder();
        String[] lines = text.split("\\n");
        boolean hasContent = false;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            // 跳过纯元数据行： [ti:xxx] [ar:xxx] [al:xxx] [by:xxx] [offset:xxx] [length:xxx]
            if (trimmed.matches("\\[ti:.*") || trimmed.matches("\\[ar:.*")
                    || trimmed.matches("\\[al:.*") || trimmed.matches("\\[by:.*")
                    || trimmed.matches("\\[offset:.*") || trimmed.matches("\\[length:.*")
                    || trimmed.matches("\\[la:.*") || trimmed.matches("\\[re:.*")
                    || trimmed.matches("\\[ve:.*")) {
                continue;
            }

            // 去掉所有 [mm:ss.xx] 或 [mm:ss.xxx] 时间戳
            String cleaned = trimmed.replaceAll("\\[\\d{2}:\\d{2}[.:]\\d{2,3}\\]", "").trim();

            if (cleaned.isEmpty()) continue;

            result.append(cleaned).append("\n");
            hasContent = true;
        }

        if (!hasContent) return text;
        return result.toString().trim();
    }

    // ==================== 网易云（中文/本地歌按歌名搜，命中率高） ====================

    /** 网易云：返回带时间戳的原始 LRC；无则 null。对"未知歌手"的本地中文歌尤其有效 */
    private static String fetchNeteaseLrcRaw(String artist, String title) {
        long id = searchNeteaseId(artist, title);
        if (id <= 0) {
            return null;
        }
        HttpURLConnection connection = null;
        try {
            String urlString = "https://music.163.com/api/song/lyric?id=" + id + "&lv=1&kv=1&tv=-1";
            connection = openNetease(urlString);
            if (connection.getResponseCode() != 200) {
                return null;
            }
            JSONObject root = new JSONObject(readBody(connection));
            JSONObject lrc = root.optJSONObject("lrc");
            if (lrc == null) {
                return null;
            }
            String lyric = lrc.optString("lyric", "");
            if (lyric.isEmpty()) {
                return null;
            }
            Log.e("huizhong", "网易云歌词成功! 长度=" + lyric.length());
            return stripLrcMetadata(lyric);
        } catch (Exception e) {
            Log.e("huizhong", "网易云歌词异常: " + e.getMessage());
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static long searchNeteaseId(String artist, String title) {
        String prefix = (artist == null || artist.isEmpty()) ? "" : artist + " ";
        long id = neteaseQuery(prefix + title);
        if (id > 0) {
            return id;
        }
        return neteaseQuery(title);
    }

    private static long neteaseQuery(String query) {
        HttpURLConnection connection = null;
        try {
            String urlString = "https://music.163.com/api/search/get/web?csrf_token=&type=1&offset=0&total=true&limit=1&s="
                    + URLEncoder.encode(query.trim(), "UTF-8");
            connection = openNetease(urlString);
            if (connection.getResponseCode() != 200) {
                return -1;
            }
            JSONObject root = new JSONObject(readBody(connection));
            JSONObject result = root.optJSONObject("result");
            if (result == null) {
                return -1;
            }
            JSONArray songs = result.optJSONArray("songs");
            if (songs == null || songs.length() == 0) {
                return -1;
            }
            return songs.getJSONObject(0).optLong("id", -1);
        } catch (Exception e) {
            return -1;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static HttpURLConnection openNetease(String urlString) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlString).openConnection();
        c.setConnectTimeout(CONNECT_TIMEOUT);
        c.setReadTimeout(READ_TIMEOUT);
        c.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36");
        c.setRequestProperty("Referer", "https://music.163.com/");
        c.setRequestMethod("GET");
        return c;
    }

    private static String readBody(HttpURLConnection c) throws Exception {
        BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
        try {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        } finally {
            r.close();
        }
    }

    // ==================== 歌手名归一化 ====================

    /**
     * 空 / 占位（未知歌手、unknown 等）一律视为"无歌手"，返回空串。
     * 避免本地文件的 "未知歌手/<unknown>" 拼进精确搜索导致 0 结果。包级可见便于单测。
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

    // ==================== 资源清理 ====================

    private static void close(BufferedReader reader, HttpURLConnection connection) {
        if (reader != null) {
            try { reader.close(); } catch (IOException e) { /* ignore */ }
        }
        if (connection != null) {
            connection.disconnect();
        }
    }
}
