package com.blastdoor;

import java.io.IOException;
import java.net.URL;
import java.util.Base64;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.ResourceBundle;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.StringConverter;

import javax.crypto.Mac;

public class PrimaryController extends BorderPane implements Runnable
{
    @FXML
    public Button scanButton, openButton, sendButton, clearButton;
    @FXML
    public ComboBox<String> portComboBox, writeStyleComboBox, newlineComboBox, byteEndOptionComboBox, readOptionComboBox;
    @FXML
    public ComboBox<Integer> baudComboBox;
    @FXML
    public TextArea termTextArea;
    @FXML
    public TextField sendTextField;
    @FXML
    public Label instructionLabel;
    @FXML
    public HBox writeHBox;
    @FXML
    public VBox macroVBox;
    @FXML
    public HBox macroInfoHBox;
    @FXML
    public TitledPane macroPane;
    @FXML
    public Spinner<Integer> lineSpinner;


    private SerialPort port;
    private SerialPort[] portList;
    private boolean txMark = true;
    private Thread thread;

    public PrimaryController()
    {
        super();

        try
        {
            //load
            FXMLLoader loader = new FXMLLoader(App.class.getResource("primary.fxml"));
            loader.setRoot(this);
            loader.setController(this);
            loader.load();
        } catch (IOException e)
        {
            e.printStackTrace();
        }

        thread = new Thread(this);

        baudComboBox.getItems().addAll(2400, 4800, 9600, 14400, 19200, 28800, 38400, 57600, 76800, 115200, 230400, 250000, 500000, 1000000);
        newlineComboBox.getItems().addAll("No CR/LF", "CR", "LF", "CR+LF");
        byteEndOptionComboBox.getItems().addAll("None", "DXLCKS (Dynamixel protocol 1.0 checksum)", "DXLCRC (Dynamixel protocol 2.0 CRC)");
        readOptionComboBox.getItems().addAll("ASCII", "Byte");
        readOptionComboBox.getSelectionModel().clearAndSelect(0);
        byteEndOptionComboBox.getSelectionModel().clearAndSelect(0);
        newlineComboBox.getSelectionModel().clearAndSelect(0);
        baudComboBox.getSelectionModel().clearAndSelect(9);
        writeStyleComboBox.getItems().setAll("ASCII", "Byte");
        writeStyleComboBox.getSelectionModel().clearAndSelect(0);
        setInstructionMessage();
        termTextArea.setWrapText(true);

        //event handling
        termTextArea.textProperty().addListener(observable ->
        {
            if(termTextArea.scrollTopProperty().get() > lineSpinner.getValue())
            {
                Platform.runLater(() -> {
                    termTextArea.clear();
                });
            }
        });

        sendTextField.textProperty().addListener(observable -> setInstructionMessage());
        byteEndOptionComboBox.getSelectionModel().selectedIndexProperty().addListener(observable -> setInstructionMessage());

        writeStyleComboBox.selectionModelProperty().get().selectedIndexProperty().addListener(observable ->
        {
            setInstructionMessage();
            if(writeStyleComboBox.getValue().equals("ASCII"))
            {
                if(writeHBox.getChildren().contains(byteEndOptionComboBox)) writeHBox.getChildren().remove(byteEndOptionComboBox);
                if(writeHBox.getChildren().indexOf(newlineComboBox) == -1) writeHBox.getChildren().add(2, newlineComboBox);
            }
            else if(writeStyleComboBox.getValue().equals("Byte"))
            {
                if(writeHBox.getChildren().contains(newlineComboBox)) writeHBox.getChildren().remove(newlineComboBox);
                if(!writeHBox.getChildren().contains(byteEndOptionComboBox)) writeHBox.getChildren().add(2, byteEndOptionComboBox);
            }
        });

        sendTextField.setOnAction(actionEvent ->
        {
            if(port != null && port.isOpen()) sendButtonClicked();
        });

        this.addEventFilter(MacroView.DELETE,macroViewEvent ->
        {
            if(macroVBox.getChildren().contains(macroViewEvent.source))
            {
                macroVBox.getChildren().remove(macroViewEvent.source);
            }
        });
        //spinner
        SpinnerValueFactory.IntegerSpinnerValueFactory spinnerValueFactory = new SpinnerValueFactory.IntegerSpinnerValueFactory(0,10000000,0,1);
        lineSpinner.setValueFactory(spinnerValueFactory);
        lineSpinner.getValueFactory().setValue(1000);
        lineSpinner.setEditable(true);

        //init
        writeHBox.getChildren().remove(byteEndOptionComboBox);
        scanPort();
        thread.start();
    }

    private byte[] convertToByteArray(String input) throws NumberFormatException, IllegalArgumentException, DxlProtocol1ParseException, DxlProtocol2ParseException
    {
        if(input.length() == 0) throw new IllegalArgumentException();
        byte[] byteArray = null;

        switch(writeStyleComboBox.getValue())
        {
            case "ASCII":
            {
                if(newlineComboBox.getValue().equals("CR") || newlineComboBox.getValue().equals("LF"))
                {
                    byteArray = new byte[input.length()+1];
                    byteArray[input.length()] = '\r';
                }
                if(newlineComboBox.getValue().equals("CR+LF"))
                {
                    byteArray = new byte[input.length()+2];
                    byteArray[input.length()] = '\r';
                    byteArray[input.length()+1] = '\n';
                }
                else
                {
                    byteArray = new byte[input.length()];
                }
                for (int i = 0; i < input.length(); i++)
                {
                    byteArray[i] = (byte) (input.charAt(i));
                }
                break;
            }
            case "Byte":
            {
                String[] split = input.split(" ");

                switch(byteEndOptionComboBox.getValue())
                {
                    case "DXLCKS (Dynamixel protocol 1.0 checksum)":
                    {
                        byteArray = new byte[split.length+1];
                        break;
                    }
                    case "DXLCRC (Dynamixel protocol 2.0 CRC)":
                    {
                        byteArray = new byte[split.length+2];
                        break;
                    }
                    default:
                    {
                        byteArray = new byte[split.length];
                    }
                }


                for (int i = 0; i < split.length; i++)
                {
                    int singleByte = 4;

                    if (split[i].startsWith("0x") || split[i].startsWith("0X"))
                    {
                        singleByte = Integer.parseInt(split[i].substring(2), 16);
                    }
                    else if (split[i].startsWith("0b") || split[i].startsWith("0B"))
                    {
                        singleByte = Integer.parseInt(split[i].substring(2), 2);
                    }
                    else if (split[i].startsWith("0o") || split[i].startsWith("0O"))
                    {
                        singleByte = Integer.parseInt(split[i].substring(2), 8);
                    }
                    else if (split[i].startsWith("0d") || split[i].startsWith("0D"))
                    {
                        singleByte = Integer.parseInt(split[i].substring(2), 10);
                    } else
                    {
                        singleByte = Integer.parseInt(split[i], 10);
                    }

                    if (0 <= singleByte && singleByte <= 255)
                    {
                        byteArray[i] = (byte) singleByte;
                    } else
                    {
                        throw new IllegalArgumentException();
                    }
                }

                switch(byteEndOptionComboBox.getValue())
                {
                    case "DXLCKS (Dynamixel protocol 1.0 checksum)":
                    {
                        if(byteArray.length < 5 || byteArray[0] != (byte)0xFF || byteArray[1] != (byte)0xFF) throw new DxlProtocol1ParseException();
                        byteArray[byteArray.length - 1] = 0x44;
                        break;
                    }
                    case "DXLCRC (Dynamixel protocol 2.0 CRC)":
                    {
                        if(byteArray.length < 9 || byteArray[0] != (byte)0xFF || byteArray[1] != (byte)0xFF || byteArray[2] != (byte)0xFD || byteArray[3] != (byte)0x00) throw new DxlProtocol2ParseException();
                        byteArray[byteArray.length-2] = (byte)(getCRC(byteArray, byteArray.length-2) & 0xff);
                        byteArray[byteArray.length-1] = (byte)((getCRC(byteArray, byteArray.length-2) >> 8) & 0xff);
                        break;
                    }
                }

                break;
            }

        }
        return byteArray;
    }

    private String generateDisplayString(String writeString)
    {
        switch(writeStyleComboBox.getValue())
        {
            case "ASCII":
            {
                if (newlineComboBox.getValue().equals("CR")) return writeString + "\\r";
                if (newlineComboBox.getValue().equals("LF")) return writeString + "\\n";
                if (newlineComboBox.getValue().equals("CR+LF")) return writeString + "\\r\\n";
                break;
            }
            case "Byte":
            {
                switch (byteEndOptionComboBox.getValue())
                {
                    case "None": return writeString;
                    case "DXLCKS (Dynamixel protocol 1.0 checksum)": return writeString + " Checksum";
                    case "DXLCRC (Dynamixel protocol 2.0 CRC)" : return writeString + " CRC";
                }
            }
        }

        return writeString;
    }

    private void scanPort()
    {
        portComboBox.getItems().clear();
        portList = SerialPort.getCommPorts();
        for(SerialPort port : portList)
        {
            portComboBox.getItems().add(port.getDescriptivePortName());
        }

        if(portComboBox.getItems().size() > 0)  portComboBox.getSelectionModel().clearAndSelect(0);
    }

    private void setInstructionMessage()
    {
        if(writeStyleComboBox.getValue() == "ASCII")
        {
            instructionLabel.setTextFill(Color.web("#000000"));
            instructionLabel.setText("ASCII값을 전송합니다.");
        }
        else if(writeStyleComboBox.getValue() == "Byte")
        {
            if (!sendTextField.getText().equals(""))
            {
                try
                {
                    convertToByteArray(sendTextField.getText());
                } catch(DxlProtocol1ParseException e)
                {
                    instructionLabel.setTextFill(Color.web("#ff0000"));
                    instructionLabel.setText("잘못된 Dynamixel Protocol 1 패킷입니다.");
                    return;
                }
                catch(DxlProtocol2ParseException e)
                {
                    instructionLabel.setTextFill(Color.web("#ff0000"));
                    instructionLabel.setText("잘못된 Dynamixel Protocol 2 패킷입니다.");
                    return;
                }
                catch (Exception e)
                {
                    instructionLabel.setTextFill(Color.web("#ff0000"));
                    instructionLabel.setText("잘못된 형식입니다.");
                    return;
                }
            }

            instructionLabel.setTextFill(Color.web("#000000"));
            instructionLabel.setText("바이트 단위로 전송합니다. 띄어쓰기로 바이트를 구분하며, 접두어 0x, 0b, 0o, 0d를 사용해 Base를 지정합니다. 미지정시 0d로 간주합니다. ex)0xff 0o123");
        }
    }

    short getCRC(byte[] data_blk_ptr, int data_blk_size)
    {
        short crc_accum = 0;
        int i, j;
        int crc_table[] = {
        0x0000, 0x8005, 0x800F, 0x000A, 0x801B, 0x001E, 0x0014, 0x8011,
                0x8033, 0x0036, 0x003C, 0x8039, 0x0028, 0x802D, 0x8027, 0x0022,
                0x8063, 0x0066, 0x006C, 0x8069, 0x0078, 0x807D, 0x8077, 0x0072,
                0x0050, 0x8055, 0x805F, 0x005A, 0x804B, 0x004E, 0x0044, 0x8041,
                0x80C3, 0x00C6, 0x00CC, 0x80C9, 0x00D8, 0x80DD, 0x80D7, 0x00D2,
                0x00F0, 0x80F5, 0x80FF, 0x00FA, 0x80EB, 0x00EE, 0x00E4, 0x80E1,
                0x00A0, 0x80A5, 0x80AF, 0x00AA, 0x80BB, 0x00BE, 0x00B4, 0x80B1,
                0x8093, 0x0096, 0x009C, 0x8099, 0x0088, 0x808D, 0x8087, 0x0082,
                0x8183, 0x0186, 0x018C, 0x8189, 0x0198, 0x819D, 0x8197, 0x0192,
                0x01B0, 0x81B5, 0x81BF, 0x01BA, 0x81AB, 0x01AE, 0x01A4, 0x81A1,
                0x01E0, 0x81E5, 0x81EF, 0x01EA, 0x81FB, 0x01FE, 0x01F4, 0x81F1,
                0x81D3, 0x01D6, 0x01DC, 0x81D9, 0x01C8, 0x81CD, 0x81C7, 0x01C2,
                0x0140, 0x8145, 0x814F, 0x014A, 0x815B, 0x015E, 0x0154, 0x8151,
                0x8173, 0x0176, 0x017C, 0x8179, 0x0168, 0x816D, 0x8167, 0x0162,
                0x8123, 0x0126, 0x012C, 0x8129, 0x0138, 0x813D, 0x8137, 0x0132,
                0x0110, 0x8115, 0x811F, 0x011A, 0x810B, 0x010E, 0x0104, 0x8101,
                0x8303, 0x0306, 0x030C, 0x8309, 0x0318, 0x831D, 0x8317, 0x0312,
                0x0330, 0x8335, 0x833F, 0x033A, 0x832B, 0x032E, 0x0324, 0x8321,
                0x0360, 0x8365, 0x836F, 0x036A, 0x837B, 0x037E, 0x0374, 0x8371,
                0x8353, 0x0356, 0x035C, 0x8359, 0x0348, 0x834D, 0x8347, 0x0342,
                0x03C0, 0x83C5, 0x83CF, 0x03CA, 0x83DB, 0x03DE, 0x03D4, 0x83D1,
                0x83F3, 0x03F6, 0x03FC, 0x83F9, 0x03E8, 0x83ED, 0x83E7, 0x03E2,
                0x83A3, 0x03A6, 0x03AC, 0x83A9, 0x03B8, 0x83BD, 0x83B7, 0x03B2,
                0x0390, 0x8395, 0x839F, 0x039A, 0x838B, 0x038E, 0x0384, 0x8381,
                0x0280, 0x8285, 0x828F, 0x028A, 0x829B, 0x029E, 0x0294, 0x8291,
                0x82B3, 0x02B6, 0x02BC, 0x82B9, 0x02A8, 0x82AD, 0x82A7, 0x02A2,
                0x82E3, 0x02E6, 0x02EC, 0x82E9, 0x02F8, 0x82FD, 0x82F7, 0x02F2,
                0x02D0, 0x82D5, 0x82DF, 0x02DA, 0x82CB, 0x02CE, 0x02C4, 0x82C1,
                0x8243, 0x0246, 0x024C, 0x8249, 0x0258, 0x825D, 0x8257, 0x0252,
                0x0270, 0x8275, 0x827F, 0x027A, 0x826B, 0x026E, 0x0264, 0x8261,
                0x0220, 0x8225, 0x822F, 0x022A, 0x823B, 0x023E, 0x0234, 0x8231,
                0x8213, 0x0216, 0x021C, 0x8219, 0x0208, 0x820D, 0x8207, 0x0202
    };

        for(j = 0; j < data_blk_size; j++)
        {
            i = ((int)(crc_accum >> 8) ^ data_blk_ptr[j]) & 0xFF;
            crc_accum = (short) ((crc_accum << 8) ^ crc_table[i]);
        }

        return crc_accum;
    }

    @FXML
    public void scanButtonClicked()
    {
        scanPort();
    }

    @FXML
    public void openButtonClicked()
    {
        if(port != null && port.isOpen())
        {
            if(port.closePort())
            {
                System.out.println("port closed");
                openButton.setText("Open");
                portComboBox.setDisable(false);
                baudComboBox.setDisable(false);
                scanButton.setDisable(false);
                writeHBox.setDisable(true);

                Iterator<Node> itr = macroVBox.getChildren().listIterator();


                Node node = null;
                while(itr.hasNext())
                {
                    node = itr.next();
                    MacroView macroView = (MacroView) node;
                    macroView.setActive(false);
                }

                macroPane.setDisable(true);
            }
            else
            {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Error");
                alert.setHeaderText("포트를 닫는 데 실패했습니다.");
                alert.setContentText("");
                alert.show();
            }
        }
        else
        {
            if(portComboBox.getSelectionModel().getSelectedIndex() == -1)
            {
                return;
            }

            port = portList[portComboBox.getSelectionModel().getSelectedIndex()];
            port.setBaudRate(baudComboBox.getValue());

            if(port.openPort())
            {
                openButton.setText("Close");
                portComboBox.setDisable(true);
                baudComboBox.setDisable(true);
                scanButton.setDisable(true);
                writeHBox.setDisable(false);

                port.removeDataListener();
                port.addDataListener(new SerialPortDataListener()
                {
                    @Override
                    public int getListeningEvents()
                    {
                        System.out.println("read!!");
                        return SerialPort.LISTENING_EVENT_DATA_AVAILABLE;
                    }

                    @Override
                    public void serialEvent(SerialPortEvent serialPortEvent)
                    {
                        readData();
                    }
                });

                macroPane.setDisable(false);
            }
            else
            {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Error");
                alert.setHeaderText("포트를 여는 데 실패했습니다.");
                alert.setContentText("포트가 다른 프로세스에서 사용되고 있는지 확인하십시오.");
                alert.show();
            }
        }
    }

    private void readData()
    {
        byte[] readByteArr = new byte[port.bytesAvailable()];
        port.readBytes(readByteArr,readByteArr.length);

        String displayStirng = "";

        if(txMark)
        {
            displayStirng +="\nRX : ";
            txMark = false;
        }

        if(readOptionComboBox.getValue() == "Byte")
        {
            for (byte b : readByteArr)
            {
                displayStirng += "0x" + Integer.toHexString(b & 0xff).toUpperCase() + " ";
            }
        }
        else if(readOptionComboBox.getValue().equals("ASCII"))
        {
            for (byte b : readByteArr)
            {
                displayStirng += Character.toString((char)b);
            }
        }

        final String runlaterDisplayString = displayStirng;
        javafx.application.Platform.runLater( () -> termTextArea.appendText(runlaterDisplayString));
    }

    private void writeAndDisplay(byte[] byteArray, String display)
    {
        Platform.runLater(()->
        {
            port.writeBytes(byteArray, byteArray.length);
            if(!txMark){
                termTextArea.appendText("\n");
            }
            txMark = true;
            termTextArea.appendText("\nTX : " + display
                    + "\n");
        });
    }

    public void close()
    {
        if(port!=null && port.isOpen())
        {
            port.closePort();
        }
        thread.interrupt();
    }

    @FXML
    public void sendButtonClicked()
    {
        String dataString = sendTextField.getText();


        try
        {
            byte[] byteArray = convertToByteArray(dataString);
            writeAndDisplay(convertToByteArray(dataString), generateDisplayString(dataString));
        }catch (Exception e)
        {

        }
    }

    @FXML
    public void clearButtonClicked()
    {
        termTextArea.clear();
    }

    @FXML
    public void addMacroButtonClicked()
    {
        try
        {
            macroVBox.getChildren().add(new MacroView(convertToByteArray(sendTextField.getText()), generateDisplayString(sendTextField.getText())));
            macroPane.setExpanded(true);
        }catch(Exception e)
        {

        }
    }

    @Override
    public void run()
    {
        while(true)
        {
            try
            {
                Iterator<Node> itr = macroVBox.getChildren().listIterator();

                Node node = null;
                while(itr.hasNext())
                {
                    node = itr.next();
                    MacroView macroView = (MacroView) node;
                    if(macroView.isActive() && macroView.advanceOneMillisecond())
                    {
                        writeAndDisplay(macroView.getByteArray(), macroView.getDisplayName());
                    }
                }
                thread.sleep(1);
            } catch (InterruptedException e)
            {
                System.out.println("thread interrupted");
                return;
            }
        }
    }
}
