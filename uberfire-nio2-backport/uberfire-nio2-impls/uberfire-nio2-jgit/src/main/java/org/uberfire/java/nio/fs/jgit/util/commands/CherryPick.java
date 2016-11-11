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

import java.text.MessageFormat;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.api.errors.MultipleParentsNotAllowedException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.uberfire.java.nio.IOException;

/**
 * TODO: update me
 */
public class CherryPick extends BaseRefUpdateCommand {

    private final Repository repo;
    private final String targetBranch;
    private final String[] commits;

    public CherryPick( final Repository repo,
                       final String targetBranch,
                       final String... commits ) {
        this.repo = repo;
        this.targetBranch = targetBranch;
        this.commits = commits;
    }

    public void execute() {
        final Git git = new Git( repo );

        RevCommit newHead = null;

        final List<ObjectId> commits = new ResolveObjectIds( git, this.commits ).execute();
        if ( commits.size() != this.commits.length ) {
            throw new IOException( "Couldn't resolve some commits." );
        }

        final Ref headRef = new GetRef( git.getRepository(), targetBranch ).execute();
        if ( headRef == null ) {
            throw new IOException( "Branch not found." );
        }

        try {
            newHead = new ResolveRevCommit( repo, headRef.getObjectId() ).execute();

            // loop through all refs to be cherry-picked
            for ( final ObjectId src : commits ) {
                final RevCommit srcCommit = new ResolveRevCommit( repo, src ).execute();

                // get the parent of the commit to cherry-pick
                if ( srcCommit.getParentCount() != 1 ) {
                    throw new IOException( new MultipleParentsNotAllowedException(
                            MessageFormat.format(
                                    JGitText.get().canOnlyCherryPickCommitsWithOneParent,
                                    srcCommit.name(),
                                    Integer.valueOf( srcCommit.getParentCount() ) ) ) );
                }

                refUpdate( git.getRepository(),
                           targetBranch,
                           newHead,
                           srcCommit,
                           "cherry-pick: " );

                newHead = srcCommit;
            }
        } catch ( final java.io.IOException e ) {
            throw new IOException( new JGitInternalException(
                    MessageFormat.format(
                            JGitText.get().exceptionCaughtDuringExecutionOfCherryPickCommand,
                            e ), e ) );
        } catch ( final Exception e ) {
            throw new IOException( e );
        }
    }

}
