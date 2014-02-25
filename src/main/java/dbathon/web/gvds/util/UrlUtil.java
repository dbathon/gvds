package dbathon.web.gvds.util;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import com.google.common.base.Charsets;

public final class UrlUtil {

  private UrlUtil() {}

  private static final String UTF_8 = Charsets.UTF_8.name();

  /**
   * See {@link URLEncoder#encode(String, String)}.
   */
  public static String urlEncode(String unencoded) {
    try {
      return URLEncoder.encode(unencoded, UTF_8);
    }
    catch (final UnsupportedEncodingException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * See {@link URLDecoder#decode(String, String)}.
   */
  public static String urlDecode(String encoded) {
    try {
      return URLDecoder.decode(encoded, UTF_8);
    }
    catch (final UnsupportedEncodingException e) {
      throw new IllegalStateException(e);
    }
  }

}
