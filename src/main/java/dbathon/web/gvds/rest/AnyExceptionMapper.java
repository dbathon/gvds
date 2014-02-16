package dbathon.web.gvds.rest;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

@Provider
public class AnyExceptionMapper implements ExceptionMapper<Throwable> {

  private static final Log log = LogFactory.getLog(AnyExceptionMapper.class);

  @Override
  public Response toResponse(Throwable exception) {
    if (exception instanceof WebApplicationException) {
      // no logging for these exceptions, just return the response
      return ((WebApplicationException) exception).getResponse();
    }

    log.warn("request failed", exception);
    // just return an empty 500 response
    return Response.serverError().build();
  }

}
