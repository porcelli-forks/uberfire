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

package org.uberfire.java.nio.fs.jgit.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.List;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.RefSpec;
import org.uberfire.commons.data.Pair;
import org.uberfire.java.nio.base.FileDiff;
import org.uberfire.java.nio.fs.jgit.JGitPathImpl;
import org.uberfire.java.nio.fs.jgit.util.commands.Clone;
import org.uberfire.java.nio.fs.jgit.util.commands.CreateRepository;
import org.uberfire.java.nio.fs.jgit.util.commands.Fork;
import org.uberfire.java.nio.fs.jgit.util.model.CommitContent;
import org.uberfire.java.nio.fs.jgit.util.model.CommitInfo;
import org.uberfire.java.nio.fs.jgit.util.model.PathInfo;

public interface Git {

    static Git createRepository( File repoDir ) {
        return new CreateRepository( repoDir ).execute().get();
    }

    static Git createRepository( File repoDir,
                                 File hookDir ) {
        return new CreateRepository( repoDir, hookDir ).execute().get();
    }

    static Git fork( File gitRepoContainerDir,
                     String origin,
                     String name,
                     CredentialsProvider credential ) throws InvalidRemoteException {
        return new Fork( gitRepoContainerDir, origin, name, credential ).execute();
    }

    static Git clone( File repoDest,
                      String origin,
                      boolean b,
                      CredentialsProvider credential ) throws InvalidRemoteException {
        return new Clone( repoDest, origin, true, credential ).execute().get();
    }

    void deleteRef( Ref ref );

    Ref getRef( String ref );

    void push( CredentialsProvider credentialsProvider,
               Pair<String, String> remote,
               boolean force,
               Collection<RefSpec> refSpecs ) throws InvalidRemoteException;

    void gc();

    RevCommit getLastCommit( String refName );

    RevCommit getLastCommit( Ref ref ) throws IOException;

    List<RevCommit> listCommits( Ref ref,
                                 String path ) throws IOException, GitAPIException;

    List<RevCommit> listCommits( ObjectId startRange,
                                 ObjectId endRange );

    Repository getRepository();

    ObjectId getTreeFromRef( String treeRef );

    void fetch( CredentialsProvider credential,
                Pair<String, String> remote,
                Collection<RefSpec> refSpecs ) throws InvalidRemoteException;

    void syncRemote( Pair<String, String> remote ) throws InvalidRemoteException;

    List<String> merge( String source,
                        String target );

    void cherryPick( JGitPathImpl target,
                     String... commits );

    void cherryPick( String targetBranch,
                     String[] commitsIDs );

    void createRef( String source,
                    String target );

    List<FileDiff> diffRefs( String branchA,
                             String branchB );

    void squash( String branch,
                 String startCommit,
                 String commitMessage );

    boolean commit( String branchName,
                    CommitInfo commitInfo,
                    boolean amend,
                    ObjectId originId,
                    CommitContent content );

    List<DiffEntry> listDiffs( ObjectId refA,
                               ObjectId refB );

    InputStream blobAsInputStream( String treeRef,
                                   String path );

    RevCommit getFirstCommit( Ref ref ) throws IOException;

    List<Ref> listRefs();

    List<ObjectId> resolveObjectIds( String... commits );

    RevCommit resolveRevCommit( ObjectId objectId ) throws IOException;

    List<RefSpec> updateRemoteConfig( Pair<String, String> remote,
                                      Collection<RefSpec> refSpecs ) throws IOException, URISyntaxException;

    PathInfo getPathInfo( String branchName,
                          String path );

    List<PathInfo> listPathContent( String branchName,
                                    String path );
}
