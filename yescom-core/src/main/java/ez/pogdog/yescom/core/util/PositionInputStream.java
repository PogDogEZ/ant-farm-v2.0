package ez.pogdog.yescom.core.util;

import java.io.FileInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

/**
 * InputStream that keeps track of the current reading position.
 *
 * https://stackoverflow.com/questions/240294/given-a-java-inputstream-how-can-i-determine-the-current-offset-in-the-stream
 * https://stackoverflow.com/questions/1094703/java-file-input-with-rewind-reset-capability
 */
public class PositionInputStream extends FilterInputStream {

    private final FileChannel fileChannel;
    private long mark = -1L;

    public PositionInputStream(FileInputStream inputStream) {
        super(inputStream);

        fileChannel = inputStream.getChannel();
    }

    /**
     * @return The current reading position.
     */
    public synchronized long getPosition() {
        try {
            return fileChannel.position();
        } catch (IOException error) { // ????
            return -1L;
        }
    }

    @Override
    public boolean markSupported() {
        return true;
    }

    @Override
    public synchronized void mark(int readLimit) {
        try {
            mark = fileChannel.position();
        } catch (IOException error) {
            mark = -1L;
        }
    }

    @Override
    public synchronized void reset() throws IOException {
        if (mark == -1L) throw new IOException("Not marked.");
        fileChannel.position(mark);
    }
}