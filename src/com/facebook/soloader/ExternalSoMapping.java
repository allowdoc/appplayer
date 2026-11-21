package com.facebook.soloader;

public interface ExternalSoMapping {
    void invokeJniOnload(String libName);
    String mapLibName(String libName);
}
