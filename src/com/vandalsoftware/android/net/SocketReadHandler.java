package com.vandalsoftware.android.net;

import java.nio.channels.ByteChannel;

public interface SocketReadHandler {
    void handleRead(ByteChannel channel);
}
