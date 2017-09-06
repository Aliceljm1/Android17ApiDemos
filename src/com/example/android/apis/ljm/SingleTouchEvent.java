package com.example.android.apis.ljm;

import android.view.MotionEvent;

public class SingleTouchEvent {

	public int width,hight,id;
	public float x,y;
	
	public SingleTouchEvent(MotionEvent event){
		
		width=(int) ((int) event.getTouchMajor()*1.0);//和硬件相关，应该是需要乘系数，*1.5, windows直接使用宽高，
		hight=(int) ((int) event.getTouchMinor()*1.0);
		if(width==0)
			width=5;
		if(hight==0)
			hight=5;
		
		x=event.getX();
		y=event.getY();
		 int point_index = event.getActionIndex();
        id = event.getPointerId(point_index);
	}
	
	
}
