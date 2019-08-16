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
package org.apache.ibatis.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;
import org.apache.ibatis.type.UnknownTypeHandler;

/**
 * 结果字段的注解
 * @author Clinton Begin
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({})
public @interface Result {

  /**
   * @return 是否是 ID 字段
   */
  boolean id() default false;

  /**
   * @return Java 类中的属性
   */
  String property() default "";

  /**
   * @return 数据库的字段
   */
  String column() default "";

  /**
   * @return Java Type
   */
  Class<?> javaType() default void.class;

  /**
   * @return JDBC Type
   */
  JdbcType jdbcType() default JdbcType.UNDEFINED;

  /**
   * @return 使用的 TypeHandler 处理器
   */
  Class<? extends TypeHandler> typeHandler() default UnknownTypeHandler.class;

  /**
   * @return {@link One} 注解
   */
  One one() default @One;

  /**
   * @return {@link Many} 注解
   */
  Many many() default @Many;
}
