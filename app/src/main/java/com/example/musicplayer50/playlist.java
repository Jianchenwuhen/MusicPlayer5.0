package com.example.musicplayer50;

import android.Manifest;
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
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by 惠中 on 2016/12/23.
 */
public class playlist extends AppCompatActivity {
    private int count;
    private TabledatabaseHelper dbHelper;
    private ArrayAdapter adapter;
    private MusicService musicService;
    private ListView listView;
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

    // BroadcastReceiver：监听播放列表数据变化，自动刷新界面
    private BroadcastReceiver playlistReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String op = intent.getStringExtra("operation");
            Log.e("huizhong", "playlist 收到广播: " + op);
            Toast.makeText(playlist.this, "播放列表已更新(" + op + ")", Toast.LENGTH_SHORT).show();
            refreshPlaylist();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.playlist);
        getSupportActionBar().hide();
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        Intent intent = new Intent(this, MusicService.class);
        bindService(intent, conn, Context.BIND_AUTO_CREATE);

        dbHelper = new TabledatabaseHelper(this,"login.db",null,1);
        Cursor cursor = getContentResolver().query(PlaylistContract.CONTENT_URI, null, null, null, null);
        musics = new ArrayList<Music>();
        musics.clear();
        count = cursor == null ? 0 : cursor.getCount();
        for (int i = 0; i < count; i++) {
                cursor.moveToNext();
                String title = cursor.getString(cursor.getColumnIndexOrThrow("title"));
                String artist = cursor.getString(cursor.getColumnIndexOrThrow("artist"));
                String url = cursor.getString(cursor.getColumnIndexOrThrow("url"));
                Music music = new Music();
                music.setTitle(title);
                music.setArtist(artist);
                music.setUrl(url);
                musics.add(music);
                Log.e("huizhong", "music adds succeedly");
        }
        if (cursor != null) {
            cursor.close();
        }

        Button button = (Button)findViewById(R.id.button2);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                musicService.start();
            }
        });

        Button button6 = (Button)findViewById(R.id.button6);
        button6.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getContentResolver().delete(PlaylistContract.CONTENT_URI, null, null);
                Log.e("huizhong","count = "+count);
                musics.clear();
                adapter.notifyDataSetChanged();
            }
        });


        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_EXTERNAL_STORAGE);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_EXTERNAL_STORAGE);
        }

        adapter = new MusicAdapter(playlist.this, R.layout.musicitem, musics); //新建想对应的适配器
      // adapter = new ArrayAdapter<String>(playlist.this,android.R.layout.simple_list_item_1,list);     //用字符串适配器试验
        listView = (ListView) findViewById(R.id.listView2);
        listView.setAdapter(adapter);

        this.registerForContextMenu(listView);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                Music music = musics.get(position);
                String url = music.getUrl();
                String title = music.getTitle();
                String artist = music.getArtist();

                Intent intent = new Intent("startnew");
                intent.putExtra("url", url);
                intent.putExtra("title", title);
                intent.putExtra("artist", artist);

                final Intent eintent = new Intent(createExplicitFromImplicitIntent(playlist.this, intent));
                bindService(eintent, conn, Service.BIND_AUTO_CREATE);
                startService(eintent);
            }
        });

        // 注册广播接收器，监听播放列表变化
        IntentFilter filter = new IntentFilter(PlaylistProvider.ACTION_PLAYLIST_CHANGED);
        registerReceiver(playlistReceiver, filter);
    }

    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenu.ContextMenuInfo menuInfo){
        menu.add(0,1,0,"删除");
    }
    public boolean onContextItemSelected(MenuItem item){
        AdapterView.AdapterContextMenuInfo menuInfo = (AdapterView.AdapterContextMenuInfo)item.getMenuInfo();
        switch(item.getItemId()){
            case 1:
                String title = ((TextView)menuInfo.targetView.findViewById(R.id.songname)).getText().toString();

                getContentResolver().delete(PlaylistContract.CONTENT_URI, "title =?", new String[]{title+""});
                Log.e("huizhong","删除SQL项成功" );
                musics.remove(menuInfo.position);
                adapter.notifyDataSetChanged();
                break;
        }
        return true;
    }

    /**
     * 广播触发的刷新：重新查询 Provider 并更新列表
     */
    private void refreshPlaylist() {
        Cursor cursor = getContentResolver().query(PlaylistContract.CONTENT_URI, null, null, null, null);
        musics.clear();
        if (cursor != null) {
            while (cursor.moveToNext()) {
                String title = cursor.getString(cursor.getColumnIndexOrThrow("title"));
                String artist = cursor.getString(cursor.getColumnIndexOrThrow("artist"));
                String url = cursor.getString(cursor.getColumnIndexOrThrow("url"));
                Music music = new Music();
                music.setTitle(title);
                music.setArtist(artist);
                music.setUrl(url);
                musics.add(music);
            }
            cursor.close();
        }
        adapter.notifyDataSetChanged();
        Log.e("huizhong", "playlist 刷新完成，共 " + musics.size() + " 首歌");
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(playlistReceiver);
        unbindService(conn);
        super.onDestroy();
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