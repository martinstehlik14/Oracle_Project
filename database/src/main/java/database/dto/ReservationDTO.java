package database.dto;
import java.time.LocalDate;

// Toto je DTO
public class ReservationDTO {
    private Integer reservationId;
    private Integer guestId;
    private Integer roomId;
    private LocalDate checkIn;
    private LocalDate checkOut;

    // Konstruktory, Gettery a Settery...
    public ReservationDTO(Integer reservationId, Integer guestId, Integer roomId, LocalDate checkIn, LocalDate checkOut) {
        this.reservationId = reservationId;
        this.guestId = guestId;
        this.roomId = roomId;
        this.checkIn = checkIn;
        this.checkOut = checkOut;
    }

    //gettery

    public Integer getReservationId() {
        return reservationId;
    }
    public void setReservationId(Integer reservationId) {
        this.reservationId = reservationId;
    }
    public Integer getGuestId() {
        return guestId;
    }
    public void setGuestId(Integer guestId) {
        this.guestId = guestId;
    }

    public Integer getRoomId() {
        return roomId;
    }

    public void setRoomId(Integer roomId) {
        this.roomId = roomId;
    }

    public LocalDate getCheckIn() {
        return checkIn;
    }

    public void setCheckIn(LocalDate checkIn) {
        this.checkIn = checkIn;
    }

    public LocalDate getCheckOut() {
        return checkOut;
    }

    public void setCheckOut(LocalDate checkOut) {
        this.checkOut = checkOut;
    }

    
    
}