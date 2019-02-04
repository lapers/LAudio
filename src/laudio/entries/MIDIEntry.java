package laudio.entries;

import java.io.*;
import javax.sound.midi.*;

public class MIDIEntry extends laudio.PlayerEntry{
    private Sequencer sequencer;
    
    @Override public void load(File file) {
        try {
            sequencer = MidiSystem.getSequencer();
            sequencer.setSequence(MidiSystem.getSequence(file));
            sequencer.open();
        }
        catch(Exception ex) { ex.printStackTrace(); }
    }
    
    @Override public void play() {
        new Thread(() -> {
            try {
                sequencer.start();
                while(true) {
                    if(sequencer.isRunning()) {
                        try { Thread.sleep(500); }
                        catch(InterruptedException ignore) { break; }
                    } else break;
                }
                if (sequencer.isOpen()) sequencer.stop();
                sequencer.close();
            }
            catch(Exception ex) { ex.printStackTrace(); }
        }).start();
    }
    
    @Override public void pause() {
        
    }
    
    @Override public void stop() {
        sequencer.stop();
    }
    
    @Override public long getMillisLength() {
        return sequencer.getMicrosecondLength()/1000;
    }

    @Override public long getMillisPosition() {
        return sequencer.getMicrosecondPosition()/1000;
    }

    public boolean isNull() {
        return sequencer == null;
    }
    
    public boolean isOpened() {
        return sequencer.isOpen();
    }
    
    public boolean isRunning() {
        return sequencer.isRunning();
    }
    
    @Override public void goTo(double part) {
        System.err.println("Not supported for .mid file format");
    }
}
