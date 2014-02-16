package dbathon.web.gvds.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.Version;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

@Entity
@Table(name = "USER_")
public class User extends AbstractEntityWithUuidAsId {

  private String username;
  private String passwordHash;

  private long version = 0;

  protected User() {}

  public User(String username, String passwordHash) {
    this.username = username;
    this.passwordHash = passwordHash;
  }

  @Column(name = "USERNAME_", unique = true, nullable = false, length = 1000)
  @NotNull
  @Size(min = 1, max = 1000)
  @Pattern(regexp = "[^:\\s]+")
  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  @Column(name = "PASSWORD_HASH", nullable = false, length = 1000)
  @NotNull
  @Size(min = 1, max = 1000)
  public String getPasswordHash() {
    return passwordHash;
  }

  public void setPasswordHash(String passwordHash) {
    this.passwordHash = passwordHash;
  }

  /**
   * <b>Not</b> a normal {@link Version} column.
   */
  @Column(name = "VERSION_", nullable = false)
  @Min(0)
  public long getVersion() {
    return version;
  }

  public void setVersion(long version) {
    this.version = version;
  }

}
