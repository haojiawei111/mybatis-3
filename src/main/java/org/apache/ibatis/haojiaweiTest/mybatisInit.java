package org.apache.ibatis.haojiaweiTest;

import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;

public class mybatisInit {

	public static void main(String[] args) throws IOException {
		InputStream inputStream = Resources.getResourceAsStream("mybatis-config.xml");

		SqlSessionFactory sqlSessionFactory = new SqlSessionFactoryBuilder().build(inputStream);
	}

}
