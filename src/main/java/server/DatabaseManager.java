package server;

import io.github.cdimascio.dotenv.Dotenv;
import java.sql.*;

public class DatabaseManager {

    private static final Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
    private static final String DB_URL = dotenv.get("DB_URL");
    private static final String DB_USER = dotenv.get("DB_USER");
    private static final String DB_PASSWORD = dotenv.get("DB_PASSWORD");

    static {
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("PostgreSQL Driver not found: " + e.getMessage());
        }
    }

    private static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL,DB_USER, DB_PASSWORD);
    }

    public static void initialize() {
        if (DB_URL == null || DB_USER == null || DB_PASSWORD == null) {
            System.err.println("ERROR: Missing DB_URL, DB_USER, or DB_PASSWORD in .env file!");
            return;
        }

        String createTableSQL = """
            CREATE TABLE IF NOT EXISTS users (
                id SERIAL PRIMARY KEY,
                login VARCHAR(50) UNIQUE NOT NULL,
                password_hash VARCHAR(256) NOT NULL,
                role VARCHAR(20) NOT NULL DEFAULT 'USER',
                is_banned BOOLEAN NOT NULL DEFAULT FALSE
            )
        """;

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute(createTableSQL);
            System.out.println("PostgreSQL database initialized successfully.");
            createAdminIfNotExist();

        } catch (SQLException e) {
            System.err.println(e.getMessage());
        }
    }

    public static boolean registerUser(String login, String password) {
        String sql = "INSERT INTO users(login, password_hash, role) VALUES(?, ?, ?)";
        String hash = PasswordHasher.hashPassword(password);

        try (Connection conn = getConnection();
             PreparedStatement preparedStatement = conn.prepareStatement(sql)) {
            preparedStatement.setString(1, login);
            preparedStatement.setString(2, hash);
            preparedStatement.setString(3, "USER");
            preparedStatement.executeUpdate();
            return true;
        } catch (SQLException e) {
            if (e.getMessage().contains("duplicate key")) {
                System.out.println("Registration error: User '" + login + "' already exists");
            } else {
                System.out.println("Registration error: " + e.getMessage());
            }
            return false;
        }
    }

     public static String authenticateUser(String login, String password) {
         String sql = "SELECT password_hash, role, is_banned FROM users WHERE login = ?";
         String inputHash = PasswordHasher.hashPassword(password);

        try (Connection conn = getConnection();
             PreparedStatement preparedStatement = conn.prepareStatement(sql)) {
            preparedStatement.setString(1, login);
            ResultSet rs = preparedStatement.executeQuery();

            if (rs.next()) {
                boolean isBanned = rs.getBoolean("is_banned");
                if (isBanned) {
                    return "BANNED";
                }

                String dbHash = rs.getString("password_hash");
                String role = rs.getString("role");

                if (dbHash.equals(inputHash)) {
                    return role;
                }
            }
        } catch (SQLException e) {
            System.err.println( e.getMessage());
        }
        return null;
    }

    private static void createAdminIfNotExist() {
        String sql = """
            INSERT INTO users(login, password_hash, role) 
            VALUES('admin', ?, 'ADMIN')
            ON CONFLICT (login) DO NOTHING
        """;

        try (Connection conn = getConnection();
             PreparedStatement preparedStatement = conn.prepareStatement(sql)) {
            preparedStatement.setString(1, PasswordHasher.hashPassword("admin123"));
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static boolean testConnection() {
        if (DB_URL == null) {
            System.err.println("Missing DB_URL in .env file");
            return false;
        }

        try (Connection conn = getConnection()) {
            return conn != null && !conn.isClosed();
        } catch (SQLException e) {
            System.err.println("Connection test failed: " + e.getMessage());
            return false;
        }
    }

    public static boolean banUserInDB(String username) {
        String sql = "UPDATE users SET is_banned = TRUE WHERE login = ?";
        try (Connection conn = getConnection();
             PreparedStatement preparedStatement = conn.prepareStatement(sql)) {
            preparedStatement.setString(1, username);
            int rows = preparedStatement.executeUpdate();
            return rows > 0;
        } catch (SQLException e) {
            System.err.println("Error while banning: " + e.getMessage());
            return false;
        }
    }
}