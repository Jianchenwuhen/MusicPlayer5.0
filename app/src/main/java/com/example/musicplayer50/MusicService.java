package com.example.musicplayer50;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
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
            if (service == null || service.mediaPlayer == null) return;

            switch (msg.what) {
                case UPDATE_PROGRESS:
                    Intent intent = new Intent("seekbarprogress");
                    intent.putExtra("seekbarprogress",
                            service.mediaPlayer.getCurrentPosition());
                    service.sendBroadcast(intent);
                    sendEmptyMessageDelayed(UPDATE_PROGRESS, 500);
                    break;
                case SET_SEEKBAR_MAX:
                    intent = new Intent("seekbarmaxprogress");
                    intent.putExtra("seekbarmaxprogress",
                            service.mediaPlayer.getDuration());
                    service.sendBroadcast(intent);
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

        // 设置为音乐流，系统会按音乐场景优化音频路由和 EQ
        mediaPlayer.setAudioStreamType(android.media.AudioManager.STREAM_MUSIC);

        // Android 8.0+ 推荐用 AudioAttributes
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build();
            mediaPlayer.setAudioAttributes(attrs);
        }

        // 播放完成切下一首
        mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                Log.e("huizhong", "播放完成，广播 nextsong");
                Intent intent = new Intent("nextsong");
                sendBroadcast(intent);
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
        if (mediaPlayer == null) return;

        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            releaseWakeLock();
            sendBroadcast(new Intent("pauseimage"));
        } else {
            mediaPlayer.start();
            acquireWakeLock();
            sendBroadcast(new Intent("playimage"));
            handler.sendEmptyMessage(UPDATE_PROGRESS);
        }
    }

    /**
     * 切歌：用 prepareAsync 异步准备，避免主线程阻塞导致音频卡顿
     */
    public void startnew(String path) {
        try {
            // 停止旧播放器并用 reset() 复用（比 release+new 高效）
            if (mediaPlayer != null) {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.reset();
            } else {
                initMediaPlayer();
            }

            mediaPlayer.setDataSource(path);

            // 关键修复：prepareAsync 不阻塞主线程
            mediaPlayer.prepareAsync();
            mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    mp.start();
                    acquireWakeLock();
                    sendBroadcast(new Intent("playimage"));
                    handler.sendEmptyMessage(SET_SEEKBAR_MAX);
                    handler.sendEmptyMessage(UPDATE_PROGRESS);
                }
            });

        } catch (Exception e) {
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

            Toast.makeText(getApplicationContext(), title, Toast.LENGTH_SHORT).show();
            Log.e("huizhong", "startnew: " + title);

            startnew(url);

            // 通知主界面更新标题
            Intent titleIntent = new Intent("gettitle");
            titleIntent.putExtra("title", title);
            titleIntent.putExtra("url", url);
            titleIntent.putExtra("artist", artist);
            sendBroadcast(titleIntent);

        } else if ("changed".equals(intent.getAction())) {
            if (mediaPlayer != null) {
                mediaPlayer.seekTo(intent.getIntExtra("seekbarprogress", 0));
            }
        }

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        releaseWakeLock();
        handler.removeCallbacksAndMessages(null);

        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }
            mediaPlayer.release();
            mediaPlayer = null;
        }

        super.onDestroy();
    }
}