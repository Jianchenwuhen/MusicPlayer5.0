package com.example.musicplayer50;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
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
    private ListView onlineList;
    private TextView onlineStatus;
    private ProgressBar onlineProgress;

    private final List<Music> results = new ArrayList<>();
    private MusicAdapter adapter;
    private LoadOnlineMusicTask currentTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.online_music);

        UiUtils.setupEdgeToEdge(this);
        UiUtils.applySystemBarInsets(findViewById(R.id.contentRoot));

        searchKeyword = (EditText) findViewById(R.id.searchKeyword);
        onlineList = (ListView) findViewById(R.id.onlineList);
        onlineStatus = (TextView) findViewById(R.id.onlineStatus);
        onlineProgress = (ProgressBar) findViewById(R.id.onlineProgress);

        adapter = new MusicAdapter(this, R.layout.musicitem, results);
        onlineList.setAdapter(adapter);
        onlineList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                playPreview(position);
            }
        });

        Button searchButton = (Button) findViewById(R.id.searchOnlineMusic);
        searchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doSearch(searchKeyword.getText().toString().trim());
            }
        });

        Button loadButton = (Button) findViewById(R.id.loadOnlineMusic);
        loadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startLoad(null);
            }
        });

        // 软键盘“搜索”键
        searchKeyword.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    doSearch(searchKeyword.getText().toString().trim());
                    return true;
                }
                return false;
            }
        });
    }

    private void doSearch(String keyword) {
        if (keyword.length() == 0) {
            showStatus("请输入搜索关键词");
            return;
        }
        startLoad(keyword);
    }

    /**
     * keyword 为 null 表示随机推荐
     */
    private void startLoad(String keyword) {
        if (currentTask != null) {
            currentTask.cancel(true);
        }
        showLoading();
        currentTask = new LoadOnlineMusicTask();
        if (keyword == null) {
            currentTask.execute();
        } else {
            currentTask.execute(keyword);
        }
    }

    private void playPreview(int position) {
        if (position < 0 || position >= results.size()) {
            return;
        }
        Music music = results.get(position);
        Intent intent = new Intent(this, MusicService.class);
        intent.setAction("startnew");
        intent.putExtra("url", music.getUrl());
        intent.putExtra("title", music.getTitle());
        intent.putExtra("artist", music.getArtist());
        startService(intent);
        Toast.makeText(this, "正在播放预览：" + music.getTitle(), Toast.LENGTH_SHORT).show();
    }

    // ==================== 三态切换 ====================

    private void showLoading() {
        onlineProgress.setVisibility(View.VISIBLE);
        onlineList.setVisibility(View.GONE);
        onlineStatus.setVisibility(View.GONE);
    }

    private void showStatus(String text) {
        onlineStatus.setText(text);
        onlineStatus.setVisibility(View.VISIBLE);
        onlineList.setVisibility(View.GONE);
        onlineProgress.setVisibility(View.GONE);
    }

    private void showResults() {
        onlineList.setVisibility(View.VISIBLE);
        onlineStatus.setVisibility(View.GONE);
        onlineProgress.setVisibility(View.GONE);
    }

    // ==================== 网络请求 ====================

    private static class SearchResult {
        List<Music> tracks = new ArrayList<>();
        String keyword = "";
        String error;
    }

    private class LoadOnlineMusicTask extends AsyncTask<String, Void, SearchResult> {
        @Override
        protected SearchResult doInBackground(String... keywords) {
            SearchResult result = new SearchResult();
            HttpURLConnection connection = null;
            BufferedReader reader = null;

            try {
                String keyword;
                if (keywords.length > 0 && keywords[0] != null && keywords[0].trim().length() > 0) {
                    keyword = keywords[0].trim();
                } else {
                    keyword = SEARCH_WORDS[new Random().nextInt(SEARCH_WORDS.length)];
                }
                result.keyword = keyword;

                String encodedKeyword = URLEncoder.encode(keyword, "UTF-8");
                URL url = new URL("https://itunes.apple.com/search?term=" + encodedKeyword + "&media=music&limit=20");
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
                JSONArray items = jsonObject.getJSONArray("results");
                for (int i = 0; i < items.length(); i++) {
                    JSONObject item = items.getJSONObject(i);
                    String preview = item.optString("previewUrl", "");
                    if (preview.length() == 0) {
                        continue; // 没有可播放预览的条目跳过
                    }
                    Music music = new Music();
                    music.setTitle(item.optString("trackName", "未知歌曲"));
                    music.setArtist(item.optString("artistName", "未知歌手"));
                    music.setUrl(preview);
                    result.tracks.add(music);
                }
                return result;
            } catch (Exception e) {
                result.error = "网络请求失败，请检查手机网络后重试";
                return result;
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
        protected void onPostExecute(SearchResult result) {
            if (result.error != null) {
                showStatus(result.error);
                return;
            }
            if (result.tracks.isEmpty()) {
                showStatus("没有搜索到 “" + result.keyword + "” 相关歌曲");
                return;
            }
            results.clear();
            results.addAll(result.tracks);
            adapter.notifyDataSetChanged();
            showResults();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (currentTask != null) {
            currentTask.cancel(true);
        }
    }
}
