module com.blastdoor {
    requires javafx.controls;
    requires javafx.fxml;
    requires com.fazecast.jSerialComm;

    opens com.blastdoor to javafx.fxml;
    exports com.blastdoor;
}