package org.wangchenlong.scrolllistview.widgets;

import android.content.Context;
import android.content.res.Resources;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.LinearLayout;

import java.util.NoSuchElementException;

/**
 * 含有滚动头部的布局, 上部分是头部, 下部分是内容.
 * <p>
 * Created by wangchenlong on 16/10/25.
 */
public class HeaderLayout extends LinearLayout {
    // Debug输出Log信息
    private static final String TAG = "DEBUG-WCL: " + HeaderLayout.class.getSimpleName();

    // 头部的状态, 扩展与坍塌
    public enum Status {
        STATUS_EXPANDED, STATUS_COLLAPSED
    }

    private final static String LAYOUT_HEADER = "layout_header"; // 头部布局ID
    private final static String LAYOUT_CONTENT = "layout_content"; // 内容布局ID
    private final static String ID = "id"; // 资源ID

    private View mHeaderView; // 头部视图
    private View mContentView; // 内容视图

    // Header的高度, 单位: px
    private int mOriginalHeaderHeight; // 原始高度
    private int mHeaderHeight; // 当前高度

    private int mTouchSlop; // 触摸滑动间隔
    private boolean isHeaderShowSucceed = false; // 头视图是否显示, 默认未显示

    // 记录上次滑动的坐标(onTouchEvent)
    private int mLastX = 0;
    private int mLastY = 0;

    // 记录上次滑动的坐标(onInterceptTouchEvent)
    private int mLastXIntercept = 0;
    private int mLastYIntercept = 0;

    // 不允许头部触发事件
    private boolean mDisallowInterceptTouchEventOnHeader = true;

    // 当前状态, 扩展还是坍塌
    private Status mStatus = Status.STATUS_EXPANDED;

    // 放弃触摸事件的监听
    private OnGiveUpTouchEventListener mGiveUpTouchEventListener;

    private boolean mIsSticky = true; // 是否固定头部

    // 默认构造器
    public HeaderLayout(Context context) {
        super(context);
    }

    public HeaderLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public HeaderLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    // View在Visible时被调用
    @Override public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        // 当拥有焦点, 头部或内容为空时, 初始化视图
        if (hasWindowFocus && (mHeaderView == null || mContentView == null)) {
            initViews(); // 初始化视图
        }
    }

    /**
     * 初始化视图与参数, 使用固定ID, 头部:layout_header, 内容:layout_content
     */
    private void initViews() {
        Resources res = getResources();
        String pkgName = getContext().getPackageName();
        int headerId = res.getIdentifier(LAYOUT_HEADER, ID, pkgName);
        int contentId = res.getIdentifier(LAYOUT_CONTENT, ID, pkgName);

        // 存在标题与内容
        if (headerId != 0 && contentId != 0) {
            mHeaderView = findViewById(headerId);
            mContentView = findViewById(contentId);

            mOriginalHeaderHeight = mHeaderView.getMeasuredHeight();
            mHeaderHeight = mOriginalHeaderHeight;

            // 用户的滑动间隔
            mTouchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();

            // 高度为0, 则表示已经显示
            if (mHeaderHeight > 0) {
                isHeaderShowSucceed = true;
            }
        } else {
            // 当ID使用错误时, 抛出异常, 指明ID.
            throw new NoSuchElementException("布局ID错误, 确认ID: 头部\"layout_header\", 内容\"layout_content\".");
        }
    }

    /**
     * 拦截事件, 确认固动效果
     *
     * @param ev 事件
     * @return 是否拦截
     */
    @Override public boolean onInterceptTouchEvent(MotionEvent ev) {
        int intercepted = 0; // 非0截获, 0不截获
        int x = (int) ev.getX();
        int y = (int) ev.getY();

        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mLastXIntercept = x;
                mLastYIntercept = y;
                mLastX = x;
                mLastY = y;
                intercepted = 0;
                break;
            case MotionEvent.ACTION_MOVE:
                int deltaX = x - mLastXIntercept;
                int deltaY = y - mLastYIntercept;

                // 在Header内部触发事件
                if (mDisallowInterceptTouchEventOnHeader && y <= mHeaderHeight) {
                    intercepted = 0; // 头部里点击, 不截获
                } else if (Math.abs(deltaY) <= Math.abs(deltaX)) {
                    intercepted = 0; // 水平滑动, 不截获
                } else if (mStatus == Status.STATUS_EXPANDED && deltaY <= -mTouchSlop) {
                    // 扩展 并且 向上滑动, 截获
                    intercepted = 1;
                } else if (mGiveUpTouchEventListener != null) {
                    if (mGiveUpTouchEventListener.giveUpTouchEvent(ev) && deltaY >= mTouchSlop) {
                        // 向下滑动, 并且回调为true, 截获
                        intercepted = 1;
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
                intercepted = 0;
                mLastXIntercept = mLastYIntercept = 0; // 事件完成结束
                break;
            default:
                break;
        }

        Log.e(TAG, "是否拦截: " + (intercepted != 0 ? "是" : "否"));

        return intercepted != 0 && mIsSticky;
    }

    /**
     * 是否消费触发事件
     *
     * @param event 事件
     * @return 是否消费
     */
    @Override public boolean onTouchEvent(MotionEvent event) {
        // 未附着, 则消费
        if (!mIsSticky) {
            return true;
        }
        int x = (int) event.getX();
        int y = (int) event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                break;
            case MotionEvent.ACTION_MOVE:
                int deltaX = x - mLastX;
                int deltaY = y - mLastY;
                mHeaderHeight += deltaY;
                setHeaderHeight(mHeaderHeight); // 设置头部的高度
                break;
            case MotionEvent.ACTION_UP:
                int destHeight = 0;
                // 自动向上或向下滑动.
                if (mHeaderHeight <= mOriginalHeaderHeight * 0.5) {
                    destHeight = 0;
                    mStatus = Status.STATUS_COLLAPSED;
                } else {
                    destHeight = mOriginalHeaderHeight;
                    mStatus = Status.STATUS_EXPANDED;
                }
                // 缓慢滑动, 0.5s
                smoothSetHeaderHeight(mHeaderHeight, destHeight, 500);
                break;
            default:
                break;
        }

        mLastX = x;
        mLastY = y;

        return true;
    }

    /**
     * 平滑设置头部的高度
     *
     * @param from     来源
     * @param to       目标
     * @param duration 持续时间
     */
    public void smoothSetHeaderHeight(final int from, final int to, long duration) {
        smoothSetHeaderHeight(from, to, duration, false);
    }

    /**
     * 平滑设置头部的高度
     *
     * @param from     来源
     * @param to       目标
     * @param duration 持续时间
     * @param modify   是否修改
     */
    public void smoothSetHeaderHeight(final int from, final int to, long duration,
                                      final boolean modify) {
        final int frameCount = (int) (duration / 1000f * 30) + 1;  // 帧数
        final float partition = (to - from) / (float) frameCount; // 距离
        new Thread("Thread#smoothSetHeaderHeight") {
            @Override public void run() {
                for (int i = 0; i < frameCount; ++i) {
                    final int height;
                    if (i == frameCount - 1) {
                        height = to;
                    } else {
                        height = (int) (from + partition * i);
                    }
                    // 逐步设置高度
                    post(new Runnable() {
                        @Override public void run() {
                            setHeaderHeight(height);
                        }
                    });
                    try {
                        sleep(10); // 睡眠
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    // 设置修改原始高度
                    if (modify) {
                        setOriginalHeaderHeight(to);
                    }
                }
            }
        }.start();
    }

    // 放弃点击事件
    public interface OnGiveUpTouchEventListener {
        boolean giveUpTouchEvent(MotionEvent event);
    }

    /**
     * 设置修改参数
     *
     * @param height 高度
     * @param modify 修改原始高度
     */
    public void setHeaderHeight(int height, boolean modify) {
        if (modify) {
            setOriginalHeaderHeight(height);
        }
        setHeaderHeight(height);
    }

    /**
     * 设置头部的高度, 公开接口
     *
     * @param height 高度
     */
    public void setHeaderHeight(int height) {
        if (!isHeaderShowSucceed) {
            initViews(); // 初始化头部视图
        }

        // 界定高度在0至原始高度之间
        if (height <= 0) {
            height = 0;
        } else if (height > mOriginalHeaderHeight) {
            height = mOriginalHeaderHeight;
        }

        // 根据高度确定类型
        if (height == 0) {
            mStatus = Status.STATUS_COLLAPSED;
        } else {
            mStatus = Status.STATUS_EXPANDED;
        }

        if (mHeaderView != null && mHeaderView.getLayoutParams() != null) {
            mHeaderView.getLayoutParams().height = height; // 设置高度
            mHeaderView.requestLayout(); // 重绘
            mHeaderHeight = height; // 高度
        }
    }

    /**
     * 设置原始头部高度
     *
     * @param originalHeaderHeight 原始头部高度
     */
    public void setOriginalHeaderHeight(int originalHeaderHeight) {
        mOriginalHeaderHeight = originalHeaderHeight;
    }
}
