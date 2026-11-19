package android.os;

import android.annotation.UnsupportedAppUsage;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.ExceptionUtils;
import android.util.Log;
import android.util.Size;
import android.util.SizeF;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import android.util.SparseIntArray;
import dalvik.annotation.optimization.CriticalNative;
import dalvik.annotation.optimization.FastNative;
import dalvik.system.VMRuntime;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import libcore.util.ArrayUtils;
import libcore.util.SneakyThrow;

public final class Parcel {
   private static final boolean DEBUG_RECYCLE = false;
   private static final boolean DEBUG_ARRAY_MAP = false;
   private static final String TAG = "Parcel";
   @UnsupportedAppUsage
   private long mNativePtr;
   private boolean mOwnsNativeParcelObject;
   private long mNativeSize;
   private ArrayMap mClassCookies;
   private RuntimeException mStack;
   private static boolean sParcelExceptionStackTrace;
   private static final int POOL_SIZE = 6;
   private static final Parcel[] sOwnedPool = new Parcel[6];
   private static final Parcel[] sHolderPool = new Parcel[6];
   private static final int VAL_NULL = -1;
   private static final int VAL_STRING = 0;
   private static final int VAL_INTEGER = 1;
   private static final int VAL_MAP = 2;
   private static final int VAL_BUNDLE = 3;
   private static final int VAL_PARCELABLE = 4;
   private static final int VAL_SHORT = 5;
   private static final int VAL_LONG = 6;
   private static final int VAL_FLOAT = 7;
   private static final int VAL_DOUBLE = 8;
   private static final int VAL_BOOLEAN = 9;
   private static final int VAL_CHARSEQUENCE = 10;
   private static final int VAL_LIST = 11;
   private static final int VAL_SPARSEARRAY = 12;
   private static final int VAL_BYTEARRAY = 13;
   private static final int VAL_STRINGARRAY = 14;
   private static final int VAL_IBINDER = 15;
   private static final int VAL_PARCELABLEARRAY = 16;
   private static final int VAL_OBJECTARRAY = 17;
   private static final int VAL_INTARRAY = 18;
   private static final int VAL_LONGARRAY = 19;
   private static final int VAL_BYTE = 20;
   private static final int VAL_SERIALIZABLE = 21;
   private static final int VAL_SPARSEBOOLEANARRAY = 22;
   private static final int VAL_BOOLEANARRAY = 23;
   private static final int VAL_CHARSEQUENCEARRAY = 24;
   private static final int VAL_PERSISTABLEBUNDLE = 25;
   private static final int VAL_SIZE = 26;
   private static final int VAL_SIZEF = 27;
   private static final int VAL_DOUBLEARRAY = 28;
   private static final int EX_SECURITY = -1;
   private static final int EX_BAD_PARCELABLE = -2;
   private static final int EX_ILLEGAL_ARGUMENT = -3;
   private static final int EX_NULL_POINTER = -4;
   private static final int EX_ILLEGAL_STATE = -5;
   private static final int EX_NETWORK_MAIN_THREAD = -6;
   private static final int EX_UNSUPPORTED_OPERATION = -7;
   private static final int EX_SERVICE_SPECIFIC = -8;
   private static final int EX_PARCELABLE = -9;
   private static final int EX_HAS_REPLY_HEADER = -128;
   private static final int EX_TRANSACTION_FAILED = -129;
   private static volatile long sLastWriteExceptionStackTrace;
   private static final int WRITE_EXCEPTION_STACK_TRACE_THRESHOLD_MS = 1000;
   public static final Parcelable.Creator STRING_CREATOR = new 1();
   private ReadWriteHelper mReadWriteHelper;
   private static final HashMap mCreators = new HashMap();

   @CriticalNative
   private static native int nativeDataSize(long var0);

   @CriticalNative
   private static native int nativeDataAvail(long var0);

   @CriticalNative
   private static native int nativeDataPosition(long var0);

   @CriticalNative
   private static native int nativeDataCapacity(long var0);

   @FastNative
   private static native long nativeSetDataSize(long var0, int var2);

   @CriticalNative
   private static native void nativeSetDataPosition(long var0, int var2);

   @FastNative
   private static native void nativeSetDataCapacity(long var0, int var2);

   @CriticalNative
   private static native boolean nativePushAllowFds(long var0, boolean var2);

   @CriticalNative
   private static native void nativeRestoreAllowFds(long var0, boolean var2);

   private static native void nativeWriteByteArray(long var0, byte[] var2, int var3, int var4);

   private static native void nativeWriteBlob(long var0, byte[] var2, int var3, int var4);

   @FastNative
   private static native void nativeWriteInt(long var0, int var2);

   @FastNative
   private static native void nativeWriteLong(long var0, long var2);

   @FastNative
   private static native void nativeWriteFloat(long var0, float var2);

   @FastNative
   private static native void nativeWriteDouble(long var0, double var2);

   static native void nativeWriteString(long var0, String var2);

   private static native void nativeWriteStrongBinder(long var0, IBinder var2);

   private static native long nativeWriteFileDescriptor(long var0, FileDescriptor var2);

   private static native byte[] nativeCreateByteArray(long var0);

   private static native boolean nativeReadByteArray(long var0, byte[] var2, int var3);

   private static native byte[] nativeReadBlob(long var0);

   @CriticalNative
   private static native int nativeReadInt(long var0);

   @CriticalNative
   private static native long nativeReadLong(long var0);

   @CriticalNative
   private static native float nativeReadFloat(long var0);

   @CriticalNative
   private static native double nativeReadDouble(long var0);

   static native String nativeReadString(long var0);

   private static native IBinder nativeReadStrongBinder(long var0);

   private static native FileDescriptor nativeReadFileDescriptor(long var0);

   private static native long nativeCreate();

   private static native long nativeFreeBuffer(long var0);

   private static native void nativeDestroy(long var0);

   private static native byte[] nativeMarshall(long var0);

   private static native long nativeUnmarshall(long var0, byte[] var2, int var3, int var4);

   private static native int nativeCompareData(long var0, long var2);

   private static native long nativeAppendFrom(long var0, long var2, int var4, int var5);

   @CriticalNative
   private static native boolean nativeHasFileDescriptors(long var0);

   private static native void nativeWriteInterfaceToken(long var0, String var2);

   private static native void nativeEnforceInterface(long var0, String var2);

   @CriticalNative
   private static native boolean nativeReplaceCallingWorkSourceUid(long var0, int var2);

   @CriticalNative
   private static native int nativeReadCallingWorkSourceUid(long var0);

   @CriticalNative
   private static native long nativeGetBlobAshmemSize(long var0);

   public static Parcel obtain() {
      Parcel[] pool = sOwnedPool;
      synchronized(pool) {
         for(int i = 0; i < 6; ++i) {
            Parcel p = pool[i];
            if (p != null) {
               pool[i] = null;
               p.mReadWriteHelper = android.os.Parcel.ReadWriteHelper.DEFAULT;
               return p;
            }
         }
      }

      return new Parcel(0L);
   }

   public final void recycle() {
      this.freeBuffer();
      Parcel[] pool;
      if (this.mOwnsNativeParcelObject) {
         pool = sOwnedPool;
      } else {
         this.mNativePtr = 0L;
         pool = sHolderPool;
      }

      synchronized(pool) {
         for(int i = 0; i < 6; ++i) {
            if (pool[i] == null) {
               pool[i] = this;
               return;
            }
         }

      }
   }

   public void setReadWriteHelper(ReadWriteHelper helper) {
      this.mReadWriteHelper = helper != null ? helper : android.os.Parcel.ReadWriteHelper.DEFAULT;
   }

   public boolean hasReadWriteHelper() {
      return this.mReadWriteHelper != null && this.mReadWriteHelper != android.os.Parcel.ReadWriteHelper.DEFAULT;
   }

   @UnsupportedAppUsage
   public static native long getGlobalAllocSize();

   @UnsupportedAppUsage
   public static native long getGlobalAllocCount();

   public final int dataSize() {
      return nativeDataSize(this.mNativePtr);
   }

   public final int dataAvail() {
      return nativeDataAvail(this.mNativePtr);
   }

   public final int dataPosition() {
      return nativeDataPosition(this.mNativePtr);
   }

   public final int dataCapacity() {
      return nativeDataCapacity(this.mNativePtr);
   }

   public final void setDataSize(int size) {
      this.updateNativeSize(nativeSetDataSize(this.mNativePtr, size));
   }

   public final void setDataPosition(int pos) {
      nativeSetDataPosition(this.mNativePtr, pos);
   }

   public final void setDataCapacity(int size) {
      nativeSetDataCapacity(this.mNativePtr, size);
   }

   public final boolean pushAllowFds(boolean allowFds) {
      return nativePushAllowFds(this.mNativePtr, allowFds);
   }

   public final void restoreAllowFds(boolean lastValue) {
      nativeRestoreAllowFds(this.mNativePtr, lastValue);
   }

   public final byte[] marshall() {
      return nativeMarshall(this.mNativePtr);
   }

   public final void unmarshall(byte[] data, int offset, int length) {
      this.updateNativeSize(nativeUnmarshall(this.mNativePtr, data, offset, length));
   }

   public final void appendFrom(Parcel parcel, int offset, int length) {
      this.updateNativeSize(nativeAppendFrom(this.mNativePtr, parcel.mNativePtr, offset, length));
   }

   public final int compareData(Parcel other) {
      return nativeCompareData(this.mNativePtr, other.mNativePtr);
   }

   public final void setClassCookie(Class clz, Object cookie) {
      if (this.mClassCookies == null) {
         this.mClassCookies = new ArrayMap();
      }

      this.mClassCookies.put(clz, cookie);
   }

   public final Object getClassCookie(Class clz) {
      return this.mClassCookies != null ? this.mClassCookies.get(clz) : null;
   }

   public final void adoptClassCookies(Parcel from) {
      this.mClassCookies = from.mClassCookies;
   }

   public Map copyClassCookies() {
      return new ArrayMap(this.mClassCookies);
   }

   public void putClassCookies(Map cookies) {
      if (cookies != null) {
         if (this.mClassCookies == null) {
            this.mClassCookies = new ArrayMap();
         }

         this.mClassCookies.putAll(cookies);
      }
   }

   public final boolean hasFileDescriptors() {
      return nativeHasFileDescriptors(this.mNativePtr);
   }

   public final void writeInterfaceToken(String interfaceName) {
      nativeWriteInterfaceToken(this.mNativePtr, interfaceName);
   }

   public final void enforceInterface(String interfaceName) {
      nativeEnforceInterface(this.mNativePtr, interfaceName);
   }

   public boolean replaceCallingWorkSourceUid(int workSourceUid) {
      return nativeReplaceCallingWorkSourceUid(this.mNativePtr, workSourceUid);
   }

   public int readCallingWorkSourceUid() {
      return nativeReadCallingWorkSourceUid(this.mNativePtr);
   }

   public final void writeByteArray(byte[] b) {
      this.writeByteArray(b, 0, b != null ? b.length : 0);
   }

   public final void writeByteArray(byte[] b, int offset, int len) {
      if (b == null) {
         this.writeInt(-1);
      } else {
         ArrayUtils.throwsIfOutOfBounds(b.length, offset, len);
         nativeWriteByteArray(this.mNativePtr, b, offset, len);
      }
   }

   @UnsupportedAppUsage
   public final void writeBlob(byte[] b) {
      this.writeBlob(b, 0, b != null ? b.length : 0);
   }

   public final void writeBlob(byte[] b, int offset, int len) {
      if (b == null) {
         this.writeInt(-1);
      } else {
         ArrayUtils.throwsIfOutOfBounds(b.length, offset, len);
         nativeWriteBlob(this.mNativePtr, b, offset, len);
      }
   }

   public final void writeInt(int val) {
      nativeWriteInt(this.mNativePtr, val);
   }

   public final void writeLong(long val) {
      nativeWriteLong(this.mNativePtr, val);
   }

   public final void writeFloat(float val) {
      nativeWriteFloat(this.mNativePtr, val);
   }

   public final void writeDouble(double val) {
      nativeWriteDouble(this.mNativePtr, val);
   }

   public final void writeString(String val) {
      this.mReadWriteHelper.writeString(this, val);
   }

   public void writeStringNoHelper(String val) {
      nativeWriteString(this.mNativePtr, val);
   }

   public final void writeBoolean(boolean val) {
      this.writeInt(val ? 1 : 0);
   }

   @UnsupportedAppUsage
   public final void writeCharSequence(CharSequence val) {
      TextUtils.writeToParcel(val, this, 0);
   }

   public final void writeStrongBinder(IBinder val) {
      nativeWriteStrongBinder(this.mNativePtr, val);
   }

   public final void writeStrongInterface(IInterface val) {
      this.writeStrongBinder(val == null ? null : val.asBinder());
   }

   public final void writeFileDescriptor(FileDescriptor val) {
      this.updateNativeSize(nativeWriteFileDescriptor(this.mNativePtr, val));
   }

   private void updateNativeSize(long newNativeSize) {
      if (this.mOwnsNativeParcelObject) {
         if (newNativeSize > 2147483647L) {
            newNativeSize = 2147483647L;
         }

         if (newNativeSize != this.mNativeSize) {
            int delta = (int)(newNativeSize - this.mNativeSize);
            if (delta > 0) {
               VMRuntime.getRuntime().registerNativeAllocation(delta);
            } else {
               VMRuntime.getRuntime().registerNativeFree(-delta);
            }

            this.mNativeSize = newNativeSize;
         }
      }

   }

   public final void writeRawFileDescriptor(FileDescriptor val) {
      nativeWriteFileDescriptor(this.mNativePtr, val);
   }

   public final void writeRawFileDescriptorArray(FileDescriptor[] value) {
      if (value != null) {
         int N = value.length;
         this.writeInt(N);

         for(int i = 0; i < N; ++i) {
            this.writeRawFileDescriptor(value[i]);
         }
      } else {
         this.writeInt(-1);
      }

   }

   public final void writeByte(byte val) {
      this.writeInt(val);
   }

   public final void writeMap(Map val) {
      this.writeMapInternal(val);
   }

   void writeMapInternal(Map val) {
      if (val == null) {
         this.writeInt(-1);
      } else {
         Set<Map.Entry<String, Object>> entries = val.entrySet();
         int size = entries.size();
         this.writeInt(size);

         for(Map.Entry e : entries) {
            this.writeValue(e.getKey());
            this.writeValue(e.getValue());
            --size;
         }

         if (size != 0) {
            throw new BadParcelableException("Map size does not match number of entries!");
         }
      }
   }

   void writeArrayMapInternal(ArrayMap val) {
      if (val == null) {
         this.writeInt(-1);
      } else {
         int N = val.size();
         this.writeInt(N);

         for(int i = 0; i < N; ++i) {
            this.writeString((String)val.keyAt(i));
            this.writeValue(val.valueAt(i));
         }

      }
   }

   @UnsupportedAppUsage
   public void writeArrayMap(ArrayMap val) {
      this.writeArrayMapInternal(val);
   }

   public void writeTypedArrayMap(ArrayMap val, int parcelableFlags) {
      if (val == null) {
         this.writeInt(-1);
      } else {
         int count = val.size();
         this.writeInt(count);

         for(int i = 0; i < count; ++i) {
            this.writeString((String)val.keyAt(i));
            this.writeTypedObject((Parcelable)val.valueAt(i), parcelableFlags);
         }

      }
   }

   @UnsupportedAppUsage
   public void writeArraySet(ArraySet val) {
      int size = val != null ? val.size() : -1;
      this.writeInt(size);

      for(int i = 0; i < size; ++i) {
         this.writeValue(val.valueAt(i));
      }

   }

   public final void writeBundle(Bundle val) {
      if (val == null) {
         this.writeInt(-1);
      } else {
         val.writeToParcel(this, 0);
      }
   }

   public final void writePersistableBundle(PersistableBundle val) {
      if (val == null) {
         this.writeInt(-1);
      } else {
         val.writeToParcel(this, 0);
      }
   }

   public final void writeSize(Size val) {
      this.writeInt(val.getWidth());
      this.writeInt(val.getHeight());
   }

   public final void writeSizeF(SizeF val) {
      this.writeFloat(val.getWidth());
      this.writeFloat(val.getHeight());
   }

   public final void writeList(List val) {
      if (val == null) {
         this.writeInt(-1);
      } else {
         int N = val.size();
         int i = 0;
         this.writeInt(N);

         while(i < N) {
            this.writeValue(val.get(i));
            ++i;
         }

      }
   }

   public final void writeArray(Object[] val) {
      if (val == null) {
         this.writeInt(-1);
      } else {
         int N = val.length;
         int i = 0;
         this.writeInt(N);

         while(i < N) {
            this.writeValue(val[i]);
            ++i;
         }

      }
   }

   public final void writeSparseArray(SparseArray val) {
      if (val == null) {
         this.writeInt(-1);
      } else {
         int N = val.size();
         this.writeInt(N);

         for(int i = 0; i < N; ++i) {
            this.writeInt(val.keyAt(i));
            this.writeValue(val.valueAt(i));
         }

      }
   }

   public final void writeSparseBooleanArray(SparseBooleanArray val) {
      if (val == null) {
         this.writeInt(-1);
      } else {
         int N = val.size();
         this.writeInt(N);

         for(int i = 0; i < N; ++i) {
            this.writeInt(val.keyAt(i));
            this.writeByte((byte)(val.valueAt(i) ? 1 : 0));
         }

      }
   }

   public final void writeSparseIntArray(SparseIntArray val) {
      if (val == null) {
         this.writeInt(-1);
      } else {
         int N = val.size();
         this.writeInt(N);

         for(int i = 0; i < N; ++i) {
            this.writeInt(val.keyAt(i));
            this.writeInt(val.valueAt(i));
         }

      }
   }

   public final void writeBooleanArray(boolean[] val) {
      if (val != null) {
         int N = val.length;
         this.writeInt(N);

         for(int i = 0; i < N; ++i) {
            this.writeInt(val[i] ? 1 : 0);
         }
      } else {
         this.writeInt(-1);
      }

   }

   public final boolean[] createBooleanArray() {
      int N = this.readInt();
      if (N >= 0 && N <= this.dataAvail() >> 2) {
         boolean[] val = new boolean[N];

         for(int i = 0; i < N; ++i) {
            val[i] = this.readInt() != 0;
         }

         return val;
      } else {
         return null;
      }
   }

   public final void readBooleanArray(boolean[] val) {
      int N = this.readInt();
      if (N == val.length) {
         for(int i = 0; i < N; ++i) {
            val[i] = this.readInt() != 0;
         }

      } else {
         throw new RuntimeException("bad array lengths");
      }
   }

   public final void writeCharArray(char[] val) {
      if (val != null) {
         int N = val.length;
         this.writeInt(N);

         for(int i = 0; i < N; ++i) {
            this.writeInt(val[i]);
         }
      } else {
         this.writeInt(-1);
      }

   }

   public final char[] createCharArray() {
      int N = this.readInt();
      if (N >= 0 && N <= this.dataAvail() >> 2) {
         char[] val = new char[N];

         for(int i = 0; i < N; ++i) {
            val[i] = (char)this.readInt();
         }

         return val;
      } else {
         return null;
      }
   }

   public final void readCharArray(char[] val) {
      int N = this.readInt();
      if (N != val.length) {
         throw new RuntimeException("bad array lengths");
      } else {
         for(int i = 0; i < N; ++i) {
            val[i] = (char)this.readInt();
         }

      }
   }

   public final void writeIntArray(int[] val) {
      if (val != null) {
         int N = val.length;
         this.writeInt(N);

         for(int i = 0; i < N; ++i) {
            this.writeInt(val[i]);
         }
      } else {
         this.writeInt(-1);
      }

   }

   public final int[] createIntArray() {
      int N = this.readInt();
      if (N >= 0 && N <= this.dataAvail() >> 2) {
         int[] val = new int[N];

         for(int i = 0; i < N; ++i) {
            val[i] = this.readInt();
         }

         return val;
      } else {
         return null;
      }
   }

   public final void readIntArray(int[] val) {
      int N = this.readInt();
      if (N != val.length) {
         throw new RuntimeException("bad array lengths");
      } else {
         for(int i = 0; i < N; ++i) {
            val[i] = this.readInt();
         }

      }
   }

   public final void writeLongArray(long[] val) {
      if (val != null) {
         int N = val.length;
         this.writeInt(N);

         for(int i = 0; i < N; ++i) {
            this.writeLong(val[i]);
         }
      } else {
         this.writeInt(-1);
      }

   }

   public final long[] createLongArray() {
      int N = this.readInt();
      if (N >= 0 && N <= this.dataAvail() >> 3) {
         long[] val = new long[N];

         for(int i = 0; i < N; ++i) {
            val[i] = this.readLong();
         }

         return val;
      } else {
         return null;
      }
   }

   public final void readLongArray(long[] val) {
      int N = this.readInt();
      if (N != val.length) {
         throw new RuntimeException("bad array lengths");
      } else {
         for(int i = 0; i < N; ++i) {
            val[i] = this.readLong();
         }

      }
   }

   public final void writeFloatArray(float[] val) {
      if (val != null) {
         int N = val.length;
         this.writeInt(N);

         for(int i = 0; i < N; ++i) {
            this.writeFloat(val[i]);
         }
      } else {
         this.writeInt(-1);
      }

   }

   public final float[] createFloatArray() {
      int N = this.readInt();
      if (N >= 0 && N <= this.dataAvail() >> 2) {
         float[] val = new float[N];

         for(int i = 0; i < N; ++i) {
            val[i] = this.readFloat();
         }

         return val;
      } else {
         return null;
      }
   }

   public final void readFloatArray(float[] val) {
      int N = this.readInt();
      if (N != val.length) {
         throw new RuntimeException("bad array lengths");
      } else {
         for(int i = 0; i < N; ++i) {
            val[i] = this.readFloat();
         }

      }
   }

   public final void writeDoubleArray(double[] val) {
      if (val != null) {
         int N = val.length;
         this.writeInt(N);

         for(int i = 0; i < N; ++i) {
            this.writeDouble(val[i]);
         }
      } else {
         this.writeInt(-1);
      }

   }

   public final double[] createDoubleArray() {
      int N = this.readInt();
      if (N >= 0 && N <= this.dataAvail() >> 3) {
         double[] val = new double[N];

         for(int i = 0; i < N; ++i) {
            val[i] = this.readDouble();
         }

         return val;
      } else {
         return null;
      }
   }

   public final void readDoubleArray(double[] val) {
      int N = this.readInt();
      if (N != val.length) {
         throw new RuntimeException("bad array lengths");
      } else {
         for(int i = 0; i < N; ++i) {
            val[i] = this.readDouble();
         }

      }
   }

   public final void writeStringArray(String[] val) {
      if (val != null) {
         int N = val.length;
         this.writeInt(N);

         for(int i = 0; i < N; ++i) {
            this.writeString(val[i]);
         }
      } else {
         this.writeInt(-1);
      }

   }

   public final String[] createStringArray() {
      int N = this.readInt();
      if (N < 0) {
         return null;
      } else {
         String[] val = new String[N];

         for(int i = 0; i < N; ++i) {
            val[i] = this.readString();
         }

         return val;
      }
   }

   public final void readStringArray(String[] val) {
      int N = this.readInt();
      if (N != val.length) {
         throw new RuntimeException("bad array lengths");
      } else {
         for(int i = 0; i < N; ++i) {
            val[i] = this.readString();
         }

      }
   }

   public final void writeBinderArray(IBinder[] val) {
      if (val != null) {
         int N = val.length;
         this.writeInt(N);

         for(int i = 0; i < N; ++i) {
            this.writeStrongBinder(val[i]);
         }
      } else {
         this.writeInt(-1);
      }

   }

   public final void writeCharSequenceArray(CharSequence[] val) {
      if (val != null) {
         int N = val.length;
         this.writeInt(N);

         for(int i = 0; i < N; ++i) {
            this.writeCharSequence(val[i]);
         }
      } else {
         this.writeInt(-1);
      }

   }

   public final void writeCharSequenceList(ArrayList val) {
      if (val != null) {
         int N = val.size();
         this.writeInt(N);

         for(int i = 0; i < N; ++i) {
            this.writeCharSequence((CharSequence)val.get(i));
         }
      } else {
         this.writeInt(-1);
      }

   }

   public final IBinder[] createBinderArray() {
      int N = this.readInt();
      if (N < 0) {
         return null;
      } else {
         IBinder[] val = new IBinder[N];

         for(int i = 0; i < N; ++i) {
            val[i] = this.readStrongBinder();
         }

         return val;
      }
   }

   public final void readBinderArray(IBinder[] val) {
      int N = this.readInt();
      if (N != val.length) {
         throw new RuntimeException("bad array lengths");
      } else {
         for(int i = 0; i < N; ++i) {
            val[i] = this.readStrongBinder();
         }

      }
   }

   public final void writeTypedList(List val) {
      this.writeTypedList(val, 0);
   }

   public final void writeTypedSparseArray(SparseArray val, int parcelableFlags) {
      if (val == null) {
         this.writeInt(-1);
      } else {
         int count = val.size();
         this.writeInt(count);

         for(int i = 0; i < count; ++i) {
            this.writeInt(val.keyAt(i));
            this.writeTypedObject((Parcelable)val.valueAt(i), parcelableFlags);
         }

      }
   }

   public void writeTypedList(List val, int parcelableFlags) {
      if (val == null) {
         this.writeInt(-1);
      } else {
         int N = val.size();
         int i = 0;
         this.writeInt(N);

         while(i < N) {
            this.writeTypedObject((Parcelable)val.get(i), parcelableFlags);
            ++i;
         }

      }
   }

   public final void writeStringList(List val) {
      if (val == null) {
         this.writeInt(-1);
      } else {
         int N = val.size();
         int i = 0;
         this.writeInt(N);

         while(i < N) {
            this.writeString((String)val.get(i));
            ++i;
         }

      }
   }

   public final void writeBinderList(List val) {
      if (val == null) {
         this.writeInt(-1);
      } else {
         int N = val.size();
         int i = 0;
         this.writeInt(N);

         while(i < N) {
            this.writeStrongBinder((IBinder)val.get(i));
            ++i;
         }

      }
   }

   public final void writeParcelableList(List val, int flags) {
      if (val == null) {
         this.writeInt(-1);
      } else {
         int N = val.size();
         int i = 0;
         this.writeInt(N);

         while(i < N) {
            this.writeParcelable((Parcelable)val.get(i), flags);
            ++i;
         }

      }
   }

   public final void writeTypedArray(Parcelable[] val, int parcelableFlags) {
      if (val != null) {
         int N = val.length;
         this.writeInt(N);

         for(int i = 0; i < N; ++i) {
            this.writeTypedObject(val[i], parcelableFlags);
         }
      } else {
         this.writeInt(-1);
      }

   }

   public final void writeTypedObject(Parcelable val, int parcelableFlags) {
      if (val != null) {
         this.writeInt(1);
         val.writeToParcel(this, parcelableFlags);
      } else {
         this.writeInt(0);
      }

   }

   public final void writeValue(Object v) {
      if (v == null) {
         this.writeInt(-1);
      } else if (v instanceof String) {
         this.writeInt(0);
         this.writeString((String)v);
      } else if (v instanceof Integer) {
         this.writeInt(1);
         this.writeInt((Integer)v);
      } else if (v instanceof Map) {
         this.writeInt(2);
         this.writeMap((Map)v);
      } else if (v instanceof Bundle) {
         this.writeInt(3);
         this.writeBundle((Bundle)v);
      } else if (v instanceof PersistableBundle) {
         this.writeInt(25);
         this.writePersistableBundle((PersistableBundle)v);
      } else if (v instanceof Parcelable) {
         this.writeInt(4);
         this.writeParcelable((Parcelable)v, 0);
      } else if (v instanceof Short) {
         this.writeInt(5);
         this.writeInt(((Short)v).intValue());
      } else if (v instanceof Long) {
         this.writeInt(6);
         this.writeLong((Long)v);
      } else if (v instanceof Float) {
         this.writeInt(7);
         this.writeFloat((Float)v);
      } else if (v instanceof Double) {
         this.writeInt(8);
         this.writeDouble((Double)v);
      } else if (v instanceof Boolean) {
         this.writeInt(9);
         this.writeInt((Boolean)v ? 1 : 0);
      } else if (v instanceof CharSequence) {
         this.writeInt(10);
         this.writeCharSequence((CharSequence)v);
      } else if (v instanceof List) {
         this.writeInt(11);
         this.writeList((List)v);
      } else if (v instanceof SparseArray) {
         this.writeInt(12);
         this.writeSparseArray((SparseArray)v);
      } else if (v instanceof boolean[]) {
         this.writeInt(23);
         this.writeBooleanArray((boolean[])v);
      } else if (v instanceof byte[]) {
         this.writeInt(13);
         this.writeByteArray((byte[])v);
      } else if (v instanceof String[]) {
         this.writeInt(14);
         this.writeStringArray((String[])v);
      } else if (v instanceof CharSequence[]) {
         this.writeInt(24);
         this.writeCharSequenceArray((CharSequence[])v);
      } else if (v instanceof IBinder) {
         this.writeInt(15);
         this.writeStrongBinder((IBinder)v);
      } else if (v instanceof Parcelable[]) {
         this.writeInt(16);
         this.writeParcelableArray((Parcelable[])v, 0);
      } else if (v instanceof int[]) {
         this.writeInt(18);
         this.writeIntArray((int[])v);
      } else if (v instanceof long[]) {
         this.writeInt(19);
         this.writeLongArray((long[])v);
      } else if (v instanceof Byte) {
         this.writeInt(20);
         this.writeInt((Byte)v);
      } else if (v instanceof Size) {
         this.writeInt(26);
         this.writeSize((Size)v);
      } else if (v instanceof SizeF) {
         this.writeInt(27);
         this.writeSizeF((SizeF)v);
      } else if (v instanceof double[]) {
         this.writeInt(28);
         this.writeDoubleArray((double[])v);
      } else {
         Class<?> clazz = v.getClass();
         if (clazz.isArray() && clazz.getComponentType() == Object.class) {
            this.writeInt(17);
            this.writeArray(v);
         } else {
            if (!(v instanceof Serializable)) {
               throw new RuntimeException("Parcel: unable to marshal value " + v);
            }

            this.writeInt(21);
            this.writeSerializable((Serializable)v);
         }
      }

   }

   public final void writeParcelable(Parcelable p, int parcelableFlags) {
      if (p == null) {
         this.writeString((String)null);
      } else {
         this.writeParcelableCreator(p);
         p.writeToParcel(this, parcelableFlags);
      }
   }

   @UnsupportedAppUsage
   public final void writeParcelableCreator(Parcelable p) {
      String name = p.getClass().getName();
      this.writeString(name);
   }

   public final void writeSerializable(Serializable s) {
      if (s == null) {
         this.writeString((String)null);
      } else {
         String name = s.getClass().getName();
         this.writeString(name);
         ByteArrayOutputStream baos = new ByteArrayOutputStream();

         try {
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(s);
            oos.close();
            this.writeByteArray(baos.toByteArray());
         } catch (IOException ioe) {
            throw new RuntimeException("Parcelable encountered IOException writing serializable object (name = " + name + ")", ioe);
         }
      }
   }

   public static void setStackTraceParceling(boolean enabled) {
      sParcelExceptionStackTrace = enabled;
   }

   public final void writeException(Exception e) {
      int code = 0;
      if (e instanceof Parcelable && e.getClass().getClassLoader() == Parcelable.class.getClassLoader()) {
         code = -9;
      } else if (e instanceof SecurityException) {
         code = -1;
      } else if (e instanceof BadParcelableException) {
         code = -2;
      } else if (e instanceof IllegalArgumentException) {
         code = -3;
      } else if (e instanceof NullPointerException) {
         code = -4;
      } else if (e instanceof IllegalStateException) {
         code = -5;
      } else if (e instanceof NetworkOnMainThreadException) {
         code = -6;
      } else if (e instanceof UnsupportedOperationException) {
         code = -7;
      } else if (e instanceof ServiceSpecificException) {
         code = -8;
      }

      this.writeInt(code);
      StrictMode.clearGatheredViolations();
      if (code == 0) {
         if (e instanceof RuntimeException) {
            throw (RuntimeException)e;
         } else {
            throw new RuntimeException(e);
         }
      } else {
         this.writeString(e.getMessage());
         long timeNow = sParcelExceptionStackTrace ? SystemClock.elapsedRealtime() : 0L;
         if (sParcelExceptionStackTrace && timeNow - sLastWriteExceptionStackTrace > 1000L) {
            sLastWriteExceptionStackTrace = timeNow;
            int sizePosition = this.dataPosition();
            this.writeInt(0);
            StackTraceElement[] stackTrace = e.getStackTrace();
            int truncatedSize = Math.min(stackTrace.length, 5);
            StringBuilder sb = new StringBuilder();

            for(int i = 0; i < truncatedSize; ++i) {
               sb.append("\tat ").append(stackTrace[i]).append('\n');
            }

            this.writeString(sb.toString());
            int payloadPosition = this.dataPosition();
            this.setDataPosition(sizePosition);
            this.writeInt(payloadPosition - sizePosition);
            this.setDataPosition(payloadPosition);
         } else {
            this.writeInt(0);
         }

         switch (code) {
            case -9:
               int sizePosition = this.dataPosition();
               this.writeInt(0);
               this.writeParcelable((Parcelable)e, 1);
               int payloadPosition = this.dataPosition();
               this.setDataPosition(sizePosition);
               this.writeInt(payloadPosition - sizePosition);
               this.setDataPosition(payloadPosition);
               break;
            case -8:
               this.writeInt(((ServiceSpecificException)e).errorCode);
         }

      }
   }

   public final void writeNoException() {
      if (StrictMode.hasGatheredViolations()) {
         this.writeInt(-128);
         int sizePosition = this.dataPosition();
         this.writeInt(0);
         StrictMode.writeGatheredViolationsToParcel(this);
         int payloadPosition = this.dataPosition();
         this.setDataPosition(sizePosition);
         this.writeInt(payloadPosition - sizePosition);
         this.setDataPosition(payloadPosition);
      } else {
         this.writeInt(0);
      }

   }

   public final void readException() {
      int code = this.readExceptionCode();
      if (code != 0) {
         String msg = this.readString();
         this.readException(code, msg);
      }

   }

   @UnsupportedAppUsage
   public final int readExceptionCode() {
      int code = this.readInt();
      if (code == -128) {
         int headerSize = this.readInt();
         if (headerSize == 0) {
            Log.e("Parcel", "Unexpected zero-sized Parcel reply header.");
         } else {
            StrictMode.readAndHandleBinderCallViolations(this);
         }

         return 0;
      } else {
         return code;
      }
   }

   public final void readException(int code, String msg) {
      String remoteStackTrace = null;
      int remoteStackPayloadSize = this.readInt();
      if (remoteStackPayloadSize > 0) {
         remoteStackTrace = this.readString();
      }

      Exception e = this.createException(code, msg);
      if (remoteStackTrace != null) {
         RemoteException cause = new RemoteException("Remote stack trace:\n" + remoteStackTrace, (Throwable)null, false, false);

         try {
            Throwable rootCause = ExceptionUtils.getRootCause(e);
            if (rootCause != null) {
               rootCause.initCause(cause);
            }
         } catch (RuntimeException ex) {
            Log.e("Parcel", "Cannot set cause " + cause + " for " + e, ex);
         }
      }

      SneakyThrow.sneakyThrow(e);
   }

   private Exception createException(int code, String msg) {
      switch (code) {
         case -9:
            if (this.readInt() > 0) {
               return (Exception)this.readParcelable(Parcelable.class.getClassLoader());
            }

            return new RuntimeException(msg + " [missing Parcelable]");
         case -8:
            return new ServiceSpecificException(this.readInt(), msg);
         case -7:
            return new UnsupportedOperationException(msg);
         case -6:
            return new NetworkOnMainThreadException();
         case -5:
            return new IllegalStateException(msg);
         case -4:
            return new NullPointerException(msg);
         case -3:
            return new IllegalArgumentException(msg);
         case -2:
            return new BadParcelableException(msg);
         case -1:
            return new SecurityException(msg);
         default:
            return new RuntimeException("Unknown exception code: " + code + " msg " + msg);
      }
   }

   public final int readInt() {
      return nativeReadInt(this.mNativePtr);
   }

   public final long readLong() {
      return nativeReadLong(this.mNativePtr);
   }

   public final float readFloat() {
      return nativeReadFloat(this.mNativePtr);
   }

   public final double readDouble() {
      return nativeReadDouble(this.mNativePtr);
   }

   public final String readString() {
      return this.mReadWriteHelper.readString(this);
   }

   public String readStringNoHelper() {
      return nativeReadString(this.mNativePtr);
   }

   public final boolean readBoolean() {
      return this.readInt() != 0;
   }

   @UnsupportedAppUsage
   public final CharSequence readCharSequence() {
      return (CharSequence)TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(this);
   }

   public final IBinder readStrongBinder() {
      return nativeReadStrongBinder(this.mNativePtr);
   }

   public final ParcelFileDescriptor readFileDescriptor() {
      FileDescriptor fd = nativeReadFileDescriptor(this.mNativePtr);
      return fd != null ? new ParcelFileDescriptor(fd) : null;
   }

   @UnsupportedAppUsage
   public final FileDescriptor readRawFileDescriptor() {
      return nativeReadFileDescriptor(this.mNativePtr);
   }

   public final FileDescriptor[] createRawFileDescriptorArray() {
      int N = this.readInt();
      if (N < 0) {
         return null;
      } else {
         FileDescriptor[] f = new FileDescriptor[N];

         for(int i = 0; i < N; ++i) {
            f[i] = this.readRawFileDescriptor();
         }

         return f;
      }
   }

   public final void readRawFileDescriptorArray(FileDescriptor[] val) {
      int N = this.readInt();
      if (N != val.length) {
         throw new RuntimeException("bad array lengths");
      } else {
         for(int i = 0; i < N; ++i) {
            val[i] = this.readRawFileDescriptor();
         }

      }
   }

   public final byte readByte() {
      return (byte)(this.readInt() & 255);
   }

   public final void readMap(Map outVal, ClassLoader loader) {
      int N = this.readInt();
      this.readMapInternal(outVal, N, loader);
   }

   public final void readList(List outVal, ClassLoader loader) {
      int N = this.readInt();
      this.readListInternal(outVal, N, loader);
   }

   public final HashMap readHashMap(ClassLoader loader) {
      int N = this.readInt();
      if (N < 0) {
         return null;
      } else {
         HashMap m = new HashMap(N);
         this.readMapInternal(m, N, loader);
         return m;
      }
   }

   public final Bundle readBundle() {
      return this.readBundle((ClassLoader)null);
   }

   public final Bundle readBundle(ClassLoader loader) {
      int length = this.readInt();
      if (length < 0) {
         return null;
      } else {
         Bundle bundle = new Bundle(this, length);
         if (loader != null) {
            bundle.setClassLoader(loader);
         }

         return bundle;
      }
   }

   public final PersistableBundle readPersistableBundle() {
      return this.readPersistableBundle((ClassLoader)null);
   }

   public final PersistableBundle readPersistableBundle(ClassLoader loader) {
      int length = this.readInt();
      if (length < 0) {
         return null;
      } else {
         PersistableBundle bundle = new PersistableBundle(this, length);
         if (loader != null) {
            bundle.setClassLoader(loader);
         }

         return bundle;
      }
   }

   public final Size readSize() {
      int width = this.readInt();
      int height = this.readInt();
      return new Size(width, height);
   }

   public final SizeF readSizeF() {
      float width = this.readFloat();
      float height = this.readFloat();
      return new SizeF(width, height);
   }

   public final byte[] createByteArray() {
      return nativeCreateByteArray(this.mNativePtr);
   }

   public final void readByteArray(byte[] val) {
      boolean valid = nativeReadByteArray(this.mNativePtr, val, val != null ? val.length : 0);
      if (!valid) {
         throw new RuntimeException("bad array lengths");
      }
   }

   @UnsupportedAppUsage
   public final byte[] readBlob() {
      return nativeReadBlob(this.mNativePtr);
   }

   @UnsupportedAppUsage
   public final String[] readStringArray() {
      String[] array = null;
      int length = this.readInt();
      if (length >= 0) {
         array = new String[length];

         for(int i = 0; i < length; ++i) {
            array[i] = this.readString();
         }
      }

      return array;
   }

   public final CharSequence[] readCharSequenceArray() {
      CharSequence[] array = null;
      int length = this.readInt();
      if (length >= 0) {
         array = new CharSequence[length];

         for(int i = 0; i < length; ++i) {
            array[i] = this.readCharSequence();
         }
      }

      return array;
   }

   public final ArrayList readCharSequenceList() {
      ArrayList<CharSequence> array = null;
      int length = this.readInt();
      if (length >= 0) {
         array = new ArrayList(length);

         for(int i = 0; i < length; ++i) {
            array.add(this.readCharSequence());
         }
      }

      return array;
   }

   public final ArrayList readArrayList(ClassLoader loader) {
      int N = this.readInt();
      if (N < 0) {
         return null;
      } else {
         ArrayList l = new ArrayList(N);
         this.readListInternal(l, N, loader);
         return l;
      }
   }

   public final Object[] readArray(ClassLoader loader) {
      int N = this.readInt();
      if (N < 0) {
         return null;
      } else {
         Object[] l = new Object[N];
         this.readArrayInternal(l, N, loader);
         return l;
      }
   }

   public final SparseArray readSparseArray(ClassLoader loader) {
      int N = this.readInt();
      if (N < 0) {
         return null;
      } else {
         SparseArray sa = new SparseArray(N);
         this.readSparseArrayInternal(sa, N, loader);
         return sa;
      }
   }

   public final SparseBooleanArray readSparseBooleanArray() {
      int N = this.readInt();
      if (N < 0) {
         return null;
      } else {
         SparseBooleanArray sa = new SparseBooleanArray(N);
         this.readSparseBooleanArrayInternal(sa, N);
         return sa;
      }
   }

   public final SparseIntArray readSparseIntArray() {
      int N = this.readInt();
      if (N < 0) {
         return null;
      } else {
         SparseIntArray sa = new SparseIntArray(N);
         this.readSparseIntArrayInternal(sa, N);
         return sa;
      }
   }

   public final ArrayList createTypedArrayList(Parcelable.Creator c) {
      int N = this.readInt();
      if (N < 0) {
         return null;
      } else {
         ArrayList<T> l;
         for(l = new ArrayList(N); N > 0; --N) {
            l.add(this.readTypedObject(c));
         }

         return l;
      }
   }

   public final void readTypedList(List list, Parcelable.Creator c) {
      int M = list.size();
      int N = this.readInt();

      int i;
      for(i = 0; i < M && i < N; ++i) {
         list.set(i, this.readTypedObject(c));
      }

      while(i < N) {
         list.add(this.readTypedObject(c));
         ++i;
      }

      while(i < M) {
         list.remove(N);
         ++i;
      }

   }

   public final SparseArray createTypedSparseArray(Parcelable.Creator creator) {
      int count = this.readInt();
      if (count < 0) {
         return null;
      } else {
         SparseArray<T> array = new SparseArray(count);

         for(int i = 0; i < count; ++i) {
            int index = this.readInt();
            T value = (T)((Parcelable)this.readTypedObject(creator));
            array.append(index, value);
         }

         return array;
      }
   }

   public final ArrayMap createTypedArrayMap(Parcelable.Creator creator) {
      int count = this.readInt();
      if (count < 0) {
         return null;
      } else {
         ArrayMap<String, T> map = new ArrayMap(count);

         for(int i = 0; i < count; ++i) {
            String key = this.readString();
            T value = (T)((Parcelable)this.readTypedObject(creator));
            map.append(key, value);
         }

         return map;
      }
   }

   public final ArrayList createStringArrayList() {
      int N = this.readInt();
      if (N < 0) {
         return null;
      } else {
         ArrayList<String> l;
         for(l = new ArrayList(N); N > 0; --N) {
            l.add(this.readString());
         }

         return l;
      }
   }

   public final ArrayList createBinderArrayList() {
      int N = this.readInt();
      if (N < 0) {
         return null;
      } else {
         ArrayList<IBinder> l;
         for(l = new ArrayList(N); N > 0; --N) {
            l.add(this.readStrongBinder());
         }

         return l;
      }
   }

   public final void readStringList(List list) {
      int M = list.size();
      int N = this.readInt();

      int i;
      for(i = 0; i < M && i < N; ++i) {
         list.set(i, this.readString());
      }

      while(i < N) {
         list.add(this.readString());
         ++i;
      }

      while(i < M) {
         list.remove(N);
         ++i;
      }

   }

   public final void readBinderList(List list) {
      int M = list.size();
      int N = this.readInt();

      int i;
      for(i = 0; i < M && i < N; ++i) {
         list.set(i, this.readStrongBinder());
      }

      while(i < N) {
         list.add(this.readStrongBinder());
         ++i;
      }

      while(i < M) {
         list.remove(N);
         ++i;
      }

   }

   public final List readParcelableList(List list, ClassLoader cl) {
      int N = this.readInt();
      if (N == -1) {
         list.clear();
         return list;
      } else {
         int M = list.size();

         int i;
         for(i = 0; i < M && i < N; ++i) {
            list.set(i, this.readParcelable(cl));
         }

         while(i < N) {
            list.add(this.readParcelable(cl));
            ++i;
         }

         while(i < M) {
            list.remove(N);
            ++i;
         }

         return list;
      }
   }

   public final Object[] createTypedArray(Parcelable.Creator c) {
      int N = this.readInt();
      if (N < 0) {
         return null;
      } else {
         T[] l = (T[])c.newArray(N);

         for(int i = 0; i < N; ++i) {
            l[i] = this.readTypedObject(c);
         }

         return l;
      }
   }

   public final void readTypedArray(Object[] val, Parcelable.Creator c) {
      int N = this.readInt();
      if (N != val.length) {
         throw new RuntimeException("bad array lengths");
      } else {
         for(int i = 0; i < N; ++i) {
            val[i] = this.readTypedObject(c);
         }

      }
   }

   /** @deprecated */
   @Deprecated
   public final Object[] readTypedArray(Parcelable.Creator c) {
      return this.createTypedArray(c);
   }

   public final Object readTypedObject(Parcelable.Creator c) {
      return this.readInt() != 0 ? c.createFromParcel(this) : null;
   }

   public final void writeParcelableArray(Parcelable[] value, int parcelableFlags) {
      if (value != null) {
         int N = value.length;
         this.writeInt(N);

         for(int i = 0; i < N; ++i) {
            this.writeParcelable(value[i], parcelableFlags);
         }
      } else {
         this.writeInt(-1);
      }

   }

   public final Object readValue(ClassLoader loader) {
      int type = this.readInt();
      switch (type) {
         case -1:
            return null;
         case 0:
            return this.readString();
         case 1:
            return this.readInt();
         case 2:
            return this.readHashMap(loader);
         case 3:
            return this.readBundle(loader);
         case 4:
            return this.readParcelable(loader);
         case 5:
            return (short)this.readInt();
         case 6:
            return this.readLong();
         case 7:
            return this.readFloat();
         case 8:
            return this.readDouble();
         case 9:
            return this.readInt() == 1;
         case 10:
            return this.readCharSequence();
         case 11:
            return this.readArrayList(loader);
         case 12:
            return this.readSparseArray(loader);
         case 13:
            return this.createByteArray();
         case 14:
            return this.readStringArray();
         case 15:
            return this.readStrongBinder();
         case 16:
            return this.readParcelableArray(loader);
         case 17:
            return this.readArray(loader);
         case 18:
            return this.createIntArray();
         case 19:
            return this.createLongArray();
         case 20:
            return this.readByte();
         case 21:
            return this.readSerializable(loader);
         case 22:
            return this.readSparseBooleanArray();
         case 23:
            return this.createBooleanArray();
         case 24:
            return this.readCharSequenceArray();
         case 25:
            return this.readPersistableBundle(loader);
         case 26:
            return this.readSize();
         case 27:
            return this.readSizeF();
         case 28:
            return this.createDoubleArray();
         default:
            int off = this.dataPosition() - 4;
            throw new RuntimeException("Parcel " + this + ": Unmarshalling unknown type code " + type + " at offset " + off);
      }
   }

   public final Parcelable readParcelable(ClassLoader loader) {
      Parcelable.Creator<?> creator = this.readParcelableCreator(loader);
      if (creator == null) {
         return null;
      } else if (creator instanceof Parcelable.ClassLoaderCreator) {
         Parcelable.ClassLoaderCreator<?> classLoaderCreator = (Parcelable.ClassLoaderCreator)creator;
         return (Parcelable)classLoaderCreator.createFromParcel(this, loader);
      } else {
         return (Parcelable)creator.createFromParcel(this);
      }
   }

   @UnsupportedAppUsage
   public final Parcelable readCreator(Parcelable.Creator creator, ClassLoader loader) {
      if (creator instanceof Parcelable.ClassLoaderCreator) {
         Parcelable.ClassLoaderCreator<?> classLoaderCreator = (Parcelable.ClassLoaderCreator)creator;
         return (Parcelable)classLoaderCreator.createFromParcel(this, loader);
      } else {
         return (Parcelable)creator.createFromParcel(this);
      }
   }

   @UnsupportedAppUsage
   public final Parcelable.Creator readParcelableCreator(ClassLoader loader) {
      String name = this.readString();
      if (name == null) {
         return null;
      } else {
         synchronized(mCreators) {
            HashMap<String, Parcelable.Creator<?>> map = (HashMap)mCreators.get(loader);
            if (map == null) {
               map = new HashMap();
               mCreators.put(loader, map);
            }

            Parcelable.Creator<?> creator = (Parcelable.Creator)map.get(name);
            if (creator == null) {
               try {
                  ClassLoader parcelableClassLoader = loader == null ? this.getClass().getClassLoader() : loader;
                  Class<?> parcelableClass = Class.forName(name, false, parcelableClassLoader);
                  if (!Parcelable.class.isAssignableFrom(parcelableClass)) {
                     throw new BadParcelableException("Parcelable protocol requires subclassing from Parcelable on class " + name);
                  }

                  Field f = parcelableClass.getField("CREATOR");
                  if ((f.getModifiers() & 8) == 0) {
                     throw new BadParcelableException("Parcelable protocol requires the CREATOR object to be static on class " + name);
                  }

                  Class<?> creatorType = f.getType();
                  if (!Parcelable.Creator.class.isAssignableFrom(creatorType)) {
                     throw new BadParcelableException("Parcelable protocol requires a Parcelable.Creator object called CREATOR on class " + name);
                  }

                  creator = (Parcelable.Creator)f.get((Object)null);
               } catch (IllegalAccessException e) {
                  Log.e("Parcel", "Illegal access when unmarshalling: " + name, e);
                  throw new BadParcelableException("IllegalAccessException when unmarshalling: " + name);
               } catch (ClassNotFoundException e) {
                  Log.e("Parcel", "Class not found when unmarshalling: " + name, e);
                  throw new BadParcelableException("ClassNotFoundException when unmarshalling: " + name);
               } catch (NoSuchFieldException var13) {
                  throw new BadParcelableException("Parcelable protocol requires a Parcelable.Creator object called CREATOR on class " + name);
               }

               if (creator == null) {
                  throw new BadParcelableException("Parcelable protocol requires a non-null Parcelable.Creator object called CREATOR on class " + name);
               }

               map.put(name, creator);
            }

            return creator;
         }
      }
   }

   public final Parcelable[] readParcelableArray(ClassLoader loader) {
      int N = this.readInt();
      if (N < 0) {
         return null;
      } else {
         Parcelable[] p = new Parcelable[N];

         for(int i = 0; i < N; ++i) {
            p[i] = this.readParcelable(loader);
         }

         return p;
      }
   }

   public final Parcelable[] readParcelableArray(ClassLoader loader, Class clazz) {
      int N = this.readInt();
      if (N < 0) {
         return null;
      } else {
         T[] p = (T[])((Parcelable[])Array.newInstance(clazz, N));

         for(int i = 0; i < N; ++i) {
            p[i] = this.readParcelable(loader);
         }

         return p;
      }
   }

   public final Serializable readSerializable() {
      return this.readSerializable((ClassLoader)null);
   }

   private final Serializable readSerializable(ClassLoader loader) {
      String name = this.readString();
      if (name == null) {
         return null;
      } else {
         byte[] serializedData = this.createByteArray();
         ByteArrayInputStream bais = new ByteArrayInputStream(serializedData);

         try {
            ObjectInputStream ois = new 2(this, bais, loader);
            return (Serializable)ois.readObject();
         } catch (IOException ioe) {
            throw new RuntimeException("Parcelable encountered IOException reading a Serializable object (name = " + name + ")", ioe);
         } catch (ClassNotFoundException cnfe) {
            throw new RuntimeException("Parcelable encountered ClassNotFoundException reading a Serializable object (name = " + name + ")", cnfe);
         }
      }
   }

   protected static final Parcel obtain(int obj) {
      throw new UnsupportedOperationException();
   }

   protected static final Parcel obtain(long obj) {
      Parcel[] pool = sHolderPool;
      synchronized(pool) {
         for(int i = 0; i < 6; ++i) {
            Parcel p = pool[i];
            if (p != null) {
               pool[i] = null;
               p.init(obj);
               return p;
            }
         }
      }

      return new Parcel(obj);
   }

   private Parcel(long nativePtr) {
      this.mReadWriteHelper = android.os.Parcel.ReadWriteHelper.DEFAULT;
      this.init(nativePtr);
   }

   private void init(long nativePtr) {
      if (nativePtr != 0L) {
         this.mNativePtr = nativePtr;
         this.mOwnsNativeParcelObject = false;
      } else {
         this.mNativePtr = nativeCreate();
         this.mOwnsNativeParcelObject = true;
      }

   }

   private void freeBuffer() {
      if (this.mOwnsNativeParcelObject) {
         this.updateNativeSize(nativeFreeBuffer(this.mNativePtr));
      }

      this.mReadWriteHelper = android.os.Parcel.ReadWriteHelper.DEFAULT;
   }

   private void destroy() {
      if (this.mNativePtr != 0L) {
         if (this.mOwnsNativeParcelObject) {
            nativeDestroy(this.mNativePtr);
            this.updateNativeSize(0L);
         }

         this.mNativePtr = 0L;
      }

      this.mReadWriteHelper = null;
   }

   protected void finalize() throws Throwable {
      this.destroy();
   }

   void readMapInternal(Map outVal, int N, ClassLoader loader) {
      while(N > 0) {
         Object key = this.readValue(loader);
         Object value = this.readValue(loader);
         outVal.put(key, value);
         --N;
      }

   }

   void readArrayMapInternal(ArrayMap outVal, int N, ClassLoader loader) {
      while(N > 0) {
         String key = this.readString();
         Object value = this.readValue(loader);
         outVal.append(key, value);
         --N;
      }

      outVal.validate();
   }

   void readArrayMapSafelyInternal(ArrayMap outVal, int N, ClassLoader loader) {
      while(N > 0) {
         String key = this.readString();
         Object value = this.readValue(loader);
         outVal.put(key, value);
         --N;
      }

   }

   @UnsupportedAppUsage
   public void readArrayMap(ArrayMap outVal, ClassLoader loader) {
      int N = this.readInt();
      if (N >= 0) {
         this.readArrayMapInternal(outVal, N, loader);
      }
   }

   @UnsupportedAppUsage
   public ArraySet readArraySet(ClassLoader loader) {
      int size = this.readInt();
      if (size < 0) {
         return null;
      } else {
         ArraySet<Object> result = new ArraySet(size);

         for(int i = 0; i < size; ++i) {
            Object value = this.readValue(loader);
            result.append(value);
         }

         return result;
      }
   }

   private void readListInternal(List outVal, int N, ClassLoader loader) {
      while(N > 0) {
         Object value = this.readValue(loader);
         outVal.add(value);
         --N;
      }

   }

   private void readArrayInternal(Object[] outVal, int N, ClassLoader loader) {
      for(int i = 0; i < N; ++i) {
         Object value = this.readValue(loader);
         outVal[i] = value;
      }

   }

   private void readSparseArrayInternal(SparseArray outVal, int N, ClassLoader loader) {
      while(N > 0) {
         int key = this.readInt();
         Object value = this.readValue(loader);
         outVal.append(key, value);
         --N;
      }

   }

   private void readSparseBooleanArrayInternal(SparseBooleanArray outVal, int N) {
      while(N > 0) {
         int key = this.readInt();
         boolean value = this.readByte() == 1;
         outVal.append(key, value);
         --N;
      }

   }

   private void readSparseIntArrayInternal(SparseIntArray outVal, int N) {
      while(N > 0) {
         int key = this.readInt();
         int value = this.readInt();
         outVal.append(key, value);
         --N;
      }

   }

   public long getBlobAshmemSize() {
      return nativeGetBlobAshmemSize(this.mNativePtr);
   }

   // $FF: synthetic method
   static long access$000(Parcel x0) {
      return x0.mNativePtr;
   }
}
