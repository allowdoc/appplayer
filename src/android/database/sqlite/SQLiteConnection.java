package android.database.sqlite;

import android.database.CursorWindow;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.util.Printer;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pure Java SQLiteConnection backed by real SQLite JDBC (org.sqlite.JDBC).
 * Executes genuine DDL, DML, PRAGMAs, and populates CursorWindow without Android native C++ code.
 */
public final class SQLiteConnection implements CancellationSignal.OnCancelListener {
    private final SQLiteConnectionPool mPool;
    private final SQLiteDatabaseConfiguration mConfiguration;
    private final int mConnectionId;
    private final boolean mIsPrimaryConnection;
    private Connection mJdbcConnection;

    private static final Map<String, Boolean> sLoggedQueries = new ConcurrentHashMap<>();

    private SQLiteConnection(SQLiteConnectionPool pool, SQLiteDatabaseConfiguration configuration,
                             int connectionId, boolean primaryConnection) {
        this.mPool = pool;
        this.mConfiguration = configuration;
        this.mConnectionId = connectionId;
        this.mIsPrimaryConnection = primaryConnection;
        initJdbc();
    }

    private void initJdbc() {
        try {
            Class.forName("org.sqlite.JDBC");
            String path = mConfiguration != null ? mConfiguration.path : ":memory:";
            String url;
            if (path == null || ":memory:".equals(path) || path.isEmpty()) {
                url = "jdbc:sqlite::memory:";
            } else {
                File dbFile = resolveDbFile(path);
                if (dbFile.getParentFile() != null && !dbFile.getParentFile().exists()) {
                    dbFile.getParentFile().mkdirs();
                }
                url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
            }
            this.mJdbcConnection = DriverManager.getConnection(url);
            this.mJdbcConnection.setAutoCommit(true);
            try (Statement s = this.mJdbcConnection.createStatement()) {
                s.execute("PRAGMA foreign_keys = ON;");
            }
        } catch (Throwable t) {
            System.err.println("[-] [SQLiteConnection] Failed to open SQLite JDBC: " + t.getMessage());
        }
    }

    private static File resolveDbFile(String androidPath) {
        if (androidPath == null) {
            return new File("data/databases/default.db");
        }
        File f = new File(androidPath);
        if (f.isAbsolute()) {
            return f;
        }
        // Handle double drive letter artifact if passed (e.g. databases\C:\...)
        if (androidPath.contains(":\\") || androidPath.contains(":/")) {
            int colonIdx = androidPath.indexOf(":");
            if (colonIdx > 0) {
                String sub = androidPath.substring(colonIdx - 1);
                File subFile = new File(sub);
                if (subFile.isAbsolute()) {
                    return subFile;
                }
            }
        }
        String fileName = f.getName();
        // Universal per-app databases directory derived from the runner root +
        // the currently running package (no absolute host paths, no app names).
        String runnerDir = System.getProperty("headless.runner.dir",
                System.getProperty("user.dir", "."));
        String pkg = "";
        try {
            pkg = android.app.ActivityThread.currentProcessName() != null
                    ? android.app.ActivityThread.currentProcessName() : "";
        } catch (Throwable ignored) {}
        if (pkg == null || pkg.isEmpty()) {
            try {
                Object at = Class.forName("android.app.ActivityThread")
                        .getMethod("currentActivityThread").invoke(null);
                if (at != null) {
                    Object bad = Class.forName("android.app.ActivityThread")
                            .getDeclaredField("mBoundApplication").get(at);
                    Object appInfo = Class.forName("android.app.ActivityThread$AppBindData")
                            .getDeclaredField("appInfo").get(bad);
                    pkg = String.valueOf(Class.forName("android.content.pm.ApplicationInfo")
                            .getDeclaredField("packageName").get(appInfo));
                }
            } catch (Throwable ignored) {}
        }
        if (pkg == null || pkg.isEmpty()) pkg = "default";
        File dbDir = new File(runnerDir, "data/" + pkg + "/databases");
        return new File(dbDir, fileName);
    }

    static SQLiteConnection open(SQLiteConnectionPool pool,
                                 SQLiteDatabaseConfiguration configuration,
                                 int connectionId, boolean primaryConnection) {
        return new SQLiteConnection(pool, configuration, connectionId, primaryConnection);
    }

    void close() {
        if (mJdbcConnection != null) {
            try {
                mJdbcConnection.close();
            } catch (Throwable ignored) {}
            mJdbcConnection = null;
        }
    }

    void reconfigure(SQLiteDatabaseConfiguration configuration) {}

    void setOnlyAllowReadOnlyOperations(boolean readOnly) {}

    boolean isPreparedStatementInCache(String sql) {
        return true;
    }

    public int getConnectionId() {
        return mConnectionId;
    }

    public boolean isPrimaryConnection() {
        return mIsPrimaryConnection;
    }

    private PreparedStatement prepareAndBind(String sql, Object[] bindArgs) throws SQLException {
        if (mJdbcConnection == null || mJdbcConnection.isClosed()) {
            initJdbc();
        }
        PreparedStatement stmt = mJdbcConnection.prepareStatement(sql);
        if (bindArgs != null) {
            for (int i = 0; i < bindArgs.length; i++) {
                Object arg = bindArgs[i];
                if (arg == null) {
                    stmt.setNull(i + 1, Types.NULL);
                } else if (arg instanceof byte[]) {
                    stmt.setBytes(i + 1, (byte[]) arg);
                } else if (arg instanceof Float || arg instanceof Double) {
                    stmt.setDouble(i + 1, ((Number) arg).doubleValue());
                } else if (arg instanceof Number) {
                    stmt.setLong(i + 1, ((Number) arg).longValue());
                } else if (arg instanceof Boolean) {
                    stmt.setLong(i + 1, (Boolean) arg ? 1L : 0L);
                } else {
                    stmt.setString(i + 1, arg.toString());
                }
            }
        }
        return stmt;
    }

    public void prepare(String sql, SQLiteStatementInfo outStatementInfo) {
        if (outStatementInfo != null) {
            // Robust pure-Java parameter counter: count unquoted '?' placeholders
            int paramCount = 0;
            if (sql != null) {
                boolean inQuote = false;
                for (int i = 0; i < sql.length(); i++) {
                    char c = sql.charAt(i);
                    if (c == '\'') {
                        inQuote = !inQuote;
                    } else if (c == '?' && !inQuote) {
                        paramCount++;
                    }
                }
            }
            outStatementInfo.numParameters = paramCount;
            outStatementInfo.readOnly = sql != null && sql.trim().toUpperCase().startsWith("SELECT");
            outStatementInfo.columnNames = new String[0];

            try {
                if (mJdbcConnection != null && !mJdbcConnection.isClosed() && sql != null) {
                    try (PreparedStatement stmt = mJdbcConnection.prepareStatement(sql)) {
                        ResultSetMetaData md = stmt.getMetaData();
                        if (md != null) {
                            int count = md.getColumnCount();
                            outStatementInfo.columnNames = new String[count];
                            for (int i = 0; i < count; i++) {
                                outStatementInfo.columnNames[i] = md.getColumnName(i + 1);
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }
    }

    public void execute(String sql, Object[] bindArgs, CancellationSignal cancellationSignal) {
        try (PreparedStatement stmt = prepareAndBind(sql, bindArgs)) {
            stmt.execute();
        } catch (Throwable t) {
            logSqlError("execute", sql, t);
        }
    }

    public long executeForLong(String sql, Object[] bindArgs, CancellationSignal cancellationSignal) {
        try (PreparedStatement stmt = prepareAndBind(sql, bindArgs);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (Throwable t) {
            logSqlError("executeForLong", sql, t);
        }
        return 0L;
    }

    public String executeForString(String sql, Object[] bindArgs, CancellationSignal cancellationSignal) {
        try (PreparedStatement stmt = prepareAndBind(sql, bindArgs);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getString(1);
            }
        } catch (Throwable t) {
            logSqlError("executeForString", sql, t);
        }
        return "";
    }

    public ParcelFileDescriptor executeForBlobFileDescriptor(String sql, Object[] bindArgs,
                                                            CancellationSignal cancellationSignal) {
        return null;
    }

    public int executeForChangedRowCount(String sql, Object[] bindArgs,
                                         CancellationSignal cancellationSignal) {
        try (PreparedStatement stmt = prepareAndBind(sql, bindArgs)) {
            return stmt.executeUpdate();
        } catch (Throwable t) {
            logSqlError("executeForChangedRowCount", sql, t);
        }
        return 0;
    }

    public long executeForLastInsertedRowId(String sql, Object[] bindArgs,
                                           CancellationSignal cancellationSignal) {
        try {
            if (sql != null && !sql.trim().isEmpty()) {
                execute(sql, bindArgs, cancellationSignal);
            }
            if (mJdbcConnection != null && !mJdbcConnection.isClosed()) {
                try (Statement stmt = mJdbcConnection.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT last_insert_rowid();")) {
                    if (rs.next()) {
                        return rs.getLong(1);
                    }
                }
            }
        } catch (Throwable t) {
            logSqlError("executeForLastInsertedRowId", sql, t);
        }
        return 1L;
    }

    public int executeForCursorWindow(String sql, Object[] bindArgs,
                                      CursorWindow window, int startPos, int requiredPos,
                                      boolean countAllRows, CancellationSignal cancellationSignal) {
        if (window == null) return 0;
        if (Boolean.getBoolean("headless.sqlite.verbose") && sLoggedQueries.putIfAbsent("cursor:" + sql, Boolean.TRUE) == null) {
            System.out.println("[*] [SQLiteConnection] executeForCursorWindow: " + sql);
        }
        try (PreparedStatement stmt = prepareAndBind(sql, bindArgs);
             ResultSet rs = stmt.executeQuery()) {
            ResultSetMetaData md = rs.getMetaData();
            int colCount = md.getColumnCount();
            window.clear();
            window.setStartPosition(startPos);
            window.setNumColumns(colCount);

            int rowIdx = 0;
            int windowRows = 0;
            while (rs.next()) {
                if (rowIdx >= startPos && windowRows < 1000) {
                    window.allocRow();
                    for (int c = 1; c <= colCount; c++) {
                        Object val = rs.getObject(c);
                        if (val == null) {
                            window.putNull(rowIdx, c - 1);
                        } else if (val instanceof Double || val instanceof Float) {
                            window.putDouble(((Number) val).doubleValue(), rowIdx, c - 1);
                        } else if (val instanceof Number) {
                            window.putLong(((Number) val).longValue(), rowIdx, c - 1);
                        } else if (val instanceof byte[]) {
                            window.putBlob((byte[]) val, rowIdx, c - 1);
                        } else {
                            window.putString(val.toString(), rowIdx, c - 1);
                        }
                    }
                    windowRows++;
                }
                rowIdx++;
                if (!countAllRows && windowRows >= 1000) {
                    break;
                }
            }
            return rowIdx;
        } catch (Throwable t) {
            logSqlError("executeForCursorWindow", sql, t);
        }
        return 0;
    }

    private void logSqlError(String op, String sql, Throwable t) {
        String key = op + ":" + sql;
        if (sLoggedQueries.putIfAbsent(key, Boolean.TRUE) == null) {
            System.out.println("[-] [SQLiteConnection] Notice in " + op + " query: " + sql + " -> " + t.getMessage());
        }
    }

    @Override
    public void onCancel() {}

    public void dump(Printer printer, boolean verbose) {}

    void dumpUnsafe(Printer printer, boolean verbose) {}

    String describeCurrentOperationUnsafe() {
        return "idle";
    }

    void collectDbStats(ArrayList<SQLiteDebug.DbStats> dbStatsList) {}

    void collectDbStatsUnsafe(ArrayList<SQLiteDebug.DbStats> dbStatsList) {}
}
