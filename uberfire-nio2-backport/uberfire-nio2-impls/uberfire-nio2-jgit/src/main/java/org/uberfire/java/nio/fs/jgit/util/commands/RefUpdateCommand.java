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

package org.uberfire.java.nio.fs.jgit.util.commands;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.jgit.api.errors.ConcurrentRefUpdateException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.ketch.Proposal;
import org.eclipse.jgit.internal.storage.reftree.Command;
import org.eclipse.jgit.internal.storage.reftree.RefTree;
import org.eclipse.jgit.internal.storage.reftree.RefTreeDatabase;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdRef;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.SymbolicRef;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.uberfire.java.nio.fs.jgit.util.Git;
import org.uberfire.java.nio.fs.jgit.util.exceptions.GitException;

import static org.eclipse.jgit.lib.Constants.*;
import static org.eclipse.jgit.lib.Ref.Storage.*;
import static org.eclipse.jgit.transport.ReceiveCommand.Result.*;

public class RefUpdateCommand {

    private final Git git;
    private final String name;
    private final RevCommit commit;

    public RefUpdateCommand( final Git git,
                             final String branchName,
                             final RevCommit commit ) {
        this.git = git;
        this.name = branchName;
        this.commit = commit;
    }

    public void execute() throws java.io.IOException, ConcurrentRefUpdateException {
        update( git.getRepository(), Constants.R_HEADS + name, commit );
        //this `initialization` aims to be temporary
        // -> without this cgit can't find master when cloning repos
        if ( name.equals( MASTER ) && !git.isHEADInitialized() ) {
            synchronized ( git.getRepository() ) {
                symRef( git, HEAD, Constants.R_HEADS + name );
                git.setHeadAsInitialized();
            }
        }
    }

    private void symRef( final Git git,
                         final String name,
                         final String dst )
            throws java.io.IOException {
        commit( git.getRepository(), null, ( reader, tree ) -> {
            Ref old = tree.exactRef( reader, name );
            Ref newx = tree.exactRef( reader, dst );
            final Command n;
            if ( newx != null ) {
                n = new Command(
                        old,
                        new SymbolicRef( name, newx ) );
            } else {
                n = new Command(
                        old,
                        new SymbolicRef(
                                name,
                                new ObjectIdRef.Unpeeled( Ref.Storage.NEW, dst, null ) ) );
            }
            return tree.apply( Collections.singleton( n ) );
        } );
    }

    private void update( final Repository repo,
                         final String name,
                         final RevCommit commit )
            throws IOException {
        commit( repo, commit, ( reader, refTree ) -> {
            final Ref old = refTree.exactRef( reader, name );
            final List<Command> n = new ArrayList<>( 1 );
            try ( RevWalk rw = new RevWalk( repo ) ) {
                n.add( new Command( old, toRef( rw, commit, name, true ) ) );
                if ( git.getKetchLeader() != null ) {
                    final Proposal proposal = new Proposal( n )
                            .setAuthor( commit.getAuthorIdent() )
                            .setMessage( "push" );
                    git.getKetchLeader().queueProposal( proposal );
                }
//                if ( proposal.isDone() ) {
//                }
            } catch ( final IOException | InterruptedException e ) {
                String msg = JGitText.get().transactionAborted;
                for ( Command cmd : n ) {
                    if ( cmd.getResult() == NOT_ATTEMPTED ) {
                        cmd.setResult( REJECTED_OTHER_REASON, msg );
                    }
                }
                throw new GitException( "Error" );
                //log.error(msg, e);
            }
            return refTree.apply( n );
        } );
    }

    private static Ref toRef( final RevWalk rw,
                              final ObjectId id,
                              final String name,
                              final boolean mustExist ) throws IOException {
        if ( ObjectId.zeroId().equals( id ) ) {
            return null;
        }

        try {
            RevObject o = rw.parseAny( id );
            if ( o instanceof RevTag ) {
                RevObject p = rw.peel( o );
                return new ObjectIdRef.PeeledTag( NETWORK, name, id, p.copy() );
            }
            return new ObjectIdRef.PeeledNonTag( NETWORK, name, id );
        } catch ( MissingObjectException e ) {
            if ( mustExist ) {
                throw e;
            }
            return new ObjectIdRef.Unpeeled( NETWORK, name, id );
        }
    }

    interface Function {

        boolean apply( final ObjectReader reader,
                       final RefTree refTree ) throws IOException;
    }

    private void commit( final Repository repo,
                         final RevCommit original,
                         final Function fun ) throws IOException {
        try ( final ObjectReader reader = repo.newObjectReader();
              final ObjectInserter inserter = repo.newObjectInserter();
              final RevWalk rw = new RevWalk( reader ) ) {

            if ( repo.getRefDatabase() instanceof RefTreeDatabase ) {
                final RefTreeDatabase refdb = (RefTreeDatabase) repo.getRefDatabase();
                final RefDatabase bootstrap = refdb.getBootstrap();
                final RefUpdate refUpdate = bootstrap.newUpdate( refdb.getTxnCommitted(), false );

                final CommitBuilder cb = new CommitBuilder();
                final Ref ref = bootstrap.exactRef( refdb.getTxnCommitted() );
                final RefTree tree;
                if ( ref != null && ref.getObjectId() != null ) {
                    tree = RefTree.read( reader, rw.parseTree( ref.getObjectId() ) );
                    cb.setParentId( ref.getObjectId() );
                    refUpdate.setExpectedOldObjectId( ref.getObjectId() );
                } else {
                    tree = RefTree.newEmptyTree();
                    refUpdate.setExpectedOldObjectId( ObjectId.zeroId() );
                }

                if ( fun.apply( reader, tree ) ) {
                    cb.setTreeId( tree.writeTree( inserter ) );
                    if ( original != null ) {
                        cb.setAuthor( original.getAuthorIdent() );
                        cb.setCommitter( original.getAuthorIdent() );
                    } else {
                        final PersonIdent personIdent = new PersonIdent( "user", "user@example.com" );
                        cb.setAuthor( personIdent );
                        cb.setCommitter( personIdent );
                    }
                    refUpdate.setNewObjectId( inserter.insert( cb ) );
                    inserter.flush();
                    switch ( refUpdate.update( rw ) ) {
                        case NEW:
                        case FAST_FORWARD:
                            break;
                        default:
                            throw new RuntimeException( refUpdate.getName() );
                    }
                }

            }
        }
    }
}
