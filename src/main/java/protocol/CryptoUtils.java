package protocol;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class CryptoUtils {
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/ECB/PKCS5Padding";
    private static final byte[] FIXED_KEY = "0123456789abcdef".getBytes(StandardCharsets.UTF_8);
    private static final SecretKeySpec secretKey = new SecretKeySpec(FIXED_KEY, ALGORITHM);

    public static byte[] encrypt(byte[] data) throws Exception {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        return cipher.doFinal(data);
    }

    public static byte[] decrypt(byte[] encryptedData) throws Exception {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, secretKey);
        return cipher.doFinal(encryptedData);
    }

    public static void sendEncrypted(ObjectOutputStream out, Object obj) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(obj);
        oos.flush();
        byte[] encrypted = encrypt(baos.toByteArray());
        out.writeInt(encrypted.length);
        out.write(encrypted);
        out.flush();
    }

    public static Object receiveEncrypted(ObjectInputStream in) throws Exception {
        int length = in.readInt();
        byte[] encrypted = new byte[length];
        in.readFully(encrypted);
        byte[] decrypted = decrypt(encrypted);
        ByteArrayInputStream bais = new ByteArrayInputStream(decrypted);
        ObjectInputStream ois = new ObjectInputStream(bais);
        return ois.readObject();
    }

    public static String encryptString(String plainText) throws Exception {
        byte[] encrypted = encrypt(plainText.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encrypted);
    }

    public static String decryptString(String encryptedText) throws Exception {
        byte[] decoded = Base64.getDecoder().decode(encryptedText);
        byte[] decrypted = decrypt(decoded);
        return new String(decrypted, StandardCharsets.UTF_8);
    }
}