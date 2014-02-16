package dbathon.web.gvds.rest;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.hibernate.Session;
import com.google.common.base.Charsets;
import dbathon.web.gvds.entity.User;

@Path("test")
@ApplicationScoped
public class Test {

  @Inject
  private EntityManager entityManager;

  @Inject
  private T2 t2;

  public static void dumpEm(String where, EntityManager entityManager) {
    final Session session = entityManager.unwrap(Session.class);
    System.out.println("!!!!! " + " " + entityManager + " " + entityManager.isJoinedToTransaction()
        + " " + System.identityHashCode(session) + " " + session.getClass() + where);
  }

  @GET
  @Path("foo")
  @Produces(MediaType.TEXT_PLAIN)
  @Transactional
  @AuthorizationRequired
  public Response foo(@QueryParam("arg") String arg) {
    dumpEm("foo", entityManager);

    entityManager.persist(new User(arg, arg));

    return Response.ok("bar " + System.currentTimeMillis() + " " + arg,
        MediaType.TEXT_PLAIN_TYPE.withCharset(Charsets.UTF_8.name())).build();
  }

  @GET
  @Path("foo2")
  @Produces(MediaType.TEXT_PLAIN)
  public Response foo2(@QueryParam("arg") String arg) {
    dumpEm("--foo2", entityManager);
    t2.trans();
    dumpEm("foo2", entityManager);
    t2.noTrans();
    dumpEm("foo2", entityManager);
    t2.trans();
    dumpEm("foo2", entityManager);
    t2.noTrans();
    dumpEm("++foo2", entityManager);

    return Response.ok("bar2 " + System.currentTimeMillis() + " " + arg,
        MediaType.TEXT_PLAIN_TYPE.withCharset(Charsets.UTF_8.name())).build();
  }

}
