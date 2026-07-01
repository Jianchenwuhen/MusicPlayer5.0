package com.example.musicplayer50;

import android.Manifest;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;

import java.io.File;
import java.util.List;

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
    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,};
    private List<Music> musics;
    private ContentObserver mediaObserver;

    private BroadcastReceiver listChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.e("huizhong", "LocalMusicActivity 收到播放列表变化广播");
            loadMusicList();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.localmusic);
        getSupportActionBar().hide();
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        Intent intent = new Intent(this, MusicService.class);
        bindService(intent, conn, Context.BIND_AUTO_CREATE);

        listView = (ListView) findViewById(R.id.listView);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)!= PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},REQUEST_EXTERNAL_STORAGE);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)!= PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},REQUEST_EXTERNAL_STORAGE);
        }

        Button button = (Button)findViewById(R.id.button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                musicService.start();
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

        loadMusicList();

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

        IntentFilter listFilter = new IntentFilter(PlaylistProvider.ACTION_PLAYLIST_CHANGED);
        registerReceiver(listChangeReceiver, listFilter);

        listView.setOnItemClickListener (new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id){
                Log.e("huizhong", "onitemclick");
                Music music = musics.get(position);
                String url = music.getUrl();
                String title = music.getTitle();
                String artist = music.getArtist();

                addTrackToPlaylist(title, artist, url);

                Intent intent = new Intent("startnew");
                intent.putExtra("url",url);
                intent.putExtra("title",title);
                intent.putExtra("artist",artist);

                final Intent eintent = new Intent(createExplicitFromImplicitIntent(LocalMusicActivity.this,intent));
                bindService(eintent,conn, Service.BIND_AUTO_CREATE);
                startService(eintent);
            }
        });
    }

    private void addTrackToPlaylist(String title, String artist, String url) {
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(
                    PlaylistContract.CONTENT_URI,
                    null,
                    PlaylistContract.COLUMN_URL + "=?",
                    new String[]{url},
                    null
            );
            if (cursor != null && cursor.moveToFirst()) {
                Log.e("huizhong", "歌曲已存在于播放列表，按 url 跳过插入");
                return;
            }

            ContentValues values = new ContentValues();
            values.put(PlaylistContract.COLUMN_TITLE, title);
            values.put(PlaylistContract.COLUMN_ARTIST, artist);
            values.put(PlaylistContract.COLUMN_URL, url);
            getContentResolver().insert(PlaylistContract.CONTENT_URI, values);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadMusicList();
        triggerMediaScan();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(listChangeReceiver);
        if (mediaObserver != null) {
            getContentResolver().unregisterContentObserver(mediaObserver);
        }
        unbindService(conn);
    }

    private void triggerMediaScan() {
        try {
            String[] scanDirs = {
                    Environment.getExternalStorageDirectory().getPath() + "/Music",
                    Environment.getExternalStorageDirectory().getPath() + "/Download",
                    Environment.getExternalStorageDirectory().getPath() + "/Alarms",
                    Environment.getExternalStorageDirectory().getPath() + "/Notifications",
            };
            for (String dir : scanDirs) {
                File file = new File(dir);
                if (file.exists() && file.isDirectory()) {
                    Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                    intent.setData(Uri.fromFile(file));
                    sendBroadcast(intent);
                }
            }
        } catch (Exception e) {
            Log.e("huizhong", "触发媒体扫描失败: " + e.getMessage());
        }
    }

    private void loadMusicList() {
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
}
