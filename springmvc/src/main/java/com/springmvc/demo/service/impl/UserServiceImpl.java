package com.springmvc.demo.service.impl;

import com.springmvc.demo.annotation.YJService;
import com.springmvc.demo.service.UserService;

@YJService
public class UserServiceImpl implements UserService {

	public String get(String name) {

		return "hello world"+name;
	}

}
