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
import android.widget.SeekBar;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

import java.util.List;

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
    private TextView lyricText;
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
        lyricText = (TextView) findViewById(R.id.lyricText);
        lyricText.setVisibility(View.GONE);
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
                seekBar.setProgress(intent.getIntExtra("seekbarprogress", 0));

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

    private void loadLyrics(String title, String artist) {
        if (title == null || title.trim().length() == 0) {
            lyricText.setVisibility(View.GONE);
            return;
        }
        lyricText.setVisibility(View.GONE);
        new LyricsTask().execute(title, artist == null ? "" : artist);
    }

    private class LyricsTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... params) {
            HttpURLConnection connection = null;
            BufferedReader reader = null;
            try {
                String title = params[0];
                String artist = params.length > 1 ? params[1] : "";
                String urlText = "https://lrclib.net/api/search?track_name="
                        + URLEncoder.encode(title, "UTF-8");
                if (artist != null && artist.trim().length() > 0 && !artist.equals("<unknown>")) {
                    urlText += "&artist_name=" + URLEncoder.encode(artist, "UTF-8");
                }

                URL url = new URL(urlText);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("User-Agent", "MusicPlayer5.0 Android Demo");
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);

                InputStream inputStream = connection.getInputStream();
                reader = new BufferedReader(new InputStreamReader(inputStream));
                StringBuilder builder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    builder.append(line);
                }

                JSONArray results = new JSONArray(builder.toString());
                if (results.length() == 0) {
                    return "";
                }

                JSONObject item = results.getJSONObject(0);
                String lyrics = item.optString("plainLyrics", "");
                if (lyrics.length() == 0) {
                    lyrics = item.optString("syncedLyrics", "");
                }
                return cleanLyrics(lyrics);
            } catch (Exception e) {
                return "";
            } finally {
                try {
                    if (reader != null) {
                        reader.close();
                    }
                } catch (Exception ignored) {
                }
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }

        @Override
        protected void onPostExecute(String lyrics) {
            if (lyrics == null || lyrics.trim().length() == 0) {
                lyricText.setVisibility(View.GONE);
            } else {
                lyricText.setText(lyrics);
                lyricText.setVisibility(View.VISIBLE);
            }
        }
    }

    private String cleanLyrics(String lyrics) {
        if (lyrics == null) {
            return "";
        }
        String[] lines = lyrics.split("\n");
        StringBuilder builder = new StringBuilder();
        int count = 0;
        for (String line : lines) {
            String text = line.replaceAll("\\[\\d{2}:\\d{2}\\.\\d{2,3}\\]", "").trim();
            if (text.length() > 0) {
                builder.append(text).append("\n");
                count++;
            }
            if (count >= 6) {
                break;
            }
        }
        return builder.toString().trim();
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