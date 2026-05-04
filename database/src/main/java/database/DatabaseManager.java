package database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;

public class DatabaseManager {

    private static HikariDataSource dataSource;
    private static final String USERNAME = "STE0611";
    
    private static final String PASSWORD = "T9RvzPsVi5vZJdBD";
    private static final String URL = "jdbc:oracle:thin:@bayer.cs.vsb.cz:1521:oracle";

    static {
        try {
            HikariConfig config = new HikariConfig();


            config.setJdbcUrl(URL);


            config.setUsername(USERNAME);
            config.setPassword(PASSWORD);

            config.setDriverClassName("oracle.jdbc.OracleDriver");
            config.setMaximumPoolSize(10);

            dataSource = new HikariDataSource(config);
            System.out.println("✅ Připojeno na školní databázi přes VPN.");
        } catch (Exception e) {
            System.err.println("❌ Nelze se připojit! (Máš zapnutou VPN?)");
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public static Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }
}