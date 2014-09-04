/*
 * SonarQube, open source software quality management tool.
 * Copyright (C) 2008-2014 SonarSource
 * mailto:contact AT sonarsource DOT com
 *
 * SonarQube is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * SonarQube is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
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
