package laudio.entries;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.Arrays;
import javax.sound.sampled.*;

public class FLACEntry extends laudio.PlayerEntry {
    SourceDataLine audio_out_line = null;
    FlacDecoder decoder = null;
    long[][] samples = null;
    long clipStartTime = 0;
    long millis = 0;
    
    double seekRequest = -1;
    
    @Override public void load(File file) {
        is_opened = false;
        try {
            AudioFileFormat fileFormat = AudioSystem.getAudioFileFormat(file);
            millis = (long)((Long)fileFormat.properties().get("duration"))/1000;
            decoder = new FlacDecoder(file);
            if (decoder.numSamples == 0) throw new FlacDecoder.FormatException("Unknown audio length");
            
            AudioFormat format = new AudioFormat(decoder.sampleRate, decoder.sampleDepth, decoder.numChannels, true, false);
            audio_out_line = (SourceDataLine)AudioSystem.getLine(new DataLine.Info(SourceDataLine.class, format));
            audio_out_line.open(format);
            
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
            if (status != 3 && status != 1) continue;
            double seekReq;
            synchronized(lock) {
                seekReq = seekRequest;
                seekRequest = -1;
            }
            
            try {
                if (seekReq == -1) {
                    Object[] temp = decoder.readNextBlock();
                    if (temp != null) samples = (long[][])temp[0];
                    else {
                        status = 9;
                        break;
                    }
		}
                else {
                    long samplePos = Math.round(seekReq * decoder.numSamples);
                    samples = decoder.seekAndReadBlock(samplePos);
                    audio_out_line.flush();
                    clipStartTime = audio_out_line.getMicrosecondPosition() - Math.round(samplePos * 1e6 / decoder.sampleRate);
                    if (status == 3) status = status_tmp;
		}
                
                if (samples == null) {
                    synchronized(lock) {
                        while (decoder.input == null && seekRequest == -1)
                        lock.wait();
                    }
                    continue;
		}
                
                int bytesPerSample = decoder.sampleDepth / 8;
                byte[] sampleBytes = new byte[samples[0].length * samples.length * bytesPerSample];
                for (int i = 0, k = 0; i < samples[0].length; i++) {
                    for (int ch = 0; ch < samples.length; ch++) {
                        long val = samples[ch][i];
                        for (int j = 0; j < bytesPerSample; j++, k++)
                            sampleBytes[k] = (byte)(val >>> (j << 3));
                    }
                }
                audio_out_line.write(sampleBytes, 0, sampleBytes.length);
            } catch (Exception ex) { ex.printStackTrace(); }
        }
    }
    
    @Override public void pause() {
        status = 2;
    }
    
    @Override public void stop() {
        status = -1;
        thread.stop();
        try {
            decoder.close();
            audio_out_line.close();
        } catch (Exception ex) { ex.printStackTrace(); }
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
    
    private static final class FlacDecoder {
        
        private Stream input;
        private long metadataEndPos;
        
        public int sampleRate = -1;
        public int numChannels = -1;
        public int sampleDepth = -1;
        public long numSamples = -1;
        public int constantBlockSize = -1;
        
        
        public FlacDecoder(File file) throws IOException {
            input = new Stream(file);
            if (input.readUint(32) != 0x664C6143)
                throw new FormatException("Invalid magic string");
            
            // Handle metadata blocks
            for (boolean last = false; !last; ) {
                last = input.readUint(1) != 0;
                int type = input.readUint(7);
                int length = input.readUint(24);
                if (type == 0) {  // Parse stream info block
                    int minBlockSize = input.readUint(16);
                    int maxBlockSize = input.readUint(16);
                    if (minBlockSize == maxBlockSize)
                        constantBlockSize = minBlockSize;
                    input.readUint(24);
                    input.readUint(24);
                    sampleRate = input.readUint(20);
                    numChannels = input.readUint(3) + 1;
                    sampleDepth = input.readUint(5) + 1;
                    numSamples = (long)input.readUint(18) << 18 | input.readUint(18);
                    for (int i = 0; i < 16; i++)
                        input.readUint(8);
                } else {  // Skip other blocks
                    for (int i = 0; i < length; i++)
                        input.readUint(8);
                }
            }
            if (sampleRate == -1)
                throw new FormatException("Stream info metadata block absent");
            metadataEndPos = input.getPosition();
        }
        
        
        public void close() throws IOException {
            input.close();
        }
        
        
        public long[][] seekAndReadBlock(long samplePos) throws IOException {
            // Binary search to find a frame slightly before requested position
            long startFilePos = metadataEndPos;
            long endFilePos = input.getLength();
            long curSamplePos = 0;
            while (endFilePos - startFilePos > 100000) {
                long middle = (startFilePos + endFilePos) / 2;
                long[] offsets = findNextDecodableFrame(middle);
                if (offsets == null || offsets[1] > samplePos)
                    endFilePos = middle;
                else {
                    startFilePos = offsets[0];
                    curSamplePos = offsets[1];
                }
            }
            
            input.seekTo(startFilePos);
            while (true) {
                Object[] temp = readNextBlock();
                if (temp == null)
                    return null;
                long[][] samples = (long[][])temp[0];
                int blockSize = samples[0].length;
                long nextSamplePos = curSamplePos + blockSize;
                if (nextSamplePos > samplePos) {
                    long[][] result = new long[samples.length][];
                    for (int ch = 0; ch < numChannels; ch++)
                        result[ch] = Arrays.copyOfRange(samples[ch], (int)(samplePos - curSamplePos), blockSize);
                    return result;
                }
                curSamplePos = nextSamplePos;
            }
        }
        
        
        // Returns (filePosition, sampleOffset) or null.
        private long[] findNextDecodableFrame(long filePos) throws IOException {
            while (true) {
                input.seekTo(filePos);
                int state = 0;
                while (true) {
                    int b = input.readByte();
                    if (b == -1)
                        return null;
                    else if (b == 0xFF)
                        state = 1;
                    else if (state == 1 && (b & 0xFE) == 0xF8)
                        break;
                    else
                        state = 0;
                }
                filePos = input.getPosition() - 2;
                input.seekTo(filePos);
                try {
                    Object[] temp = readNextBlock();
                    if (temp == null)
                        return null;
                    else
                        return new long[]{filePos, (long)temp[1]};
                } catch (FormatException e) {
                    filePos += 2;
                }
            }
        }
        
        
        // Returns (long[][] blockSamples, long sampleOffsetAtStartOfBlock)
        // if a block is decoded, or null if the end of stream is reached.
        public Object[] readNextBlock() throws IOException {
            // Find next sync code
            int byteVal = input.readByte();
            if (byteVal == -1)
                return null;
            int sync = byteVal << 6 | input.readUint(6);
            if (sync != 0x3FFE)
                throw new FormatException("Sync code expected");
            if (input.readUint(1) != 0)
                throw new FormatException("Reserved bit");
            int blockStrategy = input.readUint(1);
            
            // Read numerous header fields, and ignore some of them
            int blockSizeCode = input.readUint(4);
            int sampleRateCode = input.readUint(4);
            int chanAsgn = input.readUint(4);
            switch (input.readUint(3)) {
                case 1:  if (sampleDepth !=  8) throw new FormatException("Sample depth mismatch");  break;
                case 2:  if (sampleDepth != 12) throw new FormatException("Sample depth mismatch");  break;
                case 4:  if (sampleDepth != 16) throw new FormatException("Sample depth mismatch");  break;
                case 5:  if (sampleDepth != 20) throw new FormatException("Sample depth mismatch");  break;
                case 6:  if (sampleDepth != 24) throw new FormatException("Sample depth mismatch");  break;
                default:  throw new FormatException("Reserved/invalid sample depth");
            }
            if (input.readUint(1) != 0)
                throw new FormatException("Reserved bit");
            
            byteVal = input.readUint(8);
            long rawPosition;
            if (byteVal < 0x80)
                rawPosition = byteVal;
            else {
                int rawPosNumBytes = Integer.numberOfLeadingZeros(~(byteVal << 24)) - 1;
                rawPosition = byteVal & (0x3F >>> rawPosNumBytes);
                for (int i = 0; i < rawPosNumBytes; i++)
                    rawPosition = (rawPosition << 6) | (input.readUint(8) & 0x3F);
            }
            
            int blockSize;
            if (blockSizeCode == 1)
                blockSize = 192;
            else if (2 <= blockSizeCode && blockSizeCode <= 5)
                blockSize = 576 << (blockSizeCode - 2);
            else if (blockSizeCode == 6)
                blockSize = input.readUint(8) + 1;
            else if (blockSizeCode == 7)
                blockSize = input.readUint(16) + 1;
            else if (8 <= blockSizeCode && blockSizeCode <= 15)
                blockSize = 256 << (blockSizeCode - 8);
            else
                throw new FormatException("Reserved block size");
            
            if (sampleRateCode == 12)
                input.readUint(8);
            else if (sampleRateCode == 13 || sampleRateCode == 14)
                input.readUint(16);
            
            input.readUint(8);
            
            // Decode each channel's subframe, then skip footer
            long[][] samples = decodeSubframes(blockSize, sampleDepth, chanAsgn);
            input.alignToByte();
            input.readUint(16);
            return new Object[]{samples, rawPosition * (blockStrategy == 0 ? constantBlockSize : 1)};
        }
        
        
        private long[][] decodeSubframes(int blockSize, int sampleDepth, int chanAsgn) throws IOException {
            long[][] result;
            if (0 <= chanAsgn && chanAsgn <= 7) {
                result = new long[chanAsgn + 1][blockSize];
                for (int ch = 0; ch < result.length; ch++)
                    decodeSubframe(sampleDepth, result[ch]);
            } else if (8 <= chanAsgn && chanAsgn <= 10) {
                result = new long[2][blockSize];
                decodeSubframe(sampleDepth + (chanAsgn == 9 ? 1 : 0), result[0]);
                decodeSubframe(sampleDepth + (chanAsgn == 9 ? 0 : 1), result[1]);
                if (chanAsgn == 8) {
                    for (int i = 0; i < blockSize; i++)
                        result[1][i] = result[0][i] - result[1][i];
                } else if (chanAsgn == 9) {
                    for (int i = 0; i < blockSize; i++)
                        result[0][i] += result[1][i];
                } else if (chanAsgn == 10) {
                    for (int i = 0; i < blockSize; i++) {
                        long side = result[1][i];
                        long right = result[0][i] - (side >> 1);
                        result[1][i] = right;
                        result[0][i] = right + side;
                    }
                }
            } else
                throw new FormatException("Reserved channel assignment");
            return result;
        }
        
        
        private void decodeSubframe(int sampleDepth, long[] result) throws IOException {
            if (input.readUint(1) != 0)
                throw new FormatException("Invalid padding bit");
            int type = input.readUint(6);
            int shift = input.readUint(1);
            if (shift == 1) {
                while (input.readUint(1) == 0)
                    shift++;
            }
            sampleDepth -= shift;
            
            if (type == 0)  // Constant coding
                Arrays.fill(result, 0, result.length, input.readSignedInt(sampleDepth));
            else if (type == 1) {  // Verbatim coding
                for (int i = 0; i < result.length; i++)
                    result[i] = input.readSignedInt(sampleDepth);
            } else if (8 <= type && type <= 12 || 32 <= type && type <= 63) {
                int predOrder;
                int[] lpcCoefs;
                int lpcShift;
                if (type <= 12) {  // Fixed prediction
                    predOrder = type - 8;
                    for (int i = 0; i < predOrder; i++)
                        result[i] = input.readSignedInt(sampleDepth);
                    lpcCoefs = FIXED_PREDICTION_COEFFICIENTS[predOrder];
                    lpcShift = 0;
                } else {  // Linear predictive coding
                    predOrder = type - 31;
                    for (int i = 0; i < predOrder; i++)
                        result[i] = input.readSignedInt(sampleDepth);
                    int precision = input.readUint(4) + 1;
                    lpcShift = input.readSignedInt(5);
                    lpcCoefs = new int[predOrder];
                    for (int i = 0; i < predOrder; i++)
                        lpcCoefs[i] = input.readSignedInt(precision);
                }
                decodeRiceResiduals(predOrder, result);
                for (int i = predOrder; i < result.length; i++) {  // LPC restoration
                    long sum = 0;
                    for (int j = 0; j < lpcCoefs.length; j++)
                        sum += result[i - 1 - j] * lpcCoefs[j];
                    result[i] += sum >> lpcShift;
                }
            } else
                throw new FormatException("Reserved subframe type");
            
            for (int i = 0; i < result.length; i++)
                result[i] <<= shift;
        }
        
        
        private void decodeRiceResiduals(int warmup, long[] result) throws IOException {
            int method = input.readUint(2);
            if (method >= 2)
                throw new FormatException("Reserved residual coding method");
            int paramBits = method == 0 ? 4 : 5;
            int escapeParam = method == 0 ? 0xF : 0x1F;
            int partitionOrder = input.readUint(4);
            int numPartitions = 1 << partitionOrder;
            if (result.length % numPartitions != 0)
                throw new FormatException("Block size not divisible by number of Rice partitions");
            int partitionSize = result.length / numPartitions;
            
            for (int i = 0; i < numPartitions; i++) {
                int start = i * partitionSize + (i == 0 ? warmup : 0);
                int end = (i + 1) * partitionSize;
                int param = input.readUint(paramBits);
                if (param < escapeParam) {
                    for (int j = start; j < end; j++) {  // Read Rice signed integers
                        long val = 0;
                        while (input.readUint(1) == 0)
                            val++;
                        val = (val << param) | input.readUint(param);
                        result[j] = (val >>> 1) ^ -(val & 1);
                    }
                } else {
                    int numBits = input.readUint(5);
                    for (int j = start; j < end; j++)
                        result[j] = input.readSignedInt(numBits);
                }
            }
        }
        
        
        private static final int[][] FIXED_PREDICTION_COEFFICIENTS = {
            {},
            {1},
            {2, -1},
            {3, -3, 1},
            {4, -6, 4, -1},
        };
        
        
        
        // Provides low-level bit/byte reading of a file.
        private static final class Stream {
            
            private RandomAccessFile raf;
            private long bytePosition;
            private InputStream byteBuffer;
            private long bitBuffer;
            private int bitBufferLen;
            
            public Stream(File file) throws IOException {
                raf = new RandomAccessFile(file, "r");
                seekTo(0);
            }
            
            
            public void close() throws IOException {
                raf.close();
            }
            
            public long getLength() throws IOException {
                return raf.length();
            }
            
            public long getPosition() {
                return bytePosition;
            }
            
            public void seekTo(long pos) throws IOException {
                raf.seek(pos);
                bytePosition = pos;
                byteBuffer = new BufferedInputStream(new InputStream() {
                    public int read() throws IOException {
                        return raf.read();
                    }
                    public int read(byte[] b, int off, int len) throws IOException {
                        return raf.read(b, off, len);
                    }
                });
                bitBufferLen = 0;
            }
            
            public int readByte() throws IOException {
                if (bitBufferLen >= 8)
                    return readUint(8);
                else {
                    int result = byteBuffer.read();
                    if (result != -1)
                        bytePosition++;
                    return result;
                }
            }
            
            public int readUint(int n) throws IOException {
                while (bitBufferLen < n) {
                    int temp = byteBuffer.read();
                    if (temp == -1)
                        throw new EOFException();
                    bytePosition++;
                    bitBuffer = (bitBuffer << 8) | temp;
                    bitBufferLen += 8;
                }
                bitBufferLen -= n;
                int result = (int)(bitBuffer >>> bitBufferLen);
                if (n < 32)
                    result &= (1 << n) - 1;
                return result;
            }
            
            public int readSignedInt(int n) throws IOException {
                return (readUint(n) << (32 - n)) >> (32 - n);
            }
            
            public void alignToByte() {
                bitBufferLen -= bitBufferLen % 8;
            }
            
        }
        
        
        
        // Thrown when non-conforming FLAC data is read.
        @SuppressWarnings("serial")
        public static class FormatException extends IOException {
            public FormatException(String msg) {
                super(msg);
            }
        }
    }
}

