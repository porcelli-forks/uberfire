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

import org.eclipse.jgit.api.errors.ConcurrentRefUpdateException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;

public abstract class BaseRefUpdateCommand {

    protected void refUpdate( final Repository repo,
                              final String branchName,
                              final ObjectId headId,
                              final RevCommit commit ) throws java.io.IOException, ConcurrentRefUpdateException {
        final BatchRefUpdate batchRefUpdate = repo.getRefDatabase().newBatchUpdate();
        batchRefUpdate.addCommand( new ReceiveCommand(
                headId != null ? headId.copy() : ObjectId.zeroId(),
                commit != null ? commit.getId().copy() : ObjectId.zeroId(),
                Constants.R_HEADS + branchName ) );
        batchRefUpdate.setAllowNonFastForwards( true );
        batchRefUpdate.execute( new RevWalk( repo ), NullProgressMonitor.INSTANCE );

        for ( ReceiveCommand command : batchRefUpdate.getCommands() ) {
            switch ( command.getResult() ) {
                case OK:
                    break;
                case REJECTED_OTHER_REASON:
                case REJECTED_MISSING_OBJECT:
                case REJECTED_NONFASTFORWARD:
                case REJECTED_NODELETE:
                case REJECTED_NOCREATE:
                case REJECTED_CURRENT_BRANCH:
                case LOCK_FAILURE:

                    throw new JGitInternalException( "Fail reason: " + command.getResult().toString() + " (" + command.getMessage() + ")" );
            }

        }
    }
}
