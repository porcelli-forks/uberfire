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

import org.eclipse.jgit.api.errors.ConcurrentRefUpdateException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

public abstract class BaseRefUpdateCommand {

    protected void refUpdate( final Repository repo,
                              final String branchName,
                              final ObjectId headId,
                              final RevCommit revCommit,
                              final String comment ) throws java.io.IOException, ConcurrentRefUpdateException {

        final RefUpdate ru = repo.updateRef( Constants.R_HEADS + branchName );
        if ( headId == null ) {
            ru.setExpectedOldObjectId( ObjectId.zeroId() );
        } else {
            ru.setExpectedOldObjectId( headId );
        }
        ru.setNewObjectId( revCommit.getId() );
        ru.setRefLogMessage( comment + revCommit.getShortMessage(), false );
        forceUpdate( ru, revCommit.getId() );
    }

    protected void forceUpdate( final RefUpdate ru,
                                final ObjectId id ) throws java.io.IOException, ConcurrentRefUpdateException {
        final RefUpdate.Result rc = ru.forceUpdate();
        switch ( rc ) {
            case NEW:
            case FORCED:
            case FAST_FORWARD:
                break;
            case REJECTED:
            case LOCK_FAILURE:
                throw new ConcurrentRefUpdateException( JGitText.get().couldNotLockHEAD, ru.getRef(), rc );
            default:
                throw new JGitInternalException( MessageFormat.format( JGitText.get().updatingRefFailed, Constants.HEAD, id.toString(), rc ) );
        }
    }
}
