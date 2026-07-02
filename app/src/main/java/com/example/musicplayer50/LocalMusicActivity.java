package com.example.musicplayer50;

import android.Manifest;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by 惠中 on 2016/12/12.
 */
public class LocalMusicActivity extends AppCompatActivity {

    private MusicService musicService;
    private MusicAdapter adapter;
    ListView listView;
    private ServiceConnection conn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            musicService = ((MusicService.MyBinder) service).getService();
        }
        @Override
        public void onServiceDisconnected(ComponentName name) {
        }
    };
    private static final int REQUEST_AUDIO_PERMISSION = 1;
    private List<Music> musics;
    private ContentObserver mediaObserver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.localmusic);
        UiUtils.setupEdgeToEdge(this);
        UiUtils.applySystemBarInsets(findViewById(R.id.contentRoot));

        Intent intent = new Intent(this, MusicService.class);
        bindService(intent, conn, Context.BIND_AUTO_CREATE);

        listView = (ListView) findViewById(R.id.listView);
        listView.setEmptyView(findViewById(R.id.emptyLocalMusic));
        requestAudioPermissionIfNeeded();

        Button button = (Button)findViewById(R.id.button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (musicService != null) {
                    musicService.start();
                }
            }
        });
        Button button3 = (Button)findViewById(R.id.button3);
        button3.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent3 = new Intent(LocalMusicActivity.this,playlist.class);
                startActivity(intent3);
            }
        });

        // 首次加载音乐列表
        loadMusicList();

        // 注册 ContentObserver：系统媒体库一有变化就自动刷新列表
        mediaObserver = new ContentObserver(new Handler()) {
            @Override
            public void onChange(boolean selfChange) {
                super.onChange(selfChange);
                Log.e("huizhong", "MediaStore 发生变化，自动刷新本地音乐列表");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        loadMusicList();
                    }
                });
            }
        };
        getContentResolver().registerContentObserver(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                true, mediaObserver);

        listView.setOnItemClickListener (new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id){
                Log.e("huizhong", "onitemclick");
                if (musics == null || position < 0 || position >= musics.size()) {
                    return;
                }
                Music music = musics.get(position);
                String url = music.getUrl();
                String title = music.getTitle();
                String artist = music.getArtist();

                Cursor cursor = getContentResolver().query(
                        PlaylistContract.CONTENT_URI,
                        null,
                        PlaylistContract.COLUMN_TITLE + " = ? AND " + PlaylistContract.COLUMN_URL + " = ?",
                        new String[]{title, url},
                        null);
                try {
                    Log.e("huizhong","当前歌曲的title是："+title );
                    boolean exists = cursor != null && cursor.moveToFirst();
                    Log.e("huizhong","当前歌曲是否存在 "+exists );
                    if(!exists) {
                        Log.e("huizhong", "创建键");
                        ContentValues values = new ContentValues();
                        values.put(PlaylistContract.COLUMN_TITLE, title);
                        values.put(PlaylistContract.COLUMN_ARTIST, artist);
                        values.put(PlaylistContract.COLUMN_URL, url);
                        getContentResolver().insert(PlaylistContract.CONTENT_URI, values);
                        Log.e("huizhong", "成功插入login表");
                    }
                } finally {
                    if (cursor != null) {
                        cursor.close();
                    }
                }
                Intent intent = new Intent(LocalMusicActivity.this, MusicService.class);
                intent.setAction("startnew");
                intent.putExtra("url",url);
                intent.putExtra("title",title);
                intent.putExtra("artist",artist);

                startService(intent);
                openPlayerDetail();
            }
        });
    }
    @Override
    protected void onResume() {
        super.onResume();
        // 主动触发系统扫描标准音乐目录，让新拖入的 MP3 尽快被 MediaStore 收录
        triggerMediaScan();
        // 每次回到此界面时重新查询（覆盖从别的 Activity 返回的场景）
        loadMusicList();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaObserver != null) {
            getContentResolver().unregisterContentObserver(mediaObserver);
        }
        unbindService(conn);
    }

    /**
     * 主动通知系统扫描 Music / Download 目录中的新媒体文件
     */
    private void triggerMediaScan() {
        try {
            String root = android.os.Environment.getExternalStorageDirectory().getPath();
            String[] scanDirs = {
                    root + "/Music",
                    root + "/Download",
                    root + "/Alarms",
                    root + "/Notifications",
            };
            MediaScannerConnection.scanFile(
                    this,
                    scanDirs,
                    null,
                    new MediaScannerConnection.OnScanCompletedListener() {
                        @Override
                        public void onScanCompleted(String path, android.net.Uri uri) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    loadMusicList();
                                }
                            });
                        }
                    });
        } catch (Exception e) {
            Log.e("huizhong", "触发媒体扫描失败: " + e.getMessage());
        }
    }

    /**
     * 从 MediaStore 重新查询本地音乐，刷新 ListView
     */
    private void loadMusicList() {
        if (!hasAudioPermission()) {
            musics = new ArrayList<Music>();
            if (adapter != null) {
                adapter.clear();
                adapter.notifyDataSetChanged();
            }
            return;
        }
        Findmusic findmusic = new Findmusic();
        musics = findmusic.getmusics(getContentResolver());
        if (adapter == null) {
            adapter = new MusicAdapter(LocalMusicActivity.this, R.layout.musicitem, musics);
            listView.setAdapter(adapter);
        } else {
            adapter.clear();
            adapter.addAll(musics);
            adapter.notifyDataSetChanged();
        }
    }

    private void requestAudioPermissionIfNeeded() {
        if (!hasAudioPermission()) {
            ActivityCompat.requestPermissions(this, new String[]{getAudioPermission()}, REQUEST_AUDIO_PERMISSION);
        }
    }

    private void openPlayerDetail() {
        Intent playerIntent = new Intent(this, MainActivity.class);
        playerIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(playerIntent);
    }

    private boolean hasAudioPermission() {
        return ContextCompat.checkSelfPermission(this, getAudioPermission()) == PackageManager.PERMISSION_GRANTED;
    }

    private String getAudioPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            return Manifest.permission.READ_MEDIA_AUDIO;
        }
        return Manifest.permission.READ_EXTERNAL_STORAGE;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_AUDIO_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadMusicList();
            } else {
                Toast.makeText(this, "需要音频权限才能读取本地音乐", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
