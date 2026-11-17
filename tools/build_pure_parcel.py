import os, re, subprocess

src_file = r"C:\Users\Noval\.gemini\antigravity\scratch\android-runner\scratch\parcel_out\Parcel.java"
dst_file = r"C:\Users\Noval\.gemini\antigravity\scratch\android-runner\src\android\os\Parcel.java"

with open(src_file, "r", encoding="utf-8") as f:
    code = f.read()

# Strip UnsupportedAppUsage
code = re.sub(r"@UnsupportedAppUsage(\([^)]*\))?", "", code)

# 1. Fix STRING_CREATOR anonymous class
code = re.sub(
    r"public static final Parcelable\.Creator STRING_CREATOR = new 1\(\);",
    """public static final Parcelable.Creator<String> STRING_CREATOR = new Parcelable.Creator<String>() {
      public String createFromParcel(Parcel source) {
         return source.readString();
      }
      public String[] newArray(int size) {
         return new String[size];
      }
   };""",
    code
)

# 2. Fix ObjectInputStream anonymous class
code = re.sub(
    r"ObjectInputStream ois = new 2\(this, bais, loader\);",
    """ObjectInputStream ois = new ObjectInputStream(bais) {
               protected Class<?> resolveClass(java.io.ObjectStreamClass osClass) throws IOException, ClassNotFoundException {
                  Class<?> c = loader != null ? Class.forName(osClass.getName(), false, loader) : super.resolveClass(osClass);
                  return c != null ? c : super.resolveClass(osClass);
               }
            };""",
    code
)

# 3. Fix writeArray cast
code = re.sub(r"this\.writeArray\(v\);", "this.writeArray((Object[])v);", code)

# 4. Fix generic signatures
code = re.sub(
    r"public final ArrayList createTypedArrayList\(Parcelable\.Creator c\)",
    "public final <T> ArrayList<T> createTypedArrayList(Parcelable.Creator<T> c)",
    code
)

code = re.sub(
    r"public final SparseArray createTypedSparseArray\(Parcelable\.Creator creator\)",
    "public final <T extends Parcelable> SparseArray<T> createTypedSparseArray(Parcelable.Creator<T> creator)",
    code
)

code = re.sub(
    r"public final ArrayMap createTypedArrayMap\(Parcelable\.Creator creator\)",
    "public final <T extends Parcelable> ArrayMap<String, T> createTypedArrayMap(Parcelable.Creator<T> creator)",
    code
)

code = re.sub(
    r"public final Object\[\] createTypedArray\(Parcelable\.Creator c\)",
    "public final <T> T[] createTypedArray(Parcelable.Creator<T> c)",
    code
)

code = re.sub(
    r"public final void readTypedArray\(Object\[\] val, Parcelable\.Creator c\)",
    "public final <T> void readTypedArray(T[] val, Parcelable.Creator<T> c)",
    code
)

code = re.sub(
    r"public final Object\[\] readTypedArray\(Parcelable\.Creator c\)",
    "public final <T> T[] readTypedArray(Parcelable.Creator<T> c)",
    code
)

code = re.sub(
    r"public final Parcelable\[\] readParcelableArray\(ClassLoader loader, Class clazz\)",
    "public final <T extends Parcelable> T[] readParcelableArray(ClassLoader loader, Class<T> clazz)",
    code
)

# 4b. Casts for generics
code = re.sub(r"l\.add\(this\.readTypedObject\(c\)\);", "l.add((T)this.readTypedObject(c));", code)
code = re.sub(r"l\[i\] = this\.readTypedObject\(c\);", "l[i] = (T)this.readTypedObject(c);", code)
code = re.sub(r"val\[i\] = this\.readTypedObject\(c\);", "val[i] = (T)this.readTypedObject(c);", code)
# In readParcelableArray(loader, clazz)
code = re.sub(
    r"(public final <T extends Parcelable> T\[\] readParcelableArray[\s\S]*?)p\[i\] = this\.readParcelable\(loader\);",
    r"\1p[i] = (T)this.readParcelable(loader);",
    code
)

# 5. Add ReadWriteHelper static class before end of Parcel class
rwh = """
   public static class ReadWriteHelper {
      public static final ReadWriteHelper DEFAULT = new ReadWriteHelper();

      public ReadWriteHelper() {
      }

      public void writeString(Parcel p, String s) {
         nativeWriteString(p.mNativePtr, s);
      }

      public String readString(Parcel p) {
         return nativeReadString(p.mNativePtr);
      }
   }
"""

# 6. Remove all native annotations and signatures
pattern = r"(@(CriticalNative|FastNative)\s+)?(private|static|public)\s+(static\s+)?native\s+[^;]+;"
code = re.sub(pattern, "", code)

native_replacements = """
   private static final java.util.concurrent.atomic.AtomicLong sNativeIdSeq = new java.util.concurrent.atomic.AtomicLong(1);
   private static final java.util.Map<Long, ParcelBuffer> sParcelBuffers = new java.util.concurrent.ConcurrentHashMap<>();

   private static long nativeCreate() {
      long id = sNativeIdSeq.getAndIncrement();
      sParcelBuffers.put(id, new ParcelBuffer());
      return id;
   }

   private static void nativeDestroy(long var0) {
      sParcelBuffers.remove(var0);
   }

   private static int nativeDataSize(long var0) {
      ParcelBuffer b = sParcelBuffers.get(var0);
      return b != null ? b.dataSize() : 0;
   }

   private static int nativeDataAvail(long var0) {
      ParcelBuffer b = sParcelBuffers.get(var0);
      return b != null ? b.dataAvail() : 0;
   }

   private static int nativeDataPosition(long var0) {
      ParcelBuffer b = sParcelBuffers.get(var0);
      return b != null ? b.dataPosition() : 0;
   }

   private static int nativeDataCapacity(long var0) {
      ParcelBuffer b = sParcelBuffers.get(var0);
      return b != null ? b.dataSize() : 0;
   }

   private static long nativeSetDataSize(long var0, int var2) {
      ParcelBuffer b = sParcelBuffers.get(var0);
      if (b != null) b.setDataSize(var2);
      return 0L;
   }

   private static void nativeSetDataPosition(long var0, int var2) {
      ParcelBuffer b = sParcelBuffers.get(var0);
      if (b != null) b.setDataPosition(var2);
   }

   private static void nativeSetDataCapacity(long var0, int var2) {
   }

   private static boolean nativePushAllowFds(long var0, boolean var2) { return true; }
   private static void nativeRestoreAllowFds(long var0, boolean var2) {}

   private static void nativeWriteByteArray(long var0, byte[] var2, int var3, int var4) {
      ParcelBuffer b = sParcelBuffers.get(var0);
      if (b != null) b.writeByteArray(var2, var3, var4);
   }

   private static void nativeWriteBlob(long var0, byte[] var2, int var3, int var4) {
      nativeWriteByteArray(var0, var2, var3, var4);
   }

   private static void nativeWriteInt(long var0, int var2) {
      ParcelBuffer b = sParcelBuffers.get(var0);
      if (b != null) b.writeInt(var2);
   }

   private static void nativeWriteLong(long var0, long var2) {
      ParcelBuffer b = sParcelBuffers.get(var0);
      if (b != null) b.writeLong(var2);
   }

   private static void nativeWriteFloat(long var0, float var2) {
      ParcelBuffer b = sParcelBuffers.get(var0);
      if (b != null) b.writeFloat(var2);
   }

   private static void nativeWriteDouble(long var0, double var2) {
      ParcelBuffer b = sParcelBuffers.get(var0);
      if (b != null) b.writeDouble(var2);
   }

   static void nativeWriteString(long var0, String var2) {
      ParcelBuffer b = sParcelBuffers.get(var0);
      if (b != null) b.writeString(var2);
   }

   private static void nativeWriteStrongBinder(long var0, IBinder var2) {
      ParcelBuffer b = sParcelBuffers.get(var0);
      if (b != null) b.writeStrongBinder(var2);
   }

   private static long nativeWriteFileDescriptor(long var0, FileDescriptor var2) {
      ParcelBuffer b = sParcelBuffers.get(var0);
      if (b != null) b.writeFileDescriptor(var2);
      return 0L;
   }

   private static byte[] nativeCreateByteArray(long var0) {
      ParcelBuffer b = sParcelBuffers.get(var0);
      return b != null ? b.createByteArray() : null;
   }

   private static boolean nativeReadByteArray(long var0, byte[] var2, int var3) {
      byte[] arr = nativeCreateByteArray(var0);
      if (arr != null && var2 != null) {
         System.arraycopy(arr, 0, var2, 0, Math.min(arr.length, var3));
         return true;
      }
      return false;
   }

   private static byte[] nativeReadBlob(long var0) {
      return nativeCreateByteArray(var0);
   }

   private static int nativeReadInt(long var0) {
      ParcelBuffer b = sParcelBuffers.get(var0);
      return b != null ? b.readInt() : 0;
   }

   private static long nativeReadLong(long var0) {
      ParcelBuffer b = sParcelBuffers.get(var0);
      return b != null ? b.readLong() : 0L;
   }

   private static float nativeReadFloat(long var0) {
      ParcelBuffer b = sParcelBuffers.get(var0);
      return b != null ? b.readFloat() : 0.0f;
   }

   private static double nativeReadDouble(long var0) {
      ParcelBuffer b = sParcelBuffers.get(var0);
      return b != null ? b.readDouble() : 0.0;
   }

   static String nativeReadString(long var0) {
      ParcelBuffer b = sParcelBuffers.get(var0);
      return b != null ? b.readString() : null;
   }

   private static IBinder nativeReadStrongBinder(long var0) {
      ParcelBuffer b = sParcelBuffers.get(var0);
      return b != null ? b.readStrongBinder() : null;
   }

   private static FileDescriptor nativeReadFileDescriptor(long var0) {
      ParcelBuffer b = sParcelBuffers.get(var0);
      return b != null ? b.readFileDescriptor() : null;
   }

   private static long nativeFreeBuffer(long var0) {
      ParcelBuffer b = sParcelBuffers.get(var0);
      if (b != null) b.reset();
      return 0L;
   }

   private static byte[] nativeMarshall(long var0) {
      ParcelBuffer b = sParcelBuffers.get(var0);
      return b != null ? b.marshall() : new byte[0];
   }

   private static long nativeUnmarshall(long var0, byte[] var2, int var3, int var4) {
      ParcelBuffer b = sParcelBuffers.get(var0);
      if (b != null) b.unmarshall(var2, var3, var4);
      return 0L;
   }

   private static int nativeCompareData(long var0, long var2) { return 0; }

   private static long nativeAppendFrom(long var0, long var2, int var4, int var5) {
      ParcelBuffer src = sParcelBuffers.get(var2);
      ParcelBuffer dst = sParcelBuffers.get(var0);
      if (src != null && dst != null) {
         byte[] bytes = src.marshall();
         dst.writeByteArray(bytes, var4, var5);
      }
      return 0L;
   }

   private static boolean nativeHasFileDescriptors(long var0) { return false; }
   private static void nativeWriteInterfaceToken(long var0, String var2) { nativeWriteString(var0, var2); }
   private static void nativeEnforceInterface(long var0, String var2) {}
   private static boolean nativeReplaceCallingWorkSourceUid(long var0, int var2) { return true; }
   private static int nativeReadCallingWorkSourceUid(long var0) { return 10100; }
   private static long nativeGetBlobAshmemSize(long var0) { return 0L; }
   public static long getGlobalAllocSize() { return 0L; }
   public static long getGlobalAllocCount() { return 0L; }
"""

# Put implementations at the end of class before the last closing brace
last_brace = code.rfind("}")
code = code[:last_brace] + rwh + native_replacements + "\n}\n"

with open(dst_file, "w", encoding="utf-8") as f:
    f.write(code)

print("Generated Parcel.java with all generic fixes successfully!")
