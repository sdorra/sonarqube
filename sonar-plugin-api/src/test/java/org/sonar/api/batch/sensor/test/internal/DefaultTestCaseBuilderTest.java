package org.sonar.api.batch.sensor.test.internal;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonar.api.batch.sensor.test.TestCase.Status;
import org.sonar.api.batch.sensor.test.TestCase.Type;
import org.sonar.api.batch.sensor.test.TestPlanBuilder;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class DefaultTestCaseBuilderTest {

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private TestPlanBuilder parent = mock(TestPlanBuilder.class);

  @Test
  public void testBuilder() throws Exception {
    DefaultTestCaseBuilder builder = new DefaultTestCaseBuilder(parent, "myTest");
    builder.durationInMs(1)
      .message("message")
      .stackTrace("stack")
      .status(Status.ERROR)
      .type(Type.UNIT)
      .add();
    verify(parent).add(new DefaultTestCase("myTest", 1L, Status.ERROR, "message", Type.UNIT, "stack"));
  }

  @Test
  public void testBuilderWithDefaultValues() throws Exception {
    DefaultTestCaseBuilder builder = new DefaultTestCaseBuilder(parent, "myTest");
    builder.add();
    verify(parent).add(new DefaultTestCase("myTest", null, Status.OK, null, Type.UNIT, null));
  }

  @Test
  public void testInvalidDuration() throws Exception {
    DefaultTestCaseBuilder builder = new DefaultTestCaseBuilder(parent, "myTest");

    thrown.expect(IllegalArgumentException.class);

    builder.durationInMs(-3)
      .message("message")
      .stackTrace("stack")
      .status(Status.ERROR)
      .type(Type.UNIT)
      .add();
  }

}
