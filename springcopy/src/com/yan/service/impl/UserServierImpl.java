package com.yan.service.impl;

import com.yan.annotation.YanService;
import com.yan.service.UserServier;

@YanService
public class UserServierImpl implements UserServier {

	@Override
	public void add(String name) {
		System.out.println("==>service add="+name);
	}

}
