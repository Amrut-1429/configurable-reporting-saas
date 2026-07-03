import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public class FixSequence {
    public static void main(String[] args) {
        String url = "jdbc:postgresql://localhost:5432/amrut_data";
        String user = "postgres";
        String password = "1234";

        try (Connection conn = DriverManager.getConnection(url, user, password);
             Statement stmt = conn.createStatement()) {
            
            stmt.execute("SELECT setval('transaction_seq', (SELECT MAX(id) FROM transactions) + 1);");
            System.out.println("Sequence updated successfully.");
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
