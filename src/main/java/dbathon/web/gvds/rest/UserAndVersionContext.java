package dbathon.web.gvds.rest;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.LockModeType;
import javax.transaction.TransactionScoped;
import javax.ws.rs.core.Response.Status;
import dbathon.web.gvds.entity.DataType;
import dbathon.web.gvds.entity.User;
import dbathon.web.gvds.util.JpaUtil;

@TransactionScoped
public class UserAndVersionContext implements Serializable {

  @Inject
  private AuthorizationContext authorizationContext;

  @Inject
  private EntityManager entityManager;

  private User user;

  private boolean locked = false;

  private Long nextVersion = null;

  private final Map<String, DataType> dataTypes = new HashMap<String, DataType>();

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

  /**
   * Calling this method implicitly locks the user (see {@link #getUserForWrite()}) if it isn't
   * locked already.
   * 
   * @return the version that is created by the current transaction
   */
  public long getNextVersion() {
    if (nextVersion == null) {
      final User user = getUserForWrite();
      // TODO: prevent overflow...
      nextVersion = user.getVersion() + 1;
      user.setVersion(nextVersion);
    }
    return nextVersion;
  }

  /**
   * @param typeName
   * @return the {@link DataType} instance (if it exists) or <code>null</code>
   */
  public DataType getDataType(String typeName) {
    final DataType dataType = dataTypes.get(typeName);
    if (dataType != null) {
      return dataType;
    }

    final List<DataType> result =
        JpaUtil.query(entityManager, DataType.class,
            "select e from DataType e where e.user = ?1 and e.typeName = ?2", getUser(), typeName);
    if (result.isEmpty()) {
      return null;
    }
    else if (result.size() == 1) {
      dataTypes.put(typeName, result.get(0));
      return result.get(0);
    }
    else {
      throw new IllegalStateException("non-unique DataType: " + typeName);
    }
  }

  /**
   * Creates the {@link DataType} if necessary (in that case the user is implicitly locked, see
   * {@link #getUserForWrite()}).
   * 
   * @param typeName
   * @return the {@link DataType} instance
   */
  public DataType getOrCreateDataType(String typeName) {
    final DataType dataType = getDataType(typeName);
    if (dataType != null) {
      return dataType;
    }
    if (locked) {
      // already locked, just create a new data type
      final DataType newDataType = new DataType(typeName, getUserForWrite());
      entityManager.persist(newDataType);
      return newDataType;
    }
    else {
      // lock the user
      getUserForWrite();
      // and try again
      return getOrCreateDataType(typeName);
    }
  }

}
