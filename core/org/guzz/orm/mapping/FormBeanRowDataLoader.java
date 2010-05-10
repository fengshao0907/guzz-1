/*
 * Copyright 2008-2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.guzz.orm.mapping;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.guzz.orm.ObjectMapping;
import org.guzz.orm.type.SQLDataType;
import org.guzz.util.javabean.BeanCreator;
import org.guzz.util.javabean.BeanWrapper;
import org.guzz.util.javabean.JavaBeanWrapper;

/**
 * 
 * Map the ResultSet to any given class with set-xxx methods. The data type of each property is auto-detected
 * through the reflection from the given class.
 * 
 * <p>
 * If the class is a java.util.Map(for example:java.util.HashMap), the returned value will be put to the Map.
 * The map's key is the columnName, while the value is rs.getObject(columnName).
 * </p>
 *
 * @author liukaixuan(liukaixuan@gmail.com)
 */
public final class FormBeanRowDataLoader implements RowDataLoader {
	
	private final Class beanCls ;

	private final boolean isMap ;

	private final JavaBeanWrapper beanWrapper ;

	private final Map cachedDataTypes ;
	
	/**
	 * The column name in the database meta-data is case-insensitive, and the property name is case-sensitive.
	 * We map it with this. The key is low-cased property name, and the value is the original one.
	 */
	private final Map writableProps ;
	
	public static FormBeanRowDataLoader newInstanceForClass(Class beanCls){
		return new FormBeanRowDataLoader(beanCls) ;
	}
	
	protected FormBeanRowDataLoader(Class beanCls){
		this.beanCls = beanCls ;
		this.isMap = java.util.Map.class.isAssignableFrom(beanCls) ;
		
		if(!this.isMap){
			this.beanWrapper = BeanWrapper.createPOJOWrapper(beanCls) ;
			cachedDataTypes = new HashMap() ;
			this.writableProps = new HashMap() ;
			
			List beanProps = this.beanWrapper.getAllWritabeProps() ;
			for(int i = 0 ; i < beanProps.size() ; i++){
				String prop = (String) beanProps.get(i) ;
				writableProps.put(prop.toLowerCase(), prop) ;
			}
		}else{
			this.beanWrapper = null ;
			this.cachedDataTypes = null ;
			this.writableProps = null ;
		}
 	}

	public Object rs2Object(ObjectMapping mapping, ResultSet rs) throws SQLException {
		ResultSetMetaData meta = rs.getMetaData() ;
		int count = meta.getColumnCount() ;
		
		Object obj = BeanCreator.newBeanInstance(this.beanCls) ;
		
		if(isMap){
			for(int i = 1 ; i <= count ; i++){
				String colName = meta.getColumnName(i) ;
				((Map) obj).put(colName, rs.getObject(i)) ;
			}
		}else{
			for(int i = 1 ; i <= count ; i++){
				String colName = meta.getColumnName(i) ;
				String propName = (String) this.writableProps.get(colName.toLowerCase()) ;
				
				if(propName == null){
					//TODO:add warnings here in debug mode.
					continue ;
				}
				
				SQLDataType type = (SQLDataType) cachedDataTypes.get(propName) ;
					
				if(type != null){
					this.beanWrapper.setValue(obj, propName, type.getSQLValue(rs, i)) ;
				}else{
					String propType = this.beanWrapper.getPropertyTypeName(propName) ;
					type = mapping.getDbGroup().getDialect().getDataType(propType) ;
							
					this.beanWrapper.setValue(obj, propName, type.getSQLValue(rs, i)) ;
					cachedDataTypes.put(propName, type) ;
				}
			}
		}
		
		return obj ;
	}
	
	public Class getBeanCls() {
		return beanCls;
	}
	
	public JavaBeanWrapper getBeanWrapper() {
		return beanWrapper;
	}

}
