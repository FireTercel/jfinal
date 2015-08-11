/**
 * Copyright (c) 2011-2015, James Zhan 詹波 (jfinal@126.com).
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

package com.jfinal.core;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import com.jfinal.aop.Interceptor;
import com.jfinal.config.Interceptors;
import com.jfinal.config.Routes;

/**
 * ActionMapping action、controller和method的拦截器
 */
final class ActionMapping {
	
	private static final String SLASH = "/";
	private Routes routes;
	private Interceptors interceptors;
	
	private final Map<String, Action> mapping = new HashMap<String, Action>();
	
	ActionMapping(Routes routes, Interceptors interceptors) {
		this.routes = routes;
		this.interceptors = interceptors;
	}
	
	/**
	 * 获取控制器中没有参数的方法。
	 * @return
	 */
	private Set<String> buildExcludedMethodName() {
		Set<String> excludedMethodName = new HashSet<String>();
		Method[] methods = Controller.class.getMethods();
		for (Method m : methods) {
			if (m.getParameterTypes().length == 0)
				excludedMethodName.add(m.getName());
		}
		return excludedMethodName;
	}
	
	void buildActionMapping() {//设置映射关系，包括拦截器设置。
		mapping.clear();
		Set<String> excludedMethodName = buildExcludedMethodName();
		ActionInterceptorBuilder interceptorBuilder = new ActionInterceptorBuilder();
		Interceptor[] globalInters = interceptors.getGlobalActionInterceptor();//全局拦截器
		interceptorBuilder.addToInterceptorsMap(globalInters);
		for (Entry<String, Class<? extends Controller>> entry : routes.getEntrySet()) {//所有路径
			Class<? extends Controller> controllerClass = entry.getValue();//循环获得controller
			Interceptor[] controllerInters = interceptorBuilder.buildControllerInterceptors(controllerClass);//该controller的拦截器
			
			boolean sonOfController = (controllerClass.getSuperclass() == Controller.class);//controller父类
			Method[] methods = (sonOfController ? controllerClass.getDeclaredMethods() : controllerClass.getMethods());//存在父类则获得该子类方法
			for (Method method : methods) {
				String methodName = method.getName();
				if (excludedMethodName.contains(methodName) || method.getParameterTypes().length != 0)
					continue ;
				if (sonOfController && !Modifier.isPublic(method.getModifiers()))
					continue ;
				
				Interceptor[] methodInters = interceptorBuilder.buildMethodInterceptors(method);
				Interceptor[] actionInters = interceptorBuilder.buildActionInterceptors(globalInters, controllerInters, methodInters, method);
				String controllerKey = entry.getKey();
				
				ActionKey ak = method.getAnnotation(ActionKey.class);
				String actionKey;
				if (ak != null) {
					actionKey = ak.value().trim();
					if ("".equals(actionKey))
						throw new IllegalArgumentException(controllerClass.getName() + "." + methodName + "(): The argument of ActionKey can not be blank.");
					
					if (!actionKey.startsWith(SLASH))
						actionKey = SLASH + actionKey;
				}
				else if (methodName.equals("index")) {//当方法名为index时，actionKey为controllerKey
					actionKey = controllerKey;
				}
				else {
					actionKey = controllerKey.equals(SLASH) ? SLASH + methodName : controllerKey + SLASH + methodName;
				}
				
				Action action = new Action(controllerKey, actionKey, controllerClass, method, methodName, actionInters, routes.getViewPath(controllerKey));
				if (mapping.put(actionKey, action) != null)
					throw new RuntimeException(buildMsg(actionKey, controllerClass, method));
			}
		}
		
		// support url = controllerKey + urlParas with "/" of controllerKey
		Action actoin = mapping.get("/");
		if (actoin != null)
			mapping.put("", actoin);
	}
	
	private static final String buildMsg(String actionKey, Class<? extends Controller> controllerClass, Method method) {
		StringBuilder sb = new StringBuilder("The action \"")
			.append(controllerClass.getName()).append(".")
			.append(method.getName()).append("()\" can not be mapped, ")
			.append("actionKey \"").append(actionKey).append("\" is already in use.");
		
		String msg = sb.toString();
		System.err.println("\nException: " + msg);
		return msg;
	}
	
	/**
	 * Support four types of url<br>
	 * 1: http://abc.com/controllerKey                 ---> 00<br>
	 * 2: http://abc.com/controllerKey/para            ---> 01<br>
	 * 3: http://abc.com/controllerKey/method          ---> 10<br>
	 * 4: http://abc.com/controllerKey/method/para     ---> 11<br>
	 * The controllerKey can also contains "/"<br>
	 * Example: http://abc.com/uvw/xyz/method/para
	 */
	Action getAction(String url, String[] urlPara) {
		Action action = mapping.get(url);
		if (action != null) {
			return action;
		}
		
		// --------
		int i = url.lastIndexOf(SLASH);
		if (i != -1) {
			action = mapping.get(url.substring(0, i));
			urlPara[0] = url.substring(i + 1);
		}
		
		return action;
	}
	
	List<String> getAllActionKeys() {
		List<String> allActionKeys = new ArrayList<String>(mapping.keySet());
		Collections.sort(allActionKeys);
		return allActionKeys;
	}
}













