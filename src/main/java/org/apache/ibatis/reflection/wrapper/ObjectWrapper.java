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
package org.apache.ibatis.reflection.wrapper;

import java.util.List;

import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.property.PropertyTokenizer;

/**
 * @author Clinton Begin
 */
public interface ObjectWrapper {
  // 获取对应 prop 在当前类中属性对象
  Object get(PropertyTokenizer prop);
  // 给对应 prop 在当前类中属性对象赋值
  void set(PropertyTokenizer prop, Object value);
  // 查找对应属性名 name 在当前类中属性并返回
  String findProperty(String name, boolean useCamelCaseMapping);
  // 返回类的所有 get 方法名
  String[] getGetterNames();
  // 返回类的所有 set 方法名
  String[] getSetterNames();
  // 返回对应属性名 name 在当前类中属性的 set 方法的参数类型
  Class<?> getSetterType(String name);
  // 返回对应属性名 name 在当前类中属性的 get 方法的参数类型
  Class<?> getGetterType(String name);
  // 返回是否有对应属性名 name 在当前类中属性的 set 方法
  boolean hasSetter(String name);
  // 返回是否有对应属性名 name 在当前类中属性的 get 方法
  boolean hasGetter(String name);
  // 实例化属性对象
  MetaObject instantiatePropertyValue(String name, PropertyTokenizer prop, ObjectFactory objectFactory);


  boolean isCollection();
  
  void add(Object element);
  
  <E> void addAll(List<E> element);

}
