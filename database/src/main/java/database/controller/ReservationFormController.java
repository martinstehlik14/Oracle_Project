package database.controller;

import database.DatabaseManager;
import database.MainApp;
import database.dao.ReservationDAO;
import database.dto.ReservationDTO;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import java.sql.*;

public class ReservationFormController {

    @FXML private Label lblReservationId;
    @FXML private ComboBox<String> cbRoomNumber;
    @FXML private DatePicker dpCheckin;
    @FXML private DatePicker dpCheckout;
    @FXML private TextField tfGuestId, tfFirstName, tfLastName, tfEmail, tfPhone;
    @FXML private CheckBox cbBreakfast, cbWellness, cbParking;

    public static Integer editReservationId = null;
    private Integer currentGuestId = null;

    @FXML
    public void initialize() {
        loadRoomNumbers();

        if (editReservationId != null) {
            loadReservationDataForEdit(editReservationId);
            editReservationId = null;
        }
    }

    @FXML
    private void onBack() throws Exception {
        MainApp.switchTo("MainView");
    }

    @FXML
    private void onSave() {
        // 1. ZÍSKÁNÍ DAT Z OBRAZOVKY A VALIDACE
        if (cbRoomNumber.getValue() == null || dpCheckin.getValue() == null || dpCheckout.getValue() == null) {
            showAlert("Chyba", "Vyplňte prosím číslo místnosti a datumy.");
            return;
        }

        try {
            ReservationDAO dao = new ReservationDAO();

            // 2. Překlad čísla pokoje z ComboBoxu na reálné ID pokoje z databáze
            String selectedRoomNumber = cbRoomNumber.getValue();
            int roomId = dao.getRoomIdByNumber(selectedRoomNumber);

            // 3. Zpracování hosta - vloží do DB nového, nebo upraví stávajícího a vrátí jeho ID
            int guestId = dao.saveOrUpdateGuest(
                    currentGuestId, // proměnná třídy (null pokud je to nová rezervace)
                    tfFirstName.getText(),
                    tfLastName.getText(),
                    tfEmail.getText(),
                    tfPhone.getText(),
                    tfGuestId.getText()
            );

            // 4. Zjištění ID rezervace (null pro novou, číslo pro úpravu stávající)
            Integer resId = null;
            if (!lblReservationId.getText().equals("(nová)")) {
                resId = Integer.parseInt(lblReservationId.getText());
            }

            // 5. Zabalení do DTO
            ReservationDTO dto = new ReservationDTO(
                    resId,
                    guestId,
                    roomId,
                    dpCheckin.getValue(),
                    dpCheckout.getValue()
            );

            // 6. Odeslání do DAO, kde proběhne transakce
            dao.saveReservationTransaction(dto);

            showAlert("Úspěch", "Rezervace byla úspěšně uložena!");
            MainApp.switchTo("MainView");

        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Chyba databáze", "Při ukládání došlo k chybě:\n" + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            showAlert("Chyba", "Nepodařilo se uložit rezervaci: " + e.getMessage());
        }
    }

    // ── NAČÍTÁNÍ DAT ──

    private void loadRoomNumbers() {
        String sql = "SELECT room_number FROM Rooms ORDER BY room_number ASC";
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            cbRoomNumber.getItems().clear();
            while (rs.next()) {
                cbRoomNumber.getItems().add(rs.getString("room_number"));
            }
        } catch (SQLException e) { e.printStackTrace(); }
    }

    private void loadReservationDataForEdit(int resId) {
        String sql = "SELECT r.reservation_id, rm.room_number, r.check_in, r.check_out, " +
                "g.guest_id, g.id_card_number, g.first_name, g.last_name, g.email, g.phone " +
                "FROM Reservations r " +
                "JOIN Rooms rm ON r.room_id = rm.room_id " +
                "JOIN Guests g ON r.guest_id = g.guest_id " +
                "WHERE r.reservation_id = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, resId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                lblReservationId.setText(String.valueOf(rs.getInt("reservation_id")));
                cbRoomNumber.setValue(rs.getString("room_number"));

                if (rs.getDate("check_in") != null) dpCheckin.setValue(rs.getDate("check_in").toLocalDate());
                if (rs.getDate("check_out") != null) dpCheckout.setValue(rs.getDate("check_out").toLocalDate());

                currentGuestId = rs.getInt("guest_id");
                tfGuestId.setText(rs.getString("id_card_number"));
                tfFirstName.setText(rs.getString("first_name"));
                tfLastName.setText(rs.getString("last_name"));
                tfEmail.setText(rs.getString("email"));
                tfPhone.setText(rs.getString("phone"));

                // Načtení služeb
                loadServicesForEdit(resId);
            }
        } catch (SQLException e) { e.printStackTrace(); }
    }

    private void loadServicesForEdit(int resId) {
        // Vytáhneme služby a zaškrtneme CheckBoxy podle toho, co najdeme v databázi
        String sql = "SELECT s.service_name FROM ServiceUsage su JOIN Services s ON su.service_id = s.service_id WHERE su.reservation_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, resId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String name = rs.getString("service_name").toLowerCase();
                if (name.contains("snídaně") || name.contains("snidane")) cbBreakfast.setSelected(true);
                if (name.contains("wellness")) cbWellness.setSelected(true);
                if (name.contains("parkování") || name.contains("parkovani")) cbParking.setSelected(true);
            }
        } catch (SQLException e) { e.printStackTrace(); }
    }

    // ── UKLÁDACÍ LOGIKA ──

    private int getRoomId(String roomNumber) throws SQLException {
        String sql = "SELECT room_id FROM Rooms WHERE room_number = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, roomNumber);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt("room_id");
            throw new SQLException("Místnost s číslem " + roomNumber + " nebyla nalezena.");
        }
    }

    private int saveOrUpdateGuest() throws SQLException {
        Connection conn = DatabaseManager.getConnection();
        if (currentGuestId != null) {
            String sql = "UPDATE Guests SET first_name=?, last_name=?, email=?, phone=?, id_card_number=? WHERE guest_id=?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, tfFirstName.getText());
                stmt.setString(2, tfLastName.getText());
                stmt.setString(3, tfEmail.getText());
                stmt.setString(4, tfPhone.getText());
                stmt.setString(5, tfGuestId.getText());
                stmt.setInt(6, currentGuestId);
                stmt.executeUpdate();
            }
            return currentGuestId;
        } else {
            String sql = "INSERT INTO Guests (first_name, last_name, email, phone, id_card_number) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(sql, new String[]{"guest_id"})) {
                stmt.setString(1, tfFirstName.getText());
                stmt.setString(2, tfLastName.getText());
                stmt.setString(3, tfEmail.getText());
                stmt.setString(4, tfPhone.getText());
                stmt.setString(5, tfGuestId.getText());
                stmt.executeUpdate();
                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) return rs.getInt(1);
                    throw new SQLException("Nepodařilo se získat ID nového hosta.");
                }
            }
        }
    }

    // Změněno na návratový typ int, abychom vrátili nové reservation_id
    private int addReservation(int guestId, int roomId, Date checkIn, Date checkOut) throws SQLException {
        String sql = "INSERT INTO Reservations (guest_id, room_id, check_in, check_out, status) VALUES (?, ?, ?, ?, 'ACTIVE')";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, new String[]{"reservation_id"})) {
            stmt.setInt(1, guestId);
            stmt.setInt(2, roomId);
            stmt.setDate(3, checkIn);
            stmt.setDate(4, checkOut);
            stmt.executeUpdate();

            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
                throw new SQLException("Nepodařilo se získat ID nové rezervace.");
            }
        }
    }

    private void changeReservation(int resId, int roomId, Date checkIn, Date checkOut) throws SQLException {
        String sql = "UPDATE Reservations SET room_id = ?, check_in = ?, check_out = ? WHERE reservation_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, roomId);
            stmt.setDate(2, checkIn);
            stmt.setDate(3, checkOut);
            stmt.setInt(4, resId);
            stmt.executeUpdate();
        }
    }

    // --- NOVÁ LOGIKA PRO SLUŽBY ---

    private void saveServices(int reservationId) throws SQLException {
        try (Connection conn = DatabaseManager.getConnection()) {
            // 1. Zjistíme ID služeb podle názvu v databázi (bezpečně ignoruje, pokud neexistují)
            int idBreakfast = getServiceId(conn, "Snídaně");
            int idWellness = getServiceId(conn, "Wellness");
            int idParking = getServiceId(conn, "Parkování");

            // 2. Vymažeme tyto specifické služby u dané rezervace (aby se daly odškrtnout),
            // ale minibary a další útraty necháme!
            String delSql = "DELETE FROM ServiceUsage WHERE reservation_id = ? AND service_id IN (?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(delSql)) {
                stmt.setInt(1, reservationId);
                stmt.setInt(2, idBreakfast);
                stmt.setInt(3, idWellness);
                stmt.setInt(4, idParking);
                stmt.executeUpdate();
            }

            // 3. Vložíme ty, které jsou zaškrtnuté
            insertServiceUsage(conn, reservationId, idBreakfast, cbBreakfast.isSelected());
            insertServiceUsage(conn, reservationId, idWellness, cbWellness.isSelected());
            insertServiceUsage(conn, reservationId, idParking, cbParking.isSelected());
        }
    }

    private int getServiceId(Connection conn, String partialName) {
        String sql = "SELECT service_id FROM Services WHERE service_name LIKE ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, "%" + partialName + "%");
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return -1; // Služba v DB neexistuje
    }

    private void insertServiceUsage(Connection conn, int resId, int serviceId, boolean isSelected) throws SQLException {
        if (!isSelected || serviceId == -1) return;

        // Zjednodušeně vkládáme quantity 1 k aktuálnímu datu
        String sql = "INSERT INTO ServiceUsage (reservation_id, service_id, quantity, usage_date) VALUES (?, ?, 1, CURRENT_TIMESTAMP)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, resId);
            stmt.setInt(2, serviceId);
            stmt.executeUpdate();
        }
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}