package com.map.xsc;

import java.io.IOException;
import java.io.InputStream;
import java.util.Random;

import cn.m15.xys.R;

import com.google.android.maps.MapView;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.*;
import android.graphics.PorterDuff.Mode;
import android.hardware.*;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.SurfaceHolder.Callback;
import android.view.View.OnLongClickListener; 

public class SurfaceViewAcitvity extends Activity {

	AnimView mAnimView = null;
	double TOUCH_TOLERANCE = 10.0;
	private static final DisplayMetrics SCREEN = new DisplayMetrics();
	private MediaPlayer mMediaPlayer = new MediaPlayer();
	private Thread threads = new Thread(new TRun());
	private Random rand = new Random(); 
	@SuppressWarnings("deprecation")
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// 全屏显示窗口
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
				WindowManager.LayoutParams.FLAG_FULLSCREEN);
		// 获取屏幕宽高
		getWindowManager().getDefaultDisplay().getMetrics(SCREEN);
		// Display display = getWindowManager().getDefaultDisplay();
		// System.out.println(SCREEN.widthPixels+" "+ SCREEN.heightPixels);
		// 显示自定义的游戏View
		mAnimView = new AnimView(this, SCREEN.widthPixels, SCREEN.heightPixels);
		setContentView(mAnimView);
		initMusic();
		threads.start();
	}

	@SuppressLint("NewApi")
	public class AnimView extends SurfaceView implements Callback, Runnable,
			SensorEventListener {
		/** 向下移动动画 **/
		public final static int ANIM_DOWN = 0;
		/** 向左移动动画 **/
		public final static int ANIM_LEFT = 1;
		/** 向右移动动画 **/
		public final static int ANIM_RIGHT = 2;
		/** 向上移动动画 **/
		public final static int ANIM_UP = 3;
		/** 动画的总数量 **/
		public final static int ANIM_COUNT = 4;

		Animation mHeroAnim[][] = new Animation[2][ANIM_COUNT];
		private int roleId = 0; //男女主角的id
		Paint mPaint = null;

		/** 任意键被按下 **/
		private boolean mAllkeyDown = false;
		/** 按键下 **/
		private boolean mIskeyDown = false;
		/** 按键左 **/
		private boolean mIskeyLeft = false;
		/** 按键右 **/
		private boolean mIskeyRight = false;
		/** 按键上 **/
		private boolean mIskeyUp = false;

		// 当前绘制动画状态ID
		int mAnimationState = 0;

		// tile块的宽高
		public final static int TILE_WIDTH = 32;
		public final static int TILE_HEIGHT = 32;

		// 缓冲块的宽高的数量
		public final static int BUFFER_WIDTH_COUNT = 10;
		public final static int BUFFER_HEIGHT_COUNT = 15;

		// 场景的宽高,即整个地图的大小
		public final static int SCENCE_WIDTH = 960;
		public final static int SCENCE_HEIGHT = 640;

		// 场景偏移量 未到场景边界地图向回滚动
		public final static int SCENCE_OFFSET = 3;
		public final static int SCENCE_OFFSET_WIDTH = 100;

		// 场景块的宽高的数量
		public final static int TILE_WIDTH_COUNT = SCENCE_WIDTH / TILE_WIDTH;// 30
		public final static int TILE_HEIGHT_COUNT = SCENCE_HEIGHT / TILE_HEIGHT;// 20

		// 数组元素为0则什么都不画
		public final static int TILE_NULL = 0;
		// 第一层游戏View地图数组
		public int[][] mMapView = new int[TILE_HEIGHT_COUNT][TILE_WIDTH_COUNT]; // 行列数别弄反了。。。

		/*
		 * 第二层游戏实体actor数组,用于来画地图的 本程序地图不是真正的地图，而是由素材画出来的
		 * 这里的值，是根据该tile块在map素材中是第几个（先按行，再按列排列的），即map素材中每个tile块对应的id为： 1 2 3 4 5
		 * 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24
		 */
		public int[][] mMapAcotor = new int[TILE_HEIGHT_COUNT][TILE_WIDTH_COUNT];
		// public int[][] mCollision = new
		// int[TILE_HEIGHT_COUNT][TILE_WIDTH_COUNT];
		/*
		 * 第三层游戏碰撞物理层数组冲突还有问题，刚开始挺好的，走来走去后，就不对了以人物往下走为例：
		 * 原因是有时发生碰撞了，但还能继续走。。。但人物在地图中的位置还是等于原来碰撞前的，值一点没变，但人就是还往下走。。。
		 * 是因为虽然备份了人物碰撞前在地图中、屏幕中的位置，但是没有备份地图碰撞前的位置！！！
		 * 因为当人过了屏幕长度的三分之二大小时，就不是人在动了，而实际上是地图在往上动！
		 * 所以虽然碰撞后，人的位置变为碰撞前的位置，但是地图没有恢复到原来，还继续往上移动，所以看起来就好像是人物继续往下走！
		 * 
		 * 改了之后，虽然偶尔会有一点误差，但是不会出现人物明显穿墙的画面了！
		 * 
		 * 为了解决这个方案，利用原先代码里的drawRimString、以及Log.v，各种调试看值啊。。。囧
		 */
		public int[][] mCollision = {
				{ -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
						-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
						-1 },
				{ -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
						-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
						-1 },
				{ -1, -1, -1, -1, -1, -1, -1, -1, 0, 0, 0, 0, -1, -1, -1, -1,
						-1, -1, -1, -1, -1, -1, -1, 0, 0, 0, 0, 0, 0, -1 },
				{ -1, -1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
						0, 0, 0, 0, 0, 0, 0, 0, 0, -1 },
				{ -1, -1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
						0, 0, 0, 0, 0, 0, 0, 0, 0, -1 },
				{ -1, -1, 0, 0, 0, 0, 0, -1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
						0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -1 },
				{ -1, -1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
						0, 0, 0, 0, 0, 0, 0, 0, 0, -1 },
				{ -1, -1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
						0, 0, 0, 0, 0, 0, 0, 0, 0, -1 },
				{ -1, -1, 0, 0, 0, 0, -1, -1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
						0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -1 },
				{ -1, -1, 0, -1, -1, 0, -1, -1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
						0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -1 },
				{ -1, -1, 0, -1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
						0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -1 },
				{ -1, -1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
						0, 0, 0, 0, 0, 0, 0, 0, 0, -1 },
				{ -1, -1, -1, -1, -1, -1, -1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
						0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -1 },
				{ -1, -1, -1, -1, -1, -1, -1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
						0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -1 },
				{ -1, -1, -1, -1, -1, -1, -1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
						0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -1 },
				{ -1, -1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
						0, 0, 0, 0, 0, 0, 0, 0, 0, -1 },
				{ -1, -1, -1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
						0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -1 },
				{ -1, -1, -1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
						0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -1 },
				{ -1, -1, -1, -1, -1, -1, -1, 0, -1, -1, -1, -1, -1, -1, -1,
						-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
						-1 },
				{ -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
						-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
						-1 } };

		// 游戏地图资源
		Bitmap mBitmap = null;
		int totOfProds=6;
		//农产品图片资源
		Bitmap product[]=new Bitmap[totOfProds];
		int numOfProds[]=new int[totOfProds];
		int landx1=8,landx2=27,landy1=5,landy2=15; //耕地的索引范围
		//Bitmap toolIcon[]=new Bitmap[2];
		//int toolXY[][]=new int[2][2]; //存储x\y坐标对应的在屏幕上的索引
		// 资源文件
		Resources mResources = null;

		// 横向纵向tile块的数量
		int mWidthTileCount = 0; // map素材横向tile块的数量
		int mHeightTileCount = 0; // map素材纵向tile块的数量

		// 横向纵向
		int mBitMapWidth = 0;
		int mBitMapHeight = 0;

		// 英雄在地图中的坐标以英雄脚底中心为原点
		int mHeroPosX = 0;
		int mHeroPosY = 0;

		// 备份英雄发生碰撞以前的坐标点
		int mBackHeroPosX = 0;
		int mBackHeroPosY = 0;

		// 备份英雄发生碰撞以前的屏幕显示坐标点
		int mBackHeroScreenX = 0;
		int mBackHeroScreenY = 0;

		// 英雄在地图中绘制坐标
		int mHeroImageX = 0;
		int mHeroImageY = 0;

		// 英雄在行走范围中绘制坐标
		int mHeroScreenX = 0;
		int mHeroScreenY = 0;

		// 英雄在地图二位数组中的索引
		int mHeroIndexX = 0;
		int mHeroIndexY = 0;

		// 屏幕宽高才尺寸
		int mScreenWidth = 0;
		int mScreenHeight = 0;

		// 缓冲区域数据的index
		int mBufferIndexX = 0;
		int mBufferIndexY = 0;

		// 地图的坐标
		int mMapPosX = 0;
		int mMapPosY = 0;
		// 备份地图的坐标
		int mBackmMapPosX = 0;
		int mBackmMapPosY = 0;

		/** 人物图片资源与实际英雄脚底板坐标的偏移 **/
		public final static int OFF_HERO_X = 16;
		public final static int OFF_HERO_Y = 35;

		/** 主角行走步长 **/
		public final static int HERO_STEP = 4;

		/** 与实体层发生碰撞 **/
		private boolean isAcotrCollision = false;
		/** 与边界层发生碰撞 **/
		private boolean isBorderCollision = false;
		/** 是否收割**/
		private boolean isHarvest = false;
		/** 游戏主线程 **/
		private Thread mThread = null;
		/** 线程循环标志 **/
		private boolean mIsRunning = false;
		// 显示一个surface的抽象接口，使你可以控制surface的大小和格式， 以及在surface上编辑像素，
		// 和监视surace的改变。这个接口通常通过SurfaceView类实现。

		// 有关SurfaceHolder:
		// http://blog.csdn.net/pathuang68/article/details/7351317
		private SurfaceHolder mSurfaceHolder = null;
		private Canvas mCanvas = null;

		/** SensorManager管理器 **/
		private SensorManager mSensorMgr = null;
		Sensor mSensor = null;
		Sensor mSensor2 = null;
		/** 重力感应X轴 Y轴 Z轴的重力值 **/
		private float mGX = 0;
		private float mGY = 0;
		private float mGZ = 0;
		private boolean isNight=false;
		// private float mGZ = 0;
		// private float lastmGX=0;
		/**
		 * 构造方法
		 * 
		 * @param context
		 */
		public AnimView(Context context, int screenWidth, int screenHeight) {
			super(context);
			mPaint = new Paint();
			mScreenWidth = screenWidth;
			mScreenHeight = screenHeight;
			initAnimation(context);
			initMap(context);
			initHero();

			/** 获取mSurfaceHolder **/
			mSurfaceHolder = getHolder();
			mSurfaceHolder.addCallback(this); // 为SurfaceHolder添加一个SurfaceHolder.Callback回调接口。
			setFocusable(true);
			/** 得到SensorManager对象 **/
			mSensorMgr = (SensorManager) getSystemService(SENSOR_SERVICE);
			mSensor = mSensorMgr.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
			mSensor2 = mSensorMgr.getDefaultSensor(Sensor.TYPE_LIGHT);
			// 注册listener，第三个参数是检测的精确度
			// SENSOR_DELAY_FASTEST 最灵敏 因为太快了没必要使用
			// SENSOR_DELAY_GAME 游戏开发中使用
			// SENSOR_DELAY_NORMAL 正常速度
			// SENSOR_DELAY_UI 最慢的速度
			mSensorMgr.registerListener(this, mSensor,
					SensorManager.SENSOR_DELAY_GAME);
			mSensorMgr.registerListener(this, mSensor2,
					SensorManager.SENSOR_DELAY_GAME);
			/*
			for(int i=0;i<totOfProds;i++){
				for(int j=0;j<2;j++){
					toolXY[i][0]=i*4+1;
					toolXY[i][1]=mScreenHeight/TILE_HEIGHT-4;
				}
			}
			*/
		}

		private void initHero() {
			mHeroImageX = 100;
			mHeroImageY = 100;
			/** 根据图片显示的坐标算出英雄脚底的坐标 **/
			/** X轴+图片宽度的一半 Y轴加图片的高度 **/
			mHeroPosX = mHeroImageX + OFF_HERO_X;
			mHeroPosY = mHeroImageY + OFF_HERO_Y;
			mHeroIndexX = mHeroPosX / TILE_WIDTH;
			mHeroIndexY = mHeroPosY / TILE_HEIGHT;
			mHeroScreenX = mHeroPosX;
			mHeroScreenY = mHeroPosY;
			// 后来我附近上去的
			mBackHeroPosX = mHeroPosX;
			mBackHeroPosY = mHeroPosY;
			mBackHeroScreenX = mHeroScreenX;
			mBackHeroScreenY = mHeroScreenY;
		}

		private void initMap(Context context) {
			mBitmap = ReadBitMap(context, R.drawable.field);
			mBitMapWidth = mBitmap.getWidth();
			mBitMapHeight = mBitmap.getHeight();
			mWidthTileCount = mBitMapWidth / TILE_WIDTH; // 8=256/32
			mHeightTileCount = mBitMapHeight / TILE_HEIGHT; // 3=96/32
			// 初始化游戏的相关数据
			for (int i = 0; i < TILE_HEIGHT_COUNT; i++) {
				for (int j = 0; j < TILE_WIDTH_COUNT; j++) {
					mMapView[i][j] = 1;
					mMapAcotor[i][j]=-1;
				}
			}
			product[0]=ReadBitMap(context, R.drawable.prod0);
			product[1]=ReadBitMap(context, R.drawable.prod1);
			product[2]=ReadBitMap(context, R.drawable.prod2);
			product[3]=ReadBitMap(context, R.drawable.prod3);
			product[4]=ReadBitMap(context, R.drawable.prod4);
			product[5]=ReadBitMap(context, R.drawable.prod5);
			//toolIcon[0]=ReadBitMap(context, R.drawable.prod0);
		}

		private void initAnimation(Context context) {
			// 这里可以用循环来处理总之我们需要把动画的ID传进去
			//男主角
			mHeroAnim[0][ANIM_DOWN] = new Animation(context, new int[] {
					R.drawable.hero_down_a1, R.drawable.hero_down_b1,
					R.drawable.hero_down_c1, R.drawable.hero_down_d1 }, true);
			mHeroAnim[0][ANIM_LEFT] = new Animation(context, new int[] {
					R.drawable.hero_left_a1, R.drawable.hero_left_b1,
					R.drawable.hero_left_c1, R.drawable.hero_left_d1 }, true);
			mHeroAnim[0][ANIM_RIGHT] = new Animation(context, new int[] {
					R.drawable.hero_right_a1, R.drawable.hero_right_b1,
					R.drawable.hero_right_c1, R.drawable.hero_right_d1 }, true);
			mHeroAnim[0][ANIM_UP] = new Animation(context, new int[] {
					R.drawable.hero_up_a1, R.drawable.hero_up_b1,
					R.drawable.hero_up_c1, R.drawable.hero_up_d1 }, true);
			//女主角
			mHeroAnim[1][ANIM_DOWN] = new Animation(context, new int[] {
					R.drawable.hero_down_a2, R.drawable.hero_down_b2,
					R.drawable.hero_down_c2, R.drawable.hero_down_d2 }, true);
			mHeroAnim[1][ANIM_LEFT] = new Animation(context, new int[] {
					R.drawable.hero_left_a2, R.drawable.hero_left_b2,
					R.drawable.hero_left_c2, R.drawable.hero_left_d2 }, true);
			mHeroAnim[1][ANIM_RIGHT] = new Animation(context, new int[] {
					R.drawable.hero_right_a2, R.drawable.hero_right_b2,
					R.drawable.hero_right_c2, R.drawable.hero_right_d2 }, true);
			mHeroAnim[1][ANIM_UP] = new Animation(context, new int[] {
					R.drawable.hero_up_a2, R.drawable.hero_up_b2,
					R.drawable.hero_up_c2, R.drawable.hero_up_d2 }, true);
		}

		protected void Draw() {
			if (isShrink) {
				// 清屏，搞定！！！(http://blog.csdn.net/yanzi1225627/article/details/8236309)
				// 但是残余的就变成黑色了。。。。
				Paint p = new Paint();
				// p.setColor(Color.WHITE); //没有用额。。。
				p.setXfermode(new PorterDuffXfermode(Mode.CLEAR));
				mCanvas.drawPaint(p);
				p.setXfermode(new PorterDuffXfermode(Mode.SRC));

			}
			if(!isNight)
				mCanvas.drawColor(Color.WHITE); // 白天，光线亮，设置画布背景为白色
			else
				mCanvas.drawColor(Color.GRAY); // 夜晚，光线暗，设置画布背景为黑色
			// 缩放画布(以图片中心点进行缩放，XY轴缩放比例相同)，应该以人为中心进行缩放
			mCanvas.scale(rate, rate, mHeroScreenX, mHeroScreenY);
			/** 绘制地图 **/
			DrawMap(mCanvas, mPaint, mBitmap);
			//DrawTools(mCanvas,mPaint,toolIcon[0],toolXY[0][0],toolXY[0][1]);
			/** 绘制动画 **/
			RenderAnimation(mCanvas);
			/** 更新动画 **/
			UpdateHero();
			// String
			// str=mHeroIndexX+","+mHeroIndexY+" "+mCollision[mHeroIndexY][mHeroIndexX];
			// drawRimString(mCanvas, str, Color.BLACK, 20,mScreenHeight >> 1);
			/*
			if (isBorderCollision) {
				DrawCollision(mCanvas, "碰撞");
			}

			if (isAcotrCollision) {
				DrawCollision(mCanvas, "碰撞"); 
			}
			if(isHarvest){
				DrawCollision(mCanvas, "收割");
				isHarvest=false;
			}
			*/
		}

		private void DrawCollision(Canvas canvas, String str) {
			// String
			// str2="\n Map width:"+mBitMapWidth+" "+"height:"+mBitMapHeight;
			// String
			// str3="\n Screen width:"+mScreenWidth+" "+"height:"+mScreenHeight;
			if(!isNight)
			drawRimString(canvas, str, Color.WHITE, mHeroImageX,
					mHeroImageY);
			else
				drawRimString(canvas, str, Color.BLACK, mHeroImageX,
						mHeroImageY);
		}
		
		private void UpdateHero() {
			// 一下注释均设地图宽为480，长为800. 手机屏幕，288*480(但我总觉得应该是320*480)
			if (mAllkeyDown) {
				/** 根据按键更新显示动画 **/
				/** 在碰撞数组中寻找看自己是否与地图物理层发生碰撞 **/
				// 人物往下走
				int h = mScreenHeight / 3;
				int w = mScreenWidth / 3;
				if (mIskeyDown) {
					mAnimationState = ANIM_DOWN;
					mHeroPosY += HERO_STEP; // 英雄在地图中的坐标
					// >=320/10,(480-160)/32=20
					if (mHeroScreenY >= h * 2) {
						/*
						 * mHeroIndexX = mHeroPosX / TILE_WIDTH; 
						 * mHeroIndexY =mHeroPosY / TILE_HEIGHT;
						 * 对代码做一些解释，当然，数值是来源于原来代码中的。
						 * 地图上下移动有个范围，地图上界即屏幕上界~地图下界即屏幕下界 地图最上端为屏幕最上端时，320/32=10
						 * 地图最下端为屏幕最下端时，(800-160)/32=20
						 * 但若<=20，则地图最下面会有一小部分重复往上动，走到那里会出现多个人 改成17后，就没事了。
						 * 可能是有的手机像素高度比较大（大于800），所以会出现最下面地图重复移动的情况
						 */
						if (mHeroIndexY >= h * 2 / 32
								&& mHeroIndexY <= (SCENCE_HEIGHT - h) / 32) {
							// mMapPosY:地图的坐标，即人物不动，地图往上移动，也就是地图的Y坐标减小
							mMapPosY -= HERO_STEP;
						} else {
							mHeroScreenY += HERO_STEP;
						}
					} else {
						// 人就在屏幕上往下移动
						mHeroScreenY += HERO_STEP;
					}
				}
				// 人物往左走
				else if (mIskeyLeft) {
					mAnimationState = ANIM_LEFT;
					mHeroPosX -= HERO_STEP;
					if (mHeroScreenX <= w) {
						/*
						 * >=96/32=3，即地图最左端恰好为屏幕最左端
						 * 地图最右端为屏幕最右端时，(480-192)/32=9，原先不知为何是7
						 */
						if (mHeroIndexX >= w / 32
								&& mHeroIndexX <= (SCENCE_WIDTH - w * 2) / 32) {
							// 人不动，地图往右移动
							mMapPosX += HERO_STEP;
						} else {
							// 人在屏幕上往左移动，应该是地图向右移动不了了
							mHeroScreenX -= HERO_STEP;
						}
					} else {
						// 人在屏幕上向左移动
						mHeroScreenX -= HERO_STEP;
					}
				}
				// 人物向右移动
				else if (mIskeyRight) {
					mAnimationState = ANIM_RIGHT;
					mHeroPosX += HERO_STEP;
					if (mHeroScreenX >= w * 2) {
						/*
						 * 192/32=6，应该就是地图最左端恰好为屏幕最左端的时候
						 * 地图最右端恰好为屏幕最右端时，为:(480-96)/32=12
						 * 但是若改成12或11，当人物往右移动（即地图在向左移动时），
						 * 最后一小段距离（2或1个格子）人物会跳跃前进，即人物一下子就到了最右端
						 */
						if (mHeroIndexX >= w * 2 / 32
								&& mHeroIndexX <= (SCENCE_WIDTH - w) / 32) {
							mMapPosX -= HERO_STEP;
						} else {
							mHeroScreenX += HERO_STEP;
						}
					} else {
						mHeroScreenX += HERO_STEP;
					}
				}
				// 人物向上移动
				else if (mIskeyUp) {
					mAnimationState = ANIM_UP;
					mHeroPosY -= HERO_STEP;
					/*
					 * 地图最上端为屏幕最上端时，160/32=5 地图最下端为屏幕最下端时，(800-320)/32=15
					 */
					if (mHeroScreenY <= h) {
						if (mHeroIndexY >= h / 32
								&& mHeroIndexY <= (SCENCE_HEIGHT - h * 2) / 32) {
							mMapPosY += HERO_STEP;
						} else {
							mHeroScreenY -= HERO_STEP;
						}
					} else {
						mHeroScreenY -= HERO_STEP;
					}
				}
//Log.v("xy","mapX:"+mMapPosX+" mapY:"+mMapPosY);
				/** 算出英雄移动后在地图二位数组中的索引 **/
				mHeroIndexX = mHeroPosX / TILE_WIDTH;
				mHeroIndexY = mHeroPosY / TILE_HEIGHT;

				// /** 检测人物是否出屏 **/
				isBorderCollision = false;
				if (mHeroPosX <= 0) {
					mHeroPosX = 0;
					mHeroScreenX = 0;
					isBorderCollision = true;
				} else if (mHeroPosX >= TILE_WIDTH_COUNT * TILE_WIDTH) {
					mHeroPosX = TILE_WIDTH_COUNT * TILE_WIDTH; // 15*32
					mHeroScreenX = mScreenWidth;
					isBorderCollision = true;
				}
				if (mHeroPosY <= 0) {
					mHeroPosY = 0;
					mHeroScreenY = 0;
					isBorderCollision = true;
				} else if (mHeroPosY >= TILE_HEIGHT_COUNT * TILE_HEIGHT) {
					mHeroPosY = TILE_HEIGHT_COUNT * TILE_HEIGHT;
					mHeroScreenY = mScreenHeight;
					isBorderCollision = true;
				}

				// 防止地图越界
				if (mMapPosX >= 0) {
					mMapPosX = 0;
					// 这里为什么又是320？？？屏幕的宽按计算应该是96*3=288
				} else if (mMapPosX <= -(SCENCE_WIDTH - mScreenWidth)) {
					mMapPosX = -(SCENCE_WIDTH - mScreenWidth);
				}
				if (mMapPosY >= 0) {
					mMapPosY = 0;
				} else if (mMapPosY <= -(SCENCE_HEIGHT - mScreenHeight)) {
					mMapPosY = -(SCENCE_HEIGHT - mScreenHeight);
				}

				/** 越界检测 **/
				int width = mCollision[0].length - 1;
				int height = mCollision.length - 1;

				if (mHeroIndexX <= 0) {
					mHeroIndexX = 0;
				} else if (mHeroIndexX >= width) {
					mHeroIndexX = width;
				}
				if (mHeroIndexY <= 0) {
					mHeroIndexY = 0;
				} else if (mHeroIndexY >= height) {
					mHeroIndexY = height;
				}
				// 碰撞检测
				if (mCollision[mHeroIndexY][mHeroIndexX] == -1) {
					// drawRimString(mCanvas, "-1", Color.BLACK,
					// mScreenWidth>>1,(mScreenHeight >> 1)+50);
					// String str1=mHeroPosX+" "+mHeroPosY;
					// drawRimString(mCanvas, str1, Color.BLACK,
					// mScreenWidth>>1,(mScreenHeight >> 1)+150);
					// String str2=mBackHeroPosX+" "+mBackHeroPosY;
					// drawRimString(mCanvas, str2, Color.BLACK,
					// mScreenWidth>>1,(mScreenHeight >> 1)+200);
					// Log.v("hero", "mHeroPosY:" +
					// mHeroPosY+" "+"mBackHeroPosY:" +
					// mBackHeroPosY+" mHeroScreenY:"+mHeroScreenY);
					mHeroPosX = mBackHeroPosX;
					mHeroPosY = mBackHeroPosY;
					mHeroScreenY = mBackHeroScreenY;
					mHeroScreenX = mBackHeroScreenX;
					//我增加的
					mMapPosX = mBackmMapPosX;
					mMapPosY = mBackmMapPosY;
					isAcotrCollision = true;
				} else {

					mBackHeroPosX = mHeroPosX;
					mBackHeroPosY = mHeroPosY;
					mBackHeroScreenX = mHeroScreenX;
					mBackHeroScreenY = mHeroScreenY;
					//我增加的
					mBackmMapPosX = mMapPosX;
					mBackmMapPosY = mMapPosY;
					isAcotrCollision = false;
					// Log.v("hero", "mHeroPosY:" +
					// mHeroPosY+" "+"mBackHeroPosY:" +
					// mBackHeroPosY+" mHeroScreenY:"+mHeroScreenY);
				}
				//Log.v("hero", "mHeroIndexX " + mHeroIndexX + " "
						//+ "mHeroIndexY " + mHeroIndexY);
				/*
				 * 英雄在地图中绘制坐标？？？是在实际屏幕中吧？？？ int mHeroImageX = 0; int mHeroImageY
				 * = 0; 英雄在行走范围中绘制坐标 int mHeroScreenX = 0; int mHeroScreenY = 0;
				 */
				mHeroImageX = mHeroScreenX - OFF_HERO_X;
				mHeroImageY = mHeroScreenY - OFF_HERO_Y;
			}
		}

		private void RenderAnimation(Canvas canvas) {
			if (mAllkeyDown) {
				/** 任意键被按下 **/
				/** 绘制主角动画 **/
				mHeroAnim[roleId][mAnimationState].DrawAnimation(canvas, mPaint,
						mHeroImageX, mHeroImageY);
			} else {
				/** 按键抬起后人物停止动画 **/
				mHeroAnim[roleId][mAnimationState].DrawFrame(canvas, mPaint,
						mHeroImageX, mHeroImageY, 0);
			}
		}

		/**
		 * 设置按键状态true为按下 false为抬起
		 * 
		 * @param keyCode
		 * @param state
		 */
		public void setKeyState(int keyCode, boolean state) {
			switch (keyCode) {
			case KeyEvent.KEYCODE_DPAD_DOWN:
				mIskeyDown = state;
				break;
			case KeyEvent.KEYCODE_DPAD_UP:
				mIskeyUp = state;
				break;
			case KeyEvent.KEYCODE_DPAD_LEFT:
				mIskeyLeft = state;
				break;
			case KeyEvent.KEYCODE_DPAD_RIGHT:
				mIskeyRight = state;
				break;
			}
			mAllkeyDown = state;
		}

		/**
		 * 设置所有按键状态 false
		 * 
		 * @param keyCode
		 * @param state
		 */
		public void setKeyStateFalse() {
			mIskeyDown = false;
			mIskeyUp = false;
			mIskeyLeft = false;
			mIskeyRight = false;
			mAllkeyDown = false;
		}

		/**
		 * 这里的mbitmap是res中的map素材
		 * 
		 * @param canvas
		 * @param paint
		 * @param bitmap
		 */
		private void DrawMap(Canvas canvas, Paint paint, Bitmap bitmap) {
			/*
			 * 为什么当人物差不多向右走了一定距离后，地图不动了，但人物的x坐标还是增加？
			 * 原来是UpdateHero方法里监测地图是否越界，之前用的是数值啊啊啊啊，没改过来。。。。
			 */
			int i, j;
			for (i = 0; i < TILE_HEIGHT_COUNT; i++) {
				for (j = 0; j < TILE_WIDTH_COUNT; j++) {
					DrawMapTile(mMapAcotor[i][j], canvas, paint, bitmap, mMapPosX
							+ (j * TILE_WIDTH), mMapPosY + (i * TILE_HEIGHT),
							i, j,1);
				}
			}
		}
		
		/**
		 * 根据ID绘制一个tile块
		 * 
		 * @param id
		 * @param canvas
		 * @param paint
		 * @param bitmap
		 */
		private void DrawMapTile(int id, Canvas canvas, Paint paint,
				Bitmap bitmap, int x, int y, int row, int col,int cnt) {
			// 根据数组中的ID算出在地图资源中的XY 坐标
			// 因为编辑器默认0 所以第一张tile的ID不是1而是0， 所以这里 id--
			/*
			 * map素材横向tile块的数量 mWidthTileCount map素材纵向tile块的数量 mHeightTileCount
			 */
			// id--;
			// int rows = id / mWidthTileCount; //即它在map素材中是第几行
			// int bitmapX = (id - (rows * mWidthTileCount)) * TILE_WIDTH;
			// int bitmapY = rows * TILE_HEIGHT;
			int bitmapX = col * TILE_WIDTH;
			int bitmapY = row * TILE_HEIGHT;
			if(id>=0 && col>=landx1 && col<=landx2 && row>=landy1 && row<=landy2)
				DrawClipImage(canvas, paint, product[id], x, y, 0, 0,
						TILE_WIDTH*cnt, TILE_HEIGHT*cnt);
			else
				DrawClipImage(canvas, paint, bitmap, x, y, bitmapX, bitmapY,
					TILE_WIDTH*cnt, TILE_HEIGHT*cnt);
		}

		/**
		 * 绘制图片中的一部分图片
		 * 
		 * @param canvas
		 * @param paint
		 * @param bitmap
		 * @param x
		 *            ：屏幕中要画的tile块的左上角x，由于是画整个地图，所以该x、y可能会超出整个手机屏幕
		 * @param y
		 *            ：屏幕中要画的tile块的左上角y
		 * @param src_x
		 *            ：map素材中要画在屏幕上的tile块的左上角x坐标
		 * @param src_y
		 *            ：map素材中要画在屏幕上的tile块的左上角y坐标
		 * @param src_width
		 * @param src_Height
		 */
		private void DrawClipImage(Canvas canvas, Paint paint, Bitmap bitmap,
				int x, int y, int src_x, int src_y, int src_xp, int src_yp) {
			canvas.save();
			// 通过指定矩形修改当前区域。
			canvas.clipRect(x, y, x + src_xp, y + src_yp);
			// 用给定的paint绘制指定位图，top/left的值作为位图的中心，位图可以使用当前的矩阵变形。 ???
			canvas.drawBitmap(bitmap, x - src_x, y - src_y, paint);
			canvas.restore();
		}

		/**
		 * 程序切割图片 返回一个不可变的位图，该位图来自源图指定的子集。 x 子位图第一个像素在源位图的X坐标 y 子位图第一个像素在源位图的y坐标
		 * w 子位图每一行的像素个数 h 子位图的行数
		 * 
		 * @param bitmap
		 * @param x
		 * @param y
		 * @param w
		 * @param h
		 * @return
		 */
		public Bitmap BitmapClipBitmap(Bitmap bitmap, int x, int y, int w, int h) {
			return Bitmap.createBitmap(bitmap, x, y, w, h);
		}

		/**
		 * 读取本地资源的图片
		 * 
		 * @param context
		 * @param resId
		 * @return
		 */
		public Bitmap ReadBitMap(Context context, int resId) {
			BitmapFactory.Options opt = new BitmapFactory.Options();
			opt.inPreferredConfig = Bitmap.Config.RGB_565;
			opt.inPurgeable = true;
			opt.inInputShareable = true;
			// 获取资源图片
			InputStream is = context.getResources().openRawResource(resId);
			return BitmapFactory.decodeStream(is, null, opt);
		}

		/**
		 * 绘制画带阴影的文字
		 * 
		 * @param canvas
		 * @param str
		 * @param color
		 * @param x
		 * @param y
		 */
		public final void drawRimString(Canvas canvas, String str, int color,
				int x, int y) {
			int backColor = mPaint.getColor();
			mPaint.setColor(~color);
			canvas.drawText(str, x + 1, y, mPaint);
			canvas.drawText(str, x, y + 1, mPaint);
			canvas.drawText(str, x - 1, y, mPaint);
			canvas.drawText(str, x, y - 1, mPaint);
			mPaint.setColor(color);
			canvas.drawText(str, x, y, mPaint);
			mPaint.setColor(backColor);
		}

		@Override
		public void run() {
			while (mIsRunning) {
				// 在这里加上线程安全锁
				synchronized (mSurfaceHolder) {
					// 获取一个Canvas对象，并锁定之。所得到的Canvas对象，其实就是Surface中一个成员。
					mCanvas = mSurfaceHolder.lockCanvas();
					Draw();
					/** 绘制结束后解锁显示在屏幕上 **/
					// 当修改Surface中的数据完成后，释放同步锁，并提交改变，然后将新的数据进行展示，同时Surface中相关数据会被丢失。
					mSurfaceHolder.unlockCanvasAndPost(mCanvas);
				}
			}
		}

		/**
		 * SurfaceHolder.Callback定义了三个接口方法
		 * 当surface发生任何结构性的变化时（格式或者大小），该方法就会被立即调用。
		 */
		@Override
		public void surfaceChanged(SurfaceHolder arg0, int arg1, int arg2,
				int arg3) {
			// surfaceView的大小发生改变的时候

		}

		/**
		 * 当surface对象创建后，该方法就会被立即调用。
		 */
		@Override
		public void surfaceCreated(SurfaceHolder arg0) {
			/** 启动游戏主线程 **/
			mIsRunning = true;
			mThread = new Thread(this);
			mThread.start();
		}

		/**
		 * 当surface对象在将要销毁前，该方法会被立即调用。
		 */
		@Override
		public void surfaceDestroyed(SurfaceHolder arg0) {
			// surfaceView销毁的时候
			mIsRunning = false;
		}

		/**
		 * 重力感应器
		 */
		
		@Override
		public void onSensorChanged(SensorEvent event) {
			// TODO Auto-generated method stub
			if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) { 
				
				mGX = event.values[0];
				mGY = event.values[1];
				mGZ = event.values[2];
//String str = "X:"+mGX+" "+"Y:"+mGY+" "+"Z:"+mGZ;
//Log.v("xyz:",str);
				// mGZ = event.values[SensorManager.DATA_Z];
				setKeyStateFalse();
				if (Math.abs(mGX) >= Math.abs(mGY) && Math.abs(mGX) >= 1.0) {
					if (mGX >= 0) { 
						// 向左走
						setKeyState(KeyEvent.KEYCODE_DPAD_LEFT, true);
					} else {
						// 向右走
						setKeyState(KeyEvent.KEYCODE_DPAD_RIGHT, true);
					}
				} else if (Math.abs(mGX) < Math.abs(mGY) && Math.abs(mGY) >= 1.0) {
					// 这里之所以设置成一个大于6.0，一个小于3.0，是因为人拿手机的时候，手机基本上就是倾斜的，mGY肯定大于0.
					if (1.0 <mGY && mGY< 4.0) // 向上走
						setKeyState(KeyEvent.KEYCODE_DPAD_UP, true);
					else if (mGY >= 7.0) { // 向下走
						setKeyState(KeyEvent.KEYCODE_DPAD_DOWN, true);
					}
				}
				//摇一摇换主角
				int minValue=19;
				if (Math.abs(mGX) > minValue || Math.abs(mGY) > minValue || Math.abs(mGZ) > minValue) {
					roleId=(roleId+1)%2;
	            } 
				
			}
			else if(event.sensor.getType() == Sensor.TYPE_LIGHT){
				float lv=event.values[0];
				if(lv>10.0f){
					isNight=false;
				}
				else{
					isNight=true;
				}
			}
		}

		@Override
		public void onAccuracyChanged(Sensor sensor, int accuracy) {
			// TODO Auto-generated method stub

		}

	}

	/**
	 * 两指触摸，实现缩放
	 * 
	 * @param event
	 */
	public void setMultiTouch(MotionEvent event) {
		mAnimView.setKeyStateFalse();
		float x1 = event.getX(0);
		float y1 = event.getY(0);
		float x2 = event.getX(1);
		float y2 = event.getY(1);
		if (isFirst) {
			// 得到第一次触屏时线段的长度
			oldLineDistance = (float) Math.sqrt(Math.pow(x2 - x1, 2)
					+ Math.pow(y2 - y1, 2));
			isFirst = false;
		} else {
			// mScreenHeight=800>SCENCE_HEIGHT=640....
			float maxrate=4;
			float minrate = Math.min((float) mAnimView.mScreenHeight
					/ mAnimView.SCENCE_HEIGHT, (float) mAnimView.mScreenWidth
					/ mAnimView.SCENCE_WIDTH);
			//Log.v("ratio", "min:" + minrate + " " + mAnimView.mScreenHeight+ "," + mAnimView.mScreenWidth);
			// 得到非第一次触屏时线段的长度
			float newLineDistance = (float) Math.sqrt(Math.pow(x2 - x1, 2)
					+ Math.pow(y2 - y1, 2));
			if (newLineDistance > oldLineDistance) {
				isShrink = false;
				rate = oldRate * rvalue;
				if(rate>maxrate)
					rate=maxrate;
			} else {
				isShrink = true;
				rate = oldRate / rvalue;
				if (rate < minrate)
					rate = minrate;
			}
			// 获取本次的缩放比例
			// rate = oldRate * newLineDistance / oldLineDistance;
		}
	}

	/**
	 * 触摸实现
	 */
	private boolean isFirst = true;
	private float oldLineDistance = 0; // 两指第一次碰到屏幕时的距离
	private float oldRate = 1;
	private float rate = 1;
	private float rvalue = (float) 1.5;
	private boolean isShrink = false; // 若为true，表示地图收缩

	@SuppressLint("NewApi")
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if (event.getPointerCount() == 2) {
			setMultiTouch(event);
		}
		float x = event.getX();
		float y = event.getY();
		float dx, dy;
		float mX = mAnimView.mHeroScreenX, mY = mAnimView.mHeroScreenY;
		switch (event.getAction()) {
		// 触摸结束
		case MotionEvent.ACTION_UP:
			isFirst = true;
			oldRate = rate;
			// 手指离开屏幕，则人物不动，使所有方向都为false
			mAnimView.setKeyStateFalse();
			return false;

			// 这个也要实现，这样手指触摸一个地方不动，人物也能走动。如果这个不实现，那么手指必须触摸移动，人物才能走动。
		case MotionEvent.ACTION_DOWN:
			// mAnimView.setKeyStateFalse();
			dx = x - mX;
			dy = y - mY;
			int i,j;
			float adx=Math.abs(dx);
			float ady=Math.abs(dy);
			// if (Math.abs(dx) >= TOUCH_TOLERANCE
			// || Math.abs(dy) >= TOUCH_TOLERANCE) {
			// mAnimView.setKeyStateFalse();
			//if(adx<=20.0 && )
			if (adx >= ady) { // move from left -> right
												// or right -> left
				
				if(adx<=32.0f){
					//i=mAnimView.mHeroPosX/mAnimView.TILE_WIDTH;
					//j=mAnimView.mHeroPosY/mAnimView.TILE_HEIGHT;
					//先算出手指触摸的地方在地图中对应的数组索引
					i=(-mAnimView.mMapPosX+(int)x)/mAnimView.TILE_WIDTH;
					j=(-mAnimView.mMapPosY+(int)y)/mAnimView.TILE_HEIGHT;
//Log.v("ij", j+" "+i+" "+mAnimView.mScreenWidth+" "+mAnimView.mScreenHeight);
//Log.v("xy",x+","+mX+" "+y+","+mY);
					if(mAnimView.mMapAcotor[j][i]==-1){
						//随机一个农产品
						int tmp=rand.nextInt(mAnimView.totOfProds);
						mAnimView.mMapAcotor[j][i]=tmp;
						//mAnimView.numOfProds[tmp]++;
					}
					else{
						mAnimView.isHarvest=true; //起不了作用，屏幕没有显示收割。。。
						//mAnimView.numOfProds[mAnimView.mMapAcotor[j][i]]--;
						mAnimView.mMapAcotor[j][i]=-1;
					}
				}
				else if (dx > 35.0f) {
					mAnimView.setKeyState(KeyEvent.KEYCODE_DPAD_RIGHT, true);
				} else if (dx<-35.0f){
					mAnimView.setKeyState(KeyEvent.KEYCODE_DPAD_LEFT, true);
				}
			} else { // move from top -> bottom or bottom -> top
				if(ady<=45.0f){
					//i=mAnimView.mHeroPosX/mAnimView.TILE_WIDTH;
					//j=mAnimView.mHeroPosY/mAnimView.TILE_HEIGHT;
					i=(-mAnimView.mMapPosX+(int)x)/mAnimView.TILE_WIDTH;
					j=(-mAnimView.mMapPosY+(int)y)/mAnimView.TILE_HEIGHT;
					if(mAnimView.mMapAcotor[j][i]==-1){
						int tmp=rand.nextInt(mAnimView.totOfProds);
						mAnimView.mMapAcotor[j][i]=tmp;
						mAnimView.numOfProds[tmp]++;
					}
					else{
						mAnimView.isHarvest=true;
						mAnimView.numOfProds[mAnimView.mMapAcotor[j][i]]--;
						mAnimView.mMapAcotor[j][i]=-1;
						
					}
//Log.v("ij", j+" "+i);
				}
				else if (dy > 48.0f) {
					mAnimView.setKeyState(KeyEvent.KEYCODE_DPAD_DOWN, true);
				} else if(dy<-48.0f){
					mAnimView.setKeyState(KeyEvent.KEYCODE_DPAD_UP, true);
				}
			}
			return true;
			/**
			 * MOVE是介于MotionEvent.ACTION_DOWN和MotionEvent.ACTION_UP之间的，
			 * 所以通过MOVE来控制人物移动 如果用MotionEvent.ACTION_DOWN的话，则只能每次按一下走一步，太不方便了
			 */
		case MotionEvent.ACTION_MOVE:
			dx = x - mX;
			dy = y - mY;
			if (Math.abs(dx) >= Math.abs(dy)) { // move from left -> right
				// or right -> left
				if (dx > 0.0f) {
					mAnimView.setKeyState(KeyEvent.KEYCODE_DPAD_RIGHT, true);
				} else {
					mAnimView.setKeyState(KeyEvent.KEYCODE_DPAD_LEFT, true);
				}
			} else { // move from top -> bottom or bottom -> top
				if (dy > 0.0f) {
					mAnimView.setKeyState(KeyEvent.KEYCODE_DPAD_DOWN, true);
				} else {
					mAnimView.setKeyState(KeyEvent.KEYCODE_DPAD_UP, true);
				}
			}
			return true;
		}
		return super.onTouchEvent(event);
	}

	/**
	 * Activity中的方法
	 */
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			// ---- 提示是否退出游戏 ----
			stopMusic();
			System.exit(0);
			return false;
		}
		else{
			mAnimView.setKeyState(keyCode, true);
		}
		return super.onKeyDown(keyCode, event);
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		mAnimView.setKeyState(keyCode, false);
		return super.onKeyUp(keyCode, event);
	}
	/**
	 * 用于播放提醒时的音乐和震动
	 */
	private void initMusic() {
		try {
			int position1 = StartActivity.music.musics;
			if (position1 == 0)
				mMediaPlayer = MediaPlayer.create(SurfaceViewAcitvity.this, R.raw.fallingstar);
			if (position1 == 1)
				mMediaPlayer = MediaPlayer.create(SurfaceViewAcitvity.this, R.raw.qguose);
			if (position1 == 2)
				mMediaPlayer = MediaPlayer.create(SurfaceViewAcitvity.this,
						R.raw.qhuanyin);
			if (position1 == 3)
				mMediaPlayer = MediaPlayer.create(SurfaceViewAcitvity.this,
						R.raw.qlanxi);
			if (position1 == 4)
				mMediaPlayer = MediaPlayer.create(SurfaceViewAcitvity.this,
						R.raw.qyishui);
			if (position1 == 5)
				mMediaPlayer = MediaPlayer.create(SurfaceViewAcitvity.this,
						R.raw.qyueguang);
			//下面的不能删掉，否则音乐就播放不出来。。。
			if (mMediaPlayer != null) {
				mMediaPlayer.stop();
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
//System.out.println("error");
		}
	}
	private void stopMusic() {
		try{
			if (mMediaPlayer != null) {
				mMediaPlayer.stop();
				mMediaPlayer.release();
			}
			//finish();
		}
		catch(Exception e){
			
		}
		
	}
	private class TRun implements Runnable {

		@Override
		public void run() {
			// TODO Auto-generated method stub
			try {
				mMediaPlayer.prepare();//之前忘加这句话了，导致背景音乐播放不出来
				mMediaPlayer.start();
			} catch (IllegalStateException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
		}

	}
	
}
