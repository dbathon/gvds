package dbathon.web.gvds.rest;

import javax.annotation.Priority;
import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.NotAuthorizedException;
import javax.xml.bind.DatatypeConverter;
import com.google.common.base.Charsets;

@Interceptor
@Priority(Interceptor.Priority.APPLICATION)
@AuthorizationRequired
@Dependent
public class AuthorizationInterceptor {

  @Inject
  private HttpServletRequest request;

  private String decodeBase64(String base64) {
    return new String(DatatypeConverter.parseBase64Binary(base64), Charsets.UTF_8);
  }

  @AroundInvoke
  public Object authorize(InvocationContext invocationContext) throws Exception {
    final String authHeader = request.getHeader("Authorization");
    if (authHeader != null && authHeader.startsWith("Basic ")) {
      final String base64 = authHeader.substring(6).trim();
      System.out.println(base64 + " -> " + decodeBase64(base64));

      if ("foo:bar".equals(decodeBase64(base64))) {
        return invocationContext.proceed();
      }
    }

    throw new NotAuthorizedException("BasicNoPrompt");
  }

}
