package laudio;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Slider;
import javafx.scene.image.Image;
import laudio.entries.*;

class Player {
    private static boolean isMuted = false;
    private static Label titleLbl = null;
    private static Label timeLbl = null;
    private static UIButton playBtn = null;
    private static Slider slider = null;
    public static Slider volSlider = null;
    private static ListView<HBoxCell> list = null;
    public static File selected = null;
    public  static String path = ".";
    public static ArrayList<File> files = new ArrayList<>();
    
    private static Image pauseImg = new Image(Player.class.getResourceAsStream("/res/pause.png"));
    private static Image playImg = new Image(Player.class.getResourceAsStream("/res/play.png"));
    
    public static PlayerEntry entry = null;
    public static float volume = 1.0f;
        
    private final static int recursion = 3;
    
    static void ListFiles(File... files) {
        int id = 1;
        for (File file : files) {
            list.getItems().add(new HBoxCell((id++) + ". " + file.getName(), file.getAbsolutePath()));
        }
        list.getItems().forEach((cell) -> { cell.init(); });
    }
    
    static void LoadFile(File file) {
        Player.path = file.getParent();
        list.getItems().clear();
        Player.files.clear();
        ListFiles(file);
        Player.files.add(file);
        Set(0);
    }
    
    public static void LoadFiles(String path) {
        if (entry != null) entry.stop();
        Player.path = path;
        list.getItems().clear();
        Player.files.clear();
        
        File files[] = getFiles(path, recursion);
        if (files == null || files.length <= 0) return;
        Arrays.sort(files, (File a, File b) -> a.getName().toLowerCase().compareTo(b.getName().toLowerCase()));
        
        ListFiles(files);
        for (File file : files) {
            Player.files.add(file);
        }
    }
    
    private static File[] getFiles(String dir, int recursion) {
        ArrayList<File> flist = new ArrayList<>();
        flist.addAll(Arrays.asList(new File(dir).listFiles((pathname) -> {
            String formats[] = {
                ".mp3", ".ogg", ".wav", ".aac", ".flac"
            };
            File cur_file = new File(pathname.getAbsolutePath());
            if (cur_file.isDirectory() && recursion > 0) {
                File files[] = getFiles(cur_file.getAbsolutePath(), recursion);
                flist.addAll(Arrays.asList(files));
            }
            else {
                for (String format : formats) {
                    String name = pathname.getName();
                    if (name.endsWith(format) || name.endsWith(format.toUpperCase())) return true;
                }
            }
            return false;   
        })));
        return flist.toArray(new File[flist.size()]);
    }
    
    public static void LoadFovorites() {
        if (entry != null) entry.stop();
        list.getItems().clear();
        Player.files.clear();
        
        for (String favor : Main.favorites) {
            File fav_file = new File(favor);
            if (fav_file.exists()) files.add(fav_file);
        }
        ListFiles(files.toArray(new File[files.size()]));

        Set(0);
        Play();
    }
    
    public static void Shuffle() {
        if (Player.files.size() < 1) return;
        Collections.shuffle(Player.files);
        
        list.getItems().clear();
        ListFiles(files.toArray(new File[files.size()]));
        
        Set(0);
        Play();
    }
    
    public static boolean Set(int id) {
        if (files.size() < 1) return false;
        if (id < 0) id = files.size()-1;
        else if (id > files.size()-1) id = 0;
        
        selected = files.get(id);
        String ext = selected.getName().toLowerCase();
        
        if (entry != null) entry.stop();
        entry = (ext.endsWith(".aac")  ? new AACEntry()  :
                (ext.endsWith(".flac") ? new FLACEntry() :
                (ext.endsWith(".mp3")  ? new MP3Entry()  : 
                (ext.endsWith(".ogg")  ? new OGGEntry()  : 
                (ext.endsWith(".wav")  ? new WAVEntry()  : 
                null
        )))));
        
        if (entry == null) return false;
        
        entry.load(selected);
        if (entry instanceof FLACEntry) entry.stop();
        entry.setVolume(volume);
        
        final int id_d = id;
        if (!(id_d >= files.size() || id_d < 0)) {
            Platform.runLater(() -> {
                titleLbl.setText(files.get(id_d).getName());
                timeLbl.setText(getTime());
                playBtn.setImage(playImg, 40, 40);
            });
        }
        
        Main.configs.put("selected_track", Integer.toString(id));
        Main.Save();
        if (!(id_d >= list.getItems().size() || id_d < 0)) {
            Platform.runLater(() -> {
                list.scrollTo(id_d);
                list.getFocusModel().focus(id_d);
                slider.setValue(0);
            });
        }
        return true;
    }
    
    public static void Play() {
        if (entry == null) return;
        if (selected == null) { Set(0); }
        if (!entry.isOpened()) entry.load(selected);
       
        if (entry.getStatus() != 1 && entry.getStatus() != 3) {
            entry.play();
            Platform.runLater(() -> {
                playBtn.setImage(pauseImg, 40, 40);
            });
        }
        else {
            entry.pause();
            Platform.runLater(() -> {
                playBtn.setImage(playImg, 40, 40);
            });
        }
        Platform.runLater(() -> {
            playBtn.setImage(entry.getStatus() == 1 || entry.getStatus() == 3 ? pauseImg : playImg, 40, 40);
        });
    }
    
    public static void ForcePlay() {
        if (entry == null) return;
        if (selected == null) { Set(0); }
        if (!entry.isOpened()) entry.load(selected);
       
        if (entry.getStatus() != 1 && entry.getStatus() != 3) {
            entry.play();
            Platform.runLater(() -> {
                playBtn.setImage(pauseImg, 40, 40);
            });
        }
        Platform.runLater(() -> {
            playBtn.setImage(entry.getStatus() == 1 || entry.getStatus() == 3 ? pauseImg : playImg, 40, 40);
        });
    }
    
    public static void Stop() {
        Platform.runLater(() -> {
            playBtn.setImage(playImg, 40, 40);
            slider.setValue(0);
            timeLbl.setText("00:00 / 00:00");
        });
        if (entry != null) entry.stop();
    }
    
    public static void Next() {
        Set(files.indexOf(selected)+1);
        ForcePlay();
        Platform.runLater(() -> {
            playBtn.setImage(pauseImg, 40, 40);
        });
    }
    
    public static void Previous() {
        Set(files.indexOf(selected)-1);
        ForcePlay();
        Platform.runLater(() -> {
            playBtn.setImage(pauseImg, 40, 40);
        });
    }
    
    public static void setPlayButton(UIButton btn) {
        Player.playBtn = btn;
    }
    
    static boolean artific_seek = false;
    public static void setSlider(Slider slider) {
        Player.slider = slider;
        slider.setMin(0);
        slider.setMax(1);
        slider.setOnMousePressed((observable) -> { if (entry != null) entry.goTo(slider.getValue()); });
        slider.valueProperty().addListener((observable) -> {
            if (artific_seek) artific_seek = false;
            else if (entry != null) entry.goTo(slider.getValue());
        });
    }
    
    public static void setVolumeSlider(Slider slider) {
        Player.volSlider = slider;
        slider.setMin(0);
        slider.setMax(1);
        slider.valueProperty().addListener((observable, oldValue, newValue) -> {
            volume = newValue.floatValue();
            if (entry != null) entry.setVolume(volume);
            Main.configs.put("volume", Float.toString(volume));
            Main.Save();
        });
        slider.setOnScroll((event) -> {
            slider.setValue(slider.getValue() - (-0.00125 * event.getDeltaY()));
        });
    }
    
    public static void setTitleLabel(Label label) {
        Player.titleLbl = label;
    }
    
    public static void setTimeLabel(Label label) {
        Player.timeLbl = label;
    }
    
    public static void setList(ListView<HBoxCell> list) {
        Player.list = list;
        list.getSelectionModel().selectedItemProperty().addListener((ObservableValue<? extends HBoxCell> observable, HBoxCell oldValue, HBoxCell newValue) -> {
            Set(list.getItems().indexOf(newValue));
            ForcePlay();
            Platform.runLater(() -> {
                playBtn.setImage(pauseImg, 40, 40);
            });
        });
    }
    
    public static void update() {
        Platform.runLater(() -> {
            if (entry != null) timeLbl.setText(getTime());
        });
        
        try {
            Platform.runLater(() -> {
                if (entry != null) {
                    artific_seek = true;
                    if (entry.getStatus() == 1) slider.setValue((double)entry.getMillisPosition()/(double)entry.getMillisLength());
                }
            });
            
            if (entry != null && entry.getStatus() == 9) {
                Thread.sleep(500);
                Next();
            }
        } catch (Exception ex) {}
       }

    static boolean Mute() {
        isMuted = !isMuted;
        
        if (entry != null) entry.setMuted(isMuted);
        
        return isMuted;
    }

    private static String getTime() {
        long millis = (entry.getStatus() != -1 ? entry.getMillisPosition() : 0);
        long minutes = millis/60000;
        long seconds = (millis/1000)%60;
        String s1 = (minutes < 10 ? "0"+minutes : minutes) + ":" + (seconds < 10 ? "0"+seconds : seconds);
        millis = entry.getMillisLength();
        minutes = millis/60000;
        seconds = (millis/1000)%60;
        String s2 = (minutes < 10 ? "0"+minutes : minutes) + ":" + (seconds < 10 ? "0"+seconds : seconds);
        return s1 + " / " + s2;
    }
}
