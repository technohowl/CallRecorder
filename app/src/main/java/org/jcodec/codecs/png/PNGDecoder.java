package org.jcodec.codecs.png;

import static org.jcodec.common.tools.MathUtil.abs;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import org.jcodec.common.VideoCodecMeta;
import org.jcodec.common.VideoDecoder;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.logging.Logger;
import org.jcodec.common.model.ColorSpace;
import org.jcodec.common.model.Picture8Bit;
import org.jcodec.common.model.Size;

/**
 * This class is part of JCodec ( www.jcodec.org ) This software is distributed
 * under FreeBSD License
 * 
 * PNG image decoder.
 * 
 * Supports: RGB, palette, grey, alpha, interlace, transparency.
 * 
 * @author Stanislav Vitvitskyy
 * 
 */
public class PNGDecoder extends VideoDecoder {
    private static final long PNGSIG = 0x89504e470d0a1a0aL;
    private static final long MNGSIG = 0x8a4d4e470d0a1a0aL;
    private static final int TAG_IHDR = 0x49484452;
    private static final int TAG_IDAT = 0x49444154;
    private static final int TAG_PLTE = 0x504c5445;
    private static final int TAG_tRNS = 0x74524e53;
    private static final int TAG_IEND = 0x49454e44;

    private static final int FILTER_TYPE_LOCO = 64;
    private static final int FILTER_VALUE_NONE = 0;
    private static final int FILTER_VALUE_SUB = 1;
    private static final int FILTER_VALUE_UP = 2;
    private static final int FILTER_VALUE_AVG = 3;
    private static final int FILTER_VALUE_PAETH = 4;

    private static final int PNG_COLOR_MASK_PALETTE = 1;
    private static final int PNG_COLOR_MASK_COLOR = 2;
    private static final int PNG_COLOR_MASK_ALPHA = 4;

    private static final int PNG_COLOR_TYPE_GRAY = 0;
    private static final int PNG_COLOR_TYPE_PALETTE = (PNG_COLOR_MASK_COLOR | PNG_COLOR_MASK_PALETTE);
    private static final int PNG_COLOR_TYPE_RGB = (PNG_COLOR_MASK_COLOR);

    private static final int alphaR = 0x7f;
    private static final int alphaG = 0x7f;
    private static final int alphaB = 0x7f;

    public static final int[] logPassStep = { 3, 3, 2, 2, 1, 1, 0 };
    public static final int[] logPassRowStep = { 3, 3, 3, 2, 2, 1, 1 };
    public static final int[] passOff = { 0, 4, 0, 2, 0, 1, 0 };
    public static final int[] passRowOff = { 0, 0, 4, 0, 2, 0, 1 };
    
    private byte[] ca = new byte[4];

    @Override
    public Picture8Bit decodeFrame8Bit(ByteBuffer data, byte[][] buffer) {
        long sig = data.getLong();
        if (sig != PNGSIG && sig != MNGSIG)
            throw new RuntimeException("Not a PNG file.");

        IHDR ihdr = null;
        PLTE plte = null;
        TRNS trns = null;
        List<ByteBuffer> list = new ArrayList<ByteBuffer>();
        while (data.remaining() >= 8) {
            int length = data.getInt();
            int tag = data.getInt();

            if (data.remaining() < length)
                break;

            switch (tag) {
            case TAG_IHDR:
                ihdr = new IHDR();
                ihdr.parse(data);
                break;
            case TAG_PLTE:
                plte = new PLTE();
                plte.parse(data, length);
                break;
            case TAG_tRNS:
                trns = new TRNS(ihdr.colorType);
                trns.parse(data, length);
                break;
            case TAG_IDAT:
                list.add(NIOUtils.read(data, length));
                NIOUtils.skip(data, 4); // CRC
                break;
            case TAG_IEND:
                NIOUtils.skip(data, 4); // CRC
                break;
            default:
                data.position(data.position() + length + 4);
            }
        }
        try {
            decodeData(ihdr, plte, trns, list, buffer);
        } catch (DataFormatException e) {
            return null;
        }
        return Picture8Bit.createPicture8Bit(ihdr.width, ihdr.height, buffer, ihdr.colorSpace());
    }

    private void decodeData(IHDR ihdr, PLTE plte, TRNS trns, List<ByteBuffer> list, byte[][] buffer)
            throws DataFormatException {
        int bpp = (ihdr.getBitsPerPixel() + 7) >> 3;
        int passes = ihdr.interlaceType == 0 ? 1 : 7;
        Inflater inflater = new Inflater();
        Iterator<ByteBuffer> it = list.iterator();

        for (int pass = 0; pass < passes; pass++) {
            int rowSize, rowStart, rowStep, colStart, colStep;
            if (ihdr.interlaceType == 0) {
                rowSize = ihdr.rowSize() + 1;
                colStart = rowStart = 0;
                colStep = rowStep = 1;
            } else {
                int round = (1 << logPassStep[pass]) - 1;
                rowSize = ((ihdr.width + round) >> logPassStep[pass]) + 1;
                rowStart = passRowOff[pass];
                rowStep = 1 << logPassRowStep[pass];
                colStart = passOff[pass];
                colStep = 1 << logPassStep[pass];
            }
            byte[] lastRow = new byte[rowSize - 1];
            byte[] uncompressed = new byte[rowSize];
            int bptr = 3 * (ihdr.width * rowStart + colStart);
            for (int row = rowStart; row < ihdr.height; row += rowStep) {
                int count = inflater.inflate(uncompressed);
                if (count < uncompressed.length && inflater.needsInput()) {
                    if (!it.hasNext()) {
                        Logger.warn(String.format("Data truncation at row %d", row));
                        break;
                    }
                    ByteBuffer next = it.next();
                    inflater.setInput(NIOUtils.toArray(next));
                    int toRead = uncompressed.length - count;
                    count = inflater.inflate(uncompressed, count, toRead);
                    if (count != toRead) {
                        Logger.warn(String.format("Data truncation at row %d", row));
                        break;
                    }
                }

                int filter = uncompressed[0];
                switch (filter) {
                case FILTER_VALUE_NONE:
                    for (int i = 0; i < rowSize - 1; i++) {
                        lastRow[i] = uncompressed[i + 1];
                    }
                    break;
                case FILTER_VALUE_SUB:
                    filterSub(uncompressed, rowSize - 1, lastRow, bpp);
                    break;
                case FILTER_VALUE_UP:
                    filterUp(uncompressed, rowSize - 1, lastRow);
                    break;
                case FILTER_VALUE_AVG:
                    filterAvg(uncompressed, rowSize - 1, lastRow, bpp);
                    break;
                case FILTER_VALUE_PAETH:
                    filterPaeth(uncompressed, rowSize - 1, lastRow, bpp);
                    break;
                }

                int bptrWas = bptr;
                if ((ihdr.colorType & PNG_COLOR_MASK_PALETTE) != 0) {
                    for (int i = 0; i < rowSize - 1; i += bpp, bptr += 3 * colStep) {
                        int plt = plte.palette[lastRow[i] & 0xff];
                        buffer[0][bptr] = (byte) (((plt >> 16) & 0xff) - 128);
                        buffer[0][bptr + 1] = (byte) (((plt >> 8) & 0xff) - 128);
                        buffer[0][bptr + 2] = (byte) ((plt & 0xff) - 128);
                    }
                } else if ((ihdr.colorType & PNG_COLOR_MASK_COLOR) != 0) {
                    for (int i = 0; i < rowSize - 1; i += bpp, bptr += 3 * colStep) {
                        buffer[0][bptr] = (byte) ((lastRow[i] & 0xff) - 128);
                        buffer[0][bptr + 1] = (byte) ((lastRow[i + 1] & 0xff) - 128);
                        buffer[0][bptr + 2] = (byte) ((lastRow[i + 2] & 0xff) - 128);
                    }
                } else {
                    for (int i = 0; i < rowSize - 1; i += bpp, bptr += 3 * colStep) {
                        buffer[0][bptr] = buffer[0][bptr
                                + 1] = buffer[0][bptr + 2] = (byte) ((lastRow[i] & 0xff) - 128);
                    }
                }
                if ((ihdr.colorType & PNG_COLOR_MASK_ALPHA) != 0) {
                    for (int i = bpp - 1, j = bptrWas; i < rowSize - 1; i += bpp, j += 3 * colStep) {
                        int alpha = lastRow[i] & 0xff, nalpha = 256 - alpha;
                        buffer[0][j] = (byte) ((alphaR * nalpha + buffer[0][j] * alpha) >> 8);
                        buffer[0][j + 1] = (byte) ((alphaG * nalpha + buffer[0][j + 1] * alpha) >> 8);
                        buffer[0][j + 2] = (byte) ((alphaB * nalpha + buffer[0][j + 2] * alpha) >> 8);
                    }
                } else if (trns != null) {
                    if (ihdr.colorType == PNG_COLOR_TYPE_PALETTE) {
                        for (int i = 0, j = bptrWas; i < rowSize - 1; i++, j += 3 * colStep) {
                            int alpha = trns.alphaPal[lastRow[i] & 0xff] & 0xff, nalpha = 256 - alpha;
                            buffer[0][j] = (byte) ((alphaR * nalpha + buffer[0][j] * alpha) >> 8);
                            buffer[0][j + 1] = (byte) ((alphaG * nalpha + buffer[0][j + 1] * alpha) >> 8);
                            buffer[0][j + 2] = (byte) ((alphaB * nalpha + buffer[0][j + 2] * alpha) >> 8);
                        }
                    } else if (ihdr.colorType == PNG_COLOR_TYPE_RGB) {
                        int ar = (trns.alphaR & 0xff) - 128;
                        int ag = (trns.alphaG & 0xff) - 128;
                        int ab = (trns.alphaB & 0xff) - 128;
                        if (ab != alphaB || ag != alphaG || ar != alphaR) {
                            for (int i = 0, j = bptrWas; i < rowSize - 1; i += bpp, j += 3 * colStep) {
                                if (buffer[0][j] == ar && buffer[0][j + 1] == ag && buffer[0][j + 2] == ab) {
                                    buffer[0][j] = alphaR;
                                    buffer[0][j + 1] = alphaG;
                                    buffer[0][j + 2] = alphaB;
                                }
                            }
                        }
                    } else if (ihdr.colorType == PNG_COLOR_TYPE_GRAY) {
                        for (int i = 0, j = bptrWas; i < rowSize - 1; i++, j += 3 * colStep) {
                            if (lastRow[i] == trns.alphaGrey) {
                                buffer[0][j] = alphaR;
                                buffer[0][j + 1] = alphaG;
                                buffer[0][j + 2] = alphaB;
                            }
                        }
                    }
                }
                bptr = bptrWas + (3 * ihdr.width * rowStep);
            }
        }
    }

    private void filterPaeth(byte[] uncompressed, int rowSize, byte[] lastRow, int bpp) {
        for (int i = 0; i < bpp; i++) {
            ca[i] = lastRow[i];
            lastRow[i] = (byte) ((uncompressed[i + 1] & 0xff) + (lastRow[i] & 0xff));
        }
        for (int i = bpp; i < rowSize; i++) {
            int a = lastRow[i - bpp] & 0xff;
            int b = lastRow[i] & 0xff;
            int c = ca[i % bpp] & 0xff;
            int p = b - c;
            int pc = a - c;

            int pa = abs(p);
            int pb = abs(pc);
            pc = abs(p + pc);

            if (pa <= pb && pa <= pc)
                p = a;
            else if (pb <= pc)
                p = b;
            else
                p = c;
            ca[i % bpp] = lastRow[i];
            lastRow[i] = (byte) (p + (uncompressed[i + 1] & 0xff));
        }
    }

    private void filterSub(byte[] uncompressed, int rowSize, byte[] lastRow, int bpp) {
        switch (bpp) {
        case 1:
            filterSub1(uncompressed, lastRow, rowSize);
            break;
        case 2:
            filterSub2(uncompressed, lastRow, rowSize);
            break;
        case 3:
            filterSub3(uncompressed, lastRow, rowSize);
            break;
        default:
            filterSub4(uncompressed, lastRow, rowSize);
        }
    }

    private void filterAvg(byte[] uncompressed, int rowSize, byte[] lastRow, int bpp) {
        switch (bpp) {
        case 1:
            filterAvg1(uncompressed, lastRow, rowSize);
            break;
        case 2:
            filterAvg2(uncompressed, lastRow, rowSize);
            break;
        case 3:
            filterAvg3(uncompressed, lastRow, rowSize);
            break;
        default:
            filterAvg4(uncompressed, lastRow, rowSize);
        }

    }

    private void filterSub1(byte[] uncompressed, byte[] lastRow, int rowSize) {
        byte p = lastRow[0] = uncompressed[1];
        for (int i = 1; i < rowSize; i++) {
            p = lastRow[i] = (byte) ((p & 0xff) + (uncompressed[i + 1] & 0xff));
        }
    }

    private void filterUp(byte[] uncompressed, int rowSize, byte[] lastRow) {
        for (int i = 0; i < rowSize; i++) {
            lastRow[i] = (byte) ((lastRow[i] & 0xff) + (uncompressed[i + 1] & 0xff));
        }
    }

    private void filterAvg1(byte[] uncompressed, byte[] lastRow, int rowSize) {
        byte p = lastRow[0] = (byte) ((uncompressed[1] & 0xff) + ((lastRow[0] & 0xff) >> 1));
        for (int i = 1; i < rowSize; i++) {
            p = lastRow[i] = (byte) ((((lastRow[i] & 0xff) + (p & 0xff)) >> 1) + (uncompressed[i + 1] & 0xff));
        }
    }

    private void filterSub2(byte[] uncompressed, byte[] lastRow, int rowSize) {
        byte p0 = lastRow[0] = uncompressed[1];
        byte p1 = lastRow[1] = uncompressed[2];
        for (int i = 2; i < rowSize; i += 2) {
            p0 = lastRow[i] = (byte) ((p0 & 0xff) + (uncompressed[1 + i] & 0xff));
            p1 = lastRow[i + 1] = (byte) ((p1 & 0xff) + (uncompressed[2 + i] & 0xff));
        }
    }

    private void filterAvg2(byte[] uncompressed, byte[] lastRow, int rowSize) {
        byte p0 = lastRow[0] = (byte) ((uncompressed[1] & 0xff) + ((lastRow[0] & 0xff) >> 1));
        byte p1 = lastRow[1] = (byte) ((uncompressed[2] & 0xff) + ((lastRow[1] & 0xff) >> 1));
        for (int i = 2; i < rowSize; i += 2) {
            p0 = lastRow[i] = (byte) ((((lastRow[i] & 0xff) + (p0 & 0xff)) >> 1) + (uncompressed[1 + i] & 0xff));
            p1 = lastRow[i
                    + 1] = (byte) ((((lastRow[i + 1] & 0xff) + (p1 & 0xff)) >> 1) + (uncompressed[i + 2] & 0xff));
        }
    }

    private void filterSub3(byte[] uncompressed, byte[] lastRow, int rowSize) {
        byte p0 = lastRow[0] = uncompressed[1];
        byte p1 = lastRow[1] = uncompressed[2];
        byte p2 = lastRow[2] = uncompressed[3];
        for (int i = 3; i < rowSize; i += 3) {
            p0 = lastRow[i] = (byte) ((p0 & 0xff) + (uncompressed[i + 1] & 0xff));
            p1 = lastRow[i + 1] = (byte) ((p1 & 0xff) + (uncompressed[i + 2] & 0xff));
            p2 = lastRow[i + 2] = (byte) ((p2 & 0xff) + (uncompressed[i + 3] & 0xff));
        }
    }

    private void filterAvg3(byte[] uncompressed, byte[] lastRow, int rowSize) {
        byte p0 = lastRow[0] = (byte) ((uncompressed[1] & 0xff) + ((lastRow[0] & 0xff) >> 1));
        byte p1 = lastRow[1] = (byte) ((uncompressed[2] & 0xff) + ((lastRow[1] & 0xff) >> 1));
        byte p2 = lastRow[2] = (byte) ((uncompressed[3] & 0xff) + ((lastRow[2] & 0xff) >> 1));
        for (int i = 3; i < rowSize; i += 3) {
            p0 = lastRow[i] = (byte) ((((lastRow[i] & 0xff) + (p0 & 0xff)) >> 1) + (uncompressed[i + 1] & 0xff));
            p1 = lastRow[i
                    + 1] = (byte) ((((lastRow[i + 1] & 0xff) + (p1 & 0xff)) >> 1) + (uncompressed[i + 2] & 0xff));
            p2 = lastRow[i
                    + 2] = (byte) ((((lastRow[i + 2] & 0xff) + (p2 & 0xff)) >> 1) + (uncompressed[i + 3] & 0xff));
        }
    }

    private void filterSub4(byte[] uncompressed, byte[] lastRow, int rowSize) {
        byte p0 = lastRow[0] = uncompressed[1];
        byte p1 = lastRow[1] = uncompressed[2];
        byte p2 = lastRow[2] = uncompressed[3];
        byte p3 = lastRow[3] = uncompressed[4];
        for (int i = 4; i < rowSize; i += 4) {
            p0 = lastRow[i] = (byte) ((p0 & 0xff) + (uncompressed[i + 1] & 0xff));
            p1 = lastRow[i + 1] = (byte) ((p1 & 0xff) + (uncompressed[i + 2] & 0xff));
            p2 = lastRow[i + 2] = (byte) ((p2 & 0xff) + (uncompressed[i + 3] & 0xff));
            p3 = lastRow[i + 3] = (byte) ((p3 & 0xff) + (uncompressed[i + 4] & 0xff));
        }
    }

    private void filterAvg4(byte[] uncompressed, byte[] lastRow, int rowSize) {
        byte p0 = lastRow[0] = (byte) ((uncompressed[1] & 0xff) + ((lastRow[0] & 0xff) >> 1));
        byte p1 = lastRow[1] = (byte) ((uncompressed[2] & 0xff) + ((lastRow[1] & 0xff) >> 1));
        byte p2 = lastRow[2] = (byte) ((uncompressed[3] & 0xff) + ((lastRow[2] & 0xff) >> 1));
        byte p3 = lastRow[3] = (byte) ((uncompressed[4] & 0xff) + ((lastRow[3] & 0xff) >> 1));
        for (int i = 4; i < rowSize; i += 4) {
            p0 = lastRow[i] = (byte) ((((lastRow[i] & 0xff) + (p0 & 0xff)) >> 1) + (uncompressed[i + 1] & 0xff));
            p1 = lastRow[i
                    + 1] = (byte) ((((lastRow[i + 1] & 0xff) + (p1 & 0xff)) >> 1) + (uncompressed[i + 2] & 0xff));
            p2 = lastRow[i
                    + 2] = (byte) ((((lastRow[i + 2] & 0xff) + (p2 & 0xff)) >> 1) + (uncompressed[i + 3] & 0xff));
            p3 = lastRow[i
                    + 3] = (byte) ((((lastRow[i + 3] & 0xff) + (p3 & 0xff)) >> 1) + (uncompressed[i + 4] & 0xff));
        }
    }

    private static class IHDR {
        private int width;
        private int height;
        private byte bitDepth;
        private byte colorType;
        private byte compressionType;
        private byte filterType;
        private byte interlaceType;

        public void parse(ByteBuffer data) {
            width = data.getInt();
            height = data.getInt();
            bitDepth = data.get();
            colorType = data.get();
            compressionType = data.get();
            filterType = data.get();
            interlaceType = data.get();
            data.getInt();
        }

        public int rowSize() {
            return (width * getBitsPerPixel() + 7) >> 3;
        }

        public int getNBChannels() {
            int channels;
            channels = 1;
            if ((colorType & (PNG_COLOR_MASK_COLOR | PNG_COLOR_MASK_PALETTE)) == PNG_COLOR_MASK_COLOR)
                channels = 3;
            if ((colorType & PNG_COLOR_MASK_ALPHA) != 0)
                channels++;
            return channels;
        }

        public int getBitsPerPixel() {
            return bitDepth * getNBChannels();
        }

        public ColorSpace colorSpace() {
            return ColorSpace.RGB;
        }
    }

    /**
     * Palette descriptor.
     */
    private static class PLTE {

        private int[] palette;

        public void parse(ByteBuffer data, int length) {
            if ((length % 3) != 0 || length > 256 * 3)
                throw new RuntimeException("Invalid data");
            int n = length / 3;
            palette = new int[n];
            int i = 0;
            for (i = 0; i < n; i++) {
                palette[i] = (0xff << 24) | ((data.get() & 0xff) << 16) | ((data.get() & 0xff) << 8)
                        | (data.get() & 0xff);
            }
            for (; i < 256; i++)
                palette[i] = (0xff << 24);
            data.getInt(); // crc
        }
    }

    /**
     * Transparency descriptor for paletted data
     */
    public static class TRNS {
        private int colorType;
        private byte[] alphaPal;
        private byte alphaGrey;
        private byte alphaR;
        private byte alphaG;
        private byte alphaB;

        public TRNS(byte colorType) {
            this.colorType = colorType;
        }

        public void parse(ByteBuffer data, int length) {
            if (colorType == PNG_COLOR_TYPE_PALETTE) {
                alphaPal = new byte[256];
                data.get(alphaPal, 0, length);
                for (int i = length; i < 256; i++) {
                    alphaPal[i] = (byte) 0xff;
                }
            } else if (colorType == PNG_COLOR_TYPE_GRAY) {
                alphaGrey = data.get();
            } else if (colorType == PNG_COLOR_TYPE_RGB) {
                alphaR = data.get();
                alphaG = data.get();
                alphaG = data.get();
            }
            data.getInt(); // crc
        }
    }

    @Override
    public VideoCodecMeta getCodecMeta(ByteBuffer _data) {
        ByteBuffer data = _data.duplicate();
        long sig = data.getLong();
        if (sig != PNGSIG && sig != MNGSIG)
            throw new RuntimeException("Not a PNG file.");

        while (data.remaining() >= 8) {
            int length = data.getInt();
            int tag = data.getInt();

            if (data.remaining() < length)
                break;

            switch (tag) {
            case TAG_IHDR:
                IHDR ihdr = new IHDR();
                ihdr.parse(data);
                return new VideoCodecMeta(new Size(ihdr.width, ihdr.height), ColorSpace.RGB);
            default:
                data.position(data.position() + length + 4);
            }
        }
        return null;
    }

    public static int probe(ByteBuffer data) {
        long sig = data.getLong();
        if (sig == PNGSIG && sig == MNGSIG)
            return 100;
        return 0;
    }

    public static byte[] deflate(byte[] data, Inflater inflater) throws DataFormatException {
        inflater.setInput(data);
        ByteArrayOutputStream baos = new ByteArrayOutputStream(data.length);
        byte[] buffer = new byte[1 << 14];
        while (!inflater.needsInput()) {
            int count = inflater.inflate(buffer);
            baos.write(buffer, 0, count);
            System.out.println(baos.size());
        }
        return baos.toByteArray();
    }
}
