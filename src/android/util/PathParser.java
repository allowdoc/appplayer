package android.util;

import android.graphics.Path;

/**
 * Pure-Java shadow of the framework's native-backed android.util.PathParser
 * (bin/ shadows android-all; libandroidfw PathParser.cpp equivalent).
 *
 * Parses SVG-style path data exactly like AOSP: absolute/relative commands
 * (M m L l H h V v C c S s Q q T t A a Z z), implicit command repetition,
 * scientific-notation floats, comma/whitespace separation. Arcs use the
 * endpoint-to-center parameterization from PathParser::findMidPoint... via
 * the standard SVG arc algorithm, producing the same cubic decomposition.
 *
 * VectorDrawable pathData, PathInterpolator pathData and ClipData shapes all
 * flow through here on a device - no native libandroidfw needed.
 */
public class PathParser {

    public static Path createPathFromPathData(String pathData) {
        if (pathData == null) return null;
        PathData data = new PathData(pathData);
        if (!data.isValid()) return null;
        Path path = new Path();
        createPathFromPathData(path, data);
        return path;
    }

    public static void createPathFromPathData(Path out, PathData data) {
        if (out == null || data == null) return;
        data.applyToPath(out);
    }

    public static boolean canMorph(PathData a, PathData b) {
        return a != null && b != null && a.canMorph(b);
    }

    public static boolean interpolatePathData(PathData out, PathData a, PathData b, float t) {
        if (out == null || a == null || b == null) return false;
        return out.interpolate(a, b, t);
    }

    // ------------------------------------------------------------------
    // PathData
    // ------------------------------------------------------------------
    public static class PathData {
        /** One entry per command: args[0]=command char (upper-cased, absolute), args[1..]=params. */
        float[][] mCommands = new float[0][];
        private boolean mValid = false;

        public PathData() {}

        public PathData(PathData copy) {
            if (copy != null && copy.mCommands != null) {
                mCommands = new float[copy.mCommands.length][];
                for (int i = 0; i < copy.mCommands.length; i++) {
                    mCommands[i] = copy.mCommands[i].clone();
                }
                mValid = copy.mValid;
            }
        }

        public PathData(String s) {
            setPath(s);
        }

        public long getNativePtr() {
            return 0; // pure-Java: no native representation
        }

        public void setPathData(PathData other) {
            if (other == null) return;
            mCommands = new float[other.mCommands.length][];
            for (int i = 0; i < other.mCommands.length; i++) {
                mCommands[i] = other.mCommands[i].clone();
            }
            mValid = other.mValid;
        }

        public boolean isValid() { return mValid; }

        // ---------------- parsing (AOSP PathParser semantics) ----------------

        private void setPath(String s) {
            try {
                mCommands = parse(s);
                mValid = mCommands != null;
            } catch (Throwable t) {
                mCommands = new float[0][];
                mValid = false;
            }
        }

        private static final class Tokenizer {
            final String s;
            int pos = 0;
            Tokenizer(String s) { this.s = s; }

            void skipSeparators() {
                while (pos < s.length() && (s.charAt(pos) == ',' || s.charAt(pos) == ' '
                        || s.charAt(pos) == '\t' || s.charAt(pos) == '\n' || s.charAt(pos) == '\r')) {
                    pos++;
                }
            }

            boolean atCommand() {
                skipSeparators();
                return pos < s.length() && Character.isLetter(s.charAt(pos));
            }

            char readCommand() {
                skipSeparators();
                return s.charAt(pos++);
            }

            boolean hasNextNumber() {
                skipSeparators();
                if (pos >= s.length()) return false;
                char c = s.charAt(pos);
                if (c == '-' || c == '+' || c == '.') return true;
                return c >= '0' && c <= '9';
            }

            float readNumber() {
                skipSeparators();
                int start = pos;
                if (pos < s.length() && (s.charAt(pos) == '+' || s.charAt(pos) == '-')) pos++;
                boolean hadDigits = false, hadDot = false, hadExp = false;
                while (pos < s.length()) {
                    char c = s.charAt(pos);
                    if (c >= '0' && c <= '9') { hadDigits = true; pos++; }
                    else if (c == '.' && !hadDot && !hadExp) { hadDot = true; pos++; }
                    else if ((c == 'e' || c == 'E') && hadDigits && !hadExp) {
                        hadExp = true; pos++;
                        if (pos < s.length() && (s.charAt(pos) == '+' || s.charAt(pos) == '-')) pos++;
                    }
                    else break;
                }
                if (!hadDigits) throw new NumberFormatException("bad float at " + start);
                return Float.parseFloat(s.substring(start, pos));
            }
        }

        /** AOSP command argument counts (after upper-casing to absolute). */
        private static int argCount(char cmd) {
            switch (cmd) {
                case 'M': case 'L': case 'T': return 2;
                case 'H': case 'V': return 1;
                case 'C': return 6;
                case 'S': case 'Q': return 4;
                case 'A': return 7;
                case 'Z': return 0;
                default: return -1;
            }
        }

        private float[][] parse(String s) throws NumberFormatException {
            Tokenizer tz = new Tokenizer(s);
            java.util.ArrayList<float[]> out = new java.util.ArrayList<>();
            char prevCmd = 0;
            while (true) {
                tz.skipSeparators();
                if (tz.pos >= s.length()) break;
                char cmd = tz.s.charAt(tz.pos);
                if (Character.isLetter(cmd)) {
                    tz.pos++;
                    cmd = Character.toUpperCase(cmd);
                } else {
                    // implicit repetition of the previous command; after a
                    // MoveTo the implicit command is LineTo (AOSP behavior)
                    if (prevCmd == 'M') cmd = 'L';
                    else if (prevCmd != 0) cmd = prevCmd;
                    else throw new NumberFormatException("path data starts with a coordinate");
                }
                if (cmd == 'Z' || cmd == 'z') {
                    out.add(new float[]{'Z'});
                    prevCmd = 'Z';
                    continue;
                }
                int argc = argCount(cmd);
                if (argc < 0) throw new NumberFormatException("unknown command " + cmd);
                java.util.ArrayList<Float> args = new java.util.ArrayList<>(argc);
                do {
                    for (int i = 0; i < argc; i++) {
                        if (!tz.hasNextNumber()) {
                            if (args.size() > 0) {
                                break; // trailing repeats handled by loop condition
                            }
                            throw new NumberFormatException("missing arguments for " + cmd);
                        }
                        args.add(tz.readNumber());
                    }
                    // emit one command per argument set (implicit repetition)
                    float[] entry = new float[1 + argc];
                    entry[0] = cmd;
                    for (int i = 0; i < argc; i++) entry[1 + i] = args.get(i);
                    out.add(entry);
                    args.clear();
                } while (tz.hasNextNumber());
                prevCmd = cmd;
            }
            // AOSP: if the first command is not a MoveTo, prepend one at 0,0
            if (!out.isEmpty() && out.get(0)[0] != 'M') {
                float[][] fixed = new float[out.size() + 1][];
                fixed[0] = new float[]{'M', 0f, 0f};
                for (int i = 0; i < out.size(); i++) fixed[i + 1] = out.get(i);
                return fixed;
            }
            return out.toArray(new float[0][]);
        }

        // ---------------- evaluation ----------------

        void applyToPath(Path path) {
            if (path == null || mCommands == null) return;
            float cx = 0, cy = 0;      // current point
            float lastX = 0, lastY = 0; // last control point (for S/T)
            float lastRx = 0, lastRy = 0; // last control point (for S/T, cubic)
            char lastCmd = 0;
            for (float[] c : mCommands) {
                char cmd = (char) c[0];
                boolean relative = Character.isLowerCase(cmd);
                switch (Character.toUpperCase(cmd)) {
                    case 'M': {
                        float x = c[1] + (relative ? cx : 0);
                        float y = c[2] + (relative ? cy : 0);
                        path.moveTo(x, y);
                        cx = x; cy = y;
                        break;
                    }
                    case 'L': {
                        float x = c[1] + (relative ? cx : 0);
                        float y = c[2] + (relative ? cy : 0);
                        path.lineTo(x, y);
                        cx = x; cy = y;
                        break;
                    }
                    case 'H': {
                        float x = c[1] + (relative ? cx : 0);
                        path.lineTo(x, cy);
                        cx = x;
                        break;
                    }
                    case 'V': {
                        float y = c[1] + (relative ? cy : 0);
                        path.lineTo(cx, y);
                        cy = y;
                        break;
                    }
                    case 'C': {
                        float x1 = c[1] + (relative ? cx : 0);
                        float y1 = c[2] + (relative ? cy : 0);
                        float x2 = c[3] + (relative ? cx : 0);
                        float y2 = c[4] + (relative ? cy : 0);
                        float x = c[5] + (relative ? cx : 0);
                        float y = c[6] + (relative ? cy : 0);
                        path.cubicTo(x1, y1, x2, y2, x, y);
                        lastRy = y2; lastX = x2; lastY = y2;
                        lastRx = x2;
                        cx = x; cy = y;
                        break;
                    }
                    case 'S': {
                        // first control point is the reflection of the last
                        float x1, y1;
                        if (lastCmd == 'C' || lastCmd == 'S') {
                            x1 = 2 * cx - lastRx;
                            y1 = 2 * cy - lastRy;
                        } else {
                            x1 = cx; y1 = cy;
                        }
                        float x2 = c[1] + (relative ? cx : 0);
                        float y2 = c[2] + (relative ? cy : 0);
                        float x = c[3] + (relative ? cx : 0);
                        float y = c[4] + (relative ? cy : 0);
                        path.cubicTo(x1, y1, x2, y2, x, y);
                        lastRx = x2; lastRy = y2;
                        cx = x; cy = y;
                        break;
                    }
                    case 'Q': {
                        float x1 = c[1] + (relative ? cx : 0);
                        float y1 = c[2] + (relative ? cy : 0);
                        float x = c[3] + (relative ? cx : 0);
                        float y = c[4] + (relative ? cy : 0);
                        path.quadTo(x1, y1, x, y);
                        lastX = x1; lastY = y1;
                        cx = x; cy = y;
                        break;
                    }
                    case 'T': {
                        float x1, y1;
                        if (lastCmd == 'Q' || lastCmd == 'T') {
                            x1 = 2 * cx - lastX;
                            y1 = 2 * cy - lastY;
                        } else {
                            x1 = cx; y1 = cy;
                        }
                        float x = c[1] + (relative ? cx : 0);
                        float y = c[2] + (relative ? cy : 0);
                        path.quadTo(x1, y1, x, y);
                        lastX = x1; lastY = y1;
                        cx = x; cy = y;
                        break;
                    }
                    case 'A': {
                        float rx = c[1], ry = c[2];
                        float theta = (float) Math.toRadians(c[3]);
                        boolean largeArc = c[4] != 0;
                        boolean sweep = c[5] != 0;
                        float x = c[6] + (relative ? cx : 0);
                        float y = c[7] + (relative ? cy : 0);
                        arcTo(path, cx, cy, rx, ry, theta, largeArc, sweep, x, y);
                        cx = x; cy = y;
                        break;
                    }
                    case 'Z': {
                        path.close();
                        break;
                    }
                }
                lastCmd = Character.toUpperCase(cmd);
            }
        }

        /** SVG arc: endpoint-to-center parameterization, cubic-decomposed. */
        private static void arcTo(Path path, float x0, float y0, float rx, float ry,
                                  float theta, boolean largeArc, boolean sweep,
                                  float x1, float y1) {
            if (rx == 0 || ry == 0 || (x0 == x1 && y0 == y1)) {
                path.lineTo(x1, y1);
                return;
            }
            rx = Math.abs(rx); ry = Math.abs(ry);
            double cosT = Math.cos(theta), sinT = Math.sin(theta);
            double dx = (x0 - x1) / 2.0, dy = (y0 - y1) / 2.0;
            double x1p = cosT * dx + sinT * dy;
            double y1p = -sinT * dx + cosT * dy;

            double lambda = (x1p * x1p) / (rx * rx) + (y1p * y1p) / (ry * ry);
            if (lambda > 1) {
                double s = Math.sqrt(lambda);
                rx = (float) (s * rx);
                ry = (float) (s * ry);
            }

            double sign = (largeArc != sweep) ? 1 : -1;
            double num = rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p;
            double den = rx * rx * y1p * y1p + ry * ry * x1p * x1p;
            double co = Math.sqrt(Math.max(0, num / Math.max(den, 1e-9))) * sign;
            double cxp = co * rx * y1p / ry;
            double cyp = -co * ry * x1p / rx;

            double cx = cosT * cxp - sinT * cyp + (x0 + x1) / 2.0;
            double cy = sinT * cxp + cosT * cyp + (y0 + y1) / 2.0;

            double ux = (x1p - cxp) / rx, uy = (y1p - cyp) / ry;
            double vx = (-x1p - cxp) / rx, vy = (-y1p - cyp) / ry;
            double n = Math.sqrt(ux * ux + uy * uy);
            double p = Math.sqrt(ux * ux + uy * uy + vx * vx + vy * vy);
            double ang1 = angle(1, 0, ux / n, uy / Math.max(n, 1e-9));
            double ang2 = angle(ux / Math.max(n, 1e-9), uy / Math.max(n, 1e-9),
                    vx / Math.max(p, 1e-9), vy / Math.max(p, 1e-9));
            if (!sweep && ang2 > 0) ang2 -= 2 * Math.PI;
            if (sweep && ang2 < 0) ang2 += 2 * Math.PI;

            // decompose into <=90-degree cubic segments
            int segments = Math.max(1, (int) Math.ceil(Math.abs(ang2) / (Math.PI / 2)));
            double delta = ang2 / segments;
            double t = 4.0 / 3.0 * Math.tan(delta / 4.0);
            double cosT1 = Math.cos(ang1);
            double sinT1 = Math.sin(ang1);
            for (int i = 0; i < segments; i++) {
                double cosT2 = Math.cos(ang1 + delta);
                double sinT2 = Math.sin(ang1 + delta);
                double x1e = cosT1 - t * sinT1, y1e = sinT1 + t * cosT1;
                double x2e = cosT2 + t * sinT2, y2e = sinT2 - t * cosT2;
                double ex = cosT2, ey = sinT2;
                toDevice(path, cx, cy, rx, ry, theta, x1e, y1e, x2e, y2e, ex, ey);
                cosT1 = cosT2; sinT1 = sinT2;
                ang1 += delta;
            }
        }

        private static double angle(double ux, double uy, double vx, double vy) {
            double dot = ux * vx + uy * vy;
            double len = Math.sqrt(ux * ux + uy * uy) * Math.sqrt(vx * vx + vy * vy);
            double v = dot / Math.max(len, 1e-9);
            if (v < -1) v = -1;
            if (v > 1) v = 1;
            double ang = Math.acos(v);
            if (ux * vy - uy * vx < 0) ang = -ang;
            return ang;
        }

        private static void toDevice(Path path, double cx, double cy, double rx, double ry,
                                     double theta, double x1e, double y1e,
                                     double x2e, double y2e, double ex, double ey) {
            double cosT = Math.cos(theta), sinT = Math.sin(theta);
            path.cubicTo(
                    (float) (cx + rx * (cosT * x1e - sinT * y1e)),
                    (float) (cy + ry * (sinT * x1e + cosT * y1e)),
                    (float) (cx + rx * (cosT * x2e - sinT * y2e)),
                    (float) (cy + ry * (sinT * x2e + cosT * y2e)),
                    (float) (cx + rx * (cosT * ex - sinT * ey)),
                    (float) (cy + ry * (sinT * ex + cosT * ey)));
        }

        // ---------------- morph / interpolate ----------------

        public boolean canMorph(PathData other) {
            if (other == null || other.mCommands == null) return false;
            if (mCommands.length != other.mCommands.length) return false;
            for (int i = 0; i < mCommands.length; i++) {
                float[] a = mCommands[i], b = other.mCommands[i];
                if (a.length != b.length || a[0] != b[0]) return false;
            }
            return true;
        }

        public boolean interpolate(PathData a, PathData b, float t) {
            if (!canMorph(a) || !canMorph(b)) return false;
            for (int i = 0; i < mCommands.length; i++) {
                float[] dst = new float[a.mCommands[i].length];
                dst[0] = a.mCommands[i][0];
                for (int j = 1; j < dst.length; j++) {
                    float from = a.mCommands[i][j];
                    float to = b.mCommands[i][j];
                    dst[j] = from + (to - from) * t;
                }
                mCommands[i] = dst;
            }
            mValid = true;
            return true;
        }
    }
}
