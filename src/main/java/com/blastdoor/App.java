package com.blastdoor;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * JavaFX App
 */
public class App extends Application {

    private static Scene scene;
    private PrimaryController primary;
    @Override
    public void start(Stage stage) throws IOException
    {
        primary = new PrimaryController();
        scene = new Scene(primary);
        stage.setScene(scene);
        stage.setTitle("Overseer's Terminal");
        stage.show();

        stage.setOnCloseRequest(windowEvent ->
        {
            primary.close();
        });
    }

    private static Parent loadFXML(String fxml) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(App.class.getResource(fxml + ".fxml"));
        return fxmlLoader.load();
    }

    public static void main(String[] args) {
        System.out.println("launch");
        launch();
    }

}