package com.example.musicplayer50;

import android.app.Activity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;

/**
 * UI 工具：edge-to-edge 沉浸式系统栏处理。
 * 纯平台 API 实现（minSdk 21 起支持），不依赖 AndroidX / Material。
 */
public final class UiUtils {

    private UiUtils() {
    }

    /**
     * 让内容延伸到状态栏/导航栏之后（配合主题里透明的 statusBarColor/navigationBarColor）。
     * 应在 setContentView 之后调用。取代旧式的 FLAG_FULLSCREEN（那会连状态栏一起隐藏）。
     */
    public static void setupEdgeToEdge(Activity activity) {
        if (activity == null) {
            return;
        }
        Window window = activity.getWindow();
        // 清掉可能残留的旧式全屏标记
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        View decor = window.getDecorView();
        decor.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    /**
     * 给内容容器叠加系统栏高度的内边距，避免内容被状态栏/导航栏遮挡。
     * 在布局原有 padding 基础上叠加，因此布局照常写 screen_padding。
     */
    public static void applySystemBarInsets(final View content) {
        if (content == null) {
            return;
        }
        final int left = content.getPaddingLeft();
        final int top = content.getPaddingTop();
        final int right = content.getPaddingRight();
        final int bottom = content.getPaddingBottom();
        content.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                v.setPadding(
                        left,
                        top + insets.getSystemWindowInsetTop(),
                        right,
                        bottom + insets.getSystemWindowInsetBottom());
                return insets;
            }
        });
        content.requestApplyInsets();
    }
}
