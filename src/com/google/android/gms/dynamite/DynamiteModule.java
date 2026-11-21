package com.google.android.gms.dynamite;

import android.content.Context;

/**
 * UNIVERSAL GOOGLE SERVICES FIXER (part 3).
 *
 * DynamiteModule normally loads GMS feature code from a remote GmsCore APK
 * ("Invalid GmsCore APK, remote loading disabled" spam comes from here).
 * Inside the runner there is no GmsCore; the LOCAL (app-bundled) version is
 * always reported so callers fall back to the code compiled into the app
 * itself. No remote loading is ever attempted.
 */
public class DynamiteModule {

    public static final int PREFER_REMOTE = 0;
    public static final int PREFER_LOCAL = 1;
    public static final int FORCE_LOCAL_ONLY = 2;

    // Legacy policy objects referenced by GMS client libraries
    public static final VersionPolicy PREFER_HIGHEST_OR_LOCAL_VERSION = new PreferHighestOrLocal();
    public static final VersionPolicy PREFER_REMOTE_VERSION = new PreferHighestOrLocal();
    public static final VersionPolicy PREFER_LOCAL_VERSION = new PreferHighestOrLocal();
    public static final VersionPolicy FORCE_LOCAL_VERSION = new PreferHighestOrLocal();

    public static class PreferHighestOrLocal extends VersionPolicy {
        public Version selection(Context context, String moduleId, VersionResult versions) {
            int v = Math.max(versions != null ? versions.localVersion : 0,
                    versions != null ? versions.remoteVersion : 0);
            return new Version(Math.max(v, getLocalVersion(context, moduleId)), false);
        }
    }

    public static class VersionPolicy {
        public static final int PREFER_REMOTE = 0;
        public static final int PREFER_LOCAL = 1;
        public static final int FORCE_LOCAL_ONLY = 2;

        public static final VersionPolicy PREFER_REMOTE_VERSION = new PreferRemote();
        public static final VersionPolicy PREFER_LOCAL_VERSION = new PreferLocal();

        public static class PreferLocal extends VersionPolicy {
            public Version selection(Context context, String moduleId, VersionResult versions) {
                return new Version(versions.localVersion, false);
            }
        }

        public static class PreferRemote extends VersionPolicy {
            public Version selection(Context context, String moduleId, VersionResult versions) {
                // In-process runner: "remote" (GmsCore) is never available;
                // always answer with the local version.
                return new Version(versions.localVersion, false);
            }
        }

        public int selection(Context context, String moduleId) {
            return getLocalVersion(context, moduleId);
        }
    }

    public static class VersionResult {
        public final int localVersion;
        public final int remoteVersion;
        public final boolean isFallback;
        public VersionResult(int localVersion, int remoteVersion, boolean isFallback) {
            this.localVersion = localVersion;
            this.remoteVersion = remoteVersion;
            this.isFallback = isFallback;
        }
    }

    public static class Version {
        public final int version;
        public final boolean isFallback;
        public Version(int version, boolean isFallback) {
            this.version = version;
            this.isFallback = isFallback;
        }
    }

    /** Local (in-app) version — always available in the runner. */
    public static int getLocalVersion(Context context, String moduleId) {
        return 21900; // matches recent GMS client expectations
    }

    public static int getRemoteVersion(Context context, String moduleId) {
        return 0; // no remote GmsCore in-process
    }

    public static VersionResult queryRemoteVersion(Context context, String moduleId,
                                                   VersionPolicy policy) {
        return new VersionResult(getLocalVersion(context, moduleId), 0, false);
    }

    public static VersionResult queryLocalVersion(Context context, String moduleId) {
        return new VersionResult(getLocalVersion(context, moduleId), 0, false);
    }

    public static Version selectModule(Context context, String moduleId, VersionPolicy policy) {
        return new Version(getLocalVersion(context, moduleId), false);
    }

    public static DynamiteModule load(Context context, String moduleId) throws LoadingException {
        throw new LoadingException("Dynamite module '" + moduleId
                + "' cannot be loaded in-process; using app-local implementation.");
    }

    public static DynamiteModule load(Context context, VersionPolicy policy, String moduleId)
            throws LoadingException {
        throw new LoadingException("Dynamite module '" + moduleId
                + "' cannot be loaded in-process; using app-local implementation.");
    }

    public Context getModuleContext() {
        return null;
    }

    /** Mirrors the real exception type so app catch blocks keep working. */
    public static class LoadingException extends Exception {
        public LoadingException(String message) { super(message); }
    }
}
