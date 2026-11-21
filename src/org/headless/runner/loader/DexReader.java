package org.headless.runner.loader;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

public class DexReader {
    private final File dexFile;
    private int classCount;
    private int stringCount;
    private int methodCount;
    private int protoCount;
    private int fieldCount;
    private final List<String> classNames = new ArrayList<>();

    public DexReader(File dexFile) {
        this.dexFile = dexFile;
    }

    public void parseHeader() throws Exception {
        try (RandomAccessFile raf = new RandomAccessFile(dexFile, "r");
             FileChannel channel = raf.getChannel()) {

            ByteBuffer buf = ByteBuffer.allocate((int) Math.min(channel.size(), 0x70));
            buf.order(ByteOrder.LITTLE_ENDIAN);
            channel.read(buf);
            buf.flip();

            byte[] magic = new byte[8];
            buf.get(magic);
            String magicStr = new String(magic, 0, 7);
            if (!magicStr.startsWith("dex\n")) {
                throw new IllegalArgumentException("Not a valid DEX file: " + dexFile.getName());
            }

            buf.position(0x38); // string_ids_size
            stringCount = buf.getInt();
            int stringIdsOff = buf.getInt();

            int typeIdsSize = buf.getInt();
            int typeIdsOff = buf.getInt();

            protoCount = buf.getInt();
            int protoIdsOff = buf.getInt();

            fieldCount = buf.getInt();
            int fieldIdsOff = buf.getInt();

            methodCount = buf.getInt();
            int methodIdsOff = buf.getInt();

            classCount = buf.getInt();
            int classDefsOff = buf.getInt();
        }
    }

    public int getClassCount() { return classCount; }
    public int getStringCount() { return stringCount; }
    public int getMethodCount() { return methodCount; }
    public int getFieldCount() { return fieldCount; }
    public String getFileName() { return dexFile.getName(); }
}
