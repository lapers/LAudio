package laudio;

import javafx.event.ActionEvent;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

public class UIButton extends javafx.scene.control.Button {
    Runnable onClick = null;
    
    public UIButton(String text, int w, int h, Runnable onClick, String styleClass) {
        super(text);
        this.onClick = onClick;
        this.setOnAction((ActionEvent event) -> {
            if (onClick != null) onClick.run();
        });
        getStyleClass().add(styleClass);
        setMinSize(w, h);
        setMaxSize(w, h);
        setPrefSize(w, h);
    }
    
    public UIButton(Image img, int w, int h, int iw, int ih, Runnable onClick, String styleClass) {
        this.onClick = onClick;
        this.setOnAction((ActionEvent event) -> {
            if (onClick != null) onClick.run();
        });
        getStyleClass().add(styleClass);
        ImageView imgv = new ImageView(img);
        imgv.setFitWidth(iw);
        imgv.setFitHeight(ih);
        setGraphic(imgv);
        setMinSize(w, h);
        setMaxSize(w, h);
        setPrefSize(w, h);
    }
    
    public void setOnClick(Runnable onClick) {
        this.onClick = onClick;
        this.setOnAction((ActionEvent event) -> {
            if (onClick != null) onClick.run();
        });
    }
    
    public void setImage(Image img, int iw, int ih) {
        ImageView imgv = new ImageView(img);
        imgv.setFitWidth(iw);
        imgv.setFitHeight(ih);
        setGraphic(imgv);
    }
}
