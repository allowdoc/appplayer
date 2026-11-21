package org.headless.runner.core;

import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.*;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.PersistableBundle;
import android.os.UserHandle;
import android.os.storage.VolumeInfo;
import java.util.Collections;
import java.util.List;

public class MockPackageManager extends PackageManager {
    @Override
    public int checkPermission(java.lang.String arg0, java.lang.String arg1) { return 0; }
    @Override
    public android.content.ComponentName getInstantAppInstallerComponent() { return null; }
    @Override
    public void addCrossProfileIntentFilter(android.content.IntentFilter arg0, int arg1, int arg2, int arg3) { }
    @Override
    public void clearCrossProfileIntentFilters(int arg0) { }
    @Override
    public android.content.ComponentName getInstantAppResolverSettingsComponent() { return null; }
    @Override
    public java.lang.CharSequence getText(java.lang.String arg0, int arg1, android.content.pm.ApplicationInfo arg2) { return ""; }
    @Override
    public java.lang.String getPermissionControllerPackageName() { return ""; }
    @Override
    public boolean shouldShowRequestPermissionRationale(java.lang.String arg0) { return false; }
    @Override
    public int getInstantAppCookieMaxBytes() { return 0; }
    @Override
    public java.lang.String[] canonicalToCurrentPackageNames(java.lang.String[] arg0) { return new java.lang.String[0]; }
    @Override
    public java.util.List getInstalledApplicationsAsUser(int arg0, int arg1) { return Collections.emptyList(); }
    @Override
    public java.lang.String[] getSystemSharedLibraryNames() { return new java.lang.String[0]; }
    @Override
    public java.lang.String getServicesSystemSharedLibraryPackageName() { return ""; }
    @Override
    public java.util.List getPackagesHoldingPermissions(java.lang.String[] arg0, int arg1) { return Collections.emptyList(); }
    @Override
    public java.lang.String getSharedSystemSharedLibraryPackageName() { return ""; }
    @Override
    public android.content.Intent getCarLaunchIntentForPackage(java.lang.String arg0) { return null; }
    @Override
    public java.lang.String[] currentToCanonicalPackageNames(java.lang.String[] arg0) { return new java.lang.String[0]; }
    @Override
    public android.content.Intent getLeanbackLaunchIntentForPackage(java.lang.String arg0) { return null; }
    @Override
    public boolean isPermissionRevokedByPolicy(java.lang.String arg0, java.lang.String arg1) { return false; }
    @Override
    public boolean arePermissionsIndividuallyControlled() { return false; }
    @Override
    public boolean isWirelessConsentModeEnabled() { return false; }
    @Override
    public java.lang.String getDefaultBrowserPackageNameAsUser(int arg0) { return ""; }
    @Override
    public void deleteApplicationCacheFilesAsUser(java.lang.String arg0, int arg1, android.content.pm.IPackageDataObserver arg2) { }
    @Override
    public void clearPackagePreferredActivities(java.lang.String arg0) { }
    @Override
    public int getApplicationEnabledSetting(java.lang.String arg0) { return 0; }
    @Override
    public android.graphics.drawable.Drawable getUserBadgeForDensityNoBackground(android.os.UserHandle arg0, int arg1) { return null; }
    @Override
    public boolean setDefaultBrowserPackageNameAsUser(java.lang.String arg0, int arg1) { return false; }
    @Override
    public boolean setApplicationHiddenSettingAsUser(java.lang.String arg0, boolean arg1, android.os.UserHandle arg2) { return false; }
    @Override
    public boolean getApplicationHiddenSettingAsUser(java.lang.String arg0, android.os.UserHandle arg1) { return false; }
    @Override
    public void addOnPermissionsChangeListener(android.content.pm.PackageManager.OnPermissionsChangedListener arg0) { }
    @Override
    public java.util.List queryIntentActivitiesAsUser(android.content.Intent arg0, int arg1, int arg2) { return Collections.emptyList(); }
    @Override
    public java.util.List queryIntentContentProvidersAsUser(android.content.Intent arg0, int arg1, int arg2) { return Collections.emptyList(); }
    @Override
    public android.content.pm.ProviderInfo resolveContentProviderAsUser(java.lang.String arg0, int arg1, int arg2) { return null; }
    @Override
    public java.util.List queryIntentContentProviders(android.content.Intent arg0, int arg1) { return Collections.emptyList(); }
    @Override
    public void flushPackageRestrictionsAsUser(int arg0) { }
    @Override
    public android.os.storage.VolumeInfo getPrimaryStorageCurrentVolume() { return null; }
    @Override
    public int getIntentVerificationStatusAsUser(java.lang.String arg0, int arg1) { return 0; }
    @Override
    public android.graphics.drawable.Drawable getUserBadgedDrawableForDensity(android.graphics.drawable.Drawable arg0, android.os.UserHandle arg1, android.graphics.Rect arg2, int arg3) { return null; }
    @Override
    public void deleteApplicationCacheFiles(java.lang.String arg0, android.content.pm.IPackageDataObserver arg1) { }
    @Override
    public java.util.List getPrimaryStorageCandidateVolumes() { return Collections.emptyList(); }
    @Override
    public int installExistingPackageAsUser(java.lang.String arg0, int arg1) throws android.content.pm.PackageManager.NameNotFoundException { return 0; }
    @Override
    public android.content.res.Resources getResourcesForApplicationAsUser(java.lang.String arg0, int arg1) throws android.content.pm.PackageManager.NameNotFoundException { return null; }
    @Override
    public java.util.List queryBroadcastReceiversAsUser(android.content.Intent arg0, int arg1, int arg2) { return Collections.emptyList(); }
    @Override
    public boolean updateIntentVerificationStatusAsUser(java.lang.String arg0, int arg1, int arg2) { return false; }
    @Override
    public java.util.List getIntentFilterVerifications(java.lang.String arg0) { return Collections.emptyList(); }
    @Override
    public void setApplicationEnabledSetting(java.lang.String arg0, int arg1, int arg2) { }
    @Override
    public void removeOnPermissionsChangeListener(android.content.pm.PackageManager.OnPermissionsChangedListener arg0) { }
    @Override
    public boolean isSafeMode() { return false; }
    @Override
    public boolean isUpgrade() { return false; }
    @Override
    public android.content.res.XmlResourceParser getXml(java.lang.String arg0, int arg1, android.content.pm.ApplicationInfo arg2) { return null; }
    @Override
    public boolean isSignedBy(java.lang.String arg0, android.content.pm.KeySet arg1) { return false; }
    @Override
    public android.content.pm.PackageInfo getPackageInfo(java.lang.String arg0, int arg1) throws android.content.pm.PackageManager.NameNotFoundException { return null; }
    @Override
    public android.content.pm.PackageInfo getPackageInfo(android.content.pm.VersionedPackage arg0, int arg1) throws android.content.pm.PackageManager.NameNotFoundException { return null; }
    @Override
    public android.content.pm.PackageInfo getPackageInfoAsUser(java.lang.String arg0, int arg1, int arg2) throws android.content.pm.PackageManager.NameNotFoundException { return null; }
    @Override
    public int getPackageUidAsUser(java.lang.String arg0, int arg1, int arg2) throws android.content.pm.PackageManager.NameNotFoundException { return 0; }
    @Override
    public int getPackageUidAsUser(java.lang.String arg0, int arg1) throws android.content.pm.PackageManager.NameNotFoundException { return 0; }
    @Override
    public android.content.pm.ApplicationInfo getApplicationInfoAsUser(java.lang.String arg0, int arg1, int arg2) throws android.content.pm.PackageManager.NameNotFoundException { return null; }
    @Override
    public android.content.pm.ProviderInfo getProviderInfo(android.content.ComponentName arg0, int arg1) throws android.content.pm.PackageManager.NameNotFoundException { return null; }
    @Override
    public java.util.List getInstalledPackagesAsUser(int arg0, int arg1) { return Collections.emptyList(); }
    @Override
    public boolean addPermission(android.content.pm.PermissionInfo arg0) { return false; }
    @Override
    public android.content.pm.ServiceInfo getServiceInfo(android.content.ComponentName arg0, int arg1) throws android.content.pm.PackageManager.NameNotFoundException { return null; }
    @Override
    public int getPermissionFlags(java.lang.String arg0, java.lang.String arg1, android.os.UserHandle arg2) { return 0; }
    @Override
    public void updatePermissionFlags(java.lang.String arg0, java.lang.String arg1, int arg2, int arg3, android.os.UserHandle arg4) { }
    @Override
    public java.util.List queryPermissionsByGroup(java.lang.String arg0, int arg1) throws android.content.pm.PackageManager.NameNotFoundException { return Collections.emptyList(); }
    @Override
    public android.content.Intent getLaunchIntentForPackage(java.lang.String arg0) { return null; }
    @Override
    public java.util.List getAllPermissionGroups(int arg0) { return Collections.emptyList(); }
    @Override
    public void removePermission(java.lang.String arg0) { }
    @Override
    public android.content.pm.PermissionGroupInfo getPermissionGroupInfo(java.lang.String arg0, int arg1) throws android.content.pm.PackageManager.NameNotFoundException { return null; }
    @Override
    public int[] getPackageGids(java.lang.String arg0) throws android.content.pm.PackageManager.NameNotFoundException { return new int[0]; }
    @Override
    public int[] getPackageGids(java.lang.String arg0, int arg1) throws android.content.pm.PackageManager.NameNotFoundException { return new int[0]; }
    @Override
    public java.util.List getInstalledPackages(int arg0) { return Collections.emptyList(); }
    @Override
    public boolean addPermissionAsync(android.content.pm.PermissionInfo arg0) { return false; }
    @Override
    public void revokeRuntimePermission(java.lang.String arg0, java.lang.String arg1, android.os.UserHandle arg2) { }
    @Override
    public android.content.pm.ActivityInfo getActivityInfo(android.content.ComponentName arg0, int arg1) throws android.content.pm.PackageManager.NameNotFoundException { return null; }
    @Override
    public android.content.pm.PermissionInfo getPermissionInfo(java.lang.String arg0, int arg1) throws android.content.pm.PackageManager.NameNotFoundException { return null; }
    @Override
    public android.content.pm.ApplicationInfo getApplicationInfo(java.lang.String arg0, int arg1) throws android.content.pm.PackageManager.NameNotFoundException { return null; }
    @Override
    public int checkSignatures(int arg0, int arg1) { return 0; }
    @Override
    public int checkSignatures(java.lang.String arg0, java.lang.String arg1) { return 0; }
    @Override
    public java.lang.String[] getPackagesForUid(int arg0) { return new java.lang.String[0]; }
    @Override
    public void grantRuntimePermission(java.lang.String arg0, java.lang.String arg1, android.os.UserHandle arg2) { }
    @Override
    public int getPackageUid(java.lang.String arg0, int arg1) throws android.content.pm.PackageManager.NameNotFoundException { return 0; }
    @Override
    public android.content.pm.ActivityInfo getReceiverInfo(android.content.ComponentName arg0, int arg1) throws android.content.pm.PackageManager.NameNotFoundException { return null; }
    @Override
    public java.lang.String getNameForUid(int arg0) { return ""; }
    @Override
    public java.lang.String[] getNamesForUids(int[] arg0) { return new java.lang.String[0]; }
    @Override
    public int getUidForSharedUser(java.lang.String arg0) throws android.content.pm.PackageManager.NameNotFoundException { return 0; }
    @Override
    public java.util.List getInstalledApplications(int arg0) { return Collections.emptyList(); }
    @Override
    public java.util.List getInstantApps() { return Collections.emptyList(); }
    @Override
    public java.util.List queryBroadcastReceivers(android.content.Intent arg0, int arg1) { return Collections.emptyList(); }
    @Override
    public android.content.pm.InstrumentationInfo getInstrumentationInfo(android.content.ComponentName arg0, int arg1) throws android.content.pm.PackageManager.NameNotFoundException { return null; }
    @Override
    public android.graphics.drawable.Drawable getActivityIcon(android.content.Intent arg0) throws android.content.pm.PackageManager.NameNotFoundException { return null; }
    @Override
    public android.graphics.drawable.Drawable getActivityIcon(android.content.ComponentName arg0) throws android.content.pm.PackageManager.NameNotFoundException { return null; }
    @Override
    public boolean isInstantApp() { return false; }
    @Override
    public boolean isInstantApp(java.lang.String arg0) { return false; }
    @Override
    public android.content.pm.ResolveInfo resolveActivityAsUser(android.content.Intent arg0, int arg1, int arg2) { return null; }
    @Override
    public android.graphics.drawable.Drawable getUserBadgeForDensity(android.os.UserHandle arg0, int arg1) { return null; }
    @Override
    public java.util.List getSharedLibraries(int arg0) { return Collections.emptyList(); }
    @Override
    public void verifyPendingInstall(int arg0, int arg1) { }
    @Override
    public void deletePackage(java.lang.String arg0, android.content.pm.IPackageDeleteObserver arg1, int arg2) { }
    @Override
    public void deletePackageAsUser(java.lang.String arg0, android.content.pm.IPackageDeleteObserver arg1, int arg2, int arg3) { }
    @Override
    public java.lang.String getInstallerPackageName(java.lang.String arg0) { return ""; }
    @Override
    public android.graphics.drawable.Drawable getDrawable(java.lang.String arg0, int arg1, android.content.pm.ApplicationInfo arg2) { return null; }
    @Override
    public android.content.res.Resources getResourcesForActivity(android.content.ComponentName arg0) throws android.content.pm.PackageManager.NameNotFoundException { return null; }
    @Override
    public java.util.List queryIntentServicesAsUser(android.content.Intent arg0, int arg1, int arg2) { return Collections.emptyList(); }
    @Override
    public android.content.res.Resources getResourcesForApplication(java.lang.String arg0) throws android.content.pm.PackageManager.NameNotFoundException { return null; }
    @Override
    public android.content.res.Resources getResourcesForApplication(android.content.pm.ApplicationInfo arg0) throws android.content.pm.PackageManager.NameNotFoundException { return null; }
    @Override
    public android.graphics.drawable.Drawable getActivityBanner(android.content.ComponentName arg0) throws android.content.pm.PackageManager.NameNotFoundException { return null; }
    @Override
    public android.graphics.drawable.Drawable getActivityBanner(android.content.Intent arg0) throws android.content.pm.PackageManager.NameNotFoundException { return null; }
    @Override
    public java.lang.CharSequence getApplicationLabel(android.content.pm.ApplicationInfo arg0) { return ""; }
    @Override
    public android.graphics.drawable.Drawable getInstantAppIcon(java.lang.String arg0) { return null; }
    @Override
    public void setUpdateAvailable(java.lang.String arg0, boolean arg1) { }
    @Override
    public java.util.List queryIntentActivityOptions(android.content.ComponentName arg0, android.content.Intent[] arg1, android.content.Intent arg2, int arg3) { return Collections.emptyList(); }
    @Override
    public void clearApplicationUserData(java.lang.String arg0, android.content.pm.IPackageDataObserver arg1) { }
    @Override
    public int getInstantAppCookieMaxSize() { return 0; }
    @Override
    public java.util.List queryIntentServices(android.content.Intent arg0, int arg1) { return Collections.emptyList(); }
    @Override
    public java.lang.CharSequence getUserBadgedLabel(java.lang.CharSequence arg0, android.os.UserHandle arg1) { return ""; }
    @Override
    public void updateInstantAppCookie(byte[] arg0) { }
    @Override
    public android.content.pm.ChangedPackages getChangedPackages(int arg0) { return null; }
    @Override
    public android.content.pm.FeatureInfo[] getSystemAvailableFeatures() { return new android.content.pm.FeatureInfo[0]; }
    @Override
    public java.util.List queryIntentActivities(android.content.Intent arg0, int arg1) { return Collections.emptyList(); }
    @Override
    public android.graphics.drawable.Drawable getApplicationIcon(android.content.pm.ApplicationInfo arg0) { return null; }
    @Override
    public android.graphics.drawable.Drawable getApplicationIcon(java.lang.String arg0) throws android.content.pm.PackageManager.NameNotFoundException { return null; }
    @Override
    public android.graphics.drawable.Drawable getActivityLogo(android.content.Intent arg0) throws android.content.pm.PackageManager.NameNotFoundException { return null; }
    @Override
    public android.graphics.drawable.Drawable getActivityLogo(android.content.ComponentName arg0) throws android.content.pm.PackageManager.NameNotFoundException { return null; }
    @Override
    public java.util.List getAllIntentFilters(java.lang.String arg0) { return Collections.emptyList(); }
    @Override
    public boolean hasSystemFeature(java.lang.String arg0) { return false; }
    @Override
    public boolean hasSystemFeature(java.lang.String arg0, int arg1) { return false; }
    @Override
    public byte[] getInstantAppCookie() { return new byte[0]; }
    @Override
    public android.content.pm.ResolveInfo resolveService(android.content.Intent arg0, int arg1) { return null; }
    @Override
    public int installExistingPackage(java.lang.String arg0, int arg1) throws android.content.pm.PackageManager.NameNotFoundException { return 0; }
    @Override
    public int installExistingPackage(java.lang.String arg0) throws android.content.pm.PackageManager.NameNotFoundException { return 0; }
    @Override
    public void setInstallerPackageName(java.lang.String arg0, java.lang.String arg1) { }
    @Override
    public android.content.pm.ResolveInfo resolveActivity(android.content.Intent arg0, int arg1) { return null; }
    @Override
    public void clearInstantAppCookie() { }
    @Override
    public java.util.List getSharedLibrariesAsUser(int arg0, int arg1) { return Collections.emptyList(); }
    @Override
    public android.content.pm.ResolveInfo resolveServiceAsUser(android.content.Intent arg0, int arg1, int arg2) { return null; }
    @Override
    public android.content.pm.ProviderInfo resolveContentProvider(java.lang.String arg0, int arg1) { return null; }
    @Override
    public java.util.List queryInstrumentation(java.lang.String arg0, int arg1) { return Collections.emptyList(); }
    @Override
    public boolean setInstantAppCookie(byte[] arg0) { return false; }
    @Override
    public android.graphics.drawable.Drawable getDefaultActivityIcon() { return null; }
    @Override
    public android.graphics.drawable.Drawable getApplicationBanner(java.lang.String arg0) throws android.content.pm.PackageManager.NameNotFoundException { return null; }
    @Override
    public android.graphics.drawable.Drawable getApplicationBanner(android.content.pm.ApplicationInfo arg0) { return null; }
    @Override
    public android.graphics.drawable.Drawable getApplicationLogo(android.content.pm.ApplicationInfo arg0) { return null; }
    @Override
    public android.graphics.drawable.Drawable getApplicationLogo(java.lang.String arg0) throws android.content.pm.PackageManager.NameNotFoundException { return null; }
    @Override
    public android.graphics.drawable.Drawable getUserBadgedIcon(android.graphics.drawable.Drawable arg0, android.os.UserHandle arg1) { return null; }
    @Override
    public void extendVerificationTimeout(int arg0, int arg1, long arg2) { }
    @Override
    public java.util.List queryContentProviders(java.lang.String arg0, int arg1, int arg2) { return Collections.emptyList(); }
    @Override
    public void verifyIntentFilter(int arg0, int arg1, java.util.List arg2) { }
    @Override
    public int getPreferredActivities(java.util.List arg0, java.util.List arg1, java.lang.String arg2) { return 0; }
    @Override
    public void getPackageSizeInfoAsUser(java.lang.String arg0, int arg1, android.content.pm.IPackageStatsObserver arg2) { }
    @Override
    public void unregisterMoveCallback(android.content.pm.PackageManager.MoveCallback arg0) { }
    @Override
    public android.content.ComponentName getHomeActivities(java.util.List arg0) { return null; }
    @Override
    public android.content.pm.KeySet getSigningKeySet(java.lang.String arg0) { return null; }
    @Override
    public java.util.List getPreferredPackages(int arg0) { return Collections.emptyList(); }
    @Override
    public android.content.pm.KeySet getKeySetByAlias(java.lang.String arg0, java.lang.String arg1) { return null; }
    @Override
    public boolean isSignedByExactly(java.lang.String arg0, android.content.pm.KeySet arg1) { return false; }
    @Override
    public boolean isPackageAvailable(java.lang.String arg0) { return false; }
    @Override
    public void setComponentEnabledSetting(android.content.ComponentName arg0, int arg1, int arg2) { }
    @Override
    public int movePrimaryStorage(android.os.storage.VolumeInfo arg0) { return 0; }
    @Override
    public int getComponentEnabledSetting(android.content.ComponentName arg0) { return 0; }
    @Override
    public void freeStorageAndNotify(java.lang.String arg0, long arg1, android.content.pm.IPackageDataObserver arg2) { }
    @Override
    public android.content.pm.PackageInstaller getPackageInstaller() { return null; }
    @Override
    public android.graphics.drawable.Drawable loadUnbadgedItemIcon(android.content.pm.PackageItemInfo arg0, android.content.pm.ApplicationInfo arg1) { return null; }
    @Override
    public boolean isPackageSuspendedForUser(java.lang.String arg0, int arg1) { return false; }
    @Override
    public android.content.pm.VerifierDeviceIdentity getVerifierDeviceIdentity() { return null; }
    @Override
    public java.lang.String getInstantAppAndroidId(java.lang.String arg0, android.os.UserHandle arg1) { return ""; }
    @Override
    public void registerDexModule(java.lang.String arg0, android.content.pm.PackageManager.DexModuleRegisterCallback arg1) { }
    @Override
    public int getInstallReason(java.lang.String arg0, android.os.UserHandle arg1) { return 0; }
    @Override
    public android.os.storage.VolumeInfo getPackageCurrentVolume(android.content.pm.ApplicationInfo arg0) { return null; }
    @Override
    public void setApplicationCategoryHint(java.lang.String arg0, int arg1) { }
    @Override
    public void registerMoveCallback(android.content.pm.PackageManager.MoveCallback arg0, android.os.Handler arg1) { }
    @Override
    public boolean canRequestPackageInstalls() { return false; }
    @Override
    public void replacePreferredActivity(android.content.IntentFilter arg0, int arg1, android.content.ComponentName[] arg2, android.content.ComponentName arg3) { }
    @Override
    public void addPackageToPreferred(java.lang.String arg0) { }
    @Override
    public int movePackage(java.lang.String arg0, android.os.storage.VolumeInfo arg1) { return 0; }
    @Override
    public void addPreferredActivity(android.content.IntentFilter arg0, int arg1, android.content.ComponentName[] arg2, android.content.ComponentName arg3) { }
    @Override
    public int getMoveStatus(int arg0) { return 0; }
    @Override
    public java.util.List getPackageCandidateVolumes(android.content.pm.ApplicationInfo arg0) { return Collections.emptyList(); }
    @Override
    public void freeStorage(java.lang.String arg0, long arg1, android.content.IntentSender arg2) { }
    @Override
    public void removePackageFromPreferred(java.lang.String arg0) { }
    @Override
    public android.graphics.drawable.Drawable loadItemIcon(android.content.pm.PackageItemInfo arg0, android.content.pm.ApplicationInfo arg1) { return null; }
}
