// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siard2.jdbc;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.NClob;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 벤더 드라이버 없이 래퍼를 시험하기 위한 가짜 JDBC 객체. 동적 프록시로 필요한 메서드만 구현한다. */
final class FakeJdbc {

    private FakeJdbc() {
    }

    private static SQLException unsupported(Method m) {
        return new SQLException("fake does not implement " + m.getName());
    }

    /** 가짜 결과집합. 라벨은 1부터 센다. */
    static final class Rows {
        final List<String> labels;
        final List<Object[]> rows = new ArrayList<>();
        int cursor = -1;
        boolean closed = false;
        boolean wasNull = false;

        Rows(String... labels) {
            this.labels = Arrays.asList(labels);
        }

        Rows row(Object... values) {
            rows.add(values);
            return this;
        }

        int index(String label) throws SQLException {
            for (int i = 0; i < labels.size(); i++) {
                if (labels.get(i).equalsIgnoreCase(label)) return i + 1;
            }
            throw new SQLException("no column " + label);
        }

        Object value(int index) {
            Object o = rows.get(cursor)[index - 1];
            wasNull = (o == null);
            return o;
        }
    }

    static ResultSet resultSet(Rows rows) {
        InvocationHandler h = (proxy, m, args) -> {
            String n = m.getName();
            switch (n) {
                case "next":
                    rows.cursor++;
                    return rows.cursor < rows.rows.size();
                case "close":
                    rows.closed = true;
                    return null;
                case "isClosed":
                    return rows.closed;
                case "wasNull":
                    return rows.wasNull;
                case "findColumn":
                    return rows.index((String) args[0]);
                case "getStatement":
                    return null;
                case "getMetaData":
                    return resultSetMetaData(rows);
                case "isWrapperFor":
                    return ((Class<?>) args[0]).isInstance(proxy);
                case "unwrap":
                    return proxy;
                case "toString":
                    return "FakeResultSet";
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == args[0];
                default:
                    break;
            }
            if (n.startsWith("get") && args != null && args.length >= 1) {
                int idx = (args[0] instanceof String) ? rows.index((String) args[0]) : (Integer) args[0];
                Object v = rows.value(idx);
                switch (n) {
                    case "getInt":
                        return v == null ? 0 : ((Number) v).intValue();
                    case "getLong":
                        return v == null ? 0L : ((Number) v).longValue();
                    case "getShort":
                        return v == null ? (short) 0 : ((Number) v).shortValue();
                    case "getBoolean":
                        return v != null && (Boolean) v;
                    case "getString":
                        return v == null ? null : String.valueOf(v);
                    case "getObject":
                    case "getClob":
                    case "getNClob":
                    case "getBlob":
                        return v;
                    default:
                        break;
                }
            }
            throw unsupported(m);
        };
        return (ResultSet) Proxy.newProxyInstance(FakeJdbc.class.getClassLoader(), new Class<?>[]{ResultSet.class}, h);
    }

    static ResultSetMetaData resultSetMetaData(Rows rows) {
        InvocationHandler h = (proxy, m, args) -> {
            switch (m.getName()) {
                case "getColumnCount":
                    return rows.labels.size();
                case "getColumnLabel":
                case "getColumnName":
                    return rows.labels.get((Integer) args[0] - 1);
                default:
                    throw unsupported(m);
            }
        };
        return (ResultSetMetaData) Proxy.newProxyInstance(FakeJdbc.class.getClassLoader(),
                new Class<?>[]{ResultSetMetaData.class}, h);
    }

    /** 가짜 DatabaseMetaData. 메서드 이름별로 돌려줄 결과집합을 등록한다. */
    static DatabaseMetaData databaseMetaData(Map<String, ResultSet> byMethod, int majorVersion, Connection conn) {
        InvocationHandler h = (proxy, m, args) -> {
            String n = m.getName();
            if (byMethod.containsKey(n)) return byMethod.get(n);
            switch (n) {
                case "getDatabaseMajorVersion":
                    return majorVersion;
                case "getConnection":
                    return conn;
                case "getSearchStringEscape":
                    return "\\";
                case "getDatabaseProductName":
                    return "Tibero";
                case "isWrapperFor":
                    return ((Class<?>) args[0]).isInstance(proxy);
                case "unwrap":
                    return proxy;
                case "toString":
                    return "FakeDatabaseMetaData";
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == args[0];
                default:
                    throw unsupported(m);
            }
        };
        return (DatabaseMetaData) Proxy.newProxyInstance(FakeJdbc.class.getClassLoader(),
                new Class<?>[]{DatabaseMetaData.class}, h);
    }

    /** 가짜 연결. 배열 타입 이름을 기록하고 nativeSQL 은 접두어를 붙여 위임 여부를 드러낸다. */
    static final class ConnectionSpy {
        final List<String> arrayTypes = new ArrayList<>();
        DatabaseMetaData dmd;
        ResultSet queryResult;
        Connection proxy;
    }

    static ConnectionSpy connection(DatabaseMetaData dmd, ResultSet queryResult) {
        ConnectionSpy spy = new ConnectionSpy();
        spy.dmd = dmd;
        spy.queryResult = queryResult;
        InvocationHandler h = (proxy, m, args) -> {
            switch (m.getName()) {
                case "getMetaData":
                    return spy.dmd;
                case "createArrayOf":
                    spy.arrayTypes.add((String) args[0]);
                    return null;
                case "createStatement":
                    return statement(spy.queryResult, spy.proxy);
                case "nativeSQL":
                    return "VENDOR:" + args[0];
                case "isClosed":
                    return false;
                case "close":
                case "setHoldability":
                case "setAutoCommit":
                case "commit":
                case "rollback":
                    return null;
                case "toString":
                    return "FakeConnection";
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == args[0];
                default:
                    throw unsupported(m);
            }
        };
        spy.proxy = (Connection) Proxy.newProxyInstance(FakeJdbc.class.getClassLoader(),
                new Class<?>[]{Connection.class}, h);
        return spy;
    }

    static Statement statement(ResultSet toReturn, Connection conn) {
        boolean[] closed = {false};
        InvocationHandler h = (proxy, m, args) -> {
            switch (m.getName()) {
                case "executeQuery":
                    return toReturn;
                case "executeUpdate":
                    return 0;
                case "execute":
                    return false;
                case "getResultSet":
                    return toReturn;
                case "getConnection":
                    return conn;
                case "setEscapeProcessing":
                case "setQueryTimeout":
                    return null;
                case "close":
                    closed[0] = true;
                    return null;
                case "isClosed":
                    return closed[0];
                case "toString":
                    return "FakeStatement";
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == args[0];
                default:
                    throw unsupported(m);
            }
        };
        return (Statement) Proxy.newProxyInstance(FakeJdbc.class.getClassLoader(), new Class<?>[]{Statement.class}, h);
    }

    /** free() 뒤에는 모든 접근이 실패하는 가짜 CLOB. Tibero 드라이버의 동작을 흉내 낸다. */
    static Clob clob(String content, boolean nclob) {
        boolean[] freed = {false};
        InvocationHandler h = (proxy, m, args) -> {
            String n = m.getName();
            if (n.equals("free")) {
                freed[0] = true;
                return null;
            }
            switch (n) {
                case "toString":
                    return "FakeClob";
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == args[0];
                default:
                    break;
            }
            if (freed[0]) throw new SQLException("LOB was freed");
            switch (n) {
                case "length":
                    return (long) content.length();
                case "getSubString": {
                    int pos = (int) (long) (Long) args[0];
                    int len = (Integer) args[1];
                    return content.substring(pos - 1, Math.min(content.length(), pos - 1 + len));
                }
                case "getCharacterStream":
                    return (Reader) new StringReader(content);
                default:
                    throw unsupported(m);
            }
        };
        Class<?>[] ifaces = nclob ? new Class<?>[]{NClob.class} : new Class<?>[]{Clob.class};
        return (Clob) Proxy.newProxyInstance(FakeJdbc.class.getClassLoader(), ifaces, h);
    }

    static Blob blob(byte[] content) {
        boolean[] freed = {false};
        InvocationHandler h = (proxy, m, args) -> {
            String n = m.getName();
            if (n.equals("free")) {
                freed[0] = true;
                return null;
            }
            switch (n) {
                case "toString":
                    return "FakeBlob";
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == args[0];
                default:
                    break;
            }
            if (freed[0]) throw new SQLException("LOB was freed");
            switch (n) {
                case "length":
                    return (long) content.length;
                case "getBytes": {
                    int pos = (int) (long) (Long) args[0];
                    int len = (Integer) args[1];
                    return Arrays.copyOfRange(content, pos - 1, Math.min(content.length, pos - 1 + len));
                }
                case "getBinaryStream":
                    return (InputStream) new ByteArrayInputStream(content);
                default:
                    throw unsupported(m);
            }
        };
        return (Blob) Proxy.newProxyInstance(FakeJdbc.class.getClassLoader(), new Class<?>[]{Blob.class}, h);
    }

    static Map<String, ResultSet> byMethod(Object... pairs) {
        Map<String, ResultSet> map = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], (ResultSet) pairs[i + 1]);
        }
        return map;
    }
}
