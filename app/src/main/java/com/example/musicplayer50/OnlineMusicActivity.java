package com.example.musicplayer50;

import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Random;

public class OnlineMusicActivity extends AppCompatActivity {
    private static final String[] SEARCH_WORDS = {
            "pop", "rock", "jazz", "piano", "guitar",
            "dance", "love", "summer", "classic", "hip hop"
    };

    private TextView resultText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.online_music);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        resultText = (TextView) findViewById(R.id.onlineResult);
        Button loadButton = (Button) findViewById(R.id.loadOnlineMusic);
        loadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                resultText.setText("正在获取在线推荐...");
                new LoadOnlineMusicTask().execute();
            }
        });
    }

    private class LoadOnlineMusicTask extends AsyncTask<Void, Void, String> {
        @Override
        protected String doInBackground(Void... voids) {
            HttpURLConnection connection = null;
            BufferedReader reader = null;

            try {
                String keyword = SEARCH_WORDS[new Random().nextInt(SEARCH_WORDS.length)];
                String encodedKeyword = URLEncoder.encode(keyword, "UTF-8");
                URL url = new URL("https://itunes.apple.com/search?term=" + encodedKeyword + "&media=music&limit=5");
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
                StringBuilder recommendBuilder = new StringBuilder();
                recommendBuilder.append("在线推荐歌曲：").append(keyword).append("\n\n");

                for (int i = 0; i < results.length(); i++) {
                    JSONObject item = results.getJSONObject(i);
                    recommendBuilder
                            .append(i + 1)
                            .append(". ")
                            .append(item.optString("trackName", "未知歌曲"))
                            .append(" - ")
                            .append(item.optString("artistName", "未知歌手"))
                            .append("\n");
                }

                return recommendBuilder.toString();
            } catch (Exception e) {
                return "网络请求失败，请检查手机网络后重试。\n\n" + e.getMessage();
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
        protected void onPostExecute(String result) {
            resultText.setText(result);
        }
    }
}
