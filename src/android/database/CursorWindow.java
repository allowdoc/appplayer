package android.database;

import android.annotation.UnsupportedAppUsage;
import android.content.res.Resources;
import android.database.sqlite.SQLiteClosable;
import android.os.Binder;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.Process;
import android.util.Log;
import android.util.LongSparseArray;
import android.util.SparseIntArray;
import dalvik.system.CloseGuard;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Pure Java in-process CursorWindow implementation.
 * Stores genuine typed row records without requiring native C++ Android runtime.
 */
public class CursorWindow extends SQLiteClosable implements Parcelable {
    private static final String STATS_TAG = "CursorWindowStats";
    @UnsupportedAppUsage
    private static int sCursorWindowSize = -1;
    @UnsupportedAppUsage
    public long mWindowPtr;
    private int mStartPos;
    private final String mName;
    private final CloseGuard mCloseGuard;

    // Pure Java row storage
    private final List<Object[]> mRows = new ArrayList<>();
    private int mNumColumns = 0;

    public static final Parcelable.Creator<CursorWindow> CREATOR = new Parcelable.Creator<CursorWindow>() {
        public CursorWindow createFromParcel(Parcel source) { return new CursorWindow(source); }
        public CursorWindow[] newArray(int size) { return new CursorWindow[size]; }
    };

    @UnsupportedAppUsage
    private static final LongSparseArray<Integer> sWindowToPidMap = new LongSparseArray<>();
    private static long sNextPtr = 1000L;

    public CursorWindow(String name) {
        this(name, (long) getCursorWindowSize());
    }

    public CursorWindow(String name, long windowSizeBytes) {
        this.mCloseGuard = CloseGuard.get();
        this.mStartPos = 0;
        this.mName = (name != null && name.length() != 0) ? name : "<unnamed>";
        synchronized (CursorWindow.class) {
            this.mWindowPtr = ++sNextPtr;
        }
        this.mCloseGuard.open("close");
        this.recordNewWindow(Binder.getCallingPid(), this.mWindowPtr);
    }

    /** @deprecated */
    @Deprecated
    public CursorWindow(boolean localWindow) {
        this((String) null);
    }

    private CursorWindow(Parcel source) {
        this.mCloseGuard = CloseGuard.get();
        this.mStartPos = source.readInt();
        synchronized (CursorWindow.class) {
            this.mWindowPtr = ++sNextPtr;
        }
        this.mName = source.readString();
        this.mCloseGuard.open("close");
    }

    protected void finalize() throws Throwable {
        try {
            if (this.mCloseGuard != null) {
                this.mCloseGuard.warnIfOpen();
            }
            this.dispose();
        } finally {
            super.finalize();
        }
    }

    private void dispose() {
        if (this.mCloseGuard != null) {
            this.mCloseGuard.close();
        }
        if (this.mWindowPtr != 0L) {
            this.recordClosingOfWindow(this.mWindowPtr);
            this.mRows.clear();
            this.mWindowPtr = 0L;
        }
    }

    public String getName() {
        return this.mName;
    }

    public void clear() {
        this.acquireReference();
        try {
            this.mStartPos = 0;
            this.mRows.clear();
        } finally {
            this.releaseReference();
        }
    }

    public int getStartPosition() {
        return this.mStartPos;
    }

    public void setStartPosition(int pos) {
        this.mStartPos = pos;
    }

    public int getNumRows() {
        this.acquireReference();
        try {
            return this.mRows.size();
        } finally {
            this.releaseReference();
        }
    }

    public boolean setNumColumns(int columnNum) {
        this.acquireReference();
        try {
            this.mNumColumns = columnNum;
            return true;
        } finally {
            this.releaseReference();
        }
    }

    public boolean allocRow() {
        this.acquireReference();
        try {
            int cols = mNumColumns > 0 ? mNumColumns : 32;
            this.mRows.add(new Object[cols]);
            return true;
        } finally {
            this.releaseReference();
        }
    }

    public void freeLastRow() {
        this.acquireReference();
        try {
            if (!this.mRows.isEmpty()) {
                this.mRows.remove(this.mRows.size() - 1);
            }
        } finally {
            this.releaseReference();
        }
    }

    private void ensureCell(int row, int col) {
        int targetRow = row - this.mStartPos;
        while (mRows.size() <= targetRow) {
            allocRow();
        }
        Object[] rowData = mRows.get(targetRow);
        if (rowData.length <= col) {
            Object[] expanded = Arrays.copyOf(rowData, Math.max(col + 1, rowData.length * 2));
            mRows.set(targetRow, expanded);
        }
    }

    private Object getCell(int row, int col) {
        int targetRow = row - this.mStartPos;
        if (targetRow < 0 || targetRow >= mRows.size()) return null;
        Object[] rowData = mRows.get(targetRow);
        if (col < 0 || col >= rowData.length) return null;
        return rowData[col];
    }

    @Deprecated
    public boolean isNull(int row, int column) {
        return this.getType(row, column) == Cursor.FIELD_TYPE_NULL;
    }

    @Deprecated
    public boolean isBlob(int row, int column) {
        int type = this.getType(row, column);
        return type == Cursor.FIELD_TYPE_BLOB || type == Cursor.FIELD_TYPE_NULL;
    }

    @Deprecated
    public boolean isLong(int row, int column) {
        return this.getType(row, column) == Cursor.FIELD_TYPE_INTEGER;
    }

    @Deprecated
    public boolean isFloat(int row, int column) {
        return this.getType(row, column) == Cursor.FIELD_TYPE_FLOAT;
    }

    @Deprecated
    public boolean isString(int row, int column) {
        int type = this.getType(row, column);
        return type == Cursor.FIELD_TYPE_STRING || type == Cursor.FIELD_TYPE_NULL;
    }

    public int getType(int row, int column) {
        this.acquireReference();
        try {
            Object val = getCell(row, column);
            if (val == null) return Cursor.FIELD_TYPE_NULL;
            if (val instanceof Integer || val instanceof Long || val instanceof Short || val instanceof Byte) {
                return Cursor.FIELD_TYPE_INTEGER;
            }
            if (val instanceof Float || val instanceof Double) {
                return Cursor.FIELD_TYPE_FLOAT;
            }
            if (val instanceof byte[]) {
                return Cursor.FIELD_TYPE_BLOB;
            }
            return Cursor.FIELD_TYPE_STRING;
        } finally {
            this.releaseReference();
        }
    }

    public byte[] getBlob(int row, int column) {
        this.acquireReference();
        try {
            Object val = getCell(row, column);
            if (val instanceof byte[]) return (byte[]) val;
            if (val != null) return val.toString().getBytes(StandardCharsets.UTF_8);
            return new byte[0];
        } finally {
            this.releaseReference();
        }
    }

    public String getString(int row, int column) {
        this.acquireReference();
        try {
            Object val = getCell(row, column);
            return val != null ? val.toString() : null;
        } finally {
            this.releaseReference();
        }
    }

    public void copyStringToBuffer(int row, int column, CharArrayBuffer buffer) {
        if (buffer == null) throw new IllegalArgumentException("CharArrayBuffer should not be null");
        this.acquireReference();
        try {
            String str = getString(row, column);
            if (str != null) {
                char[] chars = str.toCharArray();
                if (buffer.data == null || buffer.data.length < chars.length) {
                    buffer.data = chars;
                } else {
                    System.arraycopy(chars, 0, buffer.data, 0, chars.length);
                }
                buffer.sizeCopied = chars.length;
            } else {
                buffer.sizeCopied = 0;
            }
        } finally {
            this.releaseReference();
        }
    }

    public long getLong(int row, int column) {
        this.acquireReference();
        try {
            Object val = getCell(row, column);
            if (val == null) return 0L;
            if (val instanceof Number) return ((Number) val).longValue();
            try { return Long.parseLong(val.toString()); } catch (Throwable ignored) { return 0L; }
        } finally {
            this.releaseReference();
        }
    }

    public double getDouble(int row, int column) {
        this.acquireReference();
        try {
            Object val = getCell(row, column);
            if (val == null) return 0.0;
            if (val instanceof Number) return ((Number) val).doubleValue();
            try { return Double.parseDouble(val.toString()); } catch (Throwable ignored) { return 0.0; }
        } finally {
            this.releaseReference();
        }
    }

    public short getShort(int row, int column) {
        return (short) ((int) this.getLong(row, column));
    }

    public int getInt(int row, int column) {
        return (int) this.getLong(row, column);
    }

    public float getFloat(int row, int column) {
        return (float) this.getDouble(row, column);
    }

    public boolean putBlob(byte[] value, int row, int column) {
        this.acquireReference();
        try {
            ensureCell(row, column);
            mRows.get(row - this.mStartPos)[column] = value;
            return true;
        } finally {
            this.releaseReference();
        }
    }

    public boolean putString(String value, int row, int column) {
        this.acquireReference();
        try {
            ensureCell(row, column);
            mRows.get(row - this.mStartPos)[column] = value;
            return true;
        } finally {
            this.releaseReference();
        }
    }

    public boolean putLong(long value, int row, int column) {
        this.acquireReference();
        try {
            ensureCell(row, column);
            mRows.get(row - this.mStartPos)[column] = Long.valueOf(value);
            return true;
        } finally {
            this.releaseReference();
        }
    }

    public boolean putDouble(double value, int row, int column) {
        this.acquireReference();
        try {
            ensureCell(row, column);
            mRows.get(row - this.mStartPos)[column] = Double.valueOf(value);
            return true;
        } finally {
            this.releaseReference();
        }
    }

    public boolean putNull(int row, int column) {
        this.acquireReference();
        try {
            ensureCell(row, column);
            mRows.get(row - this.mStartPos)[column] = null;
            return true;
        } finally {
            this.releaseReference();
        }
    }

    public static CursorWindow newFromParcel(Parcel p) {
        return (CursorWindow) CREATOR.createFromParcel(p);
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        this.acquireReference();
        try {
            dest.writeInt(this.mStartPos);
            dest.writeString(this.mName);
        } finally {
            this.releaseReference();
        }
        if ((flags & 1) != 0) {
            this.releaseReference();
        }
    }

    protected void onAllReferencesReleased() {
        this.dispose();
    }

    private void recordNewWindow(int pid, long window) {
        synchronized (sWindowToPidMap) {
            sWindowToPidMap.put(window, pid);
        }
    }

    private void recordClosingOfWindow(long window) {
        synchronized (sWindowToPidMap) {
            if (sWindowToPidMap.size() != 0) {
                sWindowToPidMap.delete(window);
            }
        }
    }

    private static int getCursorWindowSize() {
        if (sCursorWindowSize < 0) {
            sCursorWindowSize = Resources.getSystem().getInteger(17694764) * 1024;
        }
        return sCursorWindowSize;
    }

    public String toString() {
        return this.getName() + " {" + Long.toHexString(this.mWindowPtr) + ", rows=" + mRows.size() + "}";
    }
}
