/*
 * Copyright Red Hat, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Red Hat trademarks are not licensed under GPLv3. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package com.redhat.swatch.component.tests.doctor.tools;

import dev.langchain4j.agent.tool.Tool;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Tools that the AI agent can use to inspect git history and files. These are exposed as function
 * calls to Claude via LangChain4j.
 */
@ApplicationScoped
public class GitTools {

  /**
   * Get the list of commits in the current branch that are not in the base branch.
   *
   * @param repoPath path to the git repository
   * @param baseBranch the base branch to compare against (default: main)
   * @param limit maximum number of commits to return (default: 10)
   * @return list of commits with hash, author, date, and message
   */
  @Tool(
      "Get recent commits in the current branch compared to a base branch. "
          + "Returns commit hashes that you can use with getFilesChangedInCommit and getFileDiff. "
          + "Example: getRecentCommits('/path/to/repo', 'main', 5)")
  public String getRecentCommits(String repoPath, String baseBranch, int limit) {
    if (baseBranch == null || baseBranch.isBlank()) {
      baseBranch = "main";
    }
    if (limit <= 0) {
      limit = 10;
    }

    Log.debugf("Getting last %d commits in %s (base: %s)", limit, repoPath, baseBranch);

    try {
      // Get current branch
      String currentBranch =
          executeGitCommand(repoPath, "git", "rev-parse", "--abbrev-ref", "HEAD");

      // Get commits not in base branch
      List<String> command = new ArrayList<>();
      command.add("git");
      command.add("log");
      command.add(baseBranch + ".." + currentBranch);
      command.add("--pretty=format:%H|%an|%ad|%s");
      command.add("--date=short");
      command.add("-" + limit);

      String output = executeGitCommand(repoPath, command.toArray(new String[0]));

      if (output.isBlank()) {
        return "No commits found. Branch may be up to date with " + baseBranch;
      }

      // Format output
      StringBuilder result = new StringBuilder();
      result
          .append("Recent commits (")
          .append(currentBranch)
          .append(" vs ")
          .append(baseBranch)
          .append("):\n\n");

      String[] commits = output.split("\n");
      for (String commit : commits) {
        String[] parts = commit.split("\\|", 4);
        if (parts.length == 4) {
          result.append("Commit: ").append(parts[0].substring(0, 8)).append("\n");
          result.append("Author: ").append(parts[1]).append("\n");
          result.append("Date: ").append(parts[2]).append("\n");
          result.append("Message: ").append(parts[3]).append("\n");
          result.append("\n");
        }
      }

      return result.toString();

    } catch (Exception e) {
      Log.error("Error getting commits", e);
      return "Error: " + e.getMessage();
    }
  }

  /**
   * Get the list of files changed in a specific commit.
   *
   * @param repoPath path to the git repository
   * @param commitHash the commit hash (can be short form like abc1234)
   * @return list of changed files with their status (A=added, M=modified, D=deleted)
   */
  @Tool(
      "Get files changed in a specific commit. "
          + "Use the ACTUAL commit hash from getRecentCommits output (e.g., 'a1b2c3d4'). "
          + "Do NOT use placeholder text like 'latest_commit_hash'. "
          + "Example: getFilesChangedInCommit('/path/to/repo', 'a1b2c3d4')")
  public String getFilesChangedInCommit(String repoPath, String commitHash) {
    Log.debugf("Getting files changed in commit %s", commitHash);

    try {
      String output =
          executeGitCommand(
              repoPath, "git", "show", "--name-status", "--pretty=format:", commitHash);

      if (output.isBlank()) {
        return "No files changed in commit " + commitHash;
      }

      StringBuilder result = new StringBuilder();
      result.append("Files changed in ").append(commitHash).append(":\n\n");

      String[] lines = output.split("\n");
      for (String line : lines) {
        line = line.trim();
        if (line.isEmpty()) continue;

        String[] parts = line.split("\\s+", 2);
        if (parts.length == 2) {
          String status = parts[0];
          String file = parts[1];

          String statusDesc =
              switch (status) {
                case "A" -> "Added";
                case "M" -> "Modified";
                case "D" -> "Deleted";
                case "R" -> "Renamed";
                default -> "Changed";
              };

          result.append(statusDesc).append(": ").append(file).append("\n");
        }
      }

      return result.toString();

    } catch (Exception e) {
      Log.error("Error getting changed files", e);
      return "Error: " + e.getMessage();
    }
  }

  /**
   * Get the diff (changes) for a specific file in a commit.
   *
   * @param repoPath path to the git repository
   * @param commitHash the commit hash (use ACTUAL hash from getRecentCommits)
   * @param filePath the file path relative to the repository root
   * @return the diff showing what changed in the file
   */
  @Tool(
      "Get the diff for a specific file in a commit. "
          + "Use the ACTUAL commit hash from getRecentCommits (e.g., 'a1b2c3d4'). "
          + "Do NOT use placeholder text. "
          + "Example: getFileDiff('/path/to/repo', 'a1b2c3d4', 'src/main/java/Example.java')")
  public String getFileDiff(String repoPath, String commitHash, String filePath) {
    Log.debugf("Getting diff for %s in commit %s", filePath, commitHash);

    try {
      String output =
          executeGitCommand(repoPath, "git", "show", commitHash + ":" + filePath, "--", filePath);

      // Limit output to avoid overwhelming the LLM
      if (output.length() > 5000) {
        output = output.substring(0, 5000) + "\n... (truncated, file too large)";
      }

      return "Diff for " + filePath + " in " + commitHash + ":\n\n" + output;

    } catch (Exception e) {
      Log.error("Error getting diff", e);
      return "Error: " + e.getMessage();
    }
  }

  /**
   * Execute a git command in a specific repository.
   *
   * @param repoPath path to the repository
   * @param command the git command and arguments
   * @return the output of the command
   */
  private String executeGitCommand(String repoPath, String... command) throws IOException {
    ProcessBuilder pb = new ProcessBuilder(command);
    pb.directory(Paths.get(repoPath).toFile());
    pb.redirectErrorStream(true);

    Process process = pb.start();

    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(process.getInputStream()))) {
      String output = reader.lines().collect(Collectors.joining("\n"));

      int exitCode = process.waitFor();
      if (exitCode != 0) {
        throw new IOException("Git command failed with exit code " + exitCode + ": " + output);
      }

      return output;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("Command interrupted", e);
    }
  }
}
