package com.example.musicplayer50;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.util.Log;
import android.widget.Toast;

import java.lang.ref.WeakReference;

public class MusicService extends Service {

    private static final String TAG = "MusicService";
    private static final String CHANNEL_ID = "music_playback";
    private static final int NOTIFICATION_ID = 1;
    private static final String PREFS_NAME = "playback_state";
    private static final String KEY_TITLE = "title";
    private static final String KEY_ARTIST = "artist";
    private static final String KEY_URL = "url";
    private static final String KEY_POSITION = "position";
    private static final String KEY_IS_PLAYING = "is_playing";

    private static final String ACTION_NOTIFY_PLAY = "com.example.musicplayer50.NOTIFY_PLAY";
    private static final String ACTION_NOTIFY_NEXT = "com.example.musicplayer50.NOTIFY_NEXT";
    private static final String ACTION_NOTIFY_PREV = "com.example.musicplayer50.NOTIFY_PREV";
    private static final String ACTION_PLAYBACK_ERROR = "playbackerror";
    private static final String ACTION_PLAYLIST_EMPTY = "playlistempty";

    private static final int UPDATE_PROGRESS = 1;
    private static final int SET_SEEKBAR_MAX = 3;

    private MediaPlayer mediaPlayer;
    private MyBinder myBinder;
    private PowerManager.WakeLock wakeLock;
    private SafeHandler handler;

    public static String currentTitle = "";
    public static String currentArtist = "";
    public static String currentUrl = "";
    public static boolean isPlaying = false;

    public class MyBinder extends Binder {
        public MusicService getService() {
            return MusicService.this;
        }
    }

    private static class SafeHandler extends Handler {
        private final WeakReference<MusicService> ref;

        SafeHandler(MusicService service) {
            this.ref = new WeakReference<>(service);
        }

        @Override
        public void handleMessage(Message msg) {
            MusicService service = ref.get();
            if (service == null || service.mediaPlayer == null) {
                return;
            }

            switch (msg.what) {
                case UPDATE_PROGRESS:
                    Intent progressIntent = new Intent("seekbarprogress");
                    progressIntent.putExtra("seekbarprogress", service.getSafeCurrentPosition());
                    service.sendBroadcast(progressIntent);
                    sendEmptyMessageDelayed(UPDATE_PROGRESS, 500);
                    break;
                case SET_SEEKBAR_MAX:
                    Intent maxIntent = new Intent("seekbarmaxprogress");
                    maxIntent.putExtra("seekbarmaxprogress", service.getSafeDuration());
                    service.sendBroadcast(maxIntent);
                    break;
                default:
                    break;
            }
        }
    }

    private final BroadcastReceiver notifyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) {
                return;
            }

            String action = intent.getAction();
            if (ACTION_NOTIFY_PLAY.equals(action)) {
                start();
            } else if (ACTION_NOTIFY_NEXT.equals(action)) {
                playAdjacent(1);
            } else if (ACTION_NOTIFY_PREV.equals(action)) {
                playAdjacent(-1);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new SafeHandler(this);
        initMediaPlayer();
        createNotificationChannel();
        // Android 13+ 注册动态广播必须显式声明导出与否；应用内部广播 → NOT_EXPORTED
        IntentFilter notifyFilter = new IntentFilter();
        notifyFilter.addAction(ACTION_NOTIFY_PLAY);
        notifyFilter.addAction(ACTION_NOTIFY_NEXT);
        notifyFilter.addAction(ACTION_NOTIFY_PREV);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(notifyReceiver, notifyFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(notifyReceiver, notifyFilter);
        }
        restorePlaybackState();
    }

    @Override
    public IBinder onBind(Intent intent) {
        myBinder = new MyBinder();
        return myBinder;
    }

    public void start() {
        if (mediaPlayer == null || currentUrl.trim().length() == 0) {
            notifyPlaybackError("请先选择一首歌曲");
            return;
        }

        try {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
                releaseWakeLock();
                isPlaying = false;
                sendBroadcast(new Intent("pauseimage"));
            } else {
                mediaPlayer.start();
                acquireWakeLock();
                isPlaying = true;
                sendBroadcast(new Intent("playimage"));
                handler.sendEmptyMessage(UPDATE_PROGRESS);
            }
            savePlaybackState();
            updateNotification();
        } catch (IllegalStateException e) {
            Log.e(TAG, "toggle playback failed", e);
            notifyPlaybackError("当前播放状态异常，请重新选择歌曲");
        }
    }

    public void playNextTrack() {
        playAdjacent(1);
    }

    public void playPreviousTrack() {
        playAdjacent(-1);
    }

    public void restoreIfAvailable() {
        restorePlaybackState();
        if (currentUrl == null || currentUrl.trim().length() == 0) {
            return;
        }

        final int restorePosition = getPersistedPosition();
        final boolean shouldResumePlayback = isPlaying;

        try {
            resetPlayerForNewSource();
            mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    if (restorePosition > 0) {
                        mp.seekTo(restorePosition);
                    }

                    if (shouldResumePlayback) {
                        mp.start();
                        acquireWakeLock();
                        isPlaying = true;
                        sendBroadcast(new Intent("playimage"));
                        handler.sendEmptyMessage(UPDATE_PROGRESS);
                    } else {
                        isPlaying = false;
                        sendBroadcast(new Intent("pauseimage"));
                    }

                    handler.sendEmptyMessage(SET_SEEKBAR_MAX);
                    broadcastTrackMetadata();
                    broadcastProgress(restorePosition);
                    savePlaybackState();
                    updateNotification();
                }
            });
            // 关键修复：content:// 本地 URI 用带 Context 的重载
            if (currentUrl.startsWith("content://")) {
                mediaPlayer.setDataSource(getApplicationContext(), Uri.parse(currentUrl));
            } else {
                mediaPlayer.setDataSource(currentUrl);
            }
            mediaPlayer.prepareAsync();
        } catch (Exception e) {
            Log.e(TAG, "restore playback failed", e);
            notifyPlaybackError("无法恢复上次播放的歌曲");
        }
    }

    public void startnew(String path) {
        try {
            resetPlayerForNewSource();
            // 先设监听再 prepareAsync，避免时序竞态
            mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    mp.start();
                    acquireWakeLock();
                    isPlaying = true;
                    sendBroadcast(new Intent("playimage"));
                    handler.sendEmptyMessage(SET_SEEKBAR_MAX);
                    handler.sendEmptyMessage(UPDATE_PROGRESS);
                    savePlaybackState();
                    updateNotification();
                }
            });
            // 关键修复：本地 content:// URI 必须用带 Context 的重载，否则 MediaPlayer 报 error -38
            if (path != null && path.startsWith("content://")) {
                mediaPlayer.setDataSource(getApplicationContext(), Uri.parse(path));
            } else {
                mediaPlayer.setDataSource(path);
            }
            mediaPlayer.prepareAsync();
        } catch (Exception e) {
            Log.e(TAG, "start new track failed", e);
            notifyPlaybackError("当前歌曲无法播放");
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            return START_NOT_STICKY;
        }

        if ("startnew".equals(intent.getAction())) {
            currentTitle = safeString(intent.getStringExtra("title"));
            currentArtist = safeString(intent.getStringExtra("artist"));
            currentUrl = safeString(intent.getStringExtra("url"));

            Toast.makeText(getApplicationContext(), currentTitle, Toast.LENGTH_SHORT).show();

            isPlaying = false;
            startForeground(NOTIFICATION_ID, buildNotification());

            startnew(currentUrl);
            broadcastTrackMetadata();
            savePlaybackState();
        } else if ("changed".equals(intent.getAction())) {
            if (mediaPlayer != null) {
                try {
                    mediaPlayer.seekTo(intent.getIntExtra("seekbarprogress", 0));
                    savePlaybackState();
                } catch (IllegalStateException e) {
                    Log.e(TAG, "seek failed", e);
                    notifyPlaybackError("拖动进度失败，请重试");
                }
            }
        }

        return START_NOT_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "音乐播放",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("音乐播放控制");
            channel.setShowBadge(false);
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void initMediaPlayer() {
        if (mediaPlayer != null) {
            mediaPlayer.release();
        }

        mediaPlayer = new MediaPlayer();
        mediaPlayer.setAudioStreamType(android.media.AudioManager.STREAM_MUSIC);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build());
        }

        mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                Log.d(TAG, "playback completed");
                playAdjacent(1);
            }
        });

        mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                Log.e(TAG, "playback error what=" + what + ", extra=" + extra);
                stopPlaybackState();
                notifyPlaybackError("播放失败，请尝试切换其他歌曲");
                return true;
            }
        });
    }

    private void resetPlayerForNewSource() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
            } catch (IllegalStateException ignored) {
            }
            mediaPlayer.reset();
        } else {
            initMediaPlayer();
        }
    }

    private void acquireWakeLock() {
        if (wakeLock == null) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MusicPlayer::Wakelock");
                wakeLock.setReferenceCounted(false);
            }
        }
        if (wakeLock != null && !wakeLock.isHeld()) {
            wakeLock.acquire();
        }
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    private void updateNotification() {
        if (currentTitle.isEmpty()) {
            return;
        }
        startForeground(NOTIFICATION_ID, buildNotification());
    }

    private Notification buildNotification() {
        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentPI = PendingIntent.getActivity(
                this,
                0,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        Intent playIntent = new Intent(ACTION_NOTIFY_PLAY);
        PendingIntent playPI = PendingIntent.getBroadcast(
                this,
                0,
                playIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        Intent nextIntent = new Intent(ACTION_NOTIFY_NEXT);
        PendingIntent nextPI = PendingIntent.getBroadcast(
                this,
                1,
                nextIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        Intent prevIntent = new Intent(ACTION_NOTIFY_PREV);
        PendingIntent prevPI = PendingIntent.getBroadcast(
                this,
                2,
                prevIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        int playIcon = isPlaying ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play;

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        builder.setContentTitle(currentTitle)
                .setContentText(currentArtist)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentIntent(contentPI)
                .setOngoing(isPlaying)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .addAction(android.R.drawable.ic_media_previous, "上一首", prevPI)
                .addAction(playIcon, "播放/暂停", playPI)
                .addAction(android.R.drawable.ic_media_next, "下一首", nextPI);

        return builder.build();
    }

    private void playAdjacent(int direction) {
        Cursor cursor = getContentResolver().query(
                PlaylistContract.CONTENT_URI,
                null,
                null,
                null,
                null
        );

        if (cursor == null || cursor.getCount() == 0) {
            if (cursor != null) {
                cursor.close();
            }
            stopPlaybackState();
            notifyPlaylistEmpty();
            return;
        }

        int targetIndex = -1;
        int count = cursor.getCount();
        for (int i = 0; i < count; i++) {
            cursor.moveToPosition(i);
            String rowUrl = cursor.getString(cursor.getColumnIndexOrThrow(PlaylistContract.COLUMN_URL));
            if (currentUrl.equals(rowUrl)) {
                targetIndex = i + direction;
                break;
            }
        }

        if (targetIndex < 0) {
            targetIndex = direction >= 0 ? 0 : count - 1;
        }
        if (targetIndex >= count) {
            targetIndex = 0;
        }

        cursor.moveToPosition(targetIndex);
        currentUrl = cursor.getString(cursor.getColumnIndexOrThrow(PlaylistContract.COLUMN_URL));
        currentTitle = cursor.getString(cursor.getColumnIndexOrThrow(PlaylistContract.COLUMN_TITLE));
        currentArtist = cursor.getString(cursor.getColumnIndexOrThrow(PlaylistContract.COLUMN_ARTIST));
        cursor.close();

        startnew(currentUrl);
        broadcastTrackMetadata();
        savePlaybackState();
    }

    private void stopPlaybackState() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.reset();
            } catch (IllegalStateException ignored) {
            }
        }

        releaseWakeLock();
        handler.removeMessages(UPDATE_PROGRESS);
        isPlaying = false;
        sendBroadcast(new Intent("pauseimage"));
        broadcastProgress(0);
        savePlaybackState();
        updateNotification();
    }

    private void broadcastTrackMetadata() {
        Intent titleIntent = new Intent("gettitle");
        titleIntent.putExtra("title", currentTitle);
        titleIntent.putExtra("url", currentUrl);
        titleIntent.putExtra("artist", currentArtist);
        sendBroadcast(titleIntent);
    }

    private void broadcastProgress(int progress) {
        Intent progressIntent = new Intent("seekbarprogress");
        progressIntent.putExtra("seekbarprogress", progress);
        sendBroadcast(progressIntent);
    }

    private void notifyPlaybackError(String message) {
        Intent errorIntent = new Intent(ACTION_PLAYBACK_ERROR);
        errorIntent.putExtra("message", safeString(message));
        sendBroadcast(errorIntent);
    }

    private void notifyPlaylistEmpty() {
        sendBroadcast(new Intent(ACTION_PLAYLIST_EMPTY));
    }

    private void savePlaybackState() {
        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(KEY_TITLE, currentTitle);
        editor.putString(KEY_ARTIST, currentArtist);
        editor.putString(KEY_URL, currentUrl);
        editor.putBoolean(KEY_IS_PLAYING, isPlaying);
        editor.putInt(KEY_POSITION, getSafeCurrentPosition());
        editor.apply();
    }

    private void restorePlaybackState() {
        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        currentTitle = preferences.getString(KEY_TITLE, "");
        currentArtist = preferences.getString(KEY_ARTIST, "");
        currentUrl = preferences.getString(KEY_URL, "");
        isPlaying = preferences.getBoolean(KEY_IS_PLAYING, false);
    }

    private int getPersistedPosition() {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getInt(KEY_POSITION, 0);
    }

    // ==================== 供前端(MainActivity)读取状态：兼容原 UI 接口 ====================
    public String getCurrentTitle() {
        return currentTitle;
    }

    public String getCurrentArtist() {
        return currentArtist;
    }

    public int getDuration() {
        return getSafeDuration();
    }

    public int getCurrentPosition() {
        return getSafeCurrentPosition();
    }

    public boolean isPlaying() {
        try {
            return mediaPlayer != null && mediaPlayer.isPlaying();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    private int getSafeCurrentPosition() {
        if (mediaPlayer == null) {
            return 0;
        }
        try {
            return mediaPlayer.getCurrentPosition();
        } catch (IllegalStateException ignored) {
            return 0;
        }
    }

    private int getSafeDuration() {
        if (mediaPlayer == null) {
            return 0;
        }
        try {
            return mediaPlayer.getDuration();
        } catch (IllegalStateException ignored) {
            return 0;
        }
    }

    private String safeString(String value) {
        return value == null ? "" : value;
    }

    @Override
    public void onDestroy() {
        savePlaybackState();
        unregisterReceiver(notifyReceiver);
        releaseWakeLock();
        handler.removeCallbacksAndMessages(null);
        stopForeground(true);
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
            } catch (IllegalStateException ignored) {
            }
            mediaPlayer.release();
            mediaPlayer = null;
        }
        super.onDestroy();
    }
}
