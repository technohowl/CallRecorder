package org.jcodec.codecs.vpx;
import static org.jcodec.codecs.vpx.VP8Util.MAX_MODE_LF_DELTAS;
import static org.jcodec.codecs.vpx.VP8Util.MAX_REF_LF_DELTAS;
import static org.jcodec.codecs.vpx.VP8Util.getBitInBytes;
import static org.jcodec.codecs.vpx.VP8Util.getBitsInBytes;
import static org.jcodec.codecs.vpx.VP8Util.getDefaultCoefProbs;
import static org.jcodec.codecs.vpx.VP8Util.getMacroblockCount;
import static org.jcodec.codecs.vpx.VP8Util.keyFrameYModeProb;
import static org.jcodec.codecs.vpx.VP8Util.keyFrameYModeTree;
import static org.jcodec.codecs.vpx.VP8Util.vp8CoefUpdateProbs;

import java.nio.ByteBuffer;

import org.jcodec.api.NotSupportedException;
import org.jcodec.codecs.vpx.Macroblock.Subblock;
import org.jcodec.codecs.vpx.VP8Util.QuantizationParams;
import org.jcodec.codecs.vpx.VP8Util.SubblockConstants;
import org.jcodec.common.Assert;
import org.jcodec.common.VideoCodecMeta;
import org.jcodec.common.VideoDecoder;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.model.ColorSpace;
import org.jcodec.common.model.Picture8Bit;
import org.jcodec.common.model.Size;
import org.jcodec.common.tools.MathUtil;

/**
 * This class is part of JCodec ( www.jcodec.org ) This software is distributed
 * under FreeBSD License
 * 
 * @author The JCodec project
 * 
 */
public class VP8Decoder extends VideoDecoder {
    private byte[][] segmentationMap;
    private int[] refLoopFilterDeltas = new int[MAX_REF_LF_DELTAS];
    private int[] modeLoopFilterDeltas = new int[MAX_MODE_LF_DELTAS];

    @Override
    public Picture8Bit decodeFrame8Bit(ByteBuffer frame, byte[][] buffer) {
        byte[] firstThree = new byte[3];
        frame.get(firstThree);

        boolean keyFrame = getBitInBytes(firstThree, 0) == 0;
        if(!keyFrame)
            return null;
        int version = getBitsInBytes(firstThree, 1, 3);
        boolean showFrame = getBitInBytes(firstThree, 4) > 0;
        int partitionSize = getBitsInBytes(firstThree, 5, 19);
        String threeByteToken = printHexByte(frame.get()) + " " + printHexByte(frame.get()) + " "
                + printHexByte(frame.get());

        int twoBytesWidth = (frame.get() & 0xFF) | (frame.get() & 0xFF) << 8;
        int twoBytesHeight = (frame.get() & 0xFF) | (frame.get() & 0xFF) << 8;
        int width = (twoBytesWidth & 0x3fff);
        int height = (twoBytesHeight & 0x3fff);
        int numberOfMBRows = getMacroblockCount(height);
        int numberOfMBCols = getMacroblockCount(width);

        /** Init macroblocks and subblocks */
        if(segmentationMap == null)
            segmentationMap = new byte[numberOfMBRows][numberOfMBCols];
        Macroblock[][] mbs = new Macroblock[numberOfMBRows + 2][numberOfMBCols + 2];
        for (int row = 0; row < numberOfMBRows + 2; row++) {
            for (int col = 0; col < numberOfMBCols + 2; col++) {
                mbs[row][col] = new Macroblock(row, col);
            }
        }

        int headerOffset = frame.position();
        VPXBooleanDecoder headerDecoder = new VPXBooleanDecoder(frame, 0);
        boolean isYUVColorSpace = (headerDecoder.decodeBit() == 0);

        boolean clampingRequired = headerDecoder.decodeBit() == 0;
        int segmentation = headerDecoder.decodeBit();
        SegmentBasedAdjustments segmentBased = null;
        if(segmentation != 0) {
            segmentBased = updateSegmentation(headerDecoder);
            // Segmentation map persists between frames
            for (int row = 0; row < numberOfMBRows; row++) {
                for (int col = 0; col < numberOfMBCols; col++) {
                    mbs[row + 1][col + 1].segment = segmentationMap[row][col];
                }
            }
        }
        int simpleFilter = headerDecoder.decodeBit();
        int filterLevel = headerDecoder.decodeInt(6);
        int filterType = (filterLevel == 0) ? 0 : (simpleFilter > 0) ? 1 : 2;
        int sharpnessLevel = headerDecoder.decodeInt(3);
        int loopFilterDeltaFlag = headerDecoder.decodeBit();
        if(loopFilterDeltaFlag == 1) {
            int loopFilterDeltaUpdate = headerDecoder.decodeBit();
            if (loopFilterDeltaUpdate == 1) {
                for (int i = 0; i < MAX_REF_LF_DELTAS; i++) {

                    if (headerDecoder.decodeBit() > 0) {
                        refLoopFilterDeltas[i] = headerDecoder.decodeInt(6);
                        ;
                        if (headerDecoder.decodeBit() > 0) // Apply sign
                            refLoopFilterDeltas[i] = refLoopFilterDeltas[i] * -1;
                    }
                }
                for (int i = 0; i < MAX_MODE_LF_DELTAS; i++) {

                    if (headerDecoder.decodeBit() > 0) {
                        modeLoopFilterDeltas[i] = headerDecoder.decodeInt(6);
                        if (headerDecoder.decodeBit() > 0) // Apply sign
                            modeLoopFilterDeltas[i] = modeLoopFilterDeltas[i] * -1;
                    }
                }
            }
        }
        int log2OfPartCnt = headerDecoder.decodeInt(2);

        Assert.assertEquals(0, log2OfPartCnt);
        int partitionsCount = 1;
        long runningSize = 0;
        long zSize = frame.limit() - (partitionSize + headerOffset);
        ByteBuffer tokenBuffer = frame.duplicate();
        tokenBuffer.position(partitionSize + headerOffset);
        VPXBooleanDecoder decoder = new VPXBooleanDecoder(tokenBuffer, 0);

        int yacIndex = headerDecoder.decodeInt(7);
        int ydcDelta = ((headerDecoder.decodeBit() > 0) ? VP8Util.delta(headerDecoder) : 0);
        int y2dcDelta = ((headerDecoder.decodeBit() > 0) ? VP8Util.delta(headerDecoder) : 0);
        int y2acDelta = ((headerDecoder.decodeBit() > 0) ? VP8Util.delta(headerDecoder) : 0);
        int chromaDCDelta = ((headerDecoder.decodeBit() > 0) ? VP8Util.delta(headerDecoder) : 0);
        int chromaACDelta = ((headerDecoder.decodeBit() > 0) ? VP8Util.delta(headerDecoder) : 0);
        boolean refreshProbs = headerDecoder.decodeBit() == 0;
        QuantizationParams quants = new QuantizationParams(yacIndex, ydcDelta, y2dcDelta, y2acDelta, chromaDCDelta,
                chromaACDelta);

        int[][][][] coefProbs = getDefaultCoefProbs();
        for (int i = 0; i < VP8Util.BLOCK_TYPES; i++)
            for (int j = 0; j < VP8Util.COEF_BANDS; j++)
                for (int k = 0; k < VP8Util.PREV_COEF_CONTEXTS; k++)
                    for (int l = 0; l < VP8Util.MAX_ENTROPY_TOKENS - 1; l++) {

                        if (headerDecoder.decodeBool(vp8CoefUpdateProbs[i][j][k][l]) > 0) {
                            int newp = headerDecoder.decodeInt(8);
                            coefProbs[i][j][k][l] = newp;
                        }
                    }

        int macroBlockNoCoeffSkip = (int) headerDecoder.decodeBit();
        Assert.assertEquals(1, macroBlockNoCoeffSkip);
        int probSkipFalse = headerDecoder.decodeInt(8);
        for (int mbRow = 0; mbRow < numberOfMBRows; mbRow++) {
            for (int mbCol = 0; mbCol < numberOfMBCols; mbCol++) {
                Macroblock mb = mbs[mbRow + 1][mbCol + 1];
                if (segmentation != 0 && segmentBased.segmentProbs != null) {
                    // if segmentation is on and if segment map is updated
                    mb.segment = headerDecoder.readTree(VP8Util.segmentTree, segmentBased.segmentProbs);
                    segmentationMap[mbRow][mbCol] = (byte) mb.segment;
                }
                if (segmentation != 0 && segmentBased.qp != null) {
                    int qIndex = yacIndex;
                    if (segmentBased.abs != 0)
                        qIndex = segmentBased.qp[mb.segment];
                    else
                        qIndex += segmentBased.qp[mb.segment];
                    quants = new QuantizationParams(qIndex, ydcDelta, y2dcDelta, y2acDelta, chromaDCDelta,
                            chromaACDelta);
                }
                mb.quants = quants;

                // Ref based filter level adjustment
                if (loopFilterDeltaFlag != 0) {
                    int level = filterLevel;
                    level = level + refLoopFilterDeltas[0];
                    level = MathUtil.clip(level,  0,  63);
                    mb.filterLevel = level;
                } else {
                    mb.filterLevel = filterLevel;
                }
                
                // Segment based filter level adjustment
                if (segmentation != 0 && segmentBased.lf != null) {
                    if (segmentBased.abs != 0) {
                        mb.filterLevel = segmentBased.lf[mb.segment];
                    } else {
                        mb.filterLevel += segmentBased.lf[mb.segment];
                        mb.filterLevel = MathUtil.clip(mb.filterLevel,  0,  63);
                    }
                }

                if (macroBlockNoCoeffSkip > 0)
                    mb.skipCoeff = headerDecoder.decodeBool(probSkipFalse);

                mb.lumaMode = headerDecoder.readTree(keyFrameYModeTree, keyFrameYModeProb);
                // 1 is added to account for non-displayed framing macroblocks,
                // which are used for prediction only.
                if (mb.lumaMode == SubblockConstants.B_PRED) {
                    for (int sbRow = 0; sbRow < 4; sbRow++) {
                        for (int sbCol = 0; sbCol < 4; sbCol++) {

                            Subblock sb = mb.ySubblocks[sbRow][sbCol];
                            Subblock A = sb.getAbove(VP8Util.PLANE.Y1, mbs);

                            Subblock L = sb.getLeft(VP8Util.PLANE.Y1, mbs);

                            sb.mode = headerDecoder.readTree(SubblockConstants.subblockModeTree,
                                    SubblockConstants.keyFrameSubblockModeProb[A.mode][L.mode]);

                        }
                    }

                } else {
                    int fixedMode;

                    switch (mb.lumaMode) {
                    case SubblockConstants.DC_PRED:
                        fixedMode = SubblockConstants.B_DC_PRED;
                        break;
                    case SubblockConstants.V_PRED:
                        fixedMode = SubblockConstants.B_VE_PRED;
                        break;
                    case SubblockConstants.H_PRED:
                        fixedMode = SubblockConstants.B_HE_PRED;
                        break;
                    case SubblockConstants.TM_PRED:
                        fixedMode = SubblockConstants.B_TM_PRED;
                        break;
                    default:
                        fixedMode = SubblockConstants.B_DC_PRED;
                        break;
                    }
                    mb.lumaMode = edgeEmu(mb.lumaMode, mbCol, mbRow);
                    for (int x = 0; x < 4; x++)
                        for (int y = 0; y < 4; y++)
                            mb.ySubblocks[y][x].mode = fixedMode;
                }
                mb.chromaMode = headerDecoder.readTree(VP8Util.vp8UVModeTree, VP8Util.vp8KeyFrameUVModeProb);
            }
        }

        for (int mbRow = 0; mbRow < numberOfMBRows; mbRow++) {
            for (int mbCol = 0; mbCol < numberOfMBCols; mbCol++) {
                Macroblock mb = mbs[mbRow + 1][mbCol + 1];
                mb.decodeMacroBlock(mbs, decoder, coefProbs);
                mb.dequantMacroBlock(mbs);
            }
        }

        if (filterType > 0 && filterLevel != 0) {
            if (filterType == 2) {
                FilterUtil.loopFilterUV(mbs, sharpnessLevel, keyFrame);
                FilterUtil.loopFilterY(mbs, sharpnessLevel, keyFrame);
            } else if (filterType == 1) {
                // loopFilterSimple(frame);
            }
        }

        Picture8Bit p = Picture8Bit.createPicture8Bit(width, height, buffer, ColorSpace.YUV420);

        int mbWidth = getMacroblockCount(width);
        int mbHeight = getMacroblockCount(height);

        for (int mbRow = 0; mbRow < mbHeight; mbRow++) {
            for (int mbCol = 0; mbCol < mbWidth; mbCol++) {
                Macroblock mb = mbs[mbRow + 1][mbCol + 1];
                mb.put(mbRow, mbCol, p);
            }
        }
        return p;
    }
    
    private int edgeEmu(int mode, int mbCol, int mbRow) {
        switch (mode) {
        case SubblockConstants.V_PRED:
            return mbRow == 0 ? SubblockConstants.DC_PRED : mode;
        case SubblockConstants.H_PRED:
            return mbCol == 0 ? SubblockConstants.DC_PRED : mode;
        case SubblockConstants.TM_PRED:
            return edgeEmuTm(mode, mbCol, mbRow);
            default:
                return mode;
        }
    }
    
    private int edgeEmuTm(int mode, int mbCol, int mbRow) {
        if (mbCol == 0)
          return mbRow != 0 ? SubblockConstants.V_PRED : SubblockConstants.DC_PRED;
      else
          return mbRow != 0 ? mode : SubblockConstants.H_PRED;
    }

    private static class SegmentBasedAdjustments {
        private int[] segmentProbs;
        private int[] qp;
        private int[] lf;
        private int abs;

        public SegmentBasedAdjustments(int[] segmentProbs, int[] qp, int[] lf, int abs) {
            this.segmentProbs = segmentProbs;
            this.qp = qp;
            this.lf = lf;
            this.abs = abs;
        }
    }

    private SegmentBasedAdjustments updateSegmentation(VPXBooleanDecoder headerDecoder) {
        int updateMBSegmentationMap = headerDecoder.decodeBit();
        int updateSegmentFeatureData = headerDecoder.decodeBit();

        int[] qp = null;
        int[] lf = null;
        int abs = 0;
        if (updateSegmentFeatureData != 0) {
            qp = new int[4];
            lf = new int[4];
            abs = headerDecoder.decodeBit();
            for (int i = 0; i < 4; i++) {
                int quantizerUpdate = headerDecoder.decodeBit();
                if (quantizerUpdate != 0) {
                    qp[i] = headerDecoder.decodeInt(7);
                    qp[i] = headerDecoder.decodeBit() != 0 ? -qp[i] : qp[i];
                }
            }
            for (int i = 0; i < 4; i++) {
                int loopFilterUpdate = headerDecoder.decodeBit();
                if (loopFilterUpdate != 0) {
                    lf[i] = headerDecoder.decodeInt(6);
                    lf[i] = headerDecoder.decodeBit() != 0 ? -lf[i] : lf[i];
                }
            }
        }
        int[] segmentProbs = new int[3];
        if (updateMBSegmentationMap != 0) {
            for (int i = 0; i < 3; i++) {
                int segmentProbUpdate = headerDecoder.decodeBit();
                if (segmentProbUpdate != 0)
                    segmentProbs[i] = headerDecoder.decodeInt(8);
                else
                    segmentProbs[i] = 255;
            }
        }
        return new SegmentBasedAdjustments(segmentProbs, qp, lf, abs);
    }

    public static int probe(ByteBuffer data) {
        if ((data.get(3) & 0xff) == 0x9d && (data.get(4) & 0xff) == 0x1 && (data.get(5) & 0xff) == 0x2a)
            return 100;
        return 0;
    }

    public static String printHexByte(byte b) {
        return "0x" + Integer.toHexString(b & 0xFF);
    }

    @Override
    public VideoCodecMeta getCodecMeta(ByteBuffer frame) {
        NIOUtils.skip(frame, 6);

        int twoBytesWidth = (frame.get() & 0xFF) | (frame.get() & 0xFF) << 8;
        int twoBytesHeight = (frame.get() & 0xFF) | (frame.get() & 0xFF) << 8;
        int width = (twoBytesWidth & 0x3fff);
        int height = (twoBytesHeight & 0x3fff);

        return new VideoCodecMeta(new Size(width, height), ColorSpace.YUV420);
    }
}
