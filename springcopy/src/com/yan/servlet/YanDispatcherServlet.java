package com.yan.servlet;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebInitParam;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.yan.annotation.YanAutowired;
import com.yan.annotation.YanController;
import com.yan.annotation.YanRequestMapping;
import com.yan.annotation.YanService;

/**
 * 
 * @author yan.zhang
 *
 */
@WebServlet(urlPatterns= {"/"},name = "123",loadOnStartup = 1,initParams = {@WebInitParam(name="contextConfigLocation",value="application.properties")})
public class YanDispatcherServlet extends HttpServlet{

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	Properties contextConfig=new Properties();
	List<String> classNameList=new ArrayList<String>();
	Map<String, Object> ioc=new HashMap<String, Object>();
	Map<String, Method> handlerMapping=new HashMap<String, Method>();


	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		doPost(req, resp);
	}
	
	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

		System.out.println("==>123");
		
		dispatcher(req,resp);
		
		
	}
	
	
	private void dispatcher(HttpServletRequest req, HttpServletResponse resp) throws IOException{
		String url = req.getRequestURI();// url=/springcopy/user/edit
		String contextPath = req.getContextPath();// /springcopy
		url=url.replace(contextPath, "").replaceAll("/+", "/");//url=/user/edit
		if(!handlerMapping.containsKey(url)) {
			resp.getWriter().write("404 NOT Found!");
			return;
		}
		
		Method method = handlerMapping.get(url);
		System.out.println("url mapping method "+method);
		
		//页面传参
		
		//方法调用
		
		//响应返回
		Map<String, String[]> parameterMap = req.getParameterMap();
		String beanName = lowerFirstCase(method.getDeclaringClass().getSimpleName());
		Object object = ioc.get(beanName);
		try {
			Object[] arguments = getMethodArguments(parameterMap, method);
			Object result =method.invoke(object,arguments);
			if(result==null)return;
			resp.getWriter().print(result);
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			e.printStackTrace();
		}
		
	}
	
	//参数绑定
	public Object[] getMethodArguments(Map<String, String[]> parameterMap,Method method) {
		//使用jdk1.8的的方法获取参数列表，且要jdk编译时开启 parameters，它默认是关闭的
		//1.8前获取参数名称比较复杂，可参考spring4，它先使用1.8的方式获取
		//获取失败后通过class的debug信息获取
		//参考：https://www.jianshu.com/p/f569c5705e8a
		Parameter[] parameters = method.getParameters();
		Object[]args=new Object[parameters.length];
		for (int i=0;i<parameters.length;i++) {
			Parameter parameter =parameters[i];
			String[] values = parameterMap.get(parameter.getName());
			if(values==null) {
				args[i]=null;//对于基本类型参数会有问题
				continue;
			}
			args[i]=values[0];
		}
		return args;
	}

	@Override
	public void init(ServletConfig config) throws ServletException {
		//加载配置文件
		doLoadConfig(config.getInitParameter("contextConfigLocation"));
		
		//扫描相关的类
		doScanner(contextConfig.getProperty("scanPackage"));
		
		//初始化IOC容器，创建实例并放入IOC容器
		doInstance();
		
		//DI依赖注入，为容器中的实例需要的属性进行注入
		doAutowired();
		
		//初始化HandlerMapping，为url和method关系进行绑定
		initHandlerMapping();
		
		System.out.println("YAN Spring is init.");
		
	}

	private void initHandlerMapping() {
		if(ioc.isEmpty())return;
		for (Entry<String, Object> entry : ioc.entrySet()) {
			Class<?> clazz = entry.getValue().getClass();
			if(!clazz.isAnnotationPresent(YanController.class))continue;
			
			String baseUrl="";
			if(clazz.isAnnotationPresent(YanRequestMapping.class)){
				YanRequestMapping yanRequestMapping = clazz.getAnnotation(YanRequestMapping.class);
				baseUrl=yanRequestMapping.value();// /user/
			}
			
			Method[] methods = clazz.getMethods();
			for (Method method : methods) {
				if(!method.isAnnotationPresent(YanRequestMapping.class))continue;
				
				YanRequestMapping yanRequestMapping = method.getAnnotation(YanRequestMapping.class);
				String url=(baseUrl+yanRequestMapping.value()).replaceAll("/+", "/");
				
				handlerMapping.put(url, method);// url=/user/edit
				System.out.println("Mapped "+url+" into "+method);
			}
			
			
		}
	}

	private void doAutowired() {
		if(ioc.isEmpty())return;
		for (Entry<String, Object> entry : ioc.entrySet()) {
			Field[] fields = entry.getValue().getClass().getDeclaredFields();
			for (Field field : fields) {
				if(!field.isAnnotationPresent(YanAutowired.class))continue;
				YanAutowired yanAutowired = field.getAnnotation(YanAutowired.class);
				String beanName = yanAutowired.value();
				if(beanName==null || "".equals(beanName.trim())) {
					beanName=field.getType().getName();
				}
				field.setAccessible(true);
				try {
					field.set(entry.getValue(),ioc.get(beanName));
				} catch (IllegalArgumentException | IllegalAccessException e) {
					e.printStackTrace();
				}
				
			}
		}
	}

	private void doInstance(){
		if(classNameList.isEmpty())return;
		for (String className : classNameList) {
			try {
				//className=com.yan.service.impl.UserServierImpl
				Class<?> clazz = Class.forName(className);
				if(clazz.isAnnotationPresent(YanController.class)) {
					Object instance = clazz.getDeclaredConstructor().newInstance();
					String key=lowerFirstCase(clazz.getSimpleName());
					ioc.put(key, instance);//key=userServierImpl
				}else if(clazz.isAnnotationPresent(YanService.class)) {
					YanService yanService = clazz.getAnnotation(YanService.class);
					String name = yanService.value();
					if(name==null || "".equals(name.trim())) {
						name=lowerFirstCase(clazz.getSimpleName());
					}
					Object instance = clazz.getDeclaredConstructor().newInstance();
					ioc.put(name, instance);
					
					Class<?>[] interfaces = clazz.getInterfaces();
					for (Class<?> i : interfaces) {
						//name=com.yan.service.UserServier
						ioc.put(i.getName(), instance);
					}
					
				}else {
					continue;
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			
		}
		
	}
	
	private String lowerFirstCase(String str) {
		char[] charArray = str.toCharArray();
		charArray[0]+=32;
		return String.valueOf(charArray);
	}

	private void doScanner(String scanPackage) {//com.yan
		URL url = this.getClass().getResource("/"+scanPackage.replaceAll("\\.", "/"));
		//file:/D:/DevSoftware/apache-tomcat-8.5.53/webapps/springcopy/WEB-INF/classes/com/yan/
		File classDir =new File(url.getFile());
		for (File file : classDir.listFiles()) {
			if(file.isDirectory()) {
				doScanner(scanPackage+"."+file.getName());
			}else {
			    String className=scanPackage+"."+file.getName().replace(".class", "");
			    classNameList.add(className);
			}
		}
		
	}

	private void doLoadConfig(String contextConfigLocation) {//application.properties
		InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream(contextConfigLocation);
		//this.getClass().getClassLoader().getResourceAsStream 默认从classpath，即类根路径
		//this.getClass().getResourceAsStream默认从该类路径查找，加/则从classpath开始查找， 
		//InputStream inputStream=this.getClass().getResourceAsStream("/"+contextConfigLocation);
		try {
			contextConfig.load(inputStream);
		} catch (IOException e) {
			e.printStackTrace();
		}finally {
			if(inputStream!=null) {
				try {
					inputStream.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		
	}

	
	
}
