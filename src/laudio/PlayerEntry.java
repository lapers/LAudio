package laudio;

import java.io.File;
import javax.sound.sampled.BooleanControl;
import javax.sound.sampled.FloatControl;

public abstract class PlayerEntry {
    
    protected FloatControl audio_gain;
    protected BooleanControl audio_mute;
    protected float master_volume = 1.0f;
    protected boolean muted = false;
    
    public Object lock = new Object();
    protected Thread thread = null;
    protected int status = -1;
    protected boolean is_opened = false;
    
    public abstract void load(File file);
    public abstract void play();
    public abstract void pause();
    public abstract void stop();
    public abstract long getMillisLength();
    public abstract long getMillisPosition();
    public abstract void goTo(double part);
    
    public void setVolume(float volume) {
        if (audio_gain == null) return;
        
        double d1 = audio_gain.getMinimum();
        double d2 = 0.5F * audio_gain.getMaximum() - audio_gain.getMinimum();
        double d3 = Math.log(10.0D) / 20.0D;
        double d4 = d1 + 1.0D / d3 * Math.log(1.0D + (Math.exp(d3 * d2) - 1.0D) * volume);
        
        master_volume = (float)d4;
        audio_gain.setValue(master_volume);
    }
    
    public void setMuted(boolean muted) {
        if (audio_mute == null) return;
        audio_mute.setValue(this.muted = muted);
    }
    
    public int getStatus() { return status; }
    public boolean isOpened() { return is_opened; }
}
