package com.doudh.servlet;

import com.doudh.annotation.*;
import com.doudh.modules.controller.UserController;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.URL;
import java.util.*;
import java.util.Map.Entry;

/**
 * ①通过web.xml拿到基本包信息 读外部配置文件
  ②全自动扫描基本包下的bean，加载Spring容器
  ③通过注解对象，找到每个bean，反射获取实例
  ④依赖注入，实现ioc机制
  ⑤handlerMapping通过基部署 和 基于类的url找到相应的处理器
 */
public class MyDispatcherServlet extends HttpServlet{
	
	private Properties properties = new Properties();
	// 集合全自动扫描基础包下面的类限定名
	private List<String> classNames = new ArrayList<>();

	// 缓存 key/value: 类注解参数/类实例对象，存储controller和service实例
	private Map<String, Object> instanceMaps  = new HashMap<>();

	// key/value: 请求url/handler的method
	private Map<String, Method> handlerMapping = new  HashMap<>();
	// 再维护一个map，存储controller实例
	private Map<String, Object> controllerMap  =new HashMap<>();
	

	@Override
	public void init(ServletConfig config) throws ServletException {
		
		//1.加载配置文件
		doLoadConfig(config.getInitParameter("contextConfigLocation"));
		
		//2.初始化所有相关联的类,扫描用户设定的包下面所有的类
		doScanner(properties.getProperty("scanPackage"));

		// 3.通过注解对象，找到每个bean，反射获取实例
		reflectBeansInstance();

		// 4.依赖注入，实现ioc机制
		doIoc();

		//5.初始化HandlerMapping(将url和method对应上)
		initHandlerMapping();
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		this.doPost(req,resp);
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		try {
			//处理请求
			doDispatch(req,resp);
		} catch (Exception e) {
			resp.getWriter().write("500!! Server Exception");
		}

	}
	
	
	private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws Exception {
		if(handlerMapping.isEmpty()){
			return;
		}
		String url =req.getRequestURI();
		String contextPath = req.getContextPath();
		//拼接url并把多个/替换成一个
		url=url.replace(contextPath, "").replaceAll("/+", "/");
		if(!this.handlerMapping.containsKey(url)){
			resp.getWriter().write("404 NOT FOUND!");
			return;
		}
		Method handlerMethod =this.handlerMapping.get(url);

        // 获取方法的参数列表
        Parameter methodParameters[] = handlerMethod.getParameters();
        // 调用方法需要传递的形参
        Object paramValues[] = new Object[methodParameters.length];
		//方法的参数列表
        for (int i = 0; i < methodParameters.length; i++) {
            if (ServletRequest.class.isAssignableFrom(methodParameters[i].getType())) {
                paramValues[i] = req;
            } else if (ServletResponse.class.isAssignableFrom(methodParameters[i].getType())) {
                paramValues[i] = resp;
            } else {// 其它参数，目前只支持String，Integer，Float，Double
                // 参数绑定的名称，默认为方法形参名
                String bindingValue = methodParameters[i].getName();
                if (methodParameters[i].isAnnotationPresent(MyRequestParam.class)) {
                    bindingValue = methodParameters[i].getAnnotation(MyRequestParam.class).value();
                }
                // 从请求中获取参数的值
                String paramValue = req.getParameter(bindingValue);
                paramValues[i] = paramValue;
                if (paramValue != null) {
                    if (Integer.class.isAssignableFrom(methodParameters[i].getType())) {
                        paramValues[i] = Integer.parseInt(paramValue);
                    } else if (Float.class.isAssignableFrom(methodParameters[i].getType())) {
                        paramValues[i] = Float.parseFloat(paramValue);
                    } else if (Double.class.isAssignableFrom(methodParameters[i].getType())) {
                        paramValues[i] = Double.parseDouble(paramValue);
                    }
                }
            }
        }
		//利用反射机制来调用
		try {
            handlerMethod.invoke(this.controllerMap.get(url), paramValues);//obj是method所对应的实例 在ioc容器中
		} catch (Exception e) {
			e.printStackTrace();
		}
	}



	private void  doLoadConfig(String location){
		//把web.xml中的contextConfigLocation对应value值的文件加载到留里面
		InputStream resourceAsStream = this.getClass().getClassLoader().getResourceAsStream(location);
		try {
			//用Properties文件加载文件里的内容
			properties.load(resourceAsStream);
		} catch (IOException e) {
			e.printStackTrace();
		}finally {
			//关流
			if(null!=resourceAsStream){
				try {
					resourceAsStream.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		
	}
	
	private void doScanner(String packageName) {
		//把所有的.替换成/
		URL url  =this.getClass().getClassLoader().getResource("/"+packageName.replaceAll("\\.", "/"));
		File dir = new File(url.getFile());
		for (File file : dir.listFiles()) {
			if(file.isDirectory()){
				//递归读取包
				doScanner(packageName+"."+file.getName());
			}else{
				String className =packageName +"." +file.getName().replace(".class", "");
				classNames.add(className);
			}
		}
	}

	private void reflectBeansInstance() {
		if (classNames.isEmpty()) {
			return;
		}	
		for (String className : classNames) {
			try {
				//把类搞出来,反射来实例化(只有加@MyController需要实例化)
				Class<?> clazz =Class.forName(className);
			   if(clazz.isAnnotationPresent(MyController.class)){     //controller实例
				   MyController annotation = clazz.getAnnotation(MyController.class);
				   String insMapKey = annotation.value();
				   if ("".equals(insMapKey)){
					   insMapKey = toLowerFirstWord(clazz.getSimpleName());
				   }
				   instanceMaps .put(insMapKey,clazz.newInstance());
			   }else if(clazz.isAnnotationPresent(MyService.class)){    //service层实例
				   MyService annotation = clazz.getAnnotation(MyService.class);
				   String insMapKey = annotation.value();
				   if ("".equals(insMapKey)){
					   insMapKey = toLowerFirstWord(clazz.getSimpleName());
				   }
				   instanceMaps .put(insMapKey,clazz.newInstance());
			   }else{
					continue;
				}
			} catch (Exception e) {
				e.printStackTrace();
				continue;
			}
		}
	}

	/**
	 * 依赖注入，实现ioc机制
	 * @throws Exception
	 */
	private void doIoc(){
		try {
			if (instanceMaps.isEmpty()) {
				throw new Exception("没有发现可注入的实例");
			}
			for (Map.Entry<String, Object> entry : instanceMaps.entrySet()) {
				Field[] fields = entry.getValue().getClass().getDeclaredFields();
				// 遍历bean对象的字段
				for (Field field : fields) {
					if (field.isAnnotationPresent(MyQualifier.class)) {
						// 通过bean字段对象上面的注解参数来注入实例
						String insMapKey = field.getAnnotation(MyQualifier.class).value();
						if (insMapKey.equals("")) {
							insMapKey = toLowerFirstWord(field.getType().getSimpleName());
						}
						field.setAccessible(true);
						// 注入实例
                        UserController user1= (UserController)entry.getValue();
                        field.set(entry.getValue(), instanceMaps.get(insMapKey));
                        UserController user2= (UserController)entry.getValue();
                        String dd ="";
					}
				}
			}
		}catch (Exception e){
            e.printStackTrace();
		}
	}

	private void initHandlerMapping(){
		if(instanceMaps.isEmpty()){
			return;
		}
		try {
			for (Entry<String, Object> entry: instanceMaps.entrySet()) {
				Class<? extends Object> clazz = entry.getValue().getClass();
				if(!clazz.isAnnotationPresent(MyController.class)){
					continue;
				}
				//拼url时,是controller头的url拼上方法上的url
				String baseUrl ="";
				if(clazz.isAnnotationPresent(MyRequestMapping.class)){
					MyRequestMapping annotation = clazz.getAnnotation(MyRequestMapping.class);
					baseUrl=annotation.value();
				}
				Method[] methods = clazz.getMethods();
				for (Method method : methods) {
					if(!method.isAnnotationPresent(MyRequestMapping.class)){
						continue;
					}
					MyRequestMapping annotation = method.getAnnotation(MyRequestMapping.class);
					String url = annotation.value();
					
					url =(baseUrl+"/"+url).replaceAll("/+", "/");
					// 存入handlerMapping
					handlerMapping.put(url,method);
					// 再维护一个只存储controller实例的map
					controllerMap.put(url,entry.getValue());
					System.out.println(url+","+method);
				}
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * 把字符串的首字母小写
	 * @param name
	 * @return
	 */
	private String toLowerFirstWord(String name){
		char[] charArray = name.toCharArray();
		charArray[0] += 32;
		return String.valueOf(charArray);
	}
	
		
}
