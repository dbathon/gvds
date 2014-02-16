package dbathon.web.gvds.util;

import com.google.common.base.Charsets;

/**
 * Methods to convert a string to bytes (and back), possibly compressed...
 * <p>
 * TODO: compression...
 */
public final class StringToBytes {

  public static final byte ENCODING_SIMPLE_UTF8 = 1;

  private StringToBytes() {}

  public static byte[] toBytes(String string) {
    if (string == null) {
      return null;
    }

    // for now there is only one "encoding"
    final byte[] tmp = string.getBytes(Charsets.UTF_8);
    final byte[] result = new byte[tmp.length + 1];
    result[0] = ENCODING_SIMPLE_UTF8;
    System.arraycopy(tmp, 0, result, 1, tmp.length);
    return result;
  }

  public static String toString(byte[] bytes) {
    if (bytes == null) {
      return null;
    }

    if (bytes.length < 1 || bytes[0] != ENCODING_SIMPLE_UTF8) {
      throw new IllegalArgumentException("unexpected byte array");
    }

    return new String(bytes, 1, bytes.length - 1, Charsets.UTF_8);
  }

}
