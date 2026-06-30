package com.example.musicplayer50;

import android.Manifest;
import android.app.Service;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
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

/**
 * Created by 惠中 on 2016/12/12.
 */
public class LocalMusicActivity extends AppCompatActivity {

    private TabledatabaseHelper dbHelper;
    private MusicService musicService;
    private MusicAdapter adapter;
    private Boolean Exist = false;
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



        dbHelper = new TabledatabaseHelper(this,"login.db",null,1);

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
                Music music = musics.get(position);
                String url = music.getUrl();
                String title = music.getTitle();
                String artist = music.getArtist();

                ContentValues values = new ContentValues();
                Cursor cursor = getContentResolver().query(PlaylistContract.CONTENT_URI, null, null, null, null);
                Log.e("huizhong","当前歌曲的title是："+title );
                if (cursor != null) {
                    for (int i = 0; i < cursor.getCount(); i++) {
                        cursor.moveToNext();
                        Log.e("huizhong","当前游标title是："+cursor.getString(cursor.getColumnIndexOrThrow("title")));
                        if(title.equals(cursor.getString(cursor.getColumnIndexOrThrow("title")))) {
                            Log.e("huizhong","已经存在歌曲，不插入了" );
                            Exist = true;
                            break;
                        }
                    }
                }
                Log.e("huizhong","当前歌曲是否存在 "+Exist );
                if(Exist==false) {
                    Log.e("huizhong", "创建键");
                    values.put("title", title);
                    values.put("artist", artist);
                    values.put("url", url);
                    getContentResolver().insert(PlaylistContract.CONTENT_URI, values);
                    values.clear();
                    Log.e("huizhong", "成功插入login表");
                    Exist = false;
                }
                if (cursor != null) {
                    cursor.close();
                }
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
    @Override
    protected void onResume() {
        super.onResume();
        // 每次回到此界面时重新查询（覆盖从别的 Activity 返回的场景）
        loadMusicList();
        // 主动触发系统扫描标准音乐目录，让新拖入的 MP3 尽快被 MediaStore 收录
        triggerMediaScan();
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

    /**
     * 从 MediaStore 重新查询本地音乐，刷新 ListView
     */
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
        // Retrieve all services that can match the given intent
        PackageManager pm = context.getPackageManager();
        List<ResolveInfo> resolveInfo = pm.queryIntentServices(implicitIntent, 0);
        // Make sure only one match was found
        if (resolveInfo == null || resolveInfo.size() != 1) {
            return null;
        }
        // Get component info and create ComponentName
        ResolveInfo serviceInfo = resolveInfo.get(0);
        String packageName = serviceInfo.serviceInfo.packageName;
        String className = serviceInfo.serviceInfo.name;
        ComponentName component = new ComponentName(packageName, className);
        // Create a new intent. Use the old one for extras and such reuse
        Intent explicitIntent = new Intent(implicitIntent);
        // Set the component to be explicit
        explicitIntent.setComponent(component);
        return explicitIntent;
    }
}
