/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.apis.graphics;

import com.example.android.apis.ljm.SoftPath;
import com.example.android.apis.ljm.SingleTouchEvent;

import android.content.Context;
import android.graphics.*;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;

public class FingerPaint extends GraphicsActivity
        implements ColorPickerDialog.OnColorChangedListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(new MyView(this));


        mEmboss = new EmbossMaskFilter(new float[] { 1, 1, 1 },
                                       0.4f, 6, 3.5f);

        mBlur = new BlurMaskFilter(8, BlurMaskFilter.Blur.NORMAL);
    }

    private Paint       mPaint;
    private MaskFilter  mEmboss;
    private MaskFilter  mBlur;

    public void colorChanged(int color) {
        mPaint.setColor(color);
    }

    public class MyView extends View {

        private static final float MINP = 0.25f;
        private static final float MAXP = 0.75f;

        private Bitmap  mBitmap;
        private Canvas  mCanvas;//临时画布
        private Path    mPath;
        private SoftPath mps;
        private Paint   mBitmapPaint;

        public MyView(Context c) {
            super(c);
            
            mPaint = new Paint();
            mPaint.setAntiAlias(true);
            mPaint.setDither(true);
            mPaint.setColor(0xFFFF0000);
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setStrokeJoin(Paint.Join.ROUND);
            mPaint.setStrokeCap(Paint.Cap.ROUND);
            mPaint.setStrokeWidth(12);
            
            mPath = new Path();
            mps=new SoftPath(mPaint);
            mBitmapPaint = new Paint(Paint.DITHER_FLAG);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            mBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            mCanvas = new Canvas(mBitmap);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.drawColor(0xFFAAAAAA);//如果不设置则为背景为黑色
           canvas.drawBitmap(mBitmap, 0, 0, mBitmapPaint);
            //将历史笔迹绘制到view的canvas上，
//            canvas.drawPath(mPath, mPaint);
           if(mps!=null)
            mps.draw(canvas);
        }

        private float mX, mY;
        private static final float TOUCH_TOLERANCE = 4;

        private void touch_start(float x, float y,SingleTouchEvent stouch) {
//            mPath.reset();
//            mPath.moveTo(x, y);
        	if(mps==null)
        		mps=new SoftPath(mPaint);
            mps.reset();
            mps.touchEvent(stouch);
            
            mX = x;
            mY = y;
        }
        private void touch_move(float x, float y,SingleTouchEvent stouch) {
            float dx = Math.abs(x - mX);
            float dy = Math.abs(y - mY);
            if (dx >= TOUCH_TOLERANCE || dy >= TOUCH_TOLERANCE) {
//                mPath.quadTo(mX, mY, (x + mX)/2, (y + mY)/2);
				if(mps!=null)
					mps.touchEvent(stouch);//add by ljm
			
                mX = x;
                mY = y;
            }
        }
        private void touch_up(SingleTouchEvent stouch) {
        	mps.lineTo(mX,mY);
        	mCanvas.drawPath(mps, mPaint);
        	mps=null;
        	
        	//            mPath.lineTo(mX, mY);
//            // commit the path to our offscreen
//            mCanvas.drawPath(mPath, mPaint);//会绘制到bitmap上，最后会将bitmap绘制到view的Canvas上
//            // kill this so we don't double draw
//            mPath.reset(); //和touch_start 中的重复 可以注释
            
            
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX();
            float y = event.getY();
        	SingleTouchEvent stouch=new SingleTouchEvent(event);//规
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    touch_start(x, y,stouch);
                    invalidate();
                    break;
                case MotionEvent.ACTION_MOVE:
                    touch_move(x, y,stouch);
                    invalidate();
                    break;
                case MotionEvent.ACTION_UP:
                    touch_up(stouch);
//                    invalidate(); 可以注释
                    break;
            }
            return true;
        }
    }

    private static final int COLOR_MENU_ID = Menu.FIRST;
    private static final int EMBOSS_MENU_ID = Menu.FIRST + 1;
    private static final int BLUR_MENU_ID = Menu.FIRST + 2;
    private static final int ERASE_MENU_ID = Menu.FIRST + 3;
    private static final int SRCATOP_MENU_ID = Menu.FIRST + 4;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        menu.add(0, COLOR_MENU_ID, 0, "Color").setShortcut('3', 'c');
        menu.add(0, EMBOSS_MENU_ID, 0, "Emboss").setShortcut('4', 's');
        menu.add(0, BLUR_MENU_ID, 0, "Blur").setShortcut('5', 'z');
        menu.add(0, ERASE_MENU_ID, 0, "Erase").setShortcut('5', 'z');
        menu.add(0, SRCATOP_MENU_ID, 0, "SrcATop").setShortcut('5', 'z');

        /****   Is this the mechanism to extend with filter effects?
        Intent intent = new Intent(null, getIntent().getData());
        intent.addCategory(Intent.CATEGORY_ALTERNATIVE);
        menu.addIntentOptions(
                              Menu.ALTERNATIVE, 0,
                              new ComponentName(this, NotesList.class),
                              null, intent, 0, null);
        *****/
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        mPaint.setXfermode(null);
        mPaint.setAlpha(0xFF);

        switch (item.getItemId()) {
            case COLOR_MENU_ID:
                new ColorPickerDialog(this, this, mPaint.getColor()).show();
                return true;
            case EMBOSS_MENU_ID:
                if (mPaint.getMaskFilter() != mEmboss) {
                    mPaint.setMaskFilter(mEmboss);
                } else {
                    mPaint.setMaskFilter(null);
                }
                return true;
            case BLUR_MENU_ID:
                if (mPaint.getMaskFilter() != mBlur) {
                    mPaint.setMaskFilter(mBlur);
                } else {
                    mPaint.setMaskFilter(null);
                }
                return true;
            case ERASE_MENU_ID:
                //设定为清除模式，会留下黑色的线条
            	mPaint.setXfermode(new PorterDuffXfermode(
                                                        PorterDuff.Mode.CLEAR));
                return true;
            case SRCATOP_MENU_ID:
                mPaint.setXfermode(new PorterDuffXfermode(
                                                    PorterDuff.Mode.SRC_ATOP));
                mPaint.setAlpha(0x80);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
