package com.example.musicplayer50;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by 惠中 on 2016/12/23.
 */
public class playlist extends AppCompatActivity {
    private int count;
    private MusicAdapter adapter;
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
    private List<Music> musics;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.playlist);
        UiUtils.setupEdgeToEdge(this);
        UiUtils.applySystemBarInsets(findViewById(R.id.contentRoot));

        Intent intent = new Intent(this, MusicService.class);
        bindService(intent, conn, Context.BIND_AUTO_CREATE);

        Cursor cursor = getContentResolver().query(
                PlaylistContract.CONTENT_URI,
                null,
                null,
                null,
                PlaylistContract.COLUMN_ID + " ASC");
        musics = new ArrayList<Music>();
        if (cursor != null) {
            while (cursor.moveToNext()) {
                String title = cursor.getString(cursor.getColumnIndexOrThrow(PlaylistContract.COLUMN_TITLE));
                String artist = cursor.getString(cursor.getColumnIndexOrThrow(PlaylistContract.COLUMN_ARTIST));
                String url = cursor.getString(cursor.getColumnIndexOrThrow(PlaylistContract.COLUMN_URL));
                Music music = new Music();
                music.setTitle(title);
                music.setArtist(artist);
                music.setUrl(url);
                musics.add(music);
                Log.e("huizhong", "music adds succeedly");
            }
            count = musics.size();
            cursor.close();
        }

        Button button = (Button)findViewById(R.id.button2);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (musicService != null) {
                    musicService.start();
                }
            }
        });

        Button button6 = (Button)findViewById(R.id.button6);
        button6.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getContentResolver().delete(PlaylistContract.CONTENT_URI, null, null);
                Log.e("huizhong","count = "+count);
                musics.clear();
                count = 0;
                adapter.notifyDataSetChanged();
            }
        });

        adapter = new MusicAdapter(playlist.this, R.layout.musicitem, musics); //新建想对应的适配器
      // adapter = new ArrayAdapter<String>(playlist.this,android.R.layout.simple_list_item_1,list);     //用字符串适配器试验
        listView = (ListView) findViewById(R.id.listView2);
        listView.setEmptyView(findViewById(R.id.emptyPlaylist));
        listView.setAdapter(adapter);

        this.registerForContextMenu(listView);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                Music music = musics.get(position);
                String url = music.getUrl();
                String title = music.getTitle();
                String artist = music.getArtist();

                Intent intent = new Intent(playlist.this, MusicService.class);
                intent.setAction("startnew");
                intent.putExtra("url", url);
                intent.putExtra("title", title);
                intent.putExtra("artist", artist);

                startService(intent);
            }
        });
    }

    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenu.ContextMenuInfo menuInfo){
        menu.add(0,1,0,"删除");
    }
    public boolean onContextItemSelected(MenuItem item){
        AdapterView.AdapterContextMenuInfo menuInfo = (AdapterView.AdapterContextMenuInfo)item.getMenuInfo();
        switch(item.getItemId()){
            case 1:
                Music music = musics.get(menuInfo.position);
                getContentResolver().delete(
                        PlaylistContract.CONTENT_URI,
                        PlaylistContract.COLUMN_TITLE + " = ? AND " + PlaylistContract.COLUMN_URL + " = ?",
                        new String[]{music.getTitle(), music.getUrl()});
                Log.e("huizhong","删除SQL项成功" );
                musics.remove(menuInfo.position);
                count = musics.size();
                adapter.notifyDataSetChanged();
                break;
        }
        return true;
    }
    @Override
    protected void onDestroy() {
        unbindService(conn);
        super.onDestroy();
    }
}
