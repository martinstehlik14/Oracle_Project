package database.controller;

import database.DatabaseManager;
import database.MainApp;
import database.dao.ReservationDAO;
import database.dto.GuestDTO;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import java.sql.*;

public class MainController {

    @FXML private Label lblStaffName;
    @FXML private TextField tfSearchReservation;
    @FXML private TextField tfSearchGuest;


    // Nové proměnné bez radio buttonů, přidán lblStatusValue
    @FXML private Label lblRoomValue, lblCheckinValue, lblHostValue, lblServicesValue, lblStatusValue;


    // Zavolá se automaticky po načtení okna
    @FXML
    public void initialize() {
        getStaff(1); // F1: Natvrdo načteme zaměstnance s ID 1
    }

    // ── Navigace ──
    // ── Navigace a předání dat ──

    @FXML
    private void onNewReservation() {
        try {
            // Pro jistotu vyčistíme ID, aby se otevřel čistý formulář
            ReservationFormController.editReservationId = null;
            MainApp.switchTo("ReservationFormView");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void onEditReservation() {
        if (tfSearchReservation.getText().isEmpty()) {
            showAlert("Chyba", "Nejprve vyhledejte rezervaci (zadejte ID), kterou chcete upravit.");
            return;
        }

        try {
            // Uložíme ID rezervace do statické proměnné ve druhém controlleru
            ReservationFormController.editReservationId = Integer.parseInt(tfSearchReservation.getText());
            MainApp.switchTo("ReservationFormView");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    @FXML
    private void onSearchGuest() {
        String input = tfSearchGuest.getText();

        if (input == null || input.trim().isEmpty()) {
            showAlert("Chyba", "Zadejte prosím číslo OP nebo jméno a příjmení.");
            return;
        }

        ReservationDAO dao = new ReservationDAO();
        try {
            // Získáme kompletní objekt hosta z databáze
            GuestDTO foundGuest = dao.searchGuestUniversal(input);

            // Předáme hosta do druhého Controlleru pomocí statické proměnné
            GuestInfoController.loadedGuest = foundGuest;

            // Přepneme obrazovku
            MainApp.switchTo("GuestInfoView");

        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Nenalezeno", e.getMessage());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ── F1: getStaff ──
    private void getStaff(int p_staff_id) {
        String sql = "SELECT first_name, last_name FROM Staff WHERE staff_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, p_staff_id);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                lblStaffName.setText(rs.getString("first_name") + " " + rs.getString("last_name"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // ── F2: getReservation ──
    @FXML
    private void onSearchReservation() {
        if (tfSearchReservation.getText().isEmpty()) return;
        int p_res_id = Integer.parseInt(tfSearchReservation.getText());

        String sql = "SELECT rm.room_number, r.check_in, r.check_out, g.first_name, g.last_name, r.status " +
                "FROM Reservations r " +
                "JOIN Guests g ON r.guest_id = g.guest_id " +
                "JOIN Rooms rm ON r.room_id = rm.room_id " +
                "WHERE r.reservation_id = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, p_res_id);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                lblRoomValue.setText(rs.getString("room_number"));
                lblCheckinValue.setText(rs.getDate("check_in") + " / " + rs.getDate("check_out"));
                lblHostValue.setText(rs.getString("first_name") + " " + rs.getString("last_name"));
                lblStatusValue.setText(rs.getString("status"));

                // TOTO JE NOVÉ: Zavoláme naši metodu, která načte a vypíše služby
                loadServicesForDisplay(p_res_id);

            } else {
                showAlert("Nenalezeno", "Rezervace s tímto ID neexistuje.");
                lblRoomValue.setText("—");
                lblCheckinValue.setText("—");
                lblHostValue.setText("—");
                lblStatusValue.setText("—");
                lblServicesValue.setText("—"); // Vyčistíme i služby při nenalezení
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // ── F3: cancelReservation ──
    @FXML private void onCancelReservation() {
        updateReservationStatus("CANCELLED", "Rezervace byla zrušena.");
    }

    // ── F5: getReservations (Upraveno pro výpis seznamu) ──
    @FXML
    private void onShowAllReservations() {
        // Změnili jsme dotaz, abychom viděli užitečná data (číslo pokoje, jméno, datum, stav)
        String sql = "SELECT r.reservation_id, rm.room_number, g.first_name, g.last_name, r.check_in, r.check_out, r.status " +
                "FROM Reservations r " +
                "JOIN Rooms rm ON r.room_id = rm.room_id " +
                "JOIN Guests g ON r.guest_id = g.guest_id " +
                "ORDER BY r.check_in ASC"; // Seřadíme od nejbližší

        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            StringBuilder reservationsList = new StringBuilder("Seznam všech rezervací:\n\n");
            int count = 0;

            while (rs.next()) {
                count++;
                reservationsList.append(String.format("ID: %d | Pokoj: %s | Host: %s %s\n",
                        rs.getInt("reservation_id"),
                        rs.getString("room_number"),
                        rs.getString("first_name"),
                        rs.getString("last_name")));
                reservationsList.append(String.format("Od: %tF Do: %tF | Stav: %s\n",
                        rs.getDate("check_in"),
                        rs.getDate("check_out"),
                        rs.getString("status")));
                reservationsList.append("--------------------------------------------------\n");
            }

            if (count == 0) {
                reservationsList.append("V systému aktuálně nejsou žádné rezervace.");
            }

            // Protože Alert může být pro dlouhý seznam malý, použijeme TextArea pro scrollování
            TextArea textArea = new TextArea(reservationsList.toString());
            textArea.setEditable(false);
            textArea.setWrapText(true);
            textArea.setPrefHeight(300); // Nastavíme rozumnou výšku pro scrollování

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Všechny rezervace");
            alert.setHeaderText(null);

            // Místo obyčejného textu vložíme náš TextArea komponent do Alertu
            alert.getDialogPane().setContent(textArea);
            alert.showAndWait();

        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Chyba", "Nepodařilo se načíst rezervace z databáze.");
        }
    }

    // ── F9: getFreeRooms ──
    @FXML private void onFreeRooms() {
        // Ukázka logiky - reálně by sis vzal data z nějakých DatePickerů
        String sql = "SELECT room_number FROM Rooms WHERE room_id NOT IN (" +
                "SELECT room_id FROM Reservations WHERE status IN ('ACTIVE', 'CHECKED_IN'))";
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            StringBuilder freeRooms = new StringBuilder("Volné pokoje:\n");
            while (rs.next()) {
                freeRooms.append(rs.getString("room_number")).append("\n");
            }
            showAlert("Volné pokoje", freeRooms.toString());
        } catch (SQLException e) { e.printStackTrace(); }
    }

    // ── F10: checkIn ──
    @FXML private void onCheckIn() {
        updateReservationStatus("CHECKED_IN", "Host byl úspěšně ubytován (Check-in).");
    }

    @FXML
    private void onCheckOut() {
        if (tfSearchReservation.getText().isEmpty()) return;
        int resId = Integer.parseInt(tfSearchReservation.getText());
        ReservationDAO dao = new ReservationDAO();

        try {
            // Nyní vrací String (např. "SUCCESS: Host odhlášen. Faktura ID = 5, Částka = 1500 CZK")
            String resultMessage = dao.checkOutJavaTransaction(resId);

            // Pokud zpráva začíná "SUCCESS", vše je OK
            if (resultMessage.startsWith("SUCCESS")) {
                showAlert("Check-out úspěšný", resultMessage);
                if (lblStatusValue != null) lblStatusValue.setText("CHECKED_OUT");
            } else {
                // Pokud zpráva začíná "ERROR", procedura provedla rollback
                showAlert("Chyba procedury", resultMessage);
            }

        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Kritická chyba", "Nepodařilo se komunikovat s databází:\n" + e.getMessage());
        }
    }

    // ── F12: calculateTotalBill (Procedura z dřívějška) ──
    @FXML private void onTotalBill() {
        if (tfSearchReservation.getText().isEmpty()) return;
        int p_res_id = Integer.parseInt(tfSearchReservation.getText());
        try (Connection conn = DatabaseManager.getConnection();
             CallableStatement stmt = conn.prepareCall("{call GENERATE_INVOICE(?, ?)}")) {
            stmt.setInt(1, p_res_id);
            stmt.registerOutParameter(2, Types.NUMERIC);
            stmt.execute();
            showAlert("Faktura", "Celková cena: " + stmt.getDouble(2) + " Kč");
        } catch (SQLException e) { e.printStackTrace(); }
    }

    // Pomocná metoda pro F3, F10 a F11
    private void updateReservationStatus(String status, String successMessage) {
        if (tfSearchReservation.getText().isEmpty()) return;
        int p_res_id = Integer.parseInt(tfSearchReservation.getText());
        String sql = "UPDATE Reservations SET status = ? WHERE reservation_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, status);
            stmt.setInt(2, p_res_id);
            if (stmt.executeUpdate() > 0){
                showAlert("Úspěch", successMessage);
                lblStatusValue.setText(status);
            }

        } catch (SQLException e) { e.printStackTrace(); }
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private void loadServicesForDisplay(int resId) {
        String sql = "SELECT s.service_name FROM ServiceUsage su JOIN Services s ON su.service_id = s.service_id WHERE su.reservation_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, resId);
            ResultSet rs = stmt.executeQuery();

            StringBuilder services = new StringBuilder();
            while (rs.next()) {
                if (services.length() > 0) {
                    services.append(", "); // Oddělovač
                }
                services.append(rs.getString("service_name"));
            }

            if (services.length() == 0) {
                lblServicesValue.setText("Žádné");
            } else {
                lblServicesValue.setText(services.toString());
            }

        } catch (SQLException e) {
            e.printStackTrace();
            lblServicesValue.setText("Chyba načítání");
        }
    }
}