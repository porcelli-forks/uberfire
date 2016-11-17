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
import java.util.Collections;

import org.eclipse.jgit.api.errors.ConcurrentRefUpdateException;
import org.eclipse.jgit.errors.MissingObjectException;
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

import static org.eclipse.jgit.lib.Constants.*;
import static org.eclipse.jgit.lib.Ref.Storage.*;

public abstract class BaseRefUpdateCommand {

    protected void refUpdate( final Git git,
                              final String branchName,
                              final RevCommit commit ) throws java.io.IOException, ConcurrentRefUpdateException {
        update( git.getRepository(), Constants.R_HEADS + branchName, commit );
        //this aims to be temporary
        //but without this c git can't find master
        if ( branchName.equals( MASTER ) && !git.isHEADInitialized() ) {
            synchronized ( git.getRepository() ) {
                symRef( git, HEAD, Constants.R_HEADS + branchName );
                git.setHeadAsInitialized();
            }
        }
    }

    protected void symRef( final Git git,
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
            final Command n;
            try ( RevWalk rw = new RevWalk( repo ) ) {
                n = new Command( old, toRef( rw, commit, name, true ) );
            }
            return refTree.apply( Collections.singleton( n ) );
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

                fun.apply( reader, tree );
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
