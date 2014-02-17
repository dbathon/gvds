package dbathon.web.gvds.misc;

import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.enterprise.context.ApplicationScoped;
import com.google.common.base.Charsets;

@ApplicationScoped
public class Pbkdf2Service {

  /**
   * TODO: good/more/less?
   */
  private static final int ITERATIONS = 8000;

  /**
   * TODO: maybe cache the hash for some time to reduce the overhead for every request
   */
  public byte[] hashPassword(String password, String salt) {
    final PBEKeySpec spec =
        new PBEKeySpec(password.toCharArray(), salt.getBytes(Charsets.UTF_8), ITERATIONS, 256);

    try {
      final SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
      return secretKeyFactory.generateSecret(spec).getEncoded();
    }
    catch (final NoSuchAlgorithmException | InvalidKeySpecException e) {
      throw new RuntimeException(e);
    }
  }

}
