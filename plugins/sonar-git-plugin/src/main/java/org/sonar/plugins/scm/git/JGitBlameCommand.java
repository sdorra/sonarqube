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

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.BatchComponent;
import org.sonar.api.batch.InstantiationStrategy;
import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.scm.BlameCommand;
import org.sonar.api.batch.scm.BlameLine;
import org.sonar.api.scan.filesystem.PathResolver;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@InstantiationStrategy(InstantiationStrategy.PER_BATCH)
public class JGitBlameCommand implements BlameCommand, BatchComponent {

  private static final Logger LOG = LoggerFactory.getLogger(JGitBlameCommand.class);

  private final PathResolver pathResolver;

  public JGitBlameCommand(PathResolver pathResolver) {
    this.pathResolver = pathResolver;
  }

  @Override
  public void blame(FileSystem fs, Iterable<InputFile> files, BlameResult result) {
    Git git = null;
    File basedir = fs.baseDir();
    try {
      Repository repo = new RepositoryBuilder()
        .findGitDir(basedir)
        .setMustExist(true)
        .build();
      git = Git.wrap(repo);
      File gitBaseDir = repo.getWorkTree();
      for (InputFile inputFile : files) {
        blame(result, git, gitBaseDir, inputFile);
      }
    } catch (IOException e) {
      throw new IllegalStateException("Unable to open Git repository", e);
    } catch (GitAPIException e) {
      throw new IllegalStateException("Unable to blame", e);
    } finally {
      if (git != null && git.getRepository() != null) {
        git.getRepository().close();
      }
    }
  }

  private void blame(BlameResult result, Git git, File gitBaseDir, InputFile inputFile) throws GitAPIException {
    String filename = pathResolver.relativePath(gitBaseDir, inputFile.file());
    org.eclipse.jgit.blame.BlameResult blameResult = git.blame()
      // Equivalent to -w command line option
      .setTextComparator(RawTextComparator.WS_IGNORE_ALL)
      .setFilePath(filename).call();
    List<BlameLine> lines = new ArrayList<BlameLine>();
    for (int i = 0; i < blameResult.getResultContents().size(); i++) {
      if (blameResult.getSourceAuthor(i) == null || blameResult.getSourceCommit(i) == null || blameResult.getSourceCommitter(i) == null) {
        LOG.info("Author: " + blameResult.getSourceAuthor(i));
        LOG.info("Committer: " + blameResult.getSourceCommitter(i));
        LOG.info("Source commit: " + blameResult.getSourceCommit(i));
        throw new IllegalStateException("Unable to blame file " + inputFile.relativePath() + ". No blame info at line " + (i + 1) + ". Is file commited?");
      }
      lines.add(new org.sonar.api.batch.scm.BlameLine(blameResult.getSourceAuthor(i).getWhen(),
        blameResult.getSourceCommit(i).getName(),
        blameResult.getSourceAuthor(i).getEmailAddress(),
        blameResult.getSourceCommitter(i).getEmailAddress()));
    }
    if (lines.size() == inputFile.lines() - 1) {
      // SONARPLUGINS-3097 Git do not report blame on last empty line
      lines.add(lines.get(lines.size() - 1));
    }
    result.add(inputFile, lines);
  }

}
