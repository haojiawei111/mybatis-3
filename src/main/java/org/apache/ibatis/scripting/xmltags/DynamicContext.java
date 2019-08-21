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
package org.apache.ibatis.scripting.xmltags;

import java.util.HashMap;
import java.util.Map;

import ognl.OgnlContext;
import ognl.OgnlRuntime;
import ognl.PropertyAccessor;

import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;

/**
 * 动态 SQL ，用于每次执行 SQL 操作时，记录动态 SQL 处理后的最终 SQL 字符串
 *
 * @author Clinton Begin
 */
public class DynamicContext {

  /**
   * {@link #bindings} _parameter 的键，参数
   */
  public static final String PARAMETER_OBJECT_KEY = "_parameter";
  /**
   * {@link #bindings} _databaseId 的键，数据库编号
   */
  public static final String DATABASE_ID_KEY = "_databaseId";

  static {
    // <1.2> 设置 OGNL 的属性访问器
    OgnlRuntime.setPropertyAccessor(ContextMap.class, new ContextAccessor());
  }

  /**
   * 上下文的参数集合
   */
  private final ContextMap bindings;
  /**
   * 生成后的 SQL
   */
  private final StringBuilder sqlBuilder = new StringBuilder();
  /**
   * 唯一编号。在 {@link org.apache.ibatis.scripting.xmltags.XMLScriptBuilder.ForEachHandler} 使用
   */
  private int uniqueNumber = 0;

  // 当需要使用到 OGNL 表达式时，parameterObject 非空
  public DynamicContext(Configuration configuration, Object parameterObject) {
    // <1> 初始化 bindings 参数
    if (parameterObject != null && !(parameterObject instanceof Map)) {
      // 调用 Configuration#newMetaObject(Object object) 方法，创建 MetaObject 对象，实现对 parameterObject 参数的访问
      MetaObject metaObject = configuration.newMetaObject(parameterObject);
      bindings = new ContextMap(metaObject);
    } else {
      bindings = new ContextMap(null);
    }
    // <2> 添加 bindings 的默认值
    bindings.put(PARAMETER_OBJECT_KEY, parameterObject);
    bindings.put(DATABASE_ID_KEY, configuration.getDatabaseId());
  }

  public Map<String, Object> getBindings() {
    return bindings;
  }
  // 可以往 bindings 属性中，添加新的 KV 键值对。
  public void bind(String name, Object value) {
    bindings.put(name, value);
  }
  // 可以不断向 sqlBuilder 属性中，添加 SQL 段。
  public void appendSql(String sql) {
    sqlBuilder.append(sql);
    sqlBuilder.append(" ");
  }

  public String getSql() {
    return sqlBuilder.toString().trim();
  }
  // 每次请求，获得新的序号。
  public int getUniqueNumber() {
    return uniqueNumber++;
  }

  /**
   * ContextMap ，是 DynamicContext 的内部静态类，继承 HashMap 类，上下文的参数集合
   * 该类在 HashMap 的基础上，增加支持对 parameterMetaObject 属性的访问
   */
  static class ContextMap extends HashMap<String, Object> {
    private static final long serialVersionUID = 2977601501966151582L;
    /**
     * parameter 对应的 MetaObject 对象
     */
    private MetaObject parameterMetaObject;

    public ContextMap(MetaObject parameterMetaObject) {
      this.parameterMetaObject = parameterMetaObject;
    }

    @Override
    public Object get(Object key) {
      // 如果有 key 对应的值，直接获得
      String strKey = (String) key;
      if (super.containsKey(strKey)) {
        return super.get(strKey);
      }
      // 从 parameterMetaObject 中，获得 key 对应的属性
      if (parameterMetaObject != null) {
        // issue #61 do not modify the context when reading
        return parameterMetaObject.getValue(strKey);
      }

      return null;
    }
  }

  /**
   * ContextAccessor ，是 DynamicContext 的内部静态类，实现 ognl.PropertyAccessor 接口，上下文访问器
   */
  static class ContextAccessor implements PropertyAccessor {

    @Override
    public Object getProperty(Map context, Object target, Object name) {
      Map map = (Map) target;
      // 优先从 ContextMap 中，获得属性
      Object result = map.get(name);
      if (map.containsKey(name) || result != null) {
        return result;
      }
      // 如果没有，则从 PARAMETER_OBJECT_KEY 对应的 Map 中，获得属性
      // 为什么可以访问 PARAMETER_OBJECT_KEY 属性，并且是 Map 类型呢？回看 DynamicContext 构造方法，就可以明白了
      Object parameterObject = map.get(PARAMETER_OBJECT_KEY);
      if (parameterObject instanceof Map) {
        return ((Map)parameterObject).get(name);
      }

      return null;
    }

    @Override
    public void setProperty(Map context, Object target, Object name, Object value) {
      Map<Object, Object> map = (Map<Object, Object>) target;
      map.put(name, value);
    }

    @Override
    public String getSourceAccessor(OgnlContext arg0, Object arg1, Object arg2) {
      return null;
    }

    @Override
    public String getSourceSetter(OgnlContext arg0, Object arg1, Object arg2) {
      return null;
    }
  }
}