package com.blastdoor;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.event.Event;
import javafx.event.EventType;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.util.StringConverter;

import java.io.IOException;
import java.time.Instant;


public class MacroView extends HBox
{
    @FXML
    public Label nameLabel, byteArrayLabel;
    @FXML
    public Button playButton, deleteButton;
    @FXML
    public Spinner<Integer> spinner;

    private boolean deleteRequested = false;

    private long lastUpdateTime;

    private byte[] byteArray;
    private String displayName;
    private BooleanProperty active;

    public static EventType<MacroViewEvent> DELETE = new EventType<>("MACRO_VIEW_DELETE");

    class MacroViewEvent extends Event
    {
        public Node source;
        public MacroViewEvent(EventType<? extends Event> eventType, Node source)
        {
            super(eventType);
            this.source = source;
        }
    }

    public MacroView(byte[] byteArray, String displayName)
    {
        super();

        this.byteArray = byteArray;
        this.displayName = displayName;
        active = new SimpleBooleanProperty(false);

        try
        {
            //load
            FXMLLoader loader = new FXMLLoader(App.class.getResource("macro.fxml"));
            loader.setRoot(this);
            loader.setController(this);
            loader.load();

            nameLabel.setText(displayName);

            String byteArrayText = "";
            for(byte b : byteArray)
            {
                byteArrayText += String.format("0x%02X ", b);
            }
            byteArrayLabel.setText(byteArrayText);



            SpinnerValueFactory.IntegerSpinnerValueFactory spinnerValueFactory = new SpinnerValueFactory.IntegerSpinnerValueFactory(0,10000000,0,1);
            spinnerValueFactory.setConverter(new StringConverter<Integer>()
            {

                @Override
                public String toString(Integer value)
                {
                    return String.format("%d ms", value);
                }

                @Override
                public Integer fromString(String string)
                {
                    String valueWithoutUnits = string.trim().replaceAll("ms", "").replaceAll("ms", "").replaceAll(" ","");
                    if (valueWithoutUnits.isEmpty())
                    {
                        return 0;
                    }
                    else
                    {
                        try
                        {
                            return Integer.valueOf(valueWithoutUnits);
                        }
                        catch(Exception e)
                        {
                            spinner.cancelEdit();
                            return spinner.getValue();
                        }
                    }
                }

            });
            spinner.setValueFactory(spinnerValueFactory);

        } catch (IOException e)
        {
            e.printStackTrace();
        }

        //property binding

        //event handling
        active.addListener(observable ->
        {
            setPlayIcon();
        });
    }

    private void setPlayIcon()
    {
        if(!active.get())
        {
            playButton.setStyle("-fx-background-color: #4CAF50; -fx-shape:  \"M8 5v14l11-7z\";");
        }
        else
        {
            playButton.setStyle("" +
                    "-fx-background-color: #F44336;" +
                    "-fx-shape: \"M6 6h12v12H6z\";");
        }
    }

    @FXML
    public void deleteButtonClicked()
    {
        this.fireEvent(new MacroViewEvent(DELETE,this));
    }
    @FXML
    public void playButtonClicked()
    {
        lastUpdateTime = System.currentTimeMillis();
        active.set(!active.get());
    }

    byte[] getByteArray()
    {
        Platform.runLater(()->
        {
            if (spinner.getValue() == 0) active.set(false);
        });

        return byteArray;
    }

    public String getDisplayName()
    {
        return displayName;
    }
    public boolean advanceOneMillisecond()
    {
        if(System.currentTimeMillis() >= spinner.getValue() + lastUpdateTime)
        {
            lastUpdateTime = System.currentTimeMillis();
            return true;
        }
        else
        {
            return false;
        }
    }

    public boolean isActive()
    {
        return active.get();
    }

    public BooleanProperty activeProperty()
    {
        return active;
    }

    public void setActive(boolean active)
    {
        this.active.set(active);
    }

}
