module database {
    requires javafx.controls;
    requires javafx.fxml;
    requires com.zaxxer.hikari;
    requires java.sql;

    exports database;
    opens database to javafx.fxml,javafx.graphics;
    opens database.controller to javafx.fxml;
}