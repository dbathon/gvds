package dbathon.web.gvds.rest;

import java.util.ArrayList;
import java.util.List;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import dbathon.web.gvds.util.JpaUtil;

@Path("type")
@ApplicationScoped
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Transactional
@AuthorizationRequired
public class TypeResource {

  @Inject
  private RestHelper restHelper;

  @Inject
  private EntityManager entityManager;

  @Inject
  private UserAndVersionContext userAndVersionContext;

  static class TypeDto {
    public TypeDto() {}

    public TypeDto(String name) {
      this.name = name;
    }

    public String name;
  }

  @GET
  public Response getTypeList() {
    final List<String> typeNames =
        JpaUtil.query(entityManager, String.class, "select e.typeName from DataType e "
            + "where e.user = ?1 order by e.typeName", userAndVersionContext.getUser());

    final List<TypeDto> result = new ArrayList<>(typeNames.size());
    for (final String typeName : typeNames) {
      result.add(new TypeDto(typeName));
    }

    return restHelper.buildResultResponse(Status.OK, result);
  }

}
