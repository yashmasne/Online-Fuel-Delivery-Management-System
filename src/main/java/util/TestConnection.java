package util;

import java.sql.Connection;

/**
 * Quick standalone check that the DB credentials/URL in DBConnection work,
 * before deploying the whole webapp. Run with:
 *   mvn compile exec:java -Dexec.mainClass=util.TestConnection
 * (or just run this class's main method from your IDE)
 */
public class TestConnection {

    public static void main(String[] args) {
        try (Connection connection = DBConnection.getConnection()) {
            if (connection != null) {
                System.out.println("Database connected successfully!");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
