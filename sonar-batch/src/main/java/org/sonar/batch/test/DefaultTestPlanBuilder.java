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
package org.sonar.batch.test;

import com.google.inject.internal.util.Preconditions;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.sensor.test.TestCase;
import org.sonar.api.batch.sensor.test.TestCaseBuilder;
import org.sonar.api.batch.sensor.test.TestPlanBuilder;
import org.sonar.api.batch.sensor.test.internal.DefaultTestCaseBuilder;
import org.sonar.api.test.MutableTestPlan;

import javax.annotation.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

public class DefaultTestPlanBuilder implements TestPlanBuilder {

  private final InputFile testFile;
  private final Map<String, TestCase> testCases = new LinkedHashMap<String, TestCase>();
  private final MutableTestPlan testPlan;
  private final TestCaseCache testCaseCache;

  public DefaultTestPlanBuilder(InputFile testFile, @Nullable TestCaseCache testCaseCache, @Nullable MutableTestPlan testPlan) {
    this.testFile = testFile;
    this.testCaseCache = testCaseCache;
    this.testPlan = testPlan;
  }

  @Override
  public TestCaseBuilder newTestCase(String testName) {
    Preconditions.checkNotNull(testName);
    return new DefaultTestCaseBuilder(this, testName);
  }

  @Override
  public void done() {
    if (testPlan != null) {
      // Batch 1.0
      for (TestCase testCase : testCases.values()) {
        testPlan
          .addTestCase(testCase.name())
          .setDurationInMs(testCase.durationInMs())
          .setType(testCase.type().name())
          .setStatus(org.sonar.api.test.TestCase.Status.valueOf(testCase.status().name()))
          .setMessage(testCase.message())
          .setStackTrace(testCase.stackTrace());
      }
    } else {
      // Batch 2.0
      for (TestCase testCase : testCases.values()) {
        testCaseCache.put(testFile, testCase);
      }
    }
  }

  @Override
  public TestPlanBuilder add(TestCase testCase) {
    if (testCases.containsKey(testCase.name())) {
      throw new IllegalStateException("Unable to have test cases with same name: " + testCase + " and " + testCases.get(testCase.name()));
    }
    testCases.put(testCase.name(), testCase);
    return this;
  }

}
