package laudio;

import java.awt.geom.Point2D;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import javafx.application.Application;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Slider;
import javafx.scene.image.Image;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

public class Main extends Application {
    
    private Scene scene;
    public static Map<String, String> configs = new HashMap<String, String>();
    
    @Override
    public void start(Stage stage) {
        VBox mContainer = new VBox();
        scene = new Scene(mContainer, 320, 193);
                
        Label lab = new Label("LAudio");
        Image tofImg = new Image(getClass().getResourceAsStream("/res/open.png"));
        Image tosImg = new Image(getClass().getResourceAsStream("/res/note.png"));
        Image tstImg = new Image(getClass().getResourceAsStream("/res/themes.png"));
        UIButton tofBtn = new UIButton(tofImg, 20, 20, 16, 16, () -> {
            DirectoryChooser fc = new DirectoryChooser();
            fc.setInitialDirectory(new java.io.File(Player.path));
            fc.setTitle("Select source folder");
            java.io.File dir = fc.showDialog(stage);
            if (dir != null && dir.exists()) {
                Player.LoadFiles(dir.getPath());
                if (!Player.files.contains(Player.selected) && Player.files.size() > 0) Player.Set(0);
                configs.put("source_folder", Player.path);
                Save();
            }
        }, "tools-btn");
        UIButton tosBtn = new UIButton(tosImg, 20, 20, 18, 16, () -> {
            FileChooser fc = new FileChooser();
            FileChooser.ExtensionFilter allf = new FileChooser.ExtensionFilter("All audio files", "*.mp3", "*.MP3", "*.ogg", "*.OGG", "*.wav", "*.WAV", "*.aac", "*.AAC", "*.flac", "*.FLAC");
            fc.getExtensionFilters().add(allf);
            FileChooser.ExtensionFilter mp3f = new FileChooser.ExtensionFilter("MP3 files (*.mp3)", "*.mp3", "*.MP3");
            fc.getExtensionFilters().add(mp3f);
            FileChooser.ExtensionFilter oggf = new FileChooser.ExtensionFilter("OGG files (*.ogg)", "*.ogg", "*.OGG");
            fc.getExtensionFilters().add(oggf);
            FileChooser.ExtensionFilter wavf = new FileChooser.ExtensionFilter("WAV files (*.wav)", "*.wav", "*.WAV");
            fc.getExtensionFilters().add(wavf);
            FileChooser.ExtensionFilter aacf = new FileChooser.ExtensionFilter("AAC files (*.aac)", "*.aac", "*.AAC");
            fc.getExtensionFilters().add(aacf);
            FileChooser.ExtensionFilter flacf = new FileChooser.ExtensionFilter("FLAC files (*.flac)", "*.flac", "*.FLAC");
            fc.getExtensionFilters().add(flacf);
            fc.setInitialDirectory(new java.io.File(Player.path));
            fc.setTitle("Select source folder");
            java.io.File file = fc.showOpenDialog(stage);
            if (file != null && file.exists()) {
                Player.LoadFile(file);
                if (!Player.files.contains(Player.selected) && Player.files.size() > 0) Player.Set(0);
                configs.put("source_folder", Player.path);
                Save();
            }
        }, "tools-btn");
        UIButton tstBtn = new UIButton(tstImg, 20, 20, 16, 16, null, "tools-btn");
        ContextMenu contextMenu = new ContextMenu();
        contextMenu.getStyleClass().add("context-menu");
        MenuItem gycmi = new MenuItem("Gray");
        MenuItem gncmi = new MenuItem("Green");
        MenuItem rcmi = new MenuItem("Red");
        MenuItem bcmi = new MenuItem("Blue");
        MenuItem pcmi = new MenuItem("Purple");
        contextMenu.getItems().addAll(gycmi, gncmi, rcmi, bcmi, pcmi);
        gycmi.setOnAction((e) -> { SetTheme("/res/css/main.css"); });
        gncmi.setOnAction((e) -> { SetTheme("/res/css/green.css"); });
        rcmi.setOnAction((e) -> { SetTheme("/res/css/red.css"); });
        bcmi.setOnAction((e) -> { SetTheme("/res/css/blue.css"); });
        pcmi.setOnAction((e) -> { SetTheme("/res/css/purple.css"); });
        tstBtn.setOnMousePressed((a) -> {
            contextMenu.show(tstBtn, a.getScreenX(), a.getScreenY());
        });
        tstBtn.setContextMenu(contextMenu);
        HBox tools = new HBox(lab, tofBtn, tosBtn, tstBtn);
        tools.getStyleClass().add("tools");
        
        Image ttlMinImg = new Image(getClass().getResourceAsStream("/res/minimize.png"));
        Image ttlExtImg = new Image(getClass().getResourceAsStream("/res/exit.png"));
        
        HBox title = new HBox(
                tools,
                new UIButton(ttlMinImg, 25, 25, 12, 12, () -> { stage.setIconified(true); }, "ttl-btn"),
                new UIButton(ttlExtImg, 25, 25, 12, 12, () -> { stage.close(); System.exit(0); }, "ttl-btn")
        );
        title.getStyleClass().add("ttl");
        final Point2D.Double cPos = new Point2D.Double();
        title.setOnMousePressed((event) -> {
            cPos.x = event.getX();
            cPos.y = event.getY();
        });
        title.setOnMouseDragged((event) -> {
            stage.setX(event.getScreenX() - cPos.x);
            stage.setY(event.getScreenY() - cPos.y);
        });
        mContainer.getChildren().add(title);
        Label sngTtlLbl = new Label();
        sngTtlLbl.setStyle("-fx-padding: 3px;");
        Player.setTitleLabel(sngTtlLbl);
        mContainer.getChildren().add(sngTtlLbl);
        
        
        Image playImg = new Image(getClass().getResourceAsStream("/res/play.png"));
        Image stopImg = new Image(getClass().getResourceAsStream("/res/stop.png"));
        Image prevImg = new Image(getClass().getResourceAsStream("/res/previous.png"));
        Image nextImg = new Image(getClass().getResourceAsStream("/res/next.png"));
        Image spk = new Image(getClass().getResourceAsStream("/res/speaker.png"));
        Image mut = new Image(getClass().getResourceAsStream("/res/mute.png"));
        
        UIButton plBtn = new UIButton(playImg, 60, 60, 40, 40, () -> { Player.Play(); }, "pl-btn");
        Player.setPlayButton(plBtn);
        Slider volume = new Slider(0, 1, 1);
        Player.setVolumeSlider(volume);
        UIButton volBtn = new UIButton(spk, 20, 20, 20, 20, null, "volume-button");
        volBtn.setOnClick(() -> {
            volBtn.setImage(Player.Mute() ? mut : spk, 20, 20);
        });
        volume.getStyleClass().add("controls-volume");
        HBox controls = new HBox(
            new UIButton(prevImg, 40, 40, 20, 20, () -> {
                Player.Previous();
            }, "pl-min-btn"),
            plBtn,
            new UIButton(stopImg, 40, 40, 20, 20, () -> {
                Player.Stop();
            }, "pl-min-btn"),
            new UIButton(nextImg, 40, 40, 20, 20, () -> {
                Player.Next();
            }, "pl-min-btn")
        );
        controls.getStyleClass().add("controls");
        
        Slider slider = new Slider();
        Player.setSlider(slider);
        slider.setPrefWidth(320);
        slider.getStyleClass().add("player-slider");
        Label sngTimeLbl = new Label("00:00 / 00:00");
        Player.setTimeLabel(sngTimeLbl);
        VBox player = new VBox(
            controls,
            new HBox(new Label(" ")),
            new VBox(new HBox(slider, volBtn, volume), sngTimeLbl)
        );
        player.getStyleClass().add("player");
        mContainer.getChildren().add(player);
        
        Label listOpenLbl = new Label("▶ All songs");
        ListView<String> list = new ListView<String>();
        Player.setList(list);
        VBox popList = new VBox(new HBox(listOpenLbl), list);
        popList.setOnMouseClicked(new EventHandler<MouseEvent>() {
            boolean opened = false;
            @Override
            public void handle(MouseEvent event) {
                opened = !opened;
                if (opened) {
                    listOpenLbl.setText("▼ All songs");
                    stage.setHeight(380);
                }
                else {
                    listOpenLbl.setText("▶ All songs");
                    stage.setHeight(193);
                }
            }
        });
        popList.getStyleClass().add("player-list");
        mContainer.getChildren().add(popList);
        
        stage.initStyle(javafx.stage.StageStyle.UNDECORATED);
        stage.setTitle("LAudio");
        stage.getIcons().add(new Image(getClass().getResourceAsStream("/res/icon.png")));
        stage.setResizable(false);
        stage.setScene(scene);
        stage.show();
        
        new Thread(() -> {
            int i = 0;
            while (true) {
                Player.update();
                try {
                    Thread.sleep(100);
                    i++;
                    if (i > 9) i = 0;
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
            }
        }).start();
        scene.getStylesheets().clear();
        scene.getStylesheets().add("/res/css/main.css");
        Initialize();
    }
    
    private void Initialize() {
        String configs = "";
        try {
            File cffile = new File("./configs.txt");
            if (!cffile.exists()) Files.copy(getClass().getResourceAsStream("/res/configs"), Paths.get("./configs.txt"));
            
            InputStream is = new FileInputStream(cffile);
            ByteArrayOutputStream result = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int length;
            while ((length = is.read(buffer)) != -1) {
                result.write(buffer, 0, length);
            }
            configs = result.toString("UTF-8");
        } catch (IOException ex) { }
        
        String lines[] = configs.split("\n");
        for (String line : lines) {
            if (line.startsWith("source_folder")) {
                String path = line.replaceAll("source_folder=", "");
                if (path.isEmpty() || !new File(path).exists()) continue;
                Player.LoadFiles(path);
                this.configs.put("source_folder", Player.path);
            }
            else if (line.startsWith("theme")) {
                SetTheme(line.replaceAll("theme=", ""));
            }
            else if (line.startsWith("volume")) {
                Player.volume = Float.valueOf(line.replaceAll("volume=", ""));
                Player.volSlider.setValue(Player.volume);
            }
            else if (line.startsWith("selected_track")) {
                int track = 0;
                try { track = Integer.valueOf(line.replaceAll("selected_track=", "")); }
                catch (Exception ex) {}
                Player.Set(track);
            }
        }
        if (Player.selected == null && Player.files.size() > 0) Player.Set(0);
    }

    private void SetTheme(String style) {
        this.configs.put("theme", style);
        Save();
        scene.getStylesheets().clear();
        scene.getStylesheets().add(style);
    }
    public static void Save() {
        try (PrintWriter out = new PrintWriter("./configs.txt")) {
            configs.forEach((key, val) -> {
                out.println(key + "=" + val);
            });
        }
        catch (Exception ex) { ex.printStackTrace(); }
    }
    
    public static void main(String[] args) throws InterruptedException {
        System.setOut(new PrintStream(new OutStream()));
        launch(args);
    }
}
