/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.vandalsoftware.android.spdy;

import com.jcraft.jzlib.Deflater;
import com.jcraft.jzlib.GZIPException;
import com.jcraft.jzlib.JZlib;
import org.jboss.netty.buffer.ChannelBuffer;

import static com.vandalsoftware.android.spdy.SpdyCodecUtil.SPDY2_DICT;
import static com.vandalsoftware.android.spdy.SpdyCodecUtil.SPDY_DICT;
import static com.vandalsoftware.android.spdy.SpdyCodecUtil.SPDY_MAX_VERSION;
import static com.vandalsoftware.android.spdy.SpdyCodecUtil.SPDY_MIN_VERSION;

class SpdyHeaderBlockZlibCompressor extends SpdyHeaderBlockCompressor {

    private final byte[] out = new byte[8192];
    private final Deflater compressor;

    public SpdyHeaderBlockZlibCompressor(int version, int compressionLevel) throws GZIPException {
        if (version < SPDY_MIN_VERSION || version > SPDY_MAX_VERSION) {
            throw new IllegalArgumentException(
                    "unsupported version: " + version);
        }
        if (compressionLevel < 0 || compressionLevel > 9) {
            throw new IllegalArgumentException(
                    "compressionLevel: " + compressionLevel + " (expected: 0-9)");
        }
        compressor = new Deflater(compressionLevel);
        if (version < 3) {
            compressor.setDictionary(SPDY2_DICT, SPDY2_DICT.length);
        } else {
            compressor.setDictionary(SPDY_DICT, SPDY_DICT.length);
        }
    }

    @Override
    public void setInput(ChannelBuffer decompressed) {
        byte[] in = new byte[decompressed.readableBytes()];
        decompressed.readBytes(in);
        compressor.setInput(in);
    }

    @Override
    public void encode(ChannelBuffer compressed) {
        int numBytes = out.length;
        while (numBytes == out.length) {
            compressor.setOutput(out, 0, out.length);
            compressor.deflate(JZlib.Z_SYNC_FLUSH);
            numBytes = compressor.next_out_index;
            compressed.writeBytes(out, 0, numBytes);
        }
    }

    @Override
    public void end() {
        compressor.end();
    }
}
