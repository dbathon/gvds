package dbathon.web.gvds.rest;

import java.util.Arrays;
import java.util.List;
import javax.annotation.Priority;
import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;
import javax.persistence.EntityManager;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.NotAuthorizedException;
import javax.xml.bind.DatatypeConverter;
import com.google.common.base.Charsets;
import dbathon.web.gvds.misc.Pbkdf2Service;
import dbathon.web.gvds.util.JpaUtil;

@Interceptor
@Priority(Interceptor.Priority.APPLICATION)
@AuthorizationRequired
@Dependent
public class AuthorizationInterceptor {

  @Inject
  private HttpServletRequest request;

  @Inject
  private AuthorizationContext authorizationContext;

  @Inject
  private EntityManager entityManager;

  @Inject
  private Pbkdf2Service pbkdf2Service;

  private String decodeBase64(String base64) {
    try {
      return new String(DatatypeConverter.parseBase64Binary(base64), Charsets.UTF_8);
    }
    catch (final RuntimeException e) {
      // just return empty string
      return "";
    }
  }

  @AroundInvoke
  public Object authorize(InvocationContext invocationContext) throws Exception {
    final String authHeader = request.getHeader("Authorization");
    if (authHeader != null && authHeader.startsWith("Basic ")) {
      final String base64 = authHeader.substring(6).trim();
      final String decoded = decodeBase64(base64);
      final int indexOfColon = decoded.indexOf(':');

      if (indexOfColon > 0) {
        final String username = decoded.substring(0, indexOfColon);
        final String password = decoded.substring(indexOfColon + 1);

        final List<Object[]> userRows =
            JpaUtil.query(entityManager, Object[].class,
                "select e.id, e.passwordHash from User e where e.username = ?1", username);
        if (userRows.size() == 1) {
          final Object[] row = userRows.get(0);
          final String id = (String) row[0];
          final byte[] hash = (byte[]) row[1];
          final byte[] expectedHash = pbkdf2Service.hashPassword(password, id);
          if (Arrays.equals(hash, expectedHash)) {
            authorizationContext.setUserId(id);
            return invocationContext.proceed();
          }
        }
      }
    }

    throw new NotAuthorizedException("BasicNoPrompt");
  }
}
