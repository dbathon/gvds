package dbathon.web.gvds.rest;

import java.util.Collections;
import java.util.Set;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import dbathon.web.gvds.entity.DataType;
import dbathon.web.gvds.entity.DataWithVersion;
import dbathon.web.gvds.entity.User;
import dbathon.web.gvds.util.JpaUtil;

/**
 * <ul>
 * <li>GET data/{type}
 * <li>POST data/{type}
 * <li>GET data/{type}/count
 * <li>GET data/{type}/id/{id}
 * <li>PUT data/{type}/id/{id}
 * <li>DELETE data/{type}/id/{id}
 * <li>GET data/{type}/id/{id}/referenced two ways...
 * <li>GET data/'*'
 * <li>GET data/'*'/count
 * <li>GET data/'*'/id/{id}
 * <li>...
 * </ul>
 */
@Path(DataResource.PATH)
@ApplicationScoped
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Transactional
@AuthorizationRequired
public class DataResource {

  static final String PATH = "data";

  @Inject
  private RestHelper restHelper;

  @Inject
  private EntityManager entityManager;

  @Inject
  private UserAndVersionContext userAndVersionContext;

  static class DataDto {
    public DataDto() {}

    public DataDto(String id, Long versionFrom, Long versionTo, String type, String key1,
        String key2, String data, Set<String> references) {
      this.id = id;
      this.versionFrom = versionFrom;
      this.versionTo = versionTo == null || versionTo >= 0 ? versionTo : null;
      this.type = type;
      this.key1 = key1;
      this.key2 = key2;
      this.data = data;
      this.references = references;
    }

    public DataDto(DataWithVersion data) {
      this(data.getId(), data.getVersionFrom(), data.getVersionTo(),
          data.getDataType().getTypeName(), data.getKey1(), data.getKey2(), data.getDataString(),
          data.getReferencesSet());
    }

    public String id;

    public Long versionFrom;
    public Long versionTo;

    public String type;

    public String key1;
    public String key2;

    public String data;

    public Set<String> references;
  }

  private DataDto toDataDto(String jsonString) {
    final DataDto dataDto =
        restHelper.notNullOrInvalidRequest(restHelper.fromJsonString(jsonString, DataDto.class));
    if (dataDto.references == null) {
      dataDto.references = Collections.<String>emptySet();
    }
    return dataDto;
  }

  private DataDto buildCreateUpdateResponseDto(DataWithVersion dataWithVersion) {
    final DataDto result = new DataDto();
    // only return the "metadata"
    result.id = dataWithVersion.getId();
    result.versionFrom = dataWithVersion.getVersionFrom();
    result.type = dataWithVersion.getDataType().getTypeName();
    return result;
  }

  private void validateReferences(Set<String> references) {
    if (!references.isEmpty()) {
      if (references.size() > 500) {
        throw new RequestError("too many references");
      }

      final long expectedCount = references.size();

      final User user = userAndVersionContext.getUserForWrite();

      final long count =
          JpaUtil.queryOne(entityManager, Long.class,
              "select count(e) from DataWithVersion e join e.dataType t "
                  + "where e.id in (?1) and e.versionTo = -1 and t.user = ?2", references, user);

      if (count < expectedCount) {
        throw new RequestError("not all references exist");
      }
      else if (count > expectedCount) {
        throw new IllegalStateException(count + " > " + expectedCount);
      }
    }
  }

  @POST
  @Path("{type}")
  public Response createData(@PathParam("type") String type, String jsonString) {
    final DataDto dataDto = toDataDto(jsonString);

    validateReferences(dataDto.references);

    final DataWithVersion dataWithVersion =
        new DataWithVersion(userAndVersionContext.getOrCreateDataType(type),
            userAndVersionContext.getNextVersion(), dataDto.key1, dataDto.key2, dataDto.data,
            dataDto.references);

    entityManager.persist(dataWithVersion);

    return restHelper.buildCreatedResponse(PATH + "/" + type + "/id/" + dataWithVersion.getId(),
        buildCreateUpdateResponseDto(dataWithVersion));
  }

  private DataWithVersion find(String id, String type) {
    final DataType dataType = userAndVersionContext.getDataType(type);
    if (dataType != null) {
      return JpaUtil.queryOneOrNone(entityManager, DataWithVersion.class,
          "select e from DataWithVersion e "
              + "where e.id = ?1 and e.versionTo = -1 and e.dataType = ?2", id, dataType);
    }
    return null;
  }

  @GET
  @Path("{type}/id/{id}")
  public Response getData(@PathParam("type") String type, @PathParam("id") String id) {
    final DataWithVersion dataWithVersion = restHelper.notNullOr404(find(id, type));
    return restHelper.buildJsonResponse(Status.OK, new DataDto(dataWithVersion));
  }

  @DELETE
  @Path("{type}/id/{id}")
  public Response deleteData(@PathParam("type") String type, @PathParam("id") String id) {
    // lock first
    userAndVersionContext.getUserForWrite();

    final DataWithVersion dataWithVersion = restHelper.notNullOr404(find(id, type));
    dataWithVersion.setVersionTo(userAndVersionContext.getNextVersion() - 1);
    if (dataWithVersion.getVersionTo() < dataWithVersion.getVersionFrom()) {
      // should not happen...
      throw new IllegalStateException("data created and deleted in same transaction");
    }

    // TODO: validate no incoming references

    return Response.noContent().build();
  }

  @PUT
  @Path("{type}/id/{id}")
  public Response updateData(@PathParam("type") String type, @PathParam("id") String id,
      String jsonString) {
    // lock first
    userAndVersionContext.getUserForWrite();

    final DataWithVersion dataWithVersion = restHelper.notNullOr404(find(id, type));

    final DataDto dataDto = toDataDto(jsonString);

    validateReferences(dataDto.references);

    final DataWithVersion newDataWithVersion =
        new DataWithVersion(dataWithVersion, userAndVersionContext.getNextVersion(), dataDto.key1,
            dataDto.key2, dataDto.data, dataDto.references);

    if (dataWithVersion.getVersionTo() < dataWithVersion.getVersionFrom()) {
      // could happen if the same data is updated twice in the same transaction, forbid for now...
      throw new RequestError("multiple update to the same data is not allowed");
    }

    entityManager.persist(newDataWithVersion);

    return restHelper.buildJsonResponse(Status.OK, buildCreateUpdateResponseDto(newDataWithVersion));
  }

}
