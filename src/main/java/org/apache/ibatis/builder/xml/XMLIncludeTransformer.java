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

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.builder.IncompleteElementException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.parsing.PropertyParser;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.session.Configuration;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * XML <include /> 标签的转换器，负责将 SQL 中的 <include /> 标签转换成对应的 <sql /> 的内容
 *
 * @author Frank D. Martinez [mnesarco]
 */
public class XMLIncludeTransformer {

  private final Configuration configuration;
  private final MapperBuilderAssistant builderAssistant;

  public XMLIncludeTransformer(Configuration configuration, MapperBuilderAssistant builderAssistant) {
    this.configuration = configuration;
    this.builderAssistant = builderAssistant;
  }
  /**
   * 将 <include /> 标签，替换成引用的 <sql />
   *
   * @param source
   */
  public void applyIncludes(Node source) {
    // <1> 创建 variablesContext ，并将 configurationVariables 添加到其中
    Properties variablesContext = new Properties();
    Properties configurationVariables = configuration.getVariables();
    if (configurationVariables != null) {
      variablesContext.putAll(configurationVariables);
    }
    // 处理 <include />
    applyIncludes(source, variablesContext, false);
  }

  /**
   * 将 <include /> 标签，替换成引用的 <sql />
   * 处理 <include />
   *
   * 使用递归的方式，将 <include /> 标签，替换成引用的 <sql />
   *
   * Recursively apply includes through all SQL fragments.
   * @param source Include node in DOM tree
   * @param variablesContext Current context for static variables with values
   */
  private void applyIncludes(Node source, final Properties variablesContext, boolean included) {
    // <1> 如果是 <include /> 标签
    if (source.getNodeName().equals("include")) {
      // <1.1> 获得 <sql /> 对应的节点
      Node toInclude = findSqlFragment(getStringAttribute(source, "refid"), variablesContext);
      // <1.2> 获得包含 <include /> 标签内的属性
      Properties toIncludeContext = getVariablesContext(source, variablesContext);
      // <1.3> 递归调用 #applyIncludes(...) 方法，继续替换。注意，此处是 <sql /> 对应的节点
      applyIncludes(toInclude, toIncludeContext, true);
      if (toInclude.getOwnerDocument() != source.getOwnerDocument()) {
        toInclude = source.getOwnerDocument().importNode(toInclude, true);
      }
      // <1.4> 将 <include /> 节点替换成 <sql /> 节点
      source.getParentNode().replaceChild(toInclude, source); // 注意，这是一个奇葩的 API ，前者为 newNode ，后者为 oldNode
      // <1.4> 将 <sql /> 子节点添加到 <sql /> 节点前面
      while (toInclude.hasChildNodes()) {
        // 这里有个点，一定要注意。当子节点添加到其它节点下面后，这个子节点会不见了，相当于是“移动操作”
        toInclude.getParentNode().insertBefore(toInclude.getFirstChild(), toInclude);
      }
      toInclude.getParentNode().removeChild(toInclude);
    } else if (source.getNodeType() == Node.ELEMENT_NODE) {
      if (included && !variablesContext.isEmpty()) {
        // replace variables in attribute values
        NamedNodeMap attributes = source.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
          Node attr = attributes.item(i);
          attr.setNodeValue(PropertyParser.parse(attr.getNodeValue(), variablesContext));
        }
      }
      NodeList children = source.getChildNodes();
      for (int i = 0; i < children.getLength(); i++) {
        applyIncludes(children.item(i), variablesContext, included);
      }
    } else if (included && source.getNodeType() == Node.TEXT_NODE
        && !variablesContext.isEmpty()) {
      // replace variables in text node
      source.setNodeValue(PropertyParser.parse(source.getNodeValue(), variablesContext));
    }
  }

  private Node findSqlFragment(String refid, Properties variables) {
    refid = PropertyParser.parse(refid, variables);
    refid = builderAssistant.applyCurrentNamespace(refid, true);
    try {
      XNode nodeToInclude = configuration.getSqlFragments().get(refid);
      return nodeToInclude.getNode().cloneNode(true);
    } catch (IllegalArgumentException e) {
      throw new IncompleteElementException("Could not find SQL statement to include with refid '" + refid + "'", e);
    }
  }

  private String getStringAttribute(Node node, String name) {
    return node.getAttributes().getNamedItem(name).getNodeValue();
  }

  /**
   * Read placeholders and their values from include node definition. 
   * @param node Include node instance
   * @param inheritedVariablesContext Current context used for replace variables in new variables values
   * @return variables context from include instance (no inherited values)
   */
  private Properties getVariablesContext(Node node, Properties inheritedVariablesContext) {
    Map<String, String> declaredProperties = null;
    NodeList children = node.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      Node n = children.item(i);
      if (n.getNodeType() == Node.ELEMENT_NODE) {
        String name = getStringAttribute(n, "name");
        // Replace variables inside
        String value = PropertyParser.parse(getStringAttribute(n, "value"), inheritedVariablesContext);
        if (declaredProperties == null) {
          declaredProperties = new HashMap<>();
        }
        if (declaredProperties.put(name, value) != null) {
          throw new BuilderException("Variable " + name + " defined twice in the same include definition");
        }
      }
    }
    if (declaredProperties == null) {
      return inheritedVariablesContext;
    } else {
      Properties newProperties = new Properties();
      newProperties.putAll(inheritedVariablesContext);
      newProperties.putAll(declaredProperties);
      return newProperties;
    }
  }
}
