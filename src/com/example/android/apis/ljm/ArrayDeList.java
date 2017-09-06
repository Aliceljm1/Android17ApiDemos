package com.example.android.apis.ljm;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * @描述         双向链表
 * @项目名称      Java_DataStruct
 * @包名         com.struct.linklist
 * @类名         LinkList
 * @author      chenlin
 * @date        2010年6月26日 上午8:00:28
 * @version     1.0 
 */

public class ArrayDeList<T> extends LinkedList<T> {
	
	//
	public ArrayList<T> getPreArray()
	{
		return (ArrayList<T>) this.subList(0, this.size()/2);
	}
	
	public ArrayList<T> getSufArray()
	{
		return (ArrayList<T>) this.subList(this.size()/2, this.size());
	}
	
	

}