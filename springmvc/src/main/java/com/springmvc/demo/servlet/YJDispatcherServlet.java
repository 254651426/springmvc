package com.springmvc.demo.servlet;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.springmvc.demo.annotation.YJAutowired;
import com.springmvc.demo.annotation.YJController;
import com.springmvc.demo.annotation.YJRequestMapping;
import com.springmvc.demo.annotation.YJRequestParam;
import com.springmvc.demo.annotation.YJService;

public class YJDispatcherServlet extends HttpServlet {
	private Properties contextConfig = new Properties();
	private List<String> classNames = new ArrayList<String>();
	private Map<String, Object> ioc = new HashMap<String, Object>();
	private List<Handler> handlerMapping = new ArrayList<Handler>();

	@Override
	public void init(ServletConfig config) throws ServletException {
		// TODO Auto-generated method stub
		// 加载配置文件
		doLoadConfig(config.getInitParameter("contextConfigLocation"));

		// 解析配置文件
		doScanner(contextConfig.getProperty("scanPackage"));

		// 初始化所有相关类的实例
		doInstance();

		// 依赖注入
		doAutoWired();

		// 构造HandlerMapping
		initHandlerMapping();

	}

	private void initHandlerMapping() {
		if (ioc.isEmpty())
			return;
		for (Map.Entry<String, Object> entry : ioc.entrySet()) {
			Class<?> clazz = entry.getValue().getClass();
			if (!clazz.isAnnotationPresent(YJController.class)) {
				continue;
			}
			String baseUrl = "";
			if (clazz.isAnnotationPresent(YJRequestMapping.class)) {
				YJRequestMapping requestMapping = clazz.getAnnotation(YJRequestMapping.class);
				baseUrl = requestMapping.value();
			}
			Method[] methods = clazz.getMethods();
			for (Method method : methods) {
				if (!method.isAnnotationPresent(YJRequestMapping.class)) {
					continue;
				}
				YJRequestMapping requestMapping = method.getAnnotation(YJRequestMapping.class);
				String url = (baseUrl + requestMapping.value()).replaceAll("/+", "/");
				Pattern pattern = Pattern.compile(url);
				handlerMapping.add(new Handler(pattern, entry.getValue(), method));
				System.out.println("mapped:" + url + "=>" + method);
			}
		}

	}

	private void doAutoWired() {
		if (ioc.isEmpty())
			return;
		for (Map.Entry<String, Object> entry : ioc.entrySet()) {
			// 依赖注入->给加了XXAutowired注解的字段赋值
			Field[] fields = entry.getValue().getClass().getDeclaredFields();
			for (Field field : fields) {
				if (!field.isAnnotationPresent(YJAutowired.class)) {
					continue;
				}
				YJAutowired autowired = field.getAnnotation(YJAutowired.class);
				String beanName = autowired.value();
				if ("".equals(beanName)) {
					beanName = field.getType().getName();
				}
				field.setAccessible(true);
				try {
					field.set(entry.getValue(), ioc.get(beanName));
				} catch (IllegalAccessException e) {
					e.printStackTrace();
					continue;
				}
			}
		}

	}

	private void doInstance() {
		if (classNames.isEmpty())
			return;
		try {
			for (String className : classNames) {
				Class<?> clazz = Class.forName(className);
				if (clazz.isAnnotationPresent(YJController.class)) {
					String beanName = lowerFirstCase(clazz.getSimpleName());
					ioc.put(beanName, clazz.newInstance());
				} else if (clazz.isAnnotationPresent(YJService.class)) {

					YJService service = clazz.getAnnotation(YJService.class);
					String beanName = service.value();
					if ("".equals(beanName)) {
						beanName = lowerFirstCase(clazz.getSimpleName());
					}
					Object instance = clazz.newInstance();
					ioc.put(beanName, instance);
					Class<?>[] interfaces = clazz.getInterfaces();
					for (Class<?> i : interfaces) {
						ioc.put(i.getName(), instance);
					}
				} else {
					continue;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private String lowerFirstCase(String simpleName) {
		// TODO Auto-generated method stub
		char[] ss = simpleName.toCharArray();
		ss[0] += 32;
		return ss.toString();
	}

	private void doLoadConfig(String location) {
		InputStream input = this.getClass().getClassLoader().getResourceAsStream(location);
		try {
			contextConfig.load(input);
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (input != null) {
				try {
					input.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	private void doScanner(String packageName) {
		URL resource = this.getClass().getClassLoader().getResource("/" + packageName.replaceAll("\\.", "/"));
		File classDir = new File(resource.getFile());
		for (File classFile : classDir.listFiles()) {
			if (classFile.isDirectory()) {
				doScanner(packageName + "." + classFile.getName());
			} else {
				String className = (packageName + "." + classFile.getName()).replace(".class", "");
				classNames.add(className);
			}
		}

		for (int i = 0; i < classNames.size(); i++) {
			System.out.println(classNames.get(i));
		}
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
		this.doPost(req, res);
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
		try {
			doDispatcher(req, res);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private void doDispatcher(HttpServletRequest req, HttpServletResponse res) throws Exception {
		try {
			Handler handler = getHandler(req);
			if (handler == null) {
				res.getWriter().write("404 not found.");
				return;
			}
			Class<?>[] paramTypes = handler.method.getParameterTypes();
			Object[] paramValues = new Object[paramTypes.length];
			Map<String, String[]> params = req.getParameterMap();
			for (Entry<String, String[]> param : params.entrySet()) {
				String value = Arrays.toString(param.getValue()).replaceAll("\\[|\\]", "");
				if (!handler.paramIndexMapping.containsKey(param.getKey())) {
					continue;
				}
				int index = handler.paramIndexMapping.get(param.getKey());
				paramValues[index] = convert(paramTypes[index], value);
			}
			int reqIndex = handler.paramIndexMapping.get(HttpServletRequest.class.getName());
			paramValues[reqIndex] = req;
			int resIndex = handler.paramIndexMapping.get(HttpServletResponse.class.getName());
			paramValues[resIndex] = res;
			handler.method.invoke(handler.controller, paramValues);
		} catch (Exception e) {
			e.printStackTrace();
		}
		String url = req.getRequestURI();
		String contextPath = req.getContextPath();
		url = url.replace(contextPath, "").replaceAll("/+", "/");

	}

	private Object convert(Class<?> type, String value) {
		if (Integer.class == type) {
			return Integer.valueOf(value);
		}
		return value;
	}

	private Handler getHandler(HttpServletRequest req) {
		if (handlerMapping.isEmpty()) {
			return null;
		}
		String url = req.getRequestURI();
		String contextPath = req.getContextPath();
		url = url.replace(contextPath, "").replaceAll("/+", "/");
		for (Handler handler : handlerMapping) {
			Matcher matcher = handler.pattern.matcher(url);
			if (!matcher.matches()) {
				continue;
			}
			return handler;
		}
		return null;
	}

	private class Handler {
		protected Object controller;
		protected Method method;
		protected Pattern pattern;
		protected Map<String, Integer> paramIndexMapping;

		protected Handler(Pattern pattern, Object controller, Method method) {
			this.pattern = pattern;
			this.controller = controller;
			this.method = method;
			paramIndexMapping = new HashMap<String, Integer>();
			putParamIndexMapping(method);
		}

		private void putParamIndexMapping(Method method) {
			Annotation[][] pa = method.getParameterAnnotations();
			for (int i = 0; i < pa.length; i++) {
				for (Annotation a : pa[i]) {
					if (a instanceof YJRequestParam) {
						String paramName = ((YJRequestParam) a).value();
						if (!"".equals(paramName)) {
							paramIndexMapping.put(paramName, i);
						}
					}
				}
			}
			Class<?>[] paramTypes = method.getParameterTypes();
			for (int i = 0; i < paramTypes.length; i++) {
				Class<?> type = paramTypes[i];
				if (type == HttpServletRequest.class || type == HttpServletResponse.class) {
					paramIndexMapping.put(type.getName(), i);
				}
			}
		}
	}

}