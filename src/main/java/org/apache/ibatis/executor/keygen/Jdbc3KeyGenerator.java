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
package org.apache.ibatis.executor.keygen;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

import org.apache.ibatis.binding.MapperMethod.ParamMap;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.ExecutorException;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.reflection.ArrayUtil;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.defaults.DefaultSqlSession.StrictMap;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;
import org.apache.ibatis.type.TypeHandlerRegistry;

/**
 * 实现 KeyGenerator 接口，基于 Statement#getGeneratedKeys() 方法的 KeyGenerator 实现类，适用于 MySQL、H2 主键生成。
 *
 * @author Clinton Begin
 * @author Kazuki Shimizu
 */
public class Jdbc3KeyGenerator implements KeyGenerator {

  /**
   * A shared instance.
   *
   *  共享的单例
   *
   * @since 3.4.3
   */
  public static final Jdbc3KeyGenerator INSTANCE = new Jdbc3KeyGenerator();

  /**
   * 空实现。因为对于 Jdbc3KeyGenerator 类的主键，是在 SQL 执行后，才生成。
   *
   * @param executor
   * @param ms
   * @param stmt
   * @param parameter
   */
  @Override
  public void processBefore(Executor executor, MappedStatement ms, Statement stmt, Object parameter) {
    // do nothing
  }

  /**
   * 调用 #processBatch(Executor executor, MappedStatement ms, Statement stmt, Object parameter) 方法，
   * 处理返回的自增主键。单个 parameter 参数，可以认为是批量的一个特例。
   *
   * @param executor
   * @param ms
   * @param stmt
   * @param parameter
   */
  @Override
  public void processAfter(Executor executor, MappedStatement ms, Statement stmt, Object parameter) {
    processBatch(ms, stmt, parameter);
  }

  public void processBatch(MappedStatement ms, Statement stmt, Object parameter) {
    // <1> 获得主键属性的配置。如果为空，则直接返回，说明不需要主键
    final String[] keyProperties = ms.getKeyProperties();
    if (keyProperties == null || keyProperties.length == 0) {
      return;
    }
    // <2> 获得返回的自增主键
    // 调用 Statement#getGeneratedKeys() 方法，获得返回的自增主键
    // 利用try特性，自动关闭
    try (ResultSet rs = stmt.getGeneratedKeys()) {
      final Configuration configuration = ms.getConfiguration();
      if (rs.getMetaData().getColumnCount() >= keyProperties.length) {
        // <3> 获得唯一的参数对象
        Object soleParam = getSoleParameter(parameter);
        if (soleParam != null) {
          // <3.1> 设置主键们，到参数 soleParam 中
          assignKeysToParam(configuration, rs, keyProperties, soleParam);
        } else {
          // <3.2> 设置主键们，到参数 parameter 中
          assignKeysToOneOfParams(configuration, rs, keyProperties, (Map<?, ?>) parameter);
        }
      }
    } catch (Exception e) {
      throw new ExecutorException("Error getting generated key or setting result to parameter object. Cause: " + e, e);
    }
  }

  protected void assignKeysToOneOfParams(final Configuration configuration, ResultSet rs, final String[] keyProperties,
      Map<?, ?> paramMap) throws SQLException {
    // Assuming 'keyProperty' includes the parameter name. e.g. 'param.id'.
    int firstDot = keyProperties[0].indexOf('.');
    if (firstDot == -1) {
      throw new ExecutorException(
          "Could not determine which parameter to assign generated keys to. "
              + "Note that when there are multiple parameters, 'keyProperty' must include the parameter name (e.g. 'param.id'). "
              + "Specified key properties are " + ArrayUtil.toString(keyProperties) + " and available parameters are "
              + paramMap.keySet());
    }
    String paramName = keyProperties[0].substring(0, firstDot);
    Object param;
    if (paramMap.containsKey(paramName)) {
      param = paramMap.get(paramName);
    } else {
      throw new ExecutorException("Could not find parameter '" + paramName + "'. "
          + "Note that when there are multiple parameters, 'keyProperty' must include the parameter name (e.g. 'param.id'). "
          + "Specified key properties are " + ArrayUtil.toString(keyProperties) + " and available parameters are "
          + paramMap.keySet());
    }
    // Remove param name from 'keyProperty' string. e.g. 'param.id' -> 'id'
    String[] modifiedKeyProperties = new String[keyProperties.length];
    for (int i = 0; i < keyProperties.length; i++) {
      if (keyProperties[i].charAt(firstDot) == '.' && keyProperties[i].startsWith(paramName)) {
        modifiedKeyProperties[i] = keyProperties[i].substring(firstDot + 1);
      } else {
        throw new ExecutorException("Assigning generated keys to multiple parameters is not supported. "
            + "Note that when there are multiple parameters, 'keyProperty' must include the parameter name (e.g. 'param.id'). "
            + "Specified key properties are " + ArrayUtil.toString(keyProperties) + " and available parameters are "
            + paramMap.keySet());
      }
    }
    assignKeysToParam(configuration, rs, modifiedKeyProperties, param);
  }

  private void assignKeysToParam(final Configuration configuration, ResultSet rs, final String[] keyProperties,
      Object param)
      throws SQLException {
    final TypeHandlerRegistry typeHandlerRegistry = configuration.getTypeHandlerRegistry();
    final ResultSetMetaData rsmd = rs.getMetaData();
    // Wrap the parameter in Collection to normalize the logic.
    Collection<?> paramAsCollection = null;
    if (param instanceof Object[]) {
      paramAsCollection = Arrays.asList((Object[]) param);
    } else if (!(param instanceof Collection)) {
      paramAsCollection = Arrays.asList(param);
    } else {
      paramAsCollection = (Collection<?>) param;
    }
    TypeHandler<?>[] typeHandlers = null;
    for (Object obj : paramAsCollection) {
      if (!rs.next()) {
        break;
      }
      MetaObject metaParam = configuration.newMetaObject(obj);
      if (typeHandlers == null) {
        typeHandlers = getTypeHandlers(typeHandlerRegistry, metaParam, keyProperties, rsmd);
      }
      populateKeys(rs, metaParam, keyProperties, typeHandlers);
    }
  }

  /**
   * 获得唯一的参数对象
   *
   * 如果获得不到唯一的参数对象，则返回 null
   *
   * @Options(useGeneratedKeys = true, keyProperty = "id")
   * @Insert({"insert into country (countryname,countrycode) values (#{country.countryname},#{country.countrycode})"})
   * int insertNamedBean(@Param("country") Country country);
   *
   *
   * @Options(useGeneratedKeys = true, keyProperty = "country.id")
   * @Insert({"insert into country (countryname, countrycode) values (#{country.countryname}, #{country.countrycode})"})
   * int insertMultiParams_keyPropertyWithWrongParamName2(@Param("country") Country country,
   *                                                      @Param("someId") Integer someId);
   *
   * @Options(useGeneratedKeys = true, keyProperty = "id")
   * @Insert({"insert into country (countryname, countrycode) values (#{country.countryname}, #{country.countrycode})"})
   * int insertMultiParams_keyPropertyWithWrongParamName3(@Param("country") Country country);
   *
   * @param parameter 参数对象
   * @return 唯一的参数对象
   */
  private Object getSoleParameter(Object parameter) {
    // <1> 如果非 Map 对象，则直接返回 parameter
    if (!(parameter instanceof ParamMap || parameter instanceof StrictMap)) {
      return parameter;
    }
    // <3> 如果是 Map 对象，则获取第一个元素的值
    // <2> 如果有多个元素，则说明获取不到唯一的参数对象，则返回 null
    Object soleParam = null;
    for (Object paramValue : ((Map<?, ?>) parameter).values()) {
      if (soleParam == null) {
        // 第一个元素
        soleParam = paramValue;
      } else if (soleParam != paramValue) {
        // 如果有多个元素
        soleParam = null;
        break;
      }
    }
    return soleParam;
  }

  private TypeHandler<?>[] getTypeHandlers(TypeHandlerRegistry typeHandlerRegistry, MetaObject metaParam, String[] keyProperties, ResultSetMetaData rsmd) throws SQLException {
    TypeHandler<?>[] typeHandlers = new TypeHandler<?>[keyProperties.length];
    for (int i = 0; i < keyProperties.length; i++) {
      if (metaParam.hasSetter(keyProperties[i])) {
        Class<?> keyPropertyType = metaParam.getSetterType(keyProperties[i]);
        typeHandlers[i] = typeHandlerRegistry.getTypeHandler(keyPropertyType, JdbcType.forCode(rsmd.getColumnType(i + 1)));
      } else {
        throw new ExecutorException("No setter found for the keyProperty '" + keyProperties[i] + "' in '"
            + metaParam.getOriginalObject().getClass().getName() + "'.");
      }
    }
    return typeHandlers;
  }

  private void populateKeys(ResultSet rs, MetaObject metaParam, String[] keyProperties, TypeHandler<?>[] typeHandlers) throws SQLException {
    for (int i = 0; i < keyProperties.length; i++) {
      String property = keyProperties[i];
      TypeHandler<?> th = typeHandlers[i];
      if (th != null) {
        Object value = th.getResult(rs, i + 1);
        metaParam.setValue(property, value);
      }
    }
  }

}
