package laudio.entries;

import java.io.File;
import java.io.FileInputStream;
import javax.sound.sampled.*;
import org.gagravarr.ogg.OggFile;
import org.gagravarr.ogg.audio.OggAudioStatistics;
import org.gagravarr.vorbis.VorbisFile;

public class OGGEntry extends laudio.PlayerEntry {
    AudioInputStream in = null;
    AudioFormat decodedFormat = null;
    AudioInputStream din = null;
    SourceDataLine audio_out_line = null;
    
    File file;
    long bytes = 0;
    long millis = 0;
    long clipStartTime = 0;
    private int chunk_size = 16384;
    double seekRequest = -1;
    
    @Override public void load(File file) {
        is_opened = false;
        try {
            AudioFileFormat fileFormat = AudioSystem.getAudioFileFormat(file);
            
            this.file = file;
            in = AudioSystem.getAudioInputStream(file);
            
            OggFile ogg = new OggFile(new FileInputStream(file));
            VorbisFile vf = new VorbisFile(ogg);
            OggAudioStatistics stats = new OggAudioStatistics(vf, vf);
            stats.calculate();
            millis = (long)(stats.getDurationSeconds() * 1000);
            bytes = stats.getLastGranule()*4;
            
            AudioFormat baseFormat = in.getFormat();
            decodedFormat = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                baseFormat.getSampleRate(),
                16,
                baseFormat.getChannels(),
                baseFormat.getChannels() * 2,
                baseFormat.getSampleRate(),
            false);
            
            din = AudioSystem.getAudioInputStream(decodedFormat, in);
            
            audio_out_line = (SourceDataLine)AudioSystem.getLine(new DataLine.Info(SourceDataLine.class, decodedFormat));
            audio_out_line.open(decodedFormat);
            
            audio_gain = ((FloatControl)audio_out_line.getControl(FloatControl.Type.MASTER_GAIN));
            audio_gain.setValue(master_volume);
            audio_mute = ((BooleanControl)audio_out_line.getControl(BooleanControl.Type.MUTE));
            audio_mute.setValue(muted);
            audio_out_line.start();
            
            status = 0;
            thread = new Thread(() -> { update(); });
            thread.start();
        } catch(Exception e) { e.printStackTrace(); status = -1; }
        is_opened = true;
    }
    
    @Override public void play() {
        if (is_opened) status = 1;
    }
    
    void update() {
        while (status != -1) {
            try { Thread.sleep(1); } catch (Exception ex) {}
            if (status != 3 && status != 1) continue;
            
            double seekReq;
            synchronized(lock) {
                seekReq = seekRequest;
                seekRequest = -1;
            }
            
            try {                
                if (seekReq != -1) {
                    long bytes_to_skip = Math.round(bytes * seekReq);
                    in = AudioSystem.getAudioInputStream(file);
                    din = AudioSystem.getAudioInputStream(decodedFormat, in);
                    din.skip(bytes_to_skip);
                    audio_out_line.flush();
                    clipStartTime = audio_out_line.getMicrosecondPosition() - Math.round(bytes_to_skip * 1e6 / (44100*4));
                    if (status == 3) status = status_tmp;
                }
                
                byte[] data = new byte[chunk_size];
            
                int bytes_read = 0;
                bytes_read = din.read(data, 0, data.length);
                if (bytes_read == -1) {
                    status = 9;
                    break;
                }
                
                audio_out_line.write(data, 0, bytes_read);
            } catch (Exception ex) { ex.printStackTrace(); }
        }
    }
    
    @Override public void pause() {
        status = 2;
        audio_out_line.flush();
    }
    
    @Override public void stop() {
        status = -1;
        thread.stop();
        
        try {
            din.close();
            in.close();
            audio_out_line.close();
        } catch(Exception e) { e.printStackTrace(); }
        
        is_opened = false;
    }
    
    @Override public long getMillisLength() {
        return millis;
    }
    
    @Override public long getMillisPosition() {
        return (audio_out_line != null ? (audio_out_line.getMicrosecondPosition() - clipStartTime)/1000 : 1);
    }
    
    int status_tmp = 0;
    @Override public void goTo(double part) {
        synchronized(lock) {
            if (status != 3) {
                status_tmp = status;
                status = 3;
            }
            seekRequest = part;
            lock.notify();
        }
    }
}

