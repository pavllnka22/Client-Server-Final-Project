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
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }

    public static void initialize() {
        if (DB_URL == null || DB_USER == null || DB_PASSWORD == null) {
            System.err.println("ERROR: Missing DB_URL, DB_USER, or DB_PASSWORD in .env file!");
            return;
        }

        String createUsersTable = """
            CREATE TABLE IF NOT EXISTS users (
                id SERIAL PRIMARY KEY,
                login VARCHAR(50) UNIQUE NOT NULL,
                password_hash VARCHAR(256) NOT NULL,
                role VARCHAR(20) NOT NULL DEFAULT 'USER',
                is_banned BOOLEAN NOT NULL DEFAULT FALSE,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """;

        String createStatsTable = """
            CREATE TABLE IF NOT EXISTS player_stats (
                username VARCHAR(50) PRIMARY KEY,
                games_played INT DEFAULT 0,
                wins INT DEFAULT 0,
                losses INT DEFAULT 0,
                rating INT DEFAULT 1000,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """;

        String createGameHistoryTable = """
            CREATE TABLE IF NOT EXISTS game_history (
                id SERIAL PRIMARY KEY,
                player1 VARCHAR(50) NOT NULL,
                player2 VARCHAR(50) NOT NULL,
                winner VARCHAR(50),
                played_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """;

        String alterTableSQL = """
            DO $$
            BEGIN
                IF NOT EXISTS (
                    SELECT 1 
                    FROM information_schema.columns 
                    WHERE table_name='game_history' AND column_name='played_at'
                ) THEN
                    ALTER TABLE game_history ADD COLUMN played_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;
                END IF;
            END $$;
        """;

        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(createUsersTable);
            stmt.execute(createStatsTable);
            stmt.execute(createGameHistoryTable);
            stmt.execute(alterTableSQL);
            System.out.println("PostgreSQL database initialized successfully.");
            createAdminIfNotExist();
        } catch (SQLException e) {
            System.err.println("Database initialization error: " + e.getMessage());
        }
    }

    public static boolean registerUser(String login, String password) {
        String sql = "INSERT INTO users(login, password_hash, role) VALUES(?, ?, ?)";
        String hash = PasswordHasher.hashPassword(password);

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, login);
            ps.setString(2, hash);
            ps.setString(3, "USER");
            ps.executeUpdate();

            String statsSql = "INSERT INTO player_stats(username) VALUES(?) ON CONFLICT (username) DO NOTHING";
            try (PreparedStatement statsPs = conn.prepareStatement(statsSql)) {
                statsPs.setString(1, login);
                statsPs.executeUpdate();
            }
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
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, login);
            ResultSet rs = ps.executeQuery();

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
            System.err.println("Auth error: " + e.getMessage());
        }
        return null;
    }

    public static String getPlayerStats(String username) {
        String sql = "SELECT * FROM player_stats WHERE username = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                StringBuilder stats = new StringBuilder();
                int games = rs.getInt("games_played");
                int wins = rs.getInt("wins");
                int losses = rs.getInt("losses");
                int rating = rs.getInt("rating");

                stats.append("Games played: ").append(games).append("\n");
                stats.append("Wins: ").append(wins).append("\n");
                stats.append("Losses: ").append(losses).append("\n");
                stats.append("Rating: ").append(rating).append("\n");

                String historySql = """
                    SELECT player1, player2, winner 
                    FROM game_history 
                    WHERE player1 = ? OR player2 = ? 
                    ORDER BY id DESC 
                    LIMIT 10
                """;
                try (PreparedStatement historyPs = conn.prepareStatement(historySql)) {
                    historyPs.setString(1, username);
                    historyPs.setString(2, username);
                    ResultSet historyRs = historyPs.executeQuery();

                    while (historyRs.next()) {
                        String p1 = historyRs.getString("player1");
                        String p2 = historyRs.getString("player2");
                        String winner = historyRs.getString("winner");
                        String opponent = p1.equals(username) ? p2 : p1;
                        String result = winner != null ? (winner.equals(username) ? "Win" : "Loss") : "Draw";
                        stats.append(opponent).append(" vs ").append(result).append("\n");
                    }
                }
                return stats.toString();
            } else {
                String insertSql = "INSERT INTO player_stats(username) VALUES(?) ON CONFLICT (username) DO NOTHING";
                try (PreparedStatement insertPs = conn.prepareStatement(insertSql)) {
                    insertPs.setString(1, username);
                    insertPs.executeUpdate();
                }
                return "Games played: 0\nWins: 0\nLosses: 0\nRating: 1000\nNo games played yet.";
            }
        } catch (SQLException e) {
            System.err.println("Error getting stats: " + e.getMessage());
            return null;
        }
    }

    public static void updatePlayerStats(String winner, String loser) {
        String winnerSql = """
            UPDATE player_stats 
            SET games_played = games_played + 1, 
                wins = wins + 1,
                rating = rating + 10,
                updated_at = CURRENT_TIMESTAMP
            WHERE username = ?
        """;

        String loserSql = """
            UPDATE player_stats 
            SET games_played = games_played + 1, 
                losses = losses + 1,
                rating = GREATEST(rating - 5, 0),
                updated_at = CURRENT_TIMESTAMP
            WHERE username = ?
        """;

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement ps1 = conn.prepareStatement(winnerSql)) {
                ps1.setString(1, winner);
                ps1.executeUpdate();
            }

            try (PreparedStatement ps2 = conn.prepareStatement(loserSql)) {
                ps2.setString(1, loser);
                ps2.executeUpdate();
            }

            String historySql = """
                INSERT INTO game_history(player1, player2, winner) 
                VALUES(?, ?, ?)
            """;
            try (PreparedStatement ps3 = conn.prepareStatement(historySql)) {
                ps3.setString(1, winner);
                ps3.setString(2, loser);
                ps3.setString(3, winner);
                ps3.executeUpdate();
            }

            conn.commit();
        } catch (SQLException e) {
            System.err.println("Error updating stats: " + e.getMessage());
        }
    }

    public static boolean isUserBanned(String username) {
        String sql = "SELECT is_banned FROM users WHERE login = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getBoolean("is_banned");
            }
        } catch (SQLException e) {
            System.err.println("Error checking ban: " + e.getMessage());
        }
        return false;
    }

    public static boolean banUser(String username) {
        String sql = "UPDATE users SET is_banned = TRUE WHERE login = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error banning user: " + e.getMessage());
            return false;
        }
    }

    public static boolean banUserInDB(String username) {
        return banUser(username);
    }

    public static boolean unbanUserInDB(String login) {
        String sql = "UPDATE users SET is_banned = FALSE WHERE login = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, login);
            int affectedRows = pstmt.executeUpdate();
            return affectedRows > 0;
        } catch (SQLException e) {
            System.err.println("Error unbanning user: " + e.getMessage());
            return false;
        }
    }

    private static void createAdminIfNotExist() {
        String sql = """
            INSERT INTO users(login, password_hash, role) 
            VALUES('admin', ?, 'ADMIN')
            ON CONFLICT (login) DO NOTHING
        """;

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, PasswordHasher.hashPassword("admin123"));
            ps.executeUpdate();

            String statsSql = """
                INSERT INTO player_stats(username) 
                VALUES('admin')
                ON CONFLICT (username) DO NOTHING
            """;
            try (PreparedStatement statsPs = conn.prepareStatement(statsSql)) {
                statsPs.executeUpdate();
            }
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
}