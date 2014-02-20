package dbathon.web.gvds.rest;

import java.io.Serializable;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.LockModeType;
import javax.transaction.TransactionScoped;
import javax.ws.rs.core.Response.Status;
import dbathon.web.gvds.entity.User;

@TransactionScoped
public class UserAndVersionContext implements Serializable {

  @Inject
  private AuthorizationContext authorizationContext;

  @Inject
  private EntityManager entityManager;

  private User user;

  boolean locked = false;

  /**
   * @return the authorized user (never <code>null</code>)
   * @throws if no user is available
   */
  public User getUser() {
    if (user != null) {
      return user;
    }
    final String userId = authorizationContext.getUserId();
    if (userId == null) {
      throw new IllegalStateException("userId not set");
    }
    user = entityManager.find(User.class, userId);
    if (user == null) {
      // can theoretically happen...
      throw new RequestError("user no longer exists", Status.CONFLICT);
    }
    return user;
  }

  /**
   * Similar to {@link #getUser()}, but the user will be {@linkplain LockModeType#PESSIMISTIC_WRITE
   * locked} for the current transaction.
   */
  public User getUserForWrite() {
    final User user = getUser();
    if (!locked) {
      entityManager.refresh(user, LockModeType.PESSIMISTIC_WRITE);
      locked = true;
    }
    return user;
  }

}
