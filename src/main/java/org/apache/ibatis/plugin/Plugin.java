/**
 *    Copyright 2009-2019 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.plugin;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.ibatis.reflection.ExceptionUtil;

/**
 * 实现 InvocationHandler 接口，插件类，一方面提供创建动态代理对象的方法，另一方面实现对指定类的指定方法的拦截处理。
 *
 * TODO:Plugin 是 MyBatis 插件体系的核心类。
 *
 * @author Clinton Begin
 */
public class Plugin implements InvocationHandler {

  /**
   * 目标对象
   */
  private final Object target;
  /**
   * 拦截器
   */
  private final Interceptor interceptor;
  /**
   * 拦截的方法映射
   *
   * KEY：类
   * VALUE：方法集合
   */
  private final Map<Class<?>, Set<Method>> signatureMap;

  private Plugin(Object target, Interceptor interceptor, Map<Class<?>, Set<Method>> signatureMap) {
    this.target = target;
    this.interceptor = interceptor;
    this.signatureMap = signatureMap;
  }

  /**
   * 静态方法，创建目标类的代理对象
   *
   * @param target
   * @param interceptor
   * @return
   */
  public static Object wrap(Object target, Interceptor interceptor) {
    // <1> 获得拦截的方法映射
    Map<Class<?>, Set<Method>> signatureMap = getSignatureMap(interceptor);
    // <2> 获得目标类的类型
    Class<?> type = target.getClass();
    // <2> 获得目标类的接口集合
    // 返回所有才signatureMap中包含的type父类接口
    // 调用 #getAllInterfaces(Class<?> type, Map<Class<?>, Set<Method>> signatureMap) 方法，获得目标类的接口集合
    Class<?>[] interfaces = getAllInterfaces(type, signatureMap);
    // <3.1> 若有接口，则创建目标对象的 JDK Proxy 对象
    // 情况一，若有接口，则创建目标对象的 JDK Proxy 对象。
    // 情况二，若无接口，则返回原始的目标对象。
    if (interfaces.length > 0) {
      // 创建代理
      return Proxy.newProxyInstance(
          type.getClassLoader(),
          interfaces,
          new Plugin(target, interceptor, signatureMap));// 因为 Plugin 实现了 InvocationHandler 接口，所以可以作为 JDK 动态代理的调用处理器
    }
    // <3.2> 如果没有，则返回原始的目标对象
    return target;
  }

  // 执行method方法
  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    try {
      // 获得目标方法是否被拦截
      Set<Method> methods = signatureMap.get(method.getDeclaringClass());
      if (methods != null && methods.contains(method)) {
        // 如果是，则拦截处理该方法
        return interceptor.intercept(new Invocation(target, method, args));
      }
      // 如果不是，则调用原方法
      return method.invoke(target, args);
    } catch (Exception e) {
      throw ExceptionUtil.unwrapThrowable(e);
    }
  }

  /**
   * 获得拦截的方法映射
   * 基于 @Intercepts 和 @Signature 注解
   *
   * @param interceptor
   * @return
   */
  private static Map<Class<?>, Set<Method>> getSignatureMap(Interceptor interceptor) {
    // 获取拦截器的Intercepts注解
    Intercepts interceptsAnnotation = interceptor.getClass().getAnnotation(Intercepts.class);
    // issue #251
    if (interceptsAnnotation == null) {
      throw new PluginException("No @Intercepts annotation was found in interceptor " + interceptor.getClass().getName());      
    }
    // 获取拦截器的值
    Signature[] sigs = interceptsAnnotation.value();
    Map<Class<?>, Set<Method>> signatureMap = new HashMap<>();
    for (Signature sig : sigs) {
      Set<Method> methods = signatureMap.computeIfAbsent(sig.type(), k -> new HashSet<>());
      try {
        Method method = sig.type().getMethod(sig.method(), sig.args());
        methods.add(method);
      } catch (NoSuchMethodException e) {
        throw new PluginException("Could not find method on " + sig.type() + " named " + sig.method() + ". Cause: " + e, e);
      }
    }
    return signatureMap;
  }

  /**
   * 调用 #getAllInterfaces(Class<?> type, Map<Class<?>, Set<Method>> signatureMap) 方法，获得目标类的接口集合
   *
   * 从这里可以看出，@Signature 注解的 type 属性，必须是接口
   * @param type
   * @param signatureMap
   * @return
   */
  private static Class<?>[] getAllInterfaces(Class<?> type, Map<Class<?>, Set<Method>> signatureMap) {
    // 接口的集合
    Set<Class<?>> interfaces = new HashSet<>();
    while (type != null) {
      // 遍历接口
      for (Class<?> c : type.getInterfaces()) {
        if (signatureMap.containsKey(c)) {
          interfaces.add(c);
        }
      }
      // 父类接口
      type = type.getSuperclass();
    }
    // 创建接口的数组
    return interfaces.toArray(new Class<?>[interfaces.size()]);
  }

}
