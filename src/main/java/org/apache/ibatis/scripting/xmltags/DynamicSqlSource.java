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

import java.util.Map;

import org.apache.ibatis.builder.SqlSourceBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.session.Configuration;

/**
 * 实现 SqlSource 接口，mybatis动态SQL实现类
 *
 * 动态 SQL ，用于每次执行 SQL 操作时，记录动态 SQL 处理后的最终 SQL 字符串
 *
 * @author Clinton Begin
 */
public class DynamicSqlSource implements SqlSource {

  private final Configuration configuration;
  private final SqlNode rootSqlNode;

  public DynamicSqlSource(Configuration configuration, SqlNode rootSqlNode) {
    this.configuration = configuration;
    this.rootSqlNode = rootSqlNode;
  }

  /**
   * TODO:适用于使用了 OGNL 表达式，或者使用了 ${} 表达式的 SQL ，所以它是动态的，需要在每次执行 #getBoundSql(Object parameterObject) 方法，根据参数，生成对应的 SQL 。
   *
   * @param parameterObject 参数对象
   * @return
   */
  @Override
  public BoundSql getBoundSql(Object parameterObject) {
    // <1> 应用 rootSqlNode
    DynamicContext context = new DynamicContext(configuration, parameterObject);
    // TODO: 这一句会执行动态SQL生成最终要执行的SQL
    rootSqlNode.apply(context);
    // <2> 创建 SqlSourceBuilder 对象
    SqlSourceBuilder sqlSourceParser = new SqlSourceBuilder(configuration);
    // <2> 解析出 SqlSource 对象
    Class<?> parameterType = parameterObject == null ? Object.class : parameterObject.getClass();
    // TODO: 返回的 SqlSource 对象，类型是 StaticSqlSource 类。
    SqlSource sqlSource = sqlSourceParser.parse(context.getSql(), parameterType, context.getBindings());
    // <3> 输入 SQL 的参数，获得 BoundSql 对象
    BoundSql boundSql = sqlSource.getBoundSql(parameterObject);
    // <4> 添加附加参数到 BoundSql 对象中
    for (Map.Entry<String, Object> entry : context.getBindings().entrySet()) {
      boundSql.setAdditionalParameter(entry.getKey(), entry.getValue());
    }
    // <5> 返回 BoundSql 对象
    return boundSql;
  }

}
