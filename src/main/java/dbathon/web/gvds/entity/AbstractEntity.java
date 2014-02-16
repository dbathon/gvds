package dbathon.web.gvds.entity;

import java.io.Serializable;
import javax.persistence.Access;
import javax.persistence.AccessType;
import javax.persistence.MappedSuperclass;

/**
 * Base class for all entites.
 */
@MappedSuperclass
@Access(AccessType.PROPERTY)
public abstract class AbstractEntity implements Serializable {}
