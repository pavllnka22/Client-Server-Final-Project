package server;

import java.sql.*;

public class DatabaseManager {

    private static final String URL = "jdbc:sqlite:battleship.db";

    public static void initialize() {
        String createTableSQL = "CREATE TABLE IF NOT EXISTS users (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "login TEXT UNIQUE NOT NULL," +
                "password_hash TEXT NOT NULL," +
                "role TEXT NOT NULL" +
                ");";

        try (Connection conn = DriverManager.getConnection(URL);
             Statement stmt = conn.createStatement()) {
            stmt.execute(createTableSQL);
            System.out.println("Базу даних успішно ініціалізовано.");
            createAdminIfNotExist();

        } catch (SQLException e) {
            System.err.println(e.getMessage());
        }
    }

    public static boolean registerUser(String login, String password) {
        String sql = "INSERT INTO users(login, password_hash, role) VALUES(?, ?, ?)";
        String hash = PasswordHasher.hashPassword(password);

        try (Connection conn = DriverManager.getConnection(URL);
             PreparedStatement preparedStatement = conn.prepareStatement(sql)) {
            preparedStatement.setString(1, login);
            preparedStatement.setString(2, hash);
            preparedStatement.setString(3, "USER");
            preparedStatement.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.out.println("Registration error/login already in use: " + e.getMessage());
            return false;
        }
    }

     public static String authenticateUser(String login, String password) {
        String sql = "SELECT password_hash, role FROM users WHERE login = ?";
        String inputHash = PasswordHasher.hashPassword(password);

        try (Connection conn = DriverManager.getConnection(URL);
             PreparedStatement preparedStatement = conn.prepareStatement(sql)) {
            preparedStatement.setString(1, login);
            ResultSet rs = preparedStatement.executeQuery();

            if (rs.next()) {
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
        String sql = "INSERT OR IGNORE INTO users(id, login, password_hash, role) VALUES(1, 'admin', ?, 'ADMIN')";
        try (Connection conn = DriverManager.getConnection(URL);
             PreparedStatement preparedStatement = conn.prepareStatement(sql)) {
            preparedStatement.setString(1, PasswordHasher.hashPassword("admin123"));
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
