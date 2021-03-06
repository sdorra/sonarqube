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
package org.sonar.plugins.scm.git;

import com.google.common.collect.ImmutableList;
import org.sonar.api.CoreProperties;
import org.sonar.api.PropertyType;
import org.sonar.api.SonarPlugin;
import org.sonar.api.config.PropertyDefinition;

import java.util.List;

public final class GitPlugin extends SonarPlugin {

  static final String CATEGORY_GIT = "Git";
  static final String GIT_IMPLEMENTATION_PROP_KEY = "sonar.git.implementation";
  static final String JGIT = "jgit";
  static final String EXE = "exe";

  @Override
  public List getExtensions() {
    return ImmutableList.of(
      GitScmProvider.class,
      GitBlameCommand.class,
      JGitBlameCommand.class,

      PropertyDefinition.builder(GIT_IMPLEMENTATION_PROP_KEY)
        .name("Git implementation")
        .description("By default pure Java implementation is used. You can force use of command line git executable in case of issue.")
        .defaultValue(JGIT)
        .type(PropertyType.SINGLE_SELECT_LIST)
        .options(EXE, JGIT)
        .category(CoreProperties.CATEGORY_SCM)
        .subCategory(CATEGORY_GIT)
        .build());
  }

}
