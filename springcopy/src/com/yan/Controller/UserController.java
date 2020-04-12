package com.yan.Controller;

import com.yan.annotation.YanAutowired;
import com.yan.annotation.YanController;
import com.yan.annotation.YanRequestMapping;
import com.yan.annotation.YanRequestParam;
import com.yan.service.UserServier;

@YanController
@YanRequestMapping("/user/")
public class UserController {
	
	@YanAutowired
	UserServier userServier;
	
	@YanRequestMapping("add")
	public String add(@YanRequestParam String name){
		//System.out.println("456789");
		userServier.add(name);
		return name;
	}
	
	
	@YanRequestMapping("edit")
	public String exit(@YanRequestParam String name){
	
		return name;
	}
	
}
