package dbathon.web.gvds.entity;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.Index;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import dbathon.web.gvds.util.StringToBytes;

/**
 * The order of the primary key should be id,versionFrom (there is no way to specify this with the
 * annotations).
 * <p>
 * TODO: indexes
 */
@Entity
@Table(name = "DATA_WITH_VERSION", indexes = {
    @Index(columnList = "ID_DATA_TYPE, VERSION_TO, ID_"),
    @Index(columnList = "ID_DATA_TYPE, KEY_1, VERSION_TO"),
    @Index(columnList = "ID_DATA_TYPE, KEY_2, VERSION_TO"),
})
@IdClass(DataWithVersionKey.class)
public class DataWithVersion extends AbstractEntity {

  private static final Splitter SPLITTER_SPACE = Splitter.on(' ');
  private static final Joiner JOINER_SPACE = Joiner.on(' ');

  private String id;

  private long versionFrom;
  private long versionTo = -1;

  private DataType dataType;

  private String key1;
  private String key2;

  private byte[] data;

  private Set<String> references = new HashSet<>();
  private byte[] referencesInline;

  protected DataWithVersion() {}

  private DataWithVersion(String id, DataType dataType, long versionFrom, String key1, String key2,
      String dataString, Collection<String> references) {
    this.id = id;
    this.dataType = dataType;
    this.versionFrom = versionFrom;
    this.key1 = key1;
    this.key2 = key2;
    data = StringToBytes.toBytes(dataString);
    this.references.addAll(references);

    if (!this.references.isEmpty()) {
      // always sort referencesInline
      final Object[] refercesArray = this.references.toArray();
      Arrays.sort(refercesArray);
      referencesInline = StringToBytes.toBytes(JOINER_SPACE.join(refercesArray));
    }
  }

  /**
   * Constructor for new data.
   */
  public DataWithVersion(DataType dataType, long versionFrom, String key1, String key2,
      String dataString, Collection<String> references) {
    this(AbstractEntityWithUuidAsId.newBase64Uuid(), dataType, versionFrom, key1, key2, dataString,
        references);
  }

  /**
   * Constructor for a new version of existing data. This constructor also updates
   * <code>previous</code>'s {@link #getVersionTo() versionTo}.
   */
  public DataWithVersion(DataWithVersion previous, long versionFrom, String key1, String key2,
      String dataString, Collection<String> references) {
    this(previous.getId(), previous.getDataType(), versionFrom, key1, key2, dataString, references);

    if (versionFrom <= previous.getVersionFrom()) {
      throw new IllegalArgumentException("versionFrom must be after previous.versionFrom: "
          + versionFrom + ", " + previous.getVersionFrom());
    }
    previous.setVersionTo(versionFrom - 1);
  }

  @Id
  @Column(name = "ID_", nullable = false, length = 30)
  public String getId() {
    return id;
  }

  protected void setId(String id) {
    this.id = id;
  }

  @Id
  @Column(name = "VERSION_FROM", nullable = false)
  @Min(0)
  public long getVersionFrom() {
    return versionFrom;
  }

  protected void setVersionFrom(long versionFrom) {
    this.versionFrom = versionFrom;
  }

  /**
   * <code>-1</code> means that there is no "versionTo".
   */
  @Column(name = "VERSION_TO", nullable = false)
  @Min(-1)
  public long getVersionTo() {
    return versionTo;
  }

  /**
   * This should only be used for "deleting", for new versions
   * {@link #DataWithVersion(DataWithVersion, long, String, String, String, Set)} takes care of
   * setting the versionTo.
   */
  public void setVersionTo(long versionTo) {
    this.versionTo = versionTo;
  }

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "ID_DATA_TYPE", nullable = false)
  @NotNull
  public DataType getDataType() {
    return dataType;
  }

  protected void setDataType(DataType dataType) {
    this.dataType = dataType;
  }

  @Column(name = "KEY_1", length = 1000)
  @Size(max = 1000)
  public String getKey1() {
    return key1;
  }

  protected void setKey1(String key1) {
    this.key1 = key1;
  }

  @Column(name = "KEY_2", length = 1000)
  @Size(max = 1000)
  public String getKey2() {
    return key2;
  }

  protected void setKey2(String key2) {
    this.key2 = key2;
  }

  /**
   * Store the actual data as byte array
   */
  @Column(name = "DATA_")
  protected byte[] getData() {
    return data;
  }

  protected void setData(byte[] data) {
    this.data = data;
  }

  @Transient
  public String getDataString() {
    return StringToBytes.toString(getData());
  }

  /**
   * A set of references to ids of other data (similar to a foreign key, but there is no actual
   * foreign key in the database). This collection table is only used for querying. To access the
   * actual set in the application {@link #getReferencesInline()} is used via
   * {@link #getReferencesSet()}.
   */
  @ElementCollection
  @CollectionTable(name = "DATA_WITH_VERSION_REFERENCES", joinColumns = {
      @JoinColumn(name = "ID_", referencedColumnName = "ID_"),
      @JoinColumn(name = "VERSION_FROM", referencedColumnName = "VERSION_FROM")
  }, indexes = @Index(columnList = "REFERENCE_ID"))
  @Column(name = "REFERENCE_ID", nullable = false, length = 30)
  protected Set<String> getReferences() {
    return references;
  }

  protected void setReferences(Set<String> references) {
    this.references = references;
  }

  @Column(name = "REFERENCES_INLINE")
  protected byte[] getReferencesInline() {
    return referencesInline;
  }

  protected void setReferencesInline(byte[] referencesInline) {
    this.referencesInline = referencesInline;
  }

  public static Set<String> toReferencesSet(byte[] referencesInline) {
    final String referencesString = StringToBytes.toString(referencesInline);
    if (referencesString == null) {
      return ImmutableSet.of();
    }
    else {
      return ImmutableSet.copyOf(SPLITTER_SPACE.split(referencesString));
    }
  }

  @Transient
  public Set<String> getReferencesSet() {
    return toReferencesSet(getReferencesInline());
  }

}
