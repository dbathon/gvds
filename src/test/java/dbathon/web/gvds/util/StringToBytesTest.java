package dbathon.web.gvds.util;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import java.util.UUID;
import org.junit.Test;

public class StringToBytesTest {

  @Test
  public void roundTripTest() {
    final String[] tests = {
        "test", "random-" + UUID.randomUUID().toString()
    };
    for (final String test : tests) {
      assertThat(test, equalTo(StringToBytes.toString(StringToBytes.toBytes(test))));
    }
  }

}
