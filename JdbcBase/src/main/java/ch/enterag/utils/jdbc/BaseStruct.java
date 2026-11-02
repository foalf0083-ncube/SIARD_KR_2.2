// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.enterag.utils.jdbc;

import java.sql.SQLException;
import java.sql.Struct;
import java.util.Map;


public abstract class BaseStruct
        implements Struct {
    private Struct _structWrapped = null;


    public BaseStruct(Struct structWrapped) {
        this._structWrapped = structWrapped;
    }


    public String getSQLTypeName() throws SQLException {
        return this._structWrapped.getSQLTypeName();
    }


    public Object[] getAttributes() throws SQLException {
        return this._structWrapped.getAttributes();
    }


    public Object[] getAttributes(Map<String, Class<?>> map) throws SQLException {
        return this._structWrapped.getAttributes(map);
    }
}
