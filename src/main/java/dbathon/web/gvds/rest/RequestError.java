package dbathon.web.gvds.rest;

import javax.ws.rs.core.Response.Status;
import com.google.common.base.Preconditions;

/**
 * Will be mapped to an appropriate response using {@link #getStatus()} and {@link #getMessage()} by
 * {@link AnyExceptionMapper}.
 */
public class RequestError extends RuntimeException {

  private final Status status;

  public RequestError(String message, Status status) {
    super(message);
    this.status = Preconditions.checkNotNull(status);
  }

  public RequestError(String message, Throwable cause, Status status) {
    super(message, cause);
    this.status = Preconditions.checkNotNull(status);
  }

  public RequestError(String message) {
    this(message, Status.BAD_REQUEST);
  }

  public RequestError(String message, Throwable cause) {
    this(message, cause, Status.BAD_REQUEST);
  }

  public Status getStatus() {
    return status;
  }

}
