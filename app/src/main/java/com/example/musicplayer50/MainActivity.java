package com.example.musicplayer50;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

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
    private List<LrcLine> lrcLines = new ArrayList<>();
    private int currentLrcIndex = -1;
    private MusicService musicService;
    private String currentTitle = "CurrentTitle";
    private GestureDetector gestureDetector;

    private final ServiceConnection conn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            musicService = ((MusicService.MyBinder) service).getService();
            musicService.restoreIfAvailable();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            musicService = null;
        }
    };

    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) {
                return;
            }

            String action = intent.getAction();
            if ("seekbarmaxprogress".equals(action)) {
                seekBar.setMax(intent.getIntExtra("seekbarmaxprogress", 100));
            } else if ("seekbarprogress".equals(action)) {
                int progress = intent.getIntExtra("seekbarprogress", 0);
                seekBar.setProgress(progress);
                syncLyricHighlight(progress);
            } else if ("pauseimage".equals(action)) {
                play.setBackgroundResource(R.drawable.pause);
            } else if ("playimage".equals(action)) {
                play.setBackgroundResource(R.drawable.play);
            } else if ("gettitle".equals(action)) {
                currentTitle = intent.getStringExtra("title");
                String artist = intent.getStringExtra("artist");
                Log.e("huizhong", "CurrentTitle = " + currentTitle);
                textView2.setText(artist);
                textView.setText(currentTitle);
                loadLyrics(currentTitle, artist);
            } else if ("playbackerror".equals(action)) {
                Toast.makeText(
                        MainActivity.this,
                        intent.getStringExtra("message"),
                        Toast.LENGTH_SHORT
                ).show();
            } else if ("playlistempty".equals(action)) {
                seekBar.setProgress(0);
                Toast.makeText(MainActivity.this, "播放列表为空，请先添加歌曲", Toast.LENGTH_SHORT).show();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

        textView2 = (TextView) findViewById(R.id.textView2);
        textView = (TextView) findViewById(R.id.textView);
        lyricListView = (ListView) findViewById(R.id.lyricListView);
        lyricAdapter = new LyricAdapter(this, lrcLines);
        lyricListView.setAdapter(lyricAdapter);

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

        Intent serviceIntent = new Intent(this, MusicService.class);
        bindService(serviceIntent, conn, Context.BIND_AUTO_CREATE);

        IntentFilter filter = new IntentFilter();
        filter.addAction("seekbarmaxprogress");
        filter.addAction("seekbarprogress");
        filter.addAction("gettitle");
        filter.addAction("pauseimage");
        filter.addAction("playimage");
        filter.addAction("playbackerror");
        filter.addAction("playlistempty");
        registerReceiver(broadcastReceiver, filter);

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    Intent intent = new Intent("changed");
                    intent.putExtra("seekbarprogress", progress);

                    Intent explicitIntent = createExplicitFromImplicitIntent(MainActivity.this, intent);
                    if (explicitIntent != null) {
                        bindService(explicitIntent, conn, Service.BIND_AUTO_CREATE);
                        startService(explicitIntent);
                    }
                    syncLyricHighlight(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if ((e1.getRawX() - e2.getRawX()) > 200) {
                    startActivity(new Intent(MainActivity.this, playlist.class));
                    return true;
                }

                if ((e2.getRawX() - e1.getRawX()) > 200) {
                    startActivity(new Intent(MainActivity.this, LocalMusicActivity.class));
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
            startActivity(new Intent(MainActivity.this, LocalMusicActivity.class));
        } else if (id == R.id.onlinemusic) {
            startActivity(new Intent(MainActivity.this, OnlineMusicActivity.class));
        } else if (id == R.id.playlist) {
            startActivity(new Intent(MainActivity.this, playlist.class));
        } else if (id == R.id.playnext) {
            if (musicService != null) {
                musicService.playNextTrack();
            }
        } else if (id == R.id.playlast) {
            if (musicService != null) {
                musicService.playPreviousTrack();
            }
        }
    }

    private void loadLyrics(String title, String artist) {
        if (title == null || title.trim().length() == 0) {
            showPlainLyrics("暂无歌词");
            return;
        }
        showPlainLyrics("正在加载歌词...");
        new LyricsLoadTask().execute(title, artist == null ? "" : artist);
    }

    private class LyricsLoadTask extends AsyncTask<String, Void, String> {
        private boolean isLrc;

        @Override
        protected String doInBackground(String... params) {
            String title = params[0];
            String artist = params.length > 1 ? params[1] : "";

            String lrc = LyricsFetcher.fetchLrcRaw(artist, title);
            if (lrc != null && !lrc.isEmpty()) {
                isLrc = true;
                return lrc;
            }

            isLrc = false;
            return LyricsFetcher.fetchLyrics(artist, title);
        }

        @Override
        protected void onPostExecute(String result) {
            if (result == null || result.trim().isEmpty()) {
                showPlainLyrics("暂无歌词");
                sendBroadcast(new Intent("lyricsLoaded"));
                return;
            }

            if (isLrc) {
                lrcLines = parseLrc(result);
                currentLrcIndex = -1;
                lyricAdapter.clear();
                lyricAdapter.addAll(lrcLines);
                lyricAdapter.notifyDataSetChanged();
                if (lrcLines.isEmpty()) {
                    showPlainLyrics("暂无歌词");
                }
            } else {
                showPlainLyrics(result);
            }
            sendBroadcast(new Intent("lyricsLoaded"));
        }
    }

    private void showPlainLyrics(String text) {
        lrcLines.clear();
        currentLrcIndex = -1;
        if (text != null && !text.trim().isEmpty()) {
            String[] lines = text.split("\n");
            for (String line : lines) {
                if (line.trim().length() > 0) {
                    lrcLines.add(new LrcLine(0, line.trim()));
                }
            }
            lyricAdapter.clear();
            lyricAdapter.addAll(lrcLines);
        } else {
            lyricAdapter.clear();
        }
        lyricAdapter.clearHighlight();
        lyricAdapter.notifyDataSetChanged();
    }

    private List<LrcLine> parseLrc(String rawLrc) {
        List<LrcLine> lines = new ArrayList<>();
        if (rawLrc == null || rawLrc.isEmpty()) {
            return lines;
        }

        String[] rawLines = rawLrc.split("\n");
        Pattern pattern = Pattern.compile("\\[(\\d{2}):(\\d{2})[.:](\\d{2,3})\\]");

        for (String line : rawLines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            Matcher matcher = pattern.matcher(trimmed);
            List<Long> times = new ArrayList<>();

            while (matcher.find()) {
                int min = Integer.parseInt(matcher.group(1));
                int sec = Integer.parseInt(matcher.group(2));
                String msStr = matcher.group(3);
                int ms = Integer.parseInt(msStr);
                if (msStr.length() == 2) {
                    ms *= 10;
                }
                times.add((min * 60L + sec) * 1000 + ms);
            }

            String text = trimmed.replaceAll("\\[\\d{2}:\\d{2}[.:]\\d{2,3}\\]", "").trim();
            if (text.isEmpty()) {
                continue;
            }

            for (Long time : times) {
                lines.add(new LrcLine(time, text));
            }
        }

        Collections.sort(lines, (a, b) -> Long.compare(a.getTime(), b.getTime()));
        return lines;
    }

    private void syncLyricHighlight(int positionMs) {
        if (lrcLines.isEmpty()) {
            return;
        }

        int lo = 0;
        int hi = lrcLines.size() - 1;
        int best = -1;
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

    private void scrollToCurrentLine(final int index) {
        if (index < 0 || lyricListView == null) {
            return;
        }
        lyricListView.post(new Runnable() {
            @Override
            public void run() {
                int listHeight = lyricListView.getHeight();
                if (listHeight <= 0) {
                    return;
                }
                int offset = listHeight / 2 - lyricListView.getPaddingTop();
                lyricListView.setSelectionFromTop(index, offset);
            }
        });
    }

    public static Intent createExplicitFromImplicitIntent(Context context, Intent implicitIntent) {
        PackageManager pm = context.getPackageManager();
        List<ResolveInfo> resolveInfo = pm.queryIntentServices(implicitIntent, 0);
        if (resolveInfo == null || resolveInfo.size() != 1) {
            return null;
        }

        ResolveInfo serviceInfo = resolveInfo.get(0);
        String packageName = serviceInfo.serviceInfo.packageName;
        String className = serviceInfo.serviceInfo.name;
        ComponentName component = new ComponentName(packageName, className);

        Intent explicitIntent = new Intent(implicitIntent);
        explicitIntent.setComponent(component);
        return explicitIntent;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(conn);
        unregisterReceiver(broadcastReceiver);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        gestureDetector.onTouchEvent(event);
        return super.onTouchEvent(event);
    }
}
