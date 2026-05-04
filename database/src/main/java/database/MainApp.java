package database;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

import java.sql.Connection;

public class MainApp extends Application {

    private static Stage primaryStage;

    @Override
    public void start(Stage stage) {
        primaryStage = stage;

        // 1. Ověření připojení k databázi před načtením UI
        if (!testDatabaseConnection()) {
            // Pokud připojení selže, nemá smysl aplikaci spouštět, takže ji ukončíme
            Platform.exit();
            return;
        }

        // 2. Načtení hlavního okna, pokud databáze funguje
        try {
            switchTo("MainView");
            primaryStage.setTitle("Hotel Management System");
            primaryStage.setResizable(false);
            primaryStage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Zkusí získat připojení z DatabaseManageru. Pokud se to povede, hned ho zavře
     * (spojení zůstane připravené v connection poolu HikariCP).
     * Pokud se to nepovede, zobrazí vyskakovací okno s chybou.
     */
    private boolean testDatabaseConnection() {
        try (Connection conn = DatabaseManager.getConnection()) {
            System.out.println("✅ Připojení k DB ověřeno. Aplikace startuje.");
            return true;
        } catch (Exception e) {
            System.err.println("❌ Nelze se připojit k databázi.");
            e.printStackTrace();

            // Zobrazení chybového okna pro uživatele
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Kritická chyba databáze");
            alert.setHeaderText("Nepodařilo se připojit k databázi!");
            alert.setContentText("Zkontrolujte připojení k síti, VPN (pokud používáte školní server), nebo zda lokální databáze běží.\n\nDetail: " + e.getMessage());
            alert.showAndWait();

            return false;
        }
    }

    // Statická metoda pro přepínání mezi formuláři
    public static void switchTo(String fxmlName) throws Exception {
        FXMLLoader loader = new FXMLLoader(
                MainApp.class.getResource("/fxml/" + fxmlName + ".fxml")
        );
        Parent root = loader.load();
        Scene scene = new Scene(root);
        scene.getStylesheets().add(
                MainApp.class.getResource("/css/style.css").toExternalForm()
        );
        primaryStage.setScene(scene);
    }

    public static void main(String[] args) {
        launch(args);
    }
}