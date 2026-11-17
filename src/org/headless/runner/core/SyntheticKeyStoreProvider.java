package org.headless.runner.core;

import android.security.keystore.KeyGenParameterSpec;

import javax.crypto.KeyGeneratorSpi;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pure Java in-process implementation of the AndroidKeyStore JCA Provider.
 * Persists keys across restarts to prevent EncryptedSharedPreferences AEADBadTagException.
 * Fully supports MasterKeys, AES-GCM, RSA/EC keypairs, and synthetic X509 certificates.
 */
public class SyntheticKeyStoreProvider extends Provider {
    public static final String PROVIDER_NAME = "AndroidKeyStore";
    private static final File STORAGE_FILE = new File("C:\\Users\\Noval\\.gemini\\antigravity\\scratch\\android-runner\\data\\.keystore.dat");

    public SyntheticKeyStoreProvider() {
        super(PROVIDER_NAME, "1.0", "In-Process AndroidKeyStore Software Security Provider");

        put("KeyStore." + PROVIDER_NAME, KeyStoreSpiImpl.class.getName());
        put("KeyStore.AndroidKeyStoreProvider", KeyStoreSpiImpl.class.getName());
        put("KeyGenerator.AES", KeyGeneratorAES.class.getName());
        put("KeyGenerator.HmacSHA256", KeyGeneratorHmac.class.getName());
        put("KeyPairGenerator.RSA", KeyPairGeneratorRSA.class.getName());
        put("KeyPairGenerator.EC", KeyPairGeneratorEC.class.getName());
    }

    public static synchronized void install() {
        try {
            loadFromDisk();
            if (Security.getProvider(PROVIDER_NAME) == null) {
                Security.addProvider(new SyntheticKeyStoreProvider());
                System.out.println("[+] [AndroidKeyStore] Pure Java AndroidKeyStore provider installed successfully.");
            }
        } catch (Throwable t) {
            System.err.println("[-] Error installing AndroidKeyStore provider: " + t);
        }
    }

    // In-memory key storage shared across SPI instances
    private static final Map<String, Key> sKeyMap = new ConcurrentHashMap<>();
    private static final Map<String, Certificate[]> sCertChainMap = new ConcurrentHashMap<>();
    private static final Map<String, Date> sCreationDateMap = new ConcurrentHashMap<>();
    private static volatile boolean sLoaded = false;

    private static synchronized void loadFromDisk() {
        if (sLoaded) return;
        sLoaded = true;
        if (!STORAGE_FILE.exists()) return;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(STORAGE_FILE)))) {
            int keyCount = in.readInt();
            for (int i = 0; i < keyCount; i++) {
                String alias = in.readUTF();
                String type = in.readUTF();
                String algorithm = in.readUTF();
                int len = in.readInt();
                byte[] encoded = new byte[len];
                in.readFully(encoded);

                if ("SECRET".equals(type)) {
                    sKeyMap.put(alias, new SecretKeySpec(encoded, algorithm));
                } else if ("PRIVATE".equals(type)) {
                    try {
                        KeyFactory kf = KeyFactory.getInstance(algorithm);
                        PrivateKey pk = kf.generatePrivate(new PKCS8EncodedKeySpec(encoded));
                        sKeyMap.put(alias, pk);
                    } catch (Throwable ignored) {}
                }
                sCreationDateMap.put(alias, new Date());
            }

            int certCount = in.readInt();
            for (int i = 0; i < certCount; i++) {
                String alias = in.readUTF();
                String algorithm = in.readUTF();
                int len = in.readInt();
                byte[] pubBytes = new byte[len];
                in.readFully(pubBytes);
                try {
                    KeyFactory kf = KeyFactory.getInstance(algorithm);
                    PublicKey pub = kf.generatePublic(new X509EncodedKeySpec(pubBytes));
                    sCertChainMap.put(alias, new Certificate[] { new SyntheticX509Certificate(pub, alias) });
                } catch (Throwable ignored) {}
            }
            System.out.println("[+] [AndroidKeyStore] Loaded " + sKeyMap.size() + " key(s) from persistent storage.");
        } catch (Throwable t) {
            System.err.println("[-] [AndroidKeyStore] Notice loading keystore: " + t.getMessage());
        }
    }

    private static synchronized void saveToDisk() {
        try {
            File parent = STORAGE_FILE.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();

            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(STORAGE_FILE)))) {
                out.writeInt(sKeyMap.size());
                for (Map.Entry<String, Key> e : sKeyMap.entrySet()) {
                    Key k = e.getValue();
                    byte[] encoded = k.getEncoded();
                    if (encoded != null) {
                        out.writeUTF(e.getKey());
                        out.writeUTF(k instanceof SecretKey ? "SECRET" : "PRIVATE");
                        out.writeUTF(k.getAlgorithm() != null ? k.getAlgorithm() : "AES");
                        out.writeInt(encoded.length);
                        out.write(encoded);
                    }
                }

                out.writeInt(sCertChainMap.size());
                for (Map.Entry<String, Certificate[]> e : sCertChainMap.entrySet()) {
                    Certificate[] chain = e.getValue();
                    if (chain != null && chain.length > 0 && chain[0].getPublicKey() != null) {
                        PublicKey pub = chain[0].getPublicKey();
                        byte[] pubBytes = pub.getEncoded();
                        if (pubBytes != null) {
                            out.writeUTF(e.getKey());
                            out.writeUTF(pub.getAlgorithm() != null ? pub.getAlgorithm() : "RSA");
                            out.writeInt(pubBytes.length);
                            out.write(pubBytes);
                        }
                    }
                }
            }
        } catch (Throwable t) {
            System.err.println("[-] [AndroidKeyStore] Notice saving keystore: " + t.getMessage());
        }
    }

    public static void putKeyDirect(String alias, Key key) {
        if (alias != null && key != null) {
            sKeyMap.put(alias, key);
            sCreationDateMap.put(alias, new Date());
            saveToDisk();
        }
    }

    public static Key getKeyDirect(String alias) {
        return alias != null ? sKeyMap.get(alias) : null;
    }

    public static class KeyStoreSpiImpl extends KeyStoreSpi {
        @Override
        public Key engineGetKey(String alias, char[] password) throws NoSuchAlgorithmException, UnrecoverableKeyException {
            if (alias == null) return null;
            Key k = sKeyMap.get(alias);
            if (k == null && alias.startsWith("USRSKEY_")) {
                k = sKeyMap.get(alias.substring(8));
            } else if (k == null && alias.startsWith("USRPKEY_")) {
                k = sKeyMap.get(alias.substring(8));
            }
            if (k == null) {
                // Generate and persist a deterministic or random key
                byte[] defaultBytes = new byte[32];
                new SecureRandom().nextBytes(defaultBytes);
                k = new SecretKeySpec(defaultBytes, "AES");
                sKeyMap.put(alias, k);
                sCreationDateMap.put(alias, new Date());
                saveToDisk();
                System.out.println("[+] [AndroidKeyStore] Provisioned persistent AES key for alias: " + alias);
            }
            return k;
        }

        @Override
        public Certificate[] engineGetCertificateChain(String alias) {
            Certificate[] chain = sCertChainMap.get(alias);
            if (chain == null && alias != null && alias.startsWith("USRPKEY_")) {
                chain = sCertChainMap.get(alias.substring(8));
            }
            return chain;
        }

        @Override
        public Certificate engineGetCertificate(String alias) {
            Certificate[] chain = engineGetCertificateChain(alias);
            return (chain != null && chain.length > 0) ? chain[0] : null;
        }

        @Override
        public Date engineGetCreationDate(String alias) {
            Date d = sCreationDateMap.get(alias);
            return d != null ? d : new Date();
        }

        @Override
        public void engineSetKeyEntry(String alias, Key key, char[] password, Certificate[] chain) throws KeyStoreException {
            if (alias == null || key == null) return;
            sKeyMap.put(alias, key);
            sCreationDateMap.put(alias, new Date());
            if (chain != null) {
                sCertChainMap.put(alias, chain);
            }
            saveToDisk();
        }

        @Override
        public void engineSetKeyEntry(String alias, byte[] key, Certificate[] chain) throws KeyStoreException {
            if (alias == null || key == null) return;
            sKeyMap.put(alias, new SecretKeySpec(key, "RAW"));
            sCreationDateMap.put(alias, new Date());
            if (chain != null) {
                sCertChainMap.put(alias, chain);
            }
            saveToDisk();
        }

        @Override
        public void engineSetCertificateEntry(String alias, Certificate cert) throws KeyStoreException {
            if (alias != null && cert != null) {
                sCertChainMap.put(alias, new Certificate[] { cert });
                sCreationDateMap.put(alias, new Date());
                saveToDisk();
            }
        }

        @Override
        public void engineDeleteEntry(String alias) throws KeyStoreException {
            if (alias != null) {
                sKeyMap.remove(alias);
                sCertChainMap.remove(alias);
                sCreationDateMap.remove(alias);
                saveToDisk();
            }
        }

        @Override
        public Enumeration<String> engineAliases() {
            Set<String> all = new HashSet<>(sKeyMap.keySet());
            all.addAll(sCertChainMap.keySet());
            return Collections.enumeration(all);
        }

        @Override
        public boolean engineContainsAlias(String alias) {
            if (alias == null) return false;
            return sKeyMap.containsKey(alias) || sCertChainMap.containsKey(alias)
                    || sKeyMap.containsKey("USRSKEY_" + alias) || sKeyMap.containsKey("USRPKEY_" + alias);
        }

        @Override
        public int engineSize() {
            return sKeyMap.size();
        }

        @Override
        public boolean engineIsKeyEntry(String alias) {
            return sKeyMap.containsKey(alias);
        }

        @Override
        public boolean engineIsCertificateEntry(String alias) {
            return sCertChainMap.containsKey(alias) && !sKeyMap.containsKey(alias);
        }

        @Override
        public String engineGetCertificateAlias(Certificate cert) {
            for (Map.Entry<String, Certificate[]> entry : sCertChainMap.entrySet()) {
                if (entry.getValue() != null && entry.getValue().length > 0 && entry.getValue()[0].equals(cert)) {
                    return entry.getKey();
                }
            }
            return null;
        }

        @Override
        public void engineStore(OutputStream stream, char[] password) throws IOException, NoSuchAlgorithmException, CertificateException {
            saveToDisk();
        }

        @Override
        public void engineLoad(InputStream stream, char[] password) throws IOException, NoSuchAlgorithmException, CertificateException {
            loadFromDisk();
        }

        @Override
        public void engineSetEntry(String alias, KeyStore.Entry entry, KeyStore.ProtectionParameter protParam) throws KeyStoreException {
            if (alias == null || entry == null) return;
            if (entry instanceof KeyStore.SecretKeyEntry) {
                SecretKey sk = ((KeyStore.SecretKeyEntry) entry).getSecretKey();
                engineSetKeyEntry(alias, sk, null, null);
            } else if (entry instanceof KeyStore.PrivateKeyEntry) {
                PrivateKey pk = ((KeyStore.PrivateKeyEntry) entry).getPrivateKey();
                Certificate[] chain = ((KeyStore.PrivateKeyEntry) entry).getCertificateChain();
                engineSetKeyEntry(alias, pk, null, chain);
            } else if (entry instanceof KeyStore.TrustedCertificateEntry) {
                Certificate cert = ((KeyStore.TrustedCertificateEntry) entry).getTrustedCertificate();
                engineSetCertificateEntry(alias, cert);
            }
        }
    }

    public static class KeyGeneratorAES extends KeyGeneratorSpi {
        private String mAlias;
        private int mKeySize = 256;

        @Override
        protected void engineInit(SecureRandom random) {
            this.mKeySize = 256;
        }

        @Override
        protected void engineInit(AlgorithmParameterSpec params, SecureRandom random) throws InvalidAlgorithmParameterException {
            if (params instanceof KeyGenParameterSpec) {
                KeyGenParameterSpec spec = (KeyGenParameterSpec) params;
                this.mAlias = spec.getKeystoreAlias();
                int size = spec.getKeySize();
                if (size > 0) this.mKeySize = size;
            }
        }

        @Override
        protected void engineInit(int keysize, SecureRandom random) {
            this.mKeySize = keysize > 0 ? keysize : 256;
        }

        @Override
        protected SecretKey engineGenerateKey() {
            if (mAlias != null && sKeyMap.containsKey(mAlias)) {
                Key existing = sKeyMap.get(mAlias);
                if (existing instanceof SecretKey) {
                    return (SecretKey) existing;
                }
            }
            byte[] bytes = new byte[mKeySize / 8];
            new SecureRandom().nextBytes(bytes);
            SecretKey key = new SecretKeySpec(bytes, "AES");
            if (mAlias != null) {
                sKeyMap.put(mAlias, key);
                sCreationDateMap.put(mAlias, new Date());
                saveToDisk();
                System.out.println("[+] [AndroidKeyStore] Generated and persisted AES key for alias: " + mAlias);
            }
            return key;
        }
    }

    public static class KeyGeneratorHmac extends KeyGeneratorSpi {
        private String mAlias;
        private int mKeySize = 256;

        @Override
        protected void engineInit(SecureRandom random) {
            this.mKeySize = 256;
        }

        @Override
        protected void engineInit(AlgorithmParameterSpec params, SecureRandom random) throws InvalidAlgorithmParameterException {
            if (params instanceof KeyGenParameterSpec) {
                KeyGenParameterSpec spec = (KeyGenParameterSpec) params;
                this.mAlias = spec.getKeystoreAlias();
                int size = spec.getKeySize();
                if (size > 0) this.mKeySize = size;
            }
        }

        @Override
        protected void engineInit(int keysize, SecureRandom random) {
            this.mKeySize = keysize > 0 ? keysize : 256;
        }

        @Override
        protected SecretKey engineGenerateKey() {
            if (mAlias != null && sKeyMap.containsKey(mAlias)) {
                Key existing = sKeyMap.get(mAlias);
                if (existing instanceof SecretKey) {
                    return (SecretKey) existing;
                }
            }
            byte[] bytes = new byte[mKeySize / 8];
            new SecureRandom().nextBytes(bytes);
            SecretKey key = new SecretKeySpec(bytes, "HmacSHA256");
            if (mAlias != null) {
                sKeyMap.put(mAlias, key);
                sCreationDateMap.put(mAlias, new Date());
                saveToDisk();
            }
            return key;
        }
    }

    private static KeyPairGenerator getUnderlyingKeyPairGenerator(String algorithm) throws NoSuchAlgorithmException {
        for (Provider p : Security.getProviders()) {
            if (!PROVIDER_NAME.equals(p.getName()) && !"AndroidKeyStoreProvider".equals(p.getName())) {
                try {
                    return KeyPairGenerator.getInstance(algorithm, p);
                } catch (NoSuchAlgorithmException ignored) {}
            }
        }
        throw new NoSuchAlgorithmException("No underlying provider found for " + algorithm);
    }

    public static class KeyPairGeneratorRSA extends KeyPairGeneratorSpi {
        private String mAlias;
        private int mKeySize = 2048;

        @Override
        public void initialize(int keysize, SecureRandom random) {
            this.mKeySize = keysize > 0 ? keysize : 2048;
        }

        @Override
        public void initialize(AlgorithmParameterSpec params, SecureRandom random) throws InvalidAlgorithmParameterException {
            if (params instanceof KeyGenParameterSpec) {
                KeyGenParameterSpec spec = (KeyGenParameterSpec) params;
                this.mAlias = spec.getKeystoreAlias();
                int size = spec.getKeySize();
                if (size > 0) this.mKeySize = size;
            }
        }

        @Override
        public KeyPair generateKeyPair() {
            try {
                KeyPairGenerator kpg = getUnderlyingKeyPairGenerator("RSA");
                kpg.initialize(mKeySize);
                KeyPair kp = kpg.generateKeyPair();
                if (mAlias != null) {
                    sKeyMap.put(mAlias, kp.getPrivate());
                    sCertChainMap.put(mAlias, new Certificate[] { new SyntheticX509Certificate(kp.getPublic(), mAlias) });
                    sCreationDateMap.put(mAlias, new Date());
                    saveToDisk();
                    System.out.println("[+] [AndroidKeyStore] Generated and persisted RSA keypair & certificate for alias: " + mAlias);
                }
                return kp;
            } catch (Throwable t) {
                throw new RuntimeException("Failed to generate RSA keypair", t);
            }
        }
    }

    public static class KeyPairGeneratorEC extends KeyPairGeneratorSpi {
        private String mAlias;
        private int mKeySize = 256;

        @Override
        public void initialize(int keysize, SecureRandom random) {
            this.mKeySize = keysize > 0 ? keysize : 256;
        }

        @Override
        public void initialize(AlgorithmParameterSpec params, SecureRandom random) throws InvalidAlgorithmParameterException {
            if (params instanceof KeyGenParameterSpec) {
                KeyGenParameterSpec spec = (KeyGenParameterSpec) params;
                this.mAlias = spec.getKeystoreAlias();
                int size = spec.getKeySize();
                if (size > 0) this.mKeySize = size;
            }
        }

        @Override
        public KeyPair generateKeyPair() {
            try {
                KeyPairGenerator kpg = getUnderlyingKeyPairGenerator("EC");
                kpg.initialize(mKeySize);
                KeyPair kp = kpg.generateKeyPair();
                if (mAlias != null) {
                    sKeyMap.put(mAlias, kp.getPrivate());
                    sCertChainMap.put(mAlias, new Certificate[] { new SyntheticX509Certificate(kp.getPublic(), mAlias) });
                    sCreationDateMap.put(mAlias, new Date());
                    saveToDisk();
                    System.out.println("[+] [AndroidKeyStore] Generated and persisted EC keypair & certificate for alias: " + mAlias);
                }
                return kp;
            } catch (Throwable t) {
                throw new RuntimeException("Failed to generate EC keypair", t);
            }
        }
    }

    /**
     * Synthetic lightweight X.509 certificate fulfilling getPublicKey() and getSubjectDN().
     */
    public static class SyntheticX509Certificate extends X509Certificate implements Serializable {
        private final PublicKey mPublicKey;
        private final String mAlias;

        public SyntheticX509Certificate(PublicKey publicKey, String alias) {
            this.mPublicKey = publicKey;
            this.mAlias = alias != null ? alias : "synthetic_cert";
        }

        @Override
        public PublicKey getPublicKey() {
            return mPublicKey;
        }

        @Override
        public Principal getSubjectDN() {
            return () -> "CN=" + mAlias;
        }

        @Override
        public Principal getIssuerDN() {
            return () -> "CN=AndroidKeyStore";
        }

        @Override
        public Date getNotBefore() {
            return new Date(System.currentTimeMillis() - 86400000L);
        }

        @Override
        public Date getNotAfter() {
            return new Date(System.currentTimeMillis() + 315360000000L);
        }

        @Override
        public byte[] getEncoded() throws CertificateEncodingException {
            return mPublicKey != null ? mPublicKey.getEncoded() : new byte[0];
        }

        @Override
        public void checkValidity() throws java.security.cert.CertificateExpiredException, java.security.cert.CertificateNotYetValidException {}

        @Override
        public void checkValidity(Date date) throws java.security.cert.CertificateExpiredException, java.security.cert.CertificateNotYetValidException {}

        @Override
        public int getVersion() { return 3; }

        @Override
        public BigInteger getSerialNumber() { return BigInteger.valueOf(1L); }

        @Override
        public byte[] getTBSCertificate() { return new byte[0]; }

        @Override
        public byte[] getSignature() { return new byte[0]; }

        @Override
        public String getSigAlgName() { return "SHA256withRSA"; }

        @Override
        public String getSigAlgOID() { return "1.2.840.113549.1.1.11"; }

        @Override
        public byte[] getSigAlgParams() { return null; }

        @Override
        public boolean[] getIssuerUniqueID() { return null; }

        @Override
        public boolean[] getSubjectUniqueID() { return null; }

        @Override
        public boolean[] getKeyUsage() { return null; }

        @Override
        public int getBasicConstraints() { return -1; }

        @Override
        public void verify(PublicKey key) throws CertificateException, NoSuchAlgorithmException, InvalidKeyException, NoSuchProviderException, SignatureException {}

        @Override
        public void verify(PublicKey key, String sigProvider) throws CertificateException, NoSuchAlgorithmException, InvalidKeyException, NoSuchProviderException, SignatureException {}

        @Override
        public String toString() {
            return "SyntheticX509Certificate[" + mAlias + "]";
        }

        @Override
        public boolean hasUnsupportedCriticalExtension() { return false; }

        @Override
        public Set<String> getCriticalExtensionOIDs() { return null; }

        @Override
        public Set<String> getNonCriticalExtensionOIDs() { return null; }

        @Override
        public byte[] getExtensionValue(String oid) { return null; }
    }
}
