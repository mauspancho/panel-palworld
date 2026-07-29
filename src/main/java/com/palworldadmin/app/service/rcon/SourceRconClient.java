package com.palworldadmin.app.service.rcon;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

public class SourceRconClient implements Closeable {
    private static final int SERVERDATA_RESPONSE_VALUE = 0;
    private static final int SERVERDATA_EXECCOMMAND = 2;
    private static final int SERVERDATA_AUTH = 3;
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 5000;

    private final Socket socket;
    private final DataInputStream input;
    private final OutputStream output;
    private int requestId = 100;

    public SourceRconClient(String host, int port, String password) {
        try {
            this.socket = new Socket();
            this.socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            this.socket.setSoTimeout(READ_TIMEOUT_MS);
            this.input = new DataInputStream(socket.getInputStream());
            this.output = socket.getOutputStream();
            authenticate(password);
        } catch (IOException e) {
            throw new RconException("No se pudo conectar a RCON en " + host + ":" + port + ": " + e.getMessage(), e);
        }
    }

    public String command(String command) {
        try {
            int id = nextRequestId();
            writePacket(id, SERVERDATA_EXECCOMMAND, command);
            StringBuilder response = new StringBuilder();
            while (true) {
                try {
                    Packet packet = readPacket();
                    if (packet.type() == SERVERDATA_RESPONSE_VALUE || packet.requestId() == id) {
                        response.append(packet.body());
                    }
                } catch (SocketTimeoutException e) {
                    break;
                }
                if (response.length() > 0) {
                    break;
                }
            }
            return response.toString().trim();
        } catch (IOException e) {
            throw new RconException("No se pudo ejecutar comando RCON: " + e.getMessage(), e);
        }
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }

    private void authenticate(String password) throws IOException {
        int id = nextRequestId();
        writePacket(id, SERVERDATA_AUTH, password == null ? "" : password);
        boolean authenticated = false;
        for (int i = 0; i < 2; i++) {
            Packet packet;
            try {
                packet = readPacket();
            } catch (SocketTimeoutException e) {
                break;
            }
            if (packet.requestId() == -1) {
                throw new RconException("Password RCON invalido.");
            }
            if (packet.requestId() == id) {
                authenticated = true;
            }
        }
        if (!authenticated) {
            throw new RconException("No se pudo autenticar con RCON.");
        }
    }

    private int nextRequestId() {
        return requestId++;
    }

    private void writePacket(int id, int type, String body) throws IOException {
        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(4 + 4 + 4 + bodyBytes.length + 2).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(4 + 4 + bodyBytes.length + 2);
        buffer.putInt(id);
        buffer.putInt(type);
        buffer.put(bodyBytes);
        buffer.put((byte) 0);
        buffer.put((byte) 0);
        output.write(buffer.array());
        output.flush();
    }

    private Packet readPacket() throws IOException {
        int size;
        try {
            size = Integer.reverseBytes(input.readInt());
        } catch (EOFException e) {
            throw new RconException("RCON cerro la conexion.", e);
        }
        if (size < 10 || size > 4096 * 8) {
            throw new RconException("Respuesta RCON invalida.");
        }
        byte[] payload = input.readNBytes(size);
        if (payload.length != size) {
            throw new RconException("Respuesta RCON incompleta.");
        }
        ByteBuffer buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);
        int id = buffer.getInt();
        int type = buffer.getInt();
        byte[] bodyBytes = new byte[Math.max(0, size - 10)];
        buffer.get(bodyBytes);
        String body = new String(bodyBytes, StandardCharsets.UTF_8);
        return new Packet(id, type, body);
    }

    private record Packet(int requestId, int type, String body) {
    }
}
