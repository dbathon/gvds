package dbathon.web.gvds.entity;

import java.io.Serializable;
import java.util.Objects;

public class DataWithVersionKey implements Serializable {

  private String id;
  private long versionFrom;

  protected DataWithVersionKey() {}

  public DataWithVersionKey(String id, long versionFrom) {
    this.id = id;
    this.versionFrom = versionFrom;
  }

  public String getId() {
    return id;
  }

  protected void setId(String id) {
    this.id = id;
  }

  public long getVersionFrom() {
    return versionFrom;
  }

  protected void setVersionFrom(long versionFrom) {
    this.versionFrom = versionFrom;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((id == null) ? 0 : id.hashCode());
    result = prime * result + (int) (versionFrom ^ (versionFrom >>> 32));
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    final DataWithVersionKey other = (DataWithVersionKey) obj;
    return Objects.equals(id, other.id) && versionFrom == other.versionFrom;
  }

}
