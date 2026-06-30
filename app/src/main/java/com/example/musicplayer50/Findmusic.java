package com.example.musicplayer50;

import android.content.ContentResolver;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Findmusic {

    // 支持的音频文件后缀
    private static final String[] AUDIO_EXTENSIONS = {
            ".mp3", ".wav", ".aac", ".ogg", ".flac", ".m4a", ".wma", ".mid", ".xmf", ".rtttl", ".rtx", ".ota", ".imy"
    };

    // 要扫描的目录列表
    private static final String[] SCAN_DIRS = {
            "/sdcard/Music",
            "/sdcard/Download",
            "/sdcard/music",
            "/sdcard/音乐",
            "/sdcard"
    };

    public List<Music> getmusics(ContentResolver contentResolver) {
        List<Music> musics = new ArrayList<Music>();

        // 方式1：通过 MediaStore 查询（系统已索引的音乐）
        Cursor cursor = null;
        try {
            cursor = contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    null, null, null,
                    MediaStore.Audio.Media.DEFAULT_SORT_ORDER);
            if (cursor != null) {
                for (int i = 0; i < cursor.getCount(); i++) {
                    cursor.moveToNext();
                    long duration = cursor.getLong(cursor.getColumnIndex(MediaStore.Audio.Media.DURATION));
                    int isMusic = cursor.getInt(cursor.getColumnIndex(MediaStore.Audio.Media.IS_MUSIC));
                    if (isMusic != 0 && duration / (1000 * 60) >= 1) {
                        Music music = new Music();
                        music.setTitle(cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)));
                        music.setArtist(cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)));
                        music.setDuration(duration);
                        music.setUrl(cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.DATA)));
                        musics.add(music);
                    }
                }
                cursor.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (cursor != null) {
                cursor.close();
            }
        }

        // 方式2：直接扫描文件夹（找到 MediaStore 漏掉的文件）
        Set<String> existingUrls = new HashSet<>();
        for (Music m : musics) {
            if (m.getUrl() != null) {
                existingUrls.add(m.getUrl());
            }
        }

        for (String dir : SCAN_DIRS) {
            File dirFile = new File(dir);
            if (dirFile.exists() && dirFile.isDirectory()) {
                Log.e("huizhong", "直接扫描目录: " + dir);
                scanDirectory(dirFile, musics, existingUrls, (dir.equals("/sdcard") ? 1 : 3));
            }
        }

        Log.e("huizhong", "总共找到 " + musics.size() + " 首歌曲");
        return musics;
    }

    /**
     * 递归扫描目录中的音频文件
     * @param dir     要扫描的目录
     * @param musics  结果列表
     * @param existingUrls 已存在的文件路径（去重用）
     * @param maxDepth 最大递归深度
     */
    private void scanDirectory(File dir, List<Music> musics, Set<String> existingUrls, int maxDepth) {
        if (maxDepth < 0) return;

        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory() && maxDepth > 0) {
                // 跳过隐藏文件夹和 Android 系统文件夹
                String name = file.getName();
                if (!name.startsWith(".") && !name.equals("Android") && !name.equals("Notifications")
                        && !name.equals("Ringtones") && !name.equals("Alarms") && !name.equals("Podcasts")) {
                    scanDirectory(file, musics, existingUrls, maxDepth - 1);
                }
            } else if (file.isFile()) {
                String fileName = file.getName().toLowerCase();
                if (isAudioFile(fileName)) {
                    String path = file.getAbsolutePath();
                    if (!existingUrls.contains(path)) {
                        existingUrls.add(path);
                        Music music = createMusicFromFile(file);
                        if (music != null) {
                            musics.add(music);
                            Log.e("huizhong", "直接扫描到: " + music.getTitle() + " - " + path);
                        }
                    }
                }
            }
        }
    }

    /**
     * 判断文件是否为音频文件
     */
    private boolean isAudioFile(String fileName) {
        for (String ext : AUDIO_EXTENSIONS) {
            if (fileName.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 从音频文件创建 Music 对象，使用 MediaMetadataRetriever 获取元数据
     */
    private Music createMusicFromFile(File file) {
        String path = file.getAbsolutePath();
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(path);

            String title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
            String artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
            String durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);

            // 如果没有标题元数据，用文件名（去掉后缀）
            if (title == null || title.trim().isEmpty()) {
                title = file.getName();
                int dotIndex = title.lastIndexOf('.');
                if (dotIndex > 0) {
                    title = title.substring(0, dotIndex);
                }
            }
            if (artist == null || artist.trim().isEmpty()) {
                artist = "未知歌手";
            }

            long duration = 0;
            try {
                if (durationStr != null) {
                    duration = Long.parseLong(durationStr);
                }
            } catch (NumberFormatException e) {
                duration = 0;
            }

            // 过滤掉时长小于1分钟的（除非是从文件夹直接扫描到的，放宽到30秒）
            // 文件夹直接扫描的放宽限制，因为有些音效文件也很短
            if (duration > 0 && duration < 30000) {
                // 太短的文件，跳过
                retriever.release();
                return null;
            }

            Music music = new Music();
            music.setTitle(title);
            music.setArtist(artist);
            music.setDuration(duration);
            music.setUrl(path);
            retriever.release();
            return music;

        } catch (Exception e) {
            // MediaMetadataRetriever 失败时，用文件名作为标题
            try {
                retriever.release();
            } catch (Exception ignored) {}

            String title = file.getName();
            int dotIndex = title.lastIndexOf('.');
            if (dotIndex > 0) {
                title = title.substring(0, dotIndex);
            }

            Music music = new Music();
            music.setTitle(title);
            music.setArtist("未知歌手");
            music.setDuration(0);
            music.setUrl(path);
            return music;
        }
    }
}
