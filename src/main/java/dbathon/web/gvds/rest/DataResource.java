package dbathon.web.gvds.rest;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import javax.transaction.Transactional;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;
import com.google.common.base.Splitter;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Sets;
import dbathon.web.gvds.entity.DataType;
import dbathon.web.gvds.entity.DataWithVersion;
import dbathon.web.gvds.entity.User;
import dbathon.web.gvds.persistence.WhereClauseBuilder;
import dbathon.web.gvds.util.JpaUtil;
import dbathon.web.gvds.util.StringToBytes;
import dbathon.web.gvds.util.UrlUtil;

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

    public Boolean key1Unique;
    public Boolean key2Unique;

    public String data;

    public Set<String> references;
  }

  private DataDto toDataDto(String jsonString) {
    final DataDto dataDto =
        restHelper.notNullOrInvalidRequest(restHelper.fromJsonString(jsonString, DataDto.class));
    if (dataDto.references == null) {
      dataDto.references = Collections.<String>emptySet();
    }
    else {
      // make sure references does not contain null
      dataDto.references.remove(null);
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

  private void checkUniquenessSingle(DataType dataType, final String key, final String value) {
    final long count =
        JpaUtil.queryOne(entityManager, Long.class, "select count(e) from DataWithVersion e "
            + "where e." + key + " = ?1 and e.versionTo = -1 and e.dataType = ?2", value, dataType);
    if (count > 1) {
      throw new RequestError(key + " is not unique", Status.CONFLICT);
    }
  }

  private void checkUniqueness(DataDto dataDto, DataType dataType) {
    if (dataDto.key1 != null && Boolean.TRUE.equals(dataDto.key1Unique)) {
      checkUniquenessSingle(dataType, "key1", dataDto.key1);
    }
    if (dataDto.key2 != null && Boolean.TRUE.equals(dataDto.key2Unique)) {
      checkUniquenessSingle(dataType, "key2", dataDto.key2);
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

    checkUniqueness(dataDto, dataWithVersion.getDataType());

    return restHelper.buildCreatedResponse(PATH + "/" + UrlUtil.urlEncode(type) + "/id/"
        + dataWithVersion.getId(), buildCreateUpdateResponseDto(dataWithVersion));
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
    final User user = userAndVersionContext.getUserForWrite();

    final DataWithVersion dataWithVersion = restHelper.notNullOr404(find(id, type));

    final long referencesCount =
        JpaUtil.queryOne(entityManager, Long.class,
            "select count(e) from DataWithVersion e join e.dataType t join e.references r "
                + "where r = ?1 and not e.id = ?1 and e.versionTo = -1 and t.user = ?2", id, user);

    if (referencesCount > 0) {
      throw new RequestError("data cannot be deleted, because it is referenced by other data",
          Status.CONFLICT);
    }

    dataWithVersion.setVersionTo(userAndVersionContext.getNextVersion() - 1);
    if (dataWithVersion.getVersionTo() < dataWithVersion.getVersionFrom()) {
      // should not happen...
      throw new IllegalStateException("data created and deleted in same transaction");
    }

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

    if (dataDto.id != null && !id.equals(dataDto.id)) {
      // if the id is given, then it must match
      throw new RequestError("ids are not matching");
    }
    if (dataDto.type != null && !type.equals(dataDto.type)) {
      // if the type is given, then it must match
      throw new RequestError("types are not matching");
    }

    if (Objects.equals(dataDto.key1, dataWithVersion.getKey1())
        && Objects.equals(dataDto.key2, dataWithVersion.getKey2())
        && Objects.equals(dataDto.data, dataWithVersion.getDataString())
        && dataWithVersion.getReferencesSet().equals(dataDto.references)) {
      // no changes, so don't update
      return restHelper.buildJsonResponse(Status.OK, buildCreateUpdateResponseDto(dataWithVersion));
    }

    /**
     * If there are changes and versionFrom is given then it must match the "old" versionFrom of
     * data, this can be used for optimistic locking.
     */
    if (dataDto.versionFrom != null && dataDto.versionFrom != dataWithVersion.getVersionFrom()) {
      throw new RequestError("versionFrom does not match", Status.CONFLICT);
    }

    validateReferences(dataDto.references);

    final DataWithVersion newDataWithVersion =
        new DataWithVersion(dataWithVersion, userAndVersionContext.getNextVersion(), dataDto.key1,
            dataDto.key2, dataDto.data, dataDto.references);

    if (dataWithVersion.getVersionTo() < dataWithVersion.getVersionFrom()) {
      // could happen if the same data is updated twice in the same transaction, forbid for now...
      throw new RequestError("multiple update to the same data is not allowed");
    }

    entityManager.persist(newDataWithVersion);

    checkUniqueness(dataDto, newDataWithVersion.getDataType());

    return restHelper.buildJsonResponse(Status.OK, buildCreateUpdateResponseDto(newDataWithVersion));
  }

  private <T> T parseJson(String jsonString, Class<T> classOfT) {
    return jsonString == null ? null : restHelper.fromJsonString(jsonString, classOfT);
  }

  private static final Splitter COMMA_SPLITTER = Splitter.on(",");

  private static final String QUERY_FROM = "from DataWithVersion e left join e.dataType t";

  private static final Pattern CONDITION_PATTERN = Pattern.compile("((?:or\\w*-)?)(\\w+)-(\\w+)");

  private enum Property {
    id("e.id", String.class),
    versionFrom("e.versionFrom", Long.class),
    versionTo("e.versionTo", Long.class),
    type("t.typeName", String.class),
    key1("e.key1", String.class),
    key2("e.key2", String.class),
    data("e.data") {
      @Override
      public Object extractValue(Object queryResult) {
        return queryResult == null ? null : StringToBytes.toString((byte[]) queryResult);
      }
    },
    references("e.referencesInline") {
      @Override
      public Object extractValue(Object queryResult) {
        return queryResult == null ? null : DataWithVersion.toReferencesSet((byte[]) queryResult);
      }
    };

    private final String queryExpression;

    private final Class<?> expressionType;

    private Property(String queryExpression, Class<?> expressionType) {
      this.queryExpression = queryExpression;
      this.expressionType = expressionType;
    }

    private Property(String queryExpression) {
      this(queryExpression, null);
    }

    public String getQueryExpression() {
      return queryExpression;
    }

    public Class<?> getExpressionType() {
      return expressionType;
    }

    public boolean isOrderPossible() {
      return expressionType != null;
    }

    public Object extractValue(Object queryResult) {
      return queryResult;
    }

    public static Property forName(String name) {
      try {
        return Property.valueOf(name);
      }
      catch (final RuntimeException e) {
        throw new RequestError("unknown property: " + name);
      }
    }

    public static Property forNameOrNull(String name) {
      try {
        return Property.valueOf(name);
      }
      catch (final RuntimeException ignore) {
        return null;
      }
    }
  }

  private void addSimpleCondition(WhereClauseBuilder wcb, Property property, String operator,
      String valueStr) {
    final Class<?> type = property.getExpressionType();
    if (type == null) {
      return;
    }

    final Object value;
    if (operator.endsWith("null") || operator.endsWith("in")) {
      // no value needed or handled below
      value = null;
    }
    else if (type == String.class) {
      value = valueStr;
    }
    else {
      value = parseJson(valueStr, type);
    }

    switch (operator) {
    case "null":
      wcb.add(property.getQueryExpression() + " is null");
      return;
    case "notNull":
      wcb.add(property.getQueryExpression() + " is not null");
      return;
    }

    if (type == String.class || Number.class.isAssignableFrom(type)) {
      // string or number
      switch (operator) {
      case "eq":
        wcb.add(property.getQueryExpression() + " = ?", value);
        return;
      case "ne":
        wcb.add(property.getQueryExpression() + " <> ?", value);
        return;
      case "lt":
        wcb.add(property.getQueryExpression() + " < ?", value);
        return;
      case "gt":
        wcb.add(property.getQueryExpression() + " > ?", value);
        return;
      case "le":
        wcb.add(property.getQueryExpression() + " <= ?", value);
        return;
      case "ge":
        wcb.add(property.getQueryExpression() + " >= ?", value);
        return;
      }
      if ("in".equals(operator) || "notin".equals(operator)) {
        // TODO: improve
        final Class<?> arrayType = Array.newInstance(type, 0).getClass();
        final Object array = parseJson(valueStr, arrayType);
        final List<Object> list = Arrays.asList((Object[]) array);
        for (final Object o : list) {
          if (o == null) {
            throw new RequestError("list parameter cannot contain null");
          }
        }
        wcb.add(property.getQueryExpression() + " " + ("in".equals(operator) ? "in" : "not in")
            + " (?)", list);
        return;
      }
    }

    if (type == String.class) {
      // string only
      switch (operator) {
      case "like":
        wcb.add(property.getQueryExpression() + " like ?", value);
        return;
      case "notLike":
        wcb.add(property.getQueryExpression() + " not like ?", value);
        return;
      case "ilike":
        wcb.add("lower(" + property.getQueryExpression() + ") like lower(?)", value);
        return;
      case "notIlike":
        wcb.add("lower(" + property.getQueryExpression() + ") not like lower(?)", value);
        return;
      }
    }
  }

  private WhereClauseBuilder buildWhereClause(final DataType dataType,
      MultivaluedMap<String, String> queryParameters) {
    final WhereClauseBuilder wcb = new WhereClauseBuilder();
    wcb.add("e.dataType = ?", dataType);

    final Long version = parseJson(queryParameters.getFirst("version"), Long.class);

    if (version == null) {
      wcb.add("e.versionTo = -1");
    }
    else {
      wcb.add("e.versionFrom <= ? and (e.versionTo >= ? or e.versionTo = -1)", version, version);
    }

    final ListMultimap<String, Object[]> orGroups = ArrayListMultimap.create();

    for (final Entry<String, List<String>> entry : queryParameters.entrySet()) {
      final Matcher matcher = CONDITION_PATTERN.matcher(entry.getKey());
      if (matcher.matches()) {
        final Property property = Property.forNameOrNull(matcher.group(2));
        if (property != null) {
          final String operator = matcher.group(3);
          final String orGroup = matcher.group(1);

          for (final String value : entry.getValue()) {
            if (orGroup.isEmpty()) {
              addSimpleCondition(wcb, property, operator, value);
            }
            else {
              orGroups.put(orGroup, new Object[] {
                  property, operator, value
              });
            }
          }
        }
      }
    }

    for (final Collection<Object[]> orGroupList : orGroups.asMap().values()) {
      wcb.startOr();
      for (final Object[] orGroupEntry : orGroupList) {
        // wrap each condition in an and (in case it consists of multiple adds
        wcb.startAnd();

        addSimpleCondition(wcb, (Property) orGroupEntry[0], (String) orGroupEntry[1],
            (String) orGroupEntry[2]);

        wcb.finishAnd();
      }
      wcb.finishOr();
    }

    // TODO filter by references...

    return wcb;
  }

  private Object extractPropertyValue(Property property, Map<Property, Integer> propertyToColumn,
      Object[] row) {
    final Integer index = propertyToColumn.get(property);
    if (index != null) {
      return property.extractValue(row[index]);
    }
    else {
      return null;
    }
  }

  @GET
  @Path("{type}")
  public Response queryData(@PathParam("type") String type, @Context UriInfo uriInfo) {
    final DataType dataType = userAndVersionContext.getDataType(type);
    if (dataType == null) {
      return restHelper.buildResultResponse(Status.OK, Collections.emptyList());
    }

    final MultivaluedMap<String, String> queryParameters = uriInfo.getQueryParameters();

    final Set<String> include;
    if (queryParameters.containsKey("include")) {
      include = Sets.newHashSet(COMMA_SPLITTER.split(queryParameters.getFirst("include")));
    }
    else {
      include = null;
    }

    final StringBuilder q = new StringBuilder("select ");

    final Map<Property, Integer> propertyToColumn = new EnumMap<>(Property.class);

    int columnCount = 0;
    for (final Property property : Property.values()) {
      if (include == null || include.contains(property.name())) {
        if (columnCount > 0) {
          q.append(", ");
        }
        q.append(property.getQueryExpression());
        propertyToColumn.put(property, columnCount);
        ++columnCount;
      }
    }

    // add dummy columns to ensure we have at least two...
    while (columnCount < 2) {
      if (columnCount > 0) {
        q.append(", ");
      }
      q.append(Property.versionFrom.getQueryExpression());
      ++columnCount;
    }

    q.append(" ").append(QUERY_FROM);

    final WhereClauseBuilder wcb = buildWhereClause(dataType, queryParameters);

    q.append(wcb.buildWhereClause());

    q.append(" order by ");

    final String order = queryParameters.getFirst("order");
    if (order != null) {
      for (String orderPart : COMMA_SPLITTER.split(order)) {
        boolean desc = false;
        if (orderPart.startsWith("-")) {
          desc = true;
          orderPart = orderPart.substring(1);
        }
        final Property property = Property.forName(orderPart);
        if (!property.isOrderPossible()) {
          throw new RequestError("cannot order by " + orderPart);
        }
        q.append(property.getQueryExpression()).append(desc ? " desc" : " asc").append(", ");
      }
    }

    // always order by id last, to have a deterministic ordering
    q.append("e.id");

    System.out.println("query: " + q.toString());

    final TypedQuery<Object[]> query =
        wcb.applyParameters(entityManager.createQuery(q.toString(), Object[].class));

    final Integer first = parseJson(queryParameters.getFirst("first"), Integer.class);
    Integer max = parseJson(queryParameters.getFirst("max"), Integer.class);
    if (max == null || max > 1000) {
      // at most 1000 rows
      max = 1000;
    }
    if (first != null) {
      query.setFirstResult(first);
    }
    query.setMaxResults(max);

    final List<Object[]> queryResult = query.getResultList();

    final List<DataDto> result = new ArrayList<>(queryResult.size());

    for (final Object[] row : queryResult) {
      @SuppressWarnings("unchecked")
      final Set<String> rowReferences =
          (Set<String>) extractPropertyValue(Property.references, propertyToColumn, row);
      result.add(new DataDto((String) extractPropertyValue(Property.id, propertyToColumn, row),
          (Long) extractPropertyValue(Property.versionFrom, propertyToColumn, row),
          (Long) extractPropertyValue(Property.versionTo, propertyToColumn, row),
          (String) extractPropertyValue(Property.type, propertyToColumn, row),
          (String) extractPropertyValue(Property.key1, propertyToColumn, row),
          (String) extractPropertyValue(Property.key2, propertyToColumn, row),
          (String) extractPropertyValue(Property.data, propertyToColumn, row), rowReferences));
    }

    return restHelper.buildResultResponse(Status.OK, result);
  }

  @GET
  @Path("{type}/count")
  public Response countData(@PathParam("type") String type, @Context UriInfo uriInfo) {
    final DataType dataType = userAndVersionContext.getDataType(type);
    if (dataType == null) {
      return restHelper.buildResultResponse(Status.OK, 0L);
    }

    final WhereClauseBuilder wcb = buildWhereClause(dataType, uriInfo.getQueryParameters());

    final String q = "select count(e) " + QUERY_FROM + wcb.buildWhereClause();

    System.out.println("count query: " + q);

    final TypedQuery<Long> query = wcb.applyParameters(entityManager.createQuery(q, Long.class));

    return restHelper.buildResultResponse(Status.OK, query.getSingleResult());
  }

}
