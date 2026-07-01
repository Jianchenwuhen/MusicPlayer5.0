package com.example.musicplayer50;

import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class OnlineMusicActivity extends AppCompatActivity {
    private static final String[] SEARCH_WORDS = {
            "pop", "rock", "jazz", "piano", "guitar",
            "dance", "love", "summer", "classic", "hip hop"
    };

    private EditText searchKeyword;
    private TextView resultText;
    private ListView onlineMusicListView;
    private MusicAdapter onlineMusicAdapter;
    private List<Music> onlineTracks;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.online_music);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        searchKeyword = (EditText) findViewById(R.id.searchKeyword);
        resultText = (TextView) findViewById(R.id.onlineResult);
        onlineMusicListView = (ListView) findViewById(R.id.onlineMusicListView);
        onlineTracks = new ArrayList<Music>();
        onlineMusicAdapter = new MusicAdapter(this, R.layout.musicitem, onlineTracks);
        onlineMusicListView.setAdapter(onlineMusicAdapter);
        onlineMusicListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                playOnlineTrack(onlineTracks.get(position));
            }
        });

        Button searchButton = (Button) findViewById(R.id.searchOnlineMusic);
        searchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String keyword = searchKeyword.getText().toString().trim();
                if (keyword.length() == 0) {
                    resultText.setText("请输入搜索关键词");
                    onlineTracks.clear();
                    onlineMusicAdapter.notifyDataSetChanged();
                } else {
                    resultText.setText("正在搜索: " + keyword);
                    new LoadOnlineMusicTask().execute(keyword);
                }
            }
        });

        Button loadButton = (Button) findViewById(R.id.loadOnlineMusic);
        loadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                resultText.setText("正在获取在线推荐...");
                new LoadOnlineMusicTask().execute();
            }
        });
    }

    private void playOnlineTrack(Music music) {
        if (music.getUrl() == null || music.getUrl().trim().length() == 0) {
            Toast.makeText(this, "当前歌曲没有可播放链接", Toast.LENGTH_SHORT).show();
            return;
        }

        addTrackToPlaylist(music);

        Intent intent = new Intent(this, MusicService.class);
        intent.setAction("startnew");
        intent.putExtra("url", music.getUrl());
        intent.putExtra("title", music.getTitle());
        intent.putExtra("artist", music.getArtist());
        startService(intent);
        Toast.makeText(this, "正在播放: " + music.getTitle(), Toast.LENGTH_SHORT).show();
    }

    private void addTrackToPlaylist(Music music) {
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(
                    PlaylistContract.CONTENT_URI,
                    null,
                    PlaylistContract.COLUMN_URL + "=?",
                    new String[]{music.getUrl()},
                    null
            );

            if (cursor != null && cursor.moveToFirst()) {
                return;
            }

            ContentValues values = new ContentValues();
            values.put(PlaylistContract.COLUMN_TITLE, music.getTitle());
            values.put(PlaylistContract.COLUMN_ARTIST, music.getArtist());
            values.put(PlaylistContract.COLUMN_URL, music.getUrl());
            getContentResolver().insert(PlaylistContract.CONTENT_URI, values);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private class LoadOnlineMusicTask extends AsyncTask<String, Void, OnlineResult> {
        @Override
        protected OnlineResult doInBackground(String... keywords) {
            HttpURLConnection connection = null;
            BufferedReader reader = null;

            try {
                String keyword;
                if (keywords.length > 0 && keywords[0] != null && keywords[0].trim().length() > 0) {
                    keyword = keywords[0].trim();
                } else {
                    keyword = SEARCH_WORDS[new Random().nextInt(SEARCH_WORDS.length)];
                }

                String encodedKeyword = URLEncoder.encode(keyword, "UTF-8");
                URL url = new URL("https://itunes.apple.com/search?term=" + encodedKeyword + "&media=music&entity=song&limit=8");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);

                InputStream inputStream = connection.getInputStream();
                reader = new BufferedReader(new InputStreamReader(inputStream));
                StringBuilder builder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    builder.append(line);
                }

                JSONObject jsonObject = new JSONObject(builder.toString());
                JSONArray results = jsonObject.getJSONArray("results");
                List<Music> musics = new ArrayList<Music>();

                for (int i = 0; i < results.length(); i++) {
                    JSONObject item = results.getJSONObject(i);
                    String previewUrl = item.optString("previewUrl", "");
                    if (previewUrl.length() == 0) {
                        continue;
                    }

                    Music music = new Music();
                    music.setTitle(item.optString("trackName", "未知歌曲"));
                    music.setArtist(item.optString("artistName", "未知歌手"));
                    music.setUrl(previewUrl);
                    music.setDuration(item.optLong("trackTimeMillis", 0L));
                    musics.add(music);
                }

                String message;
                if (musics.isEmpty()) {
                    message = "没有找到可播放的在线歌曲";
                } else {
                    message = "找到 " + musics.size() + " 首在线歌曲，点击列表即可播放";
                }
                return new OnlineResult(keyword, message, musics);
            } catch (Exception e) {
                return new OnlineResult("", "网络请求失败，请检查网络后重试\n\n" + e.getMessage(), new ArrayList<Music>());
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
        protected void onPostExecute(OnlineResult result) {
            if (result.keyword.length() == 0) {
                resultText.setText(result.message);
            } else {
                resultText.setText("关键词: " + result.keyword + "\n" + result.message);
            }
            onlineTracks.clear();
            onlineTracks.addAll(result.musics);
            onlineMusicAdapter.notifyDataSetChanged();
        }
    }

    private static class OnlineResult {
        private final String keyword;
        private final String message;
        private final List<Music> musics;

        OnlineResult(String keyword, String message, List<Music> musics) {
            this.keyword = keyword;
            this.message = message;
            this.musics = musics;
        }
    }
}
