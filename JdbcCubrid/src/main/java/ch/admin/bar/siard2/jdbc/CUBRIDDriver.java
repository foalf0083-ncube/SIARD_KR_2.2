// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
/*======================================================================
CubridDriver implements a wrapped Cubrid Driver.
Version     : $Id: $
Application : SIARD2
Description : CubridDriver implements a wrapped Cubrid Driver.
Platform    : Java 7   
------------------------------------------------------------------------
Copyright  : 2016, Enter AG, Rüti ZH, Switzerland
Created    : 26.10.2016, Simon Jutz
======================================================================*/
package ch.admin.bar.siard2.jdbc;

import java.nio.charset.Charset;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

import ch.enterag.utils.jdbc.BaseDriver;
import ch.enterag.utils.logging.IndentLogger;

/* =============================================================================== */
/**
 * CubridDriver implements a wrapped Cubrid Driver
 * @author Simon Jutz
 */
public class CUBRIDDriver extends BaseDriver implements Driver 
{
	private static final IndentLogger _il = IndentLogger.getIndentLogger(CUBRIDDriver.class.getName());
  /** protocol sub scheme for MsSQL JDBC URL */
  public static final String sCUBRID_SCHEME = "cubrid";
  /** URL prefix for Cubrid JDBC URL */
  public static final String sCUBRID_URL_PREFIX = sJDBC_SCHEME+":"+sCUBRID_SCHEME+":";
  /** URL for database name.
   * @param sDatabaseName host:port/database, e.g. localhost:3306/testdb 
   * @return JDBC URL.
   */
  public static String getUrl(String sDatabaseName,boolean bNoSsl)
  {
    String sUrl = sDatabaseName;
    if (!sUrl.startsWith(sCUBRID_URL_PREFIX))
      sUrl = sCUBRID_URL_PREFIX+"//"+sDatabaseName;
    if (bNoSsl)
      sUrl = sUrl+"?verifyServerCertificate=false&useSSL=false&requireSSL=false";
    return sUrl;
  } /* getUrl */

	/** register this driver, replacing original Cubrid driver
	 */
	public static void register()
	{
		System.setProperty("jdbc.drivers",  "cubrid.jdbc.driver.CUBRIDDriver");
		
		// cubrid.jdbc.dirver.CUBRIDDriver is excluded in acceptsURL
		try { BaseDriver.register(new CUBRIDDriver(), "cubrid.jdbc.driver.CUBRIDDriver", "jdbc:cubrid:localhost:33000:demodb:public::"); }
		catch(Exception e) { throw new Error(e); }
	}

	/** replace Cubrid driver by this, when this class is loaded */
	static
	{
		register();
	}

	/* ------------------------------------------------------------------------ */
	/**
	 * {@inheritDoc}
	 * @return the appropriately wrapped Connection
	 */
	public Connection connect(String url, Properties info) throws SQLException {
		_il.enter(url, info);
		/* add/change properties */
		if (info == null)
		  info = new Properties();
		/* avoid huge JAVA memory for large BLOBs */
		info.setProperty("useCursorFetch", "true");
		info.setProperty("defaultFetchSize", "1");
		/* avoid escape processing */
		info.setProperty("enableEscapeProcessing", "false");
		info.setProperty("processEscapeCodesForPrepStmts", "false");
		/* disable backslash escapes */
		info.setProperty("sessionVariables", "sql_mode='ANSI,NO_BACKSLASH_ESCAPES'");
		Connection conn = super.connect(url, info);
		if (conn != null)
			conn = alignConnectionCharset(url, info, conn);
		if (conn != null)
			conn = new CUBRIDConnection(conn);
		_il.exit(conn);
		return conn;
	} /* connect */

	/* ------------------------------------------------------------------------ */
	/**
	 * 접속 문자집합을 데이터베이스의 것과 맞춘다.
	 *
	 * 드라이버는 식별자를 읽을 때는 서버가 알려준 컬럼 문자집합으로 해석하고,
	 * 되돌려 보낼 때는 접속 문자집합으로 부호화한다. 둘이 다르면 읽을 때 멀쩡했던
	 * 이름이 보낼 때 깨져 "Unknown class" 가 된다. 접속 문자집합의 기본값은
	 * file.encoding 이라, 한국어 윈도우(MS949)에서 UTF-8 데이터베이스에 붙는
	 * 흔한 조합만으로도 한글 테이블명이 어긋난다.
	 *
	 * 그래서 붙은 뒤 카탈로그의 문자집합을 물어보고, 어긋나면 맞춘 값으로 다시 붙는다.
	 * 사용자가 charSet 을 직접 지정했으면 그 뜻을 존중해 건드리지 않는다.
	 */
	private Connection alignConnectionCharset(String url, Properties info, Connection conn)
	{
		if (info.getProperty("charSet") != null)
			return conn;
		try
		{
			String sCatalogCharset = queryCatalogCharset(conn);
			if ("iso88591".equalsIgnoreCase(String.valueOf(sCatalogCharset).trim()))
				_il.event("CUBRID database catalog charset is iso88591. "+
						"Korean identifiers cannot be represented in such a database "+
						"— create it with a utf8 locale (e.g. cubrid createdb <db> en_US.utf8).");
			String sJavaCharset = toJavaCharsetName(sCatalogCharset);
			if (sJavaCharset == null)
				return conn;
			String sCurrent = System.getProperty("file.encoding");
			if ((sCurrent != null) && Charset.forName(sCurrent).equals(Charset.forName(sJavaCharset)))
				return conn;

			Properties aligned = new Properties();
			aligned.putAll(info);
			aligned.setProperty("charSet", sJavaCharset);
			Connection connAligned = super.connect(url, aligned);
			if (connAligned == null)
				return conn;
			try { conn.close(); } catch (SQLException se) { /* 새 접속을 이미 얻었으므로 진행한다 */ }
			_il.event("CUBRID connection charset aligned to database catalog charset: "+sJavaCharset+
					" (was "+sCurrent+")");
			return connAligned;
		}
		catch (Exception e)
		{
			/* 맞추지 못했어도 원래 접속은 살려 둔다. ASCII 이름만 쓰면 아무 영향이 없다. */
			_il.event("CUBRID connection charset could not be aligned: "+e.getMessage());
			return conn;
		}
	} /* alignConnectionCharset */

	/* ------------------------------------------------------------------------ */
	/** 식별자를 담는 카탈로그 컬럼이 어떤 문자집합인지 물어본다. 질의문은 ASCII 뿐이라 안전하다. */
	private String queryCatalogCharset(Connection conn) throws SQLException
	{
		String sSql = "SELECT charset FROM db_attribute "+
				"WHERE class_name = '_db_class' AND attr_name = 'class_name'";
		try (Statement stmt = conn.createStatement();
				ResultSet rs = stmt.executeQuery(sSql))
		{
			if (rs.next())
				return rs.getString(1);
		}
		return null;
	} /* queryCatalogCharset */

	/* ------------------------------------------------------------------------ */
	/** CUBRID 문자집합 이름을 자바 이름으로 옮긴다. 맞출 필요가 없으면 null 을 준다. */
	private String toJavaCharsetName(String sCubridCharset)
	{
		if (sCubridCharset == null)
			return null;
		switch (sCubridCharset.trim().toLowerCase())
		{
			case "utf8": return "UTF-8";
			case "iso88591": return "ISO-8859-1";
			case "euckr": return "EUC-KR";
			/* ascii · binary 는 식별자에 한글을 담을 수 없어 맞출 대상이 아니다 */
			default: return null;
		}
	} /* toJavaCharsetName */

	/* ------------------------------------------------------------------------ */
	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean acceptsURL(String url) {
		_il.enter(url);
		boolean bAccepts = url != null && url.toLowerCase().startsWith("jdbc:cubrid:");
		_il.exit(bAccepts);
		return bAccepts;
	} /* acceptsURL */

} /* class CubridDriver */