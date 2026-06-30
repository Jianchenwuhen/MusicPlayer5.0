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
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.IBinder;
import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
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
    private List<LrcLine> lrcLines = new ArrayList<>();
    private int currentLrcIndex = -1;
    private MusicService musicService;
    private TabledatabaseHelper dbHelper;
    private String CurrentTitle = "CurrentTitle";
    private GestureDetector mGestureDetector;

    private ServiceConnection conn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            musicService = ((MusicService.MyBinder) service).getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        this.getWindow().setFlags(
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

        Intent intent = new Intent(this, MusicService.class);
        bindService(intent, conn, Context.BIND_AUTO_CREATE);

        IntentFilter filter = new IntentFilter();
        filter.addAction("seekbarmaxprogress");
        filter.addAction("seekbarprogress");
        filter.addAction("gettitle");
        filter.addAction("pauseimage");
        filter.addAction("playimage");
        filter.addAction("nextsong");
        registerReceiver(broadcastReceiver, filter);

        dbHelper = new TabledatabaseHelper(this, "login.db", null, 1);

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
                    Intent intent = new Intent("changed");
                    intent.putExtra("seekbarprogress", progress);

                    final Intent eiintent = createExplicitFromImplicitIntent(MainActivity.this, intent);
                    if (eiintent != null) {
                        bindService(eiintent, conn, Service.BIND_AUTO_CREATE);
                        startService(eiintent);
                    }
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
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            Cursor cursor = db.query("login", null, null, null, null, null, null);

            if (cursor.moveToFirst()) {
                do {
                    Log.e("huizhong", "CurrentTitle = " + CurrentTitle);

                    if (CurrentTitle.equals(cursor.getString(cursor.getColumnIndexOrThrow("title")))) {
                        Log.e("huizhong", "找到匹配");

                        cursor.moveToNext();

                        if (cursor.isAfterLast()) {
                            Log.e("huizhong", "当前歌曲在最后一行返回第一行");
                            cursor.moveToFirst();

                            String url = cursor.getString(cursor.getColumnIndexOrThrow("url"));
                            String title = cursor.getString(cursor.getColumnIndexOrThrow("title"));
                            String artist = cursor.getString(cursor.getColumnIndexOrThrow("artist"));

                            Intent intent2 = new Intent("startnew");
                            intent2.putExtra("url", url);
                            intent2.putExtra("title", title);
                            intent2.putExtra("artist", artist);

                            final Intent eiiintent = createExplicitFromImplicitIntent(MainActivity.this, intent2);
                            if (eiiintent != null) {
                                bindService(eiiintent, conn, Service.BIND_AUTO_CREATE);
                                startService(eiiintent);
                            }
                            break;

                        } else {
                            Log.e("huizhong", "当前歌曲不是在最后一行");

                            String url = cursor.getString(cursor.getColumnIndexOrThrow("url"));
                            String title = cursor.getString(cursor.getColumnIndexOrThrow("title"));
                            String artist = cursor.getString(cursor.getColumnIndexOrThrow("artist"));

                            cursor.moveToLast();

                            Intent intent2 = new Intent("startnew");
                            intent2.putExtra("url", url);
                            intent2.putExtra("title", title);
                            intent2.putExtra("artist", artist);

                            final Intent eiintent = createExplicitFromImplicitIntent(MainActivity.this, intent2);
                            if (eiintent != null) {
                                bindService(eiintent, conn, Service.BIND_AUTO_CREATE);
                                startService(eiintent);
                            }
                            break;
                        }
                    }
                } while (cursor.moveToNext());
            }

            cursor.close();

        } else if (id == R.id.playlast) {
            SQLiteDatabase dbb = dbHelper.getWritableDatabase();
            Cursor cursorr = dbb.query("login", null, null, null, null, null, null);

            if (cursorr.moveToFirst()) {
                do {
                    if (CurrentTitle.equals(cursorr.getString(cursorr.getColumnIndexOrThrow("title")))) {
                        cursorr.moveToPrevious();

                        if (cursorr.isBeforeFirst()) {
                            cursorr.moveToLast();

                            String url = cursorr.getString(cursorr.getColumnIndexOrThrow("url"));
                            String title = cursorr.getString(cursorr.getColumnIndexOrThrow("title"));
                            String artist = cursorr.getString(cursorr.getColumnIndexOrThrow("artist"));

                            Intent intent8 = new Intent("startnew");
                            intent8.putExtra("url", url);
                            intent8.putExtra("title", title);
                            intent8.putExtra("artist", artist);

                            final Intent eeiintent = createExplicitFromImplicitIntent(MainActivity.this, intent8);
                            if (eeiintent != null) {
                                bindService(eeiintent, conn, Service.BIND_AUTO_CREATE);
                                startService(eeiintent);
                            }
                            break;

                        } else {
                            String url = cursorr.getString(cursorr.getColumnIndexOrThrow("url"));
                            String title = cursorr.getString(cursorr.getColumnIndexOrThrow("title"));
                            String artist = cursorr.getString(cursorr.getColumnIndexOrThrow("artist"));

                            cursorr.moveToNext();

                            Intent intent8 = new Intent("startnew");
                            intent8.putExtra("url", url);
                            intent8.putExtra("title", title);
                            intent8.putExtra("artist", artist);

                            final Intent eeiintent = createExplicitFromImplicitIntent(MainActivity.this, intent8);
                            if (eeiintent != null) {
                                bindService(eeiintent, conn, Service.BIND_AUTO_CREATE);
                                startService(eeiintent);
                            }
                            break;
                        }
                    }
                } while (cursorr.moveToNext());
            }

            cursorr.close();
        }
    }

    private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals("seekbarmaxprogress")) {
                seekBar.setMax(intent.getIntExtra("seekbarmaxprogress", 100));

            } else if (intent.getAction().equals("seekbarprogress")) {
                int progress = intent.getIntExtra("seekbarprogress", 0);
                seekBar.setProgress(progress);
                syncLyricHighlight(progress);

            } else if (intent.getAction().equals("pauseimage")) {
                play.setBackgroundResource(R.drawable.pause);

            } else if (intent.getAction().equals("playimage")) {
                play.setBackgroundResource(R.drawable.play);

            } else if (intent.getAction().equals("gettitle")) {
                CurrentTitle = intent.getStringExtra("title");
                String artist = intent.getStringExtra("artist");
                Log.e("huizhong", "CurrentTitle = " + CurrentTitle);
                textView2.setText(artist);
                textView.setText(CurrentTitle);
                loadLyrics(CurrentTitle, artist);

            } else if (intent.getAction().equals("nextsong")) {
                Log.e("huizhong", "歌曲播放结束，接收到广播，发送下一首歌曲");

                SQLiteDatabase dbb = dbHelper.getWritableDatabase();
                Cursor cursorr = dbb.query("login", null, null, null, null, null, null);

                if (cursorr.moveToFirst()) {
                    do {
                        Log.e("huizhong", "CurrentTitle = " + CurrentTitle);

                        if (CurrentTitle.equals(cursorr.getString(cursorr.getColumnIndexOrThrow("title")))) {
                            Log.e("huizhong", "找到匹配");

                            cursorr.moveToNext();

                            if (cursorr.isAfterLast()) {
                                Log.e("huizhong", "当前歌曲在最后一行返回第一行");
                                cursorr.moveToFirst();

                                String url = cursorr.getString(cursorr.getColumnIndexOrThrow("url"));
                                String title = cursorr.getString(cursorr.getColumnIndexOrThrow("title"));
                                String artist = cursorr.getString(cursorr.getColumnIndexOrThrow("artist"));

                                Intent intent2 = new Intent("startnew");
                                intent2.putExtra("url", url);
                                intent2.putExtra("title", title);
                                intent2.putExtra("artist", artist);

                                final Intent eiiintent = createExplicitFromImplicitIntent(MainActivity.this, intent2);
                                if (eiiintent != null) {
                                    bindService(eiiintent, conn, Service.BIND_AUTO_CREATE);
                                    startService(eiiintent);
                                }
                                break;

                            } else {
                                Log.e("huizhong", "当前歌曲不是在最后一行");

                                String url = cursorr.getString(cursorr.getColumnIndexOrThrow("url"));
                                String title = cursorr.getString(cursorr.getColumnIndexOrThrow("title"));
                                String artist = cursorr.getString(cursorr.getColumnIndexOrThrow("artist"));

                                cursorr.moveToLast();

                                Intent intent2 = new Intent("startnew");
                                intent2.putExtra("url", url);
                                intent2.putExtra("title", title);
                                intent2.putExtra("artist", artist);

                                final Intent eiintent = createExplicitFromImplicitIntent(MainActivity.this, intent2);
                                if (eiintent != null) {
                                    bindService(eiintent, conn, Service.BIND_AUTO_CREATE);
                                    startService(eiintent);
                                }
                                break;
                            }
                        }
                    } while (cursorr.moveToNext());
                }

                cursorr.close();
            }
        }
    };

    // ==================== 歌词加载 & 同步 ====================

    /**
     * 切歌时触发：先尝试获取 LRC 同步歌词，失败则降级为纯文本
     */
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

            // 第一步：尝试获取带时间戳的 LRC（优先，用于同步高亮）
            String lrc = LyricsFetcher.fetchLrcRaw(artist, title);
            if (lrc != null && !lrc.isEmpty()) {
                isLrc = true;
                return lrc;
            }

            // 第二步：降级为纯文本歌词（无时间戳，仅展示）
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
            // 发送歌词加载完成广播（供其他组件监听）
            sendBroadcast(new Intent("lyricsLoaded"));
        }
    }

    /**
     * 纯文本歌词展示（无时间戳，降级方案）
     */
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
     * 将当前歌词行滚动到 ListView 垂直居中位置
     */
    private void scrollToCurrentLine(final int index) {
        if (index < 0 || lyricListView == null) return;
        lyricListView.post(new Runnable() {
            @Override
            public void run() {
                int listHeight = lyricListView.getHeight();
                if (listHeight <= 0) return;
                // offset = 居中偏移量，让当前行显示在 ListView 中间
                int offset = listHeight / 2 - lyricListView.getPaddingTop();
                lyricListView.setSelectionFromTop(index, offset);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        unbindService(conn);
        unregisterReceiver(broadcastReceiver);
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
    public boolean onTouchEvent(MotionEvent event) {
        mGestureDetector.onTouchEvent(event);
        return super.onTouchEvent(event);
    }
}