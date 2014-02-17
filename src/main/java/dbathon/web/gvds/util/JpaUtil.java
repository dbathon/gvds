package dbathon.web.gvds.util;

import java.util.List;
import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;

public final class JpaUtil {

  private JpaUtil() {}

  public static <T> List<T> query(EntityManager entityManager, Class<T> resultType,
      String queryString, Object... parameters) {
    final TypedQuery<T> query = entityManager.createQuery(queryString, resultType);
    if (parameters != null) {
      for (int i = 0; i < parameters.length; ++i) {
        query.setParameter(i + 1, parameters[i]);
      }
    }
    return query.getResultList();
  }

}
