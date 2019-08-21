/**
 * Copyright 2009-2019 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.ibatis.executor.statement;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.ExecutorException;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

/**
 * 实现 StatementHandler 接口，路由的 StatementHandler 对象，根据 Statement 类型，转发到对应的 StatementHandler 实现类中。
 *
 * StatementHandler 对象究竟在 MyBatis 中，是如何被创建的呢？
 * Configuration 类中，提供 #newStatementHandler(Executor executor, MappedStatement mappedStatement, Object parameterObject, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) 方法
 *
 * @author Clinton Begin
 */
public class RoutingStatementHandler implements StatementHandler {

	/**
	 * 被委托的 StatementHandler 对象
	 */
	private final StatementHandler delegate;

	/**
	 * 根据不同的类型，创建对应的 StatementHandler 实现类
	 * 经典的装饰器模式。实际上，有点多余。。。还不如改成工厂模式
	 *
	 * @param executor
	 * @param ms
	 * @param parameter
	 * @param rowBounds
	 * @param resultHandler
	 * @param boundSql
	 */
	public RoutingStatementHandler(Executor executor, MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) {
		// 根据不同的类型，创建对应的 StatementHandler 实现类
		switch (ms.getStatementType()) {
			case STATEMENT:
				delegate = new SimpleStatementHandler(executor, ms, parameter, rowBounds, resultHandler, boundSql);
				break;
			case PREPARED:
				delegate = new PreparedStatementHandler(executor, ms, parameter, rowBounds, resultHandler, boundSql);
				break;
			case CALLABLE:
				delegate = new CallableStatementHandler(executor, ms, parameter, rowBounds, resultHandler, boundSql);
				break;
			default:
				throw new ExecutorException("Unknown statement type: " + ms.getStatementType());
		}
	}

	/*******************************所有的实现方法，调用 delegate 对应的方法即可*************************************/
	@Override
	public Statement prepare(Connection connection, Integer transactionTimeout) throws SQLException {
		return delegate.prepare(connection, transactionTimeout);
	}

	@Override
	public void parameterize(Statement statement) throws SQLException {
		delegate.parameterize(statement);
	}

	@Override
	public void batch(Statement statement) throws SQLException {
		delegate.batch(statement);
	}

	@Override
	public int update(Statement statement) throws SQLException {
		return delegate.update(statement);
	}

	@Override
	public <E> List<E> query(Statement statement, ResultHandler resultHandler) throws SQLException {
		return delegate.<E>query(statement, resultHandler);
	}

	@Override
	public <E> Cursor<E> queryCursor(Statement statement) throws SQLException {
		return delegate.queryCursor(statement);
	}

	@Override
	public BoundSql getBoundSql() {
		return delegate.getBoundSql();
	}

	@Override
	public ParameterHandler getParameterHandler() {
		return delegate.getParameterHandler();
	}
}
