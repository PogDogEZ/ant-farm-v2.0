package ez.pogdog.yescom.core.data;

import ez.pogdog.yescom.api.data.player.PlayerInfo;
import ez.pogdog.yescom.api.data.player.Session;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

/**
 * Utility class for writing certain objects common to a byte array.
 */
public final class Serial {

    /**
     * Reading from an InputStream.
     */
    public static final class Read {

        /* ------------------------------ Numeric types ------------------------------ */

        public static int readInteger(InputStream inputStream) throws IOException {
            int num = 0;
            int shift = 0;

            int read;
            do {
                read = inputStream.read();
                if (read < 0) throw new EOFException("EOF when reading integer.");
                num |= (read & 0x7f) << shift;
                shift += 7;
                if (shift >= 35) throw new IOException("Integer read overflow (35 bits).");
            } while ((read & 0x80) != 0);

            return num;
        }

        public static long readLong(InputStream inputStream) throws IOException {
            long num = 0;
            int shift = 0;

            int read;
            do {
                read = inputStream.read();
                if (read < 0) throw new EOFException("EOF when reading long.");
                num |= (long)(read & 0x7f) << shift;
                shift += 7;
                if (shift >= 70) throw new IOException("Long read overflow (70 bits).");
            } while ((read & 0x80) != 0);

            return num;
        }

        public static BigInteger readBigInteger(InputStream inputStream) throws IOException {
            BigInteger num = BigInteger.ZERO;
            int shift = 0;

            int read;
            do {
                read = inputStream.read();
                if (read < 0) throw new EOFException("EOF when reading big integer.");
                num = num.add(BigInteger.valueOf(read & 0x7f).shiftLeft(shift));
                shift += 7;
                if (shift >= 133) throw new IOException("Big integer read overflow (133 bits).");
            } while ((read & 0x80) != 0);

            return num;
        }

        public static float readFloat(InputStream inputStream) throws IOException {
            try {
                return ByteBuffer.wrap(inputStream.readNBytes(4)).getFloat();
            } catch (BufferUnderflowException error) {
                throw new IOException(error);
            }
        }

        public static double readDouble(InputStream inputStream) throws IOException {
            try {
                return ByteBuffer.wrap(inputStream.readNBytes(8)).getDouble();
            } catch (BufferUnderflowException error) {
                throw new IOException(error);
            }
        }

        /* ------------------------------ Strings and UUIDs ------------------------------ */

        public static String readString(InputStream inputStream) throws IOException {
            int length = readInteger(inputStream);
            return new String(inputStream.readNBytes(length), StandardCharsets.UTF_8);
        }

        public static UUID readUUID(InputStream inputStream) throws IOException {
            ByteBuffer buffer = ByteBuffer.wrap(inputStream.readNBytes(16));
            try {
                return new UUID(buffer.getLong(), buffer.getLong());
            } catch (BufferUnderflowException error) {
                throw new IOException(error);
            }
        }

        /* ------------------------------ YesCom data ------------------------------ */

        /**
         * Reads a {@link PlayerInfo} object partially.
         * @param inputStream The input stream to read from.
         * @return A {@link PlayerInfo} without the {@link PlayerInfo#sessions}.
         */
        public static PlayerInfo readPlayerInfo(InputStream inputStream) throws IOException {
            int lookupID = readInteger(inputStream);
            UUID uuid = readUUID(inputStream);
            long firstSeen = readLong(inputStream);
            PlayerInfo info = new PlayerInfo(lookupID, uuid, firstSeen);
            info.username = readString(inputStream);
            info.skinURL = readString(inputStream);
            return info;
        }
    }

    /**
     * Writing to an OutputStream.
     */
    public static final class Write {

        private static final BigInteger BIG_128 = BigInteger.valueOf(128);

        /* ------------------------------ Numeric types ------------------------------ */

        public static void writeInteger(int num, OutputStream outputStream) throws IOException {
            do {
                byte byteValue = (byte)(num % 0x80);
                if (num >= 0x80) byteValue |= 0x80;

                num >>= 7;
                outputStream.write(byteValue);
            } while (num > 0);
        }

        public static void writeLong(long num, OutputStream outputStream) throws IOException {
            do {
                byte byteValue = (byte)(num % 0x80);
                if (num >= 0x80) byteValue |= 0x80;

                num >>= 7;
                outputStream.write(byteValue);
            } while (num > 0);
        }

        public static void writeBigInteger(BigInteger num, OutputStream outputStream) throws IOException {
            do {
                byte byteValue = (byte)num.mod(BIG_128).intValue();
                if (num.compareTo(BIG_128) >= 0) byteValue |= 0x80;

                num = num.shiftRight(7);
                outputStream.write(byteValue);
            } while (num.compareTo(BigInteger.ZERO) > 0);
        }

        public static void writeFloat(float num, OutputStream outputStream) throws IOException {
            outputStream.write(ByteBuffer.allocate(4).putFloat(num).array());
        }

        public static void writeDouble(double num, OutputStream outputStream) throws IOException {
            outputStream.write(ByteBuffer.allocate(8).putDouble(num).array());
        }

        /* ------------------------------ Strings and UUIDs ------------------------------ */

        public static void writeString(String str, OutputStream outputStream) throws IOException {
            byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
            writeInteger(bytes.length, outputStream);
            outputStream.write(bytes);
        }

        public static void writeUUID(UUID uuid, OutputStream outputStream) throws IOException {
            outputStream.write(ByteBuffer.allocate(16)
                    .putLong(uuid.getMostSignificantBits())
                    .putLong(uuid.getLeastSignificantBits())
                    .array()
            );
        }

        /* ------------------------------ YesCom data ------------------------------ */

        /**
         * Partially writes a {@link PlayerInfo} without the session data.
         * @param info The info to write.
         * @param outputStream The output stream to write it to.
         */
        public static void writePlayerInfo(PlayerInfo info, OutputStream outputStream) throws IOException {
            writeInteger(info.lookupID, outputStream);
            writeUUID(info.uuid, outputStream);
            writeLong(info.firstSeen, outputStream);
            writeString(info.username, outputStream);
            writeString(info.skinURL, outputStream);
        }
    }
}
