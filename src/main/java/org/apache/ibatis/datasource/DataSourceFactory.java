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
package org.apache.ibatis.datasource;

import java.util.Properties;
import javax.sql.DataSource;

/**
 * 有三种内建的数据源类型（也就是 type="[UNPOOLED|POOLED|JNDI]"）：
 * UNPOOLED– 这个数据源的实现会每次请求时打开和关闭连接。虽然有点慢，但对那些数据库连接可用性要求不高的简单应用程序来说，是一个很好的选择。
 * 性能表现则依赖于使用的数据库，对某些数据库来说，使用连接池并不重要，这个配置就很适合这种情形。
 *
 * POOLED– 这种数据源的实现利用“池”的概念将 JDBC 连接对象组织起来，避免了创建新的连接实例时所必需的初始化和认证时间。
 * 这种处理方式很流行，能使并发 Web 应用快速响应请求。
 *
 * JNDI – 这个数据源实现是为了能在如 EJB 或应用服务器这类容器中使用，容器可以集中或在外部配置数据源，然后放置一个 JNDI 上下文的数据源引用
 *
 * avax.sql.DataSource工厂接口
 *
 * @author Clinton Begin
 */
public interface DataSourceFactory {

  /**
   * 设置 DataSource 对象的属性
   *
   * @param props 属性
   */
  void setProperties(Properties props);

  /**
   * 获得 DataSource 对象
   *
   * @return DataSource 对象
   */
  DataSource getDataSource();

}
