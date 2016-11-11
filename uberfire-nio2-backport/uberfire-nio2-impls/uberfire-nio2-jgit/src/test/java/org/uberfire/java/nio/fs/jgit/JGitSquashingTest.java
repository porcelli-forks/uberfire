/*
 * Copyright 2016 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.uberfire.java.nio.fs.jgit;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.uberfire.java.nio.fs.jgit.util.JGitUtil;
import org.uberfire.java.nio.fs.jgit.util.commands.Commit;
import org.uberfire.java.nio.fs.jgit.util.commands.CreateRepository;
import org.uberfire.java.nio.fs.jgit.util.commands.GetRef;
import org.uberfire.java.nio.fs.jgit.util.commands.Squash;
import org.uberfire.java.nio.fs.jgit.util.exceptions.GitException;

import static org.fest.assertions.api.Assertions.*;
import static org.uberfire.java.nio.fs.jgit.util.JGitUtil.*;
import static org.uberfire.java.nio.fs.jgit.util.PathType.*;

public class JGitSquashingTest extends AbstractTestInfra {

    private Logger logger = LoggerFactory.getLogger( JGitSquashingTest.class );

    static {
        CredentialsProvider.setDefault( new UsernamePasswordCredentialsProvider( "guest", "" ) );
    }

    /*
     * This test make 5 commits and then squah the last 4 into a single commit
    */
    @Test
    public void testSquash4Of5Commits() throws IOException, GitAPIException {

        final File parentFolder = createTempDirectory();
        logger.info( ">> Parent Forlder for the Test: " + parentFolder.getAbsolutePath() );
        final File gitFolder = new File( parentFolder, "my-local-repo.git" );

        final Git origin = new CreateRepository( gitFolder ).execute().get();

        new Commit( origin, "master", "salaboy", "salaboy@example.com", "commit 1!", null, null, false, new HashMap<String, File>() {{
            put( "path/to/file1.txt", tempFile( "initial content file 1" ) );
        }} ).execute();
        new Commit( origin, "master", "salaboy", "salaboy@example.com", "commit 2!", null, null, false, new HashMap<String, File>() {{
            put( "path/to/file2.txt", tempFile( "initial content file 2" ) );
        }} ).execute();
        Iterable<RevCommit> logs = origin.log().setMaxCount( 1 ).all().call();
        RevCommit secondCommit = logs.iterator().next();

        new Commit( origin, "master", "salaboy", "salaboy@example.com", "commit 3!", null, null, false, new HashMap<String, File>() {{
            put( "path/to/file1.txt", tempFile( "new content file 1" ) );
        }} ).execute();

        new Commit( origin, "master", "salaboy", "salaboy@example.com", "commit 4!", null, null, false, new HashMap<String, File>() {{
            put( "path/to/file2.txt", tempFile( "new content file 2" ) );
        }} ).execute();
        new Commit( origin, "master", "salaboy", "salaboy@example.com", "commit 5!", null, null, false, new HashMap<String, File>() {{
            put( "path/to/file3.txt", tempFile( "initial content file 3" ) );
        }} ).execute();
        logs = origin.log().all().call();
        int commitsCount = 0;
        for ( RevCommit commit : logs ) {
            logger.info( ">>> Origin Commit: " + commit.getFullMessage() + " - " + commit.toString() );
            commitsCount++;
        }
        assertThat( commitsCount ).isEqualTo( 5 );

        assertThat( JGitUtil.getPathInfo( origin, "master", "pathx/" ).getPathType() ).isEqualTo( NOT_FOUND );
        assertThat( JGitUtil.getPathInfo( origin, "master", "path/to/file1.txt" ).getPathType() ).isEqualTo( FILE );
        assertThat( JGitUtil.getPathInfo( origin, "master", "path/to/file2.txt" ).getPathType() ).isEqualTo( FILE );
        assertThat( JGitUtil.getPathInfo( origin, "master", "path/to/file3.txt" ).getPathType() ).isEqualTo( FILE );
        assertThat( JGitUtil.getPathInfo( origin, "master", "path/to" ).getPathType() ).isEqualTo( DIRECTORY );

        logger.info( "Squashing from " + secondCommit.getName() + "  to HEAD" );
        new Squash( origin, "master", secondCommit.getName(), "squashed message" ).execute();

        commitsCount = 0;
        for ( RevCommit commit : origin.log().all().call() ) {
            logger.info( ">>> Final Commit: " + commit.getFullMessage() + " - " + commit.toString() );
            commitsCount++;
        }
        assertThat( commitsCount ).isEqualTo( 2 );

    }

    @Test
    public void testFailWhenTryToSquashCommitsFromDifferentBranches() throws IOException, GitAPIException {

        final File parentFolder = createTempDirectory();
        logger.info( ">> Parent Forlder for the Test: " + parentFolder.getAbsolutePath() );
        final File gitFolder = new File( parentFolder, "my-local-repo.git" );

        final Git origin = new CreateRepository( gitFolder ).execute().get();

        new Commit( origin, "master", "aparedes", "aparedes@example.com", "commit 1!", null, null, false, new HashMap<String, File>() {{
            put( "path/to/file1.txt", tempFile( "initial content file 1" ) );
        }} ).execute();
        new Commit( origin, "develop", "salaboy", "salaboy@example.com", "commit 2!", null, null, false, new HashMap<String, File>() {{
            put( "path/to/file2.txt", tempFile( "initial content file 2" ) );
        }} ).execute();
        new Commit( origin, "master", "aparedes", "aparedes@example.com", "commit 3!", null, null, false, new HashMap<String, File>() {{
            put( "path/to/file3.txt", tempFile( "initial content file 1" ) );
        }} ).execute();
        new Commit( origin, "master", "aparedes", "aparedes@example.com", "commit 4!", null, null, false, new HashMap<String, File>() {{
            put( "path/to/file4.txt", tempFile( "initial content file 1" ) );
        }} ).execute();

        List<RevCommit> masterCommits = getCommitsFromBranch( origin, "master" );
        List<RevCommit> developCommits = getCommitsFromBranch( origin, "develop" );

        assertThat( masterCommits.size() ).isEqualTo( 3 );
        assertThat( developCommits.size() ).isEqualTo( 1 );

        try {
            new Squash( origin, "master", developCommits.get( 0 ).getName(), "squashed message" ).execute();
            fail( "If it reaches here the test has failed because he found the commit into the branch" );
        } catch ( GitException e ) {
            logger.info( e.getMessage() );
            assertThat( e ).isNotNull();
        }

    }

    private List<RevCommit> getCommitsFromBranch( final Git origin,
                                                  String branch ) throws GitAPIException, MissingObjectException, IncorrectObjectTypeException {
        List<RevCommit> commits = new ArrayList<>();
        final ObjectId id = new GetRef( origin.getRepository(), branch ).execute().getObjectId();
        for ( RevCommit commit : origin.log().add( id ).call() ) {
            logger.info( ">>> " + branch + " Commits: " + commit.getFullMessage() + " - " + commit.toString() );
            commits.add( commit );
        }
        return commits;
    }

    /*
     * This test also perform 5 commits and squash the last 4 into a single commit
     *  but now the changes are in different paths
    */
    @Test
    public void testSquashCommitsWithDifferentPaths() throws IOException, GitAPIException {

        final File parentFolder = createTempDirectory();
        logger.info( ">> Parent Folder for the Test: " + parentFolder.getAbsolutePath() );
        final File gitFolder = new File( parentFolder, "my-local-repo.git" );

        final Git origin = new CreateRepository( gitFolder ).execute().get();

        new Commit( origin, "master", "salaboy", "salaboy@example.com", "commit 1!", null, null, false, new HashMap<String, File>() {{
            put( "file1.txt", tempFile( "initial content file 1" ) );
        }} ).execute();
        new Commit( origin, "master", "salaboy", "salaboy@example.com", "commit 2!", null, null, false, new HashMap<String, File>() {{
            put( "path/to/file2.txt", tempFile( "initial content file 2" ) );
        }} ).execute();
        Iterable<RevCommit> logs = origin.log().setMaxCount( 1 ).all().call();
        RevCommit secondCommit = logs.iterator().next();

        new Commit( origin, "master", "salaboy", "salaboy@example.com", "commit 3!", null, null, false, new HashMap<String, File>() {{
            put( "file1.txt", tempFile( "new content file 1" ) );
        }} ).execute();

        new Commit( origin, "master", "salaboy", "salaboy@example.com", "commit 4!", null, null, false, new HashMap<String, File>() {{
            put( "path/to/file2.txt", tempFile( "new content file 2" ) );
        }} ).execute();
        new Commit( origin, "master", "salaboy", "salaboy@example.com", "commit 5!", null, null, false, new HashMap<String, File>() {{
            put( "path/file3.txt", tempFile( "initial content file 3" ) );
        }} ).execute();

        for ( RevCommit commit : origin.log().all().call() ) {
            logger.info( ">>> Origin Commit: " + commit.getFullMessage() + " - " + commit.toString() );
        }

        assertThat( JGitUtil.getPathInfo( origin, "master", "pathx/" ).getPathType() ).isEqualTo( NOT_FOUND );
        assertThat( JGitUtil.getPathInfo( origin, "master", "file1.txt" ).getPathType() ).isEqualTo( FILE );
        assertThat( JGitUtil.getPathInfo( origin, "master", "path/to/file2.txt" ).getPathType() ).isEqualTo( FILE );
        assertThat( JGitUtil.getPathInfo( origin, "master", "path/file3.txt" ).getPathType() ).isEqualTo( FILE );
        assertThat( JGitUtil.getPathInfo( origin, "master", "path/to" ).getPathType() ).isEqualTo( DIRECTORY );

        logger.info( "Squashing from " + secondCommit.getName() + "  to HEAD" );
        new Squash( origin, "master", secondCommit.getName(), "squashed message" ).execute();

        int commitsCount = 0;
        for ( RevCommit commit : origin.log().all().call() ) {
            logger.info( ">>> Final Commit: " + commit.getFullMessage() + " - " + commit.toString() );
            commitsCount++;
        }

        assertThat( commitsCount ).isEqualTo( 2 );

    }

    private void createAddAndCommitFile( Git git,
                                         String file ) throws GitAPIException, IOException {
        File myfile = new File( git.getRepository().getDirectory().getParent(), file );
        myfile.createNewFile();

        git.add()
                .addFilepattern( file )
                .call();

        git.commit()
                .setMessage( "Added " + file )
                .call();

    }
}
