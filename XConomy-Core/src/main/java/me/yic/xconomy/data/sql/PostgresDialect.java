package me.yic.xconomy.data.sql;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Locale;

/** PostgreSQL translation at the JDBC boundary; other backends keep their SQL. */
public final class PostgresDialect {
    private PostgresDialect() { }

    public static String translate(String sql, boolean ignoreCase) {
        String result = sql.replaceAll("(?i)int\\(\\d+\\)", "integer")
                .replaceAll("(?i)double\\(\\d+,\\d+\\)", "numeric(20,2)")
                .replaceAll("(?i)integer not null auto_increment", "bigserial not null")
                .replaceAll("(?i)default charset = [a-zA-Z0-9_-]+", "")
                .replaceAll("(?i)\\bdatetime datetime\\b", "datetime timestamp")
                .replaceAll("(?i)last_time datetime", "last_time timestamp")
                .replaceAll("(?i)ifnull\\(", "coalesce(");
        result = result.replaceAll("(?i)player = \\? COLLATE NOCASE", "lower(player) = lower(?)");
        if (ignoreCase) result = result.replaceAll("(?i)where player = \\?", "where lower(player) = lower(?)");
        result = result.replaceAll("(?i)binary player", "player COLLATE \"C\"")
                .replaceAll("(?i)binary account", "account COLLATE \"C\"")
                .replaceAll("(?i)player = \\? COLLATE NOCASE", "lower(player) = lower(?)");
        if (result.toUpperCase(Locale.ROOT).startsWith("INSERT IGNORE INTO"))
            result = result.replaceFirst("(?i)INSERT IGNORE INTO", "INSERT INTO") + " ON CONFLICT DO NOTHING";
        result = result.replaceAll("(?i)ON DUPLICATE KEY UPDATE DUUID = \\?", "ON CONFLICT (UUID) DO UPDATE SET DUUID = ?")
                .replaceAll("(?i)ON DUPLICATE KEY UPDATE last_time = \\?", "ON CONFLICT (UUID) DO UPDATE SET last_time = ?");
        return result;
    }

    public static Connection wrap(Connection connection, boolean ignoreCase) {
        return (Connection) Proxy.newProxyInstance(PostgresDialect.class.getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, args) -> {
            Object[] rewritten = args;
            if (args != null && args.length > 0 && args[0] instanceof String && method.getName().startsWith("prepare")) {
                rewritten = args.clone(); rewritten[0] = translate((String) args[0], ignoreCase);
            }
            try {
                Object result = method.invoke(connection, rewritten);
                if (method.getName().equals("createStatement")) return wrapStatement((Statement) result, ignoreCase);
                return result;
            } catch (InvocationTargetException error) { throw error.getCause(); }
        });
    }

    private static Statement wrapStatement(Statement statement, boolean ignoreCase) {
        return (Statement) Proxy.newProxyInstance(PostgresDialect.class.getClassLoader(), new Class<?>[]{Statement.class}, (proxy, method, args) -> {
            Object[] rewritten = args;
            if (args != null && args.length > 0 && args[0] instanceof String) {
                rewritten = args.clone(); rewritten[0] = translate((String) args[0], ignoreCase);
            }
            try { return method.invoke(statement, rewritten); }
            catch (InvocationTargetException error) { throw error.getCause(); }
        });
    }
}
