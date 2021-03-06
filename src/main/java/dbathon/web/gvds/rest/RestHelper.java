package dbathon.web.gvds.rest;

import java.net.URI;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.validation.ConstraintViolationException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.Response.StatusType;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

@ApplicationScoped
public class RestHelper {

  private static final Log log = LogFactory.getLog(RestHelper.class);

  public static final Object MEDIA_TYPE_JSON_UTF_8 =
      MediaType.APPLICATION_JSON_TYPE.withCharset(Charsets.UTF_8.name());

  @Inject
  private HttpServletRequest request;

  private volatile Gson gson;

  private Gson getGson() {
    Gson gson = this.gson;
    if (gson == null) {
      // no synchronization, multiple inits are ok
      final GsonBuilder gsonBuilder = new GsonBuilder();

      // TODO: make pretty printing configurable per request?
      gsonBuilder.setPrettyPrinting();
      gsonBuilder.disableHtmlEscaping();

      gson = this.gson = gsonBuilder.create();
    }
    return gson;
  }

  public String toJsonString(Object object) {
    if (object instanceof JsonElement) {
      return getGson().toJson((JsonElement) object);
    }
    else {
      return getGson().toJson(object);
    }
  }

  public <T> T fromJsonString(String jsonString, Class<T> classOfT) {
    return getGson().fromJson(jsonString, classOfT);
  }

  public <T> T fromJsonString(String jsonString, TypeToken<T> typeToken) {
    return getGson().fromJson(jsonString, typeToken.getType());
  }

  public Response buildJsonResponse(ResponseBuilder builder, Object object) {
    builder.entity(toJsonString(object).getBytes(Charsets.UTF_8));
    builder.header(HttpHeaders.CONTENT_TYPE, MEDIA_TYPE_JSON_UTF_8);
    return builder.build();
  }

  public Response buildJsonResponse(StatusType status, Object object) {
    return buildJsonResponse(Response.status(status), object);
  }

  public Response buildResultResponse(StatusType status, Object object) {
    return buildJsonResponse(status, ImmutableMap.of("result", object));
  }

  public Response buildErrorResponse(StatusType status, String message) {
    return buildJsonResponse(status, ImmutableMap.of("error", message));
  }

  public Response buildCreatedResponse(String relativePath, Object object) {
    String baseUri = System.getProperty("gvds.baseUri");
    if (baseUri == null) {
      // fallback to current request URL...
      final String requestUrl = request.getRequestURL().toString();
      final String requestPath = request.getRequestURI();
      if (!requestUrl.endsWith(requestPath)) {
        throw new IllegalStateException("requestUrl does not end with requestPath: " + requestUrl
            + ", " + requestPath);
      }
      baseUri =
          requestUrl.substring(0, requestUrl.length() - requestPath.length())
              + request.getContextPath() + "/";
    }

    return buildJsonResponse(Response.created(URI.create(baseUri + relativePath)), object);
  }

  public void invalidRequest() {
    throw new RequestError("invalid request");
  }

  public <T> T notNullOrInvalidRequest(T t) {
    if (t == null) {
      invalidRequest();
    }
    return t;
  }

  public <T> T notNullOr404(T t) {
    if (t == null) {
      throw new RequestError("not found", Status.NOT_FOUND);
    }
    return t;
  }

  protected RuntimeException handleException(RuntimeException exception) {
    // the "current" exception
    Throwable e = exception;
    // prevent infinite loop
    int depth = 0;

    Response response = null;

    while (response == null) {
      if (e instanceof WebApplicationException) {
        // just rethrow
        return (WebApplicationException) e;
      }
      else if (e instanceof ConstraintViolationException) {
        response = buildErrorResponse(Status.BAD_REQUEST, "constraint violation");
      }
      else if (e instanceof JsonParseException) {
        response = buildErrorResponse(Status.BAD_REQUEST, "invalid json");
      }
      // TODO: more?

      // unwrap e and try again
      final Throwable cause = e.getCause();
      if (cause == null || cause == e || ++depth >= 100) {
        break;
      }
      else {
        e = cause;
      }
    }

    if (response != null) {
      log.info("mapped expected exception", e);
      return new WebApplicationException(response);
    }
    else {
      // rethrow original exception, will be "handled" by AnyExceptionMapper
      return exception;
    }
  }

}
