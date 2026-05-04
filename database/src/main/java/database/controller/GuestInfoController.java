package database.controller;

import database.dto.GuestDTO;
import database.MainApp;
import javafx.fxml.FXML;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;

public class GuestInfoController {

    // Statická proměnná pro přijetí dat z jiné obrazovky
    public static GuestDTO loadedGuest = null;

    // TADY JE TA OPRAVA: Prvky přesně odpovídají fx:id a typům (TextField) ve FXML
    @FXML private TextField tfGuestId;
    @FXML private TextField tfFirstName;
    @FXML private TextField tfLastName;
    @FXML private TextField tfEmail;
    @FXML private TextField tfPhone;

    // Připraveno pro tvůj pravý panel s rezervacemi
    @FXML private ListView<String> lvReservations;

    @FXML
    public void initialize() {
        if (loadedGuest != null) {
            // 1. Zobrazení informací o hostovi do levého panelu
            tfGuestId.setText(loadedGuest.getIdCardNumber());
            tfFirstName.setText(loadedGuest.getFirstName());
            tfLastName.setText(loadedGuest.getLastName());
            tfEmail.setText(loadedGuest.getEmail() != null ? loadedGuest.getEmail() : "");
            tfPhone.setText(loadedGuest.getPhone() != null ? loadedGuest.getPhone() : "");

            // 2. Načtení jeho rezervací do pravého panelu
            try {
                database.dao.ReservationDAO dao = new database.dao.ReservationDAO();
                java.util.List<String> history = dao.getGuestReservations(loadedGuest.getGuestId());

                // Vyčistíme list (pro jistotu) a přidáme data
                lvReservations.getItems().clear();
                if (history.isEmpty()) {
                    lvReservations.getItems().add("Tento host nemá žádné rezervace.");
                } else {
                    lvReservations.getItems().addAll(history);
                }
            } catch (Exception e) {
                e.printStackTrace();
                lvReservations.getItems().add("Chyba při načítání dat z databáze.");
            }

            // Okamžitě proměnnou vyčistíme
            loadedGuest = null;
        }
    }

    @FXML
    private void onBack() {
        try {
            MainApp.switchTo("MainView");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}