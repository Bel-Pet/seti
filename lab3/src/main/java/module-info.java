module com.example.lab {
    requires javafx.controls;
    requires javafx.fxml;
    requires org.json;
    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    requires javafx.graphics;
    requires java.net.http;


    opens com.example.lab3 to javafx.fxml;
    exports com.example.lab3;
}