package laudio;

import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

public class HBoxCell extends HBox {
    String path;
    
    Label label = new Label();
    Image unlikeImg = new Image(getClass().getResourceAsStream("/res/star.png"));
    Image likeImg = new Image(getClass().getResourceAsStream("/res/star_filled.png"));
    UIButton likeBtn;
    boolean liked = false;

    public HBoxCell(String text, String path) {
        super();
        this.path = path;
        
        getStyleClass().add("hboxcell");
        
        label.setText(text);
        HBox.setHgrow(label, Priority.ALWAYS);

        likeBtn = new UIButton(unlikeImg, 20, 20, 16, 16, () -> {
            liked = !liked;
            update();
            
            boolean contains = Main.favorites.contains(path);
            if (liked && !contains) Main.favorites.add(path);
            else if (!liked && contains) Main.favorites.remove(path);
            
            Main.UpdateFavorites();

        }, "tools-btn");

        this.getChildren().addAll(likeBtn, label);
    }
    
    public void init() {
        liked = Main.favorites.contains(path);
        update();
    }

    private void update() {
        if (liked) likeBtn.setImage(likeImg, 16, 16);
        else likeBtn.setImage(unlikeImg, 16, 16);
    }
}
