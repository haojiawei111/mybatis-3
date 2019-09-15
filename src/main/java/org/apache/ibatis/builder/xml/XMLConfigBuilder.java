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
package org.apache.ibatis.builder.xml;

import java.io.InputStream;
import java.io.Reader;
import java.util.Properties;
import javax.sql.DataSource;

import org.apache.ibatis.builder.BaseBuilder;
import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.datasource.DataSourceFactory;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.executor.loader.ProxyFactory;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.io.VFS;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.reflection.DefaultReflectorFactory;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.reflection.ReflectorFactory;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.wrapper.ObjectWrapperFactory;
import org.apache.ibatis.session.AutoMappingBehavior;
import org.apache.ibatis.session.AutoMappingUnknownColumnBehavior;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.type.JdbcType;

/**
 * 继承 BaseBuilder 抽象类，XML 配置构建器，主要负责解析 mybatis-config.xml 配置文件
 *
 * @author Clinton Begin
 * @author Kazuki Shimizu
 */
public class XMLConfigBuilder extends BaseBuilder {

  /**
   * 是否已解析
   */
  private boolean parsed;
  /**
   * 基于 Java XPath 解析器
   */
  private final XPathParser parser;
  /**
   * 环境
   */
  private String environment;
  /**
   * ReflectorFactory 对象
   * 这个对象不是mybatis-config.xml  <reflectorFactory/> 节点里面配置的
   * 这个对象只是为了验证<settings>标签配置是否正确
   */
  private final ReflectorFactory localReflectorFactory = new DefaultReflectorFactory();

  public XMLConfigBuilder(Reader reader) {
    this(reader, null, null);
  }

  public XMLConfigBuilder(Reader reader, String environment) {
    this(reader, environment, null);
  }

  public XMLConfigBuilder(Reader reader, String environment, Properties props) {
    this(new XPathParser(reader, true, props, new XMLMapperEntityResolver()), environment, props);
  }

  public XMLConfigBuilder(InputStream inputStream) {
    this(inputStream, null, null);
  }

  public XMLConfigBuilder(InputStream inputStream, String environment) {
    this(inputStream, environment, null);
  }

  public XMLConfigBuilder(InputStream inputStream, String environment, Properties props) {
    this(new XPathParser(inputStream, true, props, new XMLMapperEntityResolver()), environment, props);
  }

  private XMLConfigBuilder(XPathParser parser, String environment, Properties props) {
    // <1> 创建 Configuration 对象
    super(new Configuration());
    ErrorContext.instance().resource("SQL Mapper Configuration");
    // <2> 设置 Configuration 的 variables 属性
    this.configuration.setVariables(props);
    // 解析完成的标志
    this.parsed = false;
    this.environment = environment;
    this.parser = parser;
  }

  /**
   * 解析 XML 成 Configuration 对象
   *
   * TODO:通过Configuration对象会创建SqlSessionFactory对象
   *
   * @return
   */
  public Configuration parse() {
    // <1.1> 若已解析，抛出 BuilderException 异常
    if (parsed) {
      throw new BuilderException("Each XMLConfigBuilder can only be used once.");
    }
    // <1.2> 已解析标记为true
    parsed = true;

    // <2> 解析 XML configuration 节点
    // 调用 XPathParser#evalNode(String expression) 方法，获得 XML <configuration /> 节点，
    // 后调用 #parseConfiguration(XNode root) 方法，解析该节点
    parseConfiguration(parser.evalNode("*[local-name()='configuration']"));

    return configuration;
  }

  private void parseConfiguration(XNode root) {
    try {
      //issue #117 read properties first
      // <1> 解析 <properties /> 标签
      propertiesElement(root.evalNode("*[local-name()='properties']"));

      // <2> 解析 <settings /> 标签
      Properties settings = settingsAsProperties(root.evalNode("*[local-name()='settings']"));

      // <3> 加载自定义 VFS 实现类
      // vfsImpl
      loadCustomVfs(settings);
      // logImpl
      loadCustomLogImpl(settings);

      // <4> 解析 <typeAliases /> 标签
      typeAliasesElement(root.evalNode("*[local-name()='typeAliases']"));

      // <5> 解析 <plugins /> 标签
      pluginElement(root.evalNode("*[local-name()='plugins']"));

      // <6> 解析 <objectFactory /> 标签
      objectFactoryElement(root.evalNode("*[local-name()='objectFactory']"));

      // <7> 解析 <objectWrapperFactory /> 标签
      objectWrapperFactoryElement(root.evalNode("*[local-name()='objectWrapperFactory']"));

      // <8> 解析 <reflectorFactory /> 标签
      reflectorFactoryElement(root.evalNode("*[local-name()='reflectorFactory']"));

      // <9> 赋值 <settings /> 到 Configuration 属性
      // 就是调用<setting> 标签里面的name在Configuration中响应的set方法，settingsAsProperties方法已经检查过所有配置的name在Configuration中都有对应的set方法
      settingsElement(settings);

      // read it after objectFactory and objectWrapperFactory issue #631
      // <10> 解析 <environments /> 标签
      environmentsElement(root.evalNode("*[local-name()='environments']"));

      // <11> 解析 <databaseIdProvider /> 标签
      databaseIdProviderElement(root.evalNode("*[local-name()='databaseIdProvider']"));

      // <12> 解析 <typeHandlers /> 标签
      typeHandlerElement(root.evalNode("*[local-name()='typeHandlers']"));

      // <13> 解析 <mappers /> 标签
      mapperElement(root.evalNode("*[local-name()='mappers']"));
    } catch (Exception e) {
      throw new BuilderException("Error parsing SQL Mapper Configuration. Cause: " + e, e);
    }
  }

  /**
   * 将 <setting /> 标签解析为 Properties 对象
   *
   * @param context
   * @return
   */
  private Properties settingsAsProperties(XNode context) {
    // 将子标签，解析成 Properties 对象
    if (context == null) {
      return new Properties();
    }
    Properties props = context.getChildrenAsProperties();
    // Check that all settings are known to the configuration class
    // TODO:校验每个属性，在 Configuration 中，有相应的 setting 方法，否则抛出 BuilderException 异常
    MetaClass metaConfig = MetaClass.forClass(Configuration.class, localReflectorFactory);
    for (Object key : props.keySet()) {
      if (!metaConfig.hasSetter(String.valueOf(key))) {
        throw new BuilderException("The setting " + key + " is not known.  Make sure you spelled it correctly (case sensitive).");
      }
    }
    return props;
  }

  /**
   * 加载自定义 VFS 实现类
   *
   * @param props
   * @throws ClassNotFoundException
   */
  private void loadCustomVfs(Properties props) throws ClassNotFoundException {
    // 获得 vfsImpl 属性
    String value = props.getProperty("vfsImpl");
    if (value != null) {
      // 使用 , 作为分隔符，拆成 VFS 类名的数组
      String[] clazzes = value.split(",");
      for (String clazz : clazzes) {
        if (!clazz.isEmpty()) {
          @SuppressWarnings("unchecked")
          Class<? extends VFS> vfsImpl = (Class<? extends VFS>)Resources.classForName(clazz);
          // 设置到 Configuration 中
          configuration.setVfsImpl(vfsImpl);
        }
      }
    }
  }

  /**
   * 加载自定义 logImpl 实现类
   * @param props
   */
  private void loadCustomLogImpl(Properties props) {
    Class<? extends Log> logImpl = resolveClass(props.getProperty("logImpl"));
    configuration.setLogImpl(logImpl);
  }

  /**
   * 解析 <typeAliases /> 标签，将配置类注册到 typeAliasRegistry 中
   *
   * @param parent
   */
  private void typeAliasesElement(XNode parent) {
    if (parent != null) {
      // 遍历子节点
      for (XNode child : parent.getChildren()) {
        // 指定为包的情况下，注册包下的每个类
        if ("package".equals(child.getName())) {
          String typeAliasPackage = child.getStringAttribute("name");
          configuration.getTypeAliasRegistry().registerAliases(typeAliasPackage);
        } else {
          // 指定为类的情况下，直接注册类和别名
          String alias = child.getStringAttribute("alias");
          String type = child.getStringAttribute("type");
          try {
            Class<?> clazz = Resources.classForName(type);// 获得类是否存在
            // 注册到 typeAliasRegistry 中
            if (alias == null) {
              typeAliasRegistry.registerAlias(clazz);
            } else {
              typeAliasRegistry.registerAlias(alias, clazz);
            }
          } catch (ClassNotFoundException e) {
            // 若类不存在，则抛出 BuilderException 异常
            throw new BuilderException("Error registering typeAlias for '" + alias + "'. Cause: " + e, e);
          }
        }
      }
    }
  }

  /**
   * 解析 <plugins /> 标签，添加到 Configuration#interceptorChain 中
   *
   * @param parent
   * @throws Exception
   */
  private void pluginElement(XNode parent) throws Exception {
    if (parent != null) {
      // 遍历 <plugins /> 标签
      for (XNode child : parent.getChildren()) {
        // <plugins /> 标签的子标签里的interceptor属性
        String interceptor = child.getStringAttribute("interceptor");
        // <plugins /> 标签的子标签的子标签，是interceptor的属性
        Properties properties = child.getChildrenAsProperties();
        // <1> 创建 Interceptor 拦截器对象，并设置属性
        Interceptor interceptorInstance = (Interceptor) resolveClass(interceptor).newInstance();
        interceptorInstance.setProperties(properties);
        // <2> 添加到 configuration 中
        configuration.addInterceptor(interceptorInstance);
      }
    }
  }

  /**
   * 解析 <objectFactory /> 节点
   *
   * @param context
   * @throws Exception
   */
  private void objectFactoryElement(XNode context) throws Exception {
    if (context != null) {
      // 获得 ObjectFactory 的实现类
      String type = context.getStringAttribute("type");
      // 获得 Properties 属性
      Properties properties = context.getChildrenAsProperties();
      // <1> 创建 ObjectFactory 对象，并设置 Properties 属性
      ObjectFactory factory = (ObjectFactory) resolveClass(type).newInstance();
      factory.setProperties(properties);
      // <2> 设置 Configuration 的 objectFactory 属性
      configuration.setObjectFactory(factory);
    }
  }

  /**
   * 解析 <objectWrapperFactory /> 节点
   *
   * @param context
   * @throws Exception
   */
  private void objectWrapperFactoryElement(XNode context) throws Exception {
    if (context != null) {
      // 获得 ObjectFactory 的实现类
      String type = context.getStringAttribute("type");
      // <1> 创建 ObjectWrapperFactory 对象
      ObjectWrapperFactory factory = (ObjectWrapperFactory) resolveClass(type).newInstance();
      // 设置 Configuration 的 objectWrapperFactory 属性
      configuration.setObjectWrapperFactory(factory);
    }
  }

  /**
   * 解析 <reflectorFactory /> 节点
   *
   * @param context
   * @throws Exception
   */
  private void reflectorFactoryElement(XNode context) throws Exception {
    if (context != null) {
      // 获得 ReflectorFactory 的实现类
      String type = context.getStringAttribute("type");
      // 创建 ReflectorFactory 对象
      ReflectorFactory factory = (ReflectorFactory) resolveClass(type).newInstance();
      // 设置 Configuration 的 reflectorFactory 属性
       configuration.setReflectorFactory(factory);
    }
  }

  /**
   * 解析 <properties /> 节点
   *
   * @param context
   * @throws Exception
   */
  private void propertiesElement(XNode context) throws Exception {
    if (context != null) {
      // 读取子标签们，为 Properties 对象
      Properties defaults = context.getChildrenAsProperties();
      // 读取 resource 和 url 属性
      String resource = context.getStringAttribute("resource");
      String url = context.getStringAttribute("url");
      if (resource != null && url != null) {
        // TODO:resource 和 url 都存在的情况下，抛出 BuilderException 异常
        throw new BuilderException("The properties element cannot specify both a URL and a resource based property file reference.  Please specify one or the other.");
      }

      // 读取本地 Properties 配置文件到 defaults 中。
      if (resource != null) {
        defaults.putAll(Resources.getResourceAsProperties(resource));
        // 读取远程 Properties 配置文件到 defaults 中。
      } else if (url != null) {
        defaults.putAll(Resources.getUrlAsProperties(url));
      }

      // 覆盖 configuration 中的 Properties 对象到 defaults 中。
      Properties vars = configuration.getVariables();
      if (vars != null) {
        defaults.putAll(vars);
      }
      // 设置 defaults 到 parser 和 configuration 中。
      parser.setVariables(defaults);
      configuration.setVariables(defaults);
    }
  }

  /**
   * TODO: 赋值 <settings /> 到 Configuration 属性
   *
   * @param props
   */
  private void settingsElement(Properties props) {
    configuration.setAutoMappingBehavior(AutoMappingBehavior.valueOf(props.getProperty("autoMappingBehavior", "PARTIAL")));
    configuration.setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior.valueOf(props.getProperty("autoMappingUnknownColumnBehavior", "NONE")));
    configuration.setCacheEnabled(booleanValueOf(props.getProperty("cacheEnabled"), true));
    configuration.setProxyFactory((ProxyFactory) createInstance(props.getProperty("proxyFactory")));
    configuration.setLazyLoadingEnabled(booleanValueOf(props.getProperty("lazyLoadingEnabled"), false));
    configuration.setAggressiveLazyLoading(booleanValueOf(props.getProperty("aggressiveLazyLoading"), false));
    configuration.setMultipleResultSetsEnabled(booleanValueOf(props.getProperty("multipleResultSetsEnabled"), true));
    configuration.setUseColumnLabel(booleanValueOf(props.getProperty("useColumnLabel"), true));
    configuration.setUseGeneratedKeys(booleanValueOf(props.getProperty("useGeneratedKeys"), false));
    configuration.setDefaultExecutorType(ExecutorType.valueOf(props.getProperty("defaultExecutorType", "SIMPLE")));
    configuration.setDefaultStatementTimeout(integerValueOf(props.getProperty("defaultStatementTimeout"), null));
    configuration.setDefaultFetchSize(integerValueOf(props.getProperty("defaultFetchSize"), null));
    configuration.setMapUnderscoreToCamelCase(booleanValueOf(props.getProperty("mapUnderscoreToCamelCase"), false));
    configuration.setSafeRowBoundsEnabled(booleanValueOf(props.getProperty("safeRowBoundsEnabled"), false));
    configuration.setLocalCacheScope(LocalCacheScope.valueOf(props.getProperty("localCacheScope", "SESSION")));
    configuration.setJdbcTypeForNull(JdbcType.valueOf(props.getProperty("jdbcTypeForNull", "OTHER")));
    configuration.setLazyLoadTriggerMethods(stringSetValueOf(props.getProperty("lazyLoadTriggerMethods"), "equals,clone,hashCode,toString"));
    configuration.setSafeResultHandlerEnabled(booleanValueOf(props.getProperty("safeResultHandlerEnabled"), true));
    configuration.setDefaultScriptingLanguage(resolveClass(props.getProperty("defaultScriptingLanguage")));
    configuration.setDefaultEnumTypeHandler(resolveClass(props.getProperty("defaultEnumTypeHandler")));
    configuration.setCallSettersOnNulls(booleanValueOf(props.getProperty("callSettersOnNulls"), false));
    configuration.setUseActualParamName(booleanValueOf(props.getProperty("useActualParamName"), true));
    configuration.setReturnInstanceForEmptyRow(booleanValueOf(props.getProperty("returnInstanceForEmptyRow"), false));
    configuration.setLogPrefix(props.getProperty("logPrefix"));
    configuration.setConfigurationFactory(resolveClass(props.getProperty("configurationFactory")));
  }

  /**
   * 解析 <environments /> 标签
   *
   * @param context
   * @throws Exception
   */
  private void environmentsElement(XNode context) throws Exception {
    if (context != null) {
      // <1> environment 属性非空，从 default 属性获得
      if (environment == null) {
        // 获取environments的default 属性
        environment = context.getStringAttribute("default");
      }
      // 遍历 <environment> 节点
      for (XNode child : context.getChildren()) {
        // <2> 判断 environment 是否匹配
        // 遍历 XNode 节点，获得其 id 属性，后调用 #isSpecifiedEnvironment(String id) 方法，判断 environment 和 id 是否匹配
        String id = child.getStringAttribute("id");
        // TODO: 判断id和environment是否相等
        if (isSpecifiedEnvironment(id)) {
          // <3> 解析 `<transactionManager />` 标签，返回 TransactionFactory 对象
          // 调用 #transactionManagerElement(XNode context) 方法，解析 <transactionManager /> 标签，返回 TransactionFactory 对象
          // 创建事务管理器
          TransactionFactory txFactory = transactionManagerElement(child.evalNode("*[local-name()='transactionManager']"));
          // <4> 解析 `<dataSource />` 标签，返回 DataSourceFactory 对象
          // 调用 #dataSourceElement(XNode context) 方法，解析 <dataSource /> 标签，返回 DataSourceFactory 对象，而后返回 DataSource 对象
          DataSourceFactory dsFactory = dataSourceElement(child.evalNode("dataSource"));
          DataSource dataSource = dsFactory.getDataSource();
          // <5> 创建 Environment.Builder 对象，设置DataSource和TransactionFactory
          Environment.Builder environmentBuilder = new Environment.Builder(id)
              .transactionFactory(txFactory)
              .dataSource(dataSource);
          // <6> 构造 Environment 对象，并设置到 configuration 中
          configuration.setEnvironment(environmentBuilder.build());
        }
      }
    }
  }

  /**
   * 解析 <databaseIdProvider /> 标签
   *
   * @param context
   * @throws Exception
   */
  private void databaseIdProviderElement(XNode context) throws Exception {
    DatabaseIdProvider databaseIdProvider = null;
    if (context != null) {
      // <1> 获得 DatabaseIdProvider 的类
      String type = context.getStringAttribute("type");
      // awful patch to keep backward compatibility 保持兼容
      if ("VENDOR".equals(type)) {
          type = "DB_VENDOR";
      }
      // <2> 获得 Properties 对象
      Properties properties = context.getChildrenAsProperties();
      // <3> 创建 DatabaseIdProvider 对象，并设置对应的属性
      databaseIdProvider = (DatabaseIdProvider) resolveClass(type).newInstance();
      databaseIdProvider.setProperties(properties);
    }

    Environment environment = configuration.getEnvironment();
    if (environment != null && databaseIdProvider != null) {
      // <4> 获得对应的 databaseId 编号
      String databaseId = databaseIdProvider.getDatabaseId(environment.getDataSource());
      // <5> 设置到 configuration 中
      configuration.setDatabaseId(databaseId);
    }
  }

  private TransactionFactory transactionManagerElement(XNode context) throws Exception {
    if (context != null) {
      // 获得 TransactionFactory 的类
      String type = context.getStringAttribute("type");
      // 获得 Properties 属性
      Properties props = context.getChildrenAsProperties();
      // 创建 TransactionFactory 对象，并设置属性
      TransactionFactory factory = (TransactionFactory) resolveClass(type).newInstance();
      factory.setProperties(props);
      return factory;
    }
    throw new BuilderException("Environment declaration requires a TransactionFactory.");
  }

  private DataSourceFactory dataSourceElement(XNode context) throws Exception {
    if (context != null) {
      // 获得 DataSourceFactory 的类
      String type = context.getStringAttribute("type");
      // 获得 Properties 属性
      Properties props = context.getChildrenAsProperties();
      // 创建 DataSourceFactory 对象，并设置属性
      DataSourceFactory factory = (DataSourceFactory) resolveClass(type).newInstance();
      factory.setProperties(props);
      return factory;
    }
    throw new BuilderException("Environment declaration requires a DataSourceFactory.");
  }

  /**
   * 解析 <typeHandlers /> 标签
   *
   * 配置 类型处理器
   *
   * @param parent
   */
  private void typeHandlerElement(XNode parent) {
    if (parent != null) {
      // 遍历子节点
      for (XNode child : parent.getChildren()) {
        // <1> 如果是 package 标签，则扫描该包
        if ("package".equals(child.getName())) {
          String typeHandlerPackage = child.getStringAttribute("name");
          typeHandlerRegistry.register(typeHandlerPackage);
        } else {
          // <2> 如果是 typeHandler 标签，则注册该 typeHandler 信息
          String javaTypeName = child.getStringAttribute("javaType");
          String jdbcTypeName = child.getStringAttribute("jdbcType");
          String handlerTypeName = child.getStringAttribute("handler");
          Class<?> javaTypeClass = resolveClass(javaTypeName);
          JdbcType jdbcType = resolveJdbcType(jdbcTypeName);
          Class<?> typeHandlerClass = resolveClass(handlerTypeName);// 非空
          // 注册 typeHandler
          if (javaTypeClass != null) {
            if (jdbcType == null) {
              // 配置了handler、javaType
              typeHandlerRegistry.register(javaTypeClass, typeHandlerClass);
            } else {
              // 配置了handler、javaType 和 jdbcType
              typeHandlerRegistry.register(javaTypeClass, jdbcType, typeHandlerClass);
            }
          } else {
            // 配置了 handler ，handler类上面可以配置MappedTypes注解
            typeHandlerRegistry.register(typeHandlerClass);
          }
        }
      }
    }
  }

  /**
   * 解析 <mappers /> 标签
   *
   * @param parent
   * @throws Exception
   */
  private void mapperElement(XNode parent) throws Exception {
    if (parent != null) {
      // <0> 遍历子节点
      for (XNode child : parent.getChildren()) {
        // <1> 如果是 package 标签，则扫描该包
        if ("package".equals(child.getName())) {
          // 获得包名
          String mapperPackage = child.getStringAttribute("name");
          // 添加到 configuration 中
          configuration.addMappers(mapperPackage);
        } else {
          // 如果是 mapper 标签
          // <1> 如果是 package 标签，则扫描该包
          String resource = child.getStringAttribute("resource");
          String url = child.getStringAttribute("url");
          String mapperClass = child.getStringAttribute("class");
          // <2> 使用相对于类路径的资源引用
          if (resource != null && url == null && mapperClass == null) {//配置了resource
            ErrorContext.instance().resource(resource);
            // 获得 resource 的 InputStream 对象
            InputStream inputStream = Resources.getResourceAsStream(resource);
            // 创建 XMLMapperBuilder 对象
            XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, resource, configuration.getSqlFragments());
            // 执行解析
            mapperParser.parse();
          } else if (resource == null && url != null && mapperClass == null) {//配置了url
            // <3> 使用完全限定资源定位符（URL）
            ErrorContext.instance().resource(url);
            // 获得 url 的 InputStream 对象
            InputStream inputStream = Resources.getUrlAsStream(url);
            // 创建 XMLMapperBuilder 对象
            XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, url, configuration.getSqlFragments());
            // 执行解析
            mapperParser.parse();
          } else if (resource == null && url == null && mapperClass != null) {//配置了mapperClass
            // <4> 使用映射器接口实现类的完全限定类名
            // 获得 Mapper 接口
            Class<?> mapperInterface = Resources.classForName(mapperClass);
            // 添加到 configuration 中
            configuration.addMapper(mapperInterface);
          } else {
            throw new BuilderException("A mapper element may only specify a url, resource or class, but not more than one.");
          }
        }
      }
    }
  }

  private boolean isSpecifiedEnvironment(String id) {
    if (environment == null) {
      throw new BuilderException("No environment specified.");
    } else if (id == null) {
      throw new BuilderException("Environment requires an id attribute.");
    } else if (environment.equals(id)) { // // 相等
      return true;
    }
    return false;
  }

}
