package database.dao;

import database.DatabaseManager;
import database.dto.GuestDTO;
import database.dto.ReservationDTO;
import java.sql.*;

public class ReservationDAO {

    // =================================================================================
    // SPLNĚNÝ BOD 1: "metody v ORM" (Transakce řízená z Javy s využitím DTO a JDBC)
    // =================================================================================
    public void saveReservationTransaction(ReservationDTO res) throws SQLException {
        Connection conn = null;
        try {
            conn = DatabaseManager.getConnection();
            conn.setAutoCommit(false); // Začátek transakce na aplikační vrstvě

            // Ukázka vložení rezervace (Parametrizovaná operace)
            String sql = "INSERT INTO Reservations (guest_id, room_id, check_in, check_out, status) VALUES (?, ?, ?, ?, 'ACTIVE')";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                // Tady probíhá to tvé "ruční ORM" - mapuješ DTO do SQL
                stmt.setInt(1, res.getGuestId());
                stmt.setInt(2, res.getRoomId());
                stmt.setDate(3, Date.valueOf(res.getCheckIn()));
                stmt.setDate(4, Date.valueOf(res.getCheckOut()));

                stmt.executeUpdate();
            }



            conn.commit(); // Potvrzení transakce
            System.out.println("Java transakce (ruční ORM) proběhla úspěšně.");

        } catch (SQLException e) {
            if (conn != null) conn.rollback(); // Rollback při chybě
            System.err.println("Chyba v Java transakci, proveden rollback.");
            throw e;
        } finally {
            if (conn != null) conn.setAutoCommit(true);
        }
    }


    // =================================================================================
    // F11 & F12: Check-out a výpočet celkové ceny (Volání uložené procedury)
    // =================================================================================
    public String checkOutAndGenerateInvoice(int reservationId) throws SQLException {
        String call = "{call GENERATE_INVOICE(?, ?)}";

        try (Connection conn = DatabaseManager.getConnection();
             CallableStatement stmt = conn.prepareCall(call)) {

            // 1. Nastavíme vstupní parametr (ID rezervace)
            stmt.setInt(1, reservationId);

            // 2. OPRAVA: Zaregistrujeme výstupní parametr jako VARCHAR (Text)
            stmt.registerOutParameter(2, Types.VARCHAR);

            // 3. Spustíme proceduru
            stmt.execute();

            // 4. Vrátíme TEXTOVOU zprávu zpět do Controlleru
            return stmt.getString(2);
        }
    }

    // Metoda pro získání ID pokoje podle jeho textového čísla z ComboBoxu
    public int getRoomIdByNumber(String roomNumber) throws SQLException {
        String sql = "SELECT room_id FROM Rooms WHERE room_number = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, roomNumber);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("room_id");
            }
            throw new SQLException("Místnost s číslem " + roomNumber + " nebyla v databázi nalezena.");
        }
    }

    // Metoda pro uložení nového hosta, nebo aktualizaci stávajícího (vrací ID)
    public int saveOrUpdateGuest(Integer currentGuestId, String fName, String lName, String email, String phone, String idCard) throws SQLException {
        Connection conn = DatabaseManager.getConnection();

        if (currentGuestId != null) {
            // Úprava stávajícího hosta
            String sql = "UPDATE Guests SET first_name=?, last_name=?, email=?, phone=?, id_card_number=? WHERE guest_id=?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, fName);
                stmt.setString(2, lName);
                stmt.setString(3, email);
                stmt.setString(4, phone);
                stmt.setString(5, idCard);
                stmt.setInt(6, currentGuestId);
                stmt.executeUpdate();
            }
            return currentGuestId;
        } else {
            // Vytvoření nového hosta a získání jeho automaticky vygenerovaného ID
            String sql = "INSERT INTO Guests (first_name, last_name, email, phone, id_card_number) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(sql, new String[]{"guest_id"})) {
                stmt.setString(1, fName);
                stmt.setString(2, lName);
                stmt.setString(3, email);
                stmt.setString(4, phone);
                stmt.setString(5, idCard);
                stmt.executeUpdate();

                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        return rs.getInt(1); // Vrátí nově vygenerované guest_id
                    }
                    throw new SQLException("Nepodařilo se získat ID nového hosta.");
                }
            }
        }
    }


    public String checkOutJavaTransaction(int reservationId) throws SQLException {
        Connection conn = null;
        try {
            conn = DatabaseManager.getConnection();
            conn.setAutoCommit(false); // 1. Začátek transakce v Javě

            // --- KROK 1: Ověření stavu a výpočet počtu nocí ---
            String checkSql = "SELECT status, TRUNC(check_out) - TRUNC(check_in) AS nights FROM Reservations WHERE reservation_id = ?";
            String status = "";
            int nights = 0;

            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setInt(1, reservationId);
                ResultSet rs = checkStmt.executeQuery();
                if (rs.next()) {
                    status = rs.getString("status");
                    nights = rs.getInt("nights");
                } else {
                    throw new SQLException("Rezervace s ID " + reservationId + " neexistuje.");
                }
            }


            if (!"CHECKED_IN".equals(status)) {
                throw new SQLException("Lze odhlásit pouze hosty ve stavu CHECKED_IN. Aktuální stav: " + status);
            }
            if (nights <= 0) {
                throw new SQLException("Datum odjezdu musí být větší než datum příjezdu.");
            }

            // Výpočet celkové částky (Pokoje + Služby) ---
            String calcSql = "SELECT (? * rt.base_price) + NVL(SUM(s.price * su.quantity), 0) AS total_bill " +
                    "FROM Reservations r " +
                    "JOIN Rooms rm ON r.room_id = rm.room_id " +
                    "JOIN RoomTypes rt ON rm.type_id = rt.type_id " +
                    "LEFT JOIN ServiceUsage su ON r.reservation_id = su.reservation_id " +
                    "LEFT JOIN Services s ON su.service_id = s.service_id " +
                    "WHERE r.reservation_id = ? " +
                    "GROUP BY rt.base_price";

            double totalBill = 0;
            try (PreparedStatement calcStmt = conn.prepareStatement(calcSql)) {
                calcStmt.setInt(1, nights); // Dosadíme spočítané noci z kroku 1
                calcStmt.setInt(2, reservationId);
                ResultSet rs = calcStmt.executeQuery();
                if (rs.next()) {
                    totalBill = rs.getDouble("total_bill");
                }
            }

            //Změna stavu rezervace na CHECKED_OUT ---
            String updateSql = "UPDATE Reservations SET status = 'CHECKED_OUT' WHERE reservation_id = ?";
            try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                updateStmt.setInt(1, reservationId);
                updateStmt.executeUpdate();
            }

            // Vytvoření faktury a získání jejího ID ---
            String invoiceSql = "INSERT INTO Invoices (reservation_id, total_amount, issue_date) VALUES (?, ?, CURRENT_TIMESTAMP)";
            int invoiceId = -1;

            
            try (PreparedStatement invStmt = conn.prepareStatement(invoiceSql, new String[]{"invoice_id"})) {
                invStmt.setInt(1, reservationId);
                invStmt.setDouble(2, totalBill);
                invStmt.executeUpdate();

                try (ResultSet rs = invStmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        invoiceId = rs.getInt(1);
                    }
                }
            }


            conn.commit();
            return "SUCCESS: Host odhlášen přes Java transakci! Faktura ID = " + invoiceId + ", Částka = " + totalBill + " CZK";

        } catch (SQLException e) {

            if (conn != null) conn.rollback();
            throw e;
        } finally {
            if (conn != null) conn.setAutoCommit(true);
        }
    }



    public GuestDTO searchGuestUniversal(String searchInput) throws SQLException {
        searchInput = searchInput.trim();
        boolean isNameSearch = searchInput.contains(" ");
        String sql;

        if (isNameSearch) {
            sql = "SELECT guest_id, first_name, last_name, email, phone, id_card_number FROM Guests " +
                    "WHERE LOWER(first_name) = LOWER(?) AND LOWER(last_name) = LOWER(?)";
        } else {
            sql = "SELECT guest_id, first_name, last_name, email, phone, id_card_number FROM Guests " +
                    "WHERE id_card_number = ?";
        }

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            if (isNameSearch) {
                String[] parts = searchInput.split(" ", 2);
                stmt.setString(1, parts[0].trim());
                stmt.setString(2, parts[1].trim());
            } else {
                stmt.setString(1, searchInput);
            }

            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                // Zabalíme data z databáze do DTO "krabičky" a pošleme ven
                return new GuestDTO(
                        rs.getInt("guest_id"),
                        rs.getString("first_name"),
                        rs.getString("last_name"),
                        rs.getString("email"),
                        rs.getString("phone"),
                        rs.getString("id_card_number")
                );
            } else {
                throw new SQLException("Host s údajem '" + searchInput + "' nebyl nalezen.");
            }
        }
    }

    // =================================================================================
    // ZÍSKÁNÍ SEZNAMU REZERVACÍ PRO KONKRÉTNÍHO HOSTA
    // =================================================================================
    public java.util.List<String> getGuestReservations(int guestId) throws SQLException {
        java.util.List<String> reservationsList = new java.util.ArrayList<>();

        // Vytáhneme číslo pokoje, datumy a status. Seřadíme od nejnovější (DESC).
        String sql = "SELECT rm.room_number, r.check_in, r.check_out, r.status " +
                "FROM Reservations r " +
                "JOIN Rooms rm ON r.room_id = rm.room_id " +
                "WHERE r.guest_id = ? " +
                "ORDER BY r.check_in DESC";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, guestId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                String room = rs.getString("room_number");
                java.sql.Date checkIn = rs.getDate("check_in");
                java.sql.Date checkOut = rs.getDate("check_out");
                String status = rs.getString("status");

                // Naformátujeme to hezky pro uživatele
                String row = "Pokoj: " + room + " | " + checkIn + " - " + checkOut + " (" + status + ")";
                reservationsList.add(row);
            }
        }
        return reservationsList;
    }
}