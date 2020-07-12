/**
 * Copyright 2009-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.ibatis.executor;

/**
 * 错误上下文，负责记录错误日志
 *
 * @author Clinton Begin
 */
public class ErrorContext {

  // 获取系统换行符
  private static final String LINE_SEPARATOR = System.getProperty("line.separator", "\n");

  /**
   * ThreadLocal 是本地线程存储，它的作用是为变量在每个线程中创建一个副本，每个线程内部都可以使用该副本，线程之间互不影响。 ErrorContext 可以看作是线程内部的单例模式
   */
  private static final ThreadLocal<ErrorContext> LOCAL = new ThreadLocal<>();

  private ErrorContext stored;

  // 存储异常存在于哪个资源文件中
  private String resource;

  // 存储异常是做什么操作时发生的
  private String activity;

  // 存储哪个对象操作时发生异常
  private String object;

  // 存储异常的概览信息
  private String message;

  // 存储发生日常的 SQL 语句
  private String sql;

  // 存储详细的 Java 异常日志
  private Throwable cause;

  private ErrorContext() {
  }

  /**
   * 获取线程内实例的静态公有的接口
   *
   * @return 该线程的ErrorContext
   */
  public static ErrorContext instance() {
    ErrorContext context = LOCAL.get();
    if (context == null) {
      context = new ErrorContext();
      LOCAL.set(context);
    }
    return context;
  }

  /**
   * TODO: 创建了一个新的ErrorContext并设置到LOCAL中，同时新的ErrorContext中的stored会引用旧的ErrorContext
   */
  public ErrorContext store() {
    ErrorContext newContext = new ErrorContext();
    newContext.stored = this;
    LOCAL.set(newContext);
    return LOCAL.get();
  }

  /**
   * TODO: 把旧的ErrorContext重新设置回LOCAL，并制空stored
   */
  public ErrorContext recall() {
    if (stored != null) {
      LOCAL.set(stored);
      stored = null;
    }
    return LOCAL.get();
  }

  public ErrorContext resource(String resource) {
    this.resource = resource;
    return this;
  }

  public ErrorContext activity(String activity) {
    this.activity = activity;
    return this;
  }

  public ErrorContext object(String object) {
    this.object = object;
    return this;
  }

  public ErrorContext message(String message) {
    this.message = message;
    return this;
  }

  public ErrorContext sql(String sql) {
    this.sql = sql;
    return this;
  }

  public ErrorContext cause(Throwable cause) {
    this.cause = cause;
    return this;
  }

  // reset() 顾名思义，用来重置变量，为变量赋 null 值，以便 gc 的执行，并清空 LOCAL
  public ErrorContext reset() {
    resource = null;
    activity = null;
    object = null;
    message = null;
    sql = null;
    cause = null;
    LOCAL.remove();
    return this;
  }

  @Override
  public String toString() {
    StringBuilder description = new StringBuilder();

    // message
    if (this.message != null) {
      description.append(LINE_SEPARATOR);
      description.append("### ");
      description.append(this.message);
    }

    // resource
    if (resource != null) {
      description.append(LINE_SEPARATOR);
      description.append("### The error may exist in ");
      description.append(resource);
    }

    // object
    if (object != null) {
      description.append(LINE_SEPARATOR);
      description.append("### The error may involve ");
      description.append(object);
    }

    // activity
    if (activity != null) {
      description.append(LINE_SEPARATOR);
      description.append("### The error occurred while ");
      description.append(activity);
    }

    // activity
    if (sql != null) {
      description.append(LINE_SEPARATOR);
      description.append("### SQL: ");
      description.append(sql.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ').trim());
    }

    // cause
    if (cause != null) {
      description.append(LINE_SEPARATOR);
      description.append("### Cause: ");
      description.append(cause.toString());
    }

    return description.toString();
  }

}
