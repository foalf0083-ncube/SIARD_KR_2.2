// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
/*======================================================================
CubridDatabaseMetaData implements a wrapped Cubrid DatabaseMetaData.
Version     : $Id: $
Application : SIARD2
Description : CubridDatabaseMetaData implements a wrapped Cubrid DatabaseMetaData.
Platform    : Java 7   
------------------------------------------------------------------------
Copyright  : 2016, Enter AG, Rüti ZH, Switzerland
Created    : 27.10.2016, Simon Jutz
======================================================================*/
package ch.admin.bar.siard2.jdbc;

import java.sql.*;
import java.util.ArrayList;

import ch.enterag.utils.jdbc.*;
import ch.enterag.sqlparser.*;

/* =============================================================================== */
/**
 * CubridDatabaseMetaData implements a wrapped Cubrid DatabaseMetaData
 * @author Byungwook Kim
 */
public class CUBRIDDatabaseMetaData 
  extends BaseDatabaseMetaData 
  implements DatabaseMetaData 
{
	boolean is_closed;
	int major_version;
	int minor_version;
	
	private CUBRIDConnection _conn = null;
	/**
	 * Constructor 
	 * @param dmdWrapped database meta data to wrapped
	 */
	public CUBRIDDatabaseMetaData(DatabaseMetaData dmdWrapped, CUBRIDConnection conn)
	{
		super(dmdWrapped);
		_conn = conn;
	} /* constructor */

  /*------------------------------------------------------------------*/
  /** {@inheritDoc} */
  @Override
  public Connection getConnection() throws SQLException
  {
    return _conn;
  } /* getConnection */
  
	/* ------------------------------------------------------------------------ */
	/**
	 * {@inheritDoc}
	 */
	@Override
	public ResultSet getBestRowIdentifier(String catalog, String schema, String table, int scope, boolean nullable) throws SQLException {
		if (catalog != null) {
			throw new SQLException("Cubrid does not support catalogs, argument must be NULL.");
		}
		return super.getBestRowIdentifier(schema, catalog, table, scope, nullable);
	} /* getBestRowIdentifier */

	/* ------------------------------------------------------------------------ */
	/**
	 * {@inheritDoc}
	 */
	@Override
	public ResultSet getVersionColumns(String catalog, String schema, String table) throws SQLException 
	{
	  ResultSet rs = null;
		if (catalog == null)
		  rs = super.getVersionColumns(schema, catalog, table);
		else
			throw new SQLException("Cubrid does not support catalogs, argument must be NULL.");
		return rs; 
	} /* getVersionColumns */

	/* ------------------------------------------------------------------------ */
	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean supportsResultSetConcurrency(int type, int concurrency) throws SQLException {
		return super.supportsResultSetConcurrency(type, concurrency);
	} /* supportsResultSetConcurrency */

	/* ------------------------------------------------------------------------ */
	/**
	 * {@inheritDoc}
	 */
	@Override
	public ResultSet getIndexInfo(String catalog, String schema, String table, boolean unique, boolean approximate) throws SQLException {
		if (catalog != null) {
			throw new SQLException("Cubrid does not support catalogs, argument must be NULL.");
		}
		
		return super.getIndexInfo(catalog, schema, qualifyForVendor(schema, table), unique, approximate);
	} /* getIndexInfo */

	/* ------------------------------------------------------------------------ */
	/**
	 * {@inheritDoc}
	 */
	@Override
	public ResultSet getColumnPrivileges(String catalog, String schema, String table, String columnNamePattern) throws SQLException {
		if (catalog != null) {
			throw new SQLException("Cubrid does not support catalogs, argument must be NULL.");
		}
		return super.getColumnPrivileges(schema, catalog, table, columnNamePattern);
	} /* getColumnPrivileges */

	/* ------------------------------------------------------------------------ */
	/**
	 * {@inheritDoc}
	 */
	@Override
	public ResultSet getTablePrivileges(String catalog, String schemaPattern, String tableNamePattern) throws SQLException {
		if (catalog != null) {
			throw new SQLException("Cubrid does not support catalogs, argument must be NULL.");
		}
		
		return super.getTablePrivileges(catalog, schemaPattern, tableNamePattern);
	} /* getTablePrivileges */

	/* ------------------------------------------------------------------------ */
	/**
	 * {@inheritDoc}
	 */
	@Override
	public ResultSet getPrimaryKeys(String catalog, String schema, String table) throws SQLException 
	{
		if (catalog != null) { throw new SQLException("Cubrid does not support catalogs, argument must be NULL."); }
		
		return super.getPrimaryKeys(catalog, schema, qualifyForVendor(schema, table));
	} /* getPrimaryKeys */

	/* ------------------------------------------------------------------------ */
	/**
	 * {@inheritDoc}
	 */
	@Override
	public ResultSet getAttributes(String catalog, String schemaPattern, String typeNamePattern, String attributeNamePattern) throws SQLException {
		if (catalog != null) {
			throw new SQLException("Cubrid does not support catalogs, argument must be NULL.");
		}
		return super.getAttributes(schemaPattern, catalog, typeNamePattern, attributeNamePattern);
	} /* getAttributes */

  /* ------------------------------------------------------------------------ */
	@SuppressWarnings("unused")
	private String getNameCondition(String sColumn, String sName)
	{
	  StringBuilder sbCondition = new StringBuilder();
    if (sName != null)
    {
      sbCondition.append("  AND ");
      if (sName.startsWith("\""))
      {
        sbCondition.append(sColumn);
        sbCondition.append(" = ");
        sbCondition.append(SqlLiterals.formatStringLiteral(sName));
      }
      else
      {
        sbCondition.append("LOWER(");
        sbCondition.append(sColumn);
        sbCondition.append(") = LOWER(");
        sbCondition.append(SqlLiterals.formatStringLiteral(sName));
        sbCondition.append(")");
      }
      sbCondition.append("\r\n");
    }
    return sbCondition.toString();
	} /* getNameCondition */
	
  /* ------------------------------------------------------------------------ */
  /**
   * {@inheritDoc}
   */
  @Override
  public ResultSet getCrossReference(
    String parentCatalog, String parentSchema, String parentTable, 
    String foreignCatalog, String foreignSchema, String foreignTable) 
      throws SQLException 
  {
    if (parentCatalog != null || foreignCatalog != null) 
      throw new SQLException("Cubrid does not support catalogs, argument must be NULL.");
    
    return super.getCrossReference(parentCatalog, parentSchema, parentTable, foreignCatalog, foreignSchema, foreignTable);
  } /* getCrossReference */

  /* ------------------------------------------------------------------------ */
  /**
   * {@inheritDoc}
   */
  @Override
  public ResultSet getImportedKeys(String catalog, String schema, String table) throws SQLException 
  {
    if (catalog != null) { throw new SQLException("Cubrid does not support catalogs, argument must be NULL."); }
 
	return super.getImportedKeys(catalog, schema, qualifyForVendor(schema, table));
  } /* getImportedKeys */

	/* ------------------------------------------------------------------------ */
	/**
	 * {@inheritDoc}
	 */
	@Override
	public ResultSet getExportedKeys(String catalog, String schema, String table) throws SQLException 
	{
		if (catalog != null) { 
			throw new SQLException("Cubrid does not support catalogs, argument must be NULL.");
		}
		
		return super.getExportedKeys(catalog, schema, qualifyForVendor(schema, table));
	} /* getExportedKeys */

	/* ------------------------------------------------------------------------ */
	/**
	 * {@inheritDoc}
	 */
	@Override
	public ResultSet getFunctionColumns(String catalog, String schemaPattern, String functionNamePattern, String columnNamePattern) throws SQLException {
		if (catalog != null) {
			throw new SQLException("Cubrid does not support catalogs, argument must be NULL.");
		}
		
		StringBuilder sb = new StringBuilder();
		sb.append("SELECT  NULL AS FUNCTION_CAT,\n" + 
				"'" +	currentSchemaSafe() + "' AS FUNCTION_SCHEM, \n" + 
				"		a.SP_NAME AS FUNCTION_NAME, \n" + 
				"		arg_name AS COLUMN_NAME, \n" + 
				"		CASE \n" + 
				"			WHEN MODE = 'OUT' THEN 4 \n" + 
				"			WHEN MODE = 'INOUT' THEN 2 \n" + 
				"			WHEN MODE = 'IN' THEN 1 \n" + 
				"			WHEN MODE = NULL THEN 5 \n" + 
				"		ELSE 0 END AS COLUMN_TYPE, \n" + 
				"		-1 AS DATA_TYPE, \n" + 
				"		DATA_TYPE AS TYPE_NAME, \n" + 
				"		NULL AS `PRECISION`, \n" + 
				"		NULL AS LENGTH, \n" + 
				"		NULL AS SCALE, \n" + 
				"		10 AS RADIX, \n" + 
				"		0 AS NULLABLE, \n" + 
				"		NULL AS REMARKS, \n" + 
				"		NULL AS CHAR_OCTET_LENGTH, \n" + 
				"		index_of + 1 as ORDINAL_POSITION, \n" + 
				"		'' AS IS_NULLABLE, \n" + 
				"		NULL as SPECIFIC_NAME \n" + 
				"FROM db_stored_procedure p, db_stored_procedure_args a \n");

		// where clause criteria
		ArrayList<String> whereClauseComponents = new ArrayList<String>();
		whereClauseComponents.add("P.SP_TYPE = 'FUNCTION'");
		whereClauseComponents.add("P.SP_NAME = A.SP_NAME");
		
		if(functionNamePattern != null) {
			whereClauseComponents.add("P.SP_NAME LIKE " + SqlLiterals.formatStringLiteral(functionNamePattern));
		}
		if(columnNamePattern != null) {
			whereClauseComponents.add("A.ARG_NAME LIKE " + SqlLiterals.formatStringLiteral(columnNamePattern));
		}

		StringBuilder sbWhereClause = new StringBuilder();
		for(int i=0; i<whereClauseComponents.size(); i++) {
			if(i>0) {
				sbWhereClause.append(" AND ");
			}
			sbWhereClause.append(whereClauseComponents.get(i));
		}
		sb.append("WHERE ");
		sb.append(sbWhereClause);
		sb.append("\r\n");

		// order according to the JDBC specification
		sb.append("ORDER BY FUNCTION_CAT, FUNCTION_SCHEM, FUNCTION_NAME, SPECIFIC_NAME, ORDINAL_POSITION");
		
		Statement stmt = this.getConnection().createStatement();
		return new CUBRIDMetaColumns(stmt.unwrap(Statement.class).executeQuery(sb.toString()), 6, 7, 8, 9, _conn);
	} /* getFunctionColumns */

	/* ------------------------------------------------------------------------ */
	/**
	 * {@inheritDoc}
	 */
	@Override
	public ResultSet getProcedureColumns(String catalog, String schemaPattern, String procedureNamePattern, String columnNamePattern) throws SQLException {
		if (catalog != null) {
			throw new SQLException("Cubrid does not support catalogs, argument must be NULL.");
		}

		StringBuilder sb = new StringBuilder();
		sb.append("SELECT  NULL AS PROCEDURE_CAT,\n" + 
				"'" +	currentSchemaSafe() + "' AS PROCEDURE_SCHEM, \n" + 
				"		a.SP_NAME AS PROCEDURE_NAME, \n" + 
				"		arg_name AS COLUMN_NAME, \n" + 
				"		CASE \n" + 
				"			WHEN MODE = 'OUT' THEN 4 \n" + 
				"			WHEN MODE = 'INOUT' THEN 2 \n" + 
				"			WHEN MODE = 'IN' THEN 1 \n" + 
				"			WHEN MODE = NULL THEN 5 \n" + 
				"		ELSE 0 END AS COLUMN_TYPE, \n" + 
				"		-1 AS DATA_TYPE, \n" + 
				"		DATA_TYPE AS TYPE_NAME, \n" + 
				"		NULL AS `PRECISION`, \n" + 
				"		NULL AS LENGTH, \n" + 
				"		NULL AS SCALE, \n" + 
				"		10 AS RADIX, \n" + 
				"		0 AS NULLABLE, \n" + 
				"		NULL AS REMARKS, \n" + 
				"		NULL AS CHAR_OCTET_LENGTH, \n" + 
				"		index_of + 1 as ORDINAL_POSITION, \n" + 
				"		'' AS IS_NULLABLE, \n" + 
				"		NULL as SPECIFIC_NAME \n" + 
				"FROM db_stored_procedure p, db_stored_procedure_args a \n");

		// where clause criteria
		ArrayList<String> whereClauseComponents = new ArrayList<String>();
		whereClauseComponents.add("P.SP_TYPE = 'PROCEDURE'");
		whereClauseComponents.add("P.SP_NAME = A.SP_NAME");
		
		if(procedureNamePattern != null) {
			whereClauseComponents.add("P.SP_NAME LIKE " + SqlLiterals.formatStringLiteral(procedureNamePattern));
		}
		if(columnNamePattern != null) {
			whereClauseComponents.add("A.ARG_NAME LIKE " + SqlLiterals.formatStringLiteral(columnNamePattern));
		}

		StringBuilder sbWhereClause = new StringBuilder();
		for(int i=0; i<whereClauseComponents.size(); i++) {
			if(i>0) {
				sbWhereClause.append(" AND ");
			}
			sbWhereClause.append(whereClauseComponents.get(i));
		}
		sb.append("WHERE ");
		sb.append(sbWhereClause);
		sb.append("\r\n");

		// order according to the JDBC specification
		sb.append("ORDER BY PROCEDURE_CAT, PROCEDURE_SCHEM, PROCEDURE_NAME, SPECIFIC_NAME, ORDINAL_POSITION");
		
		Statement stmt = this.getConnection().createStatement();
		return new CUBRIDMetaColumns(stmt.unwrap(Statement.class).executeQuery(sb.toString()), 6, 7, 8, 9, _conn);
	} /* getProcedureColumns */

	/* ------------------------------------------------------------------------ */
	/**
	 * {@inheritDoc}
	 */
	public ResultSet getTriggers(String catalog, String schemaPattern, String tableNamePattern, String triggerNamePattern) throws SQLException {
		if (catalog != null) {
			throw new SQLException("Cubrid does not support catalogs, argument must be NULL.");
		}

		StringBuilder sbCondition = new StringBuilder();
		String cond;
		
		sbCondition.append("target_class in (SELECT class_of FROM db_class WHERE");
		
		if ((triggerNamePattern == null) || triggerNamePattern.equals("%"))
		    {
		      sbCondition.append(" class_name LIKE '%' ");      
		    }
		else
		    {
			  cond = SqlLiterals.formatStringLiteral(triggerNamePattern);
		      sbCondition.append(" class_name LIKE ");
		      sbCondition.append(cond);
		    }		 
		
		sbCondition.append(")");
	    
		ResultSet rsTriggers = null;

	    String sSql = "SELECT\r\n" +
	    		"name,\r\n" +
	    		"status,\r\n" +
	    		"priority,\r\n" +
	    		"event,\r\n" +
	    		"target_attribute,\r\n" +
	    		"condition_type,\r\n" +
	    		"condition,\r\n" +
	    		"condition_time,\r\n" +
	    		"action_type,\r\n" +
	    		"action_definition,\r\n" +
	    		"action_time,\r\n" +
	    		"comment\r\n" +
			"FROM db_trigger\r\n" +
	 		"WHERE " + sbCondition;
		
		  Statement stmt = getConnection().createStatement();
		  rsTriggers = stmt.unwrap(Statement.class).executeQuery(sSql);
		  return rsTriggers;
	} /* getProcedures */
	
	/* ------------------------------------------------------------------------ */
	/**
	 * {@inheritDoc}
	 */
	@Override
	public ResultSet getFunctions(String catalog, String schemaPattern, String functionNamePattern) throws SQLException {
		if (catalog != null) {
			throw new SQLException("Cubrid does not support catalogs, argument must be NULL.");
		}
		
		StringBuilder sbCondition = new StringBuilder();
		
		sbCondition.append("SP_TYPE = 'FUNCTION' ");
		if ((functionNamePattern == null) || functionNamePattern.equals("%"))
		    {
		      sbCondition.append("AND SP_NAME LIKE '%' ");
		      
		    }
		else
		    {
		      sbCondition.append("AND SP_NAME LIKE ");
		      sbCondition.append(SqlLiterals.formatStringLiteral(functionNamePattern));
		    }
		 
		ResultSet rsFunctions = null;
		    
	    String sSql = "SELECT\r\n" +
	    		"NULL as FUNCTION_CAT,\r\n" +
	    		"'" + currentSchemaSafe() + "' as FUNCTION_SCHEM,\r\n" +
	    		"SP_NAME AS FUNCTION_NAME,\r\n" +
	    		"'RETURN ' + return_type + ' AS Language ' + lang + ' NAME ' + '''' + target + '''' as REMARKS,\r\n" +
	    		"0 as FUNCTION_TYPE,\r\n" +
	    		"NULL AS SPECIFIC_NAME \r\n" +
			"FROM db_stored_procedure\r\n" +
	 		"WHERE " + sbCondition + "\r\n" +
	 		"ORDER BY FUNCTION_CAT, FUNCTION_SCHEM, FUNCTION_NAME, SPECIFIC_NAME";
		
		  Statement stmt = getConnection().createStatement();
		  rsFunctions = stmt.unwrap(Statement.class).executeQuery(sSql);
		  return rsFunctions;
	} /* getFunctions */

	/* ------------------------------------------------------------------------ */
	/**
	 * {@inheritDoc}
	 */
	@Override
	public ResultSet getProcedures(String catalog, String schemaPattern, String procedureNamePattern) throws SQLException {
		if (catalog != null) {
			throw new SQLException("Cubrid does not support catalogs, argument must be NULL.");
		}

		StringBuilder sbCondition = new StringBuilder();
		String cond;

		sbCondition.append("SP_TYPE = 'PROCEDURE'");
		
		if ((procedureNamePattern == null) || procedureNamePattern.equals("%"))
		    {
		      sbCondition.append(" AND SP_NAME LIKE '%' ");      
		    }
		else
		    {
			  cond = SqlLiterals.formatStringLiteral(procedureNamePattern);
		      sbCondition.append(" AND SP_NAME LIKE ");
		      sbCondition.append(cond);
		    }		 
		
		ResultSet rsProcedures = null;

	    String sSql = "SELECT\r\n" +
	    		"NULL as PROCEDURE_CAT,\r\n" +
	    		"'" + currentSchemaSafe() + "' as PROCEDURE_SCHEM,\r\n" +
	    		"SP_NAME AS PROCEDURE_NAME,\r\n" +
	    		"NULL as RESERVED1,\r\n" +
	    		"NULL as RESERVED2,\r\n" +
	    		"NULL as RESERVED3,\r\n" +
	    		"'AS Language ' + lang + ' NAME ' + '''' + target + '''' as REMARKS,\r\n" +
	    		"0 as PROCEDURE_TYPE,\r\n" +
	    		"NULL as SPECIFIC_NAME\r\n" +
			"FROM db_stored_procedure\r\n" +
	 		"WHERE " + sbCondition + "\r\n" +
	 		"ORDER BY PROCEDURE_CAT, PROCEDURE_SCHEM, PROCEDURE_NAME, SPECIFIC_NAME";
		
		  Statement stmt = getConnection().createStatement();
		  rsProcedures = stmt.unwrap(Statement.class).executeQuery(sSql);
		  return rsProcedures;
	} /* getProcedures */

	/* ------------------------------------------------------------------------ */
	/**
	 * {@Override}
	 */
	@Override
	public ResultSet getSchemas() throws SQLException {
		String sSql = "SELECT DISTINCT LOWER(owner_name) AS TABLE_SCHEM, NULL AS TABLE_CATALOG\n"
				+ "FROM db_class WHERE is_system_class = 'NO'";
		Statement stmt = this.getConnection().createStatement();
		return stmt.unwrap(Statement.class).executeQuery(sSql);
	} /* getSchemas */

	/* ------------------------------------------------------------------------ */
	/**
	 * {@inheritDoc}
	 */
	@Override
	public ResultSet getSchemas(String catalog, String schemaPattern) throws SQLException {
		if (catalog != null) {
			throw new SQLException("Cubrid does not support catalogs, argument must be NULL.");
		}

		return super.getSchemas(catalog, schemaPattern);
	} /* getSchemas */

	/* ------------------------------------------------------------------------ */
	/**
	 * {@inheritDoc}
	 */
	@Override
	public ResultSet getSuperTables(String catalog, String schemaPattern, String tableNamePattern) throws SQLException {
		if (catalog != null) {
			throw new SQLException("Cubrid does not support catalogs, argument must be NULL.");
		}
		return super.getSuperTables(schemaPattern, catalog, tableNamePattern);
	} /* getSuperTables */

	/* ------------------------------------------------------------------------ */
	/**
	 * {@inheritDoc}
	 */
	@Override
	public ResultSet getSuperTypes(String catalog, String schemaPattern, String typeNamePattern) throws SQLException {
		if (catalog != null) {
			throw new SQLException("Cubrid does not support catalogs, argument must be NULL.");
		}
		return super.getSuperTypes(schemaPattern, catalog, typeNamePattern);
	} /* getSuperTypes */

	private String qualifyForVendor(String schema, String table) {
		if (table == null || table.isEmpty() || "%".equals(table)) return table;
		if (table.indexOf('.') >= 0) return table;           // 이미 한정돼 있다
		if (schema == null || schema.isEmpty() || "%".equals(schema)) return table;
		return schema.toLowerCase() + "." + table;
	}

	/* ------------------------------------------------------------------------ */
	/**
	 * {@inheritDoc}
	 * @throws SQLException 
	 */
	@Override
	public ResultSet getColumns(String catalog, String schemaPattern, String tableNamePattern, String columnNamePattern) throws SQLException 
	{
		if (catalog != null)
			throw new SQLException("Cubrid does not support catalogs, argument must be NULL.");
		
		return super.getColumns(catalog, schemaPattern, qualifyForVendor(schemaPattern, tableNamePattern), columnNamePattern);
	} /* getColumns */

	/* ------------------------------------------------------------------------ */
	/**
	 * {@inheritDoc}
	 */
	@Override
	public ResultSet getTables(String catalog, String schemaPattern, String tableNamePattern, String[] types) throws SQLException {
		if (catalog != null) {
			throw new SQLException("Cubrid does not support catalogs, argument must be NULL.");
		}

		StringBuilder sb = new StringBuilder();
		sb.append("SELECT NULL AS TABLE_CAT,\n");
		sb.append("       LOWER(owner_name) AS TABLE_SCHEM,\n");
		sb.append("       class_name AS TABLE_NAME,\n");
		sb.append("       CASE class_type WHEN 'CLASS' THEN 'TABLE' ELSE 'VIEW' END AS TABLE_TYPE,\n");
		sb.append("       comment AS REMARKS,\n");
		sb.append("       NULL AS TYPE_CAT, NULL AS TYPE_SCHEM, NULL AS TYPE_NAME,\n");
		sb.append("       NULL AS SELF_REFERENCING_COL_NAME, NULL AS REF_GENERATION\n");
		sb.append("FROM db_class\n");
		sb.append("WHERE is_system_class = 'NO'\n");

		if ((schemaPattern != null) && !schemaPattern.equals("%")) {
			sb.append("  AND LOWER(owner_name) LIKE ")
			  .append(SqlLiterals.formatStringLiteral(schemaPattern.toLowerCase())).append("\n");
		}

		if ((tableNamePattern != null) && !tableNamePattern.equals("%")) {
			String bare = tableNamePattern;
			int dot = bare.lastIndexOf('.');
			if (dot >= 0) bare = bare.substring(dot + 1);
			sb.append("  AND class_name LIKE ")
			  .append(SqlLiterals.formatStringLiteral(bare)).append("\n");
		}

		if (types != null) {
			StringBuilder sbTypes = new StringBuilder();
			for (String t : types) {
				if (t == null) continue;
				String mapped = t.equalsIgnoreCase("VIEW") ? "VCLASS"
						: t.equalsIgnoreCase("TABLE") ? "CLASS" : null;
				if (mapped == null) continue;
				if (sbTypes.length() > 0) sbTypes.append(", ");
				sbTypes.append('\'').append(mapped).append('\'');
			}
			if (sbTypes.length() > 0) {
				sb.append("  AND class_type IN (").append(sbTypes).append(")\n");
			} else {
				sb.append("  AND 1 = 0\n");   // 인식 가능한 유형이 없으면 빈 결과
			}
		}
		sb.append("ORDER BY TABLE_TYPE, TABLE_NAME");

		Statement stmt = this.getConnection().createStatement();
		return stmt.unwrap(Statement.class).executeQuery(sb.toString());
	} /* getTables */

	private String currentSchemaSafe() {
		try {
			return getConnection().getSchema();
		} catch (Exception e) {
			try {
				return getUserName();
			} catch (Exception e2) {
				return "PUBLIC";
			}
		}
	}

	/* ------------------------------------------------------------------------ */
	/**
	 * {@inheritDoc}
	 */
	@Override
	public ResultSet getUDTs(String catalog, String schemaPattern, String typeNamePattern, int[] types) throws SQLException {
		if (catalog != null) {
			throw new SQLException("Cubrid does not support catalogs, argument must be NULL.");
		}

		ResultSet rsUdts;

        String sbCondition = "T.TYPE_NAME LIKE " + SqlLiterals.formatStringLiteral(typeNamePattern);

	    String sTypeSchema;
	    try {
	        sTypeSchema = currentSchemaSafe();
	    } catch (Exception e) {
	        try {
	            sTypeSchema = getUserName();
	        } catch (Exception e2) {
	            sTypeSchema = "PUBLIC";
	        }
	    }

	    String sSql = "SELECT\r\n" +
	    		"NULL as TYPE_CAT,\r\n" +
	    		"'" + sTypeSchema + "' as TYPE_SCHEM,\r\n" +
	    		"T.TYPE_NAME AS TYPE_NAME,\r\n" +
				"null AS TYPE_NAME,\r\n" +
	    		"NULL as CLASS_NAME,\r\n" +
                Types.STRUCT + " as DATA_TYPE,\r\n" +
				"NULL as DATA_TYPE,\r\n" +
	    		"NULL as REMARKS,\r\n" +
	    		"NULL as BASE_TYPE\r\n" +
			"FROM _db_data_type T\r\n" +
	 		"WHERE " + sbCondition + "\r\n" +
	 		"ORDER BY DATA_TYPE, TYPE_CAT, TYPE_SCHEM, TYPE_NAME";
		
		  Statement stmt = getConnection().createStatement();
		  rsUdts = stmt.unwrap(Statement.class).executeQuery(sSql);
		  return rsUdts;
	} /* getUDTs */


} /* class CubridDatabaseMetaData */
