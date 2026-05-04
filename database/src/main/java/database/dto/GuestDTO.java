package database.dto;

public class GuestDTO {
    private Integer guestId;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String idCardNumber;

    public GuestDTO(Integer guestId, String firstName, String lastName, String email, String phone, String idCardNumber) {
        this.guestId = guestId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.phone = phone;
        this.idCardNumber = idCardNumber;
    }

    // Gettery
    public Integer getGuestId() { return guestId; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getIdCardNumber() { return idCardNumber; }
}