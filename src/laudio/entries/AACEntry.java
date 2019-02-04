package laudio.entries;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import net.sourceforge.jaad.aac.Decoder;
import net.sourceforge.jaad.aac.SampleBuffer;
import net.sourceforge.jaad.adts.ADTSDemultiplexer;
import net.sourceforge.jaad.util.wav.WaveFileWriter;

public class AACEntry extends WAVEntry {
    File tmp_file = null;
        
    @Override public void load(File file) {
        WaveFileWriter wav = null;
        try {
            tmp_file = File.createTempFile("laudio.aac.wav", ".tmp");
            tmp_file.deleteOnExit();

            final ADTSDemultiplexer adts = new ADTSDemultiplexer(new FileInputStream(file));
            final Decoder decoder = new Decoder(adts.getDecoderSpecificInfo());

            final SampleBuffer buffer = new SampleBuffer();
            
            byte[] data;
            while(true) {
                data = adts.readNextFrame();
                if (data.length <= 0) break;
                decoder.decodeFrame(data, buffer);

                if (wav == null) wav = new WaveFileWriter(tmp_file, buffer.getSampleRate(), buffer.getChannels(), buffer.getBitsPerSample());
                wav.write(buffer.getData());
            }
        } catch(Exception e)  { }
        finally { if(wav!=null) try { wav.close(); if (tmp_file != null) super.load(tmp_file); } catch (IOException ex) {  } }
    }
    
    @Override public void play() {
        super.play();
    }
    
    @Override public void pause() {
        super.pause();
    }
    
    @Override public void stop() {
        super.stop();
        tmp_file.delete();
    }
    
    @Override public long getMillisLength() {
        return super.getMillisLength();
    }
    
    @Override public long getMillisPosition() {
        return super.getMillisPosition();
    }
    
    @Override public void goTo(double part) {
        super.goTo(part);
    }
}

