package scratch;

public class TestSqlite {
    public static void main(String[] args) throws Exception {
        Class.forName("org.sqlite.JDBC");
        java.sql.Connection conn = java.sql.DriverManager.getConnection("jdbc:sqlite::memory:");
        java.sql.Statement stmt = conn.createStatement();
        stmt.execute("CREATE TABLE test (id INTEGER PRIMARY KEY, name TEXT)");
        stmt.execute("INSERT INTO test VALUES (1, 'hello')");
        java.sql.ResultSet rs = stmt.executeQuery("SELECT * FROM test");
        while (rs.next()) {
            System.out.println("Row: " + rs.getInt(1) + ", " + rs.getString(2));
        }
        conn.close();
    }
}
