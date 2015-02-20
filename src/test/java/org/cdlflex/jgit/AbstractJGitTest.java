package org.cdlflex.jgit;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public abstract class AbstractJGitTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractJGitTest.class);

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    protected File repositoryFolder;
    protected Repository repository;

    /**
     * Initializes a git repository correctly.
     *
     * @throws java.io.IOException when things go wrong
     * @throws org.eclipse.jgit.api.errors.GitAPIException when things go wrong
     */
    @Before
    public void init() throws IOException, GitAPIException {
        repositoryFolder = temporaryFolder.newFolder(".git");
        repository = new FileRepositoryBuilder().setGitDir(repositoryFolder).build();
        repository.create();
    }

    protected File createTestFile(String name) throws IOException {
        File file = new File(repository.getWorkTree(), name);
        file.createNewFile();
        return file;
    }

    protected File createTestDirectory(String name) throws IOException {
        File directory = new File(repository.getWorkTree(), name);
        directory.mkdirs();
        return directory;
    }

    protected void writeToFile(File file, byte[] content) throws IOException {
        try (FileOutputStream writer = new FileOutputStream(file)) {
            writer.write(content);
        }
    }
}
