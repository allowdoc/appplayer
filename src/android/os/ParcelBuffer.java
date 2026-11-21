package android.os;

import java.io.FileDescriptor;
import java.util.ArrayList;
import java.util.List;

class ParcelBuffer {
    private byte[] data;
    private int position;
    private int size;
    private final List<IBinder> binders = new ArrayList<>();
    private final List<FileDescriptor> fds = new ArrayList<>();

    public ParcelBuffer() {
        this.data = new byte[256];
        this.position = 0;
        this.size = 0;
    }

    private void ensureCapacity(int needed) {
        if (position + needed > data.length) {
            int newCap = Math.max(data.length * 2, position + needed + 128);
            byte[] newData = new byte[newCap];
            System.arraycopy(data, 0, newData, 0, size);
            data = newData;
        }
    }

    public int dataSize() { return size; }
    public int dataAvail() { return Math.max(0, size - position); }
    public int dataPosition() { return position; }
    public void setDataPosition(int pos) { this.position = Math.max(0, Math.min(pos, size)); }
    public void setDataSize(int s) { this.size = Math.max(0, s); if (position > size) position = size; }
    public void reset() { position = 0; size = 0; binders.clear(); fds.clear(); }

    public void writeInt(int val) {
        ensureCapacity(4);
        data[position++] = (byte) (val & 0xFF);
        data[position++] = (byte) ((val >> 8) & 0xFF);
        data[position++] = (byte) ((val >> 16) & 0xFF);
        data[position++] = (byte) ((val >> 24) & 0xFF);
        if (position > size) size = position;
    }

    public int readInt() {
        if (position + 4 > size) return 0;
        int b0 = data[position++] & 0xFF;
        int b1 = data[position++] & 0xFF;
        int b2 = data[position++] & 0xFF;
        int b3 = data[position++] & 0xFF;
        return b0 | (b1 << 8) | (b2 << 16) | (b3 << 24);
    }

    public void writeLong(long val) {
        writeInt((int) (val & 0xFFFFFFFFL));
        writeInt((int) ((val >>> 32) & 0xFFFFFFFFL));
    }

    public long readLong() {
        long low = readInt() & 0xFFFFFFFFL;
        long high = readInt() & 0xFFFFFFFFL;
        return low | (high << 32);
    }

    public void writeFloat(float val) {
        writeInt(Float.floatToRawIntBits(val));
    }

    public float readFloat() {
        return Float.intBitsToFloat(readInt());
    }

    public void writeDouble(double val) {
        writeLong(Double.doubleToRawLongBits(val));
    }

    public double readDouble() {
        return Double.longBitsToDouble(readLong());
    }

    public void writeByteArray(byte[] b, int offset, int len) {
        if (b == null || len <= 0) {
            writeInt(-1);
            return;
        }
        writeInt(len);
        ensureCapacity(len);
        System.arraycopy(b, offset, data, position, len);
        position += len;
        int padding = (4 - (len % 4)) % 4;
        ensureCapacity(padding);
        for (int i = 0; i < padding; i++) data[position++] = 0;
        if (position > size) size = position;
    }

    public byte[] createByteArray() {
        int len = readInt();
        if (len < 0 || position + len > size) return null;
        byte[] b = new byte[len];
        System.arraycopy(data, position, b, 0, len);
        position += len;
        int padding = (4 - (len % 4)) % 4;
        position += padding;
        if (position > size) position = size;
        return b;
    }

    public void writeString(String s) {
        if (s == null) {
            writeInt(-1);
            return;
        }
        byte[] bytes = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        writeByteArray(bytes, 0, bytes.length);
    }

    public String readString() {
        byte[] bytes = createByteArray();
        if (bytes == null) return null;
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    public void writeStrongBinder(IBinder binder) {
        if (binder == null) {
            writeInt(-1);
        } else {
            binders.add(binder);
            writeInt(binders.size() - 1);
        }
    }

    public IBinder readStrongBinder() {
        int idx = readInt();
        if (idx >= 0 && idx < binders.size()) {
            return binders.get(idx);
        }
        return null;
    }

    public void writeFileDescriptor(FileDescriptor fd) {
        if (fd == null) {
            writeInt(-1);
        } else {
            fds.add(fd);
            writeInt(fds.size() - 1);
        }
    }

    public FileDescriptor readFileDescriptor() {
        int idx = readInt();
        if (idx >= 0 && idx < fds.size()) {
            return fds.get(idx);
        }
        return null;
    }

    public byte[] marshall() {
        byte[] b = new byte[size];
        System.arraycopy(data, 0, b, 0, size);
        return b;
    }

    public void unmarshall(byte[] b, int offset, int len) {
        this.data = new byte[Math.max(len, 256)];
        System.arraycopy(b, offset, this.data, 0, len);
        this.position = 0;
        this.size = len;
    }
}
