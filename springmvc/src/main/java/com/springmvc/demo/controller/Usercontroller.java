package com.springmvc.demo.controller;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.springmvc.demo.annotation.YJAutowired;
import com.springmvc.demo.annotation.YJController;
import com.springmvc.demo.annotation.YJRequestMapping;
import com.springmvc.demo.annotation.YJRequestParam;
import com.springmvc.demo.service.UserService;

@YJController
public class Usercontroller {

	@YJAutowired
	private UserService userService;

	@YJRequestMapping(value = "/show")
	public void show(HttpServletRequest request, HttpServletResponse response, @YJRequestParam("name") String name)
			throws IOException {
		String res = userService.get(name);
		System.out.println(name + "=>" + res);
		response.getWriter().write(res);

	}

}
