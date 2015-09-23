package com.kal.library;

import android.content.Context;
import android.os.AsyncTask;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewConfiguration;
import android.widget.LinearLayout;
import android.widget.ListView;

/**
 * 滑动到顶部后，再次滑动，显示搜索按钮
 * 
 * @author kal on 2015-09-18
 * 
 */
public class FloatingHeaderListView extends LinearLayout implements OnTouchListener {

	/**
	 * 下拉状态
	 */
	public static final int STATUS_SHOW_HEADER = 0;

	/**
	 * 释放立即刷新状态
	 */
	public static final int STATUS_HIDE_HEADER = 1;

	/**
	 * 当前处理什么状态，可选值有STATUS_SHOW_HEADER, STATUS_HIDE_HEADER,
	 */
	private int currentStatus = STATUS_HIDE_HEADER;

	/**
	 * 当前是否正在动画
	 */
	private boolean isAnimating = false;

	/**
	 * 下拉头部回滚的速度
	 */
	public static final int SCROLL_SPEED = 2;

	/**
	 * 下拉头的View
	 */
	private View header;

	/**
	 * 需要去下拉刷新的ListView
	 */
	private ListView listView;

	/**
	 * 下拉头的布局参数
	 */
	private MarginLayoutParams headerLayoutParams;

	/**
	 * 下拉头的高度
	 */
	private int hideHeaderHeight;

	/**
	 * 手指按下时的屏幕纵坐标
	 */
	private float yDown;

	/**
	 * 在被判定为滚动之前用户手指可以移动的最大值。
	 */
	private int touchSlop;

	/**
	 * 是否已加载过一次layout，这里onLayout中的初始化只需加载一次
	 */
	private boolean loadOnce;

	/**
	 * 下拉刷新控件的构造函数，会在运行时动态添加一个下拉头的布局。
	 * 
	 * @param context
	 * @param attrs
	 */
	public FloatingHeaderListView(Context context, AttributeSet attrs) {
		super(context, attrs);
		header = LayoutInflater.from(context).inflate(R.layout.pull_to_show_header, this, false);
		touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
		setOrientation(VERTICAL);
		addView(header, 0);
	}

	/**
	 * 进行一些关键性的初始化操作，比如：将下拉头向上偏移进行隐藏，给ListView注册touch事件。
	 */
	@Override
	protected void onLayout(boolean changed, int l, int t, int r, int b) {
		super.onLayout(changed, l, t, r, b);
		if (changed && !loadOnce) {
			hideHeaderHeight = -header.getHeight();
			headerLayoutParams = (MarginLayoutParams) header.getLayoutParams();
			headerLayoutParams.topMargin = hideHeaderHeight;
			listView = (ListView) getChildAt(1);
			listView.setOnTouchListener(this);
			loadOnce = true;

			// 当前正处于下拉或释放状态，要让ListView失去焦点，否则被点击的那一项会一直处于选中状态
			listView.setPressed(false);
			listView.setFocusable(false);
			listView.setFocusableInTouchMode(false);
		}
	}

	/**
	 * 当ListView被触摸时调用，其中处理了各种下拉刷新的具体逻辑。
	 */
	@Override
	public boolean onTouch(View v, MotionEvent event) {
		if (isAnimating) {
			return true;
		}
		// setIsAbleToPull(event);
		switch (event.getAction()) {
		case MotionEvent.ACTION_DOWN:
			yDown = event.getRawY();
			break;
		case MotionEvent.ACTION_MOVE:
			if (yDown == -1) {
				yDown = event.getRawY();
				break;
			}
			float yMove = event.getRawY();
			int distance = (int) (yMove - yDown);
			yDown = yMove;
			// 如果手指是下滑状态，并且下拉头是完全隐藏的，就屏蔽下拉事件
			if (distance <= 0 && headerLayoutParams.topMargin <= hideHeaderHeight) {
				return false;
			}
			if (Math.abs(distance) < touchSlop) {
				return false;
			}
			if (distance > 0) {
				View firstChild = listView.getChildAt(0);
				if (firstChild != null) {
					int firstVisiblePos = listView.getFirstVisiblePosition();
					if (firstVisiblePos == 0 && firstChild.getTop() == 0) {
						if (currentStatus == STATUS_HIDE_HEADER) {
							currentStatus = STATUS_SHOW_HEADER;
							// 当前正处于下拉或释放状态，要让ListView失去焦点，否则被点击的那一项会一直处于选中状态
							listView.clearFocus();
							listView.setPressed(false);
							listView.setFocusable(false);
							listView.setFocusableInTouchMode(false);
							return true;
						}
					}
				}
			} else {
				if (currentStatus == STATUS_SHOW_HEADER) {
					currentStatus = STATUS_HIDE_HEADER;
					// 当前正处于下拉或释放状态，要让ListView失去焦点，否则被点击的那一项会一直处于选中状态
					listView.clearFocus();
					listView.setPressed(false);
					listView.setFocusable(false);
					listView.setFocusableInTouchMode(false);
					return true;
				}
			}
			break;
		case MotionEvent.ACTION_UP:
		default:
			yDown = -1;
			if (currentStatus == STATUS_SHOW_HEADER) {
				// 松手时如果是释放立即刷新状态，就去调用正在刷新的任务
				new ShowHeaderTask().execute();
			} else if (currentStatus == STATUS_HIDE_HEADER) {
				// 松手时如果是下拉状态，就去调用隐藏下拉头的任务
				new HideHeaderTask().execute();
			}
		}
		return false;
	}

	/**
	 * 显示header 控件
	 * 
	 * @author kal
	 */
	class ShowHeaderTask extends AsyncTask<Void, Integer, Void> {

		public ShowHeaderTask() {
			isAnimating = true;
		}

		@Override
		protected Void doInBackground(Void... params) {

			int topMargin = headerLayoutParams.topMargin;
			while (true) {
				topMargin = topMargin + SCROLL_SPEED;
				if (topMargin >= 0) {
					topMargin = 0;
					break;
				}
				publishProgress(topMargin);
				try {
					Thread.sleep(1);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			publishProgress(topMargin);
			return null;
		}

		@Override
		protected void onProgressUpdate(Integer... topMargin) {
			headerLayoutParams.topMargin = topMargin[0];
			header.setLayoutParams(headerLayoutParams);
		}

		@Override
		protected void onPostExecute(Void result) {
			currentStatus = STATUS_SHOW_HEADER;
			isAnimating = false;
			headerLayoutParams.topMargin = 0;
			header.setLayoutParams(headerLayoutParams);
		}
	}

	/**
	 * 隐藏header 控件
	 * 
	 * @author kal
	 */
	class HideHeaderTask extends AsyncTask<Void, Integer, Integer> {

		public HideHeaderTask() {
			isAnimating = true;
		}

		@Override
		protected Integer doInBackground(Void... params) {

			int topMargin = headerLayoutParams.topMargin;
			while (true) {
				topMargin = topMargin - SCROLL_SPEED;
				if (topMargin <= hideHeaderHeight) {
					topMargin = hideHeaderHeight;
					break;
				}
				publishProgress(topMargin);
				try {
					Thread.sleep(1);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			return topMargin;
		}

		@Override
		protected void onProgressUpdate(Integer... topMargin) {
			headerLayoutParams.topMargin = topMargin[0];
			header.setLayoutParams(headerLayoutParams);
		}

		@Override
		protected void onPostExecute(Integer topMargin) {
			currentStatus = STATUS_HIDE_HEADER;
			isAnimating = false;
			headerLayoutParams.topMargin = topMargin;
			header.setLayoutParams(headerLayoutParams);
		}
	}

}
