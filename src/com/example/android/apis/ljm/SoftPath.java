package com.example.android.apis.ljm;

import android.annotation.SuppressLint;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.Region;
//import android.os.SystemProperties;
import android.util.Log;

//import com.hisense.meetingboard.cache.SoftPathCacheData;
//import com.hisense.meetingboard.paint.SerializablePointF;
//import com.hisense.meetingboard.paint.SingleTouchEvent;//??是否为封装了Touch事件的类，包含坐标点和触控面积
import com.example.android.apis.ljm.ArrayDeList;
import com.example.android.apis.ljm.SingleTouchEvent;

//import com.hisense.meetingboard.util.ArrayDeList;
//import com.hisense.meetingboard.util.Constant.PenStyleMode;
//import com.hisense.meetingboard.util.HLog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

///圆润笔迹，TODO ，相交曲线需要处理，
public class SoftPath extends Path {

	public static double MAX_DIS = 50.0;// 绘制控制点阈值，大于则直接用当前点做终点，否则用连续两个点的中点做贝塞尔的终点。

	// 海信值=50

	class SoftPointsInfo {
		ArrayDeList<PointF> regionPoints;// 侧边线点，双向链表，前半段定义为绿色点，后半段定义为蓝色点
		// 顺时针绿色在内侧，逆时针蓝色在内侧

		ArrayList<PointF> mdrawPoints;// 待绘制的点
		ArrayList<Integer> mDirectionList;// 存储点的运动方向象限，如果连续两个点象限变化不连续则修正，
		ArrayList<PointF> pathPoints;// 存储点原始点，
		ArrayList<PointInfo> pointInfos;// 存储宽高信息，
		ArrayList<PointF> fixPoints;
		private boolean isLastIntersect = false;

		class PointInfo {
			int width;
			int hight;

			PointInfo(int width, int hight) {
				this.width = width;
				this.hight = hight;
			}
		}

		PointF addPoints[] = new PointF[2];

		SoftPointsInfo() {

			regionPoints = new ArrayDeList<PointF>();
			pathPoints = new ArrayList<PointF>();
			pointInfos = new ArrayList<SoftPath.SoftPointsInfo.PointInfo>();
			mDirectionList = new ArrayList<Integer>();
			fixPoints = new ArrayList<PointF>();
			mdrawPoints = new ArrayList<PointF>();
		}

		SoftPointsInfo(float x, float y, int width, int hight) {
			this();
			pathPoints.add(new PointF(x, y));
			pointInfos.add(new PointInfo(width, hight));
		}

		SoftPointsInfo(SoftPointsInfo info) {
			regionPoints = new ArrayDeList<PointF>();
			for (PointF point : info.regionPoints.getPreArray()) {
				regionPoints.addFirst(new PointF(point.x, point.y));
			}
			for (PointF point : info.regionPoints.getSufArray()) {
				regionPoints.addLast(new PointF(point.x, point.y));
			}

			pathPoints = new ArrayList<PointF>();
			for (PointF point : info.pathPoints) {
				pathPoints.add(new PointF(point.x, point.y));
			}

			pointInfos = new ArrayList<SoftPath.SoftPointsInfo.PointInfo>();
			if (info.pointInfos.size() > 0) {
				for (PointInfo pointinfo : info.pointInfos) {
					pointInfos.add(new PointInfo(pointinfo.width,
							pointinfo.hight));
				}
			}
		}

		// 偏移，dx,dy
		void translate(float dx, float dy) {
			for (PointF point : pathPoints) {
				point.offset(dx, dy);
			}
			for (PointF point : regionPoints) {
				point.offset(dx, dy);
			}
		}

		// 通过matrix 给pointArray 赋值 源头是points
		void trasform(Matrix matrix, float points[],
				ArrayList<PointF> pointArray) {
			int i = 0;
			for (PointF point : pointArray) {
				points[i++] = point.x;
				points[i++] = point.y;
			}
			matrix.mapPoints(points);
			int k = 0;
			for (PointF point : pointArray) {
				point.x = points[k++];
				point.y = points[k++];
			}
		}

		void trasform(Matrix matrix) {
			float points[] = new float[pathPoints.size() * 2];
			trasform(matrix, points, pathPoints);
			trasform(matrix, points, regionPoints.getPreArray());
			trasform(matrix, points, regionPoints.getSufArray());
		}

		// 判定方向, 1为顺时针，-1为逆时针，0为一条直线
		public int judgeDirection(PointF p1, PointF p2, PointF p3) {
			// log("p1:"+p1+"p2:"+p2+",p3:"+p3);
			float arg = (p2.x - p1.x) * (p3.y - p2.y) - (p2.y - p1.y)
					* (p3.x - p2.x);
			return arg > 0 ? 1 : (arg < 0 ? -1 : 0);
		}

		/**
		 * p1,p2为矩形的两个顶点，p0为对角线交点,2w,2h为矩形的宽高，求解，p2,p4,其中线段p1p2与p3p4平行
		 * **/
		public void countRectPoint(PointF p0, PointF p1, PointF p3, float w,
				float h) {
			float x2, y2, x4, y4;
			float p0_2 = p0.x * p0.x + p0.y * p0.y;
			float p1_2 = p1.x * p1.x + p1.y * p1.y;
			float p3_2 = p3.x * p3.x + p3.y * p3.y;
			float h_2 = h * h;
			float w_2 = w * w;
			float d_2 = h_2 + w_2;

			float k1 = 4 * w_2 - d_2 + p0_2 - p1_2;
			float k2 = d_2 - 4 * h_2 + p3_2 - p0_2;
			float k3 = p0.y - p1.y, k4 = p0.x - p1.x, k5 = p3.x - p0.x, k6 = p3.y
					- p0.y;
			float k7 = d_2 - 4 * w_2 + p3_2 - p0_2;
			float k8 = 4 * h_2 - d_2 + p0_2 - p1_2;

			y2 = (k2 / k5 - k1) / (2 * ((k6 * k4) / k5 - k3));
			x2 = (k1 - 2 * y2 * k3) / (2 * k4);

			y4 = (k8 * k5 - k4 * k7) / (2 * (k3 * k5 - k6 * k4));
			x4 = (k7 - 2 * y4 * k6) / (2 * k5);
			log("计算结果：" + ",x2=" + x2 + ",y2=" + y2 + ",x4=" + x4 + ",y4=" + y4);
		}

		// 依据原始点为对称轴, 已知矩形的两个不相邻顶点P1,P2和中点P0，求另外两个顶点P3,P4
		public void fixPoint(int index, int lastDir, int currentDir,
				PointF currentPoint) {

			PointF lastP = pathPoints.get(index);
			PointF first = pathPoints.get(index - 1);
			PointInfo info = pointInfos.get(index);
			fixPoints.add(lastP);
			log("修复点");
			PointF pGreen = regionPoints.get(0);
			PointF pBlue = regionPoints.get(regionPoints.size() - 1);
			if (lastDir == 4 && currentDir == 2) {
				mdrawPoints.add(new PointF(pGreen.x, pGreen.y));
				log("4-2");

			} else if (lastDir == 2 && currentDir == 4) {// 2-4象限运动，核心是将偏移出去的点拉回来，
				mdrawPoints.add(new PointF(pGreen.x, pGreen.y));
				log("2-4:lasty=" + lastP.y + ",cupy=" + currentPoint.y);
				if (judgeDirection(first, lastP, currentPoint) == -1) {
					log("2-4逆时针");// 逆时针，
					pGreen.y += info.hight;
					pBlue.y -= info.hight;
				} else if (judgeDirection(first, lastP, currentPoint) == 1) {
					log("2-4顺时针");
					pGreen.x += info.width;// good
					pBlue.x -= info.width;
				}

			}

			// left.y=left.y+2*(p0.y-p1.y);
			// right.x=right.x+2*();

		}

		public void add(PointF point, int width, int height) {
			pointInfos.add(new PointInfo(width, height));

			if (pathPoints.size() > 0) {
				PointF last = pathPoints.get(pathPoints.size() - 1);
				int lastDirection = 0;
				if (mDirectionList.size() != 0)
					lastDirection = mDirectionList.get(pathPoints.size() - 1);
				for (int i = 0; i < 2; i++) {
					addPoints[i] = new PointF();
				}
				// log("&&&&&&&&&&width="+width);
				// log("&&&&&&&&&&height="+height);
				if (width > 1) {
					width = width >> 1;// 缩小两倍
				}
				if (height > 1) {
					height = height >> 1;// 缩小两倍
				}
				if (last.x > point.x) {
					if (last.y > point.y) {// 从第一象限 向第四象限移动，
						addPoints[0].x = point.x + width;
						addPoints[1].x = point.x - width;
						addPoints[0].y = point.y - height;
						addPoints[1].y = point.y + height;
						if (regionPoints.size() != 0)
							handleDirection(lastDirection, 1, point,
									regionPoints.getFirst(),
									regionPoints.getLast(), addPoints[0],
									addPoints[1]);
						else
							handleDirection(lastDirection, 1, point, null,
									null, null, null);

					} else {//
						addPoints[0].x = point.x - width;
						addPoints[1].x = point.x + width;
						addPoints[0].y = point.y - height;
						addPoints[1].y = point.y + height;
						if (regionPoints.size() != 0)
							handleDirection(lastDirection, 2, point,
									regionPoints.getFirst(),
									regionPoints.getLast(), addPoints[0],
									addPoints[1]);
						else
							handleDirection(lastDirection, 2, point, null,
									null, null, null);
					}
				} else {
					if (last.y > point.y) {
						addPoints[0].x = point.x + width;
						addPoints[1].x = point.x - width;
						addPoints[0].y = point.y + height;
						addPoints[1].y = point.y - height;
						if (regionPoints.size() != 0)
							handleDirection(lastDirection, 4, point,
									regionPoints.getFirst(),
									regionPoints.getLast(), addPoints[0],
									addPoints[1]);
						else
							handleDirection(lastDirection, 4, point, null,
									null, null, null);
					} else {// 向左下角 第四象限移动
						addPoints[0].x = point.x - width;
						addPoints[1].x = point.x + width;// 点的
						addPoints[0].y = point.y + height;
						addPoints[1].y = point.y - height;// a0,a1分布p点的左下角和右上角
						if (regionPoints.size() != 0)
							handleDirection(lastDirection, 3, point,
									regionPoints.getFirst(),
									regionPoints.getLast(), addPoints[0],
									addPoints[1]);
						else
							handleDirection(lastDirection, 3, point, null,
									null, null, null);
					}
				}

				if (pathPoints.size() == 1) {// 如果这是第二个点,必须补齐第一个点的边线点
					// 下面是向量的运算
					regionPoints.addFirst(new PointF(addPoints[0].x + last.x
							- point.x, addPoints[0].y + last.y - point.y));
					regionPoints.addLast(new PointF(addPoints[1].x + last.x
							- point.x, addPoints[1].y + last.y - point.y));

				}
				// log("边线增加1个");
				regionPoints.addFirst(addPoints[0]);
				regionPoints.addLast(addPoints[1]);
			}
			pathPoints.add(point);
		}

		// 是否有交点
		private boolean judgeX(PointF lastGreen, PointF lastBule,
				PointF currentGreen, PointF currentBule) {
			int a1 = judgeDirection(currentBule, lastBule, currentGreen);
			int a2 = judgeDirection(currentBule, lastBule, lastGreen);

			int b1 = judgeDirection(currentGreen, lastGreen, lastBule);
			int b2 = judgeDirection(currentGreen, lastGreen, currentBule);
			return a1 * a2 < 0 && b1 * b2 < 0;// 都是逆时针和顺时针交错
		}

		// 处理相交点，
		private void fixXpoint(int lastDirection, int currentDirection,
				PointF currentpoint, PointF lastGreen, PointF lastBule,
				PointF currentGreen, PointF currentBule) {
			// 蓝色点移动到和绿色点一样的位置
			{// 海信的解决方案
				lastBule.x = lastGreen.x;
				lastBule.y = lastGreen.y;
			}
			// 我的解决方案

		}

		@SuppressLint("NewApi")
		private void handleDirection(int lastDirection, int currentDirection,
				PointF currentpoint, PointF lastGreen, PointF lastBule,
				PointF currentGreen, PointF currentBule) {
			// log("处理象限：ld="+lastDirection+",cd="+currentDirection);

			if (lastDirection == 0) {
				mDirectionList.add(currentDirection);// 补齐第一个点的direction
				lastDirection = currentDirection;
			}
			if (Math.abs(currentDirection - lastDirection) == 2) {
				fixXpoint(lastDirection, currentDirection, currentpoint,
						lastGreen, lastBule, currentGreen, currentBule);
				// //取出前个点，fix下，，直接处理返回
				// fixPoint(pathPoints.size()-1,lastDirection,currentDirection,currentpoint);
			}

			if (lastBule != null && currentBule != null && lastGreen != null
					&& currentGreen != null) {

				if (judgeX(lastGreen, lastBule, currentGreen, currentBule)) {
					log("相交");
					fixXpoint(lastDirection, currentDirection, currentpoint,
							lastGreen, lastBule, currentGreen, currentBule);
				}
			}

			mDirectionList.add(currentDirection);// 参考象限变化原理.png
		}
	}

	private SoftPointsInfo mPointInfo;
	private RectF mRegin = new RectF();
	private int mActionId;// ??是什么,可能是多指触控的id
	private boolean mIsSelected = false;
	private Paint mPaint;
	private int mId;
	private int penWidthRatio = 1;
	private int maxWidth = 0;
	private boolean isNewMethod = false;
	private float moveDistance = 0;

	public SoftPath(SingleTouchEvent event, Paint paint, int penWidthIndex) {
		mActionId = event.id;
		mPaint = new Paint(paint);
		int penwidth = (event.width * penWidthRatio + event.hight
				* penWidthRatio) / 2;
		mPaint.setStrokeWidth(penwidth);
		mPaint.setStyle(Style.FILL);
		moveDistance = 0;

		penWidthRatio = penWidthIndex + 1;
		// isNewMethod = SystemProperties.getBoolean("sys.newsoft", false);
		mPointInfo = new SoftPointsInfo(event.x, event.y, event.width
				* penWidthRatio, event.hight * penWidthRatio);
	}

	public void log(String info) {
		Log.i("softpath", info);
	}

	public SoftPath(SoftPath path) {
		mActionId = path.mActionId;
		mPaint = path.mPaint;
		penWidthRatio = path.penWidthRatio;
		mPointInfo = new SoftPointsInfo(path.mPointInfo);

	}

	public SoftPath(Paint paint) {
		mPaint = paint;
		mPaint.setStyle(Style.FILL);// add by ljm 设置为封闭， 注释此行查看相交线问题
		mPointInfo = new SoftPointsInfo();
	}

	// @Override
	// public RectF getRegin() {
	// // TODO Auto-generated method stub
	// if(mPointInfo.pathPoints.size()>1){
	// computeBounds(mRegin, true);
	// }else if(mPointInfo.pathPoints.size()==1){
	// PointF point = new PointF();
	// point = mPointInfo.pathPoints.get(0);
	// mRegin.left = point.x - 1;
	// mRegin.right = point.x + 1;
	// mRegin.top = point.y - 1;
	// mRegin.bottom = point.y + 1;
	// }
	// return mRegin;
	// }

	// 移动
	// @Override
	// public DisplayElement move(float dx, float dy) {
	// SoftPath path = new SoftPath(this);
	// path.mIsSelected = mIsSelected;
	// path.maxWidth = maxWidth;
	// path.mPaint = mPaint;
	// Matrix matrix = new Matrix();
	// matrix.setTranslate(dx, dy);
	// transform(matrix, path);
	// matrix.mapRect(path.mRegin, mRegin);
	// path.mPointInfo.translate(dx, dy);
	// return path;
	// }

	// 缩放
	// @Override
	// public DisplayElement scale(float rate, PointF center) {
	// SoftPath path = new SoftPath(this);
	// path.mIsSelected = mIsSelected;
	// path.maxWidth = (int)(maxWidth*rate);
	// path.mPaint = mPaint;
	// Matrix matrix = new Matrix();
	// matrix.setScale(rate, rate, center.x, center.y);
	// transform(matrix, path);
	// matrix.mapRect(path.mRegin, mRegin);
	// path.mPointInfo.trasform(matrix);
	// return path;
	// }

	// //旋转
	// @Override
	// public DisplayElement rotate(float degrees, PointF center) {
	// SoftPath path = new SoftPath(this);
	// path.mIsSelected = mIsSelected;
	// path.maxWidth = maxWidth;
	// path.mPaint = mPaint;
	// Matrix matrix = new Matrix();
	// matrix.setRotate(degrees, center.x, center.y);
	// transform(matrix, path);
	// computeBounds(path.mRegin, false);
	// path.mPointInfo.trasform(matrix);
	// return path;
	// }

	// @Override
	// 绘制方法
	public void draw(Canvas canvas) {
		// TODO Auto-generated method stub
		Paint paint = mPaint;
		Paint selectPaint = new Paint(paint);
		selectPaint.setStrokeWidth(paint.getStrokeWidth() * 1.5f);
		selectPaint.setColor(Color.WHITE);// 被选中虚线框
		if (mPointInfo.pathPoints != null && mPointInfo.pathPoints.size() > 1) {
			if (mIsSelected) {
				canvas.drawPath(this, selectPaint);
			}
			canvas.drawPath(this, paint);
		} else if (mPointInfo.pathPoints != null
				&& mPointInfo.pathPoints.size() == 1) {// 绘制单个点，
			Paint pointPaint = new Paint(paint);
			pointPaint.setStrokeCap(Paint.Cap.ROUND);
			pointPaint.setStyle(Paint.Style.FILL);
			Paint selectpointPaint = new Paint(pointPaint);
			selectpointPaint.setColor(Color.WHITE);
			selectpointPaint.setStrokeWidth(pointPaint.getStrokeWidth() * 1.5f);
			PointF point = new PointF();
			point = mPointInfo.pathPoints.get(0);
			if (mIsSelected) {
				canvas.drawCircle(point.x, point.y,
						selectpointPaint.getStrokeWidth() / 2, selectpointPaint);
			}
			canvas.drawCircle(point.x, point.y,
					pointPaint.getStrokeWidth() / 2, pointPaint);

		}
		{//以下为绘制参考点
//
//			for (int i = 0; i < mPointInfo.pathPoints.size(); i++) {
//				int x = (int) mPointInfo.pathPoints.get(i).x;
//				int y = (int) mPointInfo.pathPoints.get(i).y;
//
//				Paint pt = new Paint();
//				pt.setStrokeWidth(10);
//				pt.setColor(Color.BLACK);
//				// pt.setARGB(255, 255, 255,0+i);
//				canvas.drawCircle(x, y, pt.getStrokeWidth() / 2, pt);
//			}
//
//			// 绘制左右边线点
//			for (int i = 0; i < mPointInfo.regionPoints.size() / 2; i++) {// 第一象限中
//																			// 左侧点
//				int x = (int) mPointInfo.regionPoints.get(i).x;
//				int y = (int) mPointInfo.regionPoints.get(i).y;
//
//				Paint pt = new Paint();
//				pt.setStrokeWidth(10);
//				pt.setColor(Color.GREEN);
//				// pt.setARGB(255, 255, 255,0+i);
//				canvas.drawCircle(x, y, pt.getStrokeWidth() / 2, pt);
//			}
//
//			for (int i = 0; i < mPointInfo.fixPoints.size(); i++) {
//				int x = (int) mPointInfo.fixPoints.get(i).x;
//				int y = (int) mPointInfo.fixPoints.get(i).y;
//
//				Paint pt = new Paint();
//				pt.setStrokeWidth(15);
//				pt.setColor(Color.YELLOW);
//				// pt.setARGB(255, 255, 255,0+i);
//				canvas.drawCircle(x, y, pt.getStrokeWidth() / 2, pt);
//			}
//
//			for (int i = 0; i < mPointInfo.mdrawPoints.size(); i++) {
//				int x = (int) mPointInfo.mdrawPoints.get(i).x;
//				int y = (int) mPointInfo.mdrawPoints.get(i).y;
//
//				Paint pt = new Paint();
//				pt.setStrokeWidth(8);
//				pt.setColor(Color.RED);
//				pt.setStyle(Paint.Style.STROKE);
//				canvas.drawCircle(x, y, pt.getStrokeWidth() / 2, pt);
//			}
//
//			for (int i = 0; i < mPointInfo.regionPoints.size() / 2; i++) {
//				int x = (int) mPointInfo.regionPoints
//						.get(mPointInfo.regionPoints.size() - i - 1).x;
//				int y = (int) mPointInfo.regionPoints
//						.get(mPointInfo.regionPoints.size() - i - 1).y;
//
//				Paint pt = new Paint();
//				pt.setStrokeWidth(10);
//				pt.setColor(Color.BLUE);
//				// pt.setARGB(255, 255, 0,255-i);
//				canvas.drawCircle(x, y, pt.getStrokeWidth() / 2, pt);
//			}
		}

	}

	// @Override
	// public int getActionId() {
	// return mActionId;
	// }

	// @Override
	// public void offset(PointF targetPoint) {
	// Matrix matrix = new Matrix();
	// matrix.setTranslate(targetPoint.x, targetPoint.y);
	// transform(matrix, this);
	// matrix.mapRect(mRegin);
	// mPointInfo.translate(targetPoint.x, targetPoint.y);
	// }

	// @Override
	// public DisplayElement delete() {
	// SoftPath path = new SoftPath(mPaint);
	// path.mActionId = mActionId;
	// path.mRegin = new RectF(0, 0, 0, 0);
	// path.maxWidth = 0;
	// return path;
	// }

	public ArrayList<SoftPath> eraser(Region region) {
		ArrayList<SoftPath> tempSplitedPaths = new ArrayList<SoftPath>();
		SoftPath path;
		tempSplitedPaths.add(new SoftPath(mPaint));

		return tempSplitedPaths;
	}

	// @Override
	// public DisplayElement select(boolean selectFlag) {
	// setselectedFlag(selectFlag);
	// mIsSelected = selectFlag;
	// SoftPath element = (SoftPath)clone();
	// element.mIsSelected = selectFlag;
	// return element;
	// }

	// 设置被选中状态
	public void setselectedFlag(boolean flag) {
		mIsSelected = flag;
	}

	public int getId() {
		return mId;
	}

	public Paint getPaint() {
		return mPaint;
	}

	public int getMaxWidth() {
		return maxWidth;
	}

	public boolean touchEvent(SingleTouchEvent event) {
		if (isNewMethod) {
			return formPathnew(event.x, event.y, event.width * penWidthRatio,
					event.hight * penWidthRatio);
		} else {
			return formPath(event.x, event.y, event.width * penWidthRatio,
					event.hight * penWidthRatio);
		}

	}

	private float lastPointX = 0;
	private float lastPointY = 0;

	private boolean formPathnew(float x, float y, int width, int hight) {
		// 暂时使用此方法，实际可使用总控的方式
		int softPenWidthThreshHold = penWidthRatio * 60;
		if (width > softPenWidthThreshHold) {// 宽高限制
			width = softPenWidthThreshHold;
		} else if (hight > softPenWidthThreshHold) {
			hight = softPenWidthThreshHold;
		}

		if (mPointInfo.pathPoints.size() > 0) {// 最后一个移动的相对距离太小了，不绘制
			lastPointX = mPointInfo.pathPoints
					.get(mPointInfo.pathPoints.size() - 1).x;
			lastPointY = mPointInfo.pathPoints
					.get(mPointInfo.pathPoints.size() - 1).y;
			if (Math.abs(x - lastPointX) < 0.001
					&& Math.abs(y - lastPointY) < 0.001) {
				return false;
			}
		}
		int currentWidth = width;
		int currentHight = hight;
		// if(mPointInfo.pathPoints.size()>0){
		// int lastWidth =
		// mPointInfo.pointInfos.get(mPointInfo.pointInfos.size()-1).width;
		// int lastHight =
		// mPointInfo.pointInfos.get(mPointInfo.pointInfos.size()-1).hight;
		//
		// currentWidth = (lastWidth+width)/2;
		// currentHight = (lastHight+hight)/2;
		// }

		mPointInfo.add(new PointF(x, y), currentWidth, currentHight);
		if (maxWidth < currentWidth) {
			maxWidth = currentWidth;
		} else if (maxWidth < currentHight) {
			maxWidth = currentHight;
		}
		// reset();

		if (mPointInfo.pathPoints.size() > 2) {// 收集到2个点以上
			ArrayList<PointF> currentPointsList = new ArrayList<PointF>();
			PointF lastlastPrePoint = mPointInfo.regionPoints.getPreArray()
					.get(mPointInfo.pointInfos.size() - 3);
			PointF lastPrePoint = mPointInfo.regionPoints.getPreArray().get(
					mPointInfo.pointInfos.size() - 2);
			PointF nextPrePoint = mPointInfo.regionPoints.getPreArray().get(
					mPointInfo.pointInfos.size() - 1);
			PointF nextSufPoint = mPointInfo.regionPoints.getSufArray().get(
					mPointInfo.pointInfos.size() - 1);
			PointF lastSufPoint = mPointInfo.regionPoints.getSufArray().get(
					mPointInfo.pointInfos.size() - 2);
			PointF lastlastSufPoint = mPointInfo.regionPoints.getSufArray()
					.get(mPointInfo.pointInfos.size() - 3);
			if (lastlastPrePoint == null || lastPrePoint == null
					|| nextPrePoint == null || nextSufPoint == null
					|| lastSufPoint == null || lastlastSufPoint == null) {

				return true;
			}
			currentPointsList.add(nextPrePoint);// 左侧右下角点为绘制起点，方向为几字型，

			currentPointsList.add(lastPrePoint);

			currentPointsList.add(lastlastPrePoint);

			currentPointsList.add(lastlastSufPoint);
			currentPointsList.add(lastSufPoint);
			currentPointsList.add(nextSufPoint);// 边线点，

			if (currentPointsList.size() > 0) {// 绘制一个类似两边为贝塞尔曲线拟合的梯形，n个梯形连接就是笔锋。
				moveTo(currentPointsList.get(0).x, currentPointsList.get(0).y);
				PointF lastPointF = currentPointsList.get(0);
				for (int i = 1; i < currentPointsList.size(); i++) {
					PointF nextPoint = currentPointsList.get(i);
					float x1 = lastPointF.x;
					float y1 = lastPointF.y;
					float x2 = nextPoint.x;
					float y2 = nextPoint.y;
					float min_x = Math.abs(x2 - x1);
					float min_y = Math.abs(y2 - y1);
					if ((min_x < 0.1F) && (min_y < 0.1F)) {
						// 如果两个点间距很小则moveTo
						if ((min_x > 0.0001F) || (min_y > 0.0001F)) {
							moveTo(x2, y2);
							lastPointF = nextPoint;
						}
						continue;
					}
					if (Math.sqrt(min_x * min_x + min_y * min_y) > MAX_DIS) {
						super.quadTo(x1, y1, x2, y2);
					} else {
						super.quadTo(x1, y1, (x2 + x1) / 2, (y2 + y1) / 2);
					}
					lastPointF = nextPoint;
				}
				float min_x = Math.abs(currentPointsList.get(0).x
						- lastPointF.x);
				float min_y = Math.abs(currentPointsList.get(0).y
						- lastPointF.y);
				if ((min_x > 0.5F) || (min_y > 0.5F)) {
					if (Math.sqrt(min_x * min_x + min_y * min_y) > MAX_DIS) {
						super.quadTo(lastPointF.x, lastPointF.y,
								currentPointsList.get(0).x,
								currentPointsList.get(0).y);
					} else {
						super.quadTo(
								lastPointF.x,
								lastPointF.y,
								(currentPointsList.get(0).x + lastPointF.x) / 2,
								(currentPointsList.get(0).y + lastPointF.y) / 2);
					}
				} else {
					if ((min_x > 0.0001F) || (min_y > 0.0001F)) {
						moveTo(currentPointsList.get(0).x,
								currentPointsList.get(0).y);
					}
				}
			}
		} else if (mPointInfo.pathPoints.size() == 2) {
			ArrayList<PointF> currentPointsList = new ArrayList<PointF>();
			PointF lastPrePoint = mPointInfo.regionPoints.getPreArray().get(0);
			PointF nextPrePoint = mPointInfo.regionPoints.getPreArray().get(1);
			PointF nextSufPoint = mPointInfo.regionPoints.getSufArray().get(1);
			PointF lastSufPoint = mPointInfo.regionPoints.getSufArray().get(0);
			if (lastPrePoint == null || nextPrePoint == null
					|| nextSufPoint == null || lastSufPoint == null) {
				return true;
			}
			currentPointsList.add(nextPrePoint);
			currentPointsList.add(lastPrePoint);
			currentPointsList.add(lastSufPoint);
			currentPointsList.add(nextSufPoint);

			if (currentPointsList.size() > 0) {
				moveTo(currentPointsList.get(0).x, currentPointsList.get(0).y);
				PointF lastPointF = currentPointsList.get(0);
				for (int i = 1; i < currentPointsList.size(); i++) {
					PointF nextPoint = currentPointsList.get(i);
					float x1 = lastPointF.x;
					float y1 = lastPointF.y;
					float x2 = nextPoint.x;
					float y2 = nextPoint.y;
					float min_x = Math.abs(x2 - x1);
					float min_y = Math.abs(y2 - y1);
					if ((min_x < 0.001F) && (min_y < 0.001F)) {
						continue;
					}
					super.quadTo(x1, y1, (x2 + x1) / 2, (y2 + y1) / 2);
					lastPointF = nextPoint;
				}
				float min_x = Math.abs(currentPointsList.get(0).x
						- lastPointF.x);
				float min_y = Math.abs(currentPointsList.get(0).y
						- lastPointF.y);
				if ((min_x > 0.001F) || (min_y > 0.001F)) {
					super.quadTo(lastPointF.x, lastPointF.y,
							(currentPointsList.get(0).x + lastPointF.x) / 2,
							(currentPointsList.get(0).y + lastPointF.y) / 2);
				} else {
					moveTo(currentPointsList.get(0).x,
							currentPointsList.get(0).y);
				}
			}
		} else if (mPointInfo.pathPoints.size() == 1) {
			moveTo(mPointInfo.pathPoints.get(0).x,
					mPointInfo.pathPoints.get(0).y);
		}
		close();
		return false;
	}

	private boolean formPathByCache(ArrayDeList<PointF> regionPoints) {
		reset();
		Iterator<PointF> iter = regionPoints.iterator();
		PointF firstPoint = iter.next();
		if (firstPoint == null) {
			return true;
		}
		moveTo(firstPoint.x, firstPoint.y);
		PointF lastPointF = firstPoint;
		while (iter.hasNext()) {
			PointF nextPoint = iter.next();

			float x1 = lastPointF.x;
			float y1 = lastPointF.y;
			float x2 = nextPoint.x;
			float y2 = nextPoint.y;
			float min_x = Math.abs(x2 - x1);
			float min_y = Math.abs(y2 - y1);
			if ((min_x < 3.0F) && (min_y < 3.0F)) {
				continue;
			}
			if (Math.sqrt(min_x * min_x + min_y * min_y) > MAX_DIS) {
				super.quadTo(x1, y1, x2, y2);
			} else {
				super.quadTo(x1, y1, (x2 + x1) / 2, (y2 + y1) / 2);
			}
			lastPointF = nextPoint;
		}

		float min_x = Math.abs(firstPoint.x - lastPointF.x);
		float min_y = Math.abs(firstPoint.y - lastPointF.y);
		if ((min_x > 3.0F) || (min_y > 3.0F)) {
			if (Math.sqrt(min_x * min_x + min_y * min_y) > MAX_DIS) {
				super.quadTo(lastPointF.x, lastPointF.y, firstPoint.x,
						firstPoint.y);
			} else {
				super.quadTo(lastPointF.x, lastPointF.y,
						(firstPoint.x + lastPointF.x) / 2,
						(firstPoint.y + lastPointF.y) / 2);
			}
		}

		close();
		return false;
	}

	// ??现在使用的是这个方法吗？
	private boolean formPath(float x, float y, int width, int hight) {
		// log("输入点：x="+x+",y="+y+",w="+width+",h="+hight);
		// 暂时使用此方法，实际可使用总控的方式
		int softPenWidthThreshHold = penWidthRatio * 41;
		if (width > softPenWidthThreshHold) {
			width = softPenWidthThreshHold;
		} else if (hight > softPenWidthThreshHold) {
			hight = softPenWidthThreshHold;
		}
		if (mPointInfo.pathPoints.size() > 1) {
			mPaint.setStrokeWidth(1);// 无用
		}
		if (mPointInfo.pathPoints.size() > 0) {
			lastPointX = mPointInfo.pathPoints
					.get(mPointInfo.pathPoints.size() - 1).x;
			lastPointY = mPointInfo.pathPoints
					.get(mPointInfo.pathPoints.size() - 1).y;
			if (Math.abs(x - lastPointX) < 3 && Math.abs(y - lastPointY) < 3) {
				return false;// 间距太小直接舍去
			}
			if (mPointInfo.pathPoints.size() > 2) {// 无用
				float dx = (float) (x - lastPointX);
				float dy = (float) (y - lastPointY);
				float distance = (float) Math.sqrt(dx * dx + dy * dy);
				moveDistance += distance;// ??数据哪里使用了呢
			}
		}
		int currentWidth = width;
		int currentHight = hight;

		mPointInfo.add(new PointF(x, y), currentWidth, currentHight);
		if (maxWidth < currentWidth) {// 选择使用
			maxWidth = currentWidth;
		} else if (maxWidth < currentHight) {
			maxWidth = currentHight;
		}
		reset();

		if (mPointInfo.regionPoints.size() == 0)
			return true; // add by ljm 防止崩溃

		Iterator<PointF> iter = mPointInfo.regionPoints.iterator();// ??这个集合regionPoints
																	// 有增无减，反复绘制？
		PointF firstPoint = iter.next();
		if (firstPoint == null) {
			return true;
		}
		moveTo(firstPoint.x, firstPoint.y);
		PointF lastPointF = firstPoint;
		while (iter.hasNext()) {
			PointF nextPoint = iter.next();

			float x1 = lastPointF.x;
			float y1 = lastPointF.y;
			float x2 = nextPoint.x;
			float y2 = nextPoint.y;
			float min_x = Math.abs(x2 - x1);
			float min_y = Math.abs(y2 - y1);
			if ((min_x < 3.0F) && (min_y < 3.0F)) {
				continue;// 两个点间距过小，
			}
			double dis = Math.sqrt(min_x * min_x + min_y * min_y);
			// log("连续两个点的距离："+dis);
			if (dis > MAX_DIS) {
				super.quadTo(x1, y1, x2, y2);
			} else {
				super.quadTo(x1, y1, (x2 + x1) / 2, (y2 + y1) / 2); // 速度慢了
																	// 以中点为终点，
			}
			lastPointF = nextPoint;
		}
		float min_x = Math.abs(firstPoint.x - lastPointF.x);
		float min_y = Math.abs(firstPoint.y - lastPointF.y);
		if ((min_x > 3.0F) || (min_y > 3.0F)) {
			if (Math.sqrt(min_x * min_x + min_y * min_y) > MAX_DIS) {// 封闭曲线
				super.quadTo(lastPointF.x, lastPointF.y, firstPoint.x,
						firstPoint.y);
			} else {
				super.quadTo(lastPointF.x, lastPointF.y,
						(firstPoint.x + lastPointF.x) / 2,
						(firstPoint.y + lastPointF.y) / 2); // 速度慢了 以中点为终点，
			}
		}

		close();
		return false;
	}

	// @Override
	// public ArrayList<PointF> getPoints() {
	// // TODO Auto-generated method stub
	// return mPointInfo.pathPoints;
	// }

	// @Override
	// public PenStyleMode getPenStyleMode() {
	// // TODO Auto-generated method stub
	// return PenStyleMode.PENSTYLE_SOFT;
	// }

	// @Override
	// public int getPenWidthIndex() {
	// // TODO Auto-generated method stub
	// return penWidthRatio-1;
	// }

	// @Override
	// public float getMoveDistance() {
	// // TODO Auto-generated method stub
	// return moveDistance;
	// }

}
