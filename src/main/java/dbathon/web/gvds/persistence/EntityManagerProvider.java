package dbathon.web.gvds.persistence;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

/**
 * Provides the transaction scoped {@link EntityManager} (proxy) for the gvds persistence unit via
 * {@link Inject}.
 */
@ApplicationScoped
public class EntityManagerProvider {

  @Produces
  @PersistenceContext
  private EntityManager entityManager;

}
