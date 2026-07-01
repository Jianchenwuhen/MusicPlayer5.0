package com.example.musicplayer50;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.os.IBinder;
import android.os.AsyncTask;
import android.os.Build;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private Button localmusic;
    private Button play;
    private Button playlist;
    private Button playnext;
    private Button playlast;
    private Button onlinemusic;
    private SeekBar seekBar;
    private TextView textView2;
    private TextView textView;
    private ListView lyricListView;
    private LyricAdapter lyricAdapter;
    private AudioWaveView audioWaveView;
    private List<LrcLine> lrcLines = new ArrayList<>();
    private int currentLrcIndex = -1;
    private MusicService musicService;
    private String CurrentTitle = "CurrentTitle";
    private GestureDetector mGestureDetector;
    private LyricsLoadTask lyricsLoadTask;

    private ServiceConnection conn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            musicService = ((MusicService.MyBinder) service).getService();
            syncServiceState();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        UiUtils.setupEdgeToEdge(this);
        UiUtils.applySystemBarInsets(findViewById(R.id.contentRoot));

        textView2 = (TextView) findViewById(R.id.textView2);
        textView = (TextView) findViewById(R.id.textView);
        lyricListView = (ListView) findViewById(R.id.lyricListView);
        audioWaveView = (AudioWaveView) findViewById(R.id.audioWaveView);
        lyricAdapter = new LyricAdapter(this, lrcLines);
        lyricListView.setAdapter(lyricAdapter);
        showPlainLyrics("暂无歌词");
        play = (Button) findViewById(R.id.play);
        seekBar = (SeekBar) findViewById(R.id.seekBar);
        localmusic = (Button) findViewById(R.id.localmusic);
        onlinemusic = (Button) findViewById(R.id.onlinemusic);
        playlist = (Button) findViewById(R.id.playlist);
        playnext = (Button) findViewById(R.id.playnext);
        playlast = (Button) findViewById(R.id.playlast);

        playlast.setOnClickListener(this);
        playnext.setOnClickListener(this);
        play.setOnClickListener(this);
        localmusic.setOnClickListener(this);
        onlinemusic.setOnClickListener(this);
        playlist.setOnClickListener(this);

        Intent intent = new Intent(this, MusicService.class);
        bindService(intent, conn, Context.BIND_AUTO_CREATE);

        IntentFilter filter = new IntentFilter();
        filter.addAction("seekbarmaxprogress");
        filter.addAction("seekbarprogress");
        filter.addAction("gettitle");
        filter.addAction("pauseimage");
        filter.addAction("playimage");
        filter.addAction("nextsong");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(broadcastReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(broadcastReceiver, filter);
        }

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    Intent intent = new Intent(MainActivity.this, MusicService.class);
                    intent.setAction("changed");
                    intent.putExtra("seekbarprogress", progress);
                    startService(intent);
                    // 拖拽进度条时歌词同步跳转
                    syncLyricHighlight(progress);
                }
            }
        });

        mGestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if ((e1.getRawX() - e2.getRawX()) > 200) {
                    Intent intent = new Intent(MainActivity.this, playlist.class);
                    startActivity(intent);
                    return true;
                }

                if ((e2.getRawX() - e1.getRawX()) > 200) {
                    Intent intent = new Intent(MainActivity.this, LocalMusicActivity.class);
                    startActivity(intent);
                    overridePendingTransition(R.animator.lefttodleft, R.animator.righttoleft);
                    return true;
                }

                return super.onFling(e1, e2, velocityX, velocityY);
            }
        });
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();

        if (id == R.id.play) {
            if (musicService != null) {
                musicService.start();
            }

        } else if (id == R.id.localmusic) {
            Intent intent = new Intent(MainActivity.this, LocalMusicActivity.class);
            startActivity(intent);

        } else if (id == R.id.onlinemusic) {
            Intent intent = new Intent(MainActivity.this, OnlineMusicActivity.class);
            startActivity(intent);

        } else if (id == R.id.playlist) {
            Intent intent3 = new Intent(MainActivity.this, playlist.class);
            startActivity(intent3);

        } else if (id == R.id.playnext) {
            playNextSong();

        } else if (id == R.id.playlast) {
            playPreviousSong();
        }
    }

    private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if ("seekbarmaxprogress".equals(action)) {
                seekBar.setMax(intent.getIntExtra("seekbarmaxprogress", 100));

            } else if ("seekbarprogress".equals(action)) {
                int progress = intent.getIntExtra("seekbarprogress", 0);
                seekBar.setProgress(progress);
                syncLyricHighlight(progress);

            } else if ("pauseimage".equals(action)) {
                play.setBackgroundResource(R.drawable.control_play);
                setWavePlaying(false);

            } else if ("playimage".equals(action)) {
                play.setBackgroundResource(R.drawable.control_pause);
                setWavePlaying(true);

            } else if ("gettitle".equals(action)) {
                CurrentTitle = intent.getStringExtra("title");
                String artist = intent.getStringExtra("artist");
                Log.e("huizhong", "CurrentTitle = " + CurrentTitle);
                textView2.setText(artist);
                textView.setText(CurrentTitle);
                loadLyrics(CurrentTitle, artist);
                setWavePlaying(true);

            } else if ("nextsong".equals(action)) {
                Log.e("huizhong", "歌曲播放结束，接收到广播，发送下一首歌曲");
                playNextSong();
            }
        }
    };

    private void playNextSong() {
        Music next = PlaybackQueue.next(loadPlaylistSongs(), CurrentTitle);
        startSong(next);
    }

    private void playPreviousSong() {
        Music previous = PlaybackQueue.previous(loadPlaylistSongs(), CurrentTitle);
        startSong(previous);
    }

    private List<Music> loadPlaylistSongs() {
        List<Music> songs = new ArrayList<>();
        Cursor cursor = getContentResolver().query(
                PlaylistContract.CONTENT_URI,
                null,
                null,
                null,
                PlaylistContract.COLUMN_ID + " ASC");
        if (cursor == null) {
            return songs;
        }

        try {
            while (cursor.moveToNext()) {
                Music music = new Music();
                music.setTitle(cursor.getString(cursor.getColumnIndexOrThrow(PlaylistContract.COLUMN_TITLE)));
                music.setArtist(cursor.getString(cursor.getColumnIndexOrThrow(PlaylistContract.COLUMN_ARTIST)));
                music.setUrl(cursor.getString(cursor.getColumnIndexOrThrow(PlaylistContract.COLUMN_URL)));
                songs.add(music);
            }
        } finally {
            cursor.close();
        }

        return songs;
    }

    private void startSong(Music music) {
        if (music == null) {
            showPlainLyrics("播放列表为空");
            return;
        }

        Intent intent = new Intent(this, MusicService.class);
        intent.setAction("startnew");
        intent.putExtra("url", music.getUrl());
        intent.putExtra("title", music.getTitle());
        intent.putExtra("artist", music.getArtist());
        startService(intent);
    }

    private void syncServiceState() {
        if (musicService == null) {
            setWavePlaying(false);
            return;
        }

        String title = musicService.getCurrentTitle();
        if (title != null && title.trim().length() > 0) {
            boolean shouldReloadLyrics = !title.equals(CurrentTitle) || lyricAdapter.getCount() == 0;
            CurrentTitle = title;
            String artist = musicService.getCurrentArtist();
            textView.setText(CurrentTitle);
            textView2.setText(artist);
            if (shouldReloadLyrics) {
                loadLyrics(CurrentTitle, artist);
            }
        }

        int duration = musicService.getDuration();
        if (duration > 0) {
            seekBar.setMax(duration);
            seekBar.setProgress(musicService.getCurrentPosition());
        }
        boolean playing = musicService.isPlaying();
        play.setBackgroundResource(playing ? R.drawable.control_pause : R.drawable.control_play);
        setWavePlaying(playing);
    }

    private void setWavePlaying(boolean playing) {
        if (audioWaveView == null) {
            return;
        }
        if (playing) {
            audioWaveView.start();
        } else {
            audioWaveView.stop();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        syncServiceState();
    }

    // ==================== 歌词加载 & 同步 ====================

    /**
     * 切歌时触发：先尝试获取 LRC 同步歌词，失败则降级为纯文本
     */
    private void loadLyrics(String title, String artist) {
        if (title == null || title.trim().length() == 0) {
            showPlainLyrics("暂无歌词");
            return;
        }
        if (lyricsLoadTask != null) {
            lyricsLoadTask.cancel(true);
        }
        showPlainLyrics("正在加载歌词...");
        lyricsLoadTask = new LyricsLoadTask(title, artist == null ? "" : artist);
        lyricsLoadTask.execute();
    }

    private class LyricsLoadTask extends AsyncTask<String, Void, String> {
        private boolean isLrc;
        private final String taskTitle;
        private final String taskArtist;

        LyricsLoadTask(String title, String artist) {
            this.taskTitle = title;
            this.taskArtist = artist;
        }

        @Override
        protected String doInBackground(String... params) {
            // 第一步：尝试获取带时间戳的 LRC（优先，用于同步高亮）
            String lrc = LyricsFetcher.fetchLrcRaw(taskArtist, taskTitle);
            if (lrc != null && !lrc.isEmpty()) {
                isLrc = true;
                return lrc;
            }

            // 第二步：降级为纯文本歌词（无时间戳，仅展示）
            isLrc = false;
            return LyricsFetcher.fetchLyrics(taskArtist, taskTitle);
        }

        @Override
        protected void onPostExecute(String result) {
            if (!taskTitle.equals(CurrentTitle)) {
                return;
            }
            if (result == null || result.trim().isEmpty()) {
                showPlainLyrics("暂无歌词");
                return;
            }

            if (isLrc) {
                // 带时间戳的 LRC → 解析并启用同步
                lrcLines = parseLrc(result);
                currentLrcIndex = -1;
                lyricAdapter.clear();
                lyricAdapter.addAll(lrcLines);
                lyricAdapter.notifyDataSetChanged();
                if (lrcLines.isEmpty()) {
                    showPlainLyrics("暂无歌词");
                }
            } else {
                // 纯文本 → 逐行显示，无高亮
                showPlainLyrics(result);
            }
        }
    }

    /**
     * 纯文本歌词展示（无时间戳，降级方案）
     */
    private void showPlainLyrics(String text) {
        List<LrcLine> plainLines = new ArrayList<>();
        currentLrcIndex = -1;
        if (text != null && !text.trim().isEmpty()) {
            String[] lines = text.split("\n");
            for (String line : lines) {
                if (line.trim().length() > 0) {
                    plainLines.add(new LrcLine(0, line.trim()));
                }
            }
        }
        lrcLines = plainLines;
        lyricAdapter.clear();
        lyricAdapter.addAll(lrcLines);
        lyricAdapter.clearHighlight();
        lyricAdapter.notifyDataSetChanged();
    }

    /**
     * 解析原始 LRC 文本为 LrcLine 列表，按时间戳排序
     */
    private List<LrcLine> parseLrc(String rawLrc) {
        List<LrcLine> lines = new ArrayList<>();
        if (rawLrc == null || rawLrc.isEmpty()) return lines;

        String[] rawLines = rawLrc.split("\n");
        Pattern p = Pattern.compile("\\[(\\d{2}):(\\d{2})[.:](\\d{2,3})\\]");

        for (String line : rawLines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            Matcher m = p.matcher(trimmed);
            List<Long> times = new ArrayList<>();

            while (m.find()) {
                int min = Integer.parseInt(m.group(1));
                int sec = Integer.parseInt(m.group(2));
                String msStr = m.group(3);
                int ms = Integer.parseInt(msStr);
                if (msStr.length() == 2) ms *= 10; // [00:12.34] → 340ms
                times.add((min * 60L + sec) * 1000 + ms);
            }

            String text = trimmed.replaceAll("\\[\\d{2}:\\d{2}[.:]\\d{2,3}\\]", "").trim();
            if (text.isEmpty()) continue;

            for (Long time : times) {
                lines.add(new LrcLine(time, text));
            }
        }

        Collections.sort(lines, (a, b) -> Long.compare(a.getTime(), b.getTime()));
        return lines;
    }

    /**
     * 根据当前播放位置（毫秒），二分查找应高亮的歌词行，并自动滚动
     */
    private void syncLyricHighlight(int positionMs) {
        if (lrcLines.isEmpty()) return;

        // 二分查找：找到时间戳 ≤ positionMs 的最大行
        int lo = 0, hi = lrcLines.size() - 1, best = -1;
        while (lo <= hi) {
            int mid = (lo + hi) / 2;
            if (lrcLines.get(mid).getTime() <= positionMs) {
                best = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }

        if (best != currentLrcIndex) {
            currentLrcIndex = best;
            lyricAdapter.setCurrentLine(currentLrcIndex);
            scrollToCurrentLine(currentLrcIndex);
        }
    }

    /**
     * 将 ListView 滚动到当前歌词行（保持可见区域的 1/3 处）
     */
    private void scrollToCurrentLine(int index) {
        if (index < 0 || lyricListView == null) return;
        // smoothScrollToPosition 会把目标行滚到可见区域的顶部附近
        lyricListView.smoothScrollToPosition(index);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (lyricsLoadTask != null) {
            lyricsLoadTask.cancel(true);
        }
        setWavePlaying(false);
        unbindService(conn);
        unregisterReceiver(broadcastReceiver);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        mGestureDetector.onTouchEvent(event);
        return super.onTouchEvent(event);
    }
}
