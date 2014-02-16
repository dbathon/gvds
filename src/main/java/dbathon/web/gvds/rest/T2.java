package dbathon.web.gvds.rest;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;

@ApplicationScoped
public class T2 {

  @Inject
  private EntityManager entityManager;

  @Transactional
  public void trans() {
    Test.dumpEm("transactional", entityManager);
  }

  public void noTrans() {
    Test.dumpEm("noTransactional", entityManager);
  }

}
