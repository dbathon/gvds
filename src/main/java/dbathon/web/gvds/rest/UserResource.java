package dbathon.web.gvds.rest;

import java.util.List;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import com.google.common.base.Strings;
import dbathon.web.gvds.entity.User;
import dbathon.web.gvds.misc.Pbkdf2Service;
import dbathon.web.gvds.util.JpaUtil;

@Path("user")
@ApplicationScoped
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class UserResource {

  @Inject
  private RestHelper restHelper;

  @Inject
  private EntityManager entityManager;

  @Inject
  private Pbkdf2Service pbkdf2Service;

  static class UserDto {
    public String username;
    public String password;
    public long version;
  }

  /**
   * TODO: for now everybody can create new users.
   */
  @POST
  @Transactional
  public Response createUser(String jsonString) {
    final UserDto userDto =
        restHelper.notNullOrInvalidRequest(restHelper.fromJsonString(jsonString, UserDto.class));
    if (Strings.isNullOrEmpty(userDto.username) || Strings.isNullOrEmpty(userDto.password)) {
      restHelper.invalidRequest();
    }
    final List<String> existing =
        JpaUtil.query(entityManager, String.class, "select e.id from User e where e.username = ?1",
            userDto.username);
    if (!existing.isEmpty()) {
      throw new RequestError("user already exists", Status.CONFLICT);
    }

    final User user = new User(userDto.username);
    user.setPasswordHash(pbkdf2Service.hashPassword(userDto.password, user.getId()));
    entityManager.persist(user);
    return restHelper.buildResultResponse(Status.OK, "user created");
  }

}
