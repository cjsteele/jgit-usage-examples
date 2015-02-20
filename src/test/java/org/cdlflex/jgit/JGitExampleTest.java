package org.cdlflex.jgit;

import org.eclipse.jgit.api.CheckoutCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNotNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Test class showing JGit API usage examples.
 */
public class JGitExampleTest extends AbstractJGitTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(JGitExampleTest.class);

    private static final String COMMIT_MSG = "initial";
    private static final String FILENAME = "test.txt";
    private static final String FILE_CONTENT = "1" + System.lineSeparator() + "b" + System.lineSeparator() + "3";
    private static final String OTHER_CONTENT = "1" + System.lineSeparator() + "a(main)";

    private Git git;

    /**
     * Initializes a new Git object.
     *
     * @throws IOException     file related error
     * @throws GitAPIException JGit related error
     */
    @Before public void setUp() throws IOException, GitAPIException {
        git = new Git(repository);
        git.getRepository().getBranch();
        // removing this causes tests to fail, initial commit is important!
        git.commit().setMessage(COMMIT_MSG).call();
    }

    /**
     * Creates a branch and check it out, asserting that the created branch and checked out branch have the same id.
     *
     * @throws IOException     file related error
     * @throws GitAPIException JGit related error
     */
    @Test public void testCheckoutBranch() throws IOException, GitAPIException {
        Ref branch = createBranch("test");
        Ref checkoutBranch = checkout("test");

        assertEquals(branch.getObjectId(), checkoutBranch.getObjectId());
    }

    /**
     * Add file and assert that it is staged.
     *
     * @throws IOException     file related error
     * @throws GitAPIException JGit related error
     */
    @Test public void addFile_shouldStageFile() throws GitAPIException, IOException {
        String filePath = "test.txt";

        createTestFile(filePath);
        DirCache dirCache = git.add().addFilepattern("test.txt").call();

        DirCacheEntry entry = dirCache.getEntry(filePath);

        LOGGER.info(formatDirCacheEntry(entry));
        assertNotNull(entry);
    }

    /**
     * Commits a test file and asserts that the first git log entry contains the commit message.
     *
     * @throws IOException     file related error
     * @throws GitAPIException JGit related error
     */
    @Test public void testCommit_shouldCreateLogEntry() throws IOException, GitAPIException {
        createFile(FILENAME, "test");
        commitAllChanges("testCommit");
        Iterable<RevCommit> commits = git.log().all().call();
        assertEquals("testCommit", commits.iterator().next().getFullMessage());
    }

    /**
     * Performs two commits, perform a checkout of the first commit, assert that file
     * in the second commit is not in the working directory after the checkout.
     *
     * @throws IOException     file related error
     * @throws GitAPIException JGit related error
     */
    @Test public void testMultipleCommits() throws IOException, GitAPIException {
        checkout("master");

        createFile(FILENAME, "test");
        RevCommit firstCommit = commitAllChanges("testcommit1");
        createFile(FILENAME + "2", "test2");
        commitAllChanges("testcommit2");

        CheckoutCommand checkout = git.checkout().setName(firstCommit.getName());
        checkout.call();

        assertFalse(Files.exists(repository.getWorkTree().toPath().resolve(Paths.get(FILENAME + "2"))));
        assertTrue(Files.exists(repository.getWorkTree().toPath().resolve(Paths.get(FILENAME))));
    }

    /**
     * Performs a git rm, commits the result and asserts that the working directory does not contain the file
     * afterwards.
     *
     * @throws IOException     file related error
     * @throws GitAPIException JGit related error
     */
    @Test public void deleteFileAndCommit() throws GitAPIException, IOException {
        checkout("master");

        createFile(FILENAME, "test");
        commitAllChanges("create file");

        assertTrue(Files.exists(repository.getWorkTree().toPath().resolve(Paths.get(FILENAME))));

        git.rm().addFilepattern(FILENAME).call();
        commitAllChanges("delete file");

        assertFalse(Files.exists(repository.getWorkTree().toPath().resolve(Paths.get(FILENAME))));
    }

    /**
     * Perform multiple commits changing a file, perform another commit that does not change the file, then obtain
     * all commits related to that file and iterate over them, checking that the commit messages match.
     *
     * @throws IOException     file related error
     * @throws GitAPIException JGit related error
     */
    @Test public void getAllCommitsForFile() throws IOException, GitAPIException {
        checkout("master");
        createFile(FILENAME, "test");
        commitAllChanges("first_commit");

        createFile(FILENAME, "test2");
        commitAllChanges("second_commit");

        createFile("otherfile.txt", "otherfilecontent");
        commitAllChanges("irrelevant_commit");

        createFile(FILENAME, "test3");
        commitAllChanges("third_commit");

        Iterable<RevCommit> commits = git.log().addPath(FILENAME).call();
        Iterator<RevCommit> commIterator = commits.iterator();

        //only the commits for the desired file should be in this
        assertEquals("third_commit", commIterator.next().getFullMessage());
        assertEquals("second_commit", commIterator.next().getFullMessage());
        assertEquals("first_commit", commIterator.next().getFullMessage());
    }

    /**
     * Retrieve all versions of a file and assert that the contents match what would be expected.
     *
     * @throws IOException     file related error
     * @throws GitAPIException JGit related error
     */
    @Test public void getAllVersionsOfFile() throws GitAPIException, IOException {
        checkout("master");
        createFile(FILENAME, "test");
        commitAllChanges("first_commit");

        createFile(FILENAME, "test2");
        commitAllChanges("second_commit");

        createFile(FILENAME, "test3");
        commitAllChanges("third_commit");

        Iterable<RevCommit> commits = git.log().addPath(FILENAME).call();
        Iterator<RevCommit> commIterator = commits.iterator();

        List<String> contents = new ArrayList<>();

        while (commIterator.hasNext()) {
            contents.add(getFileRevisionContent(commIterator.next(), FILENAME));
        }

        assertEquals("test3", contents.get(0));
        assertEquals("test2", contents.get(1));
        assertEquals("test", contents.get(2));
    }

    /**
     * Returns the content of the file located at path within the specified commit.
     */
    private String getFileRevisionContent(RevCommit commit, String path) throws IOException {
        ObjectReader reader = repository.newObjectReader();

        try {
            RevTree tree = commit.getTree();
            //move to the specified path within the commit
            TreeWalk treeWalk = TreeWalk.forPath(reader, path, tree);

            byte[] data = reader.open(treeWalk.getObjectId(0)).getBytes();
            return new String(data, "UTF-8");
        } finally {
            reader.release();
        }
    }

    /**
     * Make two conflicting commits and assert that the result of the merge call is conflicting.
     *
     * @throws IOException     file related error
     * @throws GitAPIException JGit related error
     */
    @Test public void testMergeConflicting_shouldNotMerge() throws IOException, GitAPIException {
        createBranch("ours");
        checkout("ours");
        createFile(FILENAME, FILE_CONTENT);
        RevCommit commit = commitAllChanges("our commit");

        checkout("master");

        createBranch("theirs");
        checkout("theirs");
        createFile(FILENAME, OTHER_CONTENT);
        commitAllChanges("their commit");

        git.add().addFilepattern(".").call();
        git.commit().setMessage("their commit").call();

        MergeResult result = git.merge().include(commit).call();

        result.getConflicts().entrySet().stream().forEach(
                (entry) -> LOGGER.info("{} {} {}", result.getMergeStatus(), entry.getKey(), entry.getValue()));
        assertEquals(MergeResult.MergeStatus.CONFLICTING, result.getMergeStatus());
    }

    /**
     * Make two conflicting commits and merge them using the "ours" strategy.
     *
     * @throws IOException     file related error
     * @throws GitAPIException JGit related error
     */
    @Test public void testMergeConflictingOurStrategy_shouldMerge() throws IOException, GitAPIException {
        createBranch("theirs");
        checkout("theirs");

        createFile(FILENAME, OTHER_CONTENT);
        RevCommit commit = commitAllChanges("their commit");
        checkout("master");

        createBranch("ours");
        checkout("ours");
        createFile(FILENAME, FILE_CONTENT);
        commitAllChanges("our commit");

        MergeResult result = git.merge().setStrategy(MergeStrategy.OURS).include(commit).call();

        LOGGER.info(result.toString());
        LOGGER.info(getFormattedFileContent(FILENAME));
        assertEquals(MergeResult.MergeStatus.MERGED, result.getMergeStatus());
        assertEquals(FILE_CONTENT, getFileContent(FILENAME));
    }

    private String getFormattedFileContent(String fileName) throws IOException {
        return System.lineSeparator() + fileName + System.lineSeparator() + getFileContent(fileName);
    }

    private String formatDirCacheEntry(DirCacheEntry entry) {
        return System.lineSeparator() + "Path: " + entry.getPathString() + System.lineSeparator() + "ObjectId: "
                + entry.getObjectId() + System.lineSeparator() + "Is merged: " + entry.isMerged();
    }

    private String getFileContent(String fileName) throws IOException {
        return Files.lines(temporaryFolder.getRoot().toPath().resolve(Paths.get(fileName)))
                .collect(Collectors.joining(System.lineSeparator()));
    }

    private Ref checkout(String name) throws GitAPIException {
        Ref ref = git.checkout().setName(name).call();
        LOGGER.info("Checking out {}", ref.getName());
        return ref;
    }

    private Ref createBranch(String name) throws GitAPIException {
        Ref branchRef = git.branchCreate().setName(name).call();
        LOGGER.info("Creating branch {}", branchRef.getName());
        return branchRef;
    }

    private File createFile(String name, String content) throws IOException, GitAPIException {
        File exampleFile = createTestFile(name);
        writeToFile(exampleFile, content.getBytes());
        return exampleFile;
    }

    private RevCommit commitAllChanges(String message) throws GitAPIException {
        git.add().addFilepattern(".").call();
        return git.commit().setMessage(message).call();
    }
}
