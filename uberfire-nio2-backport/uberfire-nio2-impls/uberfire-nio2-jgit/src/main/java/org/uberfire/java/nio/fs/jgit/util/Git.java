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

import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.CreateBranchCommand;
import org.eclipse.jgit.api.DeleteBranchCommand;
import org.eclipse.jgit.api.FetchCommand;
import org.eclipse.jgit.api.GarbageCollectCommand;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.LogCommand;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.RemoteListCommand;
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
import org.uberfire.java.nio.file.NoSuchFileException;
import org.uberfire.java.nio.fs.jgit.JGitPathImpl;
import org.uberfire.java.nio.fs.jgit.util.commands.BlobAsInputStream;
import org.uberfire.java.nio.fs.jgit.util.commands.CherryPick;
import org.uberfire.java.nio.fs.jgit.util.commands.Clone;
import org.uberfire.java.nio.fs.jgit.util.commands.Commit;
import org.uberfire.java.nio.fs.jgit.util.commands.CreateBranch;
import org.uberfire.java.nio.fs.jgit.util.commands.CreateRepository;
import org.uberfire.java.nio.fs.jgit.util.commands.DeleteBranch;
import org.uberfire.java.nio.fs.jgit.util.commands.DiffBranches;
import org.uberfire.java.nio.fs.jgit.util.commands.Fetch;
import org.uberfire.java.nio.fs.jgit.util.commands.Fork;
import org.uberfire.java.nio.fs.jgit.util.commands.GarbageCollector;
import org.uberfire.java.nio.fs.jgit.util.commands.GetFirstCommit;
import org.uberfire.java.nio.fs.jgit.util.commands.GetLastCommit;
import org.uberfire.java.nio.fs.jgit.util.commands.GetPathInfo;
import org.uberfire.java.nio.fs.jgit.util.commands.GetRef;
import org.uberfire.java.nio.fs.jgit.util.commands.GetTreeFromRef;
import org.uberfire.java.nio.fs.jgit.util.commands.ListCommits;
import org.uberfire.java.nio.fs.jgit.util.commands.ListDiffs;
import org.uberfire.java.nio.fs.jgit.util.commands.ListPathContent;
import org.uberfire.java.nio.fs.jgit.util.commands.ListRefs;
import org.uberfire.java.nio.fs.jgit.util.commands.Merge;
import org.uberfire.java.nio.fs.jgit.util.commands.Push;
import org.uberfire.java.nio.fs.jgit.util.commands.ResolveObjectIds;
import org.uberfire.java.nio.fs.jgit.util.commands.ResolveRevCommit;
import org.uberfire.java.nio.fs.jgit.util.commands.Squash;
import org.uberfire.java.nio.fs.jgit.util.commands.SyncRemote;
import org.uberfire.java.nio.fs.jgit.util.commands.UpdateRemoteConfig;
import org.uberfire.java.nio.fs.jgit.util.model.CommitContent;
import org.uberfire.java.nio.fs.jgit.util.model.CommitInfo;
import org.uberfire.java.nio.fs.jgit.util.model.PathInfo;

import static org.uberfire.java.nio.fs.jgit.util.RetryUtil.*;
import static org.uberfire.java.nio.fs.jgit.util.commands.PathUtil.*;

public class Git {

    private final org.eclipse.jgit.api.Git git;

    public Git( final org.eclipse.jgit.api.Git git ) {
        this.git = git;
    }

    public static Git createRepository( final File repoDir ) {
        return new CreateRepository( repoDir ).execute().get();
    }

    public static Git createRepository( final File repoDir,
                                        final File hookDir ) {
        return new CreateRepository( repoDir, hookDir ).execute().get();
    }

    public static Git fork( final File gitRepoContainerDir,
                            final String origin,
                            final String name,
                            final CredentialsProvider credential ) throws InvalidRemoteException {
        return new Fork( gitRepoContainerDir, origin, name, credential ).execute();
    }

    public static Git clone( final File repoDest,
                             final String origin,
                             final boolean b,
                             final CredentialsProvider credential ) throws InvalidRemoteException {
        return new Clone( repoDest, origin, true, credential ).execute().get();
    }

    public void deleteRef( final Ref ref ) {
        new DeleteBranch( this, ref ).execute();
    }

    public Ref getRef( final String ref ) {
        return new GetRef( git.getRepository(), ref ).execute();
    }

    public void push( final CredentialsProvider credentialsProvider,
                      final Pair<String, String> remote,
                      final boolean force,
                      final Collection<RefSpec> refSpecs ) throws InvalidRemoteException {
        new Push( this, credentialsProvider, remote, force, refSpecs ).execute();
    }

    public void gc() {
        new GarbageCollector( this ).execute();
    }

    public RevCommit getLastCommit( final String refName ) {
        return retryIfNeeded( RuntimeException.class, () -> new GetLastCommit( this, refName ).execute() );
    }

    public RevCommit getLastCommit( final Ref ref ) throws IOException {
        return new GetLastCommit( this, ref ).execute();
    }

    public List<RevCommit> listCommits( final Ref ref,
                                        final String path ) throws IOException, GitAPIException {
        return new ListCommits( this, ref, path ).execute();
    }

    public List<RevCommit> listCommits( final ObjectId startRange,
                                        final ObjectId endRange ) {
        return retryIfNeeded( RuntimeException.class, () -> new ListCommits( this, startRange, endRange ).execute() );
    }

    public Repository getRepository() {
        return git.getRepository();
    }

    public DeleteBranchCommand _branchDelete() {
        return git.branchDelete();
    }

    public ListBranchCommand _branchList() {
        return git.branchList();
    }

    public CreateBranchCommand _branchCreate() {
        return git.branchCreate();
    }

    public FetchCommand _fetch() {
        return git.fetch();
    }

    public GarbageCollectCommand _gc() {
        return git.gc();
    }

    public PushCommand _push() {
        return git.push();
    }

    public ObjectId getTreeFromRef( final String treeRef ) {
        return new GetTreeFromRef( this, treeRef ).execute();
    }

    public void fetch( final CredentialsProvider credential,
                       final Pair<String, String> remote,
                       final Collection<RefSpec> refSpecs ) throws InvalidRemoteException {
        new Fetch( this, credential, remote, refSpecs ).execute();
    }

    public void syncRemote( final Pair<String, String> remote ) throws InvalidRemoteException {
        new SyncRemote( this, remote ).execute();
    }

    public List<String> merge( final String source,
                               final String target ) {
        return new Merge( this, source, target ).execute();
    }

    public void cherryPick( final JGitPathImpl target,
                            final String... commits ) {
        new CherryPick( this, target.getRefTree(), commits ).execute();
    }

    public void cherryPick( final String targetBranch,
                            final String[] commitsIDs ) {
        new CherryPick( this, targetBranch, commitsIDs ).execute();
    }

    public void createRef( final String source,
                           final String target ) {
        new CreateBranch( this, source, target ).execute();

    }

    public List<FileDiff> diffRefs( final String branchA,
                                    final String branchB ) {
        return new DiffBranches( this, branchA, branchB ).execute();
    }

    public void squash( final String branch,
                        final String startCommit,
                        final String commitMessage ) {
        new Squash( this, branch, startCommit, commitMessage ).execute();
    }

    public LogCommand _log() {
        return git.log();
    }

    public boolean commit( final String branchName,
                           final CommitInfo commitInfo,
                           final boolean amend,
                           final ObjectId originId,
                           final CommitContent content ) {
        return new Commit( this, branchName, commitInfo, amend, null, content ).execute();
    }

    public List<DiffEntry> listDiffs( final ObjectId refA,
                                      final ObjectId refB ) {
        return new ListDiffs( this, refA, refB ).execute();
    }

    public InputStream blobAsInputStream( final String treeRef,
                                          final String path ) {
        return retryIfNeeded( NoSuchFileException.class,
                              () -> new BlobAsInputStream( this,
                                                           treeRef,
                                                           normalize( path ) ).execute().get() );
    }

    public RevCommit getFirstCommit( final Ref ref ) throws IOException {
        return new GetFirstCommit( this, ref ).execute();
    }

    public List<Ref> listRefs() {
        return new ListRefs( git.getRepository() ).execute();
    }

    public List<ObjectId> resolveObjectIds( final String... commits ) {
        return new ResolveObjectIds( this, commits ).execute();
    }

    public RevCommit resolveRevCommit( final ObjectId objectId ) throws IOException {
        return new ResolveRevCommit( git.getRepository(), objectId ).execute();
    }

    public List<RefSpec> updateRemoteConfig( final Pair<String, String> remote,
                                             final Collection<RefSpec> refSpecs ) throws IOException, URISyntaxException {
        return new UpdateRemoteConfig( this, remote, refSpecs ).execute();
    }

    public AddCommand _add() {
        return git.add();
    }

    public CommitCommand _commit() {
        return git.commit();
    }

    public RemoteListCommand _remoteList() {
        return git.remoteList();
    }

    public static CloneCommand _cloneRepository() {
        return org.eclipse.jgit.api.Git.cloneRepository();
    }

    public PathInfo getPathInfo( final String branchName,
                                 final String path ) {
        return retryIfNeeded( RuntimeException.class, () -> new GetPathInfo( this, branchName, path ).execute() );
    }

    public List<PathInfo> listPathContent( final String branchName,
                                           final String path ) {
        return retryIfNeeded( RuntimeException.class, () -> new ListPathContent( this, branchName, path ).execute() );
    }

}
