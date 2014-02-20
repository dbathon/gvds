package dbathon.web.gvds.rest;

import javax.enterprise.context.RequestScoped;
import com.google.common.base.Preconditions;

@RequestScoped
public class AuthorizationContext {

  private String userId;

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    Preconditions.checkNotNull(userId);
    if (this.userId != null && !this.userId.equals(userId)) {
      throw new IllegalStateException("multiple user ids per request are not allowed");
    }
    this.userId = userId;
  }

}
