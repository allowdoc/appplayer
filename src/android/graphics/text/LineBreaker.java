package android.graphics.text;

/**
 * Pure-Java In-Process Stub for android.graphics.text.LineBreaker.
 * Replaces native C++ Minikin line breaking engine with JVM-safe layout.
 */
public class LineBreaker {
    public static final int BREAK_STRATEGY_SIMPLE = 0;
    public static final int BREAK_STRATEGY_HIGH_QUALITY = 1;
    public static final int BREAK_STRATEGY_BALANCED = 2;
    public static final int HYPHENATION_FREQUENCY_NONE = 0;
    public static final int HYPHENATION_FREQUENCY_NORMAL = 1;
    public static final int HYPHENATION_FREQUENCY_FULL = 2;
    public static final int JUSTIFICATION_MODE_NONE = 0;
    public static final int JUSTIFICATION_MODE_INTER_WORD = 1;

    private final int mBreakStrategy;
    private final int mHyphenationFrequency;
    private final int mJustificationMode;
    private final int[] mIndents;

    private LineBreaker(int breakStrategy, int hyphenationFrequency, int justificationMode, int[] indents) {
        this.mBreakStrategy = breakStrategy;
        this.mHyphenationFrequency = hyphenationFrequency;
        this.mJustificationMode = justificationMode;
        this.mIndents = indents;
    }

    public Result computeLineBreaks(MeasuredText measuredText, ParagraphConstraints constraints, int lineNumber) {
        int length = measuredText != null && measuredText.getChars() != null ? measuredText.getChars().length : 0;
        float width = constraints != null ? constraints.getWidth() : 1080.0f;
        return new Result(length, width);
    }

    public static final class ParagraphConstraints {
        private float mWidth = 1080.0f;
        private float mFirstWidth = 1080.0f;
        private int mFirstWidthLineCount = 0;
        private float[] mTabStops = null;
        private float mDefaultTabStop = 0.0f;

        public ParagraphConstraints() {}

        public void setWidth(float width) {
            this.mWidth = width;
            this.mFirstWidth = width;
        }

        public void setIndent(float firstWidth, int firstWidthLineCount) {
            this.mFirstWidth = firstWidth;
            this.mFirstWidthLineCount = firstWidthLineCount;
        }

        public void setTabStops(float[] tabStops, float defaultTabStop) {
            this.mTabStops = tabStops;
            this.mDefaultTabStop = defaultTabStop;
        }

        public float getWidth() { return mWidth; }
        public float getFirstWidth() { return mFirstWidth; }
        public int getFirstWidthLineCount() { return mFirstWidthLineCount; }
        public float[] getTabStops() { return mTabStops; }
        public float getDefaultTabStop() { return mDefaultTabStop; }
    }

    public static class Result {
        private final int mLength;
        private final float mWidth;

        public Result(int length, float width) {
            this.mLength = length;
            this.mWidth = width;
        }

        public int getLineCount() {
            return 1;
        }

        public int getLineBreakOffset(int lineIndex) {
            return mLength;
        }

        public float getLineWidth(int lineIndex) {
            return mWidth;
        }

        public float getLineAscent(int lineIndex) {
            return -20.0f;
        }

        public float getLineDescent(int lineIndex) {
            return 8.0f;
        }

        public boolean hasLineTab(int lineIndex) {
            return false;
        }

        public int getStartLineHyphenEdit(int lineIndex) {
            return 0;
        }

        public int getEndLineHyphenEdit(int lineIndex) {
            return 0;
        }
    }

    public static final class Builder {
        private int mBreakStrategy = BREAK_STRATEGY_SIMPLE;
        private int mHyphenationFrequency = HYPHENATION_FREQUENCY_NONE;
        private int mJustificationMode = JUSTIFICATION_MODE_NONE;
        private int[] mIndents = null;

        public Builder() {}

        public Builder setBreakStrategy(int breakStrategy) {
            this.mBreakStrategy = breakStrategy;
            return this;
        }

        public Builder setHyphenationFrequency(int hyphenationFrequency) {
            this.mHyphenationFrequency = hyphenationFrequency;
            return this;
        }

        public Builder setJustificationMode(int justificationMode) {
            this.mJustificationMode = justificationMode;
            return this;
        }

        public Builder setIndents(int[] indents) {
            this.mIndents = indents;
            return this;
        }

        public LineBreaker build() {
            return new LineBreaker(mBreakStrategy, mHyphenationFrequency, mJustificationMode, mIndents);
        }
    }
}
