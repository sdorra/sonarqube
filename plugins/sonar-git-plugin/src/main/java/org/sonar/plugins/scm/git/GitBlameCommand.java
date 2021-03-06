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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.BatchComponent;
import org.sonar.api.batch.InstantiationStrategy;
import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.scm.BlameCommand;
import org.sonar.api.batch.scm.BlameLine;
import org.sonar.api.utils.command.Command;
import org.sonar.api.utils.command.CommandExecutor;
import org.sonar.api.utils.command.StreamConsumer;
import org.sonar.api.utils.command.StringStreamConsumer;

import java.io.File;
import java.util.List;

@InstantiationStrategy(InstantiationStrategy.PER_BATCH)
public class GitBlameCommand implements BlameCommand, BatchComponent {

  private static final Logger LOG = LoggerFactory.getLogger(GitBlameCommand.class);
  private final CommandExecutor commandExecutor;

  public GitBlameCommand() {
    this(CommandExecutor.create());
  }

  GitBlameCommand(CommandExecutor commandExecutor) {
    this.commandExecutor = commandExecutor;
  }

  @Override
  public void blame(FileSystem fs, Iterable<InputFile> files, BlameResult result) {
    LOG.debug("Working directory: " + fs.baseDir().getAbsolutePath());
    for (InputFile inputFile : files) {
      String filename = inputFile.relativePath();
      Command cl = createCommandLine(fs.baseDir(), filename);
      GitBlameConsumer consumer = new GitBlameConsumer(filename);
      StringStreamConsumer stderr = new StringStreamConsumer();

      int exitCode = execute(cl, consumer, stderr);
      if (exitCode != 0) {
        throw new IllegalStateException("The git blame command [" + cl.toString() + "] failed: " + stderr.getOutput());
      }
      List<BlameLine> lines = consumer.getLines();
      if (lines.size() == inputFile.lines() - 1) {
        // SONARPLUGINS-3097 Git do not report blame on last empty line
        lines.add(lines.get(lines.size() - 1));
      }
      result.add(inputFile, lines);
    }
  }

  public int execute(Command cl, StreamConsumer consumer, StreamConsumer stderr) {
    LOG.debug("Executing: " + cl);
    return commandExecutor.execute(cl, consumer, stderr, -1);
  }

  private Command createCommandLine(File workingDirectory, String filename) {
    Command cl = Command.create("git");
    cl.addArgument("blame");
    if (workingDirectory != null) {
      cl.setDirectory(workingDirectory);
    }
    cl.addArgument("--porcelain");
    cl.addArgument(filename);
    cl.addArgument("-w");
    return cl;
  }

}
