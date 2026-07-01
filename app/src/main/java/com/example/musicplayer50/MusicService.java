package com.example.musicplayer50;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.util.Log;
import android.widget.Toast;

import java.lang.ref.WeakReference;

/**
 * 后台音乐播放服务
 * - 使用 prepareAsync 避免主线程阻塞
 * - 设置 AudioAttributes 保障音质
 * - WakeLock 防止 CPU 休眠导致断流
 */
public class MusicService extends Service {

    private MediaPlayer mediaPlayer;
    private MyBinder myBinder;
    private PowerManager.WakeLock wakeLock;
    private boolean isPrepared;
    private boolean isPreparing;
    private String currentTitle = "";
    private String currentArtist = "";

    private static final int SET_SEEKBAR_MAX = 3;
    private static final int UPDATE_PROGRESS = 1;

    public class MyBinder extends Binder {
        public MusicService getService() {
            return MusicService.this;
        }
    }

    // 静态内部 Handler 避免内存泄漏
    private static class SafeHandler extends Handler {
        private final WeakReference<MusicService> ref;

        SafeHandler(MusicService service) {
            this.ref = new WeakReference<>(service);
        }

        @Override
        public void handleMessage(Message msg) {
            MusicService service = ref.get();
            if (service == null || service.mediaPlayer == null || !service.isPrepared) return;

            switch (msg.what) {
                case UPDATE_PROGRESS:
                    try {
                        Intent intent = new Intent("seekbarprogress");
                        intent.putExtra("seekbarprogress",
                                service.mediaPlayer.getCurrentPosition());
                        service.sendPlaybackBroadcast(intent);
                        if (service.mediaPlayer.isPlaying()) {
                            sendEmptyMessageDelayed(UPDATE_PROGRESS, 500);
                        }
                    } catch (IllegalStateException ignored) {
                    }
                    break;
                case SET_SEEKBAR_MAX:
                    try {
                        Intent intent = new Intent("seekbarmaxprogress");
                        intent.putExtra("seekbarmaxprogress",
                                service.mediaPlayer.getDuration());
                        service.sendPlaybackBroadcast(intent);
                    } catch (IllegalStateException ignored) {
                    }
                    break;
            }
        }
    }

    private SafeHandler handler;

    public MusicService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new SafeHandler(this);
        initMediaPlayer();
    }

    /**
     * 初始化 MediaPlayer：设置音频属性 + WakeLock
     */
    private void initMediaPlayer() {
        if (mediaPlayer != null) {
            mediaPlayer.release();
        }
        mediaPlayer = new MediaPlayer();
        isPrepared = false;
        isPreparing = false;

        // Android 5.0+ 推荐用 AudioAttributes，旧系统保留音乐流设置。
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build();
            mediaPlayer.setAudioAttributes(attrs);
        } else {
            mediaPlayer.setAudioStreamType(android.media.AudioManager.STREAM_MUSIC);
        }

        // 播放完成切下一首
        mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                Log.e("huizhong", "播放完成，广播 nextsong");
                releaseWakeLock();
                handler.removeMessages(UPDATE_PROGRESS);
                Intent intent = new Intent("nextsong");
                sendPlaybackBroadcast(intent);
            }
        });
        mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                Log.e("huizhong", "MediaPlayer error what=" + what + ", extra=" + extra);
                isPrepared = false;
                isPreparing = false;
                releaseWakeLock();
                handler.removeCallbacksAndMessages(null);
                sendPlaybackBroadcast("pauseimage");
                Toast.makeText(getApplicationContext(), "播放失败，请尝试下一首", Toast.LENGTH_SHORT).show();
                return true;
            }
        });
    }

    /**
     * 获取 WakeLock 防止 CPU 休眠
     */
    private void acquireWakeLock() {
        if (wakeLock == null) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                        "MusicPlayer::Wakelock");
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

    @Override
    public IBinder onBind(Intent intent) {
        myBinder = new MyBinder();
        return myBinder;
    }

    /**
     * 播放 / 暂停切换
     */
    public void start() {
        if (mediaPlayer == null || isPreparing) return;
        if (!isPrepared) {
            Toast.makeText(getApplicationContext(), "请先选择歌曲", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
                releaseWakeLock();
                handler.removeMessages(UPDATE_PROGRESS);
                sendPlaybackBroadcast("pauseimage");
            } else {
                mediaPlayer.start();
                acquireWakeLock();
                sendPlaybackBroadcast("playimage");
                handler.removeMessages(UPDATE_PROGRESS);
                handler.sendEmptyMessage(UPDATE_PROGRESS);
            }
        } catch (IllegalStateException e) {
            Log.e("huizhong", "start 状态异常: " + e.getMessage());
        }
    }

    public boolean isPlaying() {
        try {
            return mediaPlayer != null && isPrepared && mediaPlayer.isPlaying();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    public int getDuration() {
        try {
            return mediaPlayer != null && isPrepared ? mediaPlayer.getDuration() : 0;
        } catch (IllegalStateException e) {
            return 0;
        }
    }

    public int getCurrentPosition() {
        try {
            return mediaPlayer != null && isPrepared ? mediaPlayer.getCurrentPosition() : 0;
        } catch (IllegalStateException e) {
            return 0;
        }
    }

    public String getCurrentTitle() {
        return currentTitle;
    }

    public String getCurrentArtist() {
        return currentArtist;
    }

    /**
     * 切歌：用 prepareAsync 异步准备，避免主线程阻塞导致音频卡顿
     */
    public void startnew(String path) {
        if (path == null || path.trim().length() == 0) {
            Toast.makeText(getApplicationContext(), "歌曲路径无效", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            handler.removeCallbacksAndMessages(null);
            releaseWakeLock();
            initMediaPlayer();

            if (path.startsWith("content://")) {
                mediaPlayer.setDataSource(getApplicationContext(), Uri.parse(path));
            } else {
                mediaPlayer.setDataSource(path);
            }
            isPreparing = true;

            // 关键修复：prepareAsync 不阻塞主线程
            mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    isPreparing = false;
                    isPrepared = true;
                    mp.start();
                    acquireWakeLock();
                    sendPlaybackBroadcast("playimage");
                    handler.removeMessages(UPDATE_PROGRESS);
                    handler.sendEmptyMessage(SET_SEEKBAR_MAX);
                    handler.sendEmptyMessage(UPDATE_PROGRESS);
                }
            });
            mediaPlayer.prepareAsync();

        } catch (Exception e) {
            isPreparing = false;
            isPrepared = false;
            releaseWakeLock();
            sendPlaybackBroadcast("pauseimage");
            Log.e("huizhong", "startnew 异常: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;

        if ("startnew".equals(intent.getAction())) {
            String title = intent.getStringExtra("title");
            String url = intent.getStringExtra("url");
            String artist = intent.getStringExtra("artist");
            currentTitle = title == null ? "" : title;
            currentArtist = artist == null ? "" : artist;

            Toast.makeText(getApplicationContext(), title, Toast.LENGTH_SHORT).show();
            Log.e("huizhong", "startnew: " + title);

            startnew(url);

            // 通知主界面更新标题
            Intent titleIntent = new Intent("gettitle");
            titleIntent.putExtra("title", title);
            titleIntent.putExtra("url", url);
            titleIntent.putExtra("artist", artist);
            sendPlaybackBroadcast(titleIntent);

        } else if ("changed".equals(intent.getAction())) {
            if (mediaPlayer != null && isPrepared) {
                try {
                    mediaPlayer.seekTo(intent.getIntExtra("seekbarprogress", 0));
                } catch (IllegalStateException e) {
                    Log.e("huizhong", "seekTo 状态异常: " + e.getMessage());
                }
            }
        }

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        releaseWakeLock();
        handler.removeCallbacksAndMessages(null);

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

    private void sendPlaybackBroadcast(String action) {
        sendPlaybackBroadcast(new Intent(action));
    }

    private void sendPlaybackBroadcast(Intent intent) {
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
    }
}
