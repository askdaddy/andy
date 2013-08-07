package com.vandalsoftware.android.spdyexample;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.vandalsoftware.android.spdy.FrameHandler;
import com.vandalsoftware.android.net.SSLSocketChannel;
import com.vandalsoftware.android.net.SocketReadHandler;
import com.vandalsoftware.android.spdy.DefaultSpdySynStreamFrame;
import com.vandalsoftware.android.spdy.SpdyDataFrame;
import com.vandalsoftware.android.spdy.SpdyFrameCodec;
import com.vandalsoftware.android.spdy.SpdySessionHandler;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.ByteChannel;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

public class SpdyActivity extends Activity {
    private static final String TAG = "spdy";
    private LogView mLogView;
    private Socket mSock;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        mLogView = (LogView) findViewById(R.id.logview);
        Button startBtn = (Button) findViewById(R.id.start);
        startBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new ConnectTask().execute();
            }
        });
        Button stopBtn = (Button) findViewById(R.id.stop);
        stopBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                log("Closing.");
                Socket s = mSock;
                if (s != null) {
                    try {
                        s.close();
                        Log.d(TAG, "socket closed.");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                log("Finished.");
            }
        });
        log("Ready.");
    }

    private void log(String message) {
        Log.d(TAG, message);
        mLogView.log(message);
    }

    class ConnectTask extends AsyncTask<Void, Void, Socket> implements SocketReadHandler, FrameHandler {
        private SpdyFrameCodec mSpdyFrameCodec;
        private SpdySessionHandler mSpdySessionHandler;

        @Override
        protected void onPreExecute() {
            mSpdyFrameCodec = new SpdyFrameCodec(3);
            mSpdySessionHandler = new SpdySessionHandler(3, false);
        }

        @Override
        protected void onPostExecute(Socket s) {
            mSock = s;
        }

        public void handleRead(ByteChannel channel, byte[] in, int index, int length) {
            Log.d(TAG, "handleRead " + length);
            ChannelBuffer channelBuffer = ChannelBuffers.wrappedBuffer(in, index, length);
            try {
                Log.d(TAG, "read index: " + channelBuffer.readerIndex());
                mSpdyFrameCodec.handleUpstream(null, null, channelBuffer, this);
            } catch (IOException e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }
            Log.d(TAG, "handleRead done");
        }

        @Override
        protected Socket doInBackground(Void... voids) {
            Socket s = null;
            try {
                SSLSocketChannel connector = SSLSocketChannel.open("TLS");
                connector.setSocketReadHandler(this);
                connector.connect(new InetSocketAddress("api.twitter.com", 443), 15000);
                Log.d(TAG, "socket connected.");
                SpdyFrameCodec codec = mSpdyFrameCodec;
                final DefaultSpdySynStreamFrame synStreamFrame = new DefaultSpdySynStreamFrame(1, 0, (byte) 0);
                synStreamFrame.addHeader(":method", "GET");
                synStreamFrame.addHeader(":path", "/1.1/statuses/home_timeline.json");
                synStreamFrame.addHeader(":version", "HTTP/1.1");
                synStreamFrame.addHeader(":host", "api.twitter.com:443");
                synStreamFrame.addHeader(":scheme", "https");
                synStreamFrame.setLast(true);
                mSpdySessionHandler.handleDownstream(null, null, synStreamFrame);
                codec.handleDownstream(null, null, connector, synStreamFrame);
                Log.d(TAG, "Wrote to socket.");
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (KeyManagementException e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (false && s != null) {
                    try {
                        s.close();
                        Log.d(TAG, "socket closed.");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            return s;
        }

        @Override
        public void handleFrame(Object frame) {
            try {
                mSpdySessionHandler.messageReceived(null, null, frame);
                if (frame instanceof SpdyDataFrame) {
                    Log.d(TAG, "Got data frame");
                    SpdyDataFrame dataFrame = (SpdyDataFrame) frame;
                    ChannelBuffer buffer = dataFrame.getData();
                    StringBuilder sb = new StringBuilder();
                    final int baselen = buffer.readableBytes();
                    int len = baselen;
                    int index = 0;
//                    while (len > 60) {
                        sb.setLength(0);
                        try {
                            byte[] outBytes = new byte[len - index];
                            buffer.getBytes(buffer.readerIndex() + index, outBytes);
                            Log.d(TAG, "decoding: " + outBytes.length);
//                    Inflater inflater = new Inflater(JZlib.DEF_WBITS, false);
//                    InputStream in = new InflaterInputStream(new ByteArrayInputStream(outBytes), inflater);
                            Inflater inflater = new Inflater(false);
                            InputStream in = new InflaterInputStream(new ByteArrayInputStream(outBytes), inflater);
                            byte[] inBuf = new byte[1024];
                            int count;
                            while ((count = in.read(inBuf, 0, inBuf.length)) != -1) {
                                sb.append(new String(inBuf, 0, count));
                            }
//                            inflater.setInput(outBytes, 0, outBytes.length);
//                            byte[] outie = new byte[100];
//                            inflater.inflate(outie);
//                            Log.d(TAG, "decompressed " + new String(outie, "UTF-8"));
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        Log.d(TAG, "done, len=" + len + ", res=" + sb.toString());
                        index++;
                        len = baselen - index;
//                    }
                }
            } catch (IOException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            } catch (Exception e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }
        }
    }
}
