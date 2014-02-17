package dbathon.web.gvds.rest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.PersistenceException;
import javax.validation.ConstraintViolationException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import com.google.gson.JsonParseException;

@Provider
@ApplicationScoped
public class AnyExceptionMapper implements ExceptionMapper<Throwable> {

  private static final Log log = LogFactory.getLog(AnyExceptionMapper.class);

  @Inject
  private RestHelper restHelper;

  private List<Throwable> reverseCauseList(Throwable throwable) {
    final List<Throwable> result = new ArrayList<>();

    // prevent infinite loop
    int depth = 0;

    while (throwable != null && ++depth <= 100) {
      result.add(throwable);
      throwable = throwable.getCause();
    }

    Collections.reverse(result);
    return result;
  }

  @Override
  public Response toResponse(Throwable exception) {
    Response response = null;

    // try to find an "expected" exception from the inside out
    for (final Throwable e : reverseCauseList(exception)) {
      if (e instanceof WebApplicationException) {
        // no logging for these exceptions, just return the response
        return ((WebApplicationException) e).getResponse();
      }
      else if (e instanceof RequestError) {
        final RequestError requestError = (RequestError) e;
        // no logging
        return restHelper.buildErrorResponse(requestError.getStatus(), requestError.getMessage());
      }
      else if (e instanceof ConstraintViolationException) {
        response = restHelper.buildErrorResponse(Status.BAD_REQUEST, "constraint violation");
      }
      else if (e instanceof JsonParseException) {
        response = restHelper.buildErrorResponse(Status.BAD_REQUEST, "invalid json");
      }
      else if (e instanceof PersistenceException) {
        response = restHelper.buildErrorResponse(Status.CONFLICT, "persistence exception");
      }
      // TODO: more?

      if (response != null) {
        log.info("request failed with expected exception", e);
        return response;
      }
    }

    // log original exception
    log.warn("request failed with unexpected exception", exception);
    return restHelper.buildErrorResponse(Status.INTERNAL_SERVER_ERROR, "unexpected error");
  }

}
