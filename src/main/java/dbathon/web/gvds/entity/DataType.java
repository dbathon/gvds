package dbathon.web.gvds.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

@Entity
@Table(name = "DATA_TYPE", uniqueConstraints = @UniqueConstraint(columnNames = {
    "ID_USER", "TYPE_NAME"
}))
public class DataType extends AbstractEntityWithUuidAsId {

  private String typeName;
  private User user;

  protected DataType() {}

  public DataType(String typeName, User user) {
    this.typeName = typeName;
    this.user = user;
  }

  @Column(name = "TYPE_NAME", nullable = false, length = 1000)
  @NotNull
  @Size(min = 1, max = 1000)
  public String getTypeName() {
    return typeName;
  }

  protected void setTypeName(String typeName) {
    this.typeName = typeName;
  }

  /**
   * The "owner" of the type.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "ID_USER", nullable = false)
  @NotNull
  public User getUser() {
    return user;
  }

  protected void setUser(User user) {
    this.user = user;
  }

}
