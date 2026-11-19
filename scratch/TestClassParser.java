package test;

import java.io.DataInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class TestClassParser {
    public static void main(String[] args) throws Exception {
        Class<?> clazz = parseClass("io.sentry.i1", "p");
        System.out.println("Parsed target class for io.sentry.i1.p: " + clazz);
    }

    public static Class<?> parseClass(String className, String methodName) {
        try {
            String resPath = className.replace('.', '/') + ".class";
            InputStream is = TestClassParser.class.getClassLoader().getResourceAsStream(resPath);
            if (is == null) return null;

            DataInputStream dis = new DataInputStream(is);
            int magic = dis.readInt();
            if (magic != 0xCAFEBABE) return null;
            dis.readUnsignedShort(); // minor
            dis.readUnsignedShort(); // major

            int cpCount = dis.readUnsignedShort();
            Object[] cp = new Object[cpCount];
            int[] classRefNameIndex = new int[cpCount];

            for (int i = 1; i < cpCount; i++) {
                int tag = dis.readUnsignedByte();
                switch (tag) {
                    case 1: // Utf8
                        cp[i] = dis.readUTF();
                        break;
                    case 3: // Integer
                    case 4: // Float
                        dis.readInt();
                        break;
                    case 5: // Long
                    case 6: // Double
                        dis.readLong();
                        i++; // takes two slots
                        break;
                    case 7: // Class
                        classRefNameIndex[i] = dis.readUnsignedShort();
                        break;
                    case 8: // String
                    case 16: // MethodType
                    case 19: // Module
                    case 20: // Package
                        dis.readUnsignedShort();
                        break;
                    case 9: // Fieldref
                    case 10: // Methodref
                    case 11: // InterfaceMethodref
                    case 12: // NameAndType
                    case 17: // Dynamic
                    case 18: // InvokeDynamic
                        dis.readInt();
                        break;
                    case 15: // MethodHandle
                        dis.readByte();
                        dis.readUnsignedShort();
                        break;
                    default:
                        break;
                }
            }

            dis.readUnsignedShort(); // access_flags
            dis.readUnsignedShort(); // this_class
            dis.readUnsignedShort(); // super_class
            int interfacesCount = dis.readUnsignedShort();
            for (int i = 0; i < interfacesCount; i++) dis.readUnsignedShort();

            int fieldsCount = dis.readUnsignedShort();
            for (int i = 0; i < fieldsCount; i++) {
                dis.readUnsignedShort(); // access
                dis.readUnsignedShort(); // name_index
                dis.readUnsignedShort(); // desc_index
                int attrCount = dis.readUnsignedShort();
                for (int a = 0; a < attrCount; a++) {
                    dis.readUnsignedShort(); // attr_name
                    int len = dis.readInt();
                    dis.skipBytes(len);
                }
            }

            int methodsCount = dis.readUnsignedShort();
            for (int m = 0; m < methodsCount; m++) {
                dis.readUnsignedShort(); // access
                int nameIdx = dis.readUnsignedShort();
                int descIdx = dis.readUnsignedShort();
                String mName = (String) cp[nameIdx];
                int attrCount = dis.readUnsignedShort();

                byte[] codeBytes = null;
                for (int a = 0; a < attrCount; a++) {
                    int attrNameIdx = dis.readUnsignedShort();
                    int attrLen = dis.readInt();
                    String attrName = (String) cp[attrNameIdx];
                    if ("Code".equals(attrName)) {
                        dis.readUnsignedShort(); // max_stack
                        dis.readUnsignedShort(); // max_locals
                        int codeLen = dis.readInt();
                        codeBytes = new byte[codeLen];
                        dis.readFully(codeBytes);
                        int exceptionTableLen = dis.readUnsignedShort();
                        dis.skipBytes(exceptionTableLen * 8);
                        int codeAttrCount = dis.readUnsignedShort();
                        for (int ca = 0; ca < codeAttrCount; ca++) {
                            dis.readUnsignedShort();
                            int calen = dis.readInt();
                            dis.skipBytes(calen);
                        }
                    } else {
                        dis.skipBytes(attrLen);
                    }
                }

                if (methodName.equals(mName) && codeBytes != null) {
                    for (int i = 0; i < codeBytes.length - 5; i++) {
                        int opcode = codeBytes[i] & 0xFF;
                        if (opcode == 0xB8) { // invokestatic
                            int nextOpcode = codeBytes[i + 3] & 0xFF;
                            if (nextOpcode == 0xC0) { // checkcast
                                int clsIdx = ((codeBytes[i + 4] & 0xFF) << 8) | (codeBytes[i + 5] & 0xFF);
                                int utfIdx = classRefNameIndex[clsIdx];
                                String internalName = (String) cp[utfIdx];
                                if (internalName != null) {
                                    String targetClass = internalName.replace('/', '.');
                                    Class<?> res = Class.forName(targetClass);
                                    if (android.view.View.class.isAssignableFrom(res)) {
                                        return res;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
        return null;
    }
}
