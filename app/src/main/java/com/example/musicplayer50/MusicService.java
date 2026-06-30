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
import android.database.Cursor;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.util.Log;
import android.widget.Toast;

import java.lang.ref.WeakReference;

/**
 * 后台音乐播放服务（前台 Service + 通知栏媒体控制）
 */
public class MusicService extends Service {

    private static final String CHANNEL_ID = "music_playback";
    private static final int NOTIFICATION_ID = 1;

    // 通知栏按钮 action
    private static final String ACTION_NOTIFY_PLAY = "com.example.musicplayer50.NOTIFY_PLAY";
    private static final String ACTION_NOTIFY_NEXT = "com.example.musicplayer50.NOTIFY_NEXT";
    private static final String ACTION_NOTIFY_PREV = "com.example.musicplayer50.NOTIFY_PREV";

    private MediaPlayer mediaPlayer;
    private MyBinder myBinder;
    private PowerManager.WakeLock wakeLock;

    // 静态变量：供其他 Activity 查询当前播放状态
    public static String currentTitle = "";
    public static String currentArtist = "";
    public static String currentUrl = "";
    public static boolean isPlaying = false;

    private static final int UPDATE_PROGRESS = 1;
    private static final int SET_SEEKBAR_MAX = 3;

    public class MyBinder extends Binder {
        public MusicService getService() {
            return MusicService.this;
        }
    }

    private static class SafeHandler extends Handler {
        private final WeakReference<MusicService> ref;
        SafeHandler(MusicService service) { this.ref = new WeakReference<>(service); }

        @Override
        public void handleMessage(Message msg) {
            MusicService service = ref.get();
            if (service == null || service.mediaPlayer == null) return;
            switch (msg.what) {
                case UPDATE_PROGRESS:
                    Intent intent = new Intent("seekbarprogress");
                    intent.putExtra("seekbarprogress", service.mediaPlayer.getCurrentPosition());
                    service.sendBroadcast(intent);
                    sendEmptyMessageDelayed(UPDATE_PROGRESS, 500);
                    break;
                case SET_SEEKBAR_MAX:
                    intent = new Intent("seekbarmaxprogress");
                    intent.putExtra("seekbarmaxprogress", service.mediaPlayer.getDuration());
                    service.sendBroadcast(intent);
                    break;
            }
        }
    }

    private SafeHandler handler;

    // 接收通知栏按钮点击
    private BroadcastReceiver notifyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_NOTIFY_PLAY.equals(action)) {
                start(); // 播放/暂停
            } else if (ACTION_NOTIFY_NEXT.equals(action)) {
                playAdjacent(+1);
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
        registerReceiver(notifyReceiver, new IntentFilter(ACTION_NOTIFY_PLAY));
        registerReceiver(notifyReceiver, new IntentFilter(ACTION_NOTIFY_NEXT));
        registerReceiver(notifyReceiver, new IntentFilter(ACTION_NOTIFY_PREV));
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "音乐播放", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("音乐播放控制");
            channel.setShowBadge(false);
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private void initMediaPlayer() {
        if (mediaPlayer != null) mediaPlayer.release();
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setAudioStreamType(android.media.AudioManager.STREAM_MUSIC);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA).build());
        }
        mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                Log.e("huizhong", "播放完成");
                playAdjacent(+1);
            }
        });
    }

    private void acquireWakeLock() {
        if (wakeLock == null) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MusicPlayer::Wakelock");
                wakeLock.setReferenceCounted(false);
            }
        }
        if (wakeLock != null && !wakeLock.isHeld()) wakeLock.acquire();
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
    }

    @Override
    public IBinder onBind(Intent intent) {
        myBinder = new MyBinder();
        return myBinder;
    }

    public void start() {
        if (mediaPlayer == null) return;
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
        updateNotification();
    }

    public void startnew(String path) {
        try {
            if (mediaPlayer != null) {
                if (mediaPlayer.isPlaying()) mediaPlayer.stop();
                mediaPlayer.reset();
            } else {
                initMediaPlayer();
            }
            mediaPlayer.setDataSource(path);
            mediaPlayer.prepareAsync();
            mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    mp.start();
                    acquireWakeLock();
                    isPlaying = true;
                    sendBroadcast(new Intent("playimage"));
                    handler.sendEmptyMessage(SET_SEEKBAR_MAX);
                    handler.sendEmptyMessage(UPDATE_PROGRESS);
                    updateNotification();
                }
            });
        } catch (Exception e) {
            Log.e("huizhong", "startnew 异常: " + e.getMessage());
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;

        if ("startnew".equals(intent.getAction())) {
            currentTitle = intent.getStringExtra("title");
            currentArtist = intent.getStringExtra("artist");
            currentUrl = intent.getStringExtra("url");
            if (currentTitle == null) currentTitle = "";
            if (currentArtist == null) currentArtist = "";
            if (currentUrl == null) currentUrl = "";

            Toast.makeText(getApplicationContext(), currentTitle, Toast.LENGTH_SHORT).show();

            // 先展示通知（满足 Android 8.0+ 前台 Service 5秒限制）
            isPlaying = false;
            startForeground(NOTIFICATION_ID, buildNotification());

            startnew(currentUrl);

            Intent titleIntent = new Intent("gettitle");
            titleIntent.putExtra("title", currentTitle);
            titleIntent.putExtra("url", currentUrl);
            titleIntent.putExtra("artist", currentArtist);
            sendBroadcast(titleIntent);

        } else if ("changed".equals(intent.getAction())) {
            if (mediaPlayer != null) {
                mediaPlayer.seekTo(intent.getIntExtra("seekbarprogress", 0));
            }
        }

        return START_NOT_STICKY;
    }

    // ==================== 通知栏 ====================

    /**
     * 构建/刷新通知，播放时显示为前台 Service
     */
    private void updateNotification() {
        if (currentTitle.isEmpty()) return;
        startForeground(NOTIFICATION_ID, buildNotification());
    }

    private Notification buildNotification() {
        // 点击通知打开主界面
        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentPI = PendingIntent.getActivity(this, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));

        // 播放/暂停按钮
        Intent playIntent = new Intent(ACTION_NOTIFY_PLAY);
        PendingIntent playPI = PendingIntent.getBroadcast(this, 0, playIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));

        // 下一首按钮
        Intent nextIntent = new Intent(ACTION_NOTIFY_NEXT);
        PendingIntent nextPI = PendingIntent.getBroadcast(this, 1, nextIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));

        // 上一首按钮
        Intent prevIntent = new Intent(ACTION_NOTIFY_PREV);
        PendingIntent prevPI = PendingIntent.getBroadcast(this, 2, prevIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));

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

    // ==================== 上下首（从 Provider 查播放列表） ====================

    private void playAdjacent(int direction) {
        Cursor cursor = getContentResolver().query(
                PlaylistContract.CONTENT_URI, null, null, null, null);
        if (cursor == null || cursor.getCount() == 0) {
            if (cursor != null) cursor.close();
            // 播放列表为空，发广播让 Activity 处理
            sendBroadcast(new Intent("nextsong"));
            return;
        }

        int targetIndex = -1;
        int count = cursor.getCount();
        for (int i = 0; i < count; i++) {
            cursor.moveToPosition(i);
            String t = cursor.getString(cursor.getColumnIndexOrThrow("title"));
            if (currentTitle.equals(t)) { targetIndex = i + direction; break; }
        }

        if (targetIndex < 0) targetIndex = count - 1;
        if (targetIndex >= count) targetIndex = 0;

        cursor.moveToPosition(targetIndex);
        String url = cursor.getString(cursor.getColumnIndexOrThrow("url"));
        String title = cursor.getString(cursor.getColumnIndexOrThrow("title"));
        String artist = cursor.getString(cursor.getColumnIndexOrThrow("artist"));
        cursor.close();

        currentTitle = title;
        currentArtist = artist;
        currentUrl = url;
        startnew(url);

        Intent titleIntent = new Intent("gettitle");
        titleIntent.putExtra("title", title);
        titleIntent.putExtra("url", url);
        titleIntent.putExtra("artist", artist);
        sendBroadcast(titleIntent);
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(notifyReceiver);
        releaseWakeLock();
        handler.removeCallbacksAndMessages(null);
        stopForeground(true);
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
        super.onDestroy();
    }
}