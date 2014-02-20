package dbathon.web.gvds.misc;

import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.concurrent.TimeUnit;
import javax.annotation.PostConstruct;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.enterprise.context.ApplicationScoped;
import com.google.common.base.Charsets;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;

@ApplicationScoped
public class Pbkdf2Service {

  /**
   * TODO: good/more/less?
   */
  private static final int ITERATIONS = 8000;

  private LoadingCache<ImmutableList<String>, byte[]> cache;

  @PostConstruct
  private void postConstruct() {
    cache =
        CacheBuilder.newBuilder().expireAfterAccess(10, TimeUnit.MINUTES).maximumSize(1000).build(
            new CacheLoader<ImmutableList<String>, byte[]>() {
              @Override
              public byte[] load(ImmutableList<String> key) throws Exception {
                return hashPasswordInternal(key.get(0), key.get(1));
              }
            });
  }

  private byte[] hashPasswordInternal(String password, String salt) {
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

  public byte[] hashPassword(String password, String salt) {
    final byte[] hash = cache.getUnchecked(ImmutableList.of(password, salt));
    // clone the array to prevent external modification
    return hash.clone();
  }

}
